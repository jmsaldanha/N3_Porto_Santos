package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.dto.LineupKpis;
import org.example.model.Berth;
import org.example.model.CargoCategory;
import org.example.model.ScheduleStatus;
import org.example.model.ShipSchedule;
import org.example.model.TideTable;
import org.example.service.PortService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tela do Lineup Marítimo (RF001 a RF004): KPIs globais, filtros por perfil de carga,
 * cartões de berço paginados em lotes (padrão 5), rolagem vertical e barra de navio selecionado.
 */
public class LineupPane extends BorderPane {

    private final PortService service;
    private final Consumer<Berth> onScheduleBerth;

    private final Map<CargoCategory, CheckBox> categoryChecks = new EnumMap<>(CargoCategory.class);
    private final CheckBox allCategories = new CheckBox("Todos os Perfis");
    private final TextField searchField = new TextField();
    private final Label kpiBerths = new Label();
    private final Label kpiShips = new Label();
    private final Label kpiTonnage = new Label();
    private final Label kpiOccupancy = new Label();
    private final Label tideInfo = new Label();
    private final VBox timelineHeader = new VBox(4);

    private final VBox chartsContainer = new VBox(12);
    private final ScrollPane chartsScroll = new ScrollPane(chartsContainer);
    private final HBox pageButtons = new HBox(4);
    private final Label pageInfo = new Label();
    private final ComboBox<Integer> pageSize = new ComboBox<>();
    private final Label selectedShipBanner = new Label("Navio selecionado: clique em uma barra de atracação para ver os detalhes.");
    private final Label hoverStatus = new Label("Passe o mouse sobre um navio para consultar carga ↑ e descarga ↓.");

    private final List<Berth> filteredBerths = new ArrayList<>();
    private int currentPage = 1;

    public LineupPane(PortService service, Consumer<Berth> onScheduleBerth) {
        this.service = service;
        this.onScheduleBerth = onScheduleBerth;
        getStyleClass().add("content");

        VBox top = new VBox(10, buildHeader(), buildFilters(), buildTimelineHeader());
        setTop(top);

        chartsContainer.setPadding(new Insets(2, 4, 8, 0));
        chartsScroll.setFitToWidth(true);
        chartsScroll.getStyleClass().add("charts-scroll");
        chartsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        BorderPane.setMargin(chartsScroll, new Insets(10, 0, 10, 0));
        setCenter(chartsScroll);
        setBottom(buildFooter());

        // RN008: qualquer inclusão/edição/exclusão (ou troca de tábua de marés) reconstrói o Lineup.
        service.dataVersionProperty().addListener((obs, o, n) -> applyFilter());
        applyFilter();
    }

    // ------------------------------------------------------------------ construção

    private VBox buildHeader() {
        Label crumb = new Label("PORTO DE SANTOS — APS");
        crumb.getStyleClass().add("crumb");
        Label title = new Label("Lineup Portuário de Santos (APS) — Berços de Atracação");
        title.getStyleClass().add("page-title");
        HBox titleLine = new HBox(10, crumb, title);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        tideInfo.getStyleClass().add("page-subtitle");

        HBox kpis = new HBox(10,
                kpiCard("Berços no Filtro", kpiBerths),
                kpiCard("Navios Programados", kpiShips),
                kpiCard("Volume Total", kpiTonnage),
                kpiCard("Taxa Média de Ocupação", kpiOccupancy),
                legend());
        kpis.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, titleLine, tideInfo, kpis);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox kpiCard(String caption, Label value) {
        Label c = new Label(caption);
        c.getStyleClass().add("kpi-caption");
        value.getStyleClass().add("kpi-value");
        VBox card = new VBox(2, c, value);
        card.getStyleClass().add("kpi-card");
        return card;
    }

    private HBox legend() {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 0, 10));
        Label tide = new Label("■ Maré baixa (≤ 0,4 m)");
        tide.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        box.getChildren().add(tide);
        for (ScheduleStatus st : ScheduleStatus.values()) {
            Label l = new Label("● " + st.getLabel());
            l.setStyle("-fx-text-fill: " + st.getColor() + "; -fx-font-weight: bold;");
            box.getChildren().add(l);
        }
        return box;
    }

    private VBox buildFilters() {
        Label caption = new Label("PORTO DE SANTOS — PERFIL DE CARGA");
        caption.getStyleClass().add("section-caption");
        allCategories.setSelected(true);
        allCategories.setOnAction(e -> {
            categoryChecks.values().forEach(cb -> cb.setSelected(allCategories.isSelected()));
            currentPage = 1;
            applyFilter();
        });
        searchField.setPromptText("Buscar berço, terminal, navio ou carga...");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((o, a, b) -> {
            currentPage = 1;
            applyFilter();
        });
        HBox head = new HBox(12, caption, allCategories, Ui.spacer(), searchField);
        head.setAlignment(Pos.CENTER_LEFT);

        FlowPane checks = new FlowPane(16, 6);
        for (CargoCategory c : CargoCategory.values()) {
            CheckBox cb = new CheckBox(c.getLabel());
            cb.setSelected(true);
            cb.setOnAction(e -> {
                allCategories.setSelected(categoryChecks.values().stream().allMatch(CheckBox::isSelected));
                currentPage = 1;
                applyFilter();
            });
            categoryChecks.put(c, cb);
            checks.getChildren().add(cb);
        }
        VBox box = new VBox(8, head, checks);
        box.getStyleClass().add("card");
        return box;
    }

    /** Régua temporal mensal fixa no topo (TimelineHeader), alinhada às colunas dos cartões. */
    private VBox buildTimelineHeader() {
        timelineHeader.setPadding(new Insets(0, 0, 0, 13));
        return timelineHeader;
    }

    private VBox buildFooter() {
        pageSize.getItems().addAll(5, 10, 15);
        pageSize.setValue(5);
        pageSize.setOnAction(e -> {
            currentPage = 1;
            goToPage(1);
        });
        pageInfo.getStyleClass().add("muted");
        HBox pagination = new HBox(10, pageInfo, Ui.spacer(), pageButtons, new Label("Berços por página:"), pageSize);
        pagination.setAlignment(Pos.CENTER_LEFT);
        pagination.getStyleClass().add("card");

        selectedShipBanner.getStyleClass().add("selected-banner");
        selectedShipBanner.setMaxWidth(Double.MAX_VALUE);
        hoverStatus.getStyleClass().add("status-bar");
        hoverStatus.setMaxWidth(Double.MAX_VALUE);
        return new VBox(6, pagination, selectedShipBanner, hoverStatus);
    }

    // ------------------------------------------------------------------ comportamento

    /** Aplica filtros de perfil e busca, recalcula os KPIs (RF001 - A1) e redesenha a página atual. */
    public void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);
        filteredBerths.clear();
        for (Berth b : service.getBerths()) {
            CheckBox cb = categoryChecks.get(b.getCategory());
            if (cb != null && !cb.isSelected()) {
                continue;
            }
            if (!q.isEmpty() && !matches(b, q)) {
                continue;
            }
            filteredBerths.add(b);
        }
        updateCategoryCounts();
        updateKpis();
        refreshTimelineHeader();
        goToPage(currentPage);
    }

    private boolean matches(Berth b, String q) {
        if ((b.getCode() + " " + b.getName() + " " + b.getSectorName()).toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        return b.getSchedules().stream().anyMatch(s -> {
            String text = s.getVessel().getName()
                    + " " + s.getUnloadingProduct() + " " + s.getLoadingProduct();
            return text.toLowerCase(Locale.ROOT).contains(q);
        });
    }

    private void updateCategoryCounts() {
        Map<CargoCategory, Long> counts = new EnumMap<>(CargoCategory.class);
        service.getBerths().forEach(b -> counts.merge(b.getCategory(), 1L, Long::sum));
        categoryChecks.forEach((c, cb) -> cb.setText(c.getLabel() + " (" + counts.getOrDefault(c, 0L) + ")"));
        allCategories.setText("Todos os Perfis (" + service.getBerths().size() + ")");
    }

    private void updateKpis() {
        LineupKpis k = service.computeKpis(filteredBerths);
        kpiBerths.setText(k.visibleBerths() + " de " + k.totalBerths());
        kpiShips.setText(String.valueOf(k.scheduledShips()));
        kpiTonnage.setText(Ui.tons(k.totalTonnage()));
        kpiOccupancy.setText(String.format(Ui.PT_BR, "%.1f%%", k.occupancyPercent()));
        TideTable t = service.getActiveTide();
        tideInfo.setText(t == null ? "Nenhuma tábua de marés ativa." : String.format(
                "Tábua de marés ativa: %s (%s — %d medições de 30 min)  •  Dias de maré baixa crítica: %s  "
                        + "•  Navios com calado ≥ %.1f m bloqueados nesses dias (RN002)",
                t.getLabel(), t.getSource(), t.getMeasurements().size(), service.criticalDaysText(),
                PortService.DEEP_DRAFT_LIMIT_M));
    }

    private void refreshTimelineHeader() {
        TideTable t = service.getActiveTide();
        Label caption = new Label("Régua do mês — Novembro / 2026 (dias em vermelho = maré baixa crítica)");
        caption.getStyleClass().add("muted");
        timelineHeader.getChildren().setAll(caption, BerthChartCard.buildDayRuler(t));
    }

    private int totalPages() {
        return Math.max(1, (int) Math.ceil(filteredBerths.size() / (double) pageSize.getValue()));
    }

    /** RF003/RF004 — paginação: calcula fromIndex/toIndex, limpa e repovoa o container de cartões. */
    public void goToPage(int page) {
        int total = totalPages();
        currentPage = Math.max(1, Math.min(page, total));
        int size = pageSize.getValue();
        int fromIndex = (currentPage - 1) * size;
        int toIndex = Math.min(fromIndex + size, filteredBerths.size());

        chartsContainer.getChildren().clear();
        for (Berth b : filteredBerths.subList(Math.min(fromIndex, toIndex), toIndex)) {
            chartsContainer.getChildren().add(new BerthChartCard(b, service, this::selectShip,
                    hoverStatus::setText, onScheduleBerth));
        }
        if (filteredBerths.isEmpty()) {
            Label empty = new Label("Nenhum berço corresponde aos filtros selecionados.");
            empty.getStyleClass().add("muted");
            chartsContainer.getChildren().add(empty);
            pageInfo.setText("Exibindo 0 berços");
        } else {
            pageInfo.setText(String.format("Exibindo berços %d a %d de %d filtrados (Página %d de %d)",
                    fromIndex + 1, toIndex, filteredBerths.size(), currentPage, total));
        }
        chartsScroll.setVvalue(0);
        rebuildPageButtons(total);
    }

    public void nextPage() {
        goToPage(currentPage + 1);
    }

    public void previousPage() {
        goToPage(currentPage - 1);
    }

    private void rebuildPageButtons(int total) {
        pageButtons.getChildren().clear();
        pageButtons.getChildren().add(pageButton("« Primeiro", 1, false, currentPage == 1));
        pageButtons.getChildren().add(pageButton("‹ Anterior", currentPage - 1, false, currentPage == 1));
        int from = 1;
        int to = total;
        if (total > 12) {
            from = Math.max(1, currentPage - 5);
            to = Math.min(total, from + 11);
            from = Math.max(1, to - 11);
        }
        for (int p = from; p <= to; p++) {
            pageButtons.getChildren().add(pageButton(String.valueOf(p), p, p == currentPage, false));
        }
        pageButtons.getChildren().add(pageButton("Próximo ›", currentPage + 1, false, currentPage == total));
        pageButtons.getChildren().add(pageButton("Último »", total, false, currentPage == total));
    }

    private Button pageButton(String text, int target, boolean active, boolean disabled) {
        Button b = new Button(text);
        b.getStyleClass().add(active ? "page-btn-active" : "page-btn");
        b.setDisable(disabled);
        b.setOnAction(e -> goToPage(target));
        return b;
    }

    private void selectShip(ShipSchedule s) {
        selectedShipBanner.setText("Navio selecionado: " + s.describe());
    }
}
