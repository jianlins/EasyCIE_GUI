package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.runner.XMISQLCompareFunctions;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.sql.DAO;
import edu.utah.bmi.sql.DAOFactory;
import edu.utah.bmi.sql.Record;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Compare annotations against gold standard (based on SQLite, differentiate different annotators by "annotator")
 *
 * @author Jianlin Shi
 *         Created on 7/18/16.
 */
public class Compare extends javafx.concurrent.Task {
    protected XMISQLCompareFunctions compareFunctions;
    protected HashSet<String> types = new HashSet();
    protected HashSet<String> categories = new HashSet();
    private String diffTable, outputTable, annotatorCompare, annotatorAgainst, typeFilter, categoryFilter;
    private DAO dao;
    private boolean strictCompare = false;


    public Compare(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("compare");

        annotatorCompare = config.getValue(ConfigKeys.annotatorCompare);
        annotatorAgainst = config.getValue(ConfigKeys.annotatorAgainst);
        typeFilter = config.getValue(ConfigKeys.typeFilter).trim();
        categoryFilter = config.getValue(ConfigKeys.categoryFilter).trim();
        String compareMethodString = config.getValue(ConfigKeys.strictCompare).trim().toLowerCase();
        strictCompare = compareMethodString.startsWith("t") || compareMethodString.startsWith("1");

        config = tasks.getTask("settings");
        String outputDB = config.getValue(ConfigKeys.outputDBFile);
        outputTable = config.getValue(ConfigKeys.outputDBTable);
        diffTable = config.getValue(ConfigKeys.compareTable).trim();


        dao = DAOFactory.getDAO(new File(outputDB));
        if (diffTable.length() > 0) {
            dao.initiateTable(diffTable, false);
        }
        compareFunctions = new XMISQLCompareFunctions(dao, dao, types, categories, diffTable);
        compareFunctions.setCompareMethod(strictCompare);
    }

    @Override
    protected Object call() throws Exception {
        HashMap<String, Object> ress = evalAnnotators(annotatorCompare, outputTable, annotatorAgainst, outputTable, typeFilter, categoryFilter);
        StringBuilder report = new StringBuilder();
        report.append(compareFunctions.reportPerformance(ress, true, ""));
        report.append("\n");
        report.append(compareFunctions.reportChildFsString(ress, true, "\t", 0));
        if (diffTable.length() > 0) {
            compareFunctions.logDiff(ress, annotatorCompare, annotatorAgainst);
        }
        updateMessage("Performance Report|Here is the report of annotator: " + annotatorCompare + " against annotator: " + annotatorAgainst + "|" + report.toString() + "|Compare completed.");
        updateProgress(1, 1);
//        updateMessage("Compare complete.");
        return null;
    }


    private HashMap<String, Object> evalAnnotators(String annotator1, String annotator1Table, String annotator2, String annotator2Table, String typeFilter, String categoryFilter) {
        HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations1 = new HashMap<>();
        HashMap<String, HashMap<String, HashMap<String, ArrayList<Record>>>> annotations2 = new HashMap<>();
        compareFunctions.readAnnotations(dao, annotations1, annotator1, annotator1Table, typeFilter, categoryFilter);
        compareFunctions.readAnnotations(dao, annotations2, annotator2, annotator2Table, typeFilter, categoryFilter);
        System.out.println("Read annotation finished. Start comparison");
        HashMap<String, Object> ress = compareFunctions.compare(annotations1, annotations2);

        return ress;
    }


    public static void printInstructions() {
        String instr = "You need at lease 6 parameters.\n" +
                "1: the name of annotator1\n" +
                "2: the table of annotator1's annotations\n" +
                "3: the SQL file of annotations(point to the sql file if using SQLite, or the mysql configuration file if using MySQL)\n" +
                "4: the name of annotator2\n" +
                "5: the table of annotator2's annotations\n" +
                "6: the SQL file of annotations(point to the sql file if using SQLite, or the mysql configuration file if using MySQL)\n" +
                "7(optional): output table name to save the difference. If don't provide, only scores will be reported.\n" +
                "8(optional): The concept type to compare (optional). If not defined, will compare all the concept types\n" +
                "9(optional): The category (subType) of concepts to compare(optional).  If not defined, will compare all the categories\n" +
                "For example:\n" +
                "java -cp prannotator.jar edu.utah.bmi.runner.CompareWithGold anno1 output data/myoutput.sql anno2 output data/myoutput.sql annogold goldTable data/gold.sql";
        System.out.println(instr);
    }


}
