package edu.utah.bmi.nlp.easycie.reader;

import edu.utah.bmi.nlp.easycie.MetaDataCommonFunctions;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import org.apache.uima.UIMA_IllegalArgumentException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * This read will append metadata string into document text for downstream NLP analyses.
 *
 * @author Jianlin Shi on 5/20/16.
 */
public class SQLMetaTextReader extends SQLTextReader {
    public static Logger logger = Logger.getLogger(SQLMetaTextReader.class.getCanonicalName());

    public static final String PARAM_META_COLUMNS = "MetaColumns";
    public static final String PARAM_APPEND_POS = "AppendPosition";
    protected String appendPos = "prefix";
    protected ArrayList<String> metaColumns = new ArrayList<>();

    public void initialize() {
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
        dao = EDAO.getInstance(this.dbConfigFile);
        querySqlName = readConfigureString(PARAM_QUERY_SQL_NAME, "masterInputQuery");
        countSqlName = readConfigureString(PARAM_COUNT_SQL_NAME, "masterCountQuery");
        docColumnName = readConfigureString(PARAM_DOC_COLUMN_NAME, "TEXT");
        docTableName = readConfigureString(PARAM_DOC_TABLE_NAME, "DOCUMENTS");
        datasetId = readConfigureString(PARAM_DATASET_ID, "0");
        String metaColumnString = readConfigureString(PARAM_META_COLUMNS, "");
        for (String column : metaColumnString.split("[,; :]"))
            metaColumns.add(column.trim());
        Object value = this.getConfigParameterValue(PARMA_TRIM_TEXT);
        if (value != null && value instanceof Boolean)
            trimText = (Boolean) value;
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
        StringBuilder sb = new StringBuilder();
        for (String col : metaColumns) {
            sb.append("<<");
            sb.append(currentRecord.getStrByColumnName(col));
            sb.append(">>\n\n");
        }
        if (appendPos.toLowerCase().equals("prefix")) {
            text = sb.toString() + text;
        } else {
            text = text + sb.toString();
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

}
