package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorStringDebugger;
import edu.utah.bmi.nlp.uima.Processable;
import edu.utah.bmi.simple.gui.doubleclick.OpenEhost;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import edu.utah.bmi.simple.gui.task.ViewOutputDB;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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


    public static Callback<TableColumn, TableCell> colorCellFactory =
            p -> {
                ColorAnnotationCell cell = new ColorAnnotationCell();
                cell.setOnMouseClicked(e -> {
                    TasksOverviewController tasksOverviewController = TasksOverviewController.currentTasksOverviewController;
                    TabPane tabPane = tasksOverviewController.tabPane;
                    SingleSelectionModel<Tab> selectModel = tabPane.getSelectionModel();
                    int selectedTabIdx = selectModel.getSelectedIndex();
                    if (!cell.isEmpty()) {
                        Object item = cell.getItem();
                        Color color = Color.LIGHTGREY;
                        cell.setBackground(new Background(new BackgroundFill(color, null, null)));
                        String html = cell.generateHTML();
                        TasksOverviewController.currentTasksOverviewController.updateHTMLEditor(html, item);
                        if (e.getClickCount() == 2 &&
                                (selectedTabIdx == 1 || selectedTabIdx == 2 || selectedTabIdx == 3)) {
                            if (item instanceof RecordRow) {
                                CellActions.showInEhost((RecordRow) item);
                            }
                        }
                    }
                    if (e.getButton().equals(MouseButton.SECONDARY)) {
                        if (selectedTabIdx == 2 || selectedTabIdx == 1) {
//                            System.out.println("Start debugging...");
                            TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage("Start debugging...");
                            if (cell.getItem() instanceof RecordRow) {
                                RecordRow recordRow = (RecordRow) cell.getItem();
                                String text = "";
                                recordRow.addCell("TEXT", recordRow.getStrByColumnName("SNIPPET"));
                                CellActions.process(recordRow);
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
                    TasksOverviewController tasksOverviewController = TasksOverviewController.currentTasksOverviewController;
                    TabPane tabPane = tasksOverviewController.tabPane;
                    SingleSelectionModel<Tab> selectModel = tabPane.getSelectionModel();
                    int selectedTabIdx = selectModel.getSelectedIndex();
                    if (!cell.isEmpty()) {
                        Object item = cell.getItem();
                        Color color = Color.LIGHTGREY;
                        cell.setBackground(new Background(new BackgroundFill(color, null, null)));
                        String html = cell.generateHTML();
                        TasksOverviewController.currentTasksOverviewController.updateHTMLEditor(html, item);
                        if (e.getClickCount() == 2 &&
                                (selectedTabIdx == 1 || selectedTabIdx == 2 || selectedTabIdx == 3)) {
                            if (item instanceof RecordRow) {
                                CellActions.showInEhost((RecordRow) item);
                            }
                        }

//                            if (item instanceof RecordRow)
//                                updateHTMLEditor((RecordRow) item);
                        // do something with id...
                    }
                    if (e.getButton().equals(MouseButton.SECONDARY)) {
//                        System.out.println("Start debugging...");

                        if (selectedTabIdx == 3) {
                            RecordRow recordRow = (RecordRow) cell.getItem();
                            String bunchId = recordRow.getStrByColumnName("DOC_NAME");
                            selectModel.select(2);
                            viewBunchId(bunchId);
//                            int rowNum=tasksOverviewController.docIdRowPosMap.get(1).get(bunchId);
//                            tasksOverviewController.annoTableView.scrollTo(rowNum);

                        } else {
                            TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage("Start debugging...");
                            if (cell.getItem() instanceof RecordRow) {
                                RecordRow recordRow = (RecordRow) cell.getItem();
                                if (recordRow.getValueByColumnName("DOC_TEXT") == null && recordRow.getValueByColumnName("TEXT") == null) {
                                    RecordRow record = cell.queryDocContent(recordRow.getStrByColumnName("DOC_NAME"));
                                    for (Map.Entry<String, Object> entry : record.getColumnNameValues().entrySet()) {
                                        recordRow.addCell(entry.getKey(), entry.getValue());
                                    }
                                }
                                if (recordRow.getValueByColumnName("DOC_TEXT") == null && recordRow.getValueByColumnName("TEXT") == null)
                                    recordRow.addCell("TEXT","");
                                CellActions.process(recordRow);
//                                new Thread(() -> process(recordRow, docText)).start();
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


    public static boolean viewBunchId(String bunchId) {
        TasksFX tasks = TasksOverviewController.currentTasksOverviewController.mainApp.tasks;
        TaskFX config = tasks.getTask("settings");
        String inputTable = config.getValue(ConfigKeys.inputTableName);
        String outputDB = config.getValue(ConfigKeys.writeDBConfigFileName);
        String snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
        String docResultTable = config.getValue(ConfigKeys.docResultTableName);
        String bunchResultTable = config.getValue(ConfigKeys.bunchResultTableName);
        String bunchColumnName = config.getValue("bunchColumnName");
        if (bunchColumnName.trim().length() == 0)
            bunchColumnName = "BUNCH_ID";
        String docIdColumnName = config.getValue("docIdColumnName");
        if (docIdColumnName.trim().length() == 0)
            docIdColumnName = "DOC_NAME";
        config = tasks.getTask(ConfigKeys.maintask);
        String annotator = config.getValue(ConfigKeys.annotator);
        String viewQueryName = config.getValue(ConfigKeys.viewQueryName);
        EDAO dao = EDAO.getInstance(new File(outputDB));

        String[] values = ViewOutputDB.buildQuery(dao, viewQueryName, annotator, snippetResultTable, docResultTable, bunchResultTable, inputTable);
        String sourceQuery = values[0];

        String filter = values[1] + " AND " + bunchColumnName + "=" + bunchId;
        if (values[1].startsWith("RD"))
            filter = values[1] + " AND RD." + docIdColumnName + "='" + bunchId + "'";
        String annotatorLastRunid = values[2];
        String lastLogRunId = values[3];
        sourceQuery = ViewOutputDB.modifyQuery(sourceQuery, filter);
        dao.close();
        boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(sourceQuery, outputDB, ColorAnnotationCell.colorOutput, TasksOverviewController.AnnoView);
        return res;
    }


}
