package org.example.view;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.example.model.TideMeasurement;
import org.example.model.TideTable;

import java.util.List;

/** Gráfico de marés em Canvas (TideChartCanvas): curva mensal com linha de corte de 0,4 m. */
public class TideChart extends Pane {

    private static final double MAX_H = 2.0;
    private final Canvas canvas = new Canvas();
    private TideTable table;

    public TideChart() {
        canvas.setManaged(false);
        getChildren().add(canvas);
        setMinHeight(220);
        getStyleClass().add("card");
    }

    public void setTable(TideTable table) {
        this.table = table;
        draw();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        if (table == null || w < 100 || h < 80) {
            return;
        }
        double left = 52;
        double right = 16;
        double top = 26;
        double bottom = 28;
        double pw = w - left - right;
        double ph = h - top - bottom;
        int days = table.getMonth().lengthOfMonth();
        double dayW = pw / days;

        g.setFont(Font.font(11));
        g.setFill(Color.web("#0f172a"));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        g.fillText("Curva de maré — " + table.getLabel() + " (" + table.getMeasurements().size()
                + " medições de 30 min)", left, 6);

        // dias de maré baixa crítica
        for (int d = 1; d <= days; d++) {
            if (table.isCriticalDay(d)) {
                g.setFill(Color.web("#e74c3c", 0.14));
                g.fillRect(left + (d - 1) * dayW, top, dayW, ph);
            }
        }
        // grade horizontal
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        for (double v = 0; v <= MAX_H + 0.001; v += 0.5) {
            double y = top + ph - v / MAX_H * ph;
            g.setStroke(Color.web("#e2e8f0"));
            g.setLineWidth(1);
            g.strokeLine(left, y, left + pw, y);
            g.setFill(Color.web("#64748b"));
            g.fillText(String.format(Ui.PT_BR, "%.1f m", v), left - 6, y);
        }
        // dias
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        for (int d = 1; d <= days; d++) {
            g.setFill(table.isCriticalDay(d) ? Color.web("#e74c3c") : Color.web("#64748b"));
            g.fillText(String.valueOf(d), left + (d - 0.5) * dayW, top + ph + 6);
        }
        // curva
        List<TideMeasurement> ms = table.getMeasurements();
        g.setStroke(Color.web("#0284c7"));
        g.setLineWidth(1.4);
        g.beginPath();
        boolean first = true;
        for (TideMeasurement m : ms) {
            double pos = (m.day() - 1) + (m.timestamp().getHour() * 60 + m.timestamp().getMinute()) / 1440.0;
            double x = left + pos * dayW;
            double y = top + ph - Math.max(0, Math.min(MAX_H, m.heightMeters())) / MAX_H * ph;
            if (first) {
                g.moveTo(x, y);
                first = false;
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();
        // linha de corte 0,4 m
        double cutY = top + ph - TideTable.CRITICAL_LEVEL_M / MAX_H * ph;
        g.setStroke(Color.web("#e74c3c"));
        g.setLineWidth(1.5);
        g.setLineDashes(6, 4);
        g.strokeLine(left, cutY, left + pw, cutY);
        g.setLineDashes();
        g.setFill(Color.web("#e74c3c"));
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.BOTTOM);
        g.fillText("Linha d'água de corte: 0,40 m (maré baixa crítica)", left + pw - 4, cutY - 2);
    }
}
