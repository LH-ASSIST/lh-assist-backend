package com.lh.assist.test.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReportPreviewTestController {

    private static final Pattern BBOX_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final int HIGH_RISK_SAFETY_MAX = 59;
    private static final int MEDIUM_RISK_SAFETY_MAX = 79;
    private static final int MEDIUM_PRIORITY_SAFETY_MAX = 89;
    private static final int TOP_ACTION_LIMIT = 5;

    private final ObjectMapper objectMapper;

    @GetMapping("/test/report-preview")
    public String preview(
            @RequestParam(name = "showCover", defaultValue = "true") boolean showCover,
            Model model
    ) throws IOException {
        JsonNode root = loadDummyJson();
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new IllegalStateException("templates/report/dumy.json data must be array");
        }

        List<SectionRaw> sections = new ArrayList<>();
        List<RiskItemRaw> riskItems = new ArrayList<>();

        for (JsonNode sectionNode : data) {
            SectionRaw section = new SectionRaw(
                    sectionNode.path("sectionId").asLong(),
                    sectionNode.path("page").isMissingNode() ? null : sectionNode.path("page").asInt(),
                    sectionNode.path("bbox").asText(null),
                    sectionNode.path("violation").asBoolean(false),
                    normalizeSafetyScore(sectionNode.path("riskScore").isNull() ? null : sectionNode.path("riskScore").asInt())
            );
            sections.add(section);

            JsonNode items = sectionNode.path("riskItems");
            if (!items.isArray()) {
                continue;
            }
            for (JsonNode itemNode : items) {
                riskItems.add(new RiskItemRaw(
                        itemNode.path("riskId").asLong(),
                        section.sectionId(),
                        section.pageNumber(),
                        section.safetyScore(),
                        itemNode.path("riskType").asText("-"),
                        itemNode.path("detectedText").asText(""),
                        itemNode.path("guideMessage").asText(""),
                        itemNode.path("reasoning").asText("")
                ));
            }
        }

        Map<Long, Integer> markerBySection = buildMarkers(sections);
        Map<Long, Integer> riskItemCountBySection = new HashMap<>();
        for (RiskItemRaw item : riskItems) {
            riskItemCountBySection.merge(item.sectionId(), 1, Integer::sum);
        }

        int totalSections = sections.size();
        int violationCount = (int) sections.stream().filter(SectionRaw::violation).count();

        int highRisk = 0;
        int mediumRisk = 0;
        int lowRisk = 0;
        for (SectionRaw section : sections) {
            if (section.safetyScore() <= HIGH_RISK_SAFETY_MAX) {
                highRisk++;
            } else if (section.safetyScore() <= MEDIUM_RISK_SAFETY_MAX) {
                mediumRisk++;
            } else {
                lowRisk++;
            }
        }

        int totalSafetyScore = sections.isEmpty()
                ? 0
                : (int) Math.round(sections.stream().mapToInt(SectionRaw::safetyScore).average().orElse(0));

        Map<String, Integer> riskTypeCount = new LinkedHashMap<>();
        for (RiskItemRaw item : riskItems) {
            riskTypeCount.merge(riskTypeLabel(item.riskType()), 1, Integer::sum);
        }
        List<Map<String, Object>> riskTypeStats = buildRiskTypeStats(riskTypeCount, riskItems.size());
        List<Map<String, Object>> riskTypeDonut = buildRiskTypeDonut(riskTypeCount, riskItems.size());

        Map<Integer, Integer> pageCount = new HashMap<>();
        for (SectionRaw section : sections) {
            if (section.violation() && section.pageNumber() != null) {
                pageCount.merge(section.pageNumber(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> pageStats = buildPageStats(pageCount, violationCount);
        Map<Integer, List<SectionRaw>> sectionsByPage = new HashMap<>();
        for (SectionRaw section : sections) {
            if (section.pageNumber() == null) {
                continue;
            }
            sectionsByPage.computeIfAbsent(section.pageNumber(), ignored -> new ArrayList<>()).add(section);
        }

        int totalPageCount = (int) sections.stream()
                .map(SectionRaw::pageNumber)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        int violationPageCount = pageCount.size();
        int violationRatePercent = percent(violationCount, totalSections);
        int pageCoveragePercent = percent(violationPageCount, totalPageCount);
        int dominantViolationPage = pageCount.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(0);
        int dominantViolationPageCount = pageCount.getOrDefault(dominantViolationPage, 0);

        Map<String, Integer> priorityBucket = new LinkedHashMap<>();
        priorityBucket.put("urgent", 0);
        priorityBucket.put("high", 0);
        priorityBucket.put("medium", 0);
        priorityBucket.put("low", 0);
        for (RiskItemRaw item : riskItems) {
            String cssClass = actionPriorityCssClass(item.sectionSafetyScore());
            priorityBucket.compute(cssClass, (k, v) -> v == null ? 1 : v + 1);
        }
        List<Map<String, Object>> priorityStats = List.of(
                priorityRow("긴급", "urgent", priorityBucket.get("urgent"), riskItems.size()),
                priorityRow("높음", "high", priorityBucket.get("high"), riskItems.size()),
                priorityRow("중간", "medium", priorityBucket.get("medium"), riskItems.size()),
                priorityRow("낮음", "low", priorityBucket.get("low"), riskItems.size())
        );

        List<Map<String, Object>> typePriorityMatrix = buildTypePriorityMatrix(riskItems);
        List<Map<String, Object>> pageRiskProfiles = buildPageRiskProfiles(sectionsByPage);

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

        Comparator<SectionRaw> topActionOrder = Comparator
                .comparingInt(SectionRaw::safetyScore)
                .thenComparing(section -> !section.violation())
                .thenComparing((SectionRaw section) -> riskItemCountBySection.getOrDefault(section.sectionId(), 0), Comparator.reverseOrder())
                .thenComparing(section -> section.pageNumber() != null ? section.pageNumber() : Integer.MAX_VALUE)
                .thenComparing(SectionRaw::sectionId);

        List<Map<String, Object>> topActions = sections.stream()
                .filter(section -> section.pageNumber() != null)
                .sorted(topActionOrder)
                .limit(TOP_ACTION_LIMIT)
                .map(section -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("marker", markerBySection.get(section.sectionId()));
                    row.put("pageNumber", section.pageNumber());
                    row.put("sectionSafetyScore", section.safetyScore());
                    row.put("actionPriorityLabel", actionPriorityLabel(section.safetyScore()));
                    row.put("actionPriorityCssClass", actionPriorityCssClass(section.safetyScore()));
                    row.put("riskItemCount", riskItemCountBySection.getOrDefault(section.sectionId(), 0));
                    row.put("violation", section.violation());
                    return row;
                })
                .toList();

        Map<Integer, List<Map<String, Object>>> itemsByPage = new LinkedHashMap<>();
        riskItems.stream()
                .filter(item -> item.pageNumber() != null)
                .sorted(Comparator
                        .comparing(RiskItemRaw::pageNumber)
                        .thenComparingInt(RiskItemRaw::sectionSafetyScore)
                        .thenComparingLong(RiskItemRaw::sectionId)
                        .thenComparingLong(RiskItemRaw::riskId))
                .forEach(item -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("marker", markerBySection.get(item.sectionId()));
                    row.put("riskTypeLabel", riskTypeLabel(item.riskType()));
                    row.put("cssClass", riskTypeCssClass(item.riskType()));
                    row.put("pageNumber", item.pageNumber());
                    row.put("actionPriorityLabel", actionPriorityLabel(item.sectionSafetyScore()));
                    row.put("actionPriorityCssClass", actionPriorityCssClass(item.sectionSafetyScore()));
                    row.put("sectionSafetyScore", item.sectionSafetyScore());
                    row.put("detectedText", item.detectedText());
                    row.put("guideMessage", item.guideMessage());
                    row.put("reasoning", item.reasoning());
                    itemsByPage.computeIfAbsent(item.pageNumber(), ignored -> new ArrayList<>()).add(row);
                });

        Map<String, Object> viewData = new HashMap<>();
        viewData.put("title", "dumy.json 미리보기");
        viewData.put("analyzedDate", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        viewData.put("totalScore", totalSafetyScore);
        viewData.put("violationCount", violationCount);
        viewData.put("actionPriorityLabel", actionPriorityLabel(totalSafetyScore));
        viewData.put("actionPriorityCssClass", actionPriorityCssClass(totalSafetyScore));
        viewData.put("gaugeAngle", -90.0 + (Math.min(100, Math.max(0, totalSafetyScore)) / 100.0) * 180.0);
        viewData.put("gaugeFill", (Math.min(100, Math.max(0, totalSafetyScore)) / 100.0) * 126.0);
        viewData.put("highRiskSectionCount", highRisk);
        viewData.put("mediumRiskSectionCount", mediumRisk);
        viewData.put("lowRiskSectionCount", lowRisk);
        viewData.put("totalSectionCount", totalSections);
        viewData.put("highRiskSectionPercent", percent(highRisk, totalSections));
        viewData.put("mediumRiskSectionPercent", percent(mediumRisk, totalSections));
        viewData.put("lowRiskSectionPercent", percent(lowRisk, totalSections));
        viewData.put("riskTypeStats", riskTypeStats);
        viewData.put("riskTypeDonut", riskTypeDonut);
        viewData.put("pageStats", pageStats);
        viewData.put("topActions", topActions);
        viewData.put("averageSectionSafetyScore", totalSafetyScore);
        viewData.put("violationRatePercent", violationRatePercent);
        viewData.put("totalPageCount", totalPageCount);
        viewData.put("violationPageCount", violationPageCount);
        viewData.put("pageCoveragePercent", pageCoveragePercent);
        viewData.put("dominantViolationPage", dominantViolationPage);
        viewData.put("dominantViolationPageCount", dominantViolationPageCount);
        viewData.put("priorityStats", priorityStats);
        viewData.put("typePriorityMatrix", typePriorityMatrix);
        viewData.put("pageRiskProfiles", pageRiskProfiles);
        viewData.put("keyInsights", keyInsights);
        viewData.put("itemsByPage", itemsByPage);

        model.addAttribute("data", viewData);
        model.addAttribute("showCover", showCover);
        return "report/summary";
    }

    private JsonNode loadDummyJson() throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/report/dumy.json");
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }

    private List<Map<String, Object>> buildPageStats(Map<Integer, Integer> pageCount, int violationCount) {
        int maxPageCount = pageCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int barMaxHeightPx = 70;
        return pageCount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int barPercent = maxPageCount == 0 ? 0 : (int) Math.round((entry.getValue() * 100.0) / maxPageCount);
                    int barHeightPx = (int) Math.round((barPercent / 100.0) * barMaxHeightPx);
                    if (entry.getValue() > 0 && barHeightPx == 0) {
                        barHeightPx = 4;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("pageNumber", entry.getKey());
                    row.put("count", entry.getValue());
                    row.put("barPercent", percent(entry.getValue(), violationCount));
                    row.put("barHeightPx", barHeightPx);
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRiskTypeStats(Map<String, Integer> riskTypeCount, int totalRiskItems) {
        int maxTypeCount = riskTypeCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return riskTypeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> {
                    int barPercent = maxTypeCount == 0 ? 0 : (int) Math.round((entry.getValue() * 100.0) / maxTypeCount);
                    if (entry.getValue() > 0 && barPercent == 0) {
                        barPercent = 1;
                    }
                    String color = riskTypeColor(entry.getKey());
                    Map<String, Object> row = new HashMap<>();
                    row.put("label", entry.getKey());
                    row.put("count", entry.getValue());
                    row.put("percent", percent(entry.getValue(), totalRiskItems));
                    row.put("barPercent", barPercent);
                    row.put("color", color);
                    row.put("widthStyle", String.format(Locale.US, "width: %d%%; background: %s", barPercent, color));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRiskTypeDonut(Map<String, Integer> riskTypeCount, int totalRiskItems) {
        List<Map.Entry<String, Integer>> entries = riskTypeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        double cumulative = 0.0;
        double donutGap = 1.8;
        double minVisible = 0.6;
        for (Map.Entry<String, Integer> entry : entries) {
            double rawPercent = totalRiskItems == 0 ? 0.0 : (entry.getValue() * 100.0) / totalRiskItems;
            double visiblePercent = rawPercent <= 0.0 ? 0.0 : Math.max(minVisible, rawPercent - donutGap);
            Map<String, Object> row = new HashMap<>();
            row.put("label", entry.getKey());
            row.put("count", entry.getValue());
            row.put("percent", rawPercent);
            row.put("color", riskTypeColor(entry.getKey()));
            row.put("dashArray", String.format(Locale.US, "%.2f %.2f", visiblePercent, 100.0 - visiblePercent));
            row.put("dashOffset", String.format(Locale.US, "%.2f", 25.0 - cumulative));
            rows.add(row);
            cumulative += rawPercent;
        }
        return rows;
    }

    private List<Map<String, Object>> buildTypePriorityMatrix(List<RiskItemRaw> riskItems) {
        List<String> typeOrder = List.of("누락", "적정성", "명확성", "절차준수");
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        for (String type : typeOrder) {
            Map<String, Integer> row = new HashMap<>();
            row.put("urgent", 0);
            row.put("high", 0);
            row.put("medium", 0);
            row.put("low", 0);
            matrix.put(type, row);
        }

        for (RiskItemRaw item : riskItems) {
            String label = riskTypeLabel(item.riskType());
            if (!matrix.containsKey(label)) {
                Map<String, Integer> row = new HashMap<>();
                row.put("urgent", 0);
                row.put("high", 0);
                row.put("medium", 0);
                row.put("low", 0);
                matrix.put(label, row);
            }
            String priority = actionPriorityCssClass(item.sectionSafetyScore());
            Map<String, Integer> row = matrix.get(label);
            row.compute(priority, (k, v) -> v == null ? 1 : v + 1);
        }

        return matrix.entrySet().stream()
                .map(entry -> {
                    Map<String, Integer> row = entry.getValue();
                    int urgent = row.getOrDefault("urgent", 0);
                    int high = row.getOrDefault("high", 0);
                    int medium = row.getOrDefault("medium", 0);
                    int low = row.getOrDefault("low", 0);
                    int total = urgent + high + medium + low;

                    Map<String, Object> map = new HashMap<>();
                    map.put("label", entry.getKey());
                    map.put("color", riskTypeColor(entry.getKey()));
                    map.put("urgentCount", urgent);
                    map.put("highCount", high);
                    map.put("mediumCount", medium);
                    map.put("lowCount", low);
                    map.put("totalCount", total);
                    return map;
                })
                .filter(row -> ((Integer) row.get("totalCount")) > 0)
                .toList();
    }

    private List<Map<String, Object>> buildPageRiskProfiles(Map<Integer, List<SectionRaw>> sectionsByPage) {
        return sectionsByPage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<SectionRaw> pageSections = entry.getValue();
                    int pageTotal = pageSections.size();
                    int pageViolations = (int) pageSections.stream().filter(SectionRaw::violation).count();
                    int pageAverageSafety = (int) Math.round(pageSections.stream()
                            .mapToInt(SectionRaw::safetyScore)
                            .average()
                            .orElse(0.0));

                    int urgent = 0;
                    int high = 0;
                    int medium = 0;
                    int low = 0;
                    for (SectionRaw section : pageSections) {
                        String priority = actionPriorityCssClass(section.safetyScore());
                        switch (priority) {
                            case "urgent" -> urgent++;
                            case "high" -> high++;
                            case "medium" -> medium++;
                            default -> low++;
                        }
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("pageNumber", entry.getKey());
                    map.put("violationCount", pageViolations);
                    map.put("totalSectionCount", pageTotal);
                    map.put("averageSafetyScore", pageAverageSafety);
                    map.put("urgentCount", urgent);
                    map.put("highCount", high);
                    map.put("mediumCount", medium);
                    map.put("lowCount", low);
                    map.put("urgentPercent", percent(urgent, pageTotal));
                    map.put("highPercent", percent(high, pageTotal));
                    map.put("mediumPercent", percent(medium, pageTotal));
                    map.put("lowPercent", percent(low, pageTotal));
                    return map;
                })
                .toList();
    }

    private Map<String, Object> priorityRow(String label, String cssClass, Integer count, int total) {
        int normalizedCount = count == null ? 0 : count;
        Map<String, Object> row = new HashMap<>();
        row.put("label", label);
        row.put("cssClass", cssClass);
        row.put("count", normalizedCount);
        row.put("percent", percent(normalizedCount, total));
        return row;
    }

    private Map<Long, Integer> buildMarkers(List<SectionRaw> sections) {
        Map<Integer, List<SectionRaw>> byPage = new LinkedHashMap<>();
        for (SectionRaw section : sections) {
            if (!section.violation() || section.pageNumber() == null || section.bbox() == null) {
                continue;
            }
            byPage.computeIfAbsent(section.pageNumber(), ignored -> new ArrayList<>()).add(section);
        }

        Map<Long, Integer> markerBySectionId = new HashMap<>();
        for (Map.Entry<Integer, List<SectionRaw>> entry : byPage.entrySet()) {
            entry.getValue().sort(Comparator
                    .comparingDouble((SectionRaw section) -> {
                        double[] bbox = parseBbox(section.bbox());
                        return bbox == null ? 0d : bbox[1] + bbox[3];
                    })
                    .reversed()
                    .thenComparingLong(SectionRaw::sectionId));

            int idx = 1;
            for (SectionRaw section : entry.getValue()) {
                markerBySectionId.put(section.sectionId(), idx++);
            }
        }
        return markerBySectionId;
    }

    private double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        Matcher matcher = BBOX_NUMBER.matcher(bbox);
        List<Double> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }
        if (numbers.size() < 4) {
            return null;
        }
        double x = numbers.get(0);
        double y = numbers.get(1);
        double w = numbers.get(2);
        double h = numbers.get(3);
        if (w > x && h > y) {
            w = w - x;
            h = h - y;
        }
        return new double[]{x, y, w, h};
    }

    private int normalizeSafetyScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private int percent(int count, int total) {
        return total == 0 ? 0 : (int) Math.round((count * 100.0) / total);
    }

    private String actionPriorityLabel(int safetyScore) {
        if (safetyScore <= HIGH_RISK_SAFETY_MAX) {
            return "긴급";
        }
        if (safetyScore <= MEDIUM_RISK_SAFETY_MAX) {
            return "높음";
        }
        if (safetyScore <= MEDIUM_PRIORITY_SAFETY_MAX) {
            return "중간";
        }
        return "낮음";
    }

    private String actionPriorityCssClass(int safetyScore) {
        if (safetyScore <= HIGH_RISK_SAFETY_MAX) {
            return "urgent";
        }
        if (safetyScore <= MEDIUM_RISK_SAFETY_MAX) {
            return "high";
        }
        if (safetyScore <= MEDIUM_PRIORITY_SAFETY_MAX) {
            return "medium";
        }
        return "low";
    }

    private String riskTypeLabel(String type) {
        return switch (type) {
            case "MISSING" -> "누락";
            case "APPROPRIATENESS" -> "적정성";
            case "CLARITY" -> "명확성";
            case "PROCEDURE_COMPLIANCE" -> "절차준수";
            default -> type;
        };
    }

    private String riskTypeCssClass(String type) {
        return switch (type) {
            case "MISSING" -> "missing";
            case "APPROPRIATENESS" -> "appropriateness";
            case "CLARITY" -> "clarity";
            case "PROCEDURE_COMPLIANCE" -> "procedure";
            default -> "missing";
        };
    }

    private String riskTypeColor(String label) {
        return switch (label) {
            case "누락" -> "#D32F2F";
            case "적정성" -> "#F57C00";
            case "명확성" -> "#388E3C";
            case "절차준수" -> "#1976D2";
            default -> "#607D8B";
        };
    }

    private record SectionRaw(
            long sectionId,
            Integer pageNumber,
            String bbox,
            boolean violation,
            int safetyScore
    ) {
    }

    private record RiskItemRaw(
            long riskId,
            long sectionId,
            Integer pageNumber,
            int sectionSafetyScore,
            String riskType,
            String detectedText,
            String guideMessage,
            String reasoning
    ) {
    }
}
