package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.easycie.EhostExporter;
import edu.utah.bmi.simple.gui.entry.StaticVariables;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import javafx.scene.control.Button;

/**
 * If the annotations haven't been exported to eHOST format. When double click the DOC_ID cell,
 * this class will try to export the component wised annotations to eHOST format to a tmp directory (will
 * clean before export), and open eHOST to view the directory.
 */
public class ExportEhostFromDebugger extends GUITask {

    private Button button;
    private TasksFX tasks;
    private EhostExporter exporter;

    public ExportEhostFromDebugger(TasksFX tasks) {
        initiate(tasks);
    }

    public ExportEhostFromDebugger(TasksFX tasks, Button button) {
        this.button = button;
        button.setDisable(true);
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIProgress(0, 0);
        updateGUIMessage("Reading from db..");
        this.tasks = tasks;
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        String annotator = config.getValue(ConfigKeys.annotator);
        config = tasks.getTask("settings");
        String readDBConfigFileName = config.getValue(ConfigKeys.readDBConfigFileName);
        String docTableName = config.getValue(ConfigKeys.inputTableName);
        String dataSetId = config.getValue(ConfigKeys.datasetId);
        String writeConfigFileName = config.getValue(ConfigKeys.writeDBConfigFileName);
        String snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
        String docResultTable = config.getValue(ConfigKeys.docResultTableName);
        StaticVariables.randomPick = config.getValue("viewer/random_pick_color").toLowerCase().startsWith("t");
        TaskFX exportConfig = tasks.getTask("export");


        String ehostDir = exportConfig.getValue(ConfigKeys.outputEhostDir);
        exporter = new EhostExporter(ehostDir, annotator, dataSetId, readDBConfigFileName,
                writeConfigFileName, docTableName, snippetResultTable, docResultTable,
                config.getValue("viewer/color_pool"), StaticVariables.randomPick ? 1 : 0);


    }


    @Override
    protected Object call() throws Exception {
        updateGUIMessage("Start export..");
        exporter.export();
        button.setDisable(false);
        updateGUIProgress(1, 1);
        updateGUIMessage("Export complete.");
        return null;
    }
}
