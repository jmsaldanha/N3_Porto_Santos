package org.example.view;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.model.TideMeasurement;
import org.example.model.TideTable;
import org.example.service.PortService;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/** Monitoramento das tábuas de marés astronômicas com precisão de 30 minutos (RF010). */
public class TideMonitorPane extends BorderPane {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PortService service;
    private final ComboBox<TideTable> cmbTideMonth = new ComboBox<>();
    private final CheckBox onlyCritical = new CheckBox("Mostrar apenas medições críticas (≤ 0,4 m)");
    private final TableView<TideMeasurement> tideTableGrid = new TableView<>();
    private final TideChart tideChartCanvas = new TideChart();
    private final Label summary = new Label();
    private FilteredList<TideMeasurement> rows = new FilteredList<>(FXCollections.observableArrayList());

    public TideMonitorPane(PortService service) {
        this.service = service;
        getStyleClass().add("content");

        cmbTideMonth.setItems(service.getTideTables());
        cmbTideMonth.valueProperty().bindBidirectional(service.activeTideProperty());
        cmbTideMonth.valueProperty().addListener((o, a, t) -> showTable(t));
        onlyCritical.setOnAction(e -> applyCriticalFilter());

        HBox controls = new HBox(10, new Label("Mês de referência:"), cmbTideMonth,
                Ui.button("Importar Tábua CSV", "btn-primary", this::importCsv), onlyCritical);
        controls.setAlignment(Pos.CENTER_LEFT);
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);
        VBox controlsCard = new VBox(8, controls, summary);
        controlsCard.getStyleClass().add("card");

        setTop(new VBox(10,
                Ui.pageHeader("PORTOS — HIDRODINÂMICA",
                        "Tábuas de Marés Astronômicas — Precisão de 30 Minutos",
                        "Dias com maré ≤ 0,4 m são classificados como Maré Baixa Crítica e bloqueiam a atracação/desatracação "
                                + "de navios com calado ≥ 13,5 m (RN002). A troca do mês ativo atualiza o Lineup."),
                controlsCard));

        tideTableGrid.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tideTableGrid.getColumns().addAll(
                Ui.textColumn("Data", m -> m.timestamp().format(DATE), 120),
                Ui.textColumn("Hora", m -> m.timestamp().format(TIME), 80),
                Ui.column("Altura (m)", TideMeasurement::heightMeters, v -> String.format(Ui.PT_BR, "%.2f m", v), 110),
                Ui.textColumn("Condição", TideMeasurement::condition, 140));
        tideTableGrid.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(TideMeasurement item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("row-critical");
                if (!empty && item != null && item.isCritical()) {
                    getStyleClass().add("row-critical");
                }
            }
        });
        VBox tableCard = new VBox(8, new Label("Medições da tábua (TideTableGrid)"), tideTableGrid);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tideTableGrid, Priority.ALWAYS);

        SplitPane split = new SplitPane(tideChartCanvas, tableCard);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.45);
        BorderPane.setMargin(split, new Insets(10, 0, 0, 0));
        setCenter(split);
        showTable(service.getActiveTide());
    }

    private void showTable(TideTable t) {
        tideChartCanvas.setTable(t);
        if (t == null) {
            tideTableGrid.setItems(FXCollections.observableArrayList());
            summary.setText("Nenhuma tábua selecionada.");
            return;
        }
        rows = new FilteredList<>(FXCollections.observableArrayList(t.getMeasurements()));
        tideTableGrid.setItems(rows);
        applyCriticalFilter();
        summary.setText(String.format("Origem: %s  •  %d medições  •  Dias de maré baixa crítica: %s  •  "
                        + "Lineup sincronizado com esta tábua.",
                t.getSource(), t.getMeasurements().size(),
                t.getCriticalDays().isEmpty() ? "nenhum" : t.getCriticalDays().toString().replaceAll("[\\[\\]]", "")));
    }

    private void applyCriticalFilter() {
        rows.setPredicate(m -> !onlyCritical.isSelected() || m.isCritical());
    }

    private void importCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar tábua de marés (CSV oficial DHN)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV", "*.csv", "*.txt"));
        File dir = new File("exemplos");
        if (dir.isDirectory()) {
            chooser.setInitialDirectory(dir.getAbsoluteFile());
        }
        Window owner = getScene() == null ? null : getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        try {
            TideTable t = service.importTideCsv(file.toPath());
            Ui.info("Tábua importada", String.format("%s importada com %d medições. Dias críticos: %s.",
                    t.getLabel(), t.getMeasurements().size(), service.criticalDaysText()));
        } catch (IOException e) {
            Ui.error("Falha ao ler o arquivo", e.getMessage());
        } catch (IllegalArgumentException e) {
            Ui.error("Arquivo CSV inválido", e.getMessage());
        }
    }
}
