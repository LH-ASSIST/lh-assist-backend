package com.lh.assist.test.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HighlightTestController {

    private final ObjectMapper objectMapper;

    @GetMapping("/test/highlight")
    public String highlightPage(Model model) throws JsonProcessingException {
        String riskLevelCode = "MEDIUM";
        model.addAttribute("documentTitle", "공공주택 사업계획서_2024.pdf");
        model.addAttribute("documentType", "사업계획서");
        model.addAttribute("uploadedAt", "2024-01-05");
        model.addAttribute("analysisStatus", "분석 완료");
        model.addAttribute("riskLevelCode", riskLevelCode);
        model.addAttribute("riskLevel", toRiskLevelLabel(riskLevelCode));
        model.addAttribute("riskLevelClass", toRiskLevelClass(riskLevelCode));
        model.addAttribute("approvalStatus", "승인");
        model.addAttribute("reviewerName", "박차장");
        model.addAttribute("reviewerTitle", "차장");
        model.addAttribute("reviewerDept", "규정준수팀");
        model.addAttribute("reviewedAt", "2024-01-06 15:30:00");
        model.addAttribute("reviewComment",
                "하자담보책임 조항을 주택법 제46조에 맞게 수정 완료 확인했습니다. 승인합니다. " +
                "다만 향후 유사 사업계획서 작성 시 선급금 지급 조건도 보다 구체적으로 명시해주시기 바랍니다."
        );

        List<MockSection> sections = new ArrayList<>();
        sections.add(new MockSection(
                101L,
                1,
                "[120, 310, 420, 340]",
                true,
                55,
                "선급금 지급 조건이 불명확하여 분쟁 위험이 있습니다.",
                List.of(
                        new MockRiskItem(
                                201L,
                                "MISSING",
                                "선급금 30%를 지급한다.",
                                "선급금 지급 기준과 시기를 구체적으로 명시하세요.",
                                2,
                                "유사 계약에서 선급금 지급 시기가 명확하지 않아 분쟁 발생.",
                                "선급금 지급 기준이 누락됨."
                        ),
                        new MockRiskItem(
                                202L,
                                "CLARITY",
                                "나머지는 공정률에 따라 분할 지급한다.",
                                "공정률 산정 기준을 명시하세요.",
                                3,
                                null,
                                "공정률 기준이 모호함."
                        )
                )
        ));
        sections.add(new MockSection(
                102L,
                2,
                "[80, 220, 520, 260]",
                true,
                88,
                "하자담보책임 기간 및 범위가 불명확합니다.",
                List.of(
                        new MockRiskItem(
                                203L,
                                "APPROPRIATENESS",
                                "공사 완료 후 발생하는 하자에 대해서는 시공사가 책임지고 보수한다.",
                                "주택법 제46조 기준에 맞는 하자담보책임 기간을 명시하세요.",
                                1,
                                "하자담보책임 기간이 누락되어 책임 분쟁이 발생한 사례.",
                                "책임 범위가 광범위하고 기준이 모호함."
                        )
                )
        ));
        sections.add(new MockSection(
                103L,
                3,
                "[100, 410, 520, 440]",
                false,
                20,
                "입찰 공고 기한은 적정하지만 예외 조건이 없습니다.",
                List.of(
                        new MockRiskItem(
                                204L,
                                "PROCEDURE_COMPLIANCE",
                                "입찰 공고는 사업 착공 60일 전까지 공고한다.",
                                "긴급 사업 예외 기준을 별도로 명시해 절차 준수 여부를 명확히 하세요.",
                                4,
                                null,
                                "예외 절차 기준이 누락됨."
                        )
                )
        ));

        List<MockHighlight> highlights = sections.stream()
                .map(this::toHighlight)
                .toList();

        model.addAttribute("highlightsJson", objectMapper.writeValueAsString(highlights));
        model.addAttribute("sectionsJson", objectMapper.writeValueAsString(sections));

        return "test/highlight-test";
    }

    private record MockSection(
            long sectionId,
            int page,
            String bbox,
            boolean isViolation,
            int riskScore,
            String reasoning,
            List<MockRiskItem> riskItems
    ) {
    }

    private record MockRiskItem(
            long riskId,
            String riskType,
            String detectedText,
            String guideMessage,
            int priority,
            String similarCaseContent,
            String reasoning
    ) {
    }

    private record MockHighlight(
            long sectionId,
            int page,
            double x,
            double y,
            double width,
            double height
    ) {
    }

    private MockHighlight toHighlight(MockSection section) {
        double[] bbox = parseBbox(section.bbox());
        return new MockHighlight(
                section.sectionId(),
                section.page(),
                bbox[0],
                bbox[1],
                bbox[2],
                bbox[3]
        );
    }

    private double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return new double[]{0, 0, 0, 0};
        }
        String cleaned = bbox.replace("[", "").replace("]", "");
        String[] parts = cleaned.split(",");
        if (parts.length < 4) {
            return new double[]{0, 0, 0, 0};
        }
        double x1 = Double.parseDouble(parts[0].trim());
        double y1 = Double.parseDouble(parts[1].trim());
        double x2 = Double.parseDouble(parts[2].trim());
        double y2 = Double.parseDouble(parts[3].trim());
        return new double[]{x1, y1, x2 - x1, y2 - y1};
    }

    private String toRiskLevelLabel(String code) {
        return switch (code) {
            case "HIGH" -> "높음";
            case "LOW" -> "낮음";
            default -> "중간";
        };
    }

    private String toRiskLevelClass(String code) {
        return switch (code) {
            case "HIGH" -> "high";
            case "LOW" -> "low";
            default -> "medium";
        };
    }
}