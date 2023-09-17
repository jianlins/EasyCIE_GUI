package edu.utah.bmi.nlp.easycie.writer;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.easycie.MetaDataCommonFunctions;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.type.system.Sentence;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.uima.UIMA_IllegalArgumentException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.*;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


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
    public static Logger classLogger = IOUtil.getLogger(SQLWriterCasConsumer.class);
    //    allow to save sentence boundaries
    public static Logger rushaeLogger = IOUtil.getLogger(RuSH_AE.class);
    public static final String PARAM_DB_CONFIG_FILE = DeterminantValueSet.PARAM_DB_CONFIG_FILE;
    public static final String PARAM_SNIPPET_TABLENAME = "SnippetTableName";
    public static final String PARAM_DOC_TABLENAME = "DocTableName";
    public static final String PARAM_OVERWRITETABLE = "OverWriteTable";
    public static final String PARAM_WRITE_CONCEPT = "WriteConcepts";
    public static final String PARAM_BATCHSIZE = "BatchSize";
    public static final String PARAM_ANNOTATOR = DeterminantValueSet.PARAM_ANNOTATOR;
    public static final String PARAM_VERSION = DeterminantValueSet.PARAM_VERSION;
    public static final String PARAM_MIN_LENGTH = "MinTextLength";

    public static final String PARAM_USE_ANNOTATIONS_ANNOTATOR = "UserAnnotationsAnnotator";
    protected File sqlFile;
    protected String snippetTableName, docTableName, annotator = "", version;
    protected int mDocNum, batchSize = 15, minTextLength;
    public static EDAO dao = null;
    protected boolean debug = false, overwriteTable = false, useAnnotationsAnnotator = false;
    private ArrayList<String> typeToSave = new ArrayList<>();
    private HashMap<String, HashMap<String, Method>> annotationGetFeatures = new HashMap<>();


    public SQLWriterCasConsumer() {
    }


    public void initialize(UimaContext cont) {
        this.mDocNum = 0;
        this.sqlFile = new File(readConfigureString(cont, PARAM_DB_CONFIG_FILE, null));
        this.snippetTableName = readConfigureString(cont, PARAM_SNIPPET_TABLENAME, "RESULT_SNIPPET");
        this.docTableName = readConfigureString(cont, PARAM_DOC_TABLENAME, "RESULT_DOC");
        Object value = readConfigureObject(cont, PARAM_OVERWRITETABLE, false);
        if (value instanceof Boolean)
            overwriteTable = ((Boolean) value);
        else
            overwriteTable = value.toString().toLowerCase().startsWith("t");
        useAnnotationsAnnotator = (Boolean) readConfigureObject(cont, PARAM_USE_ANNOTATIONS_ANNOTATOR, false);
        batchSize = (Integer) readConfigureObject(cont, PARAM_BATCHSIZE, 15);
        minTextLength = (Integer) readConfigureObject(cont, PARAM_MIN_LENGTH, 0);
        annotator = readConfigureString(cont, PARAM_ANNOTATOR, "uima");
        version = readConfigureString(cont, PARAM_VERSION, null);

        dao = EDAO.getInstance(this.sqlFile);
        dao.batchsize = batchSize;
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", snippetTableName, overwriteTable);
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", docTableName, overwriteTable);

        if (!this.sqlFile.exists()) {
            this.sqlFile.mkdirs();
        }
        Object writeConceptObj = cont.getConfigParameterValue(PARAM_WRITE_CONCEPT);
        typeToSave.clear();
        if (writeConceptObj != null && writeConceptObj.toString().trim().length() > 0) {
            for (String type : ((String) writeConceptObj).split("[,;\\|]"))
                typeToSave.add(DeterminantValueSet.checkNameSpace(type.trim()));
        } else {
            typeToSave.add(Concept.class.getCanonicalName());
            typeToSave.add(Doc_Base.class.getCanonicalName());
        }
    }

    public void process(JCas jcas) throws AnalysisEngineProcessException {
        String fileName = null;
        if (jcas.getDocumentText().length() < minTextLength)
            return;


        IntervalST sentenceTree = new IntervalST();
        ArrayList<Sentence> sentenceList = new ArrayList<>();

        RecordRow baseRecordRow = MetaDataCommonFunctions.getMetaData(jcas);
        baseRecordRow.addCell("RUN_ID", version);

        classLogger.finest("Write annotations for doc: " + baseRecordRow.getStrByColumnName("DOC_NAME"));

        FSIterator<Annotation> it = jcas.getAnnotationIndex(Sentence.type).iterator();
        while (it.hasNext()) {
            Sentence thisSentence = (Sentence) it.next();
            sentenceList.add(thisSentence);
            sentenceTree.put(new Interval1D(thisSentence.getBegin(), thisSentence.getEnd()), sentenceList.size() - 1);
        }

        int total = saveAnnotations(jcas, baseRecordRow, sentenceList, sentenceTree, fileName);
        classLogger.finest("Total annotations: " + total);

//        ldao.insertRecords(snippetResultTable, annotations);
    }

    protected int saveAnnotations(JCas jcas, RecordRow baseRecordRow, ArrayList<Sentence> sentenceList, IntervalST sentenceTree, String fileName) {
        CAS cas = jcas.getCas();
        String docText = jcas.getDocumentText();
        int total = 0;
        for (String type : typeToSave)
            total += saveOneTypeAnnotation(cas, docText, type, baseRecordRow, sentenceList, sentenceTree, fileName);
        return total;
    }

    private int saveOneTypeAnnotation(CAS cas, String docText, String annotationType,
                                      RecordRow baseRecordRow,
                                      ArrayList<Sentence> sentenceList, IntervalST sentenceTree, String fileName) {
        int total = 0;
        Iterator<AnnotationFS> annoIter = CasUtil.iterator(cas, CasUtil.getType(cas, annotationType));
        String tableName = snippetTableName;
        while (annoIter.hasNext()) {
            Annotation thisAnnotation = (Annotation) annoIter.next();
            if (thisAnnotation.getType().getShortName().endsWith("SourceDocumentInformation")) {
                continue;
            }
            if (thisAnnotation instanceof Doc_Base) {
                tableName = docTableName;
            }
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
                String value = "";
                String typeName = thisAnnotation.getClass().getSimpleName();
                if (!annotationGetFeatures.containsKey(typeName))
                    annotationGetFeatures.put(typeName, new LinkedHashMap<>());
                Method getMethod = null;
                if (!annotationGetFeatures.get(typeName).containsKey(featureName)) {
                    try {
                        getMethod = thisAnnotation.getClass().getMethod(AnnotationOper.inferGetMethodName(featureName));
                        annotationGetFeatures.get(typeName).put(featureName, getMethod);
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                } else {
                    getMethod = annotationGetFeatures.get(typeName).get(featureName);
                }
                if (getMethod != null) {
                    try {
                        Object obj = getMethod.invoke(thisAnnotation);
                        if (obj instanceof FSArray) {
                            value = serilizeFSArray((FSArray) obj);
                        } else {
                            value = obj + "";
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
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
            } else if (annotationType.indexOf("Sentence") != -1) {
                Object sentenceIdObj = sentenceTree.get(new Interval1D(thisAnnotation.getBegin(), thisAnnotation.getEnd()));
                int sentenceId = -1;
                if (sentenceIdObj != null)
                    sentenceId = (int) sentenceIdObj;
                int snippetBegin;
                int snippetEnd = thisAnnotation.getEnd();
                if (sentenceId > 0)
                    snippetBegin = sentenceList.get(sentenceId - 1).getBegin();
                else
                    snippetBegin = thisAnnotation.getBegin();

                if (sentenceId < sentenceList.size() - 1)
                    snippetEnd = sentenceList.get(sentenceId + 1).getEnd();
                else
                    snippetEnd = thisAnnotation.getEnd();
                record.addCell("SNIPPET", docText.substring(snippetBegin, snippetEnd));
                record.addCell("SNIPPET_BEGIN", snippetBegin);
                record.addCell("SNIPPET_END", snippetEnd);
                record.addCell("BEGIN", thisAnnotation.getBegin() - snippetBegin);
                record.addCell("END", thisAnnotation.getEnd() - snippetBegin);
            } else {
                record.addCell("SNIPPET", thisAnnotation.getCoveredText());
                record.addCell("SNIPPET_BEGIN", thisAnnotation.getBegin());
                record.addCell("SNIPPET_END", thisAnnotation.getEnd());
                record.addCell("BEGIN", 0);
                record.addCell("END", thisAnnotation.getEnd() - thisAnnotation.getBegin());
            }
            try {
                classLogger.finest(dao.con.isClosed() + "");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            dao.insertRecord(tableName, record);
            total++;
        }
        return total;
    }

    private String serilizeFSArray(FSArray ary) {
        StringBuilder sb = new StringBuilder();
        int size = ary.size();
        String[] values = new String[size];
        ary.copyToArray(0, values, 0, size);
        for (FeatureStructure fs : ary) {
            List<Feature> features = fs.getType().getFeatures();
            for (Feature feature : features) {
                String domain = feature.getDomain().getShortName();
                if (domain.equals("AnnotationBase") || domain.equals("Annotation"))
                    continue;
                Type range = feature.getRange();
                if (!range.isPrimitive()) {
                    FeatureStructure child = fs.getFeatureValue(feature);
                    sb.append(child + "");
                } else {
                    sb.append("\t" + feature.getShortName() + ":" + fs.getFeatureValueAsString(feature) + "\n");
                }
            }

        }
        return sb.toString();
    }


    public void collectionProcessComplete() {
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

