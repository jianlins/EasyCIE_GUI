package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

import java.io.File;

/**
 * @author Jianlin Shi
 *         Created on 2/13/17.
 */
public class ViewOutputDB extends javafx.concurrent.Task {
    protected String outputDB, outputTable, annotator;

    protected ViewOutputDB() {

    }

    public ViewOutputDB(TasksFX tasks) {
        initiate(tasks);
    }

    public ViewOutputDB(TasksFX tasks, String... paras) {
        initiate(tasks, paras);
    }

    protected void initiate(TasksFX tasks, String... paras) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("settings");
        outputDB = config.getValue(ConfigKeys.writeConfigFileName);
        outputTable = config.getValue(ConfigKeys.outputTableName);
        config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);
        if (paras != null)
            switch (paras.length) {
                case 1:
                    annotator = paras[0];
                    break;
                case 2:
                    outputTable = paras[0];
                    annotator = paras[1];
                    break;
                case 3:
                    outputDB = paras[0];
                    outputTable = paras[1];
                    annotator = paras[2];
                    break;
            }
    }


    @Override
    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (!new File(outputDB).exists()) {
                    updateMessage("Database " + outputDB + " not exist");
                    return;
                }
                // Update UI here.
                boolean res = false;
                if (annotator.trim().length() > 0)
                    res = TasksOverviewController.currentTasksOverviewController.showAnnoTable(outputDB, outputTable, " WHERE annotator='" + annotator + "'", "output");
                else
                    res = TasksOverviewController.currentTasksOverviewController.showAnnoTable(outputDB, outputTable, "", "output");

                if (res)
                    updateMessage("data loaded");
                else
                    updateMessage("no record loaded");
                updateProgress(1, 1);
            }
        });
        return null;
    }

}
