package edu.utah.bmi.nlp.uima.loggers;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.nlp.uima.SimpleStatusCallbackListenerImpl;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.application.Platform;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMARuntimeException;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.EntityProcessStatus;
import org.apache.uima.util.ProcessTraceEvent;
import org.apache.uima.util.Progress;

import javax.swing.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public class GUILogger extends ConsoleLogger {

    protected final ColumnInfo columnInfo = new ColumnInfo();
    protected String inputPath, descriptorPath;
    protected GUITask task;
    protected boolean enableUIMAViewer = false;
    protected String tabViewName = TasksOverviewController.AnnoView;
    public int maxCommentLength = 1000;


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

    public void setTask(GUITask task) {
        this.task = task;
    }

    public GUITask getTask() {
        return task;
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

 
    @Override
    public String getItem(String key) {
        return null;
    }


    public void logString(String msg) {
        String finalMsg = msg;
        if (task != null && task.guiEnabled)
            Platform.runLater(() -> task.updateGUIMessage(finalMsg));
        else
            System.out.println(finalMsg);

    }


    @Override
    public void entityProcessComplete(CAS aCas, EntityProcessStatus aStatus) {
        if (!aStatus.isException()) {
            ++this.entityCount;
            if (task != null && task.guiEnabled) {
                if (this.totaldocs != -1) {
                    Platform.runLater(() -> task.updateGUIProgress(entityCount, totaldocs));
                } else {
                    Platform.runLater(() -> task.updateGUIProgress(1, 1));
                }
            }

            String docText = aCas.getDocumentText();
            if (docText != null) {
                this.size += (long) docText.length();
            }

        } else {
            List ex = aStatus.getExceptions();
            displayError((Throwable) ex.get(0));
        }
    }

    @Override
    public void batchProcessComplete() {

    }

    @Override
    public void collectionProcessComplete(String reportContent) {
        logCompleteTime();
        long initTime = this.initCompleteTime - startTime;
        long processingTime = completeTime - initCompleteTime;
        long elapsedTime = initTime + processingTime;
        StringBuilder report = new StringBuilder();
        report.append(this.entityCount + " notes\n");
        if (this.size > 0L) {
            report.append(this.size + " characters\n");
        }

        report.append("Total:\t" + elapsedTime + " ms\n");
        report.append("Initialization:\t" + initTime + " ms\n");
        report.append("Processing:\t" + processingTime + " ms\n");
        report.append(reportContent);
        setItem("NUM_NOTES", this.entityCount);
        String comments;
        if (report.length() > this.maxCommentLength) {
            comments = report.substring(0, this.maxCommentLength);
        } else {
            comments = report.toString();
        }

        if (reportable())
            setItem("COMMENTS", comments);

        logItems();

        if (task != null && task.guiEnabled) {
            Platform.runLater(() -> {
                boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(
                        AnnotationLogger.records.iterator(), columnInfo, "output", tabViewName);
                if (res)
                    task.updateGUIMessage("String processing completed.");
                else
                    task.updateGUIMessage("No annotation exported.");

                task.updateGUIProgress(1, 1);
                if (this.report)
                    task.popDialog("Done", "Data process compelete", comments);

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


}
