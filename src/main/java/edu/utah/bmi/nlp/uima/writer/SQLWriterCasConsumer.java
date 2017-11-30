package edu.utah.bmi.nlp.uima.writer;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.type.system.Sentence;
import org.apache.uima.UIMA_IllegalArgumentException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * I2B2CollectionReader must be used to read the data, and sentence segmentor must be used to split sentences
 * in order to get a sentence context.
 * <p>
 * absoluteOffset is to specify whether the span will be saved in absolute offset to the document
 * or relative offset to the corresponding sentence
 *
 * @author Jianlin Shi
 * Created on 5/22/16.
 */
public class SQLWriterCasConsumer extends JCasAnnotator_ImplBase {
    public static final String PARAM_SQLFILE = "SQLFile";
    public static final String PARAM_TABLENAME = "TableName";
    public static final String PARAM_OVERWRITETABLE = "OverWriteTable";
    public static final String PARAM_WRITE_CONCEPT = "WriteConcepts";
    public static final String PARAM_BATCHSIZE = "BatchSize";
    public static final String PARAM_ANNOTATOR = "Annotator";
    public static final String PARAM_VERSION = "Version";
    public static final String PARAM_MIN_LENGTH = "MinTextLength";

    public static final String PARAM_USE_ANNOTATIONS_ANNOTATOR = "UserAnnotationsAnnotator";
    protected File sqlFile;
    protected String tablename, annotator = "", version;
    protected int mDocNum, batchSize = 15,minTextLength;
    public static DAO dao = null;
    protected boolean debug = false, overwriteTable = false, useAnnotationsAnnotator = false;
    private ArrayList<String> typeToSave = new ArrayList<>();


    public SQLWriterCasConsumer() {
    }


    public void initialize(UimaContext cont) throws ResourceInitializationException {
        this.mDocNum = 0;
        this.sqlFile = new File(readConfigureString(cont, PARAM_SQLFILE, null));
        this.tablename = readConfigureString(cont, PARAM_TABLENAME, "OUTPUT");
        overwriteTable = (Boolean) readConfigureObject(cont, PARAM_OVERWRITETABLE, false);
        useAnnotationsAnnotator = (Boolean) readConfigureObject(cont, PARAM_USE_ANNOTATIONS_ANNOTATOR, false);
        batchSize = (Integer) readConfigureObject(cont, PARAM_BATCHSIZE, 15);
        minTextLength = (Integer) readConfigureObject(cont, PARAM_MIN_LENGTH, 0);
        annotator = readConfigureString(cont, PARAM_ANNOTATOR, "uima");
        version = readConfigureString(cont, PARAM_VERSION, null);

        if (dao == null) {
            dao = new DAO(this.sqlFile);
        }
        dao.batchsize = batchSize;
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", tablename, overwriteTable);

        if (!this.sqlFile.exists()) {
            this.sqlFile.mkdirs();
        }
        Object writeConceptObj = cont.getConfigParameterValue(PARAM_WRITE_CONCEPT);
        if (writeConceptObj != null && writeConceptObj.toString().trim().length()>0) {
            for (String type : ((String) writeConceptObj).split(","))
                typeToSave.add(DeterminantValueSet.checkNameSpace(type));
        } else {
            typeToSave.add(Concept.class.getCanonicalName());
            typeToSave.add(Doc_Base.class.getCanonicalName());
        }
    }

    public void process(JCas jcas) throws AnalysisEngineProcessException {
        String fileName = null;
        if(jcas.getDocumentText().length()<minTextLength)
            return;


        ArrayList<RecordRow> annotations = new ArrayList<>();
        IntervalST sentenceTree = new IntervalST();
        ArrayList<Sentence> sentenceList = new ArrayList<>();

        RecordRow baseRecordRow = new RecordRow();
        FSIterator it = jcas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
        if (it.hasNext()) {
            SourceDocumentInformation e = (SourceDocumentInformation) it.next();
            String serializedString = new File(e.getUri()).getName();
            baseRecordRow.deserialize(serializedString);
        }
        baseRecordRow.addCell("RUN_ID", version);
        if (debug)
            System.err.println("processing doc: " + fileName);

        it = jcas.getAnnotationIndex(Sentence.type).iterator();
        while (it.hasNext()) {
            Sentence thisSentence = (Sentence) it.next();
            sentenceList.add(thisSentence);
            sentenceTree.put(new Interval1D(thisSentence.getBegin(), thisSentence.getEnd()), sentenceList.size() - 1);
        }

        readAnnotations(jcas, baseRecordRow, annotations, sentenceList, sentenceTree, fileName);
        dao.insertRecords(tablename, annotations);
    }

    protected void readAnnotations(JCas jcas, RecordRow baseRecordRow, ArrayList<RecordRow> annotations, ArrayList<Sentence> sentenceList, IntervalST sentenceTree, String fileName) {
        CAS cas = jcas.getCas();
        String docText = jcas.getDocumentText();
        for (String type : typeToSave)
            saveOneTypeAnnotation(cas, docText, type, baseRecordRow, annotations, sentenceList, sentenceTree, fileName);
    }

    private void saveOneTypeAnnotation(CAS cas, String docText, String annotationType,
                                       RecordRow baseRecordRow, ArrayList<RecordRow> annotations,
                                       ArrayList<Sentence> sentenceList, IntervalST sentenceTree, String fileName) {

        Iterator<AnnotationFS> annoIter = CasUtil.iterator(cas, CasUtil.getType(cas, annotationType));
        while (annoIter.hasNext()) {
            Annotation thisAnnotation = (Annotation) annoIter.next();
            RecordRow record = baseRecordRow.clone();
            record.addCell("TYPE", thisAnnotation.getType().getShortName());
            record.addCell("TEXT", thisAnnotation.getCoveredText());
            if (useAnnotationsAnnotator) {
                if (thisAnnotation instanceof ConceptBASE) {
                    ConceptBASE conceptBASE = (ConceptBASE) thisAnnotation;
                    String thisAnnotator = conceptBASE.getAnnotator();
                    if (thisAnnotator == null || thisAnnotator.length() == 0)
                        record.addCell("ANNOTATOR", this.annotator);
                    else
                        record.addCell("ANNOTATOR", thisAnnotator);
                }
            } else {
                record.addCell("ANNOTATOR", this.annotator);
            }


            StringBuilder sb = new StringBuilder();
            for (Feature feature : thisAnnotation.getType().getFeatures()) {
                String domain = feature.getDomain().getShortName();
                if (domain.equals("AnnotationBase") || domain.equals("Annotation"))
                    continue;
                String featureName = feature.getShortName();
                String value = thisAnnotation.getFeatureValueAsString(feature);

                switch (featureName) {
                    case "Annotator":
                        record.addCell("ANNOTATOR", this.annotator);
                    default:
                        if (value != null && value.trim().length() > 0) {
                            sb.append(featureName + ": " + value);
//                sb.append(value);
                            sb.append("\n");
                        }
                }
            }
            record.addCell("FEATURES", sb.toString());

            if (thisAnnotation instanceof ConceptBASE) {

                Object sentenceIdObject = sentenceTree.get(new Interval1D(thisAnnotation.getBegin(), thisAnnotation.getEnd()));
                String sentence;

                Sentence sentenceAnno;
                int sentenceBegin, sentenceEnd;
                if (sentenceIdObject != null) {
                    int sentenceId = (Integer) sentenceIdObject;
                    sentenceAnno = sentenceList.get(sentenceId);
                    sentence = sentenceAnno.getCoveredText();

                    if (sentence.length() < 50) {
                        sentenceBegin = sentenceList.get(sentenceId == 0 ? sentenceId : sentenceId - 1).getBegin();
                        sentenceEnd = sentenceList.get(sentenceId == sentenceList.size() - 1 ? sentenceId : sentenceId + 1).getEnd();
                    } else {
                        sentenceBegin = sentenceAnno.getBegin();
                        sentenceEnd = sentenceAnno.getEnd();
                    }
                    sentence = docText.substring(sentenceBegin, sentenceEnd);
//                if (absoluteOffset) {
////              if use absolute offset, save sentence span instead of a string
//                    note = (begin - sentenceBegin) + "-" + (end - sentenceBegin) + note;
//                } else {
//                    begin = begin - sentenceBegin;
//                    end = end - sentenceBegin;
//                    note = begin + "-" + end + note;
//                }
                } else {
                    int contBegin = thisAnnotation.getBegin() - 50;
                    int contEnd = thisAnnotation.getEnd() + 50;
                    if (contBegin < 0)
                        contBegin = 0;
                    if (contEnd > docText.length()) {
                        contEnd = docText.length();
                    }
                    sentence = docText.substring(contBegin, contEnd);
//                if (absoluteOffset) {
////              if use absolute offset, save sentence span instead of a string
//                    note = (begin - contBegin) + "-" + (end - contBegin) + note;
//                } else {
//                    begin = begin - contBegin;
//                    end = end - contEnd;
//                    note = begin + "-" + end + note;
//                }
                    record.addCell("NOTE", record.getValueByColumnName("NOTE") + "<missed sentence>");
                    sentenceBegin = contBegin;
                }
                record.addCell("SNIPPET", sentence);
                record.addCell("SNIPPET_BEGIN", sentenceBegin);
                record.addCell("SNIPPET_END", sentenceBegin + sentence.length());
                record.addCell("BEGIN", thisAnnotation.getBegin() - sentenceBegin);
                record.addCell("END", thisAnnotation.getEnd() - sentenceBegin);
            }else {
                record.addCell("SNIPPET", thisAnnotation.getCoveredText());
                record.addCell("SNIPPET_BEGIN", thisAnnotation.getBegin());
                record.addCell("SNIPPET_END", thisAnnotation.getEnd());
                record.addCell("BEGIN", 0);
                record.addCell("END", thisAnnotation.getEnd() - thisAnnotation.getBegin());
            }
            annotations.add(record);
        }
    }


    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        dao.endBatchInsert();
    }

    private String getFeatures(Annotation thisAnnotation) {
        String value;
        StringBuilder sb = new StringBuilder();
        for (Feature feature : thisAnnotation.getType().getFeatures()) {
            String domain = feature.getDomain().getShortName();
            if (domain.equals("AnnotationBase") || domain.equals("Annotation"))
                continue;
            String featureName = feature.getShortName();
            value = thisAnnotation.getFeatureValueAsString(feature);
            if (value != null && value.trim().length() > 0) {
                sb.append(featureName + ": " + value);
//                sb.append(value);
                sb.append("\n");
            }
        }
        value = sb.toString();
        return value;
    }

    private Object readConfigureObject(UimaContext cont, String parameterName, Object defaultValue) {
        Object tmpObj = cont.getConfigParameterValue(parameterName);
        if (tmpObj == null) {
            if (defaultValue == null) {
                throw new UIMA_IllegalArgumentException("parameter not set", new Object[]{parameterName});
            } else {
                tmpObj = defaultValue;
            }
        }
        return tmpObj;
    }

    private String readConfigureString(UimaContext cont, String parameterName, String defaultValue) {
        String value = readConfigureObject(cont, parameterName, defaultValue) + "";
        return value.trim();
    }

}

