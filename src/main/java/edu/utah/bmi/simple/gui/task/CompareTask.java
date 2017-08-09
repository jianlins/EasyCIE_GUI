package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.easycie.EvalCounter;
import edu.utah.bmi.nlp.runner.Compare;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.nlp.uima.NLPDBLogger;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Compare annotations against gold standard (based on SQLite, differentiate different annotators by "annotator")
 *
 * @author Jianlin Shi
 * Created on 7/18/16.
 */
public class CompareTask extends javafx.concurrent.Task {
    protected Compare comparior;
    protected HashSet<String> types = new HashSet();
    private String diffTable, outputTable, compareReferenceTable, goldReferenceTable,
            targetAnnotator, referenceAnnotator, typeFilter, targetRunId, referenceRunId;
    private DAO wdao, rdao;
    protected NLPDBLogger logger;
    private boolean strictCompare = false;


    public CompareTask(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("compare");

        targetAnnotator = config.getValue(ConfigKeys.targetAnnotator);
        targetRunId = config.getValue(ConfigKeys.targetRunId);

        referenceAnnotator = config.getValue(ConfigKeys.referenceAnnotator);
        referenceRunId = config.getValue(ConfigKeys.referenceRunId);

        typeFilter = config.getValue(ConfigKeys.typeFilter).trim();
        String compareMethodString = config.getValue(ConfigKeys.strictCompare).trim().toLowerCase();
        strictCompare = compareMethodString.startsWith("t") || compareMethodString.startsWith("1");
        compareReferenceTable = config.getValue(ConfigKeys.compareReferenceTable);


        TaskFX settingConfig = tasks.getTask("settings");
        String outputDB = settingConfig.getValue(ConfigKeys.writeConfigFileName);
        outputTable = settingConfig.getValue(ConfigKeys.outputTableName);
        if (compareReferenceTable.trim().length() == 0)
            compareReferenceTable = outputTable;

        diffTable = settingConfig.getValue(ConfigKeys.compareTable).trim();


        String importDB = settingConfig.getValue(ConfigKeys.readDBConfigFile);
        String goldReferenceTable = settingConfig.getValue(ConfigKeys.referenceTable);

        wdao = new DAO(new File(outputDB));
        if (goldReferenceTable.equals(compareReferenceTable)) {
//            if compare with gold standard
            rdao = new DAO(new File(importDB));
        } else {
//            if compare with different runs
            rdao = wdao;
        }

        if (diffTable.length() > 0) {
            wdao.initiateTable(diffTable, false);
        }
        comparior = new Compare();
        comparior.setStrictCompare(strictCompare);
    }

    @Override
    protected Object call() throws Exception {
        HashMap<String, HashMap<String, ArrayList<RecordRow>>> targetAnnotations = new HashMap<>();
        HashMap<String, HashMap<String, ArrayList<RecordRow>>> referenceAnnotations = new HashMap<>();
        types.clear();
        logger = new NLPDBLogger(wdao, "LOG", "RUN_ID", targetAnnotator + "_vs_" + referenceAnnotator);
        logger.logStartTime();
        readAnnotations(wdao, targetAnnotations, targetAnnotator, outputTable, typeFilter, targetRunId);
        readAnnotations(rdao, referenceAnnotations, referenceAnnotator, compareReferenceTable, typeFilter, referenceRunId);
        updateMessage("Start comparing...");
        updateProgress(0, 1);
        HashMap<String, EvalCounter> evalCounters = comparior.eval(targetAnnotations, referenceAnnotations, types, strictCompare);
        updateProgress(1, 1);
        updateMessage("Compare complete.");
        updateMessage("Note|Report: |" + comparior.getScores(evalCounters));

        if (diffTable != null && diffTable.length() > 0) {
            wdao.initiateTableFromTemplate("ANNOTATION_TABLE", diffTable, false);
            comparior.logDiff(wdao, logger, strictCompare, evalCounters.get(comparior.total),
                    targetAnnotator, referenceAnnotator, diffTable);
        }
        return null;
    }


    public void readAnnotations(DAO dao, HashMap<String, HashMap<String, ArrayList<RecordRow>>> annotations,
                                String annotator, String annotatorTable, String typeFilter, String runId) {
        updateMessage("Read the annotations of \"" + annotator + "\" from table \"" + annotatorTable + "\"....");
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
                annotations.put(type, new HashMap<>());
            }
            HashMap<String, ArrayList<RecordRow>> fileMap = annotations.get(type);
            String docName = (String) record.getValueByColumnName("DOC_NAME");
            if (!fileMap.containsKey(docName))
                fileMap.put(docName, new ArrayList<>());
            fileMap.get(docName).add(record);
            updateProgress(count, total);
            count++;
        }
    }


    public int countQueryRecords(DAO dao, String tableName, String[] conditions) {
        int count = 0;
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ");
        sql.append(tableName);
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
            count = (int) recordIterator.next().getValueByColumnId(1);
        }
        return count;
    }


}
