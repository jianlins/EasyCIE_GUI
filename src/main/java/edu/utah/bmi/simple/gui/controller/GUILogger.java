package edu.utah.bmi.simple.gui.controller;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.application.Platform;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public class GUILogger extends ConsoleLogger {

    protected final ColumnInfo columnInfo = new ColumnInfo();
    protected String inputPath, descriptorPath;
    protected GUITask task;
    private boolean enableUIMAViewer = false;
    protected boolean report = false;
    protected String tabViewName=TasksOverviewController.AnnoView;

    protected GUILogger() {

    }

    public void setTabViewName(String tabViewName) {
        this.tabViewName = tabViewName;
    }

    public GUILogger(GUITask task, String inputPath, String descriptorPath) {
        this.task = task;

        columnInfo.addColumnInfo("ID", "string");
        columnInfo.addColumnInfo("TYPE", "string");
        columnInfo.addColumnInfo("BEGIN", "int");
        columnInfo.addColumnInfo("END", "int");

        columnInfo.addColumnInfo("FEATURES", "string");
        columnInfo.addColumnInfo("SNIPPET", "string");
//        columnInfo.addColumnInfo("ANNOTATOR","string");
//        columnInfo.addColumnInfo("RUN_ID","string");
        this.inputPath = inputPath;
        this.descriptorPath = descriptorPath;

    }

    public void setUIMAViewer(boolean enableUIMAViewer) {
        this.enableUIMAViewer = enableUIMAViewer;
    }


    public void setItem(String key, Object value) {
        loggedItems.put(key, value);
    }


    public void reset() {
        loggedItems.clear();
    }

    public String logItems() {
        StringBuilder logs = new StringBuilder();
        for (Map.Entry<String, Object> item : this.loggedItems.entrySet()) {
            logs.append(item.getKey());
            logs.append("\n");
            logs.append(item.getValue());
            logs.append("\n\n");
        }
        logString(logs.toString());
        System.out.println(logs.toString());
        return logs.toString();
    }


    public void logCompleteTime() {
        super.logCompleteTime();
        if (task.guiEnabled) {
            Platform.runLater(() -> {
                boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(
                        AnnotationLogger.records.iterator(), columnInfo, "output", tabViewName);
                if (res)
                    task.updateGUIMessage("String processing completed.");
                else
                    task.updateGUIMessage("No annotation exported.");

                task.updateGUIProgress(1, 1);

            });
            if (enableUIMAViewer)
                SwingUtilities.invokeLater(() -> {
                    JFrame frame = new MyAnnotationViewerPlain(new String[]{"Pipeline Debug Viewer", inputPath, descriptorPath + ".xml"});
                    frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    frame.pack();
                    frame.setVisible(true);
                });
        }
    }

    public void logString(String msg) {
        if (msg.startsWith("Processed")) {
            msg = msg.substring(9);
            int ofPos = msg.indexOf(" of ");
            if (ofPos != -1) {
                int processed = Integer.parseInt(msg.substring(0, ofPos).trim());
                int max = Integer.parseInt(msg.substring(ofPos + 4).trim());
                if (task.guiEnabled)
                    Platform.runLater(() -> task.updateGUIProgress(processed, max));
            }
        } else {
            String finalMsg = msg;
            if (task.guiEnabled)
                Platform.runLater(() -> task.updateGUIMessage(finalMsg));
            else
                System.out.println(finalMsg);
        }
    }

    public void setReportable(boolean report) {
        this.report = report;
    }

    public boolean reportable() {
        return report;
    }

}
