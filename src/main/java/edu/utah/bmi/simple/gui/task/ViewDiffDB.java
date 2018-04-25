package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
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
public class ViewDiffDB extends GUITask {
    protected String outputDB, diffTable, annotatorCompare, annotatorAgainst;

    public ViewDiffDB(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("compare");
        annotatorCompare = config.getValue(ConfigKeys.targetAnnotator);
        annotatorAgainst = config.getValue(ConfigKeys.referenceAnnotator);
        config = tasks.getTask("settings");
        outputDB = config.getValue(ConfigKeys.writeDBConfigFileName);
        diffTable = config.getValue(ConfigKeys.compareTable).trim();
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
                if (annotatorCompare.trim().length() > 0 && annotatorAgainst.trim().length() > 0) {
                    String annotator = annotatorCompare + "_vs_" + annotatorAgainst;
                    DAO dao = new DAO(new File(outputDB));
                    if (!dao.checkExists(diffTable)) {
                        updateMessage("Table '" + diffTable + "' does not exit.");
                        popDialog("Note", "Table '" + diffTable + "' does not exit.",
                                " You need to execute 'Compare' first.");
                        updateProgress(0, 0);
                        return;
                    }
                    RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("maxRunIDofAnnotator", diffTable, annotator);
                    if (recordRowIter == null) {
                        updateMessage("Table " + diffTable + " hasn't been created.");
                        updateProgress(1, 1);
                        return;
                    }
                    if (recordRowIter.hasNext()) {
                        RecordRow recordRow = recordRowIter.next();
                        Object obj = recordRow.getValueByColumnId(1);
                        if (obj == null) {
                            popDialog("Note", "There is not compared results" +
                                            " in the table '" + diffTable + "'.",
                                    "Make sure you have executed 'Compare' first.");
                            updateProgress(0,0);
                            return;
                        }
                        int lastRunId = (int) recordRow.getValueByColumnId(1);
                        if (CompareBDSTask.lastRunId != -1 && lastRunId < CompareBDSTask.lastRunId) {
                            popDialog("Note", "No difference saved in the most recent comparison. ",
                                    "The last comparison of \"" + annotator + "\" has no difference saved in table \"" + diffTable + "\".\n" +
                                            "Here displays the previous comparison that has some difference saved.");
                        }
                        dao.close();
                        TasksOverviewController.currentTasksOverviewController.showAnnoTable(outputDB, diffTable,
                                " WHERE annotator='" + annotator + "' AND RUN_ID=" + lastRunId, ColorAnnotationCell.colorCompare);
                        updateMessage("data loaded");
                    } else {
                        updateMessage("no record loaded");
                    }
                    updateProgress(1, 1);
                }
            }
        });
        return null;
    }

}
