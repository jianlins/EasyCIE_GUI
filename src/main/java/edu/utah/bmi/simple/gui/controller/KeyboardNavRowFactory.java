package edu.utah.bmi.simple.gui.controller;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.util.List;

import static edu.utah.bmi.simple.gui.controller.CellFactories.colorCellFactory;
import static edu.utah.bmi.simple.gui.controller.CellFactories.colorCellHidenFactory;


public class KeyboardNavRowFactory<T> implements
        Callback<TableView<T>, TableRow<T>> {

    private final Callback<TableView<T>, TableRow<T>> baseFactory;

    public KeyboardNavRowFactory(Callback<TableView<T>, TableRow<T>> baseFactory) {
        this.baseFactory = baseFactory;
    }

    @Override
    public TableRow<T> call(TableView<T> tableView) {
        final TableRow<T> row;
        if (baseFactory == null) {
            row = new TableRow<>();
        } else {
            row = baseFactory.call(tableView);
        }
        row.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                ObservableList<TablePosition> positions = tableView.getSelectionModel().getSelectedCells();
                if (positions.size() > 0) {
                    TablePosition position = tableView.getSelectionModel().getSelectedCells().get(0);
                    int colId = position.getColumn();
                    int rowId = position.getRow();
                    TableColumn col = position.getTableColumn();
                    Object value = col.getCellData(rowId);
                    Callback cellFactory = col.getCellFactory();
                    String html;
                    if (cellFactory == colorCellFactory) {
                        html = ColorAnnotationCell.generateHTML(value);
                    } else if (cellFactory == colorCellHidenFactory) {
                        html = ColorAnnotationCellHide.generateHTML(value);
                    } else {
                        html = value.toString();
                    }
                    TasksOverviewController.currentTasksOverviewController.updateHTMLEditor(html, value);
                }
            }
        });

        return row;
    }
}

