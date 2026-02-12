package com.lh.assist.analysis.application;

import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.entity.AnalysisRiskItem;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.enums.AnalysisRiskType;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.analysis.domain.repository.AnalysisRiskItemRepository;
import com.lh.assist.analysis.domain.repository.AnalysisSectionRepository;
import com.lh.assist.common.exception.AnalysisException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.infrastructure.aws.s3.S3Service;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.extend.FSSupplier;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.awt.Color;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisReportService {

    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisSectionRepository analysisSectionRepository;
    private final AnalysisRiskItemRepository analysisRiskItemRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final TemplateEngine templateEngine;

    private static final Pattern BBOX_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final int HIGH_RISK_SAFETY_MAX = 59;
    private static final int MEDIUM_RISK_SAFETY_MAX = 79;
    private static final int MEDIUM_PRIORITY_SAFETY_MAX = 89;
    private static final int TOP_ACTION_LIMIT = 5;

    // PDFBox 하이라이팅용 색상 (RGB)
    private static final Color COLOR_HIGH = new Color(244, 67, 54);   // Red
    private static final Color COLOR_MEDIUM = new Color(255, 193, 7); // Amber
    private static final Color COLOR_LOW = new Color(33, 150, 243);   // Blue

    @Transactional(readOnly = true)
    public byte[] generateReport(Long docId, String email) {
        User user = getUserByEmail(email);
        Document document = getDocumentById(docId);
        validateOwnership(user, document);

        AnalysisResult result = analysisResultRepository
                .findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(docId, AnalysisResultStatus.SUCCEEDED)
                .orElseThrow(() -> new AnalysisException(ErrorCode.ANALYSIS_NOT_FOUND));

        List<AnalysisSection> sections = analysisSectionRepository
                .findAllByAnalysisResult_AnalysisId(result.getAnalysisId());

        List<Long> sectionIds = sections.stream().map(AnalysisSection::getSectionId).toList();
        List<AnalysisRiskItem> riskItems = sectionIds.isEmpty() ? List.of() :
                analysisRiskItemRepository.findAllByAnalysisSection_SectionIdIn(sectionIds);

        try {
            // 1. [PDFBox] 원본 문서에 하이라이팅 박스 그리기
            byte[] originalPdf = s3Service.downloadFile(document.getS3Key());
            Map<Long, Integer> sectionMarkers = buildSectionMarkers(sections);
            byte[] highlightedPdf = drawHighlightsOnOriginal(originalPdf, sections, sectionMarkers);

            // 2. 표지/요약 PDF
            ReportViewDto coverData = buildReportViewDto(result, document, sections, riskItems, sectionMarkers);
            byte[] coverPdf = renderReportPdf(coverData, true);

            // 3. 페이지별 요약 PDF
            Map<Integer, byte[]> pageSummaryPdfs = new HashMap<>();
            for (Integer pageNumber : coverData.itemsByPage().keySet()) {
                ReportViewDto pageData = buildPageReportViewDto(coverData, pageNumber);
                pageSummaryPdfs.put(pageNumber, renderReportPdf(pageData, false));
            }

            // 4. 표지 + (원본페이지 + 해당페이지 요약) 교차 삽입
            return interleaveWithSummaries(highlightedPdf, coverPdf, pageSummaryPdfs);

        } catch (Exception e) {
            log.error("Failed to generate analysis report", e);
            throw new AnalysisException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Thymeleaf 템플릿을 사용하여 HTML 보고서를 PDF로 변환
     */
    private byte[] renderReportPdf(ReportViewDto viewData, boolean showCover) throws IOException {
        Context context = new Context();
        context.setVariable("data", viewData);
        context.setVariable("showCover", showCover); // 표지 표시 여부

        // HTML 렌더링
        String htmlContent = templateEngine.process("report/summary", context);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            if (!showCover) {
                builder.useFastMode();
            }
            builder.useSVGDrawer(new BatikSVGDrawer());

            // 한글 폰트 설정 (필수: resources/fonts/NotoSerifKR-Regular.ttf)
            ClassPathResource fontResource = new ClassPathResource("fonts/NotoSerifKR-Regular.ttf");
            if (fontResource.exists()) {
                byte[] fontBytes;
                try (InputStream fontStream = fontResource.getInputStream()) {
                    fontBytes = fontStream.readAllBytes();
                }
                FSSupplier<InputStream> supplier = () -> new ByteArrayInputStream(fontBytes);
                builder.useFont(supplier, "NotoSerifKR");
            } else {
                log.warn("Korean font file (NotoSerifKR-Regular.ttf) not found. Text may be broken.");
            }

            builder.withHtmlContent(htmlContent, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    /**
     * 원본 PDF 위에 반투명 하이라이팅 박스 추가 (텍스트 미포함)
     */
    private byte[] drawHighlightsOnOriginal(
            byte[] pdfBytes,
            List<AnalysisSection> sections,
            Map<Long, Integer> sectionMarkers
    ) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (AnalysisSection section : sections) {
                if (!section.isViolation() || section.getPageNumber() == null || section.getBbox() == null) {
                    continue;
                }

                double[] bbox = parseBbox(section.getBbox());
                if (bbox == null) continue;

                int pageIdx = section.getPageNumber() - 1;
                if (pageIdx < 0 || pageIdx >= doc.getNumberOfPages()) continue;

                PDPage page = doc.getPage(pageIdx);
                PDRectangle mediaBox = page.getMediaBox();
                float pageHeight = mediaBox.getHeight();

                // 좌표 변환 (Y축 반전 처리: PDFBox는 좌측 하단이 0,0)
                float x = (float) bbox[0];
                float y = pageHeight - (float) bbox[1] - (float) bbox[3];
                float w = (float) bbox[2];
                float h = (float) bbox[3];

                int score = section.getRiskScore() != null ? section.getRiskScore() : 0;
                Color color = resolveRiskColor(score);

                try (PDPageContentStream stream = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(0.3f); // 30% 투명도
                    stream.setGraphicsStateParameters(graphicsState);

                    stream.setNonStrokingColor(color);
                    stream.addRect(x, y, w, h);
                    stream.fill();
                }

                Integer marker = sectionMarkers.get(section.getSectionId());
                if (marker != null) {
                    float badgeSize = 12f;
                    float badgeX = Math.max(2f, x - badgeSize - 4f);
                    float badgeY = y + h - badgeSize - 2f;

                    try (PDPageContentStream textStream = new PDPageContentStream(
                            doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        // marker 배지
                        textStream.setNonStrokingColor(color);
                        textStream.addRect(badgeX, badgeY, badgeSize, badgeSize);
                        textStream.fill();

                        textStream.beginText();
                        textStream.setFont(PDType1Font.HELVETICA_BOLD, 8);
                        textStream.setNonStrokingColor(Color.WHITE);
                        textStream.newLineAtOffset(badgeX + 3f, badgeY + 2.5f);
                        textStream.showText(String.valueOf(marker));
                        textStream.endText();
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] mergePdfs(byte[] report, byte[] original) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            merger.addSource(new ByteArrayInputStream(report));
            merger.addSource(new ByteArrayInputStream(original));
            merger.setDestinationStream(out);
            merger.mergeDocuments(null);
            return out.toByteArray();
        }
    }

    private byte[] interleaveWithSummaries(
            byte[] highlightedPdf,
            byte[] coverPdf,
            Map<Integer, byte[]> pageSummaryPdfs
    ) throws IOException {
        List<PDDocument> openedSummaries = new ArrayList<>();
        try (PDDocument out = new PDDocument();
             PDDocument coverDoc = PDDocument.load(coverPdf);
             PDDocument highlightedDoc = PDDocument.load(highlightedPdf)) {

            for (PDPage page : coverDoc.getPages()) {
                out.importPage(page);
            }

            int pageCount = highlightedDoc.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                out.importPage(highlightedDoc.getPage(i));

                int pageNumber = i + 1;
                byte[] summaryPdf = pageSummaryPdfs.get(pageNumber);
                if (summaryPdf == null) {
                    continue;
                }
                PDDocument summaryDoc = PDDocument.load(summaryPdf);
                openedSummaries.add(summaryDoc);
                for (PDPage summaryPage : summaryDoc.getPages()) {
                    out.importPage(summaryPage);
                }
            }

            try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
                // 원본 PDF의 페이지 라벨이 섞여 표시되는 문제 방지
                out.getDocumentCatalog().setPageLabels(null);
                out.save(outStream);
                return outStream.toByteArray();
            }
        } finally {
            for (PDDocument doc : openedSummaries) {
                try {
                    doc.close();
                } catch (IOException ignore) {
                    // best-effort close
                }
            }
        }
    }

    // --- DTO 변환 로직 ---

    private ReportViewDto buildReportViewDto(
            AnalysisResult result,
            Document document,
            List<AnalysisSection> sections,
            List<AnalysisRiskItem> riskItems,
            Map<Long, Integer> sectionMarkers
    ) {
        int totalSections = sections.size();
        int violationCount = (int) sections.stream().filter(AnalysisSection::isViolation).count();
        int score = result.getTotalRiskScore() != null ? result.getTotalRiskScore() : 0;

        // 1. 리스크 분포 (고/중/저 위험 섹션 수, 안전점수 기준)
        int highRisk = 0, mediumRisk = 0, lowRisk = 0;
        for (AnalysisSection s : sections) {
            if (s.getRiskScore() == null) continue;
            int safetyScore = normalizeSafetyScore(s.getRiskScore());
            if (safetyScore <= HIGH_RISK_SAFETY_MAX) {
                highRisk++;
            } else if (safetyScore <= MEDIUM_RISK_SAFETY_MAX) {
                mediumRisk++;
            } else {
                lowRisk++;
            }
        }

        // 2. 리스크 유형 통계
        Map<AnalysisRiskType, Long> typeCounts = riskItems.stream()
                .collect(Collectors.groupingBy(AnalysisRiskItem::getRiskType, Collectors.counting()));

        int maxTypeCount = typeCounts.values().stream()
                .mapToInt(Long::intValue)
                .max()
                .orElse(0);

        List<RiskTypeStatDto> typeStats = typeCounts.entrySet().stream()
                .sorted(Map.Entry.<AnalysisRiskType, Long>comparingByValue().reversed())
                .map(e -> {
                    int percent = calculatePercent(e.getValue().intValue(), riskItems.size());
                    int barPercent = maxTypeCount == 0 ? 0
                            : (int) Math.round((e.getValue().doubleValue() / maxTypeCount) * 100);
                    if (e.getValue() > 0 && barPercent == 0) {
                        barPercent = 1;
                    }

                    // Enum 필드 대신 직접 매핑
                    String colorCode = resolveRiskColorCode(e.getKey());

                    return new RiskTypeStatDto(
                            e.getKey().getDescription(),
                            e.getValue().intValue(),
                            percent,
                            barPercent,
                            colorCode,
                            String.format("width: %d%%; background: %s", barPercent, colorCode)
                    );
                })
                .toList();

        // 2-1. 도넛 차트용 데이터
        int totalTypeCount = riskItems.size();
        double cumulative = 0.0;
        List<RiskTypeDonutDto> donutStats = new ArrayList<>();
        List<Map.Entry<AnalysisRiskType, Long>> donutEntries = typeCounts.entrySet().stream()
                .sorted(Map.Entry.<AnalysisRiskType, Long>comparingByValue().reversed())
                .toList();
        double donutGap = 1.8;
        double minVisible = 0.6;
        for (Map.Entry<AnalysisRiskType, Long> e : donutEntries) {
            double percent = totalTypeCount == 0 ? 0.0 : (e.getValue() * 100.0) / totalTypeCount;
            double visiblePercent = percent <= 0.0 ? 0.0 : Math.max(minVisible, percent - donutGap);
            String colorCode = resolveRiskColorCode(e.getKey());
            String dashArray = String.format(Locale.US, "%.2f %.2f", visiblePercent, 100.0 - visiblePercent);
            String dashOffset = String.format(Locale.US, "%.2f", 25.0 - cumulative);
            donutStats.add(new RiskTypeDonutDto(
                    e.getKey().getDescription(),
                    e.getValue().intValue(),
                    percent,
                    colorCode,
                    dashArray,
                    dashOffset
            ));
            cumulative += percent;
        }

        // 3. 페이지별 통계
        Map<Integer, List<AnalysisSection>> sectionsByPage = sections.stream()
                .filter(s -> s.getPageNumber() != null)
                .collect(Collectors.groupingBy(AnalysisSection::getPageNumber));

        Map<Integer, Long> pageCounts = sections.stream()
                .filter(AnalysisSection::isViolation)
                .collect(Collectors.groupingBy(AnalysisSection::getPageNumber, Collectors.counting()));

        int maxPageCount = pageCounts.values().stream()
                .mapToInt(Long::intValue)
                .max()
                .orElse(0);
        int barMaxHeightPx = 70;

        List<PageStatDto> pageStats = pageCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    int percent = calculatePercent(e.getValue().intValue(), violationCount);
                    int barPercent = maxPageCount == 0 ? 0
                            : (int) Math.round((e.getValue().doubleValue() / maxPageCount) * 100);
                    int barHeightPx = (int) Math.round((barPercent / 100.0) * barMaxHeightPx);
                    if (e.getValue() > 0 && barHeightPx == 0) {
                        barHeightPx = 4;
                    }
                    return new PageStatDto(e.getKey(), e.getValue().intValue(), barPercent, barHeightPx);
                })
                .toList();

        int totalPageCount = (int) sections.stream()
                .map(AnalysisSection::getPageNumber)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        int violationPageCount = pageCounts.size();
        int violationRatePercent = calculatePercent(violationCount, totalSections);
        int pageCoveragePercent = calculatePercent(violationPageCount, totalPageCount);

        int averageSectionSafetyScore = sections.isEmpty()
                ? 0
                : (int) Math.round(sections.stream()
                .mapToInt(s -> normalizeSafetyScore(s.getRiskScore()))
                .average()
                .orElse(0.0));

        int dominantViolationPage = pageCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(0);
        int dominantViolationPageCount = pageCounts.getOrDefault(dominantViolationPage, 0L).intValue();

        Map<String, Integer> priorityBucket = new LinkedHashMap<>();
        priorityBucket.put("urgent", 0);
        priorityBucket.put("high", 0);
        priorityBucket.put("medium", 0);
        priorityBucket.put("low", 0);
        for (AnalysisRiskItem item : riskItems) {
            int sectionSafetyScore = normalizeSafetyScore(item.getAnalysisSection().getRiskScore());
            String cssClass = resolveActionPriorityCssClass(sectionSafetyScore);
            priorityBucket.compute(cssClass, (k, v) -> v == null ? 1 : v + 1);
        }
        List<PriorityStatDto> priorityStats = List.of(
                buildPriorityStat("긴급", "urgent", priorityBucket.get("urgent"), riskItems.size()),
                buildPriorityStat("높음", "high", priorityBucket.get("high"), riskItems.size()),
                buildPriorityStat("중간", "medium", priorityBucket.get("medium"), riskItems.size()),
                buildPriorityStat("낮음", "low", priorityBucket.get("low"), riskItems.size())
        );

        List<TypePriorityMatrixDto> typePriorityMatrix = Arrays.stream(AnalysisRiskType.values())
                .map(type -> {
                    int urgent = 0;
                    int high = 0;
                    int medium = 0;
                    int low = 0;
                    for (AnalysisRiskItem item : riskItems) {
                        if (item.getRiskType() != type) {
                            continue;
                        }
                        String cssClass = resolveActionPriorityCssClass(normalizeSafetyScore(item.getAnalysisSection().getRiskScore()));
                        switch (cssClass) {
                            case "urgent" -> urgent++;
                            case "high" -> high++;
                            case "medium" -> medium++;
                            default -> low++;
                        }
                    }
                    return new TypePriorityMatrixDto(
                            type.getDescription(),
                            resolveRiskColorCode(type),
                            urgent,
                            high,
                            medium,
                            low,
                            urgent + high + medium + low
                    );
                })
                .filter(row -> row.totalCount() > 0)
                .toList();

        List<PageRiskProfileDto> pageRiskProfiles = sectionsByPage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int pageNumber = entry.getKey();
                    List<AnalysisSection> pageSections = entry.getValue();
                    int pageTotal = pageSections.size();
                    int pageViolations = (int) pageSections.stream().filter(AnalysisSection::isViolation).count();
                    int pageAverageSafety = (int) Math.round(pageSections.stream()
                            .mapToInt(section -> normalizeSafetyScore(section.getRiskScore()))
                            .average()
                            .orElse(0.0));

                    int urgent = 0;
                    int high = 0;
                    int medium = 0;
                    int low = 0;
                    for (AnalysisSection section : pageSections) {
                        String priority = resolveActionPriorityCssClass(normalizeSafetyScore(section.getRiskScore()));
                        switch (priority) {
                            case "urgent" -> urgent++;
                            case "high" -> high++;
                            case "medium" -> medium++;
                            default -> low++;
                        }
                    }

                    return new PageRiskProfileDto(
                            pageNumber,
                            pageViolations,
                            pageTotal,
                            pageAverageSafety,
                            urgent,
                            high,
                            medium,
                            low,
                            calculatePercent(urgent, pageTotal),
                            calculatePercent(high, pageTotal),
                            calculatePercent(medium, pageTotal),
                            calculatePercent(low, pageTotal)
                    );
                })
                .toList();

        List<String> keyInsights = new ArrayList<>();
        keyInsights.add(String.format(
                "전체 섹션 %d건 중 위반 가능성 섹션은 %d건(%d%%)으로 확인되었습니다.",
                totalSections,
                violationCount,
                violationRatePercent
        ));
        if (dominantViolationPage > 0) {
            keyInsights.add(String.format(
                    "리스크가 가장 집중된 페이지는 P%d이며, 해당 페이지에서 %d건의 위반 가능성이 탐지되었습니다.",
                    dominantViolationPage,
                    dominantViolationPageCount
            ));
        }
        keyInsights.add(String.format(
                "리스크 항목은 우선순위 기준으로 긴급 %d건, 높음 %d건, 중간 %d건, 낮음 %d건입니다.",
                priorityBucket.get("urgent"),
                priorityBucket.get("high"),
                priorityBucket.get("medium"),
                priorityBucket.get("low")
        ));

        Map<Long, Integer> riskItemCountBySection = riskItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getAnalysisSection().getSectionId(),
                        Collectors.summingInt(item -> 1)
                ));
        Comparator<AnalysisSection> sectionPriorityComparator = buildSectionPriorityComparator(riskItemCountBySection);

        // 4. 상세 내역 (페이지별 그룹핑)
        Map<Integer, List<RiskItemViewDto>> itemsByPage = riskItems.stream()
                .sorted(Comparator
                        .comparing(
                                (AnalysisRiskItem i) -> i.getAnalysisSection().getPageNumber(),
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(
                                (AnalysisRiskItem i) -> normalizeSafetyScore(i.getAnalysisSection().getRiskScore())
                        )
                        .thenComparing(i -> !i.getAnalysisSection().isViolation())
                        .thenComparing(
                                (AnalysisRiskItem i) -> i.getAnalysisSection().getSectionId(),
                                Comparator.nullsLast(Long::compareTo)
                        )
                        .thenComparing(AnalysisRiskItem::getRiskId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.groupingBy(
                        item -> item.getAnalysisSection().getPageNumber(),
                        LinkedHashMap::new,
                        Collectors.mapping(item -> toItemDto(item, sectionMarkers), Collectors.toList())
                ));

        List<TopActionDto> topActions = sections.stream()
                .sorted(sectionPriorityComparator)
                .filter(section -> section.getPageNumber() != null)
                .limit(TOP_ACTION_LIMIT)
                .map(section -> {
                    int sectionSafetyScore = normalizeSafetyScore(section.getRiskScore());
                    return new TopActionDto(
                            sectionMarkers.get(section.getSectionId()),
                            section.getPageNumber(),
                            sectionSafetyScore,
                            resolveActionPriorityLabel(sectionSafetyScore),
                            resolveActionPriorityCssClass(sectionSafetyScore),
                            riskItemCountBySection.getOrDefault(section.getSectionId(), 0),
                            section.isViolation()
                    );
                })
                .toList();

        String rawTitle = document.getTitle() != null ? document.getTitle() : "";
        String normalizedTitle = Normalizer.normalize(rawTitle, Normalizer.Form.NFKC);
        String titleNoExt = normalizedTitle.replaceFirst("\\.[^.]+$", "");

        return ReportViewDto.builder()
                .title(titleNoExt)
                .analyzedDate(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                .totalScore(score)
                .violationCount(violationCount)
                .actionPriorityLabel(resolveActionPriorityLabel(score))
                .actionPriorityCssClass(resolveActionPriorityCssClass(score))
                .gaugeAngle(-90.0 + (Math.min(100, Math.max(0, score)) / 100.0) * 180.0)
                .gaugeFill((Math.min(100, Math.max(0, score)) / 100.0) * 126.0)
                .highRiskSectionCount(highRisk)
                .mediumRiskSectionCount(mediumRisk)
                .lowRiskSectionCount(lowRisk)
                .totalSectionCount(totalSections)
                .highRiskSectionPercent(calculatePercent(highRisk, totalSections))
                .mediumRiskSectionPercent(calculatePercent(mediumRisk, totalSections))
                .lowRiskSectionPercent(calculatePercent(lowRisk, totalSections))
                .riskTypeStats(typeStats)
                .riskTypeDonut(donutStats)
                .pageStats(pageStats)
                .topActions(topActions)
                .averageSectionSafetyScore(averageSectionSafetyScore)
                .violationRatePercent(violationRatePercent)
                .totalPageCount(totalPageCount)
                .violationPageCount(violationPageCount)
                .pageCoveragePercent(pageCoveragePercent)
                .dominantViolationPage(dominantViolationPage)
                .dominantViolationPageCount(dominantViolationPageCount)
                .priorityStats(priorityStats)
                .typePriorityMatrix(typePriorityMatrix)
                .pageRiskProfiles(pageRiskProfiles)
                .keyInsights(keyInsights)
                .itemsByPage(itemsByPage)
                .build();
    }

    private ReportViewDto buildPageReportViewDto(ReportViewDto fullData, Integer pageNumber) {
        Map<Integer, List<RiskItemViewDto>> filtered = fullData.itemsByPage().entrySet().stream()
                .filter(e -> Objects.equals(e.getKey(), pageNumber))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return ReportViewDto.builder()
                .title(fullData.title())
                .analyzedDate(fullData.analyzedDate())
                .totalScore(fullData.totalScore())
                .violationCount(fullData.violationCount())
                .actionPriorityLabel(fullData.actionPriorityLabel())
                .actionPriorityCssClass(fullData.actionPriorityCssClass())
                .gaugeAngle(fullData.gaugeAngle())
                .gaugeFill(fullData.gaugeFill())
                .highRiskSectionCount(fullData.highRiskSectionCount())
                .mediumRiskSectionCount(fullData.mediumRiskSectionCount())
                .lowRiskSectionCount(fullData.lowRiskSectionCount())
                .totalSectionCount(fullData.totalSectionCount())
                .highRiskSectionPercent(fullData.highRiskSectionPercent())
                .mediumRiskSectionPercent(fullData.mediumRiskSectionPercent())
                .lowRiskSectionPercent(fullData.lowRiskSectionPercent())
                .riskTypeStats(fullData.riskTypeStats())
                .riskTypeDonut(fullData.riskTypeDonut())
                .pageStats(fullData.pageStats())
                .topActions(fullData.topActions())
                .averageSectionSafetyScore(fullData.averageSectionSafetyScore())
                .violationRatePercent(fullData.violationRatePercent())
                .totalPageCount(fullData.totalPageCount())
                .violationPageCount(fullData.violationPageCount())
                .pageCoveragePercent(fullData.pageCoveragePercent())
                .dominantViolationPage(fullData.dominantViolationPage())
                .dominantViolationPageCount(fullData.dominantViolationPageCount())
                .priorityStats(fullData.priorityStats())
                .typePriorityMatrix(fullData.typePriorityMatrix())
                .pageRiskProfiles(fullData.pageRiskProfiles())
                .keyInsights(fullData.keyInsights())
                .itemsByPage(filtered)
                .build();
    }

    private PriorityStatDto buildPriorityStat(String label, String cssClass, Integer count, int totalRiskItems) {
        int normalizedCount = count == null ? 0 : count;
        return new PriorityStatDto(
                label,
                cssClass,
                normalizedCount,
                calculatePercent(normalizedCount, totalRiskItems)
        );
    }

    private RiskItemViewDto toItemDto(AnalysisRiskItem item, Map<Long, Integer> sectionMarkers) {
        int sectionSafetyScore = normalizeSafetyScore(item.getAnalysisSection().getRiskScore());
        Integer marker = sectionMarkers.get(item.getAnalysisSection().getSectionId());
        return new RiskItemViewDto(
                marker,
                item.getRiskType().getDescription(),
                resolveCssClass(item.getRiskType()), // 직접 매핑 함수 호출
                item.getAnalysisSection().getPageNumber(),
                resolveActionPriorityLabel(sectionSafetyScore),
                resolveActionPriorityCssClass(sectionSafetyScore),
                sectionSafetyScore,
                item.getDetectedText(),
                item.getGuideMessage(),
                item.getReasoning()
        );
    }

    private Comparator<AnalysisSection> buildSectionPriorityComparator(Map<Long, Integer> riskItemCountBySection) {
        return Comparator
                .comparingInt((AnalysisSection section) -> normalizeSafetyScore(section.getRiskScore()))
                .thenComparing(section -> !section.isViolation())
                .thenComparing(
                        (AnalysisSection section) -> riskItemCountBySection.getOrDefault(section.getSectionId(), 0),
                        Comparator.reverseOrder()
                )
                .thenComparing(
                        section -> section.getPageNumber() != null ? section.getPageNumber() : Integer.MAX_VALUE
                )
                .thenComparing(
                        section -> section.getSectionId() != null ? section.getSectionId() : Long.MAX_VALUE
                );
    }

    // Enum 변경 없이 색상/클래스 처리하는 헬퍼 메서드
    private String resolveRiskColorCode(AnalysisRiskType type) {
        return switch (type) {
            case MISSING -> "#D32F2F";
            case APPROPRIATENESS -> "#F57C00";
            case CLARITY -> "#388E3C";
            case PROCEDURE_COMPLIANCE -> "#1976D2";
        };
    }

    private String resolveCssClass(AnalysisRiskType type) {
        return switch (type) {
            case MISSING -> "missing";
            case APPROPRIATENESS -> "appropriateness";
            case CLARITY -> "clarity";
            case PROCEDURE_COMPLIANCE -> "procedure";
        };
    }

    private double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) return null;
        try {
            Matcher matcher = BBOX_NUMBER.matcher(bbox);
            List<Double> numbers = new ArrayList<>();
            while (matcher.find()) numbers.add(Double.parseDouble(matcher.group()));

            if (numbers.size() < 4) return null;
            double x = numbers.get(0);
            double y = numbers.get(1);
            double w = numbers.get(2);
            double h = numbers.get(3);

            // w, h가 좌표값(x2, y2)으로 들어온 경우 변환
            if (w > x && h > y) { w = w - x; h = h - y; }
            return new double[]{x, y, w, h};
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Long, Integer> buildSectionMarkers(List<AnalysisSection> sections) {
        Map<Integer, List<AnalysisSection>> byPage = sections.stream()
                .filter(s -> s.isViolation() && s.getPageNumber() != null && s.getBbox() != null)
                .collect(Collectors.groupingBy(AnalysisSection::getPageNumber, LinkedHashMap::new, Collectors.toList()));

        Map<Long, Integer> markerBySectionId = new HashMap<>();
        for (Map.Entry<Integer, List<AnalysisSection>> entry : byPage.entrySet()) {
            List<AnalysisSection> pageSections = entry.getValue();
            pageSections.sort(Comparator.comparingDouble((AnalysisSection s) -> {
                        double[] bbox = parseBbox(s.getBbox());
                        if (bbox == null) return 0d;
                        return bbox[1] + bbox[3]; // 상단 Y 좌표
                    })
                    .reversed()
                    .thenComparing(AnalysisSection::getSectionId));

            int idx = 1;
            for (AnalysisSection section : pageSections) {
                markerBySectionId.put(section.getSectionId(), idx++);
            }
        }
        return markerBySectionId;
    }

    private Color resolveRiskColor(int score) {
        int safetyScore = normalizeSafetyScore(score);
        if (safetyScore <= HIGH_RISK_SAFETY_MAX) {
            return COLOR_HIGH;
        }
        if (safetyScore <= MEDIUM_RISK_SAFETY_MAX) {
            return COLOR_MEDIUM;
        }
        return COLOR_LOW;
    }

    private int normalizeSafetyScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String resolveActionPriorityLabel(int safetyScore) {
        int normalized = normalizeSafetyScore(safetyScore);
        if (normalized <= HIGH_RISK_SAFETY_MAX) {
            return "긴급";
        }
        if (normalized <= MEDIUM_RISK_SAFETY_MAX) {
            return "높음";
        }
        if (normalized <= MEDIUM_PRIORITY_SAFETY_MAX) {
            return "중간";
        }
        return "낮음";
    }

    private String resolveActionPriorityCssClass(int safetyScore) {
        int normalized = normalizeSafetyScore(safetyScore);
        if (normalized <= HIGH_RISK_SAFETY_MAX) {
            return "urgent";
        }
        if (normalized <= MEDIUM_RISK_SAFETY_MAX) {
            return "high";
        }
        if (normalized <= MEDIUM_PRIORITY_SAFETY_MAX) {
            return "medium";
        }
        return "low";
    }

    private int calculatePercent(int count, int total) {
        return total == 0 ? 0 : (int) Math.round(((double) count / total) * 100);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AnalysisException(ErrorCode.UNAUTHORIZED));
    }

    private Document getDocumentById(Long docId) {
        return documentRepository.findById(docId)
                .orElseThrow(() -> new AnalysisException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private void validateOwnership(User user, Document document) {
        if (!document.getUser().equals(user)) {
            throw new AnalysisException(ErrorCode.ACCESS_DENIED);
        }
    }

    // --- 내부 DTO 클래스 정의 (static record) ---

    @Builder
    public record ReportViewDto(
            String title,
            String analyzedDate,
            int totalScore,
            int violationCount,
            String actionPriorityLabel,
            String actionPriorityCssClass,
            double gaugeAngle,
            double gaugeFill,
            int highRiskSectionCount,
            int mediumRiskSectionCount,
            int lowRiskSectionCount,
            int totalSectionCount,
            int highRiskSectionPercent,
            int mediumRiskSectionPercent,
            int lowRiskSectionPercent,
            List<RiskTypeStatDto> riskTypeStats,
            List<RiskTypeDonutDto> riskTypeDonut,
            List<PageStatDto> pageStats,
            List<TopActionDto> topActions,
            int averageSectionSafetyScore,
            int violationRatePercent,
            int totalPageCount,
            int violationPageCount,
            int pageCoveragePercent,
            int dominantViolationPage,
            int dominantViolationPageCount,
            List<PriorityStatDto> priorityStats,
            List<TypePriorityMatrixDto> typePriorityMatrix,
            List<PageRiskProfileDto> pageRiskProfiles,
            List<String> keyInsights,
            Map<Integer, List<RiskItemViewDto>> itemsByPage
    ) {}

    public record RiskTypeStatDto(
            String label,
            int count,
            int percent,
            int barPercent,
            String color,
            String widthStyle
    ) {}

    public record RiskTypeDonutDto(
            String label,
            int count,
            double percent,
            String color,
            String dashArray,
            String dashOffset
    ) {}

    public record PageStatDto(
            int pageNumber,
            int count,
            int barPercent,
            int barHeightPx
    ) {}

    public record TopActionDto(
            Integer marker,
            Integer pageNumber,
            int sectionSafetyScore,
            String actionPriorityLabel,
            String actionPriorityCssClass,
            int riskItemCount,
            boolean violation
    ) {}

    public record PriorityStatDto(
            String label,
            String cssClass,
            int count,
            int percent
    ) {}

    public record TypePriorityMatrixDto(
            String label,
            String color,
            int urgentCount,
            int highCount,
            int mediumCount,
            int lowCount,
            int totalCount
    ) {}

    public record PageRiskProfileDto(
            int pageNumber,
            int violationCount,
            int totalSectionCount,
            int averageSafetyScore,
            int urgentCount,
            int highCount,
            int mediumCount,
            int lowCount,
            int urgentPercent,
            int highPercent,
            int mediumPercent,
            int lowPercent
    ) {}

    public record RiskItemViewDto(
            Integer marker,
            String riskTypeLabel,
            String cssClass,
            Integer pageNumber,
            String actionPriorityLabel,
            String actionPriorityCssClass,
            Integer sectionSafetyScore,
            String detectedText,
            String guideMessage,
            String reasoning
    ) {}
}
