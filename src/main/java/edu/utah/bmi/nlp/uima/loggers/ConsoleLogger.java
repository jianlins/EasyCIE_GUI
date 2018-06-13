/*
 * Copyright  2017  Department of Biomedical Informatics, University of Utah
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.utah.bmi.nlp.uima.loggers;

import org.apache.uima.UIMAException;
import org.apache.uima.UIMARuntimeException;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.EntityProcessStatus;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public class ConsoleLogger implements UIMALogger {
    public static Logger syslogger = Logger.getLogger(UIMALogger.class.getCanonicalName());
    protected long startTime = 0, initCompleteTime = 0, completeTime = 0;
    protected int totaldocs = -1;
    protected long size = 0L;
    protected int entityCount = 0;
    protected LinkedHashMap<String, Object> loggedItems = new LinkedHashMap();
    protected String unit = "docs";
    protected boolean report = true;
    protected DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    public ConsoleLogger() {

    }

    public void reset() {
        entityCount = 0;
        loggedItems.clear();
    }


    public void setItem(String key, Object value) {
        loggedItems.put(key, value);
    }


    public void logStartTime() {
        startTime = System.currentTimeMillis();
        entityCount = 0;
    }


    public void logCompleteTime() {
        completeTime = System.currentTimeMillis();
    }


    public long getStarttime() {
        return startTime;
    }


    public long getCompletetime() {
        return completeTime;
    }


    public String logItems() {
        StringBuilder logs = new StringBuilder();
        for (Map.Entry<String, Object> item : this.loggedItems.entrySet()) {
            logs.append(item.getKey());
            logs.append("\n");
            logs.append(item.getValue());
            logs.append("\n\n");
        }
        logString(logs.toString());
        return logs.toString();
    }

    public Object getRunid() {
        return -1;
    }

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
    public void initializationComplete(int totalDocs) {
        logString("Initialization complete. Total " + totalDocs + " documents to process.");
        this.initCompleteTime = System.currentTimeMillis();
        logString(this.df.format(new Date()) + "\tCPM Initialization Complete");
        this.totaldocs = totalDocs;

    }

    @Override
    public void entityProcessComplete(CAS aCas, EntityProcessStatus aStatus) {
        if (aStatus.isException()) {
            List ex = aStatus.getExceptions();
            displayError((Throwable) ex.get(0));
        }else{
            entityCount++;
        }
    }

    @Override
    public void batchProcessComplete() {

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
        logString(report.toString());

    }

    @Override
    public void paused() {
        logString("StatusCallbackListenerImpl::paused()");
    }

    @Override
    public void resumed() {
        logString("StatusCallbackListenerImpl::resumed");

    }

    @Override
    public void aborted() {
        logString("StatusCallbackListenerImpl::aborted");
    }

    public void setReportable(boolean report) {
        this.report = report;
    }

    public boolean reportable() {
        return report;
    }

    public String getItem(String key) {
        if (loggedItems.containsKey(key))
            return loggedItems.get(key).toString();
        return "";
    }

    public void displayError(Throwable aThrowable) {
        aThrowable.printStackTrace();

        String message = aThrowable.toString();

        // For UIMAExceptions or UIMARuntimeExceptions, add cause info.
        // We have to go through this nonsense to support Java 1.3.
        // In 1.4 all exceptions can have a cause, so this wouldn't involve
        // all of this typecasting.
        while ((aThrowable instanceof UIMAException) || (aThrowable instanceof UIMARuntimeException)) {
            if (aThrowable instanceof UIMAException) {
                aThrowable = aThrowable.getCause();
            } else if (aThrowable instanceof UIMARuntimeException) {
                aThrowable = aThrowable.getCause();
            }

            if (aThrowable != null) {
                message += ("\nCausedBy: " + aThrowable.toString());
            }
        }

        logString(message);
    }

}
