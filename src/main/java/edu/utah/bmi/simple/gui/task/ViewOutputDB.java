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
public class ViewOutputDB extends GUITask {
    protected String outputDB, readDBConfigFileName, writeConfigFileName, viewQueryName;
    protected String snippetResultTable, inputTable, docResultTable, bunchResultTable, annotator;
    private DAO dao;
    public boolean joinDocTable = true;
    private String sourceQuery;
    private String primeTable = "RS";

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
        inputTable = config.getValue(ConfigKeys.inputTableName);
        outputDB = config.getValue(ConfigKeys.writeDBConfigFileName);
        snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
        docResultTable = config.getValue(ConfigKeys.docResultTableName);
        bunchResultTable = config.getValue(ConfigKeys.bunchResultTableName);
        readDBConfigFileName = config.getValue(ConfigKeys.readDBConfigFileName);
        writeConfigFileName = config.getValue(ConfigKeys.writeDBConfigFileName);
        if (!readDBConfigFileName.equals(writeConfigFileName))
            joinDocTable = false;
        config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);
        viewQueryName = config.getValue(ConfigKeys.viewQueryName);
        if (dao == null) {
            dao = new DAO(new File(readDBConfigFileName));
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", snippetResultTable, false);
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", docResultTable, false);
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", bunchResultTable, false);
        }

    }

    public String buildQuery(String queryName) {
        String sourceQuery;
        queryName = "query" + queryName + "Annos";
        if (joinDocTable)
            queryName = queryName + "WSource";
        if (queryName.toLowerCase().indexOf("bunch") != -1) {
            primeTable = "RB";
        } else if (queryName.toLowerCase().indexOf("doc") != -1) {
            primeTable = "RD";
        }
        sourceQuery = dao.queries.get(queryName);
        sourceQuery = sourceQuery.replaceAll("\\{tableName}", snippetResultTable)
                .replaceAll("\\{docResultTable}", docResultTable)
                .replaceAll("\\{bunchResultTable}", bunchResultTable)
                .replaceAll("\\{inputTable}", inputTable);
        return sourceQuery;
    }

    public String modifyQuery(String sourceQuery, String conditions) {
        if (conditions.length() > 0) {
            int limitPos = conditions.toLowerCase().indexOf(" limit ");
            if (limitPos > 0) {
                sourceQuery = sourceQuery + " WHERE ( " + conditions.substring(0, limitPos) + " ) " + conditions.substring(limitPos);
            } else {
                sourceQuery = sourceQuery + " WHERE ( " + conditions + " ) ";
            }
        }
        return sourceQuery;
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
                sourceQuery = buildQuery(viewQueryName);
                if (annotator.trim().length() > 0) {
                    DAO dao = new DAO(new File(outputDB));
                    if (!dao.checkTableExits(snippetResultTable)) {
                        updateMessage("Table '" + snippetResultTable + "' does not exit.");
                        popDialog("Note", "Table '" + snippetResultTable + "' does not exit.",
                                " You need to execute 'RunEasyCIE' first.");
                        updateProgress(0, 0);
                        return;
                    }
                    String annotatorLastRunid = getLastRunIdofAnnotator(dao, snippetResultTable, annotator);
                    String lastLogRunId = getLastLogRunId(dao, annotator);
                    if (annotatorLastRunid.equals("-1")) {
                        popDialog("Note", "There is no output in the previous runs of the annotator: \"" + annotator + "\"",
                                "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                        "if the dataset has been imported successfully.\n" +
                                        "EasyCIE will display all the previous outputs if there is any.");
                    } else {
                        String runId;
                        if (!annotatorLastRunid.equals(lastLogRunId)) {
                            popDialog("Note", "There is no output in the most recent run of annotator:\"" + annotator + "\"," +
                                            " which RUN_ID=" + annotatorLastRunid,
                                    "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                            "if the dataset has been imported successfully.\n" +
                                            "Instead, EasyCIE Will display the last run of annotator \"" + annotator + "\" that has some output, which " +
                                            "RUN_ID=" + lastLogRunId);
                            runId = lastLogRunId;
                        } else {
                            runId = annotatorLastRunid;
                            filter = primeTable + ".annotator='" + annotator + "' AND " + primeTable + ".RUN_ID=" + runId;
                            switch (primeTable) {
                                case "RB":
                                    filter = filter + " AND RD.RUN_ID=" + runId + " AND (RS.RUN_ID=" + runId + " OR RS.RUN_ID IS NULL)";
                                    break;
                                case "RD":
                                    filter = filter + " AND (RS.RUN_ID=" + annotatorLastRunid + " OR RS.RUN_ID IS NULL)";
                                    break;
                            }
                        }
                    }

                }
                String otherConditions = TasksOverviewController.currentTasksOverviewController.sqlFilter.getText().trim();
                if (otherConditions.length() == 0) {
                    TasksOverviewController.currentTasksOverviewController.sqlFilter.setText(filter);
                    otherConditions = filter;
                }
                if (otherConditions.length() > 0) {
                    sourceQuery = modifyQuery(sourceQuery, otherConditions);
                }

                RecordRowIterator recordIterator = dao.queryRecords(sourceQuery);
                TasksOverviewController.currentTasksOverviewController.doctable = false;
                res = TasksOverviewController.currentTasksOverviewController.showDBTable(recordIterator, ColorAnnotationCell.colorOutput);
                if (res)
                    updateMessage("data loaded");
                else
                    updateMessage("no record loaded");
                updateProgress(1, 1);
            }
        });
        return null;
    }

    public String getLastRunIdofAnnotator(DAO dao, String outputTable, String annotator) {
        String id = "-1";
        RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("maxRunIDofAnnotator", outputTable, annotator);
        if (recordRowIter.hasNext()) {
            RecordRow recordRow = recordRowIter.next();
            if (recordRow != null) {
                Object value = recordRow.getValueByColumnId(1);
                if (value != null) {
                    id = value + "";
                }
            }
        }
        return id;
    }

    public String getLastLogRunId(DAO dao, String annotator) {
        String id = "-1";
        RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("lastLogRunID", annotator);
        if (recordRowIter.hasNext()) {
            RecordRow recordRow = recordRowIter.next();
            if (recordRow != null) {
                Object value = recordRow.getValueByColumnId(1);
                if (value != null) {
                    id = value + "";
                }
            }
        }
        return id;
    }

}
