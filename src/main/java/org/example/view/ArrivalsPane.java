package org.example.view;

import javafx.beans.binding.Bindings;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.dto.ShipScheduleRequest;
import org.example.model.Berth;
import org.example.model.BusinessRuleException;
import org.example.model.RegisteredVessel;
import org.example.model.ScheduleConflictException;
import org.example.model.ScheduleStatus;
import org.example.model.ShipSchedule;
import org.example.service.PortService;

import java.util.Comparator;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Programação de chegadas e atracações (RF011) com validação de sobreposição (RN001) e maré (RN002),
 * e tabela de auditoria/histórico das atracações do mês (RF012).
 */
public class ArrivalsPane extends BorderPane {

    private final PortService service;
    private final ComboBox<Berth> cmbBerthTarget = new ComboBox<>();
    private final ComboBox<RegisteredVessel> cmbVesselSelect = new ComboBox<>();
    private final ComboBox<String> cmbDischarge;
    private final ComboBox<String> cmbLoad;
    private final Spinner<Integer> txtStartDay = new Spinner<>(1, 29, 1);
    private final Spinner<Integer> txtEndDay = new Spinner<>(2, 30, 2);
    private final TextField txtTonnage = new TextField();
    private final ComboBox<ScheduleStatus> cmbStatus = new ComboBox<>();
    private final CheckBox autoEnd = new CheckBox("Calcular saída pela taxa do perfil do berço (RN004)");
    private final CheckBox supervisor = new CheckBox("Perfil supervisor auditor (RN006)");
    private final Label hint = new Label();
    private final Label formMode = new Label();
    private final TextField searchField = new TextField();
    private final TableView<ShipSchedule> arrivalsTable = new TableView<>();
    private final FilteredList<ShipSchedule> arrivalsObservableList;
    private ShipSchedule editing;
    private boolean filling;

    public ArrivalsPane(PortService service) {
        this.service = service;
        this.cmbDischarge = Ui.productCombo(service::knownProducts);
        this.cmbLoad = Ui.productCombo(service::knownProducts);
        this.arrivalsObservableList = new FilteredList<>(service.getSchedules());
        getStyleClass().add("content");
        setTop(new VBox(10,
                Ui.pageHeader("PORTOS — GESTÃO DE DADOS & CRUD DO LINEUP",
                        "Atracações (Chegada de Navios) — Programação Mensal do Lineup",
                        "Cadastre, edite ou exclua atracações por berço. O sistema valida perfil de carga (RN003), maré baixa "
                                + "(RN002) e sobreposição (RN001), sugerindo o próximo período livre em caso de conflito."),
                buildForm()));
        VBox table = buildTable();
        BorderPane.setMargin(table, new Insets(10, 0, 0, 0));
        setCenter(table);
        service.dataVersionProperty().addListener((o, a, b) -> arrivalsTable.refresh());
        clearForm();
    }

    private VBox buildForm() {
        SortedList<Berth> berths = new SortedList<>(service.getBerths(), Comparator.comparing(Berth::getName));
        cmbBerthTarget.setItems(berths);
        cmbBerthTarget.setPromptText("Berço de destino");
        SortedList<RegisteredVessel> vessels = new SortedList<>(service.getVessels(),
                Comparator.comparing(RegisteredVessel::getName));
        cmbVesselSelect.setItems(vessels);
        cmbVesselSelect.setPromptText("Navio registrado");
        cmbStatus.getItems().setAll(ScheduleStatus.values());
        txtStartDay.setEditable(true);
        txtEndDay.setEditable(true);
        txtTonnage.setPromptText("ex.: 64.000");
        autoEnd.setSelected(true);
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        cmbBerthTarget.valueProperty().addListener((o, a, b) -> updateHint());
        cmbVesselSelect.valueProperty().addListener((o, a, v) -> {
            if (v != null && !filling) {
                Ui.setProduct(cmbDischarge, v.getDefaultUnloadProduct());
                Ui.setProduct(cmbLoad, v.getDefaultLoadProduct());
                txtTonnage.setText(Ui.number(v.getDefaultTonnage()));
            }
            updateHint();
            recalcEnd();
        });
        txtStartDay.valueProperty().addListener((o, a, b) -> recalcEnd());
        txtTonnage.textProperty().addListener((o, a, b) -> recalcEnd());
        cmbBerthTarget.valueProperty().addListener((o, a, b) -> recalcEnd());
        autoEnd.setOnAction(e -> recalcEnd());

        HBox row1 = new HBox(10,
                Ui.field("Berço de Atracação:", cmbBerthTarget),
                Ui.field("Navio:", cmbVesselSelect),
                Ui.field("↓ Produto Descarregado:", cmbDischarge),
                Ui.field("↑ Produto Carregado:", cmbLoad),
                Ui.field("Volume (t):", txtTonnage));
        HBox row2 = new HBox(10,
                Ui.field("Dia Chegada (1-29):", txtStartDay),
                Ui.field("Dia Saída (2-30):", txtEndDay),
                Ui.field("Status no Lineup:", cmbStatus),
                new VBox(6, autoEnd, supervisor));
        row2.setAlignment(Pos.BOTTOM_LEFT);
        HBox actions = new HBox(8,
                Ui.button("+ Programar Atracação", "btn-success", this::create),
                Ui.button("Salvar Edição", "btn-primary", this::saveEdit),
                Ui.button("Excluir Atracação", "btn-danger", () -> delete(editing)),
                Ui.button("Limpar", "btn-outline", this::clearForm),
                formMode);
        actions.setAlignment(Pos.CENTER_LEFT);
        formMode.getStyleClass().add("muted");
        VBox box = new VBox(10, row1, row2, hint, actions);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildTable() {
        searchField.setPromptText("Buscar por berço, navio, produto ou status...");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((o, a, text) -> {
            String q = text.strip().toLowerCase(Locale.ROOT);
            arrivalsObservableList.setPredicate(s -> q.isEmpty() || searchable(s).contains(q));
        });
        SortedList<ShipSchedule> sorted = new SortedList<>(arrivalsObservableList);
        Comparator<ShipSchedule> chronological = Comparator.comparingInt(ShipSchedule::getStartDay)
                .thenComparing(s -> s.getBerth().getCode());
        sorted.comparatorProperty().bind(Bindings.createObjectBinding(
                () -> arrivalsTable.getComparator() == null ? chronological : arrivalsTable.getComparator(),
                arrivalsTable.comparatorProperty()));
        arrivalsTable.setItems(sorted);

        TableColumn<ShipSchedule, ScheduleStatus> statusCol = Ui.column("Status", ShipSchedule::getStatus,
                ScheduleStatus::getLabel, 110);
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(ScheduleStatus st, boolean empty) {
                super.updateItem(st, empty);
                if (empty || st == null) {
                    setGraphic(null);
                } else {
                    setGraphic(Ui.badge(st.getLabel(), st.getColor()));
                }
                setText(null);
            }
        });
        arrivalsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        arrivalsTable.getColumns().addAll(
                Ui.textColumn("Berço", s -> s.getBerth().toString(), 260),
                Ui.textColumn("Navio", s -> s.getVessel().getName(), 200),
                Ui.textColumn("↓ Produto Descarregado", ShipSchedule::getUnloadingProduct, 170),
                Ui.textColumn("↑ Produto Carregado", ShipSchedule::getLoadingProduct, 170),
                Ui.column("Volume", ShipSchedule::getTonnage, Ui::tons, 100),
                Ui.column("Início", ShipSchedule::getStartDay, d -> "Dia " + String.format("%02d", d), 70),
                Ui.column("Fim", ShipSchedule::getEndDay, d -> "Dia " + String.format("%02d", d), 70),
                Ui.column("Duração", ShipSchedule::getDurationDays, d -> d + " dia(s)", 75),
                statusCol,
                Ui.actionsColumn(this::fillForm, this::delete));
        arrivalsTable.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> {
            if (s != null) {
                fillForm(s);
            }
        });
        VBox.setVgrow(arrivalsTable, Priority.ALWAYS);
        Label count = new Label();
        count.getStyleClass().add("muted");
        count.textProperty().bind(Bindings.size(arrivalsObservableList).asString("%d atracação(ões) no mês"));
        HBox toolbar = new HBox(10, new Label("Histórico e auditoria de atracações — Novembro / 2026"),
                Ui.spacer(), count, searchField);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, toolbar, arrivalsTable);
        box.getStyleClass().add("card");
        return box;
    }

    private static String searchable(ShipSchedule s) {
        return (s.getBerth() + " " + s.getVessel().getName() + " " + s.getUnloadingProduct() + " "
                + s.getLoadingProduct() + " " + s.getStatus()).toLowerCase(Locale.ROOT);
    }

    /** Pré-seleciona o berço quando o operador clica em "+ Atracar Navio" no Lineup. */
    public void prepareFor(Berth berth) {
        clearForm();
        cmbBerthTarget.setValue(berth);
    }

    private void updateHint() {
        Berth b = cmbBerthTarget.getValue();
        RegisteredVessel v = cmbVesselSelect.getValue();
        StringBuilder sb = new StringBuilder();
        if (b != null) {
            sb.append(String.format("Berço: perfil \"%s\", calado máx. %s.  ", b.getCategory(), Ui.meters(b.getMaxDraft())));
        }
        if (v != null) {
            sb.append(String.format("Navio: perfil \"%s\", calado %s%s.  ", v.getProfile(), Ui.meters(v.getMaxDraft()),
                    v.getMaxDraft() >= PortService.DEEP_DRAFT_LIMIT_M
                            ? " — bloqueado nos dias de maré baixa " + service.criticalDaysText() : ""));
        }
        if (b != null && v != null && !b.getCategory().accepts(v.getProfile())) {
            sb.append("[!] Perfis incompatíveis (RN003).");
        }
        hint.setText(sb.isEmpty() ? "Selecione o berço e o navio para ver as restrições." : sb.toString());
    }

    /** RN004 — sugere o dia de saída: chegada + ⌈toneladas / taxa diária⌉. */
    private void recalcEnd() {
        if (!autoEnd.isSelected() || filling) {
            return;
        }
        try {
            long tons = Ui.parseLong(txtTonnage.getText(), "Volume");
            service.suggestedDuration(tons, cmbBerthTarget.getValue()).ifPresent(days ->
                    txtEndDay.getValueFactory().setValue(Math.min(30, txtStartDay.getValue() + days)));
        } catch (IllegalArgumentException ignored) {
            // volume ainda não preenchido
        }
    }

    private ShipScheduleRequest readForm() {
        long tons = Ui.parseLong(txtTonnage.getText(), "Volume (t)");
        return new ShipScheduleRequest(cmbBerthTarget.getValue(), cmbVesselSelect.getValue(),
                txtStartDay.getValue(), txtEndDay.getValue(), tons,
                Ui.productValue(cmbDischarge), Ui.productValue(cmbLoad), cmbStatus.getValue());
    }

    private void create() {
        submit(() -> {
            ShipScheduleRequest r = readForm();
            return trySave(r, req -> service.createShipSchedule(req));
        }, "Atracação programada", "A atracação foi validada e inserida no Lineup.");
    }

    private void saveEdit() {
        if (editing == null) {
            Ui.error("Nenhuma atracação selecionada", "Selecione uma atracação na tabela para editar.");
            return;
        }
        ShipSchedule target = editing;
        submit(() -> trySave(readForm(), req -> service.updateShipSchedule(target, req, supervisor.isSelected())),
                "Atracação atualizada", "As alterações foram validadas e o Lineup foi atualizado.");
    }

    private void submit(Supplier<ShipSchedule> action, String title, String message) {
        try {
            ShipSchedule saved = action.get();
            if (saved != null) {
                Ui.info(title, message + "\n\n" + saved.describe());
                clearForm();
                arrivalsTable.getSelectionModel().select(saved);
                arrivalsTable.scrollTo(saved);
            }
        } catch (BusinessRuleException e) {
            Ui.error("Regra de negócio violada (" + e.getRule() + ")", e.getMessage());
        } catch (IllegalArgumentException e) {
            Ui.error("Dados inválidos", e.getMessage());
        }
    }

    /** Em caso de conflito (RN001/RN002), oferece o próximo período vago encontrado (RF011 - E1). */
    private ShipSchedule trySave(ShipScheduleRequest r, java.util.function.Function<ShipScheduleRequest, ShipSchedule> op) {
        try {
            return op.apply(r);
        } catch (ScheduleConflictException e) {
            OptionalInt slot = e.getSuggestedStartDay();
            if (slot.isEmpty()) {
                throw new BusinessRuleException(e.getRule(), e.getMessage()
                        + "\n\nNão há período livre neste berço no mês para essa duração.");
            }
            ShipScheduleRequest suggestion = r.withStartDay(slot.getAsInt());
            boolean accept = Ui.confirm("Conflito de agendamento (" + e.getRule() + ")", e.getMessage()
                    + String.format("%n%nPróximo período vago encontrado: Dia %d ao Dia %d.%nDeseja programar nesse período?",
                    suggestion.startDay(), suggestion.endDay()));
            return accept ? op.apply(suggestion) : null;
        }
    }

    private void delete(ShipSchedule s) {
        if (s == null) {
            Ui.error("Nenhuma atracação selecionada", "Selecione uma atracação na tabela para excluir.");
            return;
        }
        if (Ui.confirm("Excluir atracação", "Excluir a atracação do navio " + s.getVessel().getName()
                + " no berço " + s.getBerth().getCode() + " (dias " + s.getStartDay() + "–" + s.getEndDay() + ")?")) {
            Ui.run(() -> {
                service.deleteShipSchedule(s, supervisor.isSelected());
                clearForm();
            });
        }
    }

    private void fillForm(ShipSchedule s) {
        if (s == null) {
            return;
        }
        filling = true;
        editing = s;
        cmbBerthTarget.setValue(s.getBerth());
        cmbVesselSelect.setValue(s.getVessel());
        Ui.setProduct(cmbDischarge, s.getUnloadingProduct());
        Ui.setProduct(cmbLoad, s.getLoadingProduct());
        txtTonnage.setText(Ui.number(s.getTonnage()));
        txtStartDay.getValueFactory().setValue(s.getStartDay());
        txtEndDay.getValueFactory().setValue(s.getEndDay());
        cmbStatus.setValue(s.getStatus());
        filling = false;
        updateHint();
        formMode.setText("Editando atracação #" + s.getId() + " — " + s.getVessel().getName());
    }

    private void clearForm() {
        filling = true;
        editing = null;
        arrivalsTable.getSelectionModel().clearSelection();
        cmbBerthTarget.setValue(null);
        cmbVesselSelect.setValue(null);
        Ui.setProduct(cmbDischarge, null);
        Ui.setProduct(cmbLoad, null);
        txtTonnage.clear();
        txtStartDay.getValueFactory().setValue(1);
        txtEndDay.getValueFactory().setValue(2);
        cmbStatus.setValue(ScheduleStatus.SCHEDULED);
        filling = false;
        updateHint();
        formMode.setText("Selecione uma atracação na tabela para editar ou preencha o formulário.");
    }
}
