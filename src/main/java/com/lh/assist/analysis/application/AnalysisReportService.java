package com.lh.assist.analysis.application;

import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.entity.AnalysisRiskItem;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import com.lh.assist.analysis.domain.entity.AnalysisEvidence;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.enums.AnalysisRiskType;
import com.lh.assist.analysis.domain.enums.AnalysisEvidenceSourceType;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.analysis.domain.repository.AnalysisEvidenceRepository;
import com.lh.assist.analysis.domain.repository.AnalysisRiskItemRepository;
import com.lh.assist.analysis.domain.repository.AnalysisSectionRepository;
import com.lh.assist.common.exception.AnalysisException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.infrastructure.aws.s3.S3Service;
import com.lh.assist.reg.domain.entity.AuditItem;
import com.lh.assist.reg.domain.entity.AuditManualItem;
import com.lh.assist.reg.domain.entity.RegItem;
import com.lh.assist.reg.domain.repository.AuditItemRepository;
import com.lh.assist.reg.domain.repository.AuditManualItemRepository;
import com.lh.assist.reg.domain.repository.RegItemRepository;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.extend.FSSupplier;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisReportService {

    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisSectionRepository analysisSectionRepository;
    private final AnalysisRiskItemRepository analysisRiskItemRepository;
    private final AnalysisEvidenceRepository analysisEvidenceRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final RegItemRepository regItemRepository;
    private final AuditManualItemRepository auditManualItemRepository;
    private final AuditItemRepository auditItemRepository;
    private final S3Service s3Service;
    private final TemplateEngine templateEngine;
    private final ReportChartRenderer reportChartRenderer;

    private static final Pattern BBOX_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern LIABILITY_PATTERN = Pattern.compile("(제\\s*\\d+\\s*조(?:\\s*\\d+\\s*항)?)");
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

            String baseUri = new File(".").getAbsoluteFile().toURI().toString();
            builder.withHtmlContent(htmlContent, baseUri);
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
                    if (hasRenderableContent(summaryPage)) {
                        out.importPage(summaryPage);
                    }
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

    private boolean hasRenderableContent(PDPage page) {
        try {
            if (page.getAnnotations() != null && !page.getAnnotations().isEmpty()) {
                return true;
            }
        } catch (IOException ignore) {
            // fallback to stream inspection
        }

        Iterator<PDStream> streams = page.getContentStreams();
        while (streams.hasNext()) {
            PDStream stream = streams.next();
            try (InputStream in = stream.createInputStream()) {
                if (in.read() != -1) {
                    return true;
                }
            } catch (IOException ignore) {
                // continue
            }
        }
        return false;
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

        List<PageSafetyStatDto> pageSafetyStats = sectionsByPage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int pageNumber = entry.getKey();
                    List<AnalysisSection> pageSections = entry.getValue();
                    Integer pageMinSafetyScore = pageSections.stream()
                            .map(AnalysisSection::getRiskScore)
                            .filter(Objects::nonNull)
                            .map(this::normalizeSafetyScore)
                            .min(Integer::compareTo)
                            .orElse(null);
                    boolean hasViolation = pageSections.stream().anyMatch(AnalysisSection::isViolation);
                    int pageViolationCount = (int) pageSections.stream().filter(AnalysisSection::isViolation).count();
                    String priorityCss = resolveActionPriorityCssClassNullable(pageMinSafetyScore);
                    String priorityLabel = resolveActionPriorityLabelNullable(pageMinSafetyScore);
                    return new PageSafetyStatDto(
                            pageNumber,
                            pageMinSafetyScore,
                            priorityLabel,
                            priorityCss,
                            pageMinSafetyScore == null ? 0 : pageMinSafetyScore,
                            hasViolation,
                            pageViolationCount,
                            pageSections.size()
                    );
                })
                .toList();

        Map<Integer, Map<AnalysisRiskType, Long>> heatCounts = riskItems.stream()
                .filter(item -> item.getAnalysisSection().getPageNumber() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getAnalysisSection().getPageNumber(),
                        Collectors.groupingBy(AnalysisRiskItem::getRiskType, Collectors.counting())
                ));
        int heatMax = heatCounts.values().stream()
                .flatMap(typeMap -> typeMap.values().stream())
                .mapToInt(Long::intValue)
                .max()
                .orElse(0);
        List<PageTypeHeatmapRowDto> pageTypeHeatmapRows = sectionsByPage.keySet().stream()
                .sorted()
                .map(pageNumber -> {
                    Map<AnalysisRiskType, Long> rowCounts = heatCounts.getOrDefault(pageNumber, Map.of());
                    List<HeatmapCellDto> cells = Arrays.stream(AnalysisRiskType.values())
                            .map(type -> {
                                int count = rowCounts.getOrDefault(type, 0L).intValue();
                                return new HeatmapCellDto(
                                        type.getDescription(),
                                        count,
                                        buildHeatCellStyle(count, heatMax)
                                );
                            })
                            .toList();
                    int rowTotal = cells.stream().mapToInt(HeatmapCellDto::count).sum();
                    return new PageTypeHeatmapRowDto(pageNumber, cells, rowTotal);
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
        Map<Long, List<AnalysisRiskItem>> riskItemsBySection = riskItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getAnalysisSection().getSectionId()
                ));
        Map<Long, EvidenceResolvedDto> evidenceByRiskId = buildEvidenceResolvedMap(riskItems);

        List<RiskProfileAxisDto> riskProfileAxes = buildRiskProfileAxes(
                typeCounts,
                riskItems.size(),
                priorityBucket.getOrDefault("urgent", 0),
                dominantViolationPageCount,
                violationCount
        );
        List<DeductionBreakdownDto> deductionBreakdown = buildDeductionBreakdown(typeCounts, score);
        List<PriorityBubblePointDto> priorityBubblePoints = sections.stream()
                .filter(section -> section.getPageNumber() != null)
                .sorted(Comparator.comparing(
                        (AnalysisSection section) -> normalizeSafetyScore(section.getRiskScore()))
                        .thenComparing(section -> section.getPageNumber() != null ? section.getPageNumber() : Integer.MAX_VALUE)
                        .thenComparing(section -> section.getSectionId() != null ? section.getSectionId() : Long.MAX_VALUE))
                .limit(28)
                .map(section -> {
                    int safetyScore = normalizeSafetyScore(section.getRiskScore());
                    int itemCount = riskItemCountBySection.getOrDefault(section.getSectionId(), 0);
                    String priorityCss = resolveActionPriorityCssClassNullable(section.getRiskScore());
                    int bubbleSize = 10 + Math.min(34, itemCount * 4);
                    Integer marker = sectionMarkers.get(section.getSectionId());
                    String label = "P" + section.getPageNumber() + (marker != null ? " #" + marker : "");
                    return new PriorityBubblePointDto(label, safetyScore, itemCount, bubbleSize, priorityCss);
                })
                .toList();

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
                        Collectors.mapping(item -> toItemDto(item, sectionMarkers, evidenceByRiskId), Collectors.toList())
                ));

        List<TopActionDto> topActions = sections.stream()
                .sorted(sectionPriorityComparator)
                .filter(section -> section.getPageNumber() != null)
                .limit(TOP_ACTION_LIMIT)
                .map(section -> {
                    int sectionSafetyScore = normalizeSafetyScore(section.getRiskScore());
                    List<AnalysisRiskItem> sectionRiskItems = riskItemsBySection.getOrDefault(section.getSectionId(), List.of());
                    AnalysisRiskItem primary = sectionRiskItems.stream()
                            .sorted(Comparator.comparing(AnalysisRiskItem::getRiskId, Comparator.nullsLast(Long::compareTo)))
                            .findFirst()
                            .orElse(null);
                    String primaryRiskType = primary == null ? "-" : primary.getRiskType().getDescription();
                    String primaryReason = primary == null ? "-"
                            : truncateSummaryText(
                            firstNonBlank(primary.getReasoning(), primary.getGuideMessage(), primary.getDetectedText()),
                            70
                    );
                    EvidenceResolvedDto evidence = primary == null ? null : evidenceByRiskId.get(primary.getRiskId());
                    String liabilityRef = evidence == null ? extractLiabilityReference(primary) : evidence.reference();
                    return new TopActionDto(
                            sectionMarkers.get(section.getSectionId()),
                            section.getPageNumber(),
                            sectionSafetyScore,
                            resolveActionPriorityLabel(sectionSafetyScore),
                            resolveActionPriorityCssClass(sectionSafetyScore),
                            riskItemCountBySection.getOrDefault(section.getSectionId(), 0),
                            section.isViolation(),
                            primaryRiskType,
                            primaryReason,
                            liabilityRef,
                            evidence == null ? "-" : evidence.sourceLabel(),
                            evidence == null ? null : evidence.sourceUrl()
                    );
                })
                .toList();

        String rawTitle = document.getTitle() != null ? document.getTitle() : "";
        String normalizedTitle = Normalizer.normalize(rawTitle, Normalizer.Form.NFKC);
        String titleNoExt = normalizedTitle.replaceFirst("\\.[^.]+$", "");

        String totalScoreChartImage = ensureChartImage(
                reportChartRenderer
                        .renderTotalScoreChart(score, resolveActionPriorityLabel(score))
                        .orElseGet(() -> buildTotalScoreChartImage(score)),
                "총 안전점수"
        );
        String pageSafetyChartImage = ensureChartImage(
                reportChartRenderer
                        .renderPageSafetyChart(pageSafetyStats)
                        .orElseGet(() -> buildPageSafetyChartImage(pageSafetyStats)),
                "페이지별 위험도"
        );
        String priorityDistributionChartImage = ensureChartImage(
                reportChartRenderer
                        .renderPriorityDistributionChart(priorityStats)
                        .orElseGet(() -> buildPriorityDistributionChartImage(priorityStats)),
                "유형별 리스크 분포"
        );
        String deductionWaterfallChartImage = ensureChartImage(
                reportChartRenderer
                        .renderDeductionWaterfallChart(score, deductionBreakdown)
                        .orElseGet(() -> buildDeductionWaterfallFallbackImage(score, deductionBreakdown)),
                "총점 감점 요인"
        );

        log.info(
                "Report charts prepared: total={}, page={}, priority={}, waterfall={}",
                safeLength(totalScoreChartImage),
                safeLength(pageSafetyChartImage),
                safeLength(priorityDistributionChartImage),
                safeLength(deductionWaterfallChartImage)
        );

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
                .pageSafetyStats(pageSafetyStats)
                .pageTypeHeatmapRows(pageTypeHeatmapRows)
                .totalScoreChartImage(totalScoreChartImage)
                .pageSafetyChartImage(pageSafetyChartImage)
                .priorityDistributionChartImage(priorityDistributionChartImage)
                .pageTypeHeatmapChartImage(reportChartRenderer
                        .renderPageTypeHeatmapChart(pageTypeHeatmapRows)
                        .orElse(null))
                .riskProfileRadarChartImage(reportChartRenderer
                        .renderRiskProfileRadarChart(riskProfileAxes)
                        .orElse(null))
                .deductionWaterfallChartImage(deductionWaterfallChartImage)
                .priorityBubbleChartImage(reportChartRenderer
                        .renderPriorityBubbleChart(priorityBubblePoints)
                        .orElse(null))
                .riskProfileAxes(riskProfileAxes)
                .deductionBreakdown(deductionBreakdown)
                .priorityBubblePoints(priorityBubblePoints)
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
                .pageSafetyStats(fullData.pageSafetyStats())
                .pageTypeHeatmapRows(fullData.pageTypeHeatmapRows())
                .totalScoreChartImage(fullData.totalScoreChartImage())
                .pageSafetyChartImage(fullData.pageSafetyChartImage())
                .priorityDistributionChartImage(fullData.priorityDistributionChartImage())
                .pageTypeHeatmapChartImage(fullData.pageTypeHeatmapChartImage())
                .riskProfileRadarChartImage(fullData.riskProfileRadarChartImage())
                .deductionWaterfallChartImage(fullData.deductionWaterfallChartImage())
                .priorityBubbleChartImage(fullData.priorityBubbleChartImage())
                .riskProfileAxes(fullData.riskProfileAxes())
                .deductionBreakdown(fullData.deductionBreakdown())
                .priorityBubblePoints(fullData.priorityBubblePoints())
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

    private RiskItemViewDto toItemDto(
            AnalysisRiskItem item,
            Map<Long, Integer> sectionMarkers,
            Map<Long, EvidenceResolvedDto> evidenceByRiskId
    ) {
        int sectionSafetyScore = normalizeSafetyScore(item.getAnalysisSection().getRiskScore());
        Integer marker = sectionMarkers.get(item.getAnalysisSection().getSectionId());
        EvidenceResolvedDto evidence = evidenceByRiskId.get(item.getRiskId());
        String liabilityRef = evidence == null ? extractLiabilityReference(item) : evidence.reference();
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
                item.getReasoning(),
                liabilityRef,
                evidence == null ? "-" : evidence.sourceLabel(),
                evidence == null ? null : evidence.sourceUrl(),
                evidence == null ? null : evidence.quote()
        );
    }

    private Map<Long, EvidenceResolvedDto> buildEvidenceResolvedMap(List<AnalysisRiskItem> riskItems) {
        List<Long> riskIds = riskItems.stream()
                .map(AnalysisRiskItem::getRiskId)
                .filter(Objects::nonNull)
                .toList();
        if (riskIds.isEmpty()) {
            return Map.of();
        }

        List<AnalysisEvidence> evidences = analysisEvidenceRepository.findAllByAnalysisRiskItem_RiskIdIn(riskIds);
        if (evidences.isEmpty()) {
            return Map.of();
        }

        Map<Long, RegItem> regItemById = loadRegItems(evidences);
        Map<Long, AuditManualItem> manualItemById = loadManualItems(evidences);
        Map<Long, AuditItem> auditItemById = loadAuditItems(evidences);

        Map<Long, List<AnalysisEvidence>> evidenceByRisk = evidences.stream()
                .filter(e -> e.getAnalysisRiskItem() != null && e.getAnalysisRiskItem().getRiskId() != null)
                .collect(Collectors.groupingBy(e -> e.getAnalysisRiskItem().getRiskId()));

        Map<Long, EvidenceResolvedDto> resolved = new HashMap<>();
        for (Map.Entry<Long, List<AnalysisEvidence>> entry : evidenceByRisk.entrySet()) {
            AnalysisEvidence primary = entry.getValue().stream()
                    .sorted(Comparator.comparingInt((AnalysisEvidence e) -> evidencePriority(e.getSourceType())))
                    .findFirst()
                    .orElse(null);
            if (primary == null) {
                continue;
            }
            EvidenceResolvedDto dto = resolveEvidence(primary, regItemById, manualItemById, auditItemById);
            if (dto != null) {
                resolved.put(entry.getKey(), dto);
            }
        }
        return resolved;
    }

    private Map<Long, RegItem> loadRegItems(List<AnalysisEvidence> evidences) {
        List<Long> ids = evidences.stream()
                .filter(e -> e.getSourceType() == AnalysisEvidenceSourceType.REG_ITEM)
                .map(AnalysisEvidence::getSourceId)
                .map(this::parseLongOrNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return regItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RegItem::getItemId, item -> item));
    }

    private Map<Long, AuditManualItem> loadManualItems(List<AnalysisEvidence> evidences) {
        List<Long> ids = evidences.stream()
                .filter(e -> e.getSourceType() == AnalysisEvidenceSourceType.AUDIT_MANUAL_ITEM)
                .map(AnalysisEvidence::getSourceId)
                .map(this::parseLongOrNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return auditManualItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AuditManualItem::getManualItemId, item -> item));
    }

    private Map<Long, AuditItem> loadAuditItems(List<AnalysisEvidence> evidences) {
        List<Long> ids = evidences.stream()
                .filter(e -> e.getSourceType() == AnalysisEvidenceSourceType.AUDIT_ITEM)
                .map(AnalysisEvidence::getSourceId)
                .map(this::parseLongOrNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return auditItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AuditItem::getItemId, item -> item));
    }

    private EvidenceResolvedDto resolveEvidence(
            AnalysisEvidence evidence,
            Map<Long, RegItem> regItemById,
            Map<Long, AuditManualItem> manualItemById,
            Map<Long, AuditItem> auditItemById
    ) {
        Long sourceId = parseLongOrNull(evidence.getSourceId());
        if (sourceId == null) {
            return null;
        }
        return switch (evidence.getSourceType()) {
            case REG_ITEM -> {
                RegItem regItem = regItemById.get(sourceId);
                if (regItem == null || regItem.getRegulation() == null) {
                    yield null;
                }
                String ref = firstNonBlank(regItem.getClauseNumber(), regItem.getSectionTitle(), "규정 조항");
                yield new EvidenceResolvedDto(
                        "규정 조항",
                        ref,
                        regItem.getRegulation().getSourceUrl(),
                        truncateSummaryText(evidence.getQuote(), 120)
                );
            }
            case AUDIT_MANUAL_ITEM -> {
                AuditManualItem item = manualItemById.get(sourceId);
                if (item == null) {
                    yield null;
                }
                String ref = (item.getArticleName() != null ? item.getArticleName() : "매뉴얼")
                        + (item.getSectionNumber() != null ? " §" + item.getSectionNumber() : "");
                String sourceLabel = item.getAuditManual() != null
                        ? "감사 매뉴얼: " + item.getAuditManual().getTitle()
                        : "감사 매뉴얼";
                yield new EvidenceResolvedDto(
                        sourceLabel,
                        ref,
                        null,
                        truncateSummaryText(evidence.getQuote(), 120)
                );
            }
            case AUDIT_ITEM -> {
                AuditItem item = auditItemById.get(sourceId);
                if (item == null) {
                    yield null;
                }
                String ref = "유사사례 " + (item.getDocTitle() != null ? item.getDocTitle() : item.getDocId());
                yield new EvidenceResolvedDto(
                        "유사 감사 지적 사례",
                        ref,
                        item.getSourcePath(),
                        truncateSummaryText(evidence.getQuote(), 120)
                );
            }
        };
    }

    private int evidencePriority(AnalysisEvidenceSourceType sourceType) {
        if (sourceType == null) {
            return 99;
        }
        return switch (sourceType) {
            case REG_ITEM -> 0;
            case AUDIT_MANUAL_ITEM -> 1;
            case AUDIT_ITEM -> 2;
        };
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
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

    private String resolveActionPriorityLabelNullable(Integer safetyScore) {
        if (safetyScore == null) {
            return "미확정";
        }
        return resolveActionPriorityLabel(safetyScore);
    }

    private String resolveActionPriorityCssClassNullable(Integer safetyScore) {
        if (safetyScore == null) {
            return "unknown";
        }
        return resolveActionPriorityCssClass(safetyScore);
    }

    private String buildHeatCellStyle(int count, int maxCount) {
        if (count <= 0 || maxCount <= 0) {
            return "background:#f3f6fb;color:#667085;";
        }
        double ratio = Math.min(1.0, count / (double) maxCount);
        double alpha = 0.18 + (ratio * 0.72);
        String textColor = alpha >= 0.5 ? "#ffffff" : "#1e3a5f";
        return String.format(Locale.US, "background:rgba(30,136,229,%.2f);color:%s;", alpha, textColor);
    }

    private String extractLiabilityReference(AnalysisRiskItem item) {
        if (item == null) {
            return "-";
        }
        String source = firstNonBlank(item.getReasoning(), item.getGuideMessage(), item.getDetectedText());
        if (source == null) {
            return "-";
        }
        Matcher matcher = LIABILITY_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", "");
        }
        return "-";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String truncateSummaryText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private List<RiskProfileAxisDto> buildRiskProfileAxes(
            Map<AnalysisRiskType, Long> typeCounts,
            int totalRiskItems,
            int urgentCount,
            int dominantViolationPageCount,
            int violationCount
    ) {
        List<RiskProfileAxisDto> axes = new ArrayList<>();
        for (AnalysisRiskType type : AnalysisRiskType.values()) {
            int typeCount = typeCounts.getOrDefault(type, 0L).intValue();
            axes.add(new RiskProfileAxisDto(
                    type.getDescription(),
                    calculatePercent(typeCount, Math.max(totalRiskItems, 1))
            ));
        }
        axes.add(new RiskProfileAxisDto(
                "긴급 비율",
                calculatePercent(urgentCount, Math.max(totalRiskItems, 1))
        ));
        axes.add(new RiskProfileAxisDto(
                "페이지 편중도",
                calculatePercent(dominantViolationPageCount, Math.max(violationCount, 1))
        ));
        return axes;
    }

    private List<DeductionBreakdownDto> buildDeductionBreakdown(Map<AnalysisRiskType, Long> typeCounts, int totalScore) {
        int totalDeduction = Math.max(0, 100 - normalizeSafetyScore(totalScore));
        int totalTypeCount = typeCounts.values().stream().mapToInt(Long::intValue).sum();
        if (totalDeduction == 0 || totalTypeCount == 0) {
            return Arrays.stream(AnalysisRiskType.values())
                    .map(type -> new DeductionBreakdownDto(type.getDescription(), 0))
                    .toList();
        }

        Map<AnalysisRiskType, Integer> rounded = new EnumMap<>(AnalysisRiskType.class);
        int allocated = 0;
        AnalysisRiskType maxType = null;
        long maxCount = -1;
        for (AnalysisRiskType type : AnalysisRiskType.values()) {
            int count = typeCounts.getOrDefault(type, 0L).intValue();
            if (count > maxCount) {
                maxCount = count;
                maxType = type;
            }
            int value = (int) Math.round((count / (double) totalTypeCount) * totalDeduction);
            rounded.put(type, value);
            allocated += value;
        }

        int drift = totalDeduction - allocated;
        if (drift != 0 && maxType != null) {
            rounded.compute(maxType, (k, v) -> (v == null ? 0 : v) + drift);
        }

        return Arrays.stream(AnalysisRiskType.values())
                .map(type -> new DeductionBreakdownDto(
                        type.getDescription(),
                        -Math.max(0, rounded.getOrDefault(type, 0))
                ))
                .toList();
    }

    private String buildTotalScoreChartImage(int totalScore) {
        try {
            int safeScore = normalizeSafetyScore(totalScore);
            PieChart chart = new PieChartBuilder()
                    .width(520)
                    .height(280)
                    .title("총 안전점수")
                    .build();
            chart.getStyler().setLegendVisible(false);
            chart.getStyler().setPlotContentSize(0.7);
            chart.getStyler().setDonutThickness(0.45);
            chart.getStyler().setSeriesColors(new Color[]{
                    resolvePriorityColor(resolveActionPriorityCssClass(safeScore)),
                    new Color(229, 231, 235)
            });
            chart.addSeries("안전점수", safeScore);
            chart.addSeries("잔여", Math.max(0, 100 - safeScore));
            return toDataUriPng(chart);
        } catch (Exception e) {
            log.warn("Failed to render total score chart image", e);
            return null;
        }
    }

    private String buildPageSafetyChartImage(List<PageSafetyStatDto> pageSafetyStats) {
        try {
            if (pageSafetyStats == null || pageSafetyStats.isEmpty()) {
                return null;
            }
            List<String> xData = pageSafetyStats.stream()
                    .map(stat -> "P" + stat.pageNumber())
                    .toList();
            List<Integer> yData = pageSafetyStats.stream()
                    .map(stat -> stat.minSafetyScore() == null ? 0 : stat.minSafetyScore())
                    .toList();
            List<Integer> violationMarkerData = pageSafetyStats.stream()
                    .map(stat -> stat.hasViolation() ? 100 : 0)
                    .toList();

            CategoryChart chart = new CategoryChartBuilder()
                    .width(920)
                    .height(320)
                    .title("페이지별 최소 안전점수")
                    .xAxisTitle("페이지")
                    .yAxisTitle("안전점수")
                    .build();
            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setYAxisMin(0.0);
            chart.getStyler().setYAxisMax(105.0);
            chart.getStyler().setAvailableSpaceFill(0.8);
            chart.getStyler().setOverlapped(false);
            chart.getStyler().setSeriesColors(new Color[]{
                    new Color(59, 130, 246),
                    new Color(220, 38, 38)
            });

            chart.addSeries("최소 안전점수", xData, yData);
            chart.addSeries("위반 마커", xData, violationMarkerData);
            return toDataUriPng(chart);
        } catch (Exception e) {
            log.warn("Failed to render page safety chart image", e);
            return null;
        }
    }

    private String buildPriorityDistributionChartImage(List<PriorityStatDto> priorityStats) {
        try {
            if (priorityStats == null || priorityStats.isEmpty()) {
                return null;
            }
            PieChart chart = new PieChartBuilder()
                    .width(700)
                    .height(320)
                    .title("조치 우선순위 분포")
                    .build();
            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setDonutThickness(0.5);
            chart.getStyler().setSeriesColors(new Color[]{
                    resolvePriorityColor("urgent"),
                    resolvePriorityColor("high"),
                    resolvePriorityColor("medium"),
                    resolvePriorityColor("low")
            });

            for (PriorityStatDto stat : priorityStats) {
                if (stat.count() <= 0) {
                    continue;
                }
                chart.addSeries(stat.label(), stat.count());
            }
            return toDataUriPng(chart);
        } catch (Exception e) {
            log.warn("Failed to render priority distribution chart image", e);
            return null;
        }
    }

    private String buildDeductionWaterfallFallbackImage(int totalScore, List<DeductionBreakdownDto> deductionRows) {
        try {
            List<String> xData = new ArrayList<>();
            List<Integer> yData = new ArrayList<>();
            xData.add("기준");
            yData.add(100);
            for (DeductionBreakdownDto row : deductionRows) {
                xData.add(row.label());
                yData.add(row.delta());
            }
            xData.add("최종");
            yData.add(normalizeSafetyScore(totalScore));

            CategoryChart chart = new CategoryChartBuilder()
                    .width(900)
                    .height(360)
                    .title("총점 감점 요인")
                    .xAxisTitle("항목")
                    .yAxisTitle("점수/감점")
                    .build();
            chart.getStyler().setLegendVisible(false);
            chart.getStyler().setYAxisMin(-100.0);
            chart.getStyler().setYAxisMax(100.0);
            chart.getStyler().setAvailableSpaceFill(0.85);
            chart.getStyler().setSeriesColors(new Color[]{new Color(30, 136, 229)});

            chart.addSeries("감점", xData, yData);
            return toDataUriPng(chart);
        } catch (Exception e) {
            log.warn("Failed to render deduction waterfall fallback chart image", e);
            return null;
        }
    }

    private Color resolvePriorityColor(String cssClass) {
        return switch (cssClass) {
            case "urgent" -> new Color(220, 38, 38);
            case "high" -> new Color(249, 115, 22);
            case "medium" -> new Color(245, 158, 11);
            case "low" -> new Color(34, 197, 94);
            default -> new Color(148, 163, 184);
        };
    }

    private String toDataUriPng(Object chart) throws IOException {
        byte[] bytes;
        if (chart instanceof PieChart pieChart) {
            bytes = BitmapEncoder.getBitmapBytes(pieChart, BitmapEncoder.BitmapFormat.PNG);
        } else if (chart instanceof CategoryChart categoryChart) {
            bytes = BitmapEncoder.getBitmapBytes(categoryChart, BitmapEncoder.BitmapFormat.PNG);
        } else {
            return null;
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String ensureChartImage(String dataUri, String chartName) {
        if (dataUri != null && !dataUri.isBlank()) {
            return dataUri;
        }
        return buildChartUnavailableImage(chartName);
    }

    private String buildChartUnavailableImage(String chartName) {
        try {
            BufferedImage image = new BufferedImage(920, 320, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(248, 250, 252));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(new Color(203, 213, 225));
            g.drawRect(16, 16, image.getWidth() - 32, image.getHeight() - 32);
            g.setColor(new Color(71, 85, 105));
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.drawString(chartName, 32, 64);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.drawString("차트 데이터를 생성하지 못했습니다.", 32, 98);
            g.drawString("Node 렌더러/데이터 상태를 확인하세요.", 32, 124);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.warn("Failed to build fallback unavailable chart image for {}", chartName, e);
            return null;
        }
    }

    private int safeLength(String s) {
        return s == null ? 0 : s.length();
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
            List<PageSafetyStatDto> pageSafetyStats,
            List<PageTypeHeatmapRowDto> pageTypeHeatmapRows,
            String totalScoreChartImage,
            String pageSafetyChartImage,
            String priorityDistributionChartImage,
            String pageTypeHeatmapChartImage,
            String riskProfileRadarChartImage,
            String deductionWaterfallChartImage,
            String priorityBubbleChartImage,
            List<RiskProfileAxisDto> riskProfileAxes,
            List<DeductionBreakdownDto> deductionBreakdown,
            List<PriorityBubblePointDto> priorityBubblePoints,
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
            boolean violation,
            String riskTypeLabel,
            String keyReason,
            String liabilityRef,
            String liabilitySourceLabel,
            String liabilitySourceUrl
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

    public record PageSafetyStatDto(
            int pageNumber,
            Integer minSafetyScore,
            String actionPriorityLabel,
            String actionPriorityCssClass,
            int scoreBarPercent,
            boolean hasViolation,
            int violationCount,
            int sectionCount
    ) {}

    public record PageTypeHeatmapRowDto(
            int pageNumber,
            List<HeatmapCellDto> cells,
            int totalCount
    ) {}

    public record HeatmapCellDto(
            String riskTypeLabel,
            int count,
            String style
    ) {}

    public record RiskProfileAxisDto(
            String label,
            int value
    ) {}

    public record DeductionBreakdownDto(
            String label,
            int delta
    ) {}

    public record PriorityBubblePointDto(
            String label,
            int safetyScore,
            int riskItemCount,
            int bubbleSize,
            String priorityCssClass
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
            String reasoning,
            String liabilityRef,
            String liabilitySourceLabel,
            String liabilitySourceUrl,
            String evidenceQuote
    ) {}

    public record EvidenceResolvedDto(
            String sourceLabel,
            String reference,
            String sourceUrl,
            String quote
    ) {}
}
