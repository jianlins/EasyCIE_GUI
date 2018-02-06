package edu.utah.bmi.simple.gui.controller;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import javafx.application.Platform;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public class GUILogger extends ConsoleLogger {

    protected final ColumnInfo columnInfo = new ColumnInfo();
    protected String inputPath, descriptorPath;
    protected GUITask task;

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


    public void reset() {

    }
    public String logItems() {
        setItem("COMMENTS", "");
        return "";
    }


    public void logCompleteTime() {
        super.logCompleteTime();
        if (task.guiEnabled)
            Platform.runLater(new Runnable() {
                public void run() {
                    boolean res = true;
                    res = TasksOverviewController.currentTasksOverviewController.showDBTable(
                            AnnotationLogger.records.iterator(), columnInfo, "output", false);
                    if (res)
                        task.updateGUIMessage("String processing completed.");
                    else
                        task.updateGUIMessage("No annotation exported.");

                    task.updateGUIProgress(1, 1);

                }
            });
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new MyAnnotationViewerPlain(new String[]{"Pipeline Debug Viewer", inputPath, descriptorPath+".xml"});
                frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);
            }
        });
    }


}
