//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.controller.GUILogger;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.EntityProcessStatus;
import org.apache.uima.collection.StatusCallbackListener;
import org.apache.uima.util.ProcessTraceEvent;
import org.apache.uima.util.Progress;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleStatusCallbackListenerImpl implements StatusCallbackListener {
    public static Logger syslogger = Logger.getLogger(SimpleStatusCallbackListenerImpl.class.getCanonicalName());
    protected final List<Exception> exceptions = new ArrayList();
    protected boolean isProcessing;
    protected UIMALogger logger;
    protected int entityCount;
    long size = 0L;
    public int totaldocs = -1;
    public long mInitCompleteTime;
    public int maxCommentLength = 1000;
    public CollectionProcessingEngine mCPE;
    protected DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    protected SimpleStatusCallbackListenerImpl() {
    }

    public SimpleStatusCallbackListenerImpl(UIMALogger logger) {
        this.isProcessing = true;
        this.logger = logger;
    }

    public void setCollectionProcessingEngine(CollectionProcessingEngine engine) {
        this.mCPE = engine;
    }

    public void entityProcessComplete(CAS aCas, EntityProcessStatus aStatus) {
        if (!aStatus.isException()) {
            ++this.entityCount;
            if (this.logger != null && syslogger.isLoggable(Level.FINE)) {
                if (this.totaldocs != -1) {
                    this.logger.logString("Processed " + this.entityCount + " of " + this.totaldocs);
                } else {
                    this.logger.logString("Processed " + this.entityCount);
                }
            }

            String docText = aCas.getDocumentText();
            if (docText != null) {
                this.size += (long) docText.length();
            }

        } else {
            List exceptions = aStatus.getExceptions();

            for (int i = 0; i < exceptions.size(); ++i) {
                ((Throwable) exceptions.get(i)).printStackTrace();
            }

        }
    }

    public void aborted() {
        synchronized (this) {
            if (this.isProcessing) {
                this.isProcessing = false;
                this.notify();
            }

        }
    }

    public void initializationComplete() {
        this.mInitCompleteTime = System.currentTimeMillis();
        if (this.logger == null) {
            return;
        }
        this.logger.logString(this.df.format(new Date()) + "\tCPM Initialization Complete");
        Progress[] progress = this.mCPE.getCollectionReader().getProgress();
        if (progress != null) {
            for (int i = 0; i < progress.length; ++i) {
                if (progress[i].getUnit().equals(this.logger.getUnit())) {
                    this.totaldocs = (int) progress[i].getTotal();
                    break;
                }
            }
        }

    }

    public void batchProcessComplete() {
    }

    public void paused() {
        if (this.logger != null) {
            this.logger.logString(this.df.format(new Date()) + "\tPipeline paused");
        }

    }

    public void resumed() {
        if (this.logger != null) {
            this.logger.logString(this.df.format(new Date()) + "\tPipeline resumed");
        }

    }

    public void collectionProcessComplete() {
        if (this.logger != null) {
            this.logger.logString(this.df.format(new Date()) + "\tCollection completed");
        }

        synchronized (this) {
            if (this.isProcessing) {
                this.notify();
                if (this.logger != null) {
                    this.logger.logCompleteTime();
                    long mCompleteTime = this.logger.getCompletetime();
                    long mStartTime = this.logger.getStarttime();
                    long initTime = this.mInitCompleteTime - mStartTime;
                    long processingTime = mCompleteTime - this.mInitCompleteTime;
                    long elapsedTime = initTime + processingTime;
                    StringBuilder reportContent = new StringBuilder();
                    reportContent.append(this.entityCount + " notes\n");
                    if (this.size > 0L) {
                        reportContent.append(this.size + " characters\n");
                    }

                    reportContent.append("Total:\t" + elapsedTime + " ms\n");
                    reportContent.append("Initialization:\t" + initTime + " ms\n");
                    reportContent.append("Processing:\t" + processingTime + " ms\n");
                    Iterator var13 = this.mCPE.getPerformanceReport().getEvents().iterator();

                    while (var13.hasNext()) {
                        ProcessTraceEvent event = (ProcessTraceEvent) var13.next();
                        String componentName = event.getComponentName();
                        componentName = componentName.substring(componentName.lastIndexOf(".") + 1);
                        String eventType = event.getType();
                        if (eventType.startsWith("End")) {
                            reportContent.append("\t\t" + event.getType() + "\t" + event.getDuration() + " ms\n");
                        } else {
                            reportContent.append(componentName + "\t" + event.getType() + "\t" + event.getDuration() + " ms\n");
                        }
                    }

                    this.logger.setItem("NUM_NOTES", this.entityCount);
                    String comments;
                    if (reportContent.length() > this.maxCommentLength) {
                        comments = reportContent.substring(0, this.maxCommentLength);
                    } else {
                        comments = reportContent.toString();
                    }

                    if (!(logger instanceof GUILogger) || ((GUILogger) logger).reportable())
                        this.logger.setItem("COMMENTS", comments);
                    else
                        this.logger.setItem("COMMENTS", "");
                    this.logger.logItems();
                }
            }

            this.isProcessing = false;
        }
    }
}
