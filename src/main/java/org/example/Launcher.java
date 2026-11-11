package org.example;

import javafx.application.Application;

/** Ponto de entrada (classe que não estende Application, evita erro de runtime JavaFX ausente). */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
