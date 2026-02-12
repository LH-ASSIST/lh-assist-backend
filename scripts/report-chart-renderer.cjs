const fs = require("fs");
const echarts = require("echarts");
const { Resvg } = require("@resvg/resvg-js");

const COLORS = {
  navy: "#002D56",
  emerald: "#00A082",
  urgent: "#dc2626",
  high: "#f97316",
  medium: "#f59e0b",
  low: "#22c55e",
  unknown: "#94a3b8",
  neutral: "#e5e7eb",
};

function priorityCss(score) {
  if (score == null) return "unknown";
  if (score <= 59) return "urgent";
  if (score <= 79) return "high";
  if (score <= 89) return "medium";
  return "low";
}

function parseInput() {
  const raw = fs.readFileSync(0, "utf8");
  return JSON.parse(raw);
}

function heatVisualMap(max) {
  return {
    min: 0,
    max: Math.max(max, 1),
    orient: "horizontal",
    left: "center",
    bottom: 0,
    text: ["높음", "낮음"],
    inRange: { color: ["#eff6ff", "#1e88e5"] },
    textStyle: { fontFamily: "Noto Sans KR", fontSize: 11 },
  };
}

function buildOption(kind, data) {
  if (kind === "totalScore") {
    const score = Math.max(0, Math.min(100, Number(data.score || 0)));
    const css = priorityCss(score);
    return {
      animation: false,
      series: [
        {
          type: "gauge",
          startAngle: 210,
          endAngle: -30,
          min: 0,
          max: 100,
          splitNumber: 5,
          axisLine: {
            lineStyle: {
              width: 15,
              color: [
                [0.59, COLORS.urgent],
                [0.79, COLORS.high],
                [0.89, COLORS.medium],
                [1, COLORS.low],
              ],
            },
          },
          progress: { show: true, width: 15, itemStyle: { color: COLORS[css] } },
          pointer: { show: true, length: "58%", width: 4 },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { color: "#64748b", fontSize: 11 },
          title: { show: true, offsetCenter: [0, "68%"], fontSize: 13, color: "#334155" },
          detail: {
            valueAnimation: false,
            offsetCenter: [0, "20%"],
            fontSize: 34,
            color: "#0f172a",
            formatter: "{value}",
          },
          data: [{ value: score, name: data.label || "안전등급" }],
        },
      ],
    };
  }

  if (kind === "pageSafety") {
    const rows = Array.isArray(data.rows) ? data.rows : [];
    const pages = rows.map((r) => `P${r.pageNumber}`);
    const scores = rows.map((r) => Number(r.minSafetyScore ?? 0));
    const marker = rows.map((r) => (r.hasViolation ? Number(r.minSafetyScore ?? 0) : null));
    return {
      animation: false,
      grid: { left: 40, right: 14, top: 34, bottom: 34 },
      xAxis: {
        type: "category",
        data: pages,
        axisLabel: { color: "#475569", fontSize: 10, fontFamily: "Noto Sans KR" },
      },
      yAxis: {
        type: "value",
        min: 0,
        max: 105,
        axisLabel: { color: "#475569", fontSize: 10, fontFamily: "Noto Sans KR" },
        splitLine: { lineStyle: { color: "#e2e8f0" } },
      },
      series: [
        {
          name: "최소 안전점수",
          type: "line",
          smooth: true,
          symbol: "circle",
          symbolSize: 7,
          data: scores,
          lineStyle: { width: 3, color: COLORS.navy },
          itemStyle: {
            color: COLORS.navy,
          },
          areaStyle: {
            color: {
              type: "linear",
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: "rgba(0, 45, 86, 0.38)" },
                { offset: 1, color: "rgba(0, 160, 130, 0.08)" },
              ],
            },
          },
          label: { show: true, position: "top", fontSize: 10, color: "#1e293b" },
        },
        {
          name: "위반 구간",
          type: "scatter",
          symbol: "pin",
          symbolSize: 18,
          data: marker,
          itemStyle: { color: COLORS.urgent },
        },
      ],
      legend: {
        top: 4,
        textStyle: { fontFamily: "Noto Sans KR", fontSize: 11, color: "#334155" },
      },
    };
  }

  if (kind === "priorityDistribution") {
    const rows = Array.isArray(data.rows) ? data.rows : [];
    const values = rows.map((r) => Number(r.count || 0));
    const maxValue = Math.max(...values, 1);
    const indicators = rows.map((r) => ({
      name: String(r.label || ""),
      max: Math.ceil(maxValue * 1.2),
    }));
    return {
      animation: false,
      radar: {
        center: ["50%", "54%"],
        radius: "66%",
        indicator: indicators,
        splitNumber: 4,
        axisName: { color: "#334155", fontFamily: "Noto Sans KR", fontSize: 11 },
        axisLine: { lineStyle: { color: "#cbd5e1" } },
        splitLine: { lineStyle: { color: "#dbe5f1" } },
        splitArea: { areaStyle: { color: ["#f8fbff", "#eef6ff"] } },
      },
      series: [
        {
          name: "우선순위 분포",
          type: "radar",
          data: [
            {
              value: values,
              areaStyle: { color: "rgba(0, 160, 130, 0.28)" },
              lineStyle: { color: COLORS.emerald, width: 2.5 },
              itemStyle: { color: COLORS.navy },
            },
          ],
        },
      ],
    };
  }

  if (kind === "pageTypeHeatmap") {
    const rows = Array.isArray(data.rows) ? data.rows : [];
    const y = rows.map((r) => `P${r.pageNumber}`);
    const x = ["누락", "적정성", "명확성", "절차준수"];
    const points = [];
    let max = 0;
    rows.forEach((row, yi) => {
      const cells = Array.isArray(row.cells) ? row.cells : [];
      x.forEach((label, xi) => {
        const cell = cells.find((c) => c.riskTypeLabel === label);
        const count = Number(cell?.count || 0);
        if (count > max) max = count;
        points.push([xi, yi, count]);
      });
    });
    return {
      animation: false,
      grid: { left: 52, right: 22, top: 18, bottom: 52 },
      xAxis: {
        type: "category",
        data: x,
        axisLabel: { fontFamily: "Noto Sans KR", fontSize: 11, color: "#334155" },
      },
      yAxis: {
        type: "category",
        data: y,
        axisLabel: { fontFamily: "Noto Sans KR", fontSize: 11, color: "#334155" },
      },
      visualMap: heatVisualMap(max),
      series: [
        {
          type: "heatmap",
          data: points,
          label: { show: true, fontSize: 10 },
          emphasis: { itemStyle: { borderColor: "#1e3a8a", borderWidth: 1 } },
        },
      ],
    };
  }

  if (kind === "riskProfileRadar") {
    const rows = Array.isArray(data.rows) ? data.rows : [];
    const indicators = rows.map((r) => ({
      name: String(r.label || ""),
      max: 100,
    }));
    const values = rows.map((r) => Number(r.value || 0));
    return {
      animation: false,
      radar: {
        center: ["48%", "52%"],
        radius: "67%",
        indicator: indicators,
        splitNumber: 5,
        splitArea: { areaStyle: { color: ["#f8fafc", "#f1f5f9"] } },
        axisName: { color: "#334155", fontFamily: "Noto Sans KR", fontSize: 11 },
        axisLine: { lineStyle: { color: "#cbd5e1" } },
        splitLine: { lineStyle: { color: "#dbe5f1" } },
      },
      series: [
        {
          type: "radar",
          data: [
            {
              value: values,
              name: "리스크 프로파일",
              areaStyle: { color: "rgba(30,136,229,0.24)" },
              lineStyle: { color: "#1e88e5", width: 2 },
              itemStyle: { color: "#1e88e5" },
            },
          ],
        },
      ],
    };
  }

  if (kind === "deductionWaterfall") {
    const rows = Array.isArray(data.rows) ? data.rows : [];
    const totalScore = Math.max(0, Math.min(100, Number(data.totalScore || 0)));
    const children = rows.map((r) => {
      const deduction = Math.abs(Number(r.delta || 0));
      return {
        name: String(r.label || ""),
        value: deduction,
        itemStyle: {
          color:
            String(r.label || "").includes("누락") ? "#dc2626" :
            String(r.label || "").includes("적정성") ? "#f97316" :
            String(r.label || "").includes("명확") ? "#f59e0b" : "#00A082",
        },
      };
    });
    const sumDeduction = children.reduce((acc, c) => acc + c.value, 0);
    return {
      animation: false,
      title: [
        {
          text: `최종 ${totalScore}점`,
          left: "50%",
          top: "44%",
          textAlign: "center",
          textStyle: { color: COLORS.navy, fontSize: 22, fontFamily: "Noto Sans KR", fontWeight: 700 },
        },
        {
          text: `총 감점 ${sumDeduction}점`,
          left: "50%",
          top: "54%",
          textAlign: "center",
          textStyle: { color: "#64748b", fontSize: 11, fontFamily: "Noto Sans KR" },
        },
      ],
      series: [
        {
          type: "sunburst",
          radius: ["32%", "82%"],
          sort: null,
          data: [
            {
              name: "감점 요인",
              value: Math.max(sumDeduction, 1),
              itemStyle: { color: "#e2e8f0" },
              children,
            },
          ],
          label: { rotate: "radial", color: "#334155", fontSize: 10, fontFamily: "Noto Sans KR" },
          emphasis: { focus: "ancestor" },
        },
      ],
    };
  }

  if (kind === "priorityBubble") {
    const rows = Array.isArray(data.rows) ? data.rows : [];
    return {
      animation: false,
      grid: { left: 44, right: 18, top: 24, bottom: 42 },
      xAxis: {
        type: "value",
        min: 0,
        max: 100,
        name: "섹션 안전점수",
        nameGap: 22,
        axisLabel: { color: "#475569", fontSize: 10, fontFamily: "Noto Sans KR" },
        splitLine: { lineStyle: { color: "#e2e8f0" } },
      },
      yAxis: {
        type: "value",
        min: 0,
        name: "리스크 항목 수",
        nameGap: 18,
        axisLabel: { color: "#475569", fontSize: 10, fontFamily: "Noto Sans KR" },
        splitLine: { lineStyle: { color: "#edf2f7" } },
      },
      series: [
        {
          type: "scatter",
          data: rows.map((r) => ({
            value: [Number(r.safetyScore || 0), Number(r.riskItemCount || 0), Number(r.bubbleSize || 10)],
            name: String(r.label || ""),
            itemStyle: { color: COLORS[r.priorityCssClass] || COLORS.unknown },
          })),
          symbolSize: (val) => Math.max(8, Number(val[2] || 10)),
          label: {
            show: true,
            formatter: (p) => p.name || "",
            position: "top",
            fontSize: 9,
            color: "#334155",
          },
        },
      ],
    };
  }

  return null;
}

function main() {
  const payload = parseInput();
  const width = Number(payload.width || 640);
  const height = Number(payload.height || 360);
  const kind = payload.kind;
  const data = payload.data || {};
  const option = buildOption(kind, data);
  if (!option) {
    process.stderr.write(`unsupported chart kind: ${kind}`);
    process.exit(2);
    return;
  }

  const chart = echarts.init(null, null, { renderer: "svg", ssr: true, width, height });
  chart.setOption(option, true);
  const svg = chart.renderToSVGString();
  chart.dispose();
  const pngBuffer = new Resvg(svg, {
    fitTo: { mode: "width", value: width },
    background: "white",
  })
    .render()
    .asPng();
  const dataUrl = `data:image/png;base64,${Buffer.from(pngBuffer).toString("base64")}`;
  process.stdout.write(dataUrl);
}

try {
  main();
} catch (e) {
  process.stderr.write((e && e.stack) || String(e));
  process.exit(1);
}
