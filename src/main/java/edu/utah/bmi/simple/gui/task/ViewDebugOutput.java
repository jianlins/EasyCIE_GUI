package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;

import java.util.Optional;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class ViewDebugOutput extends GUITask {

    protected String rushType, cNERType, tNERType, contextType, featureInfType, docInfType, inputStr, metaStr;
    private TasksFX tasks;
    protected String rushRule = "", fastNERRule = "", fastCNERRule = "", contextRule = "",
            featureInfRule = "", docInfRule = "", annotator, exporttypes;
    protected boolean fastNerCaseSensitive;
    protected DebugPipe debugRunner;
    protected GUITask guiTask;

    protected final ColumnInfo columnInfo = new ColumnInfo();


    public ViewDebugOutput(TasksFX tasks) {
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIMessage("Initiate configurations..");
        this.tasks = tasks;
        columnInfo.addColumnInfo("ID", "string");
        columnInfo.addColumnInfo("TYPE", "string");
        columnInfo.addColumnInfo("BEGIN", "int");
        columnInfo.addColumnInfo("END", "int");

        columnInfo.addColumnInfo("FEATURES", "string");
        columnInfo.addColumnInfo("SNIPPET", "string");

    }

    @Override
    protected Object call() throws Exception {
        if (guiEnabled)
            Platform.runLater(() -> {
                boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(
                        AnnotationLogger.records.iterator(), columnInfo, "output", TasksOverviewController.DebugView);
            });
        return null;
    }


}
