package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorStringDebugger;
import edu.utah.bmi.nlp.uima.Processable;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import edu.utah.bmi.simple.gui.task.FastDebugPipe;
import edu.utah.bmi.simple.gui.task.ViewOutputDB;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

    public static Processable debugRunner;


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
                        if (selectModel.isSelected(1)) {
                            System.out.println("Start debugging...");
                            TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage("Start debugging...");
                            if (cell.getItem() instanceof RecordRow) {
                                RecordRow recordRow = (RecordRow) cell.getItem();
                                String text = "";
                                if (recordRow.getValueByColumnName("TEXT") != null) {
                                    text = recordRow.getStrByColumnName("TEXT");
                                }
                                final String docText = text;
                                new Thread(() -> process(recordRow, docText)).start();
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
                            viewBunchId(bunchId);
//                            int rowNum=tasksOverviewController.docIdRowPosMap.get(1).get(bunchId);
//                            tasksOverviewController.annoTableView.scrollTo(rowNum);

                        } else {
                            TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage("Start debugging...");
                            if (cell.getItem() instanceof RecordRow) {
                                RecordRow recordRow = (RecordRow) cell.getItem();
                                String text = "";
                                if (recordRow.getValueByColumnName("DOC_TEXT") == null) {
                                    text = cell.queryDocContent(recordRow.getStrByColumnName("DOC_NAME"));
                                } else {
                                    text = recordRow.getStrByColumnName("DOC_TEXT");
                                }
                                final String docText = text;
                                process(recordRow, docText);
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
        config = tasks.getTask(ConfigKeys.maintask);
        String annotator = config.getValue(ConfigKeys.annotator);
        String viewQueryName = config.getValue(ConfigKeys.viewQueryName);
        EDAO dao = EDAO.getInstance(new File(outputDB));

        String[] values = ViewOutputDB.buildQuery(dao, viewQueryName, annotator, snippetResultTable, docResultTable, bunchResultTable, inputTable);
        String sourceQuery = values[0];
        String filter = values[1] + " AND BUNCH_ID=" + bunchId;
        String annotatorLastRunid = values[2];
        String lastLogRunId = values[3];
        sourceQuery = ViewOutputDB.modifyQuery(sourceQuery, filter);
        dao.close();
        boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(sourceQuery, outputDB, ColorAnnotationCell.colorOutput, TasksOverviewController.AnnoView);
        return res;
    }

    public static String initDBdebugger() {
        String clsName = "";
        if (TasksOverviewController.currentTasksOverviewController != null) {
            TasksFX tasks = TasksOverviewController.currentTasksOverviewController.mainApp.tasks;
            clsName = tasks.getTask("settings").getValue("debug/class");
            try {
                Class debuggerCls = Class.forName(clsName);
                Method getInstanceMethod = debuggerCls.getMethod("getInstance", TasksFX.class);
                Object debugger = getInstanceMethod.invoke(null, tasks);
                if (debugger.getClass().isAssignableFrom(Processable.class)) {
                    debugRunner = (Processable) debugger;
                } else if (Processable.class.isAssignableFrom(debugger.getClass())) {
                    debugRunner = (Processable) debugger;
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return clsName;
    }

    public static void process(RecordRow recordRow, String doctext) {
        String clsName = "";
        clsName = initDBdebugger();
        if (debugRunner != null) {
            if (recordRow.getValueByColumnName("DOC_TEXT") == null) {
                recordRow.addCell("DOC_TEXT", doctext);
            }
            debugRunner.process(recordRow, "DOC_TEXT", "FEATURES", "COMMENTS", "ANNOTATOR", "TEXT", "DOC_TEXT",
                    "SNIPPET", "BEGIN", "END", "SNIPPET_BEGIN");
            debugRunner.showResults();
        } else {
            AdaptableCPEDescriptorStringDebugger.classLogger.warning("Class not found: " + clsName);
        }
    }
}
