package edu.utah.bmi.nlp.uima.loggers;


import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import javafx.application.Platform;

import javax.swing.*;
import java.io.File;
import java.util.Date;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public class NLPDBLogger extends GUILogger {
	protected EDAO ldao;
	protected String tableName;
	protected String keyColumnName;
	protected long starttime = 0, completetime = 0;
	protected String annotator;
	protected String dbConfigureFile;


	protected Object runid;
	protected RecordRow recordRow;

	protected NLPDBLogger() {
		ldao = null;
		tableName = "";
		keyColumnName = "";

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
		recordRow = new RecordRow();
	}


	public void reset() {
		recordRow = new RecordRow();
		starttime = 0;
		entityCount = 0;
		completetime = 0;
		initCompleteTime = 0;
		size = 0;
		runid=0;
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
		recordRow = new RecordRow();
		starttime = 0;
		entityCount = 0;
		completetime = 0;
		initCompleteTime = 0;
		size = 0;
		ldao = EDAO.getInstance(new File(dbConfigureFile));
		if(runid==null || runid.equals(0)) {
			runid = ldao.insertRecord(tableName, recordRow);
			if (runid == null)
				runid = ldao.getLastId(tableName);
		}
		setItem("RUN_ID", runid);
		starttime = System.currentTimeMillis();
		setItem("ANNOTATOR", annotator);
		setItem("START_DTM", new Date(starttime));
	}


	public void logCompleteTime() {
		completetime = System.currentTimeMillis();
		setItem("END_DTM", new Date(completetime));
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


	@Override
	public void collectionProcessComplete(String reportContent) {
		logCompleteTime();
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

		if (reportable())
			setItem("COMMENTS", comments);

		ldao.updateRecord(tableName, recordRow);
		ldao.close();

		logItems();

		if (task != null && task.guiEnabled) {
			Platform.runLater(() -> {
				boolean res = TasksOverviewController.currentTasksOverviewController.showDBTable(
						AnnotationLogger.records.iterator(), columnInfo, "output", tabViewName);
				if (res)
					task.updateGUIMessage("String processing completed.");
				else
					task.updateGUIMessage("No annotation exported.");

				task.updateGUIProgress(1, 1);
				if (this.report)
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
		reset();
	}


}
