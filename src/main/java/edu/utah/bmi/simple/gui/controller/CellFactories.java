package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.DebugPipe;
import edu.utah.bmi.simple.gui.task.RunEasyCIEDebugger;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.util.Map;

public class CellFactories {

    public static ContextMenu contextMenu;

    public static ContextMenu getContextMenu() {
        if (contextMenu == null) {
            contextMenu = new ContextMenu();
            MenuItem reload = new MenuItem("Debug");
            reload.setOnAction(e -> {
                System.out.println("DEBUG");
            });
        }
        return contextMenu;
    }

    public static DebugPipe debugRunner;

    public static DebugPipe initDebugRunner(TasksFX tasks, GUITask guiTask) {
        if (debugRunner == null) {
            debugRunner = new DebugPipe(tasks, guiTask);
        }
        return debugRunner;
    }

    public static Callback<TableColumn, TableCell> colorCellFactory =
            p -> {
                ColorAnnotationCell cell = new ColorAnnotationCell();
                cell.setOnMouseClicked(e -> {
                    if (!cell.isEmpty()) {
                        Object item = cell.getItem();
                        Color color = Color.LIGHTGREY;
                        cell.setBackground(new Background(new BackgroundFill(color, null, null)));
                        String html = cell.generateHTML();
                        TasksOverviewController.currentTasksOverviewController.updateHTMLEditor(html, item);
//                            if (item instanceof RecordRow)
//                                updateHTMLEditor((RecordRow) item);
                        // do something with id...
                    }
                    if (e.getButton().equals(MouseButton.SECONDARY)) {
                        System.out.println("Start debugging...");
                        if ( cell.getItem() instanceof RecordRow) {
                            debugRunner=new DebugPipe(TasksOverviewController.currentTasksOverviewController.mainApp.tasks,TasksOverviewController.currentTasksOverviewController.currentGUITask);
                            debugRunner.guitask.updateGUIMessage("Start debugging...");
                            RecordRow recordRow = (RecordRow) cell.getItem();
                            RecordRow metaRow = new RecordRow();
                            metaRow.deserialize(recordRow.serialize("FEATURES", "COMMENTS", "ANNOTATOR", "TEXT",
                                    "DOC_TEXT", "SNIPPET", "BEGIN", "END", "SNIPPET_BEGIN"));
                            StringBuilder sb = new StringBuilder();
                            for (Map.Entry<String, Object> entry : metaRow.getColumnNameValues().entrySet()) {
                                sb.append(entry.getKey() + "," + entry.getValue());
                                sb.append("|");
                            }
                            debugRunner.addReader(recordRow.getStrByColumnName("SNIPPET"), sb.substring(0, sb.length() - 1));
                            debugRunner.guitask.updateGUIMessage("Execute pipeline...");
                            debugRunner.runner.run();
//                            new Thread(() -> debugRunner.run()).start();
                        }

                    }
                });
                cell.setOnMouseExited(e -> {
                    if (!cell.isEmpty()) {
                        cell.setBackground(new Background(new BackgroundFill[]{}));
                    }
                });

                return cell;
            };
    public static Callback<TableColumn, TableCell> colorCellHidenFactory =
            p -> {
                ColorAnnotationCellHide cell = new ColorAnnotationCellHide();
                cell.setOnMouseClicked(e -> {
                    if (!cell.isEmpty()) {
                        Object item = cell.getItem();
                        Color color = Color.LIGHTGREY;
                        cell.setBackground(new Background(new BackgroundFill(color, null, null)));
                        String html = cell.generateHTML();
                        TasksOverviewController.currentTasksOverviewController.updateHTMLEditor(html, item);
//                            if (item instanceof RecordRow)
//                                updateHTMLEditor((RecordRow) item);
                        // do something with id...
                    }
                });
                cell.setOnMouseExited(e -> {
                    if (!cell.isEmpty()) {
                        cell.setBackground(new Background(new BackgroundFill[]{}));
                    }
                });
                return cell;
            };


    public static Callback<TableColumn, TableCell> textCellFactory =
            p -> {
                TableCell<ObservableList, Object> cell = new TableCell<ObservableList, Object>() {
                    protected void updateItem(Object item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? null : item + "");
                    }
                };
                cell.setOnMouseClicked(e -> {
                    if (!cell.isEmpty()) {
                        Object item = cell.getItem();
                        cell.setText(item + "");
                        Color color = Color.LIGHTGREY;
                        cell.setBackground(new Background(new BackgroundFill(color, null, null)));
                        TasksOverviewController.currentTasksOverviewController.updateHTMLEditor(item);
                    }
                });
                cell.setOnMouseExited(e -> {
                    if (!cell.isEmpty()) {
                        cell.setBackground(new Background(new BackgroundFill[]{}));
                    }
                });

                return cell;
            };
}
