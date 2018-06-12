package edu.utah.bmi.nlp.uima.loggers;

import org.apache.uima.cas.CAS;
import org.apache.uima.collection.EntityProcessStatus;

/**
 * @author Jianlin Shi
 * Created on 1/13/17.
 */
public interface UIMALogger {

    void reset();

    void setItem(String key, Object value);

    void logStartTime();

    void logCompleteTime();

    long getStarttime();

    long getCompletetime();

    String logItems();

    Object getRunid();

    String getItem(String key);

    void logString(String msg);

    String getUnit();

    void setUnit(String unit);

    public void initializationComplete(int totalDocs);

    public void entityProcessComplete(CAS aCas, EntityProcessStatus aStatus);

    public void batchProcessComplete();

    public void collectionProcessComplete(String reportContent);

    public void paused();

    public void resumed();

    public void aborted();
}
