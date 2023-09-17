package edu.utah.bmi.simple.gui.controller;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;

public class SettingPanelController {

    private double savedHeight;
    private double[] dividPosition;
    @FXML
    private TitledPane executePane;

    @FXML
    private SplitPane panesplitter;

    @FXML
    private void initialize() {


        executePane.expandedProperty().addListener((observable, oldValue, newValue) -> {
            if (executePane.isExpanded()) {
                dividPosition = panesplitter.getDividerPositions();
                savedHeight = executePane.getHeight();
            } else {
                panesplitter.setDividerPositions(dividPosition);
                executePane.setPrefHeight(savedHeight);
            }
        });
    }
}
