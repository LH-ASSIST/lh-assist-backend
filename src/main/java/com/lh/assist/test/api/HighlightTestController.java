package com.lh.assist.test.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
                "절차",
                "MEDIUM",
                "계약 체결 후 선급금 30%를 지급하고, 나머지는 공정률에 따라 분할 지급한다.",
                "선급금 지급 조건이 구체적이지 않아 분쟁 소지가 있을 수 있습니다.",
                "선급금 지급 시기와 기준 공정률을 명확히 규정해야 합니다.",
                1,
                "[120, 310, 420, 340]",
                55,
                120,
                310,
                300,
                30
        ));
        sections.add(new MockSection(
                102L,
                "책임소재",
                "HIGH",
                "공사 완료 후 발생하는 하자에 대해서는 시공사가 책임지고 보수한다.",
                "하자담보책임 기간 및 범위가 '공사 완료 후'로만 명시되어 있어 불명확합니다.",
                "주택법 제46조에 따른 하자담보책임 기간을 명시하고, 보증금 예치 조항을 추가해야 합니다.",
                2,
                "[80, 220, 520, 260]",
                88,
                80,
                220,
                440,
                40
        ));
        sections.add(new MockSection(
                103L,
                "공정성",
                "LOW",
                "입찰 공고는 사업 착공 60일 전까지 공고한다.",
                "입찰 공고 기한은 통상 기준에 맞지만, 예외 조건이 명시되어 있지 않습니다.",
                "긴급 사업 예외 기준을 별도로 명시해 투명성을 확보해야 합니다.",
                3,
                "[100, 410, 520, 440]",
                20,
                100,
                410,
                420,
                30
        ));

        List<MockEvidence> evidences = List.of(
                new MockEvidence(201L, 101L, "REGULATION", "REG-2024-001", "대금은 60일 이내 지급이 원칙이다."),
                new MockEvidence(202L, 101L, "AUDIT", "AUD-2023-014", "장기 지급은 분쟁 가능성이 높다."),
                new MockEvidence(203L, 102L, "CASE", "CASE-2021-008", "손해배상 범위가 과도한 사례가 문제됨."),
                new MockEvidence(204L, 103L, "REGULATION", "REG-2022-004", "관할 합의는 가능하나 불균형 주의.")
        );

        List<MockHighlight> highlights = sections.stream()
                .map(section -> new MockHighlight(
                        section.sectionId(),
                        section.page(),
                        section.x(),
                        section.y(),
                        section.width(),
                        section.height(),
                        section.level(),
                        section.text(),
                        section.desc(),
                        section.recommendation(),
                        section.title(),
                        section.score(),
                        section.bbox()
                ))
                .toList();

        Map<Long, List<MockEvidence>> evidenceMap = evidences.stream()
                .collect(Collectors.groupingBy(MockEvidence::sectionId));

        model.addAttribute("highlightsJson", objectMapper.writeValueAsString(highlights));
        model.addAttribute("sectionsJson", objectMapper.writeValueAsString(sections));
        model.addAttribute("evidencesJson", objectMapper.writeValueAsString(evidenceMap));
        model.addAttribute("sections", sections);

        return "test/highlight-test";
    }

    private record MockHighlight(
            long sectionId,
            int page,
            double x,
            double y,
            double width,
            double height,
            String level,
            String text,
            String riskContent,
            String recommendation,
            String title,
            int score,
            String bbox
    ) {
    }

    private record MockSection(
            long sectionId,
            String title,
            String level,
            String text,
            String desc,
            String recommendation,
            int page,
            String bbox,
            int score,
            double x,
            double y,
            double width,
            double height
    ) {
    }

    private record MockEvidence(
            long evidenceId,
            long sectionId,
            String sourceType,
            String sourceId,
            String quote
    ) {
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