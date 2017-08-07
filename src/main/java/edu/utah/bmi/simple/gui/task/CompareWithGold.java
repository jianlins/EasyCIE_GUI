package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.core.Interval1D;
import edu.utah.bmi.core.IntervalST;
import edu.utah.bmi.runner.XMISQLCompareFunctions;
import edu.utah.bmi.sql.DAO;
import edu.utah.bmi.sql.DAOFactory;
import edu.utah.bmi.sql.Record;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Compare the annotations from two annotators, report the fp and fn difference against gold standard:
 * What is the new FP and new FN introduced by the 1st annotator compared with the 2nd annotator.
 * (based on SQLite, differentiate different annotators by "annotator")
 *
 * @author Jianlin Shi
 *         Created on 7/18/16.
 */
public class CompareWithGold extends XMISQLCompareFunctions {

    protected DAO daog;


    public static void main(String[] args) throws IOException {
        if (args.length < 9) {
            printInstructions();
            System.exit(0);
        } else {
            String sqlFile1 = args[0];
            String annotator1Table = args[1];
            String annotator1 = args[2];

            String sqlFile2 = args[3];
            String annotator2Table = args[4];
            String annotator2 = args[5];

            String goldSQLFile = args[6];
            String annotatorGoldTable = args[7];
            String annotatorgold = args[8];

            String outputTable = "";
            String typeFilter = "";
            String categoryFilter = "";
            if (args.length > 9) {
                outputTable = args[9];
            }
            if (args.length > 10)
                typeFilter = args[10];
            if (args.length > 11)
                categoryFilter = args[11];
            CompareWithGold comparator = new CompareWithGold();
            comparator.run(annotator1, annotator1Table, sqlFile1,
                    annotator2, annotator2Table, sqlFile2,
                    annotatorgold, annotatorGoldTable, goldSQLFile, outputTable, typeFilter, categoryFilter);
        }
    }

    public static void printInstructions() {
        System.out.println("You need at lease 9 parameters.\n" +
                "1: the SQL file of annotations(point to the sql file if using SQLite, or the mysql configuration file if using MySQL)\n" +
                "2: the table of annotator1's annotations\n" +
                "3: the name of annotator1\n" +

                "4: the SQL file of annotations(point to the sql file if using SQLite, or the mysql configuration file if using MySQL)\n" +
                "5: the table of annotator2's annotations\n" +
                "6: the name of annotator2\n" +

                "7: the SQL file of gold annotations(point to the sql file if using SQLite, or the mysql configuration file if using MySQL)\n" +
                "8: the table of gold annotations\n" +
                "9: the name of gold annotator\n" +

                "10(optional): output table name to save the difference. If don't provide, only scores will be reported.\n" +
                "11(optional): The concept type to compare (optional). If not defined, will compare all the concept types\n" +
                "12(optional): The category (subType) of concepts to compare(optional).  If not defined, will compare all the categories\n" +
                "For example:\n" +
                "java -cp prannotator.jar edu.utah.bmi.runner.CompareWithGold anno1 output data/myoutput.sql anno2 output data/myoutput.sql annogold goldTable data/gold.sql");

    }


    public void run(String annotator1, String annotator1Table, String sqlFile1,
                    String annotator2, String annotator2Table, String sqlFile2,
                    String annotatorgold, String annotatorGoldTable, String goldSQLFile,
                    String outputTable, String typeFilter, String categoryFilter) {
        differenceTable = outputTable;
        dao1 = DAOFactory.getDAO(new File(sqlFile1));
        dao2 = DAOFactory.getDAO(new File(sqlFile2));
        daog = DAOFactory.getDAO(new File(goldSQLFile));
        dao1.batchsize = 1000;
        HashMap<String, Object> ress = evalAnnotators(annotator1, annotator1Table, annotator2, annotator2Table, annotatorgold, annotatorGoldTable, typeFilter, categoryFilter);
        computeF(ress, true, "");
        reportChildFs(ress, true, "\t", 0);
        if (outputTable.length() > 0) {
            dao1.initiateTable(outputTable, false);
            logDiff(ress, annotator1, annotator2);
        }
    }

    protected HashMap<String, Object> evalAnnotators(String annotator1, String annotator1Table,
                                                     String annotator2, String annotator2Table,
                                                     String annotatorgold, String annotatorGoldTable,
                                                     String typeFilter, String categoryFilter) {
        HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations1 = new HashMap<>();
        HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations2 = new HashMap<>();
        HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotationsGold = new HashMap<>();
        readAnnotations(dao1, annotations1, annotator1, annotator1Table, typeFilter, categoryFilter);
        readAnnotations(dao2, annotations2, annotator2, annotator2Table, typeFilter, categoryFilter);
        dao2.close();
        readAnnotations(daog, annotationsGold, annotatorgold, annotatorGoldTable, typeFilter, categoryFilter);
        daog.close();
        System.out.println("Read annotation finished. Start comparison");
        HashMap<String, Object> ress = compare(annotations1, annotations2, annotationsGold);
        return ress;
    }


    protected HashMap<String, Object> compare(HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations1,
                                              HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations2,
                                              HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotationsGold) {
        HashMap<String, Object> ress = new HashMap<>();
        int tp = 0, fp = 0, fn = 0;
        for (String type : types) {
            if (annotations1.containsKey(type)) {
                HashMap<String, HashMap<String, ArrayList<Record>>> annoPerType = annotations1.get(type);
                for (String category : categories) {
                    if (annoPerType.containsKey(category)) {
//                        System.out.println("Compare category: " + category);
                        for (Map.Entry<String, ArrayList<Record>> annoPerFile : annoPerType.get(category).entrySet()) {
                            String fileName = annoPerFile.getKey();
//                            System.out.println("Compare annotations for file: " + fileName);
                            HashMap<String, Object> res = new HashMap<>();
                            if (annotations2.containsKey(type) && annotations2.get(type).containsKey(category) && annotations2.get(type).get(category).containsKey(fileName))
                                res = compare(annoPerFile.getValue(), annotations2.get(type).get(category).get(fileName));
                            else {
                                res = createRes(0, annoPerFile.getValue().size(), 0, annoPerFile.getValue(), new ArrayList<Record>());
                            }
                            //                  filter against gold standards
                            if (annotationsGold.get(type).containsKey(category) && annotationsGold.get(type).get(category).containsKey(fileName)) {
                                filterAgainstGold(res, annotationsGold.get(type).get(category).get(fileName));
                            } else {
                                res = createRes(0, annoPerFile.getValue().size(), 0, annoPerFile.getValue(), new ArrayList<Record>());
                            }
                            updateResults(ress, res, type, category, fileName);
                        }
                    } else if (annotations2.containsKey(type) && annotations2.get(type).containsKey(category)) {
                        for (Map.Entry<String, ArrayList<Record>> annoPerFile : annotations2.get(type).get(category).entrySet()) {
                            HashMap<String, Object> res = createRes(0, 0, annoPerFile.getValue().size(), new ArrayList<Record>(), annoPerFile.getValue());
                            updateResults(ress, res, type, category, annoPerFile.getKey());
                        }
                    }
                }
            } else if (annotations2.containsKey(type)) {
                HashMap<String, HashMap<String, ArrayList<Record>>> annoPerType2 = annotations2.get(type);
                for (Map.Entry<String, HashMap<String, ArrayList<Record>>> annoPerCate : annoPerType2.entrySet()) {
                    String category = annoPerCate.getKey();
                    for (Map.Entry<String, ArrayList<Record>> annoPerFile : annoPerCate.getValue().entrySet()) {
                        HashMap<String, Object> res = createRes(0, 0, annoPerFile.getValue().size(), new ArrayList<Record>(), annoPerFile.getValue());
                        updateResults(ress, res, type, category, annoPerFile.getKey());
                    }
                }
            }

        }
        return ress;
    }

//    protected HashMap<String, Object> compare(HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations1,
//                                              HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations2,
//                                              HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotationsGold) {
//        HashMap<String, Object> ress = new HashMap<>();
//        int tp = 0, fp = 0, fn = 0;
//        for (Map.Entry<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annoPerCats : annotations1.entrySet()) {
//            String category = annoPerCats.getKey();
//            HashMap<String, HashMap<String, ArrayList<Record>>> annoPerCat = annoPerCats.getValue();
//
//            for (Map.Entry<String, HashMap<String, ArrayList<Record>>> annoPerSubs : annoPerCat.entrySet()) {
//                String subCategory = annoPerSubs.getKey();
//                System.out.println("Compare category: " + subCategory);
////              save the file names that have been compared, so that some annotations may only exist in gold standard can be traced.
//                HashSet<String> comparedFiles = new HashSet<>();
//                HashMap<String, ArrayList<Record>> annoPerFiles = annoPerSubs.getValue();
//                for (Map.Entry<String, ArrayList<Record>> annoPerFile : annoPerFiles.entrySet()) {
//                    String fileName = annoPerFile.getKey();
//                    comparedFiles.add(fileName);
//                    System.out.println("Compare annotations for file: " + fileName);
//                    HashMap<String, Object> res = new HashMap<>();
//                    if (annotations2.get(category).containsKey(subCategory) && annotations2.get(category).get(subCategory).containsKey(fileName))
//                        res = compare(annoPerFile.getValue(), annotations2.get(category).get(subCategory).get(fileName));
//                    else {
//                        res.put("--tp", 0);
//                        res.put("--fp", annoPerFile.getValue().size());
//                        res.put("--fn", 0);
//                        res.put("--fpannos", annoPerFile.getValue());
//                        res.put("--fnannos", new ArrayList<Record>());
//                    }
////                  filter against gold standards
//                    if (annotationsGold.get(category).containsKey(subCategory) && annotationsGold.get(category).get(subCategory).containsKey(fileName)) {
//                        filterAgainstGold(res, annotationsGold.get(category).get(subCategory).get(fileName));
//                    } else {
//                        res.put("--tp", 0);
//                        res.put("--fp", annoPerFile.getValue().size());
//                        res.put("--fn", 0);
//                        res.put("--fpannos", annoPerFile.getValue());
//                        res.put("--fnannos", new ArrayList<Record>());
//                    }
//
//                    updateResults(ress, res, category, subCategory, fileName);
//                }
////              trace the missed files
//                if (annotations2.get(category).containsKey(subCategory))
//                    for (Map.Entry<String, ArrayList<Record>> annoPerFile : annotations2.get(category).get(subCategory).entrySet()) {
//                        if (!comparedFiles.contains(annoPerFile.getKey())) {
//                            HashMap<String, Object> res = new HashMap<>();
//                            res.put("--tp", 0);
//                            res.put("--fp", 0);
//                            res.put("--fn", annoPerFile.getValue().size());
//                            res.put("--fpannos", new ArrayList<Record>());
//                            res.put("--fnannos", annoPerFile.getValue());
//                            updateResults(ress, res, category, subCategory, annoPerFile.getKey());
//                        }
//                    }
//
//
//            }
//
//
//        }
//        return ress;
//
//    }

    protected void filterAgainstGold(HashMap<String, Object> res, ArrayList<Record> spen) {
        IntervalST<Record> goldanno = buildAnnoTree(spen);
        ArrayList<Record> fp = (ArrayList<Record>) res.get("--fpannos");
        ListIterator<Record> fpIter = fp.listIterator();
        while (fpIter.hasNext()) {
            Record currentRecord = fpIter.next();
            if (goldanno.search(new Interval1D(currentRecord.begin, currentRecord.end)) != null) {
                fpIter.remove();
            }
        }
        res.put("--fpannos", fp);
        res.put("--fp", fp.size());
        ArrayList<Record> fn = (ArrayList<Record>) res.get("--fnannos");
        ListIterator<Record> fnIter = fn.listIterator();
        while (fnIter.hasNext()) {
            Record currentRecord = fnIter.next();
            if (goldanno.search(new Interval1D(currentRecord.begin, currentRecord.end)) == null) {
                fnIter.remove();
            }
        }
        res.put("--fnannos", fn);
        res.put("--fn", fn.size());
    }


    public HashMap compare(Collection<Record> inputAnnos, Collection<Record> goldAnnos) {
        return compare(buildAnnoTree(inputAnnos), buildAnnoTree(goldAnnos));
    }


    public HashMap compare(IntervalST<Record> inputAnnos, IntervalST<Record> Annos2) {
        IntervalST<Record> beMatchedGoldAnnos = new IntervalST<>();
        HashMap<String, Object> res = new HashMap();
        int tp = 0, fp = 0, fn = 0;
        ArrayList<Record> fpAnnos = new ArrayList<>();
        ArrayList<Record> fnAnnos = new ArrayList<>();
        for (Record anno : inputAnnos.getAll(new Interval1D(0, 1000000))) {
            Interval1D inputInterval = new Interval1D(anno.begin, anno.end);
            LinkedList<Record> thisMatches = (LinkedList) Annos2.getAll(inputInterval);
//            TODO check if the default value is null
            if (thisMatches != null && thisMatches.size() != 0) {
                for (Record matchedAnno : thisMatches) {
                    tp++;
                    Interval1D matchedInterval = new Interval1D(matchedAnno.begin, matchedAnno.end);
                    beMatchedGoldAnnos.put(matchedInterval, matchedAnno);
                    Annos2.remove(matchedInterval);
                }
            } else if (beMatchedGoldAnnos.get(inputInterval) == null) {
//              if the inputInterval is ready included in the beMatchedGoldAnnos, means multiple input annotations matched one gold annotation,
//               doesn't consider this inputInterval as false positive
                fpAnnos.add(anno);
                fp++;
            } else {
                System.out.println(thisMatches);
            }
        }

        for (Record goldAnnoLeftUnmatched : Annos2.getAll(new Interval1D(0, 1000000))) {
            fn++;
            fnAnnos.add(goldAnnoLeftUnmatched);
        }
        res.put("--tp", tp);
        res.put("--fp", fp);
        res.put("--fn", fn);
        res.put("--fpannos", fpAnnos);
        res.put("--fnannos", fnAnnos);
        return res;
    }


}
