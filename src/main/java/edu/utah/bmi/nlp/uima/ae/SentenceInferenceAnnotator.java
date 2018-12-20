package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.*;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.Sentence;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static edu.utah.bmi.nlp.core.DeterminantValueSet.checkNameSpace;

public class SentenceInferenceAnnotator extends JCasAnnotator_ImplBase implements RuleBasedAEInf {


    public static final String PARAM_RULE_STR = "RuleFileOrStr";

    public LinkedHashMap<String, ArrayList<ArrayList<Object>>> inferenceMap = new LinkedHashMap<>();

    private HashMap<String, HashMap<String, Method>> evidenceConceptGetFeatures = new LinkedHashMap<>();

    private HashMap<String, Class<? extends Annotation>> conceptClassMap = new HashMap<>();

    private HashMap<String, Constructor<? extends Concept>> sentenceTypeConstructorMap = new HashMap<>();

    private HashMap<Class, IntervalST<Annotation>> evidenceAnnotationTree = new HashMap<>();

    private IntervalST<Annotation> sentenceTree = new IntervalST<>();


    //    record current document answers, Key for topic, value for document type
    private HashMap<Integer, String> currentDocTypes = new HashMap<>();
    private LinkedHashMap<String, TypeDefinition> typeDefinitions;

    public void initialize(UimaContext cont) {

        String inferenceStr = (String) cont.getConfigParameterValue(PARAM_RULE_STR);
        parseRuleStr(inferenceStr);

    }

    private void parseRuleStr(String ruleStr) {
        IOUtil ioUtil = new IOUtil(ruleStr, true);
        for (ArrayList<String> initRow : ioUtil.getInitiations()) {
            String sentenceTypeName = initRow.get(2).trim();
            buildConstructor(sentenceTypeName);
        }
        for (ArrayList<String> row : ioUtil.getRuleCells()) {

            String topic = row.get(1).trim();
            if (!inferenceMap.containsKey(topic)) {
                inferenceMap.put(topic, new ArrayList<ArrayList<Object>>());
            }
            ArrayList<Object> inference = new ArrayList<>();
//			add doc conclusion type
            String sentenceTypeName = row.get(2).trim();
            inference.add(sentenceTypeName);
            buildConstructor(sentenceTypeName);
//			add evidences
            ArrayList<Class> evidences = new ArrayList<>();
            for (String evidenceTypeName : row.get(4).split(",")) {
                Class evidenceType = AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(evidenceTypeName));
                evidences.add(evidenceType);
            }
//			add feature reader
            String featureSetting = row.get(3).trim();
            inference.add(new DocInferenceFeatureReader(featureSetting, conceptClassMap, evidenceConceptGetFeatures, evidences));


            inference.add(evidences);
//			add rule id
            inference.add(row.get(0));
            inferenceMap.get(topic).add(inference);
        }
    }

    private void buildConstructor(String sentenceTypeName) {
        Class sentenceType;
        try {
            if (!sentenceTypeConstructorMap.containsKey(sentenceTypeName)) {
                sentenceType = Class.forName(DeterminantValueSet.checkNameSpace(sentenceTypeName)).asSubclass(Concept.class);
                Constructor cc = sentenceType.getConstructor(JCas.class, int.class, int.class);
                sentenceTypeConstructorMap.put(sentenceTypeName, cc);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        indexAnnotations(jCas);
        ArrayList<Annotation> sentenceAnnotations = null;
        for (String topic : inferenceMap.keySet()) {
            ArrayList<ArrayList<Object>> inferences = inferenceMap.get(topic);
            processTopic(topic, inferences, jCas);

        }
    }

    private void processTopic(String topic, ArrayList<ArrayList<Object>> inferences, JCas jCas) {
        HashSet<Integer> matchedSentenceOffsets = new HashSet<>();
        for (ArrayList<Object> inference : inferences) {
            ArrayList<Class> evidences = (ArrayList<Class>) inference.get(2);
            checkMatch(jCas, topic, evidences, (String) inference.get(0), matchedSentenceOffsets);

        }
    }


    //	Need test
    private boolean checkMatch(JCas jCas, String topic, ArrayList<Class> evidences, String conclusionSentenceType, HashSet<Integer> matchedSentenceOffsets) {
        Annotation sentenceAnnotation = null;
        FSIndex annoIndex = jCas.getAnnotationIndex(evidences.get(0));
        Iterator annoIter = annoIndex.iterator();
        boolean matched = false;
        while (annoIter.hasNext()) {
            Annotation evidenceAnno = (Annotation) annoIter.next();
            StringBuilder notes = new StringBuilder();
            notes.append(evidences.get(0).getSimpleName() + ": " + evidenceAnno.getCoveredText().replaceAll("[\\n\\r]", " "));
//            locate a sentence based on first evidence annotation
            sentenceAnnotation = sentenceTree.get(new Interval1D(evidenceAnno.getBegin(), evidenceAnno.getEnd()));
            if (matchedSentenceOffsets.contains(sentenceAnnotation.getBegin())) {
                continue;
            }
//            check if matching other evidences
            for (int i = 1; i < evidences.size(); i++) {
                Class evidenceType = evidences.get(i);
                evidenceAnno = hasAnnotation(jCas, evidenceType, sentenceAnnotation);
                if (evidenceAnno == null) {
                    sentenceAnnotation = null;
                    break;
                } else {
                    notes.append("\n");
                    notes.append(evidences.get(0).getSimpleName() + ": " + evidenceAnno.getCoveredText().replaceAll("[\\n\\r]", " "));
                }
            }
            if (sentenceAnnotation != null) {
                addSentenceAnnotation(jCas, topic, conclusionSentenceType, notes.toString(),
                        sentenceAnnotation.getBegin(), sentenceAnnotation.getEnd());
                matchedSentenceOffsets.add(sentenceAnnotation.getBegin());
                matched = true;
            }
        }
        return matched;
    }


    private void addSentenceAnnotation(JCas jCas, String topic, String sentenceTypeName, String evidencesString, int begin, int end) {
        if (sentenceTypeName != null) {
            Constructor<? extends Concept> cs = sentenceTypeConstructorMap.get(sentenceTypeName);
            try {
                Concept sentenceAnno = cs.newInstance(jCas, begin, end);
                sentenceAnno.setNote(evidencesString);
                sentenceAnno.setCategory(topic);
                sentenceAnno.addToIndexes();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }


    private Annotation hasAnnotation(JCas jCas, Class evidenceType, Annotation scopeAnnotation) {
        if (evidenceAnnotationTree.get(evidenceType) == null) {
            return null;
        }
        Annotation res = evidenceAnnotationTree.get(evidenceType).get(new Interval1D(scopeAnnotation.getBegin(), scopeAnnotation.getEnd()));
        return res;
    }

    private void indexAnnotations(JCas jCas) {
        evidenceAnnotationTree.clear();
        for (Class conceptType : evidenceAnnotationTree.keySet()) {
            FSIndex annoIndex = jCas.getAnnotationIndex(conceptType);
            Iterator annoIter = annoIndex.iterator();
            IntervalST<Annotation> intervalST = new IntervalST<>();
            while (annoIter.hasNext()) {
                Annotation anno = (Annotation) annoIter.next();
                intervalST.put(new Interval1D(anno.getBegin(), anno.getEnd()), anno);
            }
            evidenceAnnotationTree.put(conceptType, intervalST);
        }
        FSIndex annoIndex = jCas.getAnnotationIndex(Sentence.type);
        Iterator annoIter = annoIndex.iterator();
        sentenceTree = new IntervalST<>();
        while (annoIter.hasNext()) {
            Annotation anno = (Annotation) annoIter.next();
            sentenceTree.put(new Interval1D(anno.getBegin(), anno.getEnd()), anno);
        }
    }


    /**
     * Because implement a reinforced interface method (static is not reinforced), this is deprecated, just to
     * enable back-compatibility.
     *
     * @param ruleStr Rule file path or rule content string
     * @return Type name--Type definition map
     */
    @Deprecated
    public static LinkedHashMap<String, TypeDefinition> getTypeDefinitions(String ruleStr) {
        return new SentenceInferenceAnnotator().getTypeDefs(ruleStr);
    }


    public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
        LinkedHashMap<String, TypeDefinition> typeDefinitionLinkedHashMap = new LinkedHashMap<>();
        if (ruleStr.indexOf("|") != -1) {
            ruleStr = ruleStr.replaceAll("\\|", "\n");
        }
        IOUtil ioUtil = new IOUtil(ruleStr, true);
        ArrayList<ArrayList<String>> cells = ioUtil.getRuleCells();
        for (ArrayList<String> row : cells) {
            String sentenceTypeName = checkNameSpace(row.get(1).trim());
            String shortName = DeterminantValueSet.getShortName(sentenceTypeName);
            typeDefinitionLinkedHashMap.put(shortName, new TypeDefinition(sentenceTypeName, Concept.class.getCanonicalName()));
        }
        for (ArrayList<String> initRow : ioUtil.getInitiations()) {
            String sentenceTypeName = checkNameSpace(initRow.get(2).trim());
            String shortName = DeterminantValueSet.getShortName(sentenceTypeName);
            typeDefinitionLinkedHashMap.put(shortName, new TypeDefinition(sentenceTypeName, Concept.class.getCanonicalName()));
        }
        return typeDefinitionLinkedHashMap;
    }

}
