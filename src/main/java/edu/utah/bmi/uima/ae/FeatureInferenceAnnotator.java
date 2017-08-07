package edu.utah.bmi.uima.ae;


import edu.utah.bmi.core.Interval1D;
import edu.utah.bmi.core.IntervalST;
import edu.utah.bmi.type.system.Concept;
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

import static edu.utah.bmi.uima.UIMATypeFunctions.checkTypeDomain;

/**
 * From one type of annotation's feature values, infer another type of annotation.
 * For instance, a negated Concept can be inferred to PseudoConcept;
 * a historical Cocnept can be infered to PastConcept.
 *
 * @author Jianlin Shi
 *         Created on 7/6/16.
 */
public class FeatureInferenceAnnotator extends JCasAnnotator_ImplBase {

    //    from which concept the Conclusion annotation will be drawn on, the default is edu.utah.bmi.type.system.Concept.
    public static final String PARAM_EVIDENCE_CONCEPT = "EvidenceConcept";
    //    map conclusion type to feature value pairs.
    //    e.g. "Conclusion:feature1,value1&feature2,value2|Conclusion2:feature1,value3"
    public static final String PARAM_INFERENCES = "Inferences";
    public static final String PARAM_OVERWRITE_CONCEPT = "OverWriteConcept";
    public static final String PARAM_REMOVE_OVERLAP = "RemoveOverlap";

    public boolean overwrite = true, removeOverlap = true, inference = true;

    private Class<? extends Annotation> evidenceConcept;

    private HashMap<Method, HashMap<String, HashSet<Constructor<? extends Annotation>>>> inferenceMap = new HashMap<>();

    private HashMap<Constructor<? extends Annotation>, Integer> matchChecker = new HashMap<>();


    public void initialize(UimaContext cont) {
        Object obj = cont.getConfigParameterValue(PARAM_OVERWRITE_CONCEPT);
        if (obj != null && obj instanceof Boolean && (Boolean) obj == false)
            overwrite = false;
        obj = cont.getConfigParameterValue(PARAM_REMOVE_OVERLAP);
        if (obj != null && obj instanceof Boolean && (Boolean) obj == false)
            removeOverlap = false;
        try {
            initInferences(cont);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void initInferences(UimaContext cont) throws Exception {
        Object obj = cont.getConfigParameterValue(PARAM_EVIDENCE_CONCEPT);
        try {
            if (obj != null) {
                evidenceConcept = Class.forName(checkTypeDomain((String) obj)).asSubclass(Annotation.class);
            } else {
                evidenceConcept = Concept.class;
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        obj = cont.getConfigParameterValue(PARAM_INFERENCES);
        if (obj != null) {
            if (((String) obj).trim().length() == 0) {
                inference = false;
                return;
            }
            for (String inferenceRule : ((String) obj).split("\\|")) {
                String[] rule = inferenceRule.split(":");
                if (rule.length < 2) {
                    inference = false;
                    System.err.println("Invalid rule: " + inferenceRule);
                }
                String conclusionConcept = checkTypeDomain(rule[0].trim());
                System.out.println(conclusionConcept);
                Class cl = Class.forName(conclusionConcept).asSubclass(Annotation.class);
                Constructor<? extends Annotation> constructor = cl.getConstructor(JCas.class, int.class, int.class);
                String[] featureValuePairs = rule[1].split("&");
//              save the number of feature value pairs need to be matched, in case there are partial matches
                matchChecker.put(constructor, featureValuePairs.length);
                for (String featureValue : featureValuePairs) {
                    String[] nameValue = featureValue.split(",");
                    if (nameValue.length < 2)
                        System.err.println("Invalid configuration of rule: " + inferenceRule + "\n\tat: " + nameValue);
                    String name = nameValue[0].trim();
                    String value = nameValue[1].trim();
                    name = inferGetMethodName(name);
                    Method m = evidenceConcept.getMethod(name);
                    HashMap<String, HashSet<Constructor<? extends Annotation>>> valueMap = inferenceMap.getOrDefault(m, new HashMap<String, HashSet<Constructor<? extends Annotation>>>());
                    HashSet<Constructor<? extends Annotation>> constructors = valueMap.getOrDefault(value, new HashSet<Constructor<? extends Annotation>>(0));
                    constructors.add(constructor);
                    valueMap.put(value, constructors);
                    inferenceMap.put(m, valueMap);
                }
            }
        } else {
            System.err.println("Inference rule has not been defined.\nThe rule needs to map conclusion type to feature value pairs." +
                    "\ne.g. \"Conclusion:feature1,value1&feature2,value2|Conclusion2:feature1,value3\"");
            inference = false;
        }

    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        if (!inference)
            return;
        FSIndex annoIndex = jCas.getAnnotationIndex(evidenceConcept);
        Iterator annoIter = annoIndex.iterator();
        ArrayList<Annotation> scheduledRemove = new ArrayList<>();
        while (annoIter.hasNext()) {
            Annotation thisAnno = (Annotation) annoIter.next();
            try {
                checkInference(jCas, thisAnno, scheduledRemove);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
        if (overwrite && scheduledRemove.size() > 0) {
            for (Annotation removeAnno : scheduledRemove) {
                removeAnno.removeFromIndexes();
            }
        }
        if (removeOverlap) {
            removeOverlap(jCas);
        }
    }


    private void checkInference(JCas jCas, Annotation thisAnno, ArrayList<Annotation> scheduledRemove) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        HashMap<Constructor<? extends Annotation>, Integer> matchCounter = new HashMap<>();
        //  get any matched inferences
        for (Map.Entry<Method, HashMap<String, HashSet<Constructor<? extends Annotation>>>> entry : inferenceMap.entrySet()) {
            Method thisMethod = entry.getKey();
            HashMap<String, HashSet<Constructor<? extends Annotation>>> valuesMap = entry.getValue();
            Object thisValue = thisMethod.invoke(thisAnno);
            if (thisValue != null) {
                if (valuesMap.containsKey(thisValue.toString())) {
                    for (Constructor<? extends Annotation> constructor : valuesMap.get(thisValue.toString())) {
                        matchCounter.put(constructor, matchCounter.getOrDefault(constructor, 0) + 1);
                    }
                }
            }
        }
        // check if all the criteria are met
        for (Constructor<? extends Annotation> constructor : matchCounter.keySet()) {
            if (matchCounter.get(constructor) == matchChecker.get(constructor)) {
                Annotation conclusionAnno = constructor.newInstance(jCas, thisAnno.getBegin(), thisAnno.getEnd());
                conclusionAnno.addToIndexes();
                if (overwrite)
                    scheduledRemove.add(thisAnno);
            }
        }
    }


    private void removeOverlap(JCas jCas) {
        HashMap<String, IntervalST> typeSpanMap = new HashMap<>();
        FSIndex annoIndex = jCas.getAnnotationIndex();
        Iterator annoIter = annoIndex.iterator();
        while (annoIter.hasNext()) {
            Object obj = annoIter.next();
            String typeName = obj.getClass().getCanonicalName();
            IntervalST thisSpanTree = typeSpanMap.getOrDefault(typeName, new IntervalST());
            checkOverlap(thisSpanTree, (Annotation) obj);
        }
    }

    /**
     * Because FastNER and FastCNER may have overlapped matches.
     *
     * @param intervalTree
     * @param concept
     */
    private void checkOverlap(IntervalST intervalTree, Annotation concept) {
        Interval1D interval = new Interval1D(concept.getBegin(), concept.getEnd());
        Annotation overlapped = (Annotation) intervalTree.get(interval);
        if (overlapped != null && (overlapped.getEnd() != concept.getBegin() && concept.getEnd() != overlapped.getBegin())) {
            if ((overlapped.getEnd() - overlapped.getBegin()) < (concept.getEnd() - concept.getBegin())) {
                overlapped.removeFromIndexes();
                intervalTree.remove(new Interval1D(overlapped.getBegin(), overlapped.getEnd()));
                intervalTree.put(interval, concept);
            } else {
                concept.removeFromIndexes();
            }
        } else {
            intervalTree.put(interval, concept);
        }
    }


    /**
     * Base on Feature Name of the Type System, infer the get Method Name of the corresponding Annotation class
     *
     * @param featureName
     * @return
     */
    private String inferGetMethodName(String featureName) {
        String methodName = "";
        if (featureName.trim().length() == 0) {
            methodName = "";
        } else if (featureName.startsWith("get")) {
            methodName = featureName;
        } else if (Character.isUpperCase(featureName.charAt(0))) {
            methodName = "get" + featureName;
        } else {
            methodName = "get" + Character.toUpperCase(featureName.charAt(0)) + featureName.substring(1);
        }
        return methodName;
    }
}
