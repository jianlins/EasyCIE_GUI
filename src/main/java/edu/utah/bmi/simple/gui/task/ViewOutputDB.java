package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

import java.io.File;

/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class ViewOutputDB extends GUITask {
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
                String filter = "";
                if (annotator.trim().length() > 0) {
                    DAO dao = new DAO(new File(outputDB));
                    if (!dao.checkTableExits(outputTable)) {
                        updateMessage("Table '" + outputTable + "' does not exit.");
                        popDialog("Note", "Table '" + outputTable + "' does not exit.",
                                " You need to execute 'RunEasyCIE' first.");
                        updateProgress(0, 0);
                        return;
                    }
                    int annotatorLastRunid = getLastRunIdofAnnotator(dao, outputTable, annotator);
                    int lastLogRunId = getLastLogRunId(dao, annotator);
                    if (annotatorLastRunid == -1) {
                        popDialog("Note", "There is no output in the previous runs of the annotator: \"" + annotator + "\"",
                                "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                        "if the dataset has been imported successfully.\n" +
                                        "EasyCIE will display all the previous outputs if there is any.");
                    } else if (annotatorLastRunid != lastLogRunId) {
                        popDialog("Note", "There is no output in the most recent run of annotator:\"" + annotator + "\"," +
                                        " which RUN_ID=" + lastLogRunId,
                                "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                        "if the dataset has been imported successfully.\n" +
                                        "Instead, EasyCIE Will display the last run of annotator \"" + annotator + "\" that has some output, which " +
                                        "RUN_ID=" + annotatorLastRunid);
                        filter = " WHERE annotator='" + annotator + "' AND RUN_ID=" + annotatorLastRunid;
                    } else {
                        filter = " WHERE annotator='" + annotator + "' AND RUN_ID=" + annotatorLastRunid;
                    }

                }
                res = TasksOverviewController.currentTasksOverviewController.showAnnoTable(outputDB, outputTable, filter, "output");
                if (res)
                    updateMessage("data loaded");
                else
                    updateMessage("no record loaded");
                updateProgress(1, 1);
            }
        });
        return null;
    }

    public int getLastRunIdofAnnotator(DAO dao, String outputTable, String annotator) {
        int id = -1;
        RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("maxRunIDofAnnotator", outputTable, annotator);
        if (recordRowIter.hasNext()) {
            RecordRow recordRow = recordRowIter.next();
            if (recordRow != null && recordRow.getValueByColumnId(1)!=null)
                id = (int) recordRow.getValueByColumnId(1);

        }
        return id;
    }

    public int getLastLogRunId(DAO dao, String annotator) {
        int id = -1;
        RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("lastLogRunID", annotator);
        if (recordRowIter.hasNext()) {
            RecordRow recordRow = recordRowIter.next();
            if (recordRow != null)
                id = (int) recordRow.getValueByColumnId(1);
        }
        return id;
    }

}
