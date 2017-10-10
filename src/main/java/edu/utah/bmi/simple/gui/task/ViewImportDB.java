package edu.utah.bmi.simple.gui.task;


import com.sun.javafx.application.PlatformImpl;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.DAO;
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
    protected String importType;
    public ViewImportDB(TasksFX tasks, String importType) {
        initiate(tasks, importType);
    }

    private void initiate(TasksFX tasks, String importType) {
        this.importType = importType;
        if (Platform.isAccessibilityActive()) {
            updateGUIMessage("Initiate configurations..");

        }
        TaskFX config = tasks.getTask("settings");
        SQLFile = config.getValue(ConfigKeys.readDBConfigFile);
        if (importType.equals("doc"))
            corpusTable = config.getValue(ConfigKeys.inputTableName);
        else
            corpusTable = config.getValue(ConfigKeys.referenceTable);
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
                DAO dao = new DAO(new File(SQLFile), true, false);
                if (!dao.checkTableExits(corpusTable)) {
                    popDialog("Note", "Table '" + corpusTable + "' does not exit.",
                            " You need to import documents first.");
                    updateGUIProgress(0, 0);
                    return;
                }
                if (importType.equals("doc"))
                    res = TasksOverviewController.currentTasksOverviewController.showDocTable(SQLFile, corpusTable, "", "output");
                else
                    res = TasksOverviewController.currentTasksOverviewController.showAnnoTable(SQLFile, corpusTable, "", "output");
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
