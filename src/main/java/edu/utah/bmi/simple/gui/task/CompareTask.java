package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import edu.utah.bmi.simple.gui.core.Compare;
import edu.utah.bmi.simple.gui.core.EvalCounter;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;


import java.io.File;
import java.util.*;

/**
 * Compare annotations against gold standard (based on SQLite, differentiate different annotators by "annotator")
 *
 * @author Jianlin Shi
 * @see edu.utah.bmi.simple.gui.task.CompareBDSTask
 * Created on 7/18/16.
 */
@Deprecated
public class CompareTask extends GUITask {
    protected Compare comparior;
    protected HashSet<String> types = new HashSet();
    private String diffTable, outputTable, compareReferenceTable, goldReferenceTable,
            targetAnnotator, referenceAnnotator, typeFilter, targetRunId, referenceRunId;
    private EDAO wdao, rdao;
    protected NLPDBLogger logger;
    private boolean strictCompare = false;
    public static int lastRunId = -1;


    public CompareTask(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIMessage("Initiate configurations..");
        TaskFX config = tasks.getTask(ConfigKeys.comparetask);

        targetAnnotator = config.getValue(ConfigKeys.targetAnnotator);
        targetRunId = config.getValue(ConfigKeys.targetRunId);

        referenceAnnotator = config.getValue(ConfigKeys.referenceAnnotator);
        referenceRunId = config.getValue(ConfigKeys.referenceRunId);

        typeFilter = config.getValue(ConfigKeys.typeFilter).trim();
        String compareMethodString = config.getValue(ConfigKeys.strictCompare).trim().toLowerCase();
        strictCompare = compareMethodString.startsWith("t") || compareMethodString.startsWith("1");
        compareReferenceTable = config.getValue(ConfigKeys.compareReferenceTable);


        TaskFX settingConfig = tasks.getTask("settings");
        String outputDB = settingConfig.getValue(ConfigKeys.writeDBConfigFileName);
        outputTable = settingConfig.getValue(ConfigKeys.snippetResultTableName);
        if (compareReferenceTable.trim().length() == 0)
            compareReferenceTable = outputTable;

        diffTable = settingConfig.getValue(ConfigKeys.compareTable).trim();


        String importDB = settingConfig.getValue(ConfigKeys.readDBConfigFileName);
        String goldReferenceTable = settingConfig.getValue(ConfigKeys.referenceTable);

        wdao = EDAO.getInstance(new File(outputDB));
        if (goldReferenceTable.equals(compareReferenceTable) && !importDB.equals(outputDB)) {
//            if compare with gold standard
            rdao = EDAO.getInstance(new File(importDB));
        } else {
//            if compare with different runs
            rdao = wdao;
        }

        if (diffTable.length() > 0) {
            wdao.initiateTableFromTemplate("ANNOTATION_TABLE", diffTable, false);
        }
        comparior = new Compare();
        comparior.setStrictCompare(strictCompare);
    }

    @Override
    protected Object call() throws Exception {
        HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> targetAnnotations = new HashMap<>();
        HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> referenceAnnotations = new HashMap<>();
        types.clear();
        logger = new NLPDBLogger(wdao, "LOG", "RUN_ID", targetAnnotator + "_vs_" + referenceAnnotator);
        logger.logStartTime();
        if (!wdao.checkTableExits(outputTable)) {
            updateGUIMessage("Table '" + outputTable + "' does not exit.");
            popDialog("Note", "Table '" + outputTable + "' does not exit.",
                    " You need to execute 'RunEasyCIE' first.");
            updateGUIProgress(0, 0);
            return null;
        }
        if (!rdao.checkTableExits(compareReferenceTable)) {
            updateGUIMessage("Table '" + compareReferenceTable + "' does not exit.");
            popDialog("Note", "Table '" + compareReferenceTable + "' does not exit.",
                    " You need to either execute 'RunEasyCIE' or import reference annotations.");
            updateGUIProgress(0, 0);
            return null;
        }

        readAnnotations(wdao, targetAnnotations, targetAnnotator, outputTable, typeFilter, targetRunId);
        readAnnotations(rdao, referenceAnnotations, referenceAnnotator, compareReferenceTable, typeFilter, referenceRunId);
        updateGUIMessage("Start comparing...");
        updateGUIProgress(0, 1);
        HashMap<String, EvalCounter> evalCounters = comparior.eval(targetAnnotations, referenceAnnotations, types, strictCompare);
        updateGUIProgress(1, 1);
        updateGUIMessage("Compare complete.");
        popDialog("Note", "Report: ", comparior.getScores(evalCounters));

        if (diffTable != null && diffTable.length() > 0) {
            wdao.initiateTableFromTemplate("ANNOTATION_TABLE", diffTable, false);
            comparior.logDiff(wdao, logger, strictCompare, evalCounters.get(comparior.total),
                    targetAnnotator, referenceAnnotator, diffTable);
        }
        lastRunId = (int) logger.getRunid();
        return null;
    }


    public void readAnnotations(EDAO dao, HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations,
                                String annotator, String annotatorTable, String typeFilter, String runId) {
        updateGUIMessage("Read the annotations of \"" + annotator + "\" from table \"" + annotatorTable + "\"....");
        ArrayList<String> conditions = new ArrayList<>();
        if (typeFilter != null && typeFilter.trim().length() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (String type : typeFilter.split(",")) {
                sb.append("type='" + type + "' OR ");

            }
            conditions.add(sb.substring(0, sb.length() - 4) + ")");
        }
        if (runId == null || runId.length() == 0) {
            runId = comparior.getMaxRunId(dao, annotatorTable, annotator);
        }
        if (runId != null && runId.length() > 0) {
            conditions.add("RUN_ID='" + runId + "'");
        }
        if (annotator.length() > 0) {
            conditions.add("annotator='" + annotator + "'");
        }
        String[] conditionArray = Arrays.copyOf(conditions.toArray(), conditions.size(), String[].class);
        int total = countQueryRecords(dao, annotatorTable, conditionArray);
        int count = 0;
        RecordRowIterator recordIterator = comparior.queryRecords(dao, annotatorTable, conditionArray);
        while (recordIterator.hasNext()) {
            RecordRow record = recordIterator.next();
            String type = record.getValueByColumnName("TYPE") + "";

            int begin = (int) record.getValueByColumnName("BEGIN") + (int) record.getValueByColumnName("SNIPPET_BEGIN");
            record.addCell("ABEGIN", begin);
            int end = (int) record.getValueByColumnName("END") + (int) record.getValueByColumnName("SNIPPET_BEGIN");
            record.addCell("AEND", end);


            types.add(type);
            if (!annotations.containsKey(type)) {
                annotations.put(type, new LinkedHashMap<>());
            }
            HashMap<String, ArrayList<RecordRow>> fileMap = annotations.get(type);
            String docName = (String) record.getValueByColumnName("DOC_NAME");
            if (!fileMap.containsKey(docName))
                fileMap.put(docName, new ArrayList<>());
            fileMap.get(docName).add(record);
            updateGUIProgress(count, total);
            count++;
        }
    }


    public static int countQueryRecords(EDAO dao, String tableName, String[] conditions) {
        int count = 0;
        StringBuilder sql = new StringBuilder();
        sql.append(dao.queries.get("queryCount").replaceAll("\\{tableName}",tableName));
        if (conditions != null && conditions.length > 0) {
            sql.append(" WHERE");
            for (int i = 0; i < conditions.length; i++) {
                String condition = conditions[i];
                sql.append(" ");
                sql.append(condition);
                if (i < conditions.length - 1)
                    sql.append(" AND");
            }
        }
        sql.append(";");

        RecordRowIterator recordIterator = dao.queryRecords(sql.toString());
        if (recordIterator.hasNext()) {
            RecordRow recordRow = recordIterator.next();
            Object value = recordRow.getValueByColumnId(1);
            if (value instanceof Long)
                count = ((Long) value).intValue();
            else
                count = (int) recordRow.getValueByColumnId(1);
        }
        return count;
    }


}
