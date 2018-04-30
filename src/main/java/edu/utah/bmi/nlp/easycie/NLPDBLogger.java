package edu.utah.bmi.nlp.easycie;


import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.controller.GUILogger;

import java.util.Date;
import java.util.HashMap;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public class NLPDBLogger extends GUILogger {
    private final EDAO dao;
    private final String tableName;
    long starttime = 0, completetime = 0;
    private final String keyColumnName;
    private String annotator;


    private Object runid;
    private RecordRow recordRow;

    public NLPDBLogger(EDAO dao, String tableName, String keyColumnName, String annotator) {
        this.dao = dao;
        this.tableName = tableName;
        this.keyColumnName = keyColumnName;
        this.annotator = annotator;
//        runid = dao.getLastId(tableName, keyColumnName) + 1;
    }


    public void reset() {
        recordRow = new RecordRow();
        starttime = 0;
        completetime = 0;
    }

    public void setItem(String key, Object value) {
        recordRow.addCell(key, value);
    }


    public Object getItemValue(String key) {
        return recordRow.getValueByColumnName(key);
    }


    public long getStarttime() {
        return starttime;
    }


    public long getCompletetime() {
        return completetime;
    }


    public void logStartTime() {
        starttime = System.currentTimeMillis();
        recordRow = new RecordRow();
        setItem("ANNOTATOR", annotator);
        setItem("START_DTM", new Date(starttime));
        runid = dao.insertRecord(tableName, recordRow);
        if (runid == null)
            runid = dao.getLastId(tableName);
    }


    public void logCompleteTime() {
//      refresh recordRow in case the record has been updated somewhere else
//        recordRow = dao.queryRecord(logQuery + runid);
        completetime = System.currentTimeMillis();
        setItem("END_DTM", new Date(completetime));
        dao.updateRecord(tableName, recordRow);
        dao.close();
    }

    public void logCompletToDB(int numOfNotes, String comments) {
        logCompleteTime();
        recordRow.addCell("NUM_NOTES", numOfNotes);
//        recordRow.addCell("COMMENTS", comments);
        recordRow.addCell("RUN_ID", runid);
        dao.updateRecord("LOG", recordRow);
    }

    public String logItems() {
        HashMap<Integer, Object> idcells = recordRow.getId_cells();
        if (!idcells.get(idcells.size()).getClass().getSimpleName().equals("Integer") || idcells.get(idcells.size()) != runid) {
            setItem("RUN_ID", runid);
        }
        return recordRow.serialize();
    }

    public Object getRunid() {
        return runid;
    }

    @Override
    public String getItem(String key) {
        String value = recordRow.getValueByColumnName(key) + "";
        return value == null ? "" : value;
    }

    @Override
    public void logString(String msg) {
        System.out.println(msg);

    }

    @Override
    public String getUnit() {
        return unit;
    }

    @Override
    public void setUnit(String unit) {
        this.unit = unit;
    }


}
