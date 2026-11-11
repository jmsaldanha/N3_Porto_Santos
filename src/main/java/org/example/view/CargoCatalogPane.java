package org.example.view;

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
import org.example.dto.CargoProductRequest;
import org.example.dto.RecalculationResult;
import org.example.model.BusinessRuleException;
import org.example.model.CargoCategory;
import org.example.model.CargoDirection;
import org.example.model.CargoProduct;
import org.example.service.PortService;

/** Catálogo de cargas e perfis portuários APS (RF007). */
public class CargoCatalogPane extends BorderPane {

    private final PortService service;
    private final TextField txtCargoCode = new TextField();
    private final TextField txtCargoName = new TextField();
    private final ComboBox<CargoDirection> cmbDirection = new ComboBox<>();
    private final ComboBox<CargoCategory> cmbCargoProfile = new ComboBox<>();
    private final TextField txtTonsPerDay = new TextField();
    private final ComboBox<String> cmbRateUnit = new ComboBox<>();
    private final TableView<CargoProduct> cargoCatalogTable = new TableView<>();
    private final Label formMode = new Label();
    private CargoProduct editing;

    public CargoCatalogPane(PortService service) {
        this.service = service;
        getStyleClass().add("content");
        setTop(new VBox(10,
                Ui.pageHeader("PORTOS — CATÁLOGO DE CARGAS",
                        "Catálogo de Cargas e Perfis Portuários APS",
                        "Commodities estratégicas, sentido operacional (↓ Descarga, ↑ Carga, ↓↑ Misto) e taxa média diária "
                                + "de movimentação. A taxa define a duração das atracações (RN004)."),
                buildForm()));
        VBox table = buildTable();
        BorderPane.setMargin(table, new Insets(10, 0, 0, 0));
        setCenter(table);
        service.dataVersionProperty().addListener((o, a, b) -> cargoCatalogTable.refresh());
        clearForm();
    }

    private VBox buildForm() {
        txtCargoCode.setPromptText("ex.: CRG-16");
        txtCargoName.setPromptText("Nome da commodity");
        cmbDirection.getItems().setAll(CargoDirection.values());
        cmbDirection.setPromptText("Sentido");
        cmbCargoProfile.getItems().setAll(CargoCategory.values());
        cmbCargoProfile.setPromptText("Perfil de carga");
        txtTonsPerDay.setPromptText("ex.: 36.000");
        cmbRateUnit.getItems().setAll("t/dia", "mov/dia", "unid/dia", "pax/dia");
        cmbRateUnit.setValue("t/dia");
        HBox fields = new HBox(10,
                Ui.field("Código:", txtCargoCode),
                Ui.field("Nome do Produto:", txtCargoName),
                Ui.field("Sentido:", cmbDirection),
                Ui.field("Perfil de Carga APS:", cmbCargoProfile),
                Ui.field("Taxa Média Diária:", txtTonsPerDay),
                Ui.field("Unidade:", cmbRateUnit));
        HBox actions = new HBox(8,
                Ui.button("+ Cadastrar Produto", "btn-success", this::create),
                Ui.button("Salvar Edição", "btn-primary", this::saveEdit),
                Ui.button("Excluir Produto", "btn-danger", () -> delete(editing)),
                Ui.button("Limpar", "btn-outline", this::clearForm),
                formMode);
        actions.setAlignment(Pos.CENTER_LEFT);
        formMode.getStyleClass().add("muted");
        VBox box = new VBox(10, fields, actions);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildTable() {
        SortedList<CargoProduct> sorted = new SortedList<>(service.getCargoCatalog());
        sorted.comparatorProperty().bind(cargoCatalogTable.comparatorProperty());
        cargoCatalogTable.setItems(sorted);
        cargoCatalogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cargoCatalogTable.getColumns().addAll(
                Ui.textColumn("Código", CargoProduct::getCode, 100),
                Ui.textColumn("Nome do Produto", CargoProduct::getName, 260),
                Ui.textColumn("Sentido", c -> c.getDirection().getLabel(), 120),
                Ui.textColumn("Perfil de Carga", c -> c.getProfile().getLabel(), 220),
                Ui.textColumn("Taxa Média", CargoProduct::getRateText, 140),
                Ui.column("Atracações do Perfil", c -> service.schedulesUsingProfile(c), n -> String.valueOf(n), 130),
                Ui.actionsColumn(this::fillForm, this::delete));
        cargoCatalogTable.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> {
            if (c != null) {
                fillForm(c);
            }
        });
        VBox.setVgrow(cargoCatalogTable, Priority.ALWAYS);
        Label title = new Label("Commodities portuárias cadastradas");
        VBox box = new VBox(8, title, cargoCatalogTable);
        box.getStyleClass().add("card");
        return box;
    }

    private CargoProductRequest readForm() {
        return new CargoProductRequest(txtCargoCode.getText(), txtCargoName.getText(), cmbDirection.getValue(),
                cmbCargoProfile.getValue(), Ui.parseLong(txtTonsPerDay.getText(), "Taxa Diária"),
                cmbRateUnit.getValue());
    }

    private void create() {
        if (Ui.run(() -> service.createCargoProduct(readForm()))) {
            Ui.info("Produto cadastrado", "A commodity foi incluída no catálogo.");
            clearForm();
        }
    }

    private void saveEdit() {
        if (editing == null) {
            Ui.error("Nenhum produto selecionado", "Selecione um produto na tabela para editar.");
            return;
        }
        RecalculationResult[] result = new RecalculationResult[1];
        if (Ui.run(() -> result[0] = service.updateCargoProduct(editing, readForm()))) {
            RecalculationResult r = result[0];
            Ui.info("Produto atualizado", r.recalculated() + r.keptByConflict() == 0
                    ? "Alterações salvas."
                    : String.format("Taxa diária alterada. Atracações futuras recalculadas (RN004): %d.%s",
                    r.recalculated(), r.keptByConflict() > 0
                            ? "\nMantidas sem alteração por conflito de berço/maré: " + r.keptByConflict() : ""));
        }
    }

    private void delete(CargoProduct c) {
        if (c == null) {
            Ui.error("Nenhum produto selecionado", "Selecione um produto na tabela para excluir.");
            return;
        }
        if (Ui.confirm("Excluir produto", "Deseja excluir \"" + c.getName() + "\" do catálogo?")) {
            try {
                service.deleteCargoProduct(c);
                clearForm();
            } catch (BusinessRuleException e) {
                Ui.error("Exclusão bloqueada (" + e.getRule() + ")", e.getMessage());
            }
        }
    }

    private void fillForm(CargoProduct c) {
        if (c == null) {
            return;
        }
        editing = c;
        txtCargoCode.setText(c.getCode());
        txtCargoCode.setDisable(true);
        txtCargoName.setText(c.getName());
        cmbDirection.setValue(c.getDirection());
        cmbCargoProfile.setValue(c.getProfile());
        txtTonsPerDay.setText(Ui.number(c.getAverageRate()));
        cmbRateUnit.setValue(c.getRateUnit());
        formMode.setText("Editando " + c.getCode());
    }

    private void clearForm() {
        editing = null;
        cargoCatalogTable.getSelectionModel().clearSelection();
        txtCargoCode.clear();
        txtCargoCode.setDisable(false);
        txtCargoName.clear();
        cmbDirection.setValue(null);
        cmbCargoProfile.setValue(null);
        txtTonsPerDay.clear();
        cmbRateUnit.setValue("t/dia");
        formMode.setText("Selecione uma linha para editar a taxa diária ou cadastre um novo produto.");
    }
}
