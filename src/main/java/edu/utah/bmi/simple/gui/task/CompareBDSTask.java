package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.IOUtil;
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

import static edu.utah.bmi.simple.gui.core.Compare.getIntegerValue;

/**
 * Compare annotations against gold standard (based on SQLite, differentiate different annotators by "annotator")
 * Deal with bunch, document, and snippet annotations.
 *
 * @author Jianlin Shi
 * Created on 7/18/16.
 */
public class CompareBDSTask extends GUITask {
    protected Compare comparior;
    //    protected TreeSet<String> types = new TreeSet();
    private String diffTable, snippetResultTable, documentResultTable, bunchResultTable, compareReferenceTable, goldReferenceTable,
            targetAnnotator, referenceAnnotator, typeFilter, targetRunId, referenceRunId;
    private EDAO wdao, rdao;
    protected NLPDBLogger logger;
    private boolean strictCompare = false;
    public static int lastRunId = -1;
    //  To record which type is snippet level, document level, or bunch level, based on from which table read the annotations of that type.
    private TreeMap<String, String> typeCategories = new TreeMap<>();

    private LinkedHashMap<String, LinkedHashSet<String>> typeFeatures = new LinkedHashMap<>();

    public CompareBDSTask(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIMessage("Initiate configurations..");
        TaskFX compareConfig = tasks.getTask(ConfigKeys.comparetask);

        targetAnnotator = compareConfig.getValue(ConfigKeys.targetAnnotator);
        if (targetAnnotator.trim().length() == 0) {
            targetAnnotator = tasks.getTask(ConfigKeys.maintask).getValue(ConfigKeys.annotator);
        }
        targetRunId = compareConfig.getValue(ConfigKeys.targetRunId);

        referenceAnnotator = compareConfig.getValue(ConfigKeys.referenceAnnotator);
        referenceRunId = compareConfig.getValue(ConfigKeys.referenceRunId);

        typeFilter = compareConfig.getValue(ConfigKeys.typeFilter).trim();
        String compareMethodString = compareConfig.getValue(ConfigKeys.strictCompare).trim().toLowerCase();
        strictCompare = compareMethodString.startsWith("t") || compareMethodString.startsWith("1");
        compareReferenceTable = compareConfig.getValue(ConfigKeys.compareReferenceTable);

        typeFeatures = readCompareFeatures(compareConfig.getValue(ConfigKeys.typeFeatureFilter).trim());


        TaskFX settingConfig = tasks.getTask("settings");
        String outputDB = settingConfig.getValue(ConfigKeys.writeDBConfigFileName);
        snippetResultTable = settingConfig.getValue(ConfigKeys.snippetResultTableName);
        documentResultTable = settingConfig.getValue(ConfigKeys.docResultTableName);
        bunchResultTable = settingConfig.getValue(ConfigKeys.bunchResultTableName);

        if (compareReferenceTable.trim().length() == 0)
            compareReferenceTable = snippetResultTable;

        diffTable = settingConfig.getValue(ConfigKeys.compareTable).trim();


        String importDB = settingConfig.getValue(ConfigKeys.readDBConfigFileName);
        String goldReferenceTable = settingConfig.getValue(ConfigKeys.referenceTable);

        wdao = EDAO.getInstance(new File(outputDB));
        logger = new NLPDBLogger(outputDB, "LOG", "RUN_ID", targetAnnotator + "_vs_" + referenceAnnotator);
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
        HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> targetAnnotations = new LinkedHashMap<>();
        HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> referenceAnnotations = new LinkedHashMap<>();
        typeCategories.clear();

        logger.logStartTime();
        if (!wdao.checkTableExits(snippetResultTable)) {
            updateGUIMessage("Table '" + snippetResultTable + "' does not exit.");
            popDialog("Note", "Table '" + snippetResultTable + "' does not exit.",
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

        if (wdao.checkTableExits(snippetResultTable))
            readAnnotations(wdao, targetAnnotations, targetAnnotator, snippetResultTable, typeFilter, typeFeatures, targetRunId);
        if (wdao.checkTableExits(documentResultTable))
            readAnnotations(wdao, targetAnnotations, targetAnnotator, documentResultTable, typeFilter, typeFeatures, targetRunId);
        if (wdao.checkTableExits(bunchResultTable))
            readAnnotations(wdao, targetAnnotations, targetAnnotator, bunchResultTable, typeFilter, typeFeatures, targetRunId);

        readAnnotations(rdao, referenceAnnotations, referenceAnnotator, compareReferenceTable, typeFilter, typeFeatures, referenceRunId);
        updateGUIMessage("Start comparing...");
        updateGUIProgress(0, 1);
        HashMap<String, EvalCounter> evalCounters = comparior.eval(targetAnnotations, referenceAnnotations, referenceAnnotations.keySet(), strictCompare);
        updateGUIProgress(1, 1);
        updateGUIMessage("Compare complete.");
        popDialog("Note", "Report: ", comparior.getScores(evalCounters));

        if (diffTable != null && diffTable.length() > 0) {
            wdao.initiateTableFromTemplate("ANNOTATION_TABLE", diffTable, false);
            comparior.logDiff(wdao, logger, strictCompare, evalCounters.get(comparior.total),
                    targetAnnotator, referenceAnnotator, diffTable);
        }
        return null;
    }


    /**
     * Read annotations from db.
     * If typeFeatures is configured, typeFilter will be ignored
     *
     * @param dao            DAO object
     * @param annotations    annotations ordered by names, annotation type (and feature values)
     * @param annotator      annotator name
     * @param annotatorTable which table to read from
     * @param typeFilter     a string of type names separated by comma
     * @param typeFeatures   a configuration file
     * @param runId          run id
     * @see edu.utah.bmi.simple.gui.task.CompareBDSTask#readCompareFeatures(String)
     */
    public void readAnnotations(EDAO dao, HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations,
                                String annotator, String annotatorTable, String typeFilter,
                                LinkedHashMap<String, LinkedHashSet<String>> typeFeatures, String runId) {
        updateGUIMessage("Read the annotations of \"" + annotator + "\" from table \"" + annotatorTable + "\"....");
        ArrayList<String> conditions = new ArrayList<>();
        if (typeFeatures.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (String type : typeFeatures.keySet()) {
                sb.append("type='" + type + "' OR ");

            }
                conditions.add(sb.substring(0, sb.length() - 4) + ")");
            } else if (typeFilter != null && typeFilter.trim().length() > 0) {
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
            int snippetBegin = getIntegerValue(record.getValueByColumnName("SNIPPET_BEGIN"));

            int begin = getIntegerValue(record.getValueByColumnName("BEGIN")) + snippetBegin;
            record.addCell("ABEGIN", begin);
            int end = getIntegerValue(record.getValueByColumnName("END")) + snippetBegin;
            record.addCell("AEND", end);


            if (annotatorTable.equals(snippetResultTable))
                typeCategories.put(type, "SNIPPET");
            else if (annotatorTable.equals(documentResultTable))
                typeCategories.put(type, "DOCUMENT");
            else
                typeCategories.put(type, "BUNCH");
            String key = type;
            if (typeFeatures.size() > 0) {
                if (typeFeatures.containsKey(type) && (typeFeatures.get(type).size() > 0)) {
                    key = aggregateFeatureValues(type, record, typeFeatures.get(type));
                }
            }
            if (!annotations.containsKey(key)) {
                annotations.put(key, new LinkedHashMap<>());
            }
            HashMap<String, ArrayList<RecordRow>> fileMap = annotations.get(key);
            String docName = (String) record.getValueByColumnName("DOC_NAME");
            if (!fileMap.containsKey(docName))
                fileMap.put(docName, new ArrayList<>());
            fileMap.get(docName).add(record);
            updateGUIProgress(count, total);
            count++;
        }

    }

    private String aggregateFeatureValues(String type, RecordRow record, LinkedHashSet<String> featureNames) {
        StringBuilder sb = new StringBuilder();

        if (record.getStrByColumnName("FEATURES").length() > 0)
            for (String nameValue : record.getStrByColumnName("FEATURES").split("\n")) {
                String[] pair = nameValue.split(":");
                String featureName = pair[0].trim();
                if (featureNames.contains(featureName)) {
                    sb.append(pair[0]);
                    sb.append("_");
                    sb.append(pair[1]);
                    sb.append(",");
                }
            }
        if (sb.length() > 0) {
            sb.insert(0, type + ":");
            sb.deleteCharAt(sb.length() - 1);
        } else {
            sb.append(type);
        }
        return sb.toString();
    }


    public static int countQueryRecords(EDAO dao, String tableName, String[] conditions) {
        int count = 0;
        StringBuilder sql = new StringBuilder();
        sql.append(dao.queries.get("queryCount").replaceAll("\\{tableName}", tableName));
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
//        sql.append(";");

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

    /**
     * Read from a configuration file that list the types and corresponding features that need to be compared
     * For each row, the 1st column is for the type name, the second and the rest are for the feature names
     *
     * @param typeFeatureConfigFile location of the configuration file
     * @return a map that using type name as the key, a list of feature names as the value
     */
    private LinkedHashMap<String, LinkedHashSet<String>> readCompareFeatures(String typeFeatureConfigFile) {
        LinkedHashMap<String, LinkedHashSet<String>> typeFeatures = new LinkedHashMap<>();
        if (typeFeatureConfigFile == null || typeFeatureConfigFile.trim().length() == 0)
            return typeFeatures;
        IOUtil ioUtil = new IOUtil(typeFeatureConfigFile,false);
        ArrayList<ArrayList<String>> rows = ioUtil.getRuleCells();
        for (ArrayList<String> row : rows) {
            if (!typeFeatures.containsValue(row.get(0))) {
                typeFeatures.put(row.get(0), new LinkedHashSet<>());
            }
            typeFeatures.get(row.get(0)).addAll(row.subList(1, row.size()));
        }
        return typeFeatures;
    }


}
