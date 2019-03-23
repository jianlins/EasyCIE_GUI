package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorStringDebugger;
import edu.utah.bmi.nlp.uima.Processable;
import edu.utah.bmi.simple.gui.doubleclick.OpenEhost;
import edu.utah.bmi.simple.gui.entry.TasksFX;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CellActions {
    public static Processable debugRunner;

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
                AdaptableCPEDescriptorStringDebugger.classLogger.warning("Class not found: " + clsName);
            }
        }).start();
    }

    public static void showInEhost(RecordRow recordRow){
        Object value = recordRow.getValueByColumnName("DOC_NAME");
        if (value != null && value.toString().length() > 0) {
            Thread th=new Thread(() -> {
                new OpenEhost(TasksOverviewController.currentTasksOverviewController.mainApp.tasks, value.toString()).run();
            });
            th.start();
        }
    }

}
