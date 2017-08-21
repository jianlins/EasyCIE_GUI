package edu.utah.bmi.simple.gui.controller;


import edu.utah.bmi.nlp.core.GUITask;
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

    protected final ArrayList<String> columnNames;
    protected String inputPath, descriptorPath;
    protected GUITask task;

    public GUILogger(GUITask task, String inputPath, String descriptorPath) {
        this.task = task;
        columnNames = new ArrayList<>();
        columnNames.add("ID");
        columnNames.add("SNIPPET");
        columnNames.add("TYPE");
        columnNames.add("DOC_NAME");
        columnNames.add("ANNOTATOR");
        columnNames.add("COMMENTS");
        columnNames.add("RUN_ID");
        this.inputPath = inputPath;
        this.descriptorPath = descriptorPath;

    }


    public void reset() {

    }


    public void logCompleteTime() {
        super.logCompleteTime();
        Platform.runLater(new Runnable() {
            public void run() {
                boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(columnNames,
                        AnnotationLogger.records.iterator(), "output", false);
                if (res)
                    task.updateGUIMessage("String processing completed.");
                else
                    task.updateGUIMessage("No annotation exported.");

                task.updateGUIProgress(1, 1);

            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new MyAnnotationViewerPlain(new String[]{"Pipeline Debug Viewer", inputPath, descriptorPath});
                frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);
            }
        });
    }


}
