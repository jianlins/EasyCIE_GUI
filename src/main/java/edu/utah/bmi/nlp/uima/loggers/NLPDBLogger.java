package edu.utah.bmi.nlp.uima.loggers;


import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorRunner;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.task.RunCPEDescriptorTask;
import edu.utah.bmi.simple.gui.task.ViewOutputDB;
import javafx.application.Platform;
import javafx.scene.input.MouseEvent;

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
    protected String annotator;
    protected String dbConfigureFile;


    protected Object runid;
    protected RecordRow recordRow;

    protected NLPDBLogger() {
        ldao = null;
        tableName = "";
        keyColumnName = "";

    }

    public NLPDBLogger(String dbConfigureFile, String annotator) {
        this.dbConfigureFile = dbConfigureFile;
        this.ldao = EDAO.getInstance(new File(dbConfigureFile), true, false);
        this.tableName = "LOG";
        this.annotator = annotator;
        this.keyColumnName = "RUN_ID";
        recordRow = new RecordRow();
//        runid = ldao.getLastId(tableName, keyColumnName) + 1;
    }

    public NLPDBLogger(String dbConfigureFile, String tableName, String keyColumnName, String annotator) {
        this.dbConfigureFile = dbConfigureFile;
        this.ldao = EDAO.getInstance(new File(dbConfigureFile), true, false);
        this.tableName = tableName;
        this.annotator = annotator;
        this.keyColumnName = keyColumnName;
        recordRow = new RecordRow();
//        runid = ldao.getLastId(tableName, keyColumnName) + 1;
    }

    /**
     * Deprecated, because dao may be disconnected outside this class without known.
     *
     * @param dao
     * @param tableName
     * @param keyColumnName
     * @param annotator
     */
    @Deprecated
    public NLPDBLogger(EDAO dao, String tableName, String keyColumnName, String annotator) {
        this.ldao = dao;
        this.tableName = tableName;
        this.annotator = annotator;
        this.keyColumnName = keyColumnName;
        this.dbConfigureFile = dao.getConfigFile().getAbsolutePath();
        recordRow = new RecordRow();
    }


    public void reset() {
        recordRow = new RecordRow();
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
        recordRow = new RecordRow();
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
        setItem("ANNOTATOR", annotator);
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
            setItem("RUN_ID", runid);
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
            classLogger.info(msg);
        else {
            classLogger.info(msg);
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
        classLogger.info("Total " + totalDocs + " documents to process.");
        this.initCompleteTime = System.currentTimeMillis();
        classLogger.info(this.df.format(new Date()) + "\tCPM Initialization Complete");
        this.totaldocs = totalDocs;
    }

    @Override
    public void collectionProcessComplete(String reportContent) {
        logCompleteTime();
        classLogger.info(this.df.format(completeTime) + "\tProcessing Complete");
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
        if (report.length() > this.maxCommentLength) {
            comments = report.substring(0, this.maxCommentLength);
        } else {
            comments = report.toString();
        }

        setItem("COMMENTS", comments);

        ldao.updateRecord(tableName, recordRow);
        ldao.close();

//		AdaptableCPEDescriptorRunner.getInstance("desc/cpe/smoke_cpe.xml").getmCPE().stop();
        if (task != null && task.guiEnabled) {

            Platform.runLater(() -> {
                TasksOverviewController tasksOverviewController = TasksOverviewController.currentTasksOverviewController;
                new ViewOutputDB(tasksOverviewController.mainApp.tasks, annotator).run();

                task.updateGUIProgress(1, 1);
                if (reportable())
                    task.popDialog("Done", "Data process compelete", comments);

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
