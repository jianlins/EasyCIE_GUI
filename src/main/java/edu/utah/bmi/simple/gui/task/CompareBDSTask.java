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
    protected EDAO wdao, rdao;
    protected NLPDBLogger logger;
    private boolean strictCompare = false;
    public static int lastRunId = -1;
    //  To record which type is snippet level, document level, or bunch level, based on from which table read the annotations of that type.
    private TreeMap<String, String> typeCategories = new TreeMap<>();
    protected ArrayList<ArrayList<Object>> comparingTypeFeatures;
    protected LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<Integer>>>> configIndices;
    private static final String SYS = "sys", REF = "ref";

    public CompareBDSTask(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        if (tasks == null)
            return;
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
        configIndices = new LinkedHashMap<>();
        comparingTypeFeatures = readCompareFeatures(compareConfig.getValue(ConfigKeys.typeFeatureFilter).trim(), configIndices);

        if (comparingTypeFeatures.size() == 0 && typeFilter.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (String type : typeFilter.split(",")) {
                type = type.trim();
                sb.append(type);
                sb.append("\t\t");
                sb.append(type);
                sb.append("\t\n");
            }
            comparingTypeFeatures = readCompareFeatures(sb.toString(), configIndices);
        }


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
            readAnnotations(wdao, targetAnnotations, targetAnnotator, snippetResultTable, targetRunId, configIndices, comparingTypeFeatures, SYS);
        if (wdao.checkTableExits(documentResultTable))
            readAnnotations(wdao, targetAnnotations, targetAnnotator, documentResultTable, targetRunId, configIndices, comparingTypeFeatures, SYS);
        if (wdao.checkTableExits(bunchResultTable))
            readAnnotations(wdao, targetAnnotations, targetAnnotator, bunchResultTable, targetRunId, configIndices, comparingTypeFeatures, SYS);

        readAnnotations(rdao, referenceAnnotations, referenceAnnotator, compareReferenceTable, targetRunId, configIndices, comparingTypeFeatures, REF);
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
     * @param dao                   DAO object
     * @param annotations           annotations ordered by names, annotation type (and feature values)
     * @param annotator             annotator name
     * @param annotatorTable        which table to read from
     * @param configIndices         a map of type and feature_value pair strings to matching row ids.
     * @param comparingTypeFeatures matching configurations
     * @param sysorref              System output or reference annotation
     * @param runId                 run id
     * @see CompareBDSTask#readCompareFeatures
     */
    public void readAnnotations(EDAO dao, HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations,
                                String annotator, String annotatorTable, String runId,
                                LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<Integer>>>> configIndices,
                                ArrayList<ArrayList<Object>> comparingTypeFeatures, String sysorref) {
        updateGUIMessage("Read the annotations of \"" + annotator + "\" from table \"" + annotatorTable + "\"....");
        ArrayList<String> conditions = new ArrayList<>();
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<Integer>>> configIndex = configIndices.get(sysorref);
        if (configIndex.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("(type IN (");
            for (String type : configIndex.keySet()) {
                sb.append("'" + type + "',");

            }
            conditions.add(sb.substring(0, sb.length() - 1) + "))");
        } else
            return;
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
            String features = record.getStrByColumnName("FEATURES");
            if (annotatorTable.equals(snippetResultTable))
                typeCategories.put(type, "SNIPPET");
            else if (annotatorTable.equals(documentResultTable))
                typeCategories.put(type, "DOCUMENT");
            else
                typeCategories.put(type, "BUNCH");
            String key;
            for (String featureValuePairsString : configIndex.get(type).keySet()) {
                key = type + "_" + featureValuePairsString;
                int matchId = configIndex.get(type).get(featureValuePairsString).get(0);
                ArrayList<String> FeatureValuePairs;
                boolean featureMatched = true;
                if (sysorref.equals(SYS)) {
                    FeatureValuePairs = (ArrayList<String>) comparingTypeFeatures.get(matchId).get(3);
//                  get corresponding reference type_feature_values
                    key = comparingTypeFeatures.get(matchId).get(0) + "_" + getFeatureValuePairsString((ArrayList<String>) comparingTypeFeatures.get(matchId).get(1));
                } else
                    FeatureValuePairs = (ArrayList<String>) comparingTypeFeatures.get(matchId).get(1);
                for (String featureValue : FeatureValuePairs) {
                    if (features.indexOf(featureValue) == -1) {
                        featureMatched = false;
                        break;
                    }
                }
                if (featureMatched) {
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

        }

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
     * For each row, the 1st column is for the reference type name, the 2nd is a string of the feature name and value
     * pairs (each pair is separated by a colon) separated by semicolons, the 3rd is the system output type name, the
     * 4th is is a string of feature and value pairs of system output type.
     * <p>
     * The inner value of configIndex is an ArrayList, because one type_feature_value annotation config of reference standard
     * can be set to match multiple  type_feature_value annotation config in system output (not vice versa). For instance,
     * TypeA in reference can be considered as the join annotations of TypeB and TypeC in system output.
     *
     * @param typeFeatureConfigFile location of the configuration file
     * @param configIndices the map of type --- feature_value_pair_String --- configuration id
     * @return a map that using type name as the key, a list of feature names as the value
     */
    protected ArrayList<ArrayList<Object>> readCompareFeatures(String typeFeatureConfigFile,
                                                               LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, ArrayList<Integer>>>> configIndices) {
        ArrayList<ArrayList<Object>> comparingTypeFeatures = new ArrayList<>();
        if (typeFeatureConfigFile == null || typeFeatureConfigFile.trim().length() == 0)
            return comparingTypeFeatures;
        IOUtil ioUtil = new IOUtil(typeFeatureConfigFile, false);
        ArrayList<ArrayList<String>> rows = ioUtil.getRuleCells();
        if (rows == null) {
            return comparingTypeFeatures;
        }
        int id = 0;
        if (configIndices.size() == 0) {
            configIndices.put(SYS, new LinkedHashMap<>());
            configIndices.put(REF, new LinkedHashMap<>());
        }
        for (ArrayList<String> row : rows) {
            if (row.size() < 3 || row.get(0).startsWith("#"))
                continue;
            ArrayList<Object> config = new ArrayList<>();
            config.add(row.get(0));
            ArrayList<String> pairs = getFeatureValuePairs(row.get(1));
            config.add(pairs);
            updateIndex(configIndices.get(REF), row.get(0), row.get(1), id);
            config.add(row.get(2));
            if (row.size() < 4) {
                config.add(new ArrayList<String>());
                updateIndex(configIndices.get(SYS), row.get(2), "", id);
            } else {
                config.add(getFeatureValuePairs(row.get(3)));
                updateIndex(configIndices.get(SYS), row.get(2), row.get(3), id);
            }
            comparingTypeFeatures.add(config);
            id++;
        }
        return comparingTypeFeatures;
    }

    protected void updateIndex(LinkedHashMap<String, LinkedHashMap<String, ArrayList<Integer>>> index, String type,
                               String featureValuePairString, int id) {
        if (!index.containsKey(type)) {
            index.put(type, new LinkedHashMap<>());
        }
        if (!index.get(type).containsKey(featureValuePairString)) {
            index.get(type).put(featureValuePairString, new ArrayList<>());
        }
        index.get(type).get(featureValuePairString).add(id);
    }

    protected ArrayList<String> getFeatureValuePairs(String pairsString) {
        ArrayList<String> pairs = new ArrayList<>();
        for (String pairString : pairsString.split(";")) {
            if (pairsString.trim().length() == 0)
                continue;
            String[] pair = pairString.split(":");
            pairs.add(pair[0].trim() + ": " + pair[1].trim());
        }
        return pairs;
    }

    protected String getFeatureValuePairsString(ArrayList<String> pairs) {
        StringBuilder sb = new StringBuilder();
        if(pairs.size()<1)
            return "";
        for (String pair : pairs) {
            sb.append(pair.replaceAll(" ", ""));
            sb.append(";");
        }
        return sb.substring(0, sb.length() - 1);
    }

}
