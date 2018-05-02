package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.task.FastDebugPipe;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.util.Callback;

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

    public static FastDebugPipe debugRunner;


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
                        TasksOverviewController tasksOverviewController = TasksOverviewController.currentTasksOverviewController;
                        TabPane tabPane = tasksOverviewController.tabPane;
                        SingleSelectionModel<Tab> selectModel = tabPane.getSelectionModel();
                        if (selectModel.isSelected(1))  {
                            System.out.println("Start debugging...");
                            TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage("Start debugging...");
                            if (cell.getItem() instanceof RecordRow) {
                                debugRunner = FastDebugPipe.getInstance(TasksOverviewController.currentTasksOverviewController.mainApp.tasks);
                                debugRunner.guitask.updateGUIMessage("Start debugging...");
                                RecordRow recordRow = (RecordRow) cell.getItem();
                                debugRunner.guitask.updateGUIMessage("Execute pipeline...");
                                debugRunner.process(recordRow, "SNIPPET", "FEATURES", "COMMENTS", "ANNOTATOR", "TEXT",
                                        "DOC_TEXT", "SNIPPET", "BEGIN", "END", "SNIPPET_BEGIN");
                                debugRunner.showResults();
//                            new Thread(() -> fastDebugPipe.run()).start();
                            }
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
                    if (e.getButton().equals(MouseButton.SECONDARY)) {
                        System.out.println("Start debugging...");
                        TasksOverviewController tasksOverviewController = TasksOverviewController.currentTasksOverviewController;
                        TabPane tabPane = tasksOverviewController.tabPane;
                        SingleSelectionModel<Tab> selectModel = tabPane.getSelectionModel();
                        if (selectModel.isSelected(2)) {
                            RecordRow recordRow = (RecordRow) cell.getItem();
                            String bunchId = recordRow.getStrByColumnName("DOC_NAME");
                            selectModel.select(1);
                            int rowNum=tasksOverviewController.docIdRowPosMap.get(1).get(bunchId);
                            tasksOverviewController.annoTableView.scrollTo(rowNum);

                        } else {
                            TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage("Start debugging...");
                            if (cell.getItem() instanceof RecordRow) {
                                debugRunner = FastDebugPipe.getInstance(TasksOverviewController.currentTasksOverviewController.mainApp.tasks);
                                debugRunner.guitask.updateGUIMessage("Start debugging...");
                                RecordRow recordRow = (RecordRow) cell.getItem();
                                debugRunner.guitask.updateGUIMessage("Execute pipeline...");
                                if (recordRow.getValueByColumnName("DOC_TEXT") == null) {
                                    recordRow.addCell("DOC_TEXT", cell.queryDocContent(recordRow.getStrByColumnName("DOC_NAME")));
                                }
                                debugRunner.process(recordRow, "DOC_TEXT", "FEATURES", "COMMENTS", "ANNOTATOR", "TEXT", "DOC_TEXT",
                                        "SNIPPET", "BEGIN", "END", "SNIPPET_BEGIN");
                                debugRunner.showResults();
//                            new Thread(() -> fastDebugPipe.run()).start();
                            }
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
                        TasksOverviewController.currentTasksOverviewController.updateHTMLEditor(item.toString());
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
