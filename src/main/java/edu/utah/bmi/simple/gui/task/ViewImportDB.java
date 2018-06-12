package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.simple.gui.controller.ColorAnnotationCell;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

import java.io.File;

/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class ViewImportDB extends GUITask {
    protected String SQLFile, corpusTable;
    protected TasksFX tasks;
    protected String datasetID;

    public ViewImportDB(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        this.tasks = tasks;
        if (Platform.isAccessibilityActive()) {
            updateGUIMessage("Initiate configurations..");

        }
        TaskFX config = tasks.getTask("settings");
        datasetID = config.getValue(ConfigKeys.datasetId);
        SQLFile = config.getValue(ConfigKeys.readDBConfigFileName);
        corpusTable = config.getValue(ConfigKeys.inputTableName);
    }


    @Override
    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (!new File(SQLFile).exists()) {
                    updateGUIMessage("Database " + SQLFile + " not exist");
                    updateGUIProgress(0, 0);
                    return;
                }
                // Update UI here.
                boolean res = false;
                EDAO dao = EDAO.getInstance(new File(SQLFile), true, false);
                if (!dao.checkTableExits(corpusTable)) {
                    popDialog("Note", "Table '" + corpusTable + "' does not exit.",
                            " You need to import documents first.");
                    updateGUIProgress(0, 0);
                    return;
                }

                String sql = dao.queries.get("queryDocs").replaceAll("\\{tableName}", corpusTable);
                sql += " WHERE  DATASET_ID='" + datasetID + "'";
                res = TasksOverviewController.currentTasksOverviewController.showDBTable(sql, SQLFile, ColorAnnotationCell.colorOutput, TasksOverviewController.DocView);
                if (res)
                    updateGUIMessage("data loaded");
                else
                    updateGUIMessage("no record loaded");
                updateGUIProgress(1, 1);
            }
        });
        return null;
    }

}
