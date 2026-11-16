package com.performance.platform.probe.render;

import com.performance.platform.probe.model.ProbePoint;
import com.performance.platform.probe.model.ProbeSeries;

import java.util.List;
import java.util.Locale;

/**
 * Rendu SVG deterministe d'une ou plusieurs series.
 * <p>
 * Meme echelle, meme taille, memes couleurs pour des donnees identiques : le
 * rapport peut comparer deux executions sans bruit visuel.
 */
public final class SvgChart {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 320;
    private static final int PADDING = 48;
    private static final String[] COLORS = {"#2563eb", "#b23b34", "#3f7d54", "#8a5a00"};

    private SvgChart() {
    }

    public static String render(String title, List<ProbeSeries> series) {
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(WIDTH)
                .append("\" height=\"").append(HEIGHT).append("\" viewBox=\"0 0 ")
                .append(WIDTH).append(' ').append(HEIGHT).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>");
        svg.append("<text x=\"").append(PADDING).append("\" y=\"24\" font-family=\"sans-serif\"")
                .append(" font-size=\"14\" fill=\"#1f2937\">")
                .append(escape(title == null ? "" : title)).append("</text>");

        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        double minValue = Double.MAX_VALUE;
        double maxValue = -Double.MAX_VALUE;
        for (ProbeSeries one : series) {
            for (ProbePoint point : one.points()) {
                minTime = Math.min(minTime, point.timestamp());
                maxTime = Math.max(maxTime, point.timestamp());
                minValue = Math.min(minValue, point.value());
                maxValue = Math.max(maxValue, point.value());
            }
        }
        if (minTime == Long.MAX_VALUE || maxTime <= minTime) {
            svg.append("<text x=\"").append(PADDING).append("\" y=\"60\" font-family=\"sans-serif\"")
                    .append(" font-size=\"12\" fill=\"#667085\">No data in window</text></svg>");
            return svg.toString();
        }
        if (maxValue == minValue) {
            maxValue = minValue + 1;
        }

        svg.append("<rect x=\"").append(PADDING).append("\" y=\"").append(PADDING)
                .append("\" width=\"").append(WIDTH - 2 * PADDING).append("\" height=\"")
                .append(HEIGHT - 2 * PADDING).append("\" fill=\"none\" stroke=\"#e4e7ec\"/>");

        int colorIndex = 0;
        int legendY = HEIGHT - 12;
        for (ProbeSeries one : series) {
            String color = COLORS[colorIndex % COLORS.length];
            StringBuilder points = new StringBuilder();
            for (ProbePoint point : one.points()) {
                double x = PADDING + (double) (point.timestamp() - minTime) / (maxTime - minTime)
                        * (WIDTH - 2 * PADDING);
                double y = HEIGHT - PADDING - (point.value() - minValue) / (maxValue - minValue)
                        * (HEIGHT - 2 * PADDING);
                points.append(String.format(Locale.ROOT, "%.1f,%.1f ", x, y));
            }
            if (!points.isEmpty()) {
                svg.append("<polyline fill=\"none\" stroke=\"").append(color)
                        .append("\" stroke-width=\"1.5\" points=\"")
                        .append(points.toString().strip()).append("\"/>");
            }
            svg.append("<text x=\"").append(PADDING + (colorIndex % 4) * 220)
                    .append("\" y=\"").append(legendY).append("\" font-family=\"sans-serif\"")
                    .append(" font-size=\"11\" fill=\"").append(color).append("\">")
                    .append(escape(one.metric())).append(" (").append(one.unit()).append(")</text>");
            colorIndex++;
        }
        svg.append("<text x=\"").append(PADDING).append("\" y=\"").append(HEIGHT - PADDING + 16)
                .append("\" font-family=\"sans-serif\" font-size=\"10\" fill=\"#98a2b3\">")
                .append(String.format(Locale.ROOT, "min=%.2f max=%.2f", minValue, maxValue))
                .append("</text>");
        svg.append("</svg>");
        return svg.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
