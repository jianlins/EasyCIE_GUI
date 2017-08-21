package edu.utah.bmi.simple.gui.core;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import edu.utah.bmi.nlp.type.system.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class AnnotationLogger extends JCasAnnotator_ImplBase {
    public static final String PARAM_TYPE_NAMES = "TypeNames";
    public static final String PARAM_INDICATION = "Indication";
    public static StringBuilder sb = new StringBuilder();
    public static ArrayList<RecordRow> records = new ArrayList<>();
    private String printTypeNames;
    private String indication, annotator;


    public AnnotationLogger() {
    }

    public static void reset() {
        sb.setLength(0);
        records.clear();
    }

    public void initialize(UimaContext cont) {
        this.printTypeNames = "";
        Object obj = cont.getConfigParameterValue(PARAM_TYPE_NAMES);
        if (obj != null && obj instanceof String) {
            this.printTypeNames = DeterminantValueSet.checkNameSpace((String) obj);
        }

        obj = cont.getConfigParameterValue(PARAM_INDICATION);
        if (obj != null && obj instanceof String) {
            this.indication = (String) obj;
        }
        annotator = "debugger";

    }

    public void process(JCas jCas) throws AnalysisEngineProcessException {

        IntervalST sentenceTree = new IntervalST();
        ArrayList<Sentence> sentenceList = new ArrayList<>();
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

    private void addIntroductionRecordRow(RecordRow baseRecordRow, ArrayList<RecordRow> records, String indication) {
        RecordRow record = baseRecordRow.clone();
        record.addCell("TYPE", "")
                .addCell("TEXT", indication)
                .addCell("SNIPPET", indication)
                .addCell("SNIPPET_BEGIN", 0)
                .addCell("SNIPPET_END", indication.length())
                .addCell("BEGIN", 0)
                .addCell("END", indication.length());
        records.add(record);
    }

    private void indexSentences(JCas jCas, ArrayList<Sentence> sentenceList, IntervalST sentenceTree) {
        FSIterator<Annotation> it = jCas.getAnnotationIndex(Sentence.type).iterator();
        while (it.hasNext()) {
            Sentence thisSentence = (Sentence) it.next();
            sentenceList.add(thisSentence);
            sentenceTree.put(new Interval1D(thisSentence.getBegin(), thisSentence.getEnd()), sentenceList.size() - 1);
        }
    }

    private void saveOneTypeAnnotation(CAS cas, String docText, String annotationType,
                                       RecordRow baseRecordRow, ArrayList<RecordRow> annotations,
                                       ArrayList<Sentence> sentenceList, IntervalST sentenceTree) {

        Iterator<AnnotationFS> annoIter = CasUtil.iterator(cas, CasUtil.getType(cas, annotationType));
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
                } else {
                    int contBegin = thisAnnotation.getBegin() - 50;
                    int contEnd = thisAnnotation.getEnd() + 50;
                    if (contBegin < 0)
                        contBegin = 0;
                    if (contEnd > docText.length()) {
                        contEnd = docText.length();
                    }
                    sentence = docText.substring(contBegin, contEnd);
                    record.addCell("NOTE", record.getValueByColumnName("NOTE") + "<missed sentence>");
                    sentenceBegin = contBegin;
                }
                record.addCell("SNIPPET", sentence);
                record.addCell("SNIPPET_BEGIN", sentenceBegin);
                record.addCell("SNIPPET_END", sentenceBegin + sentence.length());
                record.addCell("BEGIN", thisAnnotation.getBegin() - sentenceBegin);
                record.addCell("END", thisAnnotation.getEnd() - sentenceBegin);
            } else {
                record.addCell("SNIPPET", thisAnnotation.getCoveredText());
                record.addCell("SNIPPET_BEGIN", thisAnnotation.getBegin());
                record.addCell("SNIPPET_END", thisAnnotation.getEnd());
                record.addCell("BEGIN", 0);
                record.addCell("END", thisAnnotation.getEnd() - thisAnnotation.getBegin());
            }
            annotations.add(record);
        }
    }

}
