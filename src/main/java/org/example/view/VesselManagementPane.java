package org.example.view;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.dto.VesselRequest;
import org.example.model.CargoCategory;
import org.example.model.RegisteredVessel;
import org.example.service.PortService;

import java.util.Locale;

/** Cadastro do catálogo de navios mercantes (RF008) e consulta/filtragem da frota (RF009). */
public class VesselManagementPane extends BorderPane {

    private final PortService service;
    private final TextField txtImoCode = new TextField();
    private final TextField txtShipName = new TextField();
    private final TextField txtVesselClass = new TextField();
    private final TextField txtShipDraft = new TextField();
    private final TextField txtDwtTonnage = new TextField();
    private final ComboBox<CargoCategory> cmbProfile = new ComboBox<>();
    private final ComboBox<String> cmbDischarge;
    private final ComboBox<String> cmbLoad;
    private final TextField searchField = new TextField();
    private final TableView<RegisteredVessel> vesselTable = new TableView<>();
    private final Label formMode = new Label();
    private final FilteredList<RegisteredVessel> vesselObservableList;
    private RegisteredVessel editing;

    public VesselManagementPane(PortService service) {
        this.service = service;
        this.cmbDischarge = Ui.productCombo(service::knownProducts);
        this.cmbLoad = Ui.productCombo(service::knownProducts);
        this.vesselObservableList = new FilteredList<>(service.getVessels());
        getStyleClass().add("content");
        setTop(new VBox(10,
                Ui.pageHeader("PORTOS — CATÁLOGO DE NAVIOS",
                        "Navios Mercantes — Cadastro, Edição e Consulta da Frota",
                        "Código IMO único (RN007), classe de porte, calado máximo, tonelagem padrão e produtos padrão de descarga ↓ e carga ↑. O perfil de carga é inferido pela classe quando não informado."),
                buildForm()));
        VBox table = buildTable();
        BorderPane.setMargin(table, new Insets(10, 0, 0, 0));
        setCenter(table);
        service.dataVersionProperty().addListener((o, a, b) -> vesselTable.refresh());
        clearForm();
    }

    private VBox buildForm() {
        txtImoCode.setPromptText("ex.: IMO-9810101");
        txtShipName.setPromptText("Nome do navio");
        txtVesselClass.setPromptText("ex.: Panamax Graneleiro");
        txtShipDraft.setPromptText("ex.: 14.2");
        txtDwtTonnage.setPromptText("ex.: 68.500");
        cmbProfile.setPromptText("Automático pela classe");
        cmbProfile.getItems().setAll(CargoCategory.values());
        cmbProfile.setPromptText("Perfil de carga");
        HBox row1 = new HBox(10,
                Ui.field("Código IMO:", txtImoCode),
                Ui.field("Nome do Navio:", txtShipName),
                Ui.field("Classe de Porte:", txtVesselClass),
                Ui.field("Calado Máximo (m):", txtShipDraft),
                Ui.field("Tonelagem padrão (t):", txtDwtTonnage));
        HBox row2 = new HBox(10,
                Ui.field("Perfil de Carga:", cmbProfile),
                Ui.field("Produto padrão Descarga ↓:", cmbDischarge),
                Ui.field("Produto padrão Carga ↑:", cmbLoad));
        HBox actions = new HBox(8,
                Ui.button("+ Cadastrar Navio", "btn-success", this::create),
                Ui.button("Atualizar Navio", "btn-primary", this::saveEdit),
                Ui.button("Excluir Navio", "btn-danger", () -> delete(editing)),
                Ui.button("Limpar", "btn-outline", this::clearForm),
                formMode);
        actions.setAlignment(Pos.CENTER_LEFT);
        formMode.getStyleClass().add("muted");
        VBox box = new VBox(10, row1, row2, actions);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildTable() {
        searchField.setPromptText("Buscar por nome, IMO ou classe...");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((o, a, text) -> {
            String q = text.strip().toLowerCase(Locale.ROOT);
            vesselObservableList.setPredicate(v -> q.isEmpty()
                    || (v.getName() + " " + v.getImoCode() + " " + v.getVesselClass()).toLowerCase(Locale.ROOT).contains(q));
        });
        SortedList<RegisteredVessel> sorted = new SortedList<>(vesselObservableList);
        sorted.comparatorProperty().bind(vesselTable.comparatorProperty());
        vesselTable.setItems(sorted);
        vesselTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        vesselTable.getColumns().addAll(
                Ui.textColumn("IMO", RegisteredVessel::getImoCode, 110),
                Ui.textColumn("Nome", RegisteredVessel::getName, 240),
                Ui.textColumn("Classe", RegisteredVessel::getVesselClass, 200),
                Ui.column("Calado", RegisteredVessel::getMaxDraft, Ui::meters, 80),
                Ui.column("Tonelagem Padrão", RegisteredVessel::getDefaultTonnage, Ui::tons, 110),
                Ui.textColumn("Perfil", v -> v.getProfile().getLabel(), 170),
                Ui.textColumn("Descarga ↓", RegisteredVessel::getDefaultUnloadProduct, 160),
                Ui.textColumn("Carga ↑", RegisteredVessel::getDefaultLoadProduct, 160),
                Ui.actionsColumn(this::fillForm, this::delete));
        vesselTable.getSelectionModel().selectedItemProperty().addListener((o, a, v) -> {
            if (v != null) {
                fillForm(v);
            }
        });
        VBox.setVgrow(vesselTable, Priority.ALWAYS);
        Label count = new Label();
        count.getStyleClass().add("muted");
        count.textProperty().bind(Bindings.size(vesselObservableList).asString("%d navio(s) listados"));
        HBox toolbar = new HBox(10, new Label("Frota de navios cadastrados"), Ui.spacer(), count, searchField);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, toolbar, vesselTable);
        box.getStyleClass().add("card");
        return box;
    }

    private VesselRequest readForm() {
        return new VesselRequest(txtImoCode.getText(), txtShipName.getText(), txtVesselClass.getText(),
                Ui.parseDecimal(txtShipDraft.getText(), "Calado Máximo"),
                Ui.parseLong(txtDwtTonnage.getText(), "Tonelagem padrão"),
                cmbProfile.getValue(), Ui.productValue(cmbDischarge), Ui.productValue(cmbLoad));
    }

    private void create() {
        if (Ui.run(() -> {
            RegisteredVessel v = service.createVessel(readForm());
            vesselTable.scrollTo(v);
        })) {
            Ui.info("Navio cadastrado", "O navio está disponível para programação de atracações.");
            clearForm();
        }
    }

    private void saveEdit() {
        if (editing == null) {
            Ui.error("Nenhum navio selecionado", "Selecione um navio na tabela para editar.");
            return;
        }
        if (Ui.run(() -> service.updateVessel(editing, readForm()))) {
            Ui.info("Navio atualizado", "As alterações foram salvas.");
        }
    }

    private void delete(RegisteredVessel v) {
        if (v == null) {
            Ui.error("Nenhum navio selecionado", "Selecione um navio na tabela para excluir.");
            return;
        }
        int linked = service.schedulesOf(v).size();
        String msg = "Deseja excluir o navio " + v + "?"
                + (linked > 0 ? "\n\nATENÇÃO: " + linked + " atracação(ões) deste navio também serão excluídas." : "");
        if (Ui.confirm("Excluir navio", msg)) {
            service.deleteVessel(v);
            clearForm();
        }
    }

    private void fillForm(RegisteredVessel v) {
        if (v == null) {
            return;
        }
        editing = v;
        txtImoCode.setText(v.getImoCode());
        txtImoCode.setDisable(true);
        txtShipName.setText(v.getName());
        txtVesselClass.setText(v.getVesselClass());
        txtShipDraft.setText(String.valueOf(v.getMaxDraft()));
        txtDwtTonnage.setText(Ui.number(v.getDefaultTonnage()));
        cmbProfile.setValue(v.getProfile());
        Ui.setProduct(cmbDischarge, v.getDefaultUnloadProduct());
        Ui.setProduct(cmbLoad, v.getDefaultLoadProduct());
        formMode.setText("Editando " + v.getImoCode());
    }

    private void clearForm() {
        editing = null;
        vesselTable.getSelectionModel().clearSelection();
        txtImoCode.clear();
        txtImoCode.setDisable(false);
        txtShipName.clear();
        txtVesselClass.clear();
        txtShipDraft.clear();
        txtDwtTonnage.clear();
        cmbProfile.setValue(null);
        Ui.setProduct(cmbDischarge, null);
        Ui.setProduct(cmbLoad, null);
        formMode.setText("Selecione um navio na tabela para editar ou preencha os campos para cadastrar.");
    }

    public ObservableList<RegisteredVessel> getVesselObservableList() {
        return vesselObservableList;
    }
}
