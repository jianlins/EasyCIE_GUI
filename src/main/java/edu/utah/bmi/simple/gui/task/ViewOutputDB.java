package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.EDAO;
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
    protected String outputDB, readDBConfigFileName, writeConfigFileName, viewQueryName, referenceTable;
    protected String snippetResultTable, inputTable, docResultTable, bunchResultTable, annotator, referenceAnnotator;
    private EDAO dao;
    public boolean joinDocTable = true;
    public static String sourceQuery;
    public boolean viewReference = false, sep_db = false;


    protected ViewOutputDB() {

    }

    public ViewOutputDB(TasksFX tasks) {
        initiate(tasks, "");
    }

    public ViewOutputDB(TasksFX tasks, String paras) {
        initiate(tasks, paras);
    }

    protected void initiate(TasksFX tasks, String paras) {

        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("settings");
        TasksOverviewController.currentTasksOverviewController.currentGUITask = this;

        inputTable = config.getValue(ConfigKeys.inputTableName);
        outputDB = config.getValue(ConfigKeys.writeDBConfigFileName);
        snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
        docResultTable = config.getValue(ConfigKeys.docResultTableName);
        bunchResultTable = config.getValue(ConfigKeys.bunchResultTableName);
        readDBConfigFileName = config.getValue(ConfigKeys.readDBConfigFileName);
        writeConfigFileName = config.getValue(ConfigKeys.writeDBConfigFileName);
        referenceTable = config.getValue(ConfigKeys.referenceTable);

        if (!readDBConfigFileName.equals(writeConfigFileName))
            joinDocTable = false;
        config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);


        referenceAnnotator = config.getValue(ConfigKeys.referenceAnnotator);
        TasksOverviewController.currentTasksOverviewController.annoSqlFilter.setText("");
        viewQueryName = config.getValue(ConfigKeys.viewQueryName);
        dao = EDAO.getInstance(new File(outputDB));

        config = tasks.getTask("import");
        String importAnnotator = config.getValue(ConfigKeys.overWriteAnnotatorName);
        if (paras.equals(importAnnotator) || paras.equals("ref")) {
            viewReference = true;
            referenceAnnotator = importAnnotator;
        }


    }

    public static String[] buildQuery(EDAO dao, String queryName, String annotator,
                                      String snippetResultTable, String docResultTable, String bunchResultTable,
                                      String inputTable) {
        String sourceQuery, primeTable = "RS";
//       match to querySnippetAnnos, queryDocSnippetAnnos, queryBunchDocSnippetAnnos
//        or querySnippetAnnosWSource,queryDocSnippetAnnosWSource,queryBunchDocSnippetAnnosWSource
        queryName = "query" + queryName + "Annos";
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
        String filter = "", annotatorLastRunid = "", lastLogRunId = "";
        if (annotator.trim().length() > 0) {
            if (!dao.checkTableExits(snippetResultTable)) {
                return null;
            }
            annotatorLastRunid = getLastRunIdofAnnotator(dao, snippetResultTable, annotator);
            lastLogRunId = getLastLogRunId(dao, annotator);
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
                filter = primeTable + ".RUN_ID=" + runId;
            }

        }
        return new String[]{sourceQuery, filter, annotatorLastRunid, lastLogRunId};
    }

    public static String modifyQuery(String sourceQuery, String conditions) {
        String limit = "";
        if (sourceQuery.toLowerCase().indexOf(" limit ") > -1) {
            int limitPos = sourceQuery.toLowerCase().indexOf(" limit ");
            limit = sourceQuery.substring(limitPos);
            sourceQuery = sourceQuery.substring(0, limitPos);
        }
        if (conditions.length() > 0) {
            int limitPos = conditions.toLowerCase().indexOf(" limit ");
            int orderPos = conditions.toLowerCase().indexOf(" order ");
            if (orderPos > 0) {
                sourceQuery = sourceQuery + " WHERE ( " + conditions.substring(0, orderPos) + " ) " + conditions.substring(orderPos);
            } else if (limitPos > 0) {
                limit = "";
                sourceQuery = sourceQuery + " WHERE ( " + conditions.substring(0, limitPos) + " ) " + conditions.substring(limitPos);
            } else {
                sourceQuery = sourceQuery + " WHERE ( " + conditions + " ) ";
            }
        }
        if (limit.length() > 0) {
            sourceQuery = sourceQuery + limit;
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
                String[] values;
                String snippetTable = snippetResultTable;
                String viewAnnotator = annotator;
                String currentViewQueryName = viewQueryName;
                if (viewReference) {
                    snippetTable = referenceTable;
                    viewAnnotator = referenceAnnotator;
                    currentViewQueryName = "";
                }
                if (dao.isClosed())
                    dao = EDAO.getInstance(new File(outputDB));
                if (!readDBConfigFileName.equals(writeConfigFileName)) {
                    currentViewQueryName += "Sep";
                    sep_db = true;
                }
                values = buildQuery(dao, currentViewQueryName, viewAnnotator, snippetTable, docResultTable, bunchResultTable, inputTable);
                sourceQuery = values[0];
                String filter = values[1];
                String annotatorLastRunid = values[2];
                String lastLogRunId = values[3];
                if (values == null) {
                    updateMessage("Table '" + snippetTable + "' does not exit.");
                    popDialog("Note", "Table '" + snippetTable + "' does not exit.",
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
                    sourceQuery = modifyQuery(sourceQuery, otherConditions);
                }
                dao.close();
                if (viewReference) {
                    if (!sep_db)
                        res = TasksOverviewController.currentTasksOverviewController.showDBTable(sourceQuery, outputDB,
                                ColorAnnotationCell.colorOutput, TasksOverviewController.RefView);
                    else
                        res = TasksOverviewController.currentTasksOverviewController.showDBTable(sourceQuery, new String[]{readDBConfigFileName, writeConfigFileName},
                                ColorAnnotationCell.colorOutput, TasksOverviewController.RefView);
                } else {
                    if (!sep_db)
                        res = TasksOverviewController.currentTasksOverviewController.showDBTable(sourceQuery, outputDB,
                                ColorAnnotationCell.colorOutput, TasksOverviewController.AnnoView);
                    else
                        res = TasksOverviewController.currentTasksOverviewController.showDBTable(sourceQuery, new String[]{readDBConfigFileName, writeConfigFileName},
                                ColorAnnotationCell.colorOutput, TasksOverviewController.AnnoView);
                }
                if (res)
                    updateMessage("data loaded");
                else
                    updateMessage("no record loaded");
                updateProgress(1, 1);
            }
        });
        return null;
    }

    public static String getLastRunIdofAnnotator(EDAO dao, String outputTable, String annotator) {
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

    public static String getLastLogRunId(EDAO dao, String annotator) {
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
