package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.easycie.EhostLoggerExporter;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.GUICPEStringDebugger;
import edu.utah.bmi.nlp.uima.Processable;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.doubleclick.OpenEhost;
import edu.utah.bmi.simple.gui.entry.StaticVariables;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

public class CellActions {
    public static Processable debugRunner;
    public static final String tmpEHOSTDir = "target/generated-sources/ehost";

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


    public static void process(RecordRow recordRow) {
        new Thread(() -> {
            String clsName = "";
            clsName = initDBdebugger();
            if (debugRunner != null) {
                debugRunner.process(recordRow, "TEXT", "FEATURES", "COMMENTS", "ANNOTATOR", "DOC_TEXT",
                        "SNIPPET", "BEGIN", "END", "SNIPPET_BEGIN");
                debugRunner.showResults();
            } else {
                GUICPEStringDebugger.classLogger.warning("Class not found: " + clsName);
            }
        }).start();
    }

    public static void showInEhost(RecordRow recordRow) {
        Object value = recordRow.getValueByColumnName("DOC_NAME");
        if (value != null && value.toString().length() > 0) {
            Thread th = new Thread(() -> {
                TasksFX tasks = TasksOverviewController.currentTasksOverviewController.mainApp.tasks;
                File projectRoot = new File(tasks.getTask(ConfigKeys.exportTask).getValue(ConfigKeys.outputEhostDir),
                        tasks.getTask(ConfigKeys.maintask).getValue(ConfigKeys.annotator));
                File txtFile = new File(new File(projectRoot, "corpus"), value.toString());
                if (!txtFile.exists()) {
//                  if haven't exported, export debug outputs
                    tasks = tasks.clone();
                    tasks.getTask(ConfigKeys.exportTask).setValue(ConfigKeys.outputEhostDir, tmpEHOSTDir);
                    tasks.getTask(ConfigKeys.imporTask).setValue(ConfigKeys.annotationDir, tmpEHOSTDir+"/uima");
                    TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage("Start exporting debug info...");
                    if (recordRow.getValueByColumnName("DOC_TEXT") == null && recordRow.getValueByColumnName("TEXT") == null) {
                        RecordRow record = ColorAnnotationCellHide.queryDocContent(recordRow.getStrByColumnName("DOC_NAME"));
                        for (Map.Entry<String, Object> entry : record.getColumnNameValues().entrySet()) {
                            recordRow.addCell(entry.getKey(), entry.getValue());
                        }
                    }
                    if (recordRow.getValueByColumnName("DOC_TEXT") == null && recordRow.getValueByColumnName("TEXT") == null)
                        recordRow.addCell("TEXT", "");
                    if (recordRow.getValueByColumnName("TEXT") == null)
                        recordRow.addCell("TEXT", recordRow.getValueByColumnName("DOC_TEXT"));
                    String content= recordRow.getStrByColumnName("TEXT");
                    CellActions.processNExportEhost(recordRow, content, tasks, tmpEHOSTDir);
                } else
                    new OpenEhost(TasksOverviewController.currentTasksOverviewController.mainApp.tasks, value.toString()).run();
            });
            th.start();
        }
    }

    public static void processNExportEhost(RecordRow recordRow, String content, TasksFX tasks, String tmpEHOSTDir) {
        new Thread(() -> {
            String clsName = "";
            clsName = initDBdebugger();
            if (debugRunner != null) {
                String colorPool = tasks.getTask("settings").getValue("viewer/color_pool");
                AnnotationLogger.getRecords().clear();
                debugRunner.process(recordRow, "TEXT", "FEATURES", "COMMENTS", "ANNOTATOR", "DOC_TEXT",
                        "SNIPPET", "BEGIN", "END", "SNIPPET_BEGIN");
//                debugRunner.showResults();

                ArrayList<RecordRow> outputs = AnnotationLogger.getRecords(true);

                EhostLoggerExporter exporter = new EhostLoggerExporter(tmpEHOSTDir,
                        colorPool, StaticVariables.randomPick ? 1 : 0);
                exporter.export(outputs,content);
                new OpenEhost(tasks, outputs.get(0).getStrByColumnName("ID") + ".txt").run();
            } else {
                GUICPEStringDebugger.classLogger.warning("Class not found: " + clsName);
            }
        }).start();
    }
}
