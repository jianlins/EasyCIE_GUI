package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.runner.RunEhostConverter;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;

/**
 * @author Jianlin Shi
 *         Created on 3/21/17.
 */
public class ImportEhost extends javafx.concurrent.Task {
    protected String inputDir, outputDB, outputTable;
    protected String overWriteAnnotatorName = "";

    public ImportEhost(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("import");
        inputDir = config.getValue(ConfigKeys.ehostProjectDir);

        overWriteAnnotatorName = config.getValue(ConfigKeys.ehostAnnotatorName);

        config = tasks.getTask("settings");
        outputDB = config.getValue(ConfigKeys.outputDBFile);
        outputTable = config.getValue(ConfigKeys.outputDBTable);

    }

    @Override
    protected Object call() throws Exception {
        RunEhostConverter.main(new String[]{inputDir, outputDB, outputTable, overWriteAnnotatorName});
//        run(inputDir, outputTable, SQLFile, overwrite, filters);
        updateMessage("Import complete");
        updateProgress(1, 1);
        return null;
    }


}
