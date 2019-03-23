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
import org.apache.uima.resource.ResourceManager;
import org.apache.uima.util.Level;
import org.apache.uima.util.Progress;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
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
    protected String unit = Progress.ENTITIES;
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


    public long getStartTime() {
        return startTime;
    }


    public long getCompleteTime() {
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
        syslogger.log(syslogger.getLevel(), msg);
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
        } else {
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

    @Override
    public void log(String aMessage) {
        logString(aMessage);
    }

    @Override
    public void log(String aResourceBundleName, String aMessageKey, Object[] aArguments) {
        logString(aResourceBundleName + "\t" + aMessageKey + "\n\t" + Arrays.asList(aArguments));
    }

    @Override
    public void logException(Exception aException) {
        logString(aException.toString());
    }

    @Override
    public void setOutputStream(PrintStream aStream) {

    }

    @Override
    public void setOutputStream(OutputStream aStream) {

    }

    @Override
    public void log(Level level, String aMessage) {
        switch (level.toInteger()) {
            case Level.FINEST_INT:
                syslogger.finest(aMessage);
                break;
            case Level.FINER_INT:
                syslogger.finer(aMessage);
                break;
            case Level.FINE_INT:
                syslogger.fine(aMessage);
                break;
            case Level.INFO_INT:
                syslogger.fine(aMessage);
                break;
            case Level.WARNING_INT:
                syslogger.warning(aMessage);
                break;
            case Level.SEVERE_INT:
                syslogger.severe(aMessage);
                break;
            case Level.ALL_INT:
                syslogger.log(java.util.logging.Level.ALL, aMessage);
                break;
            case Level.CONFIG_INT:
                syslogger.log(java.util.logging.Level.CONFIG, aMessage);
            default:
                break;
        }
    }

    @Override
    public void log(Level level, String aMessage, Object param1) {
        log(level, aMessage + "\t" + param1);

    }

    @Override
    public void log(Level level, String aMessage, Object[] params) {
        log(level, aMessage + "\t" + Arrays.asList(params));
    }

    @Override
    public void log(Level level, String aMessage, Throwable thrown) {
        log(level, aMessage + "\t" + thrown.toString());
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msgKey) {
        log(level, sourceClass + ":\t" + sourceMethod + "\t" + bundleName + "\t" + msgKey);
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msgKey, Object param1) {
        log(level, sourceClass + ":\t" + sourceMethod + "\t" + bundleName + "\t" + msgKey + "\t" + param1);
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msgKey, Object[] params) {
        log(level, sourceClass + ":\t" + sourceMethod + "\t" + bundleName + "\t" + msgKey + "\t" + Arrays.asList(params));
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msgKey, Throwable thrown) {
        log(level, sourceClass + ":\t" + sourceMethod + "\t" + bundleName + "\t" + msgKey + "\t" + thrown.toString());
    }

    @Override
    public void log(String wrapperFQCN, Level level, String message, Throwable thrown) {
        log(level, wrapperFQCN + ":\t" + message + "\t" + thrown.toString());
    }

    @Override
    public boolean isLoggable(Level level) {
        return false;
    }

    @Override
    public void setLevel(Level level) {
        switch (level.toInteger()) {
            case Level.FINEST_INT:
                syslogger.setLevel(java.util.logging.Level.FINEST);
                break;
            case Level.FINER_INT:
                syslogger.setLevel(java.util.logging.Level.FINER);
                break;
            case Level.FINE_INT:
                syslogger.setLevel(java.util.logging.Level.FINE);
                break;
            case Level.INFO_INT:
                syslogger.setLevel(java.util.logging.Level.INFO);
                break;
            case Level.WARNING_INT:
                syslogger.setLevel(java.util.logging.Level.WARNING);
                break;
            case Level.SEVERE_INT:
                syslogger.setLevel(java.util.logging.Level.SEVERE);
                break;
            case Level.ALL_INT:
                syslogger.setLevel(java.util.logging.Level.ALL);
                break;
            case Level.CONFIG_INT:
                syslogger.setLevel(java.util.logging.Level.CONFIG);
                break;
            default:
                syslogger.setLevel(java.util.logging.Level.OFF);
                break;
        }
    }

    @Override
    public void setResourceManager(ResourceManager resourceManager) {

    }
}
