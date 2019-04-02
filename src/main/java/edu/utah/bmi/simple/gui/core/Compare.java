package edu.utah.bmi.simple.gui.core;


import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.*;

import static edu.utah.bmi.simple.gui.core.CommonFunc.addOption;
import static edu.utah.bmi.simple.gui.core.CommonFunc.getCmdValue;


/**
 * Compare annotations against gold standard (based on SQLite, differentiate different annotators by "annotator")
 *
 * @author Jianlin Shi
 * Created on 7/18/16.
 */
public class Compare {
    protected EDAO dao1, daor;
    protected NLPDBLogger logger;
    protected HashSet<String> types = new HashSet<>();
    protected Boolean strictCompare = false;
    public final String total = "OVERALL";
    public HashMap<String, EvalCounter> evalCounters;


    public static void main(String[] args) {
        Compare compare = new Compare(args);
    }

    public Compare() {

    }

    public Compare(String[] args) {
        CommandLine cmd = parseCommand(args);
        init(cmd);
    }

    protected void init(CommandLine cmd) {
        String writeConfigFileName = getCmdValue(cmd, "wconfig", "");
        String nlpOutputTable = getCmdValue(cmd, "writetable", "OUTPUT");
        String annotator = getCmdValue(cmd, "annotator", "uima");
        String runId1 = getCmdValue(cmd, "runid1", null);
        String runId2 = getCmdValue(cmd, "runid2", null);


        String referConfigFileName = getCmdValue(cmd, "rconfig", null);
        String referenceTableName = getCmdValue(cmd, "referencetable", "REFERENCE");
        String referenceAnnotator = getCmdValue(cmd, "referenceannotator", "ehost");

        String diffTableName = cmd.hasOption("difftable") ? getCmdValue(cmd, "difftable", "DIFF") : null;

        String typeFilter = getCmdValue(cmd, "comparetypes", null);

        strictCompare = cmd.hasOption("-s");

        logger = new NLPDBLogger(writeConfigFileName, "LOG", "RUN_ID", annotator + "_vs_" + referenceAnnotator);
        logger.logStartTime();
        run(annotator, nlpOutputTable, runId1, referenceAnnotator, referenceTableName, runId2, diffTableName, typeFilter);
    }


    public String getMaxRunId(EDAO dao, String tableName, String annotator) {
        String runId = null;
        try {
            RecordRowIterator records = dao.queryRecordsFromPstmt("maxRunIDofAnnotator", tableName, annotator);
            if (records.hasNext()) {
                Object runIdObj = records.next().getValueByColumnId(1);
                if (runIdObj != null)
                    runId = runIdObj.toString();
            }
        } catch (Exception e) {

        }
        return runId;
    }

    public void setStrictCompare(boolean strictCompare) {
        this.strictCompare = strictCompare;
    }

    protected CommandLine parseCommand(String[] args) {
        Options options = new Options();
        addOption(options, "w", "wconfig",
                "db configuration file for the reading nlp annotations and write error annotations", "1", "1");
        addOption(options, "wt", "writetable",
                "the table name where nlp needs to read nlp written annotations and write error annotations(default: \"OUTPUT\")", "0", "1");
        addOption(options, "a", "annotator",
                "annotator's name for nlp output--use to differentiate the outputs of different runs (default: uima)", "0", "1");

        addOption(options, "rid1", "runid1",
                "dataset id/name differentiate different dataset in corpus table (default: the latest runid of the same annotator)", "0", "1");

        addOption(options, "rid2", "runid2",
                "dataset id/name differentiate different dataset in corpus table (default: the latest runid of the same annotator)", "0", "1");

        addOption(options, "r", "rconfig",
                "[Required]----the db configuration file for reading reference annotations (default: the same as wconfig)", "0", "1");
        addOption(options, "rt", "referencetable",
                "the table name where nlp needs to read reference annotations from (default: \"REFERENCE\")", "0", "1");
        addOption(options, "ra", "referenceannotator",
                "the annotators' name of the reference annotations (default: \"ehost\")", "0", "1");

        addOption(options, "dt", "difftable",
                "the table name to write error annotations, if this parameter is not specified," +
                        " the error annotations will not be saved. ", "0", "1");

        addOption(options, "t", "comparetypes",
                "the uima types to be compared, multiple type names can be separated using comma. If" +
                        "this parameter is not included, will compare all types.",
                "0", "1");

        addOption(options, "s", "strict",
                "Whether use strict or relax comparison (default: relax).",
                "0", "0");

        addOption(options, "h", "help", "print help information", "0", "0");


        CommandLine cmd = CommonFunc.parseArgs(options, args, "RunPipe");
        return cmd;
    }


    public void run(String annotator1, String annotator1Table, String runId1,
                    String annotator2, String annotator2Table, String runId2,
                    String diffTable, String typeFilter) {
        logger.logStartTime();
        HashMap<String, EvalCounter> evalCounters = evalAnnotators(annotator1, annotator1Table, runId1,
                annotator2, annotator2Table, runId2, typeFilter);
        printScores(evalCounters);
        if (diffTable != null && diffTable.length() > 0) {
            dao1.initiateTableFromTemplate("ANNOTATION_TABLE", diffTable, false);
            logDiff(dao1, logger, strictCompare, evalCounters.get(total), annotator1, annotator2, diffTable);
        }
    }

    public void logDiff(EDAO dao, NLPDBLogger logger, boolean strictCompare, EvalCounter evalCounter, String annotator, String annotator2, String diffTable) {
        String compareName = annotator + "_vs_" + annotator2;
//        System.out.println("\n" + compareName + " comparision finished. Saving the difference to SQLite...");
        logAnnoDifferenceWSentence(dao, logger, compareName, "fn", evalCounter.fns, diffTable);
        logAnnoDifferenceWSentence(dao, logger, compareName, "fp", evalCounter.fps, diffTable);
        logger.logCompletToDB(evalCounter.total(), strictCompare ? "strict compare" : "relax compare");
        logger.collectionProcessComplete(getScore());
        dao.close();
    }

    protected void logAnnoDifferenceWSentence(EDAO dao, NLPDBLogger logger, String annotator, String note, ArrayList<RecordRow> fpannos, String diffTable) {
        ArrayList<RecordRow> records = new ArrayList<>();
        for (RecordRow anno : fpannos) {
            anno.addCell("ANNOTATOR", annotator);
            anno.addCell("COMMENTS", note);
            anno.addCell("RUN_ID", logger.getRunid());
            records.add(anno);
        }
        if (records.size() > 0)
            dao.insertRecords(diffTable, records);
    }


    public void printScores(HashMap<String, EvalCounter> evalCounters) {
        System.out.println(getScores(evalCounters));
    }

    public String getScore() {
        return getScores(evalCounters);
    }

    public String getScores(HashMap<String, EvalCounter> evalCounters) {
        StringBuilder sb = new StringBuilder();
        for (String type : evalCounters.keySet()) {
            EvalCounter evalCounter = evalCounters.get(type);
            if (type.equals(total)) {
                sb.append("Overall peformance using " + (strictCompare ? "strict" : "relax") + " comparison:\n");
                sb.append(evalCounter.report("\t") + "\n");
            } else {
                sb.append("  Peformance for type \"" + type + "\":\n");
                sb.append(evalCounter.report("\t\t") + "\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }


    protected HashMap<String, EvalCounter> evalAnnotators(String annotator1, String annotator1Table, String runId1,
                                                          String annotator2, String annotator2Table, String runId2,
                                                          String typeFilter) {
        HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations1 = new HashMap<>();
        HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations2 = new HashMap<>();
        readAnnotations(dao1, annotations1, annotator1, annotator1Table, typeFilter, runId1);
        readAnnotations(daor, annotations2, annotator2, annotator2Table, typeFilter, runId2);

        System.out.println("Read annotation finished. Start comparison");

        evalCounters = eval(annotations1, annotations2, types, strictCompare);
        return evalCounters;
    }

    public HashMap<String, EvalCounter> eval(HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations1,
                                             HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations2,
                                             Set<String> types, boolean strictCompare) {
        evalCounters = new LinkedHashMap<>();
        for (String type : types) {
            if (!evalCounters.containsKey(total))
                evalCounters.put(total, new EvalCounter());

            if (!evalCounters.containsKey(type))
                evalCounters.put(type, new EvalCounter());
            EvalCounter totalCounter = evalCounters.get(total);
            EvalCounter evalCounter = evalCounters.get(type);
            if (annotations1.containsKey(type)) {
                HashMap<String, ArrayList<RecordRow>> annoPerType = annotations1.get(type);
                for (Map.Entry<String, ArrayList<RecordRow>> annoPerFile : annoPerType.entrySet()) {
                    String fileName = annoPerFile.getKey();
//                            System.out.println("Compare annotations for file: " + fileName);
                    HashMap<String, Object> res = new HashMap<>();
                    if (type.toLowerCase().endsWith("_doc") || type.toLowerCase().indexOf("_doc:")!=-1) {
                        docCompare(evalCounter, totalCounter, type, fileName, annotations2);
                    } else if (annotations2.containsKey(type) && annotations2.get(type).containsKey(fileName)) {
                        if (strictCompare)
                            strictCompare(evalCounter, totalCounter, annoPerFile.getValue(), annotations2.get(type).get(fileName));
                        else
                            relaxCompare(evalCounter, totalCounter, annoPerFile.getValue(), annotations2.get(type).get(fileName));
                        annotations2.get(type).remove(fileName);
                    } else {
                        evalCounter.fp += annoPerFile.getValue().size();
                        totalCounter.fps.addAll(annoPerFile.getValue());
                    }
                }
//                      if the same category annotations2 have additional files than annotations1
                if (annotations2.containsKey(type))
                    for (Map.Entry<String, ArrayList<RecordRow>> annoPerFile : annotations2.get(type).entrySet()) {
                        evalCounter.fn += annoPerFile.getValue().size();
                        totalCounter.fns.addAll(annoPerFile.getValue());
                    }
            } else if (annotations2.containsKey(type)) {
                for (Map.Entry<String, ArrayList<RecordRow>> annoPerFile : annotations2.get(type).entrySet()) {
                    evalCounter.fn += annoPerFile.getValue().size();
                    totalCounter.fns.addAll(annoPerFile.getValue());
                }
            }

            totalCounter.addTp(evalCounter.tp);
            totalCounter.addFp(evalCounter.fp);
            totalCounter.addFn(evalCounter.fn);
        }
        return evalCounters;
    }

    private void docCompare(EvalCounter evalCounter, EvalCounter totalCounter, String type,
                            String fileName, HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations2) {
        if (annotations2.containsKey(type)) {
            if (annotations2.get(type).containsKey(fileName)) {
                evalCounter.addTp();
                totalCounter.addTp();
                annotations2.get(type).remove(fileName);
            }else{
                evalCounter.addFp();
                totalCounter.addFp();
            }
        } else {
            evalCounter.addFp();
            totalCounter.addFp();
        }
    }


    public void strictCompare(EvalCounter evalCounter, EvalCounter totalCounter, Collection<RecordRow> inputAnnos, Collection<RecordRow> goldAnnos) {
        TreeMap<Integer, RecordRow> sortedAnnos1 = sortOnAbsoluteBegin(inputAnnos);
        TreeMap<Integer, RecordRow> sortedAnnos2 = sortOnAbsoluteBegin(goldAnnos);
        for (RecordRow anno : sortedAnnos1.values()) {
            int begin = (int) anno.getValueByColumnName("ABEGIN");
            int end = (int) anno.getValueByColumnName("AEND");
            if (sortedAnnos2.containsKey(begin)) {
                if ((int) sortedAnnos2.get(begin).getValueByColumnName("AEND") == end) {
                    evalCounter.addTp();
                    totalCounter.addTp();
                    sortedAnnos2.remove(begin);
                } else {
                    evalCounter.addFp();
                    totalCounter.addFp();
                    evalCounter.fps.add(anno);
                    totalCounter.fps.add(anno);
                }
            } else {
                evalCounter.addFp();
                totalCounter.addFp();
                evalCounter.fps.add(anno);
                totalCounter.fps.add(anno);
            }
        }
        evalCounter.addFn(sortedAnnos2.size());
        totalCounter.addFn(sortedAnnos2.size());
        evalCounter.fns.addAll(sortedAnnos2.values());
        totalCounter.fns.addAll(sortedAnnos2.values());
    }


    public void relaxCompare(EvalCounter evalCounter, EvalCounter totalCounter, ArrayList<RecordRow> inputAnnos, ArrayList<RecordRow> goldAnnos) {
        IntervalST<Integer> inputAnnoTree = buildAnnoTree(inputAnnos);
        IntervalST<Integer> goldAnnoTree = buildAnnoTree(goldAnnos);
        HashSet<Integer> matchedGoldAnnoIds = new HashSet<>();
        for (Integer annoId : inputAnnoTree.getAll(new Interval1D(0, 1000000))) {
            RecordRow anno = inputAnnos.get(annoId);
            int begin = (int) anno.getValueByColumnName("ABEGIN");
            int end = (int) anno.getValueByColumnName("AEND");
            Interval1D inputInterval = new Interval1D(begin, end);
            LinkedList<Integer> thisMatches = (LinkedList) goldAnnoTree.getAll(inputInterval);
//            TODO check if the default value is null
            if (thisMatches != null && thisMatches.size() != 0) {
                evalCounter.addTp(thisMatches.size());
                totalCounter.addTp(thisMatches.size());
                matchedGoldAnnoIds.addAll(thisMatches);
            } else {
//              if the inputInterval is ready included in the beMatchedGoldAnnos, means multiple input annotations matched one gold annotation,
//               doesn't consider this inputInterval as false positive
                evalCounter.fps.add(anno);
                evalCounter.addFp();
                totalCounter.fps.add(anno);
                totalCounter.addFp();
            }
//            else {
//                System.out.println(thisMatches);
//            }
        }

        for (int i = 0; i < goldAnnos.size(); i++) {
            if (!matchedGoldAnnoIds.contains(i)) {
                RecordRow goldAnnoLeftUnmatched = goldAnnos.get(i);
                evalCounter.addFn();
                evalCounter.fns.add(goldAnnoLeftUnmatched);
                totalCounter.addFn();
                totalCounter.fns.add(goldAnnoLeftUnmatched);
            }
        }
    }


    public IntervalST<Integer> buildAnnoTree(ArrayList<RecordRow> annos) {
        IntervalST<Integer> annoTree = new IntervalST<>();
        if (annos == null)
            return annoTree;
        for (int i = 0; i < annos.size(); i++) {
            RecordRow anno = annos.get(i);
//            System.out.println(anno.getTYPE());
            int begin = (int) anno.getValueByColumnName("ABEGIN");
            int end = (int) anno.getValueByColumnName("AEND");
            Integer duplicates = annoTree.get(new Interval1D(begin, end));
            if (duplicates != null) {
//                System.out.println(duplicates);
            }
            annoTree.put(new Interval1D(begin, end), i);
        }
        return annoTree;
    }

    protected TreeMap<Integer, RecordRow> sortOnAbsoluteBegin(Collection<RecordRow> annotations) {
        TreeMap<Integer, RecordRow> sortedAnnos = new TreeMap<>();
        for (RecordRow anno : annotations) {
            int begin = (int) anno.getValueByColumnName("ABEGIN");
            int end = (int) anno.getValueByColumnName("AEND");
            if (sortedAnnos.containsKey(begin)) {
                if (end > (int) sortedAnnos.get(begin).getValueByColumnName("AEND"))
                    sortedAnnos.put(begin, anno);
            } else
                sortedAnnos.put(begin, anno);
        }
        return sortedAnnos;
    }

    public void readAnnotations(EDAO dao, HashMap<String, LinkedHashMap<String, ArrayList<RecordRow>>> annotations,
                                String annotator, String annotatorTable, String typeFilter, String runId) {

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
            runId = getMaxRunId(dao1, annotatorTable, annotator);
        }
        if (runId != null && runId.length() > 0) {
            conditions.add("RUN_ID='" + runId + "'");
        }
        if (annotator.length() > 0) {
            conditions.add("annotator='" + annotator + "'");
        }
        RecordRowIterator recordIterator = queryRecords(dao, annotatorTable, Arrays.copyOf(conditions.toArray(), conditions.size(), String[].class));
        while (recordIterator.hasNext()) {
            RecordRow record = recordIterator.next();
            String type = record.getValueByColumnName("TYPE") + "";
            int snippetBegin = getIntegerValue(record.getValueByColumnName("SNIPPET_BEGIN"));

            int begin = getIntegerValue(record.getValueByColumnName("BEGIN")) + snippetBegin;
            record.addCell("ABEGIN", begin);
            int end = getIntegerValue(record.getValueByColumnName("END")) + snippetBegin;
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
        }
    }

    public static int getIntegerValue(Object value) {
        int intValue = 0;
        if (value instanceof Integer)
            intValue = (int) value;
        else if (value instanceof Long)
            intValue = ((Long) value).intValue();
        else
            try {
                throw new Exception(value + "(type: " + value.getClass() + ")" + " cannot be converted to Integer.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        return intValue;
    }

    public RecordRowIterator queryRecords(EDAO dao, String tableName, String[] conditions) {
        StringBuilder sql = new StringBuilder();
        sql.append(dao.queries.get("queryAnnos").replaceAll("\\{tableName}", tableName));
//        sql.append("SELECT * FROM ");
//        sql.append(tableName);
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
        sql.append(" ORDER BY DOC_NAME ");
//        sql.append(";");

        RecordRowIterator recordIterator = dao.queryRecords(sql.toString());
        return recordIterator;
    }


}
