package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.core.ExcelExporter2;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class Export2Excel2 extends GUITask {
    protected String outputDB, annotator, outputTable;
    private File exportDir;
    private String sql, count;
    private int sampleSize = 0;

    protected Export2Excel2() {

    }

    public Export2Excel2(TasksFX tasks) {
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks, String... paras) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }

        updateGUIMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("settings");
        outputDB = config.getValue(ConfigKeys.writeDBConfigFileName);
        outputTable = config.getValue(ConfigKeys.snippetResultTableName);

        config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);

        config = tasks.getTask("export");
        String configString = config.getValue(ConfigKeys.excelDir);
        sql = config.getValue("excel/sql");
        String value = config.getValue("excel/sampleSize").trim();
        sampleSize = value.length() > 0 ? Integer.parseInt(value) : sampleSize;


//        count = config.getValue("excel/count");
        exportDir = new File(configString);
        if (!exportDir.exists()) {
            try {
                FileUtils.forceMkdir(exportDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    protected Object call() throws Exception {
        GUITask task = this;

        if (!new File(outputDB).exists()) {
            updateGUIMessage("Database " + outputDB + " not exist");
            return null;
        }
        // Update UI here.
        boolean res = false;
        DAO dao = new DAO(new File(outputDB));

        if (sql.toLowerCase().indexOf("where") == -1) {
            sql += " WHERE RUN_ID=" + getLastRunIdofAnnotator(dao, outputTable, annotator);
        }

        String tmp = sql.toLowerCase();
        count = "SELECT COUNT(*) " + sql.substring(tmp.indexOf(" from ")) + " AND TYPE LIKE '%_Doc'";




        RecordRow recordRow = dao.queryRecord(count);
        int total = Integer.parseInt(recordRow.getValueByColumnId(1) + "");

        if (sampleSize > 0 && total > sampleSize)
            total = sampleSize;

        File excel = new File(exportDir, "exported_" + annotator
                + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(new Date()) + ".xlsx");


        ExcelExporter2 excelExporter = new ExcelExporter2(dao, sql, total, task);
        excelExporter.export(excel);


        updateGUIProgress(1, 1);
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
