package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.sql.RecordRow;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.lang.reflect.Method;
import java.util.List;

import static edu.utah.bmi.simple.gui.controller.CellFactories.colorCellFactory;
import static edu.utah.bmi.simple.gui.controller.CellFactories.colorCellHidenFactory;


public class KeyboardNavRowFactory<T> implements
        Callback<TableView<T>, TableRow<T>> {
    private final Callback<TableView<T>, TableRow<T>> baseFactory;
    public String viewName;

    public KeyboardNavRowFactory(Callback<TableView<T>, TableRow<T>> baseFactory, String viewName) {
        this.baseFactory = baseFactory;
        this.viewName = viewName;
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
                    TablePosition position;
                    position = tableView.getSelectionModel().getSelectedCells().get(0);
                    int colId = position.getColumn();
                    int rowId = position.getRow();
                    TasksOverviewController.tableMemoRowId.put(viewName, rowId);
                    TableColumn col = position.getTableColumn();
                    if (col == null)
                        col = tableView.getColumns().get(0);
                    Object value = col.getCellData(rowId);
                    Callback cellFactory = col.getCellFactory();
                    String html;
                    if (cellFactory == colorCellFactory || cellFactory == colorCellHidenFactory) {
                        html = ColorAnnotationCell.generateHTMLFromUnKnownType(value);
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

