package edu.utah.bmi.nlp.uima.loggers;


import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.task.RunCPEDescriptorTask;
import edu.utah.bmi.simple.gui.task.ViewOutputDB;
import javafx.application.Platform;

import javax.swing.*;
import java.io.File;
import java.util.Date;
import java.util.logging.Logger;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public class NLPDBLogger extends GUILogger {
    public static Logger classLogger = IOUtil.getLogger(NLPDBLogger.class);
    protected EDAO ldao;
    protected String tableName;
    protected String keyColumnName;
    protected String dbConfigureFile;
    protected int maxCommentLength = -1;


    protected Object runid;
    protected RecordRow recordRow, initRecordRow;

    protected NLPDBLogger() {
        ldao = null;
        tableName = "";
        keyColumnName = "";

    }

    public NLPDBLogger(String dbConfigureFile, String annotator) {
        this.dbConfigureFile = dbConfigureFile;
        this.ldao = EDAO.getInstance(new File(dbConfigureFile), true, false);
        this.tableName = "LOG";
        this.initRecordRow = new RecordRow().addCell("ANNOTATOR", annotator);
        this.keyColumnName = "RUN_ID";
        recordRow = new RecordRow();
    }

    public NLPDBLogger(String dbConfigureFile, String tableName, String keyColumnName, String annotator) {
        this.dbConfigureFile = dbConfigureFile;
        this.ldao = EDAO.getInstance(new File(dbConfigureFile), true, false);
        this.tableName = tableName;
        this.initRecordRow = new RecordRow().addCell("ANNOTATOR", annotator);
        this.keyColumnName = keyColumnName;
        recordRow = new RecordRow();
    }

    public NLPDBLogger(String dbConfigureFile, String tableName, String keyColumnName, String annotator, int maxCommentLength) {
        this.dbConfigureFile = dbConfigureFile;
        this.ldao = EDAO.getInstance(new File(dbConfigureFile), true, false);
        this.tableName = tableName;
        this.initRecordRow = new RecordRow().addCell("ANNOTATOR", annotator);
        this.keyColumnName = keyColumnName;
        recordRow = new RecordRow();
        this.maxCommentLength = maxCommentLength;
    }


    public NLPDBLogger(String dbConfigureFile, String tableName, String keyColumnName, int maxCommentLength, Object... initials) {
        this.dbConfigureFile = dbConfigureFile;
        this.ldao = EDAO.getInstance(new File(dbConfigureFile), true, false);
        this.tableName = tableName;
        this.keyColumnName = keyColumnName;
        recordRow = new RecordRow();
        this.maxCommentLength = maxCommentLength;
        initRecordRow = new RecordRow();
        for (int i = 0; i < initials.length - 1; i += 2) {
            String name = (String) initials[i];
            Object value = initials[i + 1];
            initRecordRow.addCell(name, value);
        }
    }

    public NLPDBLogger(String dbConfigureFile, String tableName, String keyColumnName, int maxCommentLength, RecordRow initialRecordRow) {
        this.dbConfigureFile = dbConfigureFile;
        this.ldao = EDAO.getInstance(new File(dbConfigureFile), true, false);
        this.tableName = tableName;
        this.keyColumnName = keyColumnName;
        recordRow = new RecordRow();
        this.maxCommentLength = maxCommentLength;
        initRecordRow = initialRecordRow;
    }

    //Deprecated, because dao may be disconnected outside this class without known.
    @Deprecated
    public NLPDBLogger(EDAO dao, String tableName, String keyColumnName, String annotator) {
        this.ldao = dao;
        this.tableName = tableName;
        this.initRecordRow = new RecordRow().addCell("ANNOTATOR", annotator);
        this.keyColumnName = keyColumnName;
        this.dbConfigureFile = dao.getConfigFile().getAbsolutePath();
        recordRow = new RecordRow();
    }


    public void reset() {
        recordRow = initRecordRow.clone();
        startTime = 0;
        entityCount = 0;
        completeTime = 0;
        initCompleteTime = 0;
        size = 0;
        runid = null;
    }

    public void setItem(String key, Object value) {
        recordRow.addCell(key, value);
    }


    public Object getItemValue(String key) {
        return recordRow.getValueByColumnName(key);
    }


    public long getStartTime() {
        return startTime;
    }


    public long getCompleteTime() {
        return completeTime;
    }


    public void logStartTime() {
        recordRow = initRecordRow.clone();
        startTime = 0;
        entityCount = 0;
        completeTime = 0;
        initCompleteTime = 0;
        size = 0;
        ldao = EDAO.getInstance(new File(dbConfigureFile));
        if (runid == null || runid.equals(0)) {
            runid = ldao.insertRecord(tableName, recordRow);
            if (runid == null)
                runid = ldao.getLastId(tableName);
        }
        setItem("RUN_ID", runid);
        startTime = System.currentTimeMillis();
        setItem("START_DTM", new Date(startTime));
    }


    public void logCompleteTime() {
        completeTime = System.currentTimeMillis();
        setItem("END_DTM", new Date(completeTime));
    }

    public void logCompletToDB(int numOfNotes, String comments) {
        setItem("NUM_NOTES", numOfNotes);
        setItem("COMMENTS", comments);
//        recordRow.addCell("COMMENTS", comments);
        logCompleteTime();
    }

    public String logItems() {
        return recordRow.serialize();
    }

    public Object getRunid() {
        if (dbConfigureFile == null || !new File(dbConfigureFile).exists())
            return "-1";
        ldao = EDAO.getInstance(new File(dbConfigureFile));
        if (runid == null) {
            runid = ldao.insertRecord(tableName, recordRow);
            if (runid == null)
                runid = ldao.getLastId(tableName);
        }
        return runid;
    }

    @Override
    public String getItem(String key) {
        String value = recordRow.getValueByColumnName(key) + "";
        return value == null ? "" : value;
    }

    @Override
    public void logString(String msg) {
        if (task == null)
            classLogger.fine(msg);
        else {
            classLogger.fine(msg);
            task.updateGUIMessage(msg);
        }
    }

    @Override
    public String getUnit() {
        return unit;
    }

    @Override
    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void initializationComplete(int totalDocs) {
        logString("Initialization complete.");
        classLogger.fine("Total " + totalDocs + " documents to process.");
        this.initCompleteTime = System.currentTimeMillis();
        classLogger.fine(this.df.format(new Date()) + "\tCPM Initialization Complete");
        this.totaldocs = totalDocs;
    }

    @Override
    public void collectionProcessComplete(String reportContent) {
        logCompleteTime();
        classLogger.fine(this.df.format(completeTime) + "\tProcessing Complete");
        logString("Processing Complete.");
        long initTime = this.initCompleteTime - startTime;
        long processingTime = completeTime - initCompleteTime;
        long elapsedTime = initTime + processingTime;
        StringBuilder report = new StringBuilder();
        report.append(this.entityCount + " notes\n");
        if (this.size > 0L) {
            report.append(this.size + " characters\n");
        }

        report.append("Total:\t" + elapsedTime + " ms\n");
        report.append("Initialization:\t" + initTime + " ms\n");
        report.append("Processing:\t" + processingTime + " ms\n");
        report.append(reportContent);
        setItem("NUM_NOTES", this.entityCount);
        String comments;
        if (this.maxCommentLength > 0 && report.length() > this.maxCommentLength) {
            comments = report.substring(0, this.maxCommentLength);
        } else {
            comments = report.toString();
        }
        if (maxCommentLength > 0 && comments.length() > maxCommentLength)
            comments = comments.substring(0, maxCommentLength);
        setItem("COMMENTS", comments);

        ldao.updateRecord(tableName, recordRow);
        ldao.close();

//		AdaptableCPEDescriptorRunner.getInstance("desc/cpe/smoke_cpe.xml").getmCPE().stop();
        if (task != null && task.guiEnabled) {
            String showComment = comments;
            Platform.runLater(() -> {
                TasksOverviewController tasksOverviewController = TasksOverviewController.currentTasksOverviewController;
                new ViewOutputDB(tasksOverviewController.mainApp.tasks, recordRow.getStrByColumnName("ANNOTATOR")).run();

                task.updateGUIProgress(1, 1);
                if (reportable())
                    task.popDialog("Done", "Data process compelete", showComment);

            });
            if (enableUIMAViewer)
                SwingUtilities.invokeLater(() -> {
                    JFrame frame = new MyAnnotationViewerPlain(new String[]{"Pipeline Debug Viewer", inputPath, descriptorPath + ".xml"});
                    frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    frame.pack();
                    frame.setVisible(true);
                });
        }
        if (RunCPEDescriptorTask.button != null)
            RunCPEDescriptorTask.button.setDisable(false);
        reset();
    }


}
