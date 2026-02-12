package com.lh.assist.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportChartRenderer {

    private static final Duration NODE_RENDER_TIMEOUT = Duration.ofSeconds(8);
    private static final String SCRIPT_PATH = "scripts/report-chart-renderer.cjs";

    private final ObjectMapper objectMapper;

    public Optional<String> renderTotalScoreChart(int totalScore, String actionPriorityLabel) {
        return render("totalScore", Map.of(
                "score", totalScore,
                "label", actionPriorityLabel
        ), 520, 280);
    }

    public Optional<String> renderPageSafetyChart(List<?> pageSafetyStats) {
        return render("pageSafety", Map.of(
                "rows", pageSafetyStats
        ), 920, 320);
    }

    public Optional<String> renderPriorityDistributionChart(List<?> priorityStats) {
        return render("priorityDistribution", Map.of(
                "rows", priorityStats
        ), 700, 320);
    }

    public Optional<String> renderPageTypeHeatmapChart(List<?> pageTypeHeatmapRows) {
        return render("pageTypeHeatmap", Map.of(
                "rows", pageTypeHeatmapRows
        ), 900, 360);
    }

    public Optional<String> renderRiskProfileRadarChart(List<?> profileRows) {
        return render("riskProfileRadar", Map.of(
                "rows", profileRows
        ), 760, 360);
    }

    public Optional<String> renderDeductionWaterfallChart(int totalScore, List<?> deductionRows) {
        return render("deductionWaterfall", Map.of(
                "totalScore", totalScore,
                "rows", deductionRows
        ), 900, 360);
    }

    public Optional<String> renderPriorityBubbleChart(List<?> bubbleRows) {
        return render("priorityBubble", Map.of(
                "rows", bubbleRows
        ), 920, 360);
    }

    private Optional<String> render(String kind, Map<String, Object> data, int width, int height) {
        Process process = null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kind", kind);
            payload.put("width", width);
            payload.put("height", height);
            payload.put("data", data);
            byte[] jsonPayload = objectMapper.writeValueAsBytes(payload);

            process = new ProcessBuilder("node", SCRIPT_PATH)
                    .redirectErrorStream(false)
                    .start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(jsonPayload);
            }

            boolean finished = process.waitFor(NODE_RENDER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Chart render timed out for kind={}", kind);
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                String stderr = readAll(process.getErrorStream());
                log.warn("Chart render failed for kind={}, exit={}, stderr={}", kind, process.exitValue(), stderr);
                return Optional.empty();
            }

            String out = readAll(process.getInputStream()).trim();
            if (out.startsWith("data:image/png")) {
                return Optional.of(out);
            }
            // PDF 렌더 경로(openhtmltopdf)에서 SVG data URI는 Batik 호환 이슈가 있어
            // 여기서는 사용하지 않고 호출부의 PNG fallback(XChart)로 넘긴다.
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Node chart renderer unavailable for kind={} (node/script/dependency issue)", kind, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Chart render interrupted for kind={}", kind, e);
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String readAll(java.io.InputStream in) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

}
