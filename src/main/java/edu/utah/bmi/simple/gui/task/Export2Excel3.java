package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.core.ExcelExporter3;
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
public class Export2Excel3 extends GUITask {
    protected String outputDB, annotator, snippetResultTable, documentResultTable, bunchResultTable, inputTable, viewQueryName;
    private File exportDir;
    private String sql, count;
    private int sampleSize = 0;
    private boolean exportTxtDocs = false;

    protected Export2Excel3() {

    }

    public Export2Excel3(TasksFX tasks) {
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks, String... paras) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }

        updateGUIMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("settings");
        outputDB = config.getValue(ConfigKeys.writeDBConfigFileName);
        inputTable = config.getValue(ConfigKeys.inputTableName);
        snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
        documentResultTable = config.getValue(ConfigKeys.docResultTableName);
        bunchResultTable = config.getValue(ConfigKeys.bunchResultTableName);

        config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);
        viewQueryName = config.getValue(ConfigKeys.viewQueryName);

        config = tasks.getTask("export");

        sql = config.getValue("excel/sql");
        String configString = config.getValue(ConfigKeys.exportTxtDocs);
        exportTxtDocs = configString.length() > 0 && configString.toLowerCase().charAt(0) == 't';


        String value = config.getValue("excel/sampleSize").trim();
        sampleSize = value.length() > 0 ? Integer.parseInt(value) : sampleSize;


//        count = config.getValue("excel/count");
        configString = config.getValue(ConfigKeys.excelDir);
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
        EDAO dao = EDAO.getInstance(new File(outputDB));
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", snippetResultTable, false);
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", documentResultTable, false);
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", bunchResultTable, false);
        String[] values = ViewOutputDB.buildQuery(dao, viewQueryName, annotator, snippetResultTable, documentResultTable, bunchResultTable, inputTable);
        String sourceQuery = values[0];
        String filter = values[1];
        String annotatorLastRunid = values[2];
        String lastLogRunId = values[3];
//
//        if (sql.toLowerCase().indexOf("where") == -1) {
//            sql += " WHERE RUN_ID=" + getLastRunIdofAnnotator(ldao, snippetResultTable, annotator);
//        }
        if (sql.trim().length() == 0) {
            sql = sourceQuery;
            if (filter.trim().length() > 0) {
                sql = sql + " WHERE ( " + filter + " ) ";
            }
        }
        sql=sql.replaceAll("\\n+"," ");
        String tmp = sql.toLowerCase();
        count = "SELECT COUNT(*) " + sql.substring(tmp.indexOf(" from "));


        RecordRow recordRow = dao.queryRecord(count);
        int total = Integer.parseInt(recordRow.getValueByColumnId(1) + "");

        if (sampleSize > 0 && total > sampleSize)
            total = sampleSize;

        File excel = new File(exportDir, "exported_" + annotator
                + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(new Date()) + ".xlsx");


        ExcelExporter3 excelExporter = new ExcelExporter3(dao, sql, total, task, exportTxtDocs, inputTable);
        excelExporter.export(excel);


        updateGUIProgress(1, 1);
        return null;
    }


}
