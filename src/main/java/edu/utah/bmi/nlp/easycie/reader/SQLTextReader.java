package edu.utah.bmi.nlp.easycie.reader;

import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.easycie.MetaDataCommonFunctions;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is  fixed to column names, record identifier is "filename", and the xmi column is "xmi"
 *
 * @author Jianlin Shi on 5/20/16.
 */
public class SQLTextReader extends CasCollectionReader_ImplBase {
    public static Logger logger = IOUtil.getLogger(SQLTextReader.class);

    public static final String PARAM_DB_CONFIG_FILE = "DBConfigFile";
    @ConfigurationParameter(name = PARAM_DB_CONFIG_FILE, mandatory = true,
            description = "The db configuration file path.")
    protected String dbConfigFilePath;

    public static final String PARAM_QUERY_SQL_NAME = "InputQueryName";
    @ConfigurationParameter(name = PARAM_QUERY_SQL_NAME, mandatory = false, defaultValue = "masterInputQuery",
            description = "The sql query name for querying input documents, default is 'masterInputQuery.'")
    protected String querySqlName;

    public static final String PARAM_COUNT_SQL_NAME = "CountQueryName";
    @ConfigurationParameter(name = PARAM_COUNT_SQL_NAME, mandatory = false, defaultValue = "masterCountQuery",
            description = "The sql query name for querying the total number of input documents, defaul is 'masterCountQuery.'")
    protected String countSqlName;

    public static final String PARAM_DOC_TABLE_NAME = "DocTableName";
    @ConfigurationParameter(name = PARAM_DOC_TABLE_NAME, mandatory = false, defaultValue = "DOCUMENTS",
            description = "The document table name, default is 'DOCUMENTS.'")
    protected String docTableName;

    public static final String PARAM_DOC_COLUMN_NAME = "DocColumnName";
    @ConfigurationParameter(name = PARAM_DOC_COLUMN_NAME, mandatory = false, defaultValue = "TEXT",
            description = "The name of the column that hold the input document text, default is 'TEXT.'")
    protected String docColumnName;

    public static final String PARMA_TRIM_TEXT = "TrimText";
    @ConfigurationParameter(name = PARMA_TRIM_TEXT, mandatory = false, defaultValue = "false",
            description = "Whether to trim document text, default is 'false.'")
    protected boolean trimText;

    public static final String PARAM_DATASET_ID = "DatasetId";
    @ConfigurationParameter(name = PARAM_DATASET_ID, mandatory = false, defaultValue = "0",
            description = "The dataset id (when multiple dataset is stored in the input table), default is '0.'")
    protected String datasetId;

    protected File dbConfigFile;
    public static EDAO dao = null;
    protected int mCurrentIndex, totalDocs;
    protected RecordRowIterator recordIterator;
    @Deprecated
    public static boolean debug = false;


    public void initialize(UimaContext cont) {
        readConfigurations();
        this.mCurrentIndex = 0;
        addDocs();
    }

    protected void readConfigurations() {
        dbConfigFile = new File(dbConfigFilePath);
        dao = EDAO.getInstance(this.dbConfigFile);
    }

    protected void addDocs() {
        totalDocs = dao.countRecords(countSqlName, docTableName, datasetId);
        if (logger.isLoggable(Level.INFO))
            System.out.println("Total documents need to be processed: " + totalDocs);
        recordIterator = dao.queryRecordsFromPstmt(querySqlName, docTableName, datasetId);
    }

    public boolean hasNext() {

        return recordIterator != null && recordIterator.hasNext();
    }

    public void getNext(CAS aCAS) throws CollectionException {
        RecordRow currentRecord = recordIterator.next();
        String text = (String) currentRecord.getValueByColumnName(docColumnName);
        if (trimText) {
            text = text.replaceAll("(\\n[^\\w\\p{Punct}]+\\n)", "\n\n")
                    .replaceAll("(\\n\\s*)+(?:\\n)", "\n\n")
                    .replaceAll("^(\\n\\s*)+(?:\\n)", "")
                    .replaceAll("[^\\w\\p{Punct}\\s]", " ");
        }
        logger.finest("Read document: " + currentRecord.getStrByColumnName("DOC_NAME"));
        if (text == null)
            text = "";
        JCas jcas;
        try {
            jcas = aCAS.getJCas();
        } catch (CASException var6) {
            throw new CollectionException(var6);
        }
        jcas.setDocumentText(text);
        SourceDocumentInformation srcDocInfo = MetaDataCommonFunctions.genSourceDocumentInformationAnno(jcas, currentRecord, docColumnName, text.length());

        srcDocInfo.setLastSegment(this.mCurrentIndex == this.totalDocs);
        srcDocInfo.addToIndexes();
        mCurrentIndex++;
    }


    public void close() {
        dao.close();
    }

    public Progress[] getProgress() {
        return new Progress[]{new ProgressImpl(this.mCurrentIndex, totalDocs, Progress.ENTITIES)};
    }

    public int getNumberOfDocuments() {
        return this.totalDocs;
    }
}
