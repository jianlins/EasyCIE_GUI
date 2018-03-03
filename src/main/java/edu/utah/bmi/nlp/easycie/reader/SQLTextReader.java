package edu.utah.bmi.nlp.easycie.reader;

import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.task.RunEasyCIE;
import org.apache.uima.UIMA_IllegalArgumentException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * This class is  fixed to column names, record identifier is "filename", and the xmi column is "xmi"
 *
 * @author Jianlin Shi on 5/20/16.
 */
public class SQLTextReader extends CollectionReader_ImplBase {
    public static Logger logger = Logger.getLogger(SQLTextReader.class.getCanonicalName());
    public static final String PARAM_DB_CONFIG_FILE = "DBConfigFile";
    public static final String PARAM_DOC_TABLE_NAME = "DocTableName";
    public static final String PARAM_QUERY_SQL_NAME = "InputQueryName";
    public static final String PARAM_COUNT_SQL_NAME = "CountQueryName";
    public static final String PARAM_DOC_COLUMN_NAME = "DocColumnName";
    public static final String PARAM_DATASET_ID = "DatasetId";
    protected File dbConfigFile;
    protected String querySqlName, countSqlName, docColumnName, docTableName;
    public static DAO dao = null;
    protected int mCurrentIndex, totalDocs;
    protected RecordRowIterator recordIterator;
    @Deprecated
    public static boolean debug = false;
    private String datasetId;


    public void initialize() throws ResourceInitializationException {
        readConfigurations();
        this.mCurrentIndex = 0;
        addDocs();
    }

    protected void readConfigurations() {
        if (System.getProperty("java.util.logging.config.file") == null &&
                new File("logging.properties").exists()) {
            System.setProperty("java.util.logging.config.file", "logging.properties");
        }
        try {
            LogManager.getLogManager().readConfiguration();
        } catch (IOException e) {
            e.printStackTrace();
        }
        dbConfigFile = new File(readConfigureString(PARAM_DB_CONFIG_FILE, null));
        if (dao == null)
            dao = new DAO(this.dbConfigFile);
        querySqlName = readConfigureString(PARAM_QUERY_SQL_NAME, "masterInputQuery");
        countSqlName = readConfigureString(PARAM_COUNT_SQL_NAME, "masterCountQuery");
        docColumnName = readConfigureString(PARAM_DOC_COLUMN_NAME, "TEXT");
        docTableName = readConfigureString(PARAM_DOC_TABLE_NAME, "DOCUMENTS");
        datasetId = readConfigureString(PARAM_DATASET_ID, "0");
    }

    private String readConfigureString(String parameterName, String defaultValue) {
        Object tmpObj = this.getConfigParameterValue(parameterName);
        if (tmpObj == null) {
            if (defaultValue == null) {
                throw new UIMA_IllegalArgumentException("parameter not set", new Object[]{parameterName, this.getMetaData().getName()});
            } else {
                tmpObj = defaultValue;
            }
        }
        return (tmpObj + "").trim();
    }

    protected void addDocs() {
        totalDocs = dao.countRecords(countSqlName, docTableName, datasetId);
        if(logger.isLoggable(Level.INFO))
            System.out.println("Total documents need to be processed: "+totalDocs);
        recordIterator = dao.queryRecordsFromPstmt(querySqlName, docTableName, datasetId);
    }

    public boolean hasNext() {
        return recordIterator != null && recordIterator.hasNext();
    }

    public void getNext(CAS aCAS) throws IOException, CollectionException {

        RecordRow currentRecord = recordIterator.next();
        String metaInfor = currentRecord.serialize(docColumnName);
        String text = (String) currentRecord.getValueByColumnName(docColumnName);
        if(logger.isLoggable(Level.INFO))
            logger.finest("Read document: "+docColumnName);
        if (text == null)
            text = "";
        JCas jcas;
        try {
            jcas = aCAS.getJCas();
        } catch (CASException var6) {
            throw new CollectionException(var6);
        }
        jcas.setDocumentText(text);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jcas, 0, text.length());
        srcDocInfo.setUri(metaInfor);
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(text.length());
        srcDocInfo.setLastSegment(this.mCurrentIndex == this.totalDocs);
        srcDocInfo.addToIndexes();
        mCurrentIndex++;
    }


    public void close() throws IOException {
        dao.close();
    }

    public Progress[] getProgress() {
        return new Progress[]{new ProgressImpl(this.mCurrentIndex, totalDocs, "docs")};
    }

    public int getNumberOfDocuments() {
        return this.totalDocs;
    }
}