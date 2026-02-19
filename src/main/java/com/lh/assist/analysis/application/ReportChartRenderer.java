package com.lh.assist.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportChartRenderer {

    private static final Duration NODE_RENDER_TIMEOUT = Duration.ofSeconds(30);
    private static final String SCRIPT_PATH = "scripts/report-chart-renderer.cjs";
    private static final Path RESVG_MODULE_MANIFEST = Path.of("node_modules", "@resvg", "resvg-js", "package.json");

    private final ObjectMapper objectMapper;
    private volatile boolean nodeRendererAvailable = true;
    private volatile boolean missingDependencyWarned = false;

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

    public Optional<String> renderRiskTypeChart(List<?> riskTypeStats) {
        return render("riskTypeBar", Map.of(
                "rows", riskTypeStats
        ), 640, 420);
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
        if (!isNodeRendererAvailable()) {
            return Optional.empty();
        }

        Process process = null;
        ExecutorService ioExecutor = null;
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

            InputStream stdoutStream = process.getInputStream();
            InputStream stderrStream = process.getErrorStream();
            ioExecutor = Executors.newFixedThreadPool(2);
            Future<String> stdoutFuture = ioExecutor.submit(() -> readAll(stdoutStream));
            Future<String> stderrFuture = ioExecutor.submit(() -> readAll(stderrStream));

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(jsonPayload);
            }

            boolean finished = process.waitFor(NODE_RENDER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Chart render timed out for kind={}", kind);
                return Optional.empty();
            }

            String stdout = getStreamOutput(stdoutFuture);
            String stderr = getStreamOutput(stderrFuture);

            if (process.exitValue() != 0) {
                log.warn("Chart render failed for kind={}, exit={}, stderr={}", kind, process.exitValue(), stderr);
                return Optional.empty();
            }

            String out = stdout
                    .replaceAll("[\\r\\n\\t ]", "")
                    .trim();
            if (out.startsWith("data:image/png")) {
                return Optional.of(out);
            }
            if (!stderr.isBlank()) {
                log.debug("Chart renderer stderr for kind={}: {}", kind, stderr);
            }
            log.warn("Chart render returned unexpected format for kind={}, prefix={}",
                    kind, out.length() > 24 ? out.substring(0, 24) : out);
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Node chart renderer unavailable for kind={} (node/script/dependency issue)", kind, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Chart render interrupted for kind={}", kind, e);
            return Optional.empty();
        } finally {
            if (ioExecutor != null) {
                ioExecutor.shutdownNow();
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String getStreamOutput(Future<String> streamFuture) throws IOException {
        try {
            return streamFuture.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while collecting renderer stream output", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to collect renderer stream output", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("Timed out while collecting renderer stream output", e);
        }
    }

    private String readAll(java.io.InputStream in) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private boolean isNodeRendererAvailable() {
        if (!nodeRendererAvailable) {
            return false;
        }
        if (Files.exists(RESVG_MODULE_MANIFEST)) {
            return true;
        }
        nodeRendererAvailable = false;
        if (!missingDependencyWarned) {
            missingDependencyWarned = true;
            log.warn("Node chart renderer disabled: missing dependency {}", RESVG_MODULE_MANIFEST);
        }
        return false;
    }

}
