package edu.utah.bmi.simple.gui.core;

import edu.emory.mathcs.backport.java.util.Collections;
import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import edu.utah.bmi.nlp.type.system.Sentence;
import edu.utah.bmi.nlp.type.system.Stbegin;
import edu.utah.bmi.nlp.type.system.Stend;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.cas.*;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class AnnotationLogger extends JCasAnnotator_ImplBase {
    public static final String PARAM_TYPE_NAMES = "TypeNames";
    public static final String PARAM_INDICATION = "Indication";
    public static final String PARAM_INDICATION_HEADER = "IndicationHeader";
    public static final String PARAM_SENTENCE_TYPE = "SentenceType";
    public static StringBuilder sb = new StringBuilder();
    public static ArrayList<RecordRow> records = new ArrayList<>();
    protected static HashMap<Class, HashMap<String, Method>> getMethodsMap = new HashMap<>();
    private String printTypeNames;
    private String indication, annotator, header;
    private Class<? extends Annotation> sentenceCls = Sentence.class;


    public AnnotationLogger() {
    }

    public static void reset() {
        sb.setLength(0);
        records.clear();
        getMethodsMap.clear();
    }

    public void initialize(UimaContext cont) {
        this.printTypeNames = "";
        Object obj = cont.getConfigParameterValue(PARAM_TYPE_NAMES);
        if (obj != null && obj instanceof String) {
            this.printTypeNames = (String) obj;
        }

        obj = cont.getConfigParameterValue(PARAM_INDICATION);
        if (obj != null && obj instanceof String) {
            this.indication = (String) obj;
        }

        obj = cont.getConfigParameterValue(PARAM_SENTENCE_TYPE);
        if (obj != null && obj instanceof String) {
            try {
                sentenceCls = Class.forName(DeterminantValueSet.checkNameSpace((String) obj)).asSubclass(Annotation.class);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        obj = cont.getConfigParameterValue(PARAM_INDICATION_HEADER);
        if (obj != null && obj instanceof String) {
            this.header = (String) obj;
        }
        annotator = "debugger";

    }

    public void process(JCas jCas) {
        if (printTypeNames.trim().length() > 0) {
            IntervalST sentenceTree = new IntervalST();
            ArrayList<Annotation> sentenceList = new ArrayList<>();
            indexSentences(jCas, sentenceList, sentenceTree);
            String docText = jCas.getDocumentText();

            RecordRow baseRecordRow = new RecordRow().addCell("DOC_NAME", "SNIPPET_TEST")
                    .addCell("RUN_ID", 0).addCell("ANNOTATOR", annotator);
            addIntroductionRecordRow(baseRecordRow, records, indication);
            CAS cas = jCas.getCas();
            sb.append(indication + "\n");

            for (String typeName : printTypeNames.split(",")) {
                typeName = typeName.trim();
                if (typeName.length() == 0)
                    continue;
                typeName = DeterminantValueSet.checkNameSpace(typeName);
                saveOneTypeAnnotation(cas, docText, typeName, baseRecordRow, records, sentenceList, sentenceTree);

                Type type = CasUtil.getAnnotationType(cas, typeName);

                Collection<AnnotationFS> annotations = CasUtil.select(cas, type);
                sb.append(" Here is a list of annotation '" + typeName + "':\n");
                for (AnnotationFS annotation : annotations) {
                    sb.append(annotation.toString() + "   Covered Text: \"" + annotation.getCoveredText() + "\"\n");
                }
            }
        }
    }

    private void addIntroductionRecordRow(RecordRow baseRecordRow, ArrayList<RecordRow> records, String indication) {
        RecordRow record = baseRecordRow.clone();
        record.addCell("ID", header)
                .addCell("TYPE", "")
                .addCell("TEXT", indication)
                .addCell("SNIPPET", indication)
                .addCell("SNIPPET_BEGIN", 0)
                .addCell("SNIPPET_END", indication.length())
                .addCell("BEGIN", 0)
                .addCell("END", indication.length());
        records.add(record);
    }

    private void indexSentences(JCas jCas, ArrayList<Annotation> sentenceList, IntervalST sentenceTree) {
        Iterator<? extends Annotation> it = JCasUtil.iterator(jCas, sentenceCls);
        while (it.hasNext()) {
            Annotation thisSentence = it.next();
            sentenceList.add(thisSentence);
            sentenceTree.put(new Interval1D(thisSentence.getBegin(), thisSentence.getEnd()), sentenceList.size() - 1);
        }
    }

    private void saveOneTypeAnnotation(CAS cas, String docText, String annotationType,
                                       RecordRow baseRecordRow, ArrayList<RecordRow> annotations,
                                       ArrayList<Annotation> sentenceList, IntervalST sentenceTree) {

        Iterator<AnnotationFS> annoIter = CasUtil.iterator(cas, CasUtil.getType(cas, annotationType));
        int docLength = docText.length();
        if (annotationType.indexOf("Sentence") != -1) {
            while (annoIter.hasNext()) {
                Annotation thisAnnotation = (Annotation) annoIter.next();
                Object sentenceIdObj = sentenceTree.get(new Interval1D(thisAnnotation.getBegin(), thisAnnotation.getEnd()));
                int sentenceId = -1;
                if (sentenceIdObj != null)
                    sentenceId = (int) sentenceIdObj;
                int snippetBegin;
                int snippetEnd;
//              Because of looking at sentences, its context should be wider than itself, so extend the snippet to one
//              sentence more before and after it.
                if (sentenceId > 0)
                    snippetBegin = sentenceList.get(sentenceId - 1).getBegin();
                else
                    snippetBegin = thisAnnotation.getBegin();

                if (sentenceId < sentenceList.size() - 1)
                    snippetEnd = sentenceList.get(sentenceId + 1).getEnd();
                else
                    snippetEnd = thisAnnotation.getEnd();
                RecordRow record = baseRecordRow.clone();
                record.addCell("TYPE", thisAnnotation.getType().getShortName());
                record.addCell("TEXT", thisAnnotation.getCoveredText());
                record.addCell("FEATURES", "");
                record.addCell("SNIPPET", docText.substring(snippetBegin, snippetEnd));
                record.addCell("SNIPPET_BEGIN", snippetBegin);
                record.addCell("SNIPPET_END", snippetEnd);
                record.addCell("BEGIN", thisAnnotation.getBegin() - snippetBegin);
                record.addCell("END", thisAnnotation.getEnd() - snippetBegin);
                record.addCell("ABBEGIN", thisAnnotation.getBegin());
                annotations.add(record);
            }
        } else {
            while (annoIter.hasNext()) {
                Annotation thisAnnotation = (Annotation) annoIter.next();
                RecordRow record = baseRecordRow.clone();
                record.addCell("TYPE", thisAnnotation.getType().getShortName());
                record.addCell("TEXT", thisAnnotation.getCoveredText());


                StringBuilder sb = new StringBuilder();
                for (Feature feature : thisAnnotation.getType().getFeatures()) {
                    String domain = feature.getDomain().getShortName();
                    if (domain.equals("AnnotationBase") || domain.equals("Annotation"))
                        continue;
                    String featureName = feature.getShortName();
                    Object value=AnnotationOper.getFeatureValue(featureName, thisAnnotation);
//					System.out.println(featureName + ":v" + value);

                    switch (featureName) {
                        case "Annotator":
                            record.addCell("ANNOTATOR", this.annotator);
                        default:
                            if (value != null && (value+"").trim().length() > 0) {
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

                    Annotation sentenceAnno;
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
                        record.addCell("SNIPPET", sentence);
                        record.addCell("SNIPPET_BEGIN", sentenceBegin);
                        record.addCell("SNIPPET_END", sentenceBegin + sentence.length());
                        record.addCell("BEGIN", thisAnnotation.getBegin() - sentenceBegin);
                        record.addCell("END", thisAnnotation.getEnd() - sentenceBegin);
                    } else {
                        genFixWindowRecordRow(record, docText, docLength, thisAnnotation.getBegin(), thisAnnotation.getEnd(),
                                50, record.getValueByColumnName("NOTE") + "<missed sentence>");
                    }
                } else {
                    genFixWindowRecordRow(record, docText, docLength, thisAnnotation.getBegin(), thisAnnotation.getEnd(),
                            8, null);
                }
                record.addCell("ABBEGIN", thisAnnotation.getBegin());
                annotations.add(record);
            }
        }
    }

    private RecordRow genFixWindowRecordRow(RecordRow record, String docText, int docLength, int begin, int end, int windowSize, String note) {

        int contBegin = begin - windowSize;
        int contEnd = end + windowSize;
        if (contBegin < 0)
            contBegin = 0;
        if (contEnd > docText.length()) {
            contEnd = docText.length();
        }
        String snippet = docText.substring(contBegin, contEnd);
        if (note != null && note.trim().length() > 0)
            record.addCell("NOTE", record.getValueByColumnName("NOTE") + "<missed sentence>");
        record.addCell("SNIPPET", snippet);
        record.addCell("SNIPPET_BEGIN", contBegin);
        record.addCell("SNIPPET_END", contBegin + snippet.length());
        record.addCell("BEGIN", begin - contBegin);
        record.addCell("END", end - contBegin);
        return record;
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

    public static ArrayList<RecordRow> getRecords(boolean... sorted) {
        if (sorted.length > 0 && sorted[0] == true) {
            LinkedHashMap<RecordRow, ArrayList<RecordRow>> groupedRecords = new LinkedHashMap<>();
            RecordRow currentGroup = null;
            for (RecordRow record : records) {
                if (record.getValueByColumnName("ID") != null || currentGroup == null) {
                    currentGroup = record;
                    groupedRecords.put(currentGroup, new ArrayList<>());
                } else
                    groupedRecords.get(currentGroup).add(record);
            }

            ArrayList<RecordRow> sortedRecords = new ArrayList<>();
            for (RecordRow header : groupedRecords.keySet()) {
                sortedRecords.add(header);
                ArrayList<RecordRow> oneGroupRecord = groupedRecords.get(header);
                Collections.sort(oneGroupRecord, (Comparator<RecordRow>) (lhs, rhs) -> {
                    //     return 1 if rhs should be before lhs
                    //     return -1 if lhs should be before rhs
                    //     return 0 otherwise

                    int lbegin = (int) lhs.getValueByColumnName("ABBEGIN");
                    int rbegin = (int) rhs.getValueByColumnName("ABBEGIN");
                    int lend = (int) lhs.getValueByColumnName("END") - (int) lhs.getValueByColumnName("BEGIN");
                    int rend = (int) rhs.getValueByColumnName("END") - (int) lhs.getValueByColumnName("BEGIN");
                    if (rbegin < lbegin) {
                        return 1;
                    } else if (rbegin == lbegin) {
                        if (rend < lend)
                            return 1;
                        else if (rend == lend)
                            return 0;
                    }
                    return -1;
                });
                sortedRecords.addAll(oneGroupRecord);
            }
            records = sortedRecords;

        }
        return records;
    }

}
