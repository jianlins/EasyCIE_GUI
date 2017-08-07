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
public class ViewImportDB extends javafx.concurrent.Task {
    protected String SQLFile, corpusTable;

    public ViewImportDB(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("settings");
        SQLFile = config.getValue(ConfigKeys.corpusDBFile);
        corpusTable = config.getValue(ConfigKeys.corpusDBTable);
    }


    @Override
    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (!new File(SQLFile).exists()) {
                    updateMessage("Database " + SQLFile + " not exist");
                    return;
                }
                // Update UI here.
                boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(SQLFile, corpusTable, "", "output");
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
