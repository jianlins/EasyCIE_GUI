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
    protected String outputDB, diffTable, annotatorCompare, annotatorAgainst, inputTable;

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
        inputTable = config.getValue(ConfigKeys.inputTableName);
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
//                // Update UI here.
//                if (annotatorCompare.trim().length() > 0 && annotatorAgainst.trim().length() > 0) {
//                    String annotator = annotatorCompare + "_vs_" + annotatorAgainst;
//                    DAO dao = new DAO(new File(outputDB));
//                    dao.initiateTableFromTemplate("ANNOTATION_TABLE", diffTable, false);
//                    if (!dao.checkExists(diffTable)) {
//                        updateMessage("Table '" + diffTable + "' does not exit.");
//                        popDialog("Note", "Table '" + diffTable + "' does not exit.",
//                                " You need to execute 'Compare' first.");
//                        updateProgress(0, 0);
//                        return;
//                    }
//                    RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("maxRunIDofAnnotator", diffTable, annotator);
//                    if (recordRowIter == null) {
//                        updateMessage("Table " + diffTable + " hasn't been created.");
//                        updateProgress(1, 1);
//                        return;
//                    }
//                    if (recordRowIter.hasNext()) {
//                        RecordRow recordRow = recordRowIter.next();
//                        Object obj = recordRow.getValueByColumnId(1);
//                        if (obj == null) {
//                            popDialog("Note", "There is not compared results" +
//                                            " in the table '" + diffTable + "'.",
//                                    "Make sure you have executed 'Compare' first.");
//                            updateProgress(0, 0);
//                            return;
//                        }
//                        int lastRunId = (int) recordRow.getValueByColumnId(1);
//                        if (CompareBDSTask.lastRunId != -1 && lastRunId < CompareBDSTask.lastRunId) {
//                            popDialog("Note", "No difference saved in the most recent comparison. ",
//                                    "The last comparison of \"" + annotator + "\" has no difference saved in table \"" + diffTable + "\".\n" +
//                                            "Here displays the previous comparison that has some difference saved.");
//                        }
//                        dao.close();
//                        String filter = "annotator='" + annotator + "' AND RUN_ID=" + lastRunId;
//                        dao.queryTemplates.get()
//
//                        TasksOverviewController.currentTasksOverviewController.showDBTable(outputDB, diffTable,
//                                " WHERE " + filter, ColorAnnotationCell.colorCompare);
//                        TasksOverviewController.currentTasksOverviewController.annoSqlFilter.setText(filter);
//                        updateMessage("data loaded");
//                    } else {
//                        updateMessage("no record loaded");
//                    }
//                    updateProgress(1, 1);
//                }

                boolean res = false;
                if (annotatorCompare.trim().length() > 0 && annotatorAgainst.trim().length() > 0) {
                    String annotator = annotatorCompare + "_vs_" + annotatorAgainst;
                    DAO dao = new DAO(new File(outputDB));
                    dao.initiateTableFromTemplate("ANNOTATION_TABLE", diffTable, false);
                    String[] values = buildQuery(dao, annotator, diffTable, inputTable);
                    String sourceQuery = values[0];
                    String filter = values[1];
                    String annotatorLastRunid = values[2];
                    String lastLogRunId = values[3];
                    if (values == null) {
                        updateMessage("Table '" + diffTable + "' does not exit.");
                        popDialog("Note", "Table '" + diffTable + "' does not exit.",
                                " You need to execute 'RunEasyCIE' first.");
                        updateProgress(0, 0);
                    } else if (annotatorLastRunid.equals("-1")) {
                        popDialog("Note", "There is no output in the previous runs of the annotator: \"" + annotator + "\"",
                                "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                        "if the dataset has been imported successfully.\n" +
                                        "EasyCIE will display all the previous outputs if there is any.");
                    } else if (!annotatorLastRunid.equals(lastLogRunId)) {
                        popDialog("Note", "There is no output in the most recent run of annotator:\"" + annotator + "\"," +
                                        " which RUN_ID=" + annotatorLastRunid,
                                "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                        "if the dataset has been imported successfully.\n" +
                                        "Instead, EasyCIE Will display the last run of annotator \"" + annotator + "\" that has some output, which " +
                                        "RUN_ID=" + lastLogRunId);
                    }


                    String otherConditions = TasksOverviewController.currentTasksOverviewController.annoSqlFilter.getText().trim();
                    if (otherConditions.length() == 0) {
                        TasksOverviewController.currentTasksOverviewController.annoSqlFilter.setText(filter);
                        otherConditions = filter;
                    }
                    if (otherConditions.length() > 0) {
                        sourceQuery = ViewOutputDB.modifyQuery(sourceQuery, otherConditions);
                    }

                    res = TasksOverviewController.currentTasksOverviewController.showDBTable(dao.queryRecordsNMeta(sourceQuery), ColorAnnotationCell.colorOutput, TasksOverviewController.AnnoView);
                    dao.close();
                    if (res)
                        updateMessage("data loaded");
                    else
                        updateMessage("no record loaded");
                    updateProgress(1, 1);
                }
            }
        });
        return null;
    }

    public static String[] buildQuery(DAO dao, String annotator,
                                      String snippetResultTable, String inputTable) {
        String sourceQuery, primeTable = "RS";
//       match to querySnippetAnnos, queryDocSnippetAnnos, queryBunchDocSnippetAnnos
//        or querySnippetAnnosWSource,queryDocSnippetAnnosWSource,queryBunchDocSnippetAnnosWSource
        String queryName = "querySnippetAnnos";
        sourceQuery = dao.queries.get(queryName);
        sourceQuery = sourceQuery.replaceAll("\\{tableName}", snippetResultTable)
                .replaceAll("\\{inputTable}", inputTable);
        String filter = "", annotatorLastRunid = "", lastLogRunId = "";
        if (annotator.trim().length() > 0) {
            if (!dao.checkTableExits(snippetResultTable)) {
                return null;
            }
            annotatorLastRunid = ViewOutputDB.getLastRunIdofAnnotator(dao, snippetResultTable, annotator);
            lastLogRunId = ViewOutputDB.getLastLogRunId(dao, annotator);
            String runId;
            if (!annotatorLastRunid.equals("-1")) {
                if (!annotatorLastRunid.equals(lastLogRunId)) {
                    runId = lastLogRunId;
                } else {
                    runId = annotatorLastRunid;
                }
            } else {
                runId = lastLogRunId;
            }
            if (!runId.equals("-1")) {
                filter = primeTable + ".annotator='" + annotator + "' AND " + primeTable + ".RUN_ID=" + runId;
            }
        }
        return new String[]{sourceQuery, filter, annotatorLastRunid, lastLogRunId};
    }


}
