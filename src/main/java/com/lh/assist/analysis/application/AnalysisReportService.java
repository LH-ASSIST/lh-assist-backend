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
    private static final int SAFE_SCORE_MAX = 30;
    private static final int MEDIUM_SCORE_MAX = 50;

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

        // 1. 리스크 분포 (안전/주의/위험)
        int safe = 0, medium = 0, high = 0;
        for (AnalysisSection s : sections) {
            if (s.getRiskScore() == null) continue;
            if (s.getRiskScore() <= SAFE_SCORE_MAX) safe++;
            else if (s.getRiskScore() <= MEDIUM_SCORE_MAX) medium++;
            else high++;
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
        for (Map.Entry<AnalysisRiskType, Long> e : donutEntries) {
            double percent = totalTypeCount == 0 ? 0.0 : (e.getValue() * 100.0) / totalTypeCount;
            String colorCode = resolveRiskColorCode(e.getKey());
            String dashArray = String.format(Locale.US, "%.2f %.2f", percent, 100.0 - percent);
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
        Map<Integer, Long> pageCounts = sections.stream()
                .filter(AnalysisSection::isViolation)
                .collect(Collectors.groupingBy(AnalysisSection::getPageNumber, Collectors.counting()));

        int maxPageCount = pageCounts.values().stream()
                .mapToInt(Long::intValue)
                .max()
                .orElse(0);
        int barMaxHeightPx = 60;

        List<PageStatDto> pageStats = pageCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    int percent = calculatePercent(e.getValue().intValue(), violationCount);
                    int barPercent = maxPageCount == 0 ? 0
                            : (int) Math.round((e.getValue().doubleValue() / maxPageCount) * 100);
                    int barHeightPx = (int) Math.round((barPercent / 100.0) * barMaxHeightPx);
                    return new PageStatDto(e.getKey(), e.getValue().intValue(), barPercent, barHeightPx);
                })
                .toList();

        // 4. 상세 내역 (페이지별 그룹핑)
        Map<Integer, List<RiskItemViewDto>> itemsByPage = riskItems.stream()
                .sorted(Comparator.comparing((AnalysisRiskItem i) -> i.getAnalysisSection().getPageNumber())
                        .thenComparing(AnalysisRiskItem::getPriority))
                .collect(Collectors.groupingBy(
                        item -> item.getAnalysisSection().getPageNumber(),
                        LinkedHashMap::new,
                        Collectors.mapping(item -> toItemDto(item, sectionMarkers), Collectors.toList())
                ));

        String rawTitle = document.getTitle() != null ? document.getTitle() : "";
        String normalizedTitle = Normalizer.normalize(rawTitle, Normalizer.Form.NFKC);
        String titleNoExt = normalizedTitle.replaceFirst("\\.[^.]+$", "");

        return ReportViewDto.builder()
                .title(titleNoExt)
                .analyzedDate(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                .totalScore(score)
                .violationCount(violationCount)
                .riskLevel(getRiskLevel(score))
                .gaugeAngle(-90.0 + (Math.min(100, Math.max(0, score)) / 100.0) * 180.0)
                .safePercent(calculatePercent(safe, totalSections))
                .mediumPercent(calculatePercent(medium, totalSections))
                .highPercent(calculatePercent(high, totalSections))
                .riskTypeStats(typeStats)
                .riskTypeDonut(donutStats)
                .pageStats(pageStats)
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
                .riskLevel(fullData.riskLevel())
                .gaugeAngle(fullData.gaugeAngle())
                .safePercent(fullData.safePercent())
                .mediumPercent(fullData.mediumPercent())
                .highPercent(fullData.highPercent())
                .riskTypeStats(fullData.riskTypeStats())
                .riskTypeDonut(fullData.riskTypeDonut())
                .pageStats(fullData.pageStats())
                .itemsByPage(filtered)
                .build();
    }

    private RiskItemViewDto toItemDto(AnalysisRiskItem item, Map<Long, Integer> sectionMarkers) {
        Integer marker = sectionMarkers.get(item.getAnalysisSection().getSectionId());
        return new RiskItemViewDto(
                marker,
                item.getRiskType().getDescription(),
                resolveCssClass(item.getRiskType()), // 직접 매핑 함수 호출
                item.getAnalysisSection().getPageNumber(),
                item.getPriority(),
                item.getAnalysisSection().getRiskScore(),
                item.getDetectedText(),
                item.getGuideMessage(),
                item.getReasoning()
        );
    }

    // Enum 변경 없이 색상/클래스 처리하는 헬퍼 메서드
    private String resolveRiskColorCode(AnalysisRiskType type) {
        return switch (type) {
            case MISSING -> "#E53935";
            case APPROPRIATENESS -> "#FB8C00";
            case CLARITY -> "#7CB342";
            case PROCEDURE_COMPLIANCE -> "#1E88E5";
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
        if (score <= SAFE_SCORE_MAX) return COLOR_LOW;
        if (score <= MEDIUM_SCORE_MAX) return COLOR_MEDIUM;
        return COLOR_HIGH;
    }

    private String getRiskLevel(int score) {
        if (score <= SAFE_SCORE_MAX) return "LOW";
        if (score <= MEDIUM_SCORE_MAX) return "MEDIUM";
        return "HIGH";
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
            String riskLevel,
            double gaugeAngle,
            int safePercent,
            int mediumPercent,
            int highPercent,
            List<RiskTypeStatDto> riskTypeStats,
            List<RiskTypeDonutDto> riskTypeDonut,
            List<PageStatDto> pageStats,
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

    public record RiskItemViewDto(
            Integer marker,
            String riskTypeLabel,
            String cssClass,
            Integer pageNumber,
            Integer priority,
            Integer riskScore,
            String detectedText,
            String guideMessage,
            String reasoning
    ) {}
}
