package org.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.gateway.MockPortDataGateway;
import org.example.gateway.PortDataGateway;
import org.example.mock.SantosPortDataFactory;
import org.example.model.Berth;
import org.example.service.PortService;
import org.example.view.ArrivalsPane;
import org.example.view.BerthManagementPane;
import org.example.view.CargoCatalogPane;
import org.example.view.LineupPane;
import org.example.view.TideMonitorPane;
import org.example.view.VesselManagementPane;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Janela principal do Gêmeo Digital do Porto de Santos (View raiz do MVC).
 * Monta o menu lateral e troca o conteúdo central entre as telas do módulo Portos.
 */
public class App extends Application {

    private PortService portService;
    private final BorderPane root = new BorderPane();
    private final Map<String, Node> views = new LinkedHashMap<>();
    private final Map<String, Button> menuButtons = new LinkedHashMap<>();
    private LineupPane lineupPane;
    private ArrivalsPane arrivalsPane;
    private Node currentView;

    @Override
    public void start(Stage stage) {
        // Checkpoint 1: gateway mock em memória. Na entrega final: new BackendApiClient("http://localhost:8080").
        PortDataGateway gateway = new MockPortDataGateway(SantosPortDataFactory.create());
        portService = new PortService(gateway);
        portService.loadAll();

        lineupPane = new LineupPane(portService, this::openSchedulingFor);
        arrivalsPane = new ArrivalsPane(portService);
        views.put("Lineup", lineupPane);
        views.put("Berços", new BerthManagementPane(portService));
        views.put("Cargas", new CargoCatalogPane(portService));
        views.put("Navios", new VesselManagementPane(portService));
        views.put("Marés", new TideMonitorPane(portService));
        views.put("Atracações", arrivalsPane);

        root.setLeft(buildSidebar());
        root.getStyleClass().add("root-pane");
        navigate("Lineup");

        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/org/example/styles.css").toExternalForm());
        // RF003 - A1: setas esquerda/direita trocam a página do Lineup.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (currentView != lineupPane || e.getTarget() instanceof TextInputControl) {
                return;
            }
            if (e.getCode() == KeyCode.RIGHT) {
                lineupPane.nextPage();
                e.consume();
            } else if (e.getCode() == KeyCode.LEFT) {
                lineupPane.previousPage();
                e.consume();
            }
        });
        stage.setTitle("Gêmeo Digital do Porto de Santos — APS · FIPS (Checkpoint 1 — dados mock)");
        stage.setScene(scene);
        stage.setMinWidth(1200);
        stage.setMinHeight(760);
        stage.show();
    }

    private VBox buildSidebar() {
        Label tag = new Label("PORTO DE SANTOS  •  APS · FIPS");
        tag.getStyleClass().add("sidebar-tag");
        Label brand = new Label("Gêmeo Digital do Porto");
        brand.getStyleClass().add("sidebar-brand");
        Label sub = new Label("Portos • Ferrovia • Rodovia");
        sub.getStyleClass().add("sidebar-sub");
        VBox sidebar = new VBox(4, tag, brand, sub, section("PORTOS"));
        for (String name : views.keySet()) {
            Button b = new Button(name);
            b.getStyleClass().add("menu-item");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> navigate(name));
            menuButtons.put(name, b);
            sidebar.getChildren().add(b);
        }
        sidebar.getChildren().add(section("FERROVIA"));
        for (String future : new String[]{"Pera & Ramais", "Moegas & AMVs", "Locomotivas", "Vagões"}) {
            sidebar.getChildren().add(disabledItem(future));
        }
        sidebar.getChildren().add(section("RODOVIA"));
        for (String future : new String[]{"Gantt (180 Docas)", "Docas & Gates", "Agendamentos"}) {
            sidebar.getChildren().add(disabledItem(future));
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = new Label("Checkpoint 1 — front-end com\ndados mock em memória");
        footer.getStyleClass().add("sidebar-footer");
        sidebar.getChildren().addAll(spacer, footer);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 10, 16, 10));
        sidebar.setPrefWidth(220);
        sidebar.setMinWidth(220);
        return sidebar;
    }

    private Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("sidebar-section");
        return l;
    }

    private Button disabledItem(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("menu-item", "menu-item-future");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setTooltip(new Tooltip("Módulo previsto para as próximas sprints."));
        b.setOnAction(e -> { });
        return b;
    }

    private void navigate(String name) {
        currentView = views.get(name);
        root.setCenter(currentView);
        menuButtons.forEach((n, b) -> b.getStyleClass().remove("menu-item-active"));
        menuButtons.get(name).getStyleClass().add("menu-item-active");
    }

    private void openSchedulingFor(Berth berth) {
        navigate("Atracações");
        arrivalsPane.prepareFor(berth);
    }

    /** RF003/RF004 — atalho para a paginação do Lineup. */
    public void goToPage(int page) {
        lineupPane.goToPage(page);
    }

    /** RF001 - A1 — reaplica os filtros do Lineup. */
    public void applyFilter() {
        lineupPane.applyFilter();
    }
}
