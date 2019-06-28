package edu.utah.bmi.nlp.easycie;

import edu.utah.bmi.nlp.sql.RecordRow;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;

import java.io.File;

public class MetaDataCommonFunctions {
    public static SourceDocumentInformation genSourceDocumentInformationAnno(JCas jCas, RecordRow currentRecord, String docColumnName, int textLength) {
        String metaInfor = currentRecord.serialize(docColumnName);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jCas, 0, textLength);
        srcDocInfo.setUri(metaInfor);
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(textLength);
        return srcDocInfo;
    }

    public static RecordRow getMetaData(JCas jCas) {
        RecordRow baseRecordRow = new RecordRow();
        FSIterator it = jCas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
        if (it.hasNext()) {
            SourceDocumentInformation e = (SourceDocumentInformation) it.next();
            String serializedString = new File(e.getUri()).getName();
            baseRecordRow.deserialize(serializedString);
        }
        return baseRecordRow;
    }
}
