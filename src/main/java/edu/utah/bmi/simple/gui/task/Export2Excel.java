package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.core.ExcelExporter;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class Export2Excel extends GUITask {
    protected String outputDB, outputTable, annotator, sampleOnColumn;
    private File exportDir;
    private int sampleSize = 0;
    private HashSet<String> mentionTypes;

    protected Export2Excel() {

    }

    public Export2Excel(TasksFX tasks) {
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks, String... paras) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("settings");
        outputDB = config.getValue(ConfigKeys.writeConfigFileName);
        outputTable = config.getValue(ConfigKeys.outputTableName);
        config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);

        config = tasks.getTask("export");
        String configString = config.getValue(ConfigKeys.excelDir);
        exportDir = new File(configString);
        if (!exportDir.exists()) {
            try {
                FileUtils.forceMkdir(exportDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        configString = config.getValue(ConfigKeys.sampleSize).trim();
        if (configString.length() > 0) {
            if (NumberUtils.isNumber(configString)) {
                sampleSize = NumberUtils.toInt(configString);
            }
        }

        configString = config.getValue(ConfigKeys.mentionTypes).trim();
        if (configString.length() > 0) {
            mentionTypes = new HashSet<>();
            mentionTypes.addAll(Arrays.asList(configString.split("\\s*,\\s*")));
        }


        sampleOnColumn = config.getValue(ConfigKeys.sampleOnColumn).trim();
        if (sampleOnColumn.length() == 0)
            sampleOnColumn = "DOC_NAME";


    }


    @Override
    protected Object call() throws Exception {
        GUITask task = this;

        if (!new File(outputDB).exists()) {
            updateMessage("Database " + outputDB + " not exist");
            return null;
        }
        // Update UI here.
        boolean res = false;
        if (annotator.trim().length() > 0) {
            DAO dao = new DAO(new File(outputDB));
            if (!dao.checkExists(outputTable)) {
                updateMessage("Table '" + outputTable + "' does not exit.");
                popDialog("Note", "Table '" + outputTable + "' does not exit.",
                        " You need to execute 'RunEasyCIE' first.");
                updateProgress(0, 0);
                return null;
            }
            String filter = "";
            int annotatorLastRunid = getLastRunIdofAnnotator(dao, outputTable, annotator);
            int lastLogRunId = getLastLogRunId(dao, annotator);
            if (annotatorLastRunid == -1) {
                popDialog("Note", "There is no output in the previous runs of the annotator: \"" + annotator + "\"",
                        "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                "if the dataset has been imported successfully.\n" +
                                "EasyCIE will display all the previous outputs if there is any.");
            } else if (annotatorLastRunid != lastLogRunId) {
                popDialog("Note", "There is no output in the most recent run of annotator:\"" + annotator + "\"," +
                                " which RUN_ID=" + lastLogRunId,
                        "Please check the pipeline configuration to see if the rules are configured correctly, and " +
                                "if the dataset has been imported successfully.\n" +
                                "Instead, EasyCIE Will display the last run of annotator \"" + annotator + "\" that has some output, which " +
                                "RUN_ID=" + annotatorLastRunid);
                filter = " WHERE annotator='" + annotator + "' AND RUN_ID=" + annotatorLastRunid + " ";
            } else {
                filter = " WHERE annotator='" + annotator + "' AND RUN_ID=" + annotatorLastRunid + " ";
            }
            String sql = "SELECT COUNT(DISTINCT " + sampleOnColumn + ") FROM " +
                    dao.databaseName + "." + outputTable + " OU " + filter;
            RecordRow recordRow = dao.queryRecord(sql);
            int total = Integer.parseInt(recordRow.getValueByColumnId(1) + "");
//                    TODO extend later
            if (sampleSize > 0 && total > sampleSize) {
                filter = " JOIN\n" +
                        "(SELECT DISTINCT " + sampleOnColumn + " from `" + outputTable + "` " + filter + " order by RAND() LIMIT " + sampleSize + ") DOCLIST" +
                        " ON " + outputTable + ".DOC_NAME=DOCLIST.DOC_NAME " + filter;
                total = sampleSize;
            }
            sql = dao.queries.get("exportExcel").replaceAll("\\{tableName\\}", outputTable) + filter;
            File excel = new File(exportDir, "exported_" + annotator + "_" + annotatorLastRunid
                    + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(new Date()) + ".xlsx");
            ExcelExporter excelExporter = new ExcelExporter(dao, sql, total, task);
            excelExporter.export(excel);

        }
        updateProgress(1, 1);
        return null;
    }

    public int getLastRunIdofAnnotator(DAO dao, String outputTable, String annotator) {
        int id = -1;
        RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("maxRunIDofAnnotator", outputTable, annotator);
        if (recordRowIter.hasNext()) {
            RecordRow recordRow = recordRowIter.next();
            if (recordRow != null && recordRow.getValueByColumnId(1) != null)
                id = (int) recordRow.getValueByColumnId(1);

        }
        return id;
    }

    public int getLastLogRunId(DAO dao, String annotator) {
        int id = -1;
        RecordRowIterator recordRowIter = dao.queryRecordsFromPstmt("lastLogRunID", annotator);
        if (recordRowIter.hasNext()) {
            RecordRow recordRow = recordRowIter.next();
            if (recordRow != null)
                id = (int) recordRow.getValueByColumnId(1);
        }
        return id;
    }


}
