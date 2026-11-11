package org.example.view;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.example.model.Berth;
import org.example.model.ShipSchedule;
import org.example.model.TideMeasurement;
import org.example.model.TideTable;
import org.example.service.PortService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cartão de um berço no Lineup (RF001–RF004): cabeçalho com dados do berço e
 * gráfico mensal (dias 1 a 30) com as barras de atracação coloridas por status (RN005),
 * colunas vermelhas nos dias de maré baixa crítica e a curva de maré ao fundo.
 */
public class BerthChartCard extends VBox {

    public static final double DAY_WIDTH = 34;
    private static final double CHART_HEIGHT = 66;
    private static final double BAR_HEIGHT = 28;

    private final Berth berth;
    private final PortService service;
    private final Consumer<ShipSchedule> onSelect;
    private final Consumer<String> onHover;
    private final Consumer<Berth> onSchedule;
    private final List<Rectangle> shipRectangles = new ArrayList<>();

    public BerthChartCard(Berth berth, PortService service, Consumer<ShipSchedule> onSelect,
                          Consumer<String> onHover, Consumer<Berth> onSchedule) {
        this.berth = berth;
        this.service = service;
        this.onSelect = onSelect;
        this.onHover = onHover;
        this.onSchedule = onSchedule;
        getStyleClass().addAll("card", "berth-card");
        setSpacing(8);
        renderChart();
    }

    public void renderChart() {
        getChildren().clear();
        shipRectangles.clear();
        getChildren().addAll(buildHeader(), buildChart(), buildDayRuler(service.getActiveTide()));
    }

    private HBox buildHeader() {
        Label title = new Label(berth.getName());
        title.getStyleClass().add("berth-title");
        Label badge = Ui.badge(berth.getCategory().getLabel(), berth.getCategory().getColor());
        Label info = new Label(String.format("%s  •  Calado %s  •  %s  •  %d navio(s)", berth.getCode(),
                Ui.meters(berth.getMaxDraft()), berth.getSectorName(), berth.getSchedules().size()));
        info.getStyleClass().add("muted");
        Button schedule = Ui.button("+ Atracar Navio", "btn-outline", () -> onSchedule.accept(berth));
        HBox header = new HBox(10, title, badge, info, Ui.spacer(), schedule);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Pane buildChart() {
        Pane chart = new Pane();
        double width = Berth.MONTH_DAYS * DAY_WIDTH;
        chart.setPrefSize(width, CHART_HEIGHT);
        chart.setMinSize(width, CHART_HEIGHT);
        chart.setMaxSize(width, CHART_HEIGHT);
        TideTable tide = service.getActiveTide();

        for (int day = 1; day <= Berth.MONTH_DAYS; day++) {
            Rectangle column = new Rectangle((day - 1) * DAY_WIDTH, 0, DAY_WIDTH, CHART_HEIGHT);
            boolean critical = tide != null && tide.isCriticalDay(day);
            column.setFill(critical ? Color.web("#e74c3c", 0.16) : (day % 2 == 0 ? Color.web("#f8fafc") : Color.WHITE));
            Line grid = new Line(day * DAY_WIDTH, 0, day * DAY_WIDTH, CHART_HEIGHT);
            grid.setStroke(Color.web("#e2e8f0"));
            chart.getChildren().addAll(column, grid);
        }
        if (tide != null) {
            chart.getChildren().add(buildTideCurve(tide));
        }
        for (ShipSchedule s : berth.getSchedules()) {
            chart.getChildren().add(buildShipBar(s));
        }
        return chart;
    }

    /** Curva de maré da tábua ativa desenhada na faixa inferior do gráfico. */
    private Polyline buildTideCurve(TideTable tide) {
        Polyline curve = new Polyline();
        for (TideMeasurement m : tide.getMeasurements()) {
            double dayPosition = (m.day() - 1) + (m.timestamp().getHour() * 60 + m.timestamp().getMinute()) / 1440.0;
            if (m.day() > Berth.MONTH_DAYS) {
                break;
            }
            double x = dayPosition * DAY_WIDTH;
            double y = CHART_HEIGHT - 3 - Math.max(0, m.heightMeters()) / 2.0 * 16;
            curve.getPoints().addAll(x, y);
        }
        curve.setStroke(Color.web("#38bdf8"));
        curve.setStrokeWidth(1);
        curve.setMouseTransparent(true);
        return curve;
    }

    private StackPane buildShipBar(ShipSchedule s) {
        double x1 = (s.getStartDay() - 1) * DAY_WIDTH + DAY_WIDTH / 2;
        double x2 = (s.getEndDay() - 1) * DAY_WIDTH + DAY_WIDTH / 2;
        boolean tideIssue = service.violatesTide(s);

        Rectangle rect = new Rectangle(x2 - x1, BAR_HEIGHT);
        rect.setArcWidth(10);
        rect.setArcHeight(10);
        rect.setFill(Color.web(s.getStatus().getColor()));
        if (tideIssue) {
            rect.setStroke(Color.web("#111827"));
            rect.setStrokeWidth(2);
            rect.getStrokeDashArray().addAll(5.0, 3.0);
        }
        shipRectangles.add(rect);

        Label name = new Label((tideIssue ? "[!] " : "") + s.getVessel().getName());
        name.getStyleClass().add("ship-bar-label");
        name.setMaxWidth(x2 - x1 - 6);

        StackPane bar = new StackPane(rect, name);
        bar.setLayoutX(x1);
        bar.setLayoutY(10);
        bar.setCursor(Cursor.HAND);
        Tooltip tip = new Tooltip(s.describe() + (tideIssue
                ? "\n[!] Irregular na tábua de marés ativa (RN002)" : ""));
        tip.setShowDelay(Duration.millis(250));
        Tooltip.install(bar, tip);
        bar.setOnMouseEntered(e -> {
            rect.setOpacity(0.85);
            onHover.accept("Navio: " + s.describe());
        });
        bar.setOnMouseExited(e -> rect.setOpacity(1));
        bar.setOnMouseClicked(e -> updateSelectedShipBanner(s));
        return bar;
    }

    /** Ao clicar no navio, atualiza o painel "Navio Selecionado" do rodapé (RF002 - A1). */
    public void updateSelectedShipBanner(ShipSchedule s) {
        onSelect.accept(s);
    }

    /** Régua de dias do cartão: dias de maré baixa crítica em vermelho. */
    static HBox buildDayRuler(TideTable tide) {
        HBox ruler = new HBox();
        for (int day = 1; day <= Berth.MONTH_DAYS; day++) {
            Label l = new Label(String.format("%02d", day));
            l.setMinWidth(DAY_WIDTH);
            l.setPrefWidth(DAY_WIDTH);
            l.setAlignment(Pos.CENTER);
            l.getStyleClass().add(tide != null && tide.isCriticalDay(day) ? "day-critical" : "day-label");
            ruler.getChildren().add(l);
        }
        return ruler;
    }

    public Berth getBerth() {
        return berth;
    }
}
