package org.example.view;

import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.dto.BerthRequest;
import org.example.model.Berth;
import org.example.model.CargoCategory;
import org.example.service.PortService;

import java.util.Locale;

/** Cadastro, edição e exclusão de berços (RF005) + tabela de auditoria com 60 berços (RF006). */
public class BerthManagementPane extends BorderPane {

    private final PortService service;
    private final TextField txtBerthCode = new TextField();
    private final TextField txtBerthName = new TextField();
    private final TextField txtSectorName = new TextField();
    private final TextField txtMaxDraft = new TextField();
    private final ComboBox<CargoCategory> cmbCategory = new ComboBox<>();
    private final TextField quickFilter = new TextField();
    private final TableView<Berth> berthsTable = new TableView<>();
    private final Label formMode = new Label();
    private Berth editing;

    public BerthManagementPane(PortService service) {
        this.service = service;
        getStyleClass().add("content");
        setTop(new VBox(10,
                Ui.pageHeader("PORTOS — GESTÃO DE DADOS & CRUD DO LINEUP",
                        "Berços de Atracação — Cadastro, Edição e Exclusão",
                        "Gerencie os berços do Porto de Santos. Qualquer alteração atualiza automaticamente o gráfico de Lineup (RN008)."),
                buildForm()));
        VBox tableSection = buildBerthTableSection();
        BorderPane.setMargin(tableSection, new Insets(10, 0, 0, 0));
        setCenter(tableSection);
        service.dataVersionProperty().addListener((o, a, b) -> berthsTable.refresh());
        clearForm();
    }

    private VBox buildForm() {
        txtBerthCode.setPromptText("ex.: SV-99");
        txtBerthName.setPromptText("ex.: Berço 61 — Saboó Sul");
        txtSectorName.setPromptText("ex.: Saboó / Valongo");
        txtMaxDraft.setPromptText("ex.: 14.5");
        cmbCategory.getItems().setAll(CargoCategory.values());
        cmbCategory.setPromptText("Perfil de carga");

        HBox fields = new HBox(10,
                Ui.field("Código do Berço:", txtBerthCode),
                Ui.field("Nome do Berço / Terminal:", txtBerthName),
                Ui.field("Região / Setor do Porto:", txtSectorName),
                Ui.field("Calado Máximo (m):", txtMaxDraft),
                Ui.field("Perfil de Carga:", cmbCategory));
        Button btnCreate = Ui.button("+ Cadastrar Berço Novo", "btn-success", this::create);
        Button btnSave = Ui.button("Salvar Edição do Berço", "btn-primary", this::saveEdit);
        Button btnDelete = Ui.button("Excluir Berço", "btn-danger", () -> delete(editing));
        Button btnClear = Ui.button("Limpar", "btn-outline", this::clearForm);
        formMode.getStyleClass().add("muted");
        HBox actions = new HBox(8, btnCreate, btnSave, btnDelete, btnClear, formMode);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10, fields, actions);
        box.getStyleClass().add("card");
        return box;
    }

    /** Seção da tabela de berços com filtro rápido, edição e exclusão na linha (RF006). */
    private VBox buildBerthTableSection() {
        FilteredList<Berth> filtered = new FilteredList<>(service.getBerths());
        quickFilter.setPromptText("Filtro rápido: código, nome, setor ou perfil...");
        quickFilter.textProperty().addListener((o, a, text) -> {
            String q = text.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(b -> q.isEmpty() || (b.getCode() + " " + b.getName() + " " + b.getSectorName()
                    + " " + b.getCategory()).toLowerCase(Locale.ROOT).contains(q));
        });
        SortedList<Berth> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(berthsTable.comparatorProperty());
        berthsTable.setItems(sorted);

        berthsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        berthsTable.getColumns().addAll(
                Ui.textColumn("Código do Berço", Berth::getCode, 110),
                Ui.textColumn("Nome do Berço / Terminal", Berth::getName, 330),
                Ui.textColumn("Região / Cais", Berth::getSectorName, 230),
                Ui.column("Calado Máx.", Berth::getMaxDraft, Ui::meters, 90),
                Ui.textColumn("Perfil de Carga", b -> b.getCategory().getLabel(), 190),
                Ui.column("Navios no Lineup", b -> b.getSchedules().size(), n -> n + " navio(s)", 120),
                Ui.actionsColumn(this::fillForm, this::delete));
        berthsTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) {
                fillForm(b);
            }
        });
        berthsTable.setPlaceholder(new Label("Nenhum berço cadastrado."));
        VBox.setVgrow(berthsTable, Priority.ALWAYS);

        Label count = new Label();
        count.getStyleClass().add("muted");
        count.textProperty().bind(javafx.beans.binding.Bindings.size(filtered).asString("%d berço(s) listados"));
        HBox toolbar = new HBox(10, new Label("Berços cadastrados"), Ui.spacer(), count, quickFilter);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        quickFilter.setPrefWidth(320);
        VBox box = new VBox(8, toolbar, berthsTable);
        box.getStyleClass().add("card");
        return box;
    }

    private BerthRequest readForm() {
        return new BerthRequest(txtBerthCode.getText(), txtBerthName.getText(), txtSectorName.getText(),
                Ui.parseDecimal(txtMaxDraft.getText(), "Calado Máximo"), cmbCategory.getValue());
    }

    private void create() {
        if (Ui.run(() -> {
            Berth b = service.createBerth(readForm());
            berthsTable.getSelectionModel().select(b);
            berthsTable.scrollTo(b);
        })) {
            Ui.info("Berço cadastrado", "O berço foi incluído e já aparece no Lineup.");
            clearForm();
        }
    }

    private void saveEdit() {
        if (editing == null) {
            Ui.error("Nenhum berço selecionado", "Selecione um berço na tabela para editar.");
            return;
        }
        if (Ui.run(() -> service.updateBerth(editing, readForm()))) {
            Ui.info("Berço atualizado", "As alterações foram salvas e o Lineup foi atualizado.");
        }
    }

    private void delete(Berth b) {
        if (b == null) {
            Ui.error("Nenhum berço selecionado", "Selecione um berço na tabela para excluir.");
            return;
        }
        int linked = b.getSchedules().size();
        String msg = "Deseja excluir o berço " + b + "?"
                + (linked > 0 ? "\n\nATENÇÃO: " + linked + " atracação(ões) vinculada(s) serão excluídas em cascata (RN024)." : "");
        if (Ui.confirm("Excluir berço", msg)) {
            int removed = service.deleteBerth(b);
            clearForm();
            Ui.info("Berço excluído", "Berço removido. Atracações removidas em cascata: " + removed + ".");
        }
    }

    private void fillForm(Berth b) {
        if (b == null) {
            return;
        }
        editing = b;
        txtBerthCode.setText(b.getCode());
        txtBerthCode.setDisable(true);
        txtBerthName.setText(b.getName());
        txtSectorName.setText(b.getSectorName());
        txtMaxDraft.setText(String.valueOf(b.getMaxDraft()));
        cmbCategory.setValue(b.getCategory());
        formMode.setText("Editando " + b.getCode() + " — altere os campos e clique em \"Salvar Edição do Berço\".");
    }

    private void clearForm() {
        editing = null;
        berthsTable.getSelectionModel().clearSelection();
        txtBerthCode.clear();
        txtBerthCode.setDisable(false);
        txtBerthName.clear();
        txtSectorName.clear();
        txtMaxDraft.clear();
        cmbCategory.setValue(null);
        formMode.setText("Selecione um berço na tabela para editá-lo, ou preencha o formulário para cadastrar um novo.");
    }
}
