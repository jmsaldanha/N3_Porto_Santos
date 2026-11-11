package org.example.view;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.model.BusinessRuleException;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Utilitários de interface compartilhados pelas telas. */
public final class Ui {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Ui() {
    }

    // ------------------------------------------------------------------ formatação / parsing

    public static String tons(long tons) {
        return NumberFormat.getIntegerInstance(PT_BR).format(tons) + " t";
    }

    public static String number(double value) {
        return NumberFormat.getIntegerInstance(PT_BR).format(Math.round(value));
    }

    public static String meters(double value) {
        return String.format(PT_BR, "%.1f m", value);
    }

    /** Aceita "14.5", "14,5" ou "14,5m". */
    public static double parseDecimal(String text, String field) {
        String t = text == null ? "" : text.strip().toLowerCase().replace("m", "").replace(" ", "").replace(',', '.');
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido em \"" + field + "\": " + text);
        }
    }

    /** Aceita inteiros com separador de milhar pt-BR, ex.: "68.500" ou "30.000 t/dia". */
    public static long parseLong(String text, String field) {
        String digits = text == null ? "" : text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("Informe um número válido em \"" + field + "\".");
        }
        return Long.parseLong(digits);
    }

    // ------------------------------------------------------------------ diálogos (RN025)

    public static void error(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(title);
        a.setTitle("Gêmeo Digital — validação");
        a.showAndWait();
    }

    public static void info(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(title);
        a.setTitle("Gêmeo Digital");
        a.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(title);
        a.setTitle("Confirmação");
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    /** Executa uma ação de formulário traduzindo exceções de validação em diálogos amigáveis. */
    public static boolean run(Runnable action) {
        try {
            action.run();
            return true;
        } catch (BusinessRuleException e) {
            error("Regra de negócio violada (" + e.getRule() + ")", e.getMessage());
        } catch (IllegalArgumentException e) {
            error("Dados inválidos", e.getMessage());
        }
        return false;
    }

    // ------------------------------------------------------------------ componentes

    public static VBox pageHeader(String crumb, String title, String subtitle) {
        Label c = new Label(crumb);
        c.getStyleClass().add("crumb");
        Label t = new Label(title);
        t.getStyleClass().add("page-title");
        HBox line = new HBox(10, c, t);
        line.setAlignment(Pos.CENTER_LEFT);
        Label s = new Label(subtitle);
        s.getStyleClass().add("page-subtitle");
        s.setWrapText(true);
        VBox box = new VBox(4, line, s);
        box.getStyleClass().add("card");
        return box;
    }

    public static VBox field(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(3, l, control);
        if (control instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    public static Label badge(String text, String color) {
        Label b = new Label(text);
        b.getStyleClass().add("badge");
        b.setStyle("-fx-background-color: " + color + ";");
        return b;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Button button(String text, String styleClass, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(styleClass);
        b.setOnAction(e -> action.run());
        return b;
    }

    /** Coluna genérica com valor comparável (ordenação correta) e formatação própria. */
    public static <T, V extends Comparable<V>> TableColumn<T, V> column(String title, Function<T, V> value,
                                                                         Function<V, String> format, double width) {
        TableColumn<T, V> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(V item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        });
        col.setPrefWidth(width);
        return col;
    }

    public static <T> TableColumn<T, String> textColumn(String title, Function<T, String> value, double width) {
        return column(title, value, Function.identity(), width);
    }

    /** Coluna "Ações" com botões Editar / Excluir em cada linha (RF006, RF009, RF012). */
    public static <T> TableColumn<T, Void> actionsColumn(Consumer<T> onEdit, Consumer<T> onDelete) {
        TableColumn<T, Void> col = new TableColumn<>("Ações");
        col.setSortable(false);
        col.setPrefWidth(140);
        col.setCellFactory(c -> new TableCell<>() {
            private final Button edit = new Button("Editar");
            private final Button delete = new Button("Excluir");
            private final HBox box = new HBox(6, edit, delete);

            {
                edit.getStyleClass().add("btn-mini");
                delete.getStyleClass().addAll("btn-mini", "btn-mini-danger");
                edit.setOnAction(e -> onEdit.accept(getTableRow().getItem()));
                delete.setOnAction(e -> onDelete.accept(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : box);
            }
        });
        return col;
    }

    /**
     * Campo de produto: texto livre (como na coluna do banco) com sugestões dos produtos já
     * conhecidos. Vazio equivale a "Nenhum (Lastro)".
     */
    public static ComboBox<String> productCombo(java.util.function.Supplier<List<String>> suggestions) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.setPromptText("Nenhum (Lastro)");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setOnShowing(e -> {
            String typed = productValue(combo);
            combo.getItems().setAll(suggestions.get());
            combo.getEditor().setText(typed);
        });
        return combo;
    }

    /** Valor digitado/selecionado no campo de produto. */
    public static String productValue(ComboBox<String> combo) {
        String text = combo.getEditor().getText();
        return text == null || text.isBlank() ? combo.getValue() : text.strip();
    }

    public static void setProduct(ComboBox<String> combo, String value) {
        combo.setValue(value);
        combo.getEditor().setText(value == null ? "" : value);
    }
}
