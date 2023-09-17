//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.EntityProcessStatus;
import org.apache.uima.collection.StatusCallbackListener;
import org.apache.uima.collection.base_cpm.BaseCollectionReader;
import org.apache.uima.util.ProcessTraceEvent;
import org.apache.uima.util.Progress;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class SimpleStatusCallbackListenerImpl implements StatusCallbackListener {
    public static Logger syslogger = Logger.getLogger(SimpleStatusCallbackListenerImpl.class.getCanonicalName());
    protected final List<Exception> exceptions = new ArrayList();
    protected boolean isProcessing;
    protected UIMALogger logger;
    public CollectionProcessingEngine mCPE;
    protected StatusSetable runner;

    protected SimpleStatusCallbackListenerImpl() {
    }

    public SimpleStatusCallbackListenerImpl(UIMALogger logger) {
        this.isProcessing = true;
        this.logger = logger;
        if (logger != null)
            logger.logStartTime();
    }

    public void setCollectionProcessingEngine(CollectionProcessingEngine engine) {
        this.mCPE = engine;
    }

    public void entityProcessComplete(CAS aCas, EntityProcessStatus aStatus) {
        if (logger != null)
            logger.entityProcessComplete(aCas, aStatus);

    }

    public void aborted() {
        synchronized (this) {
            if (logger != null)
                logger.aborted();
            if (this.isProcessing) {
                this.isProcessing = false;
                this.notify();
            }
        }
    }

    public void initializationComplete() {
        if (this.logger == null) {
            return;
        }
        int totalDocs = 0;
        BaseCollectionReader reader = this.mCPE.getCollectionReader();
        Progress[] progress = reader.getProgress();
        if (progress != null) {
            for (int i = 0; i < progress.length; ++i) {
                if (progress[i].getUnit().equals(this.logger.getUnit())) {
                    totalDocs = (int) progress[i].getTotal();
                    break;
                }
            }
        }
        logger.initializationComplete(totalDocs);


    }

    public void batchProcessComplete() {
    }

    public void paused() {
        if (this.logger != null) {
            logger.paused();
        }

    }

    public void resumed() {
        if (this.logger != null) {
            logger.resumed();
        }

    }

    public void collectionProcessComplete() {

        synchronized (this) {
            if (this.isProcessing) {
                this.notify();
                if (this.logger != null) {
                    if (runner != null)
                        runner.setStatus(2);
                    StringBuilder reportContent = new StringBuilder();
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
                    if (logger != null)
                        logger.collectionProcessComplete(reportContent.toString());
                }
            }

            this.isProcessing = false;
//            System.exit(1);
        }
    }

    public void setRunner(StatusSetable runner) {
        this.runner = runner;
    }
}
