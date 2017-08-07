package edu.utah.bmi.uima.ae;


import edu.utah.bmi.type.system.Concept;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.jcas.JCas;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;

import static edu.utah.bmi.uima.UIMATypeFunctions.checkTypeDomain;

/**
 * From the existence of one concept, conclude a document type annotation.
 * For instance, if a Fever annotation is found, then this document can be concluded as FeverPresentDoc.
 *
 * @author Jianlin Shi
 *         Created on 7/6/16.
 */
public class DocInferenceAnnotator extends JCasAnnotator_ImplBase {

    //    list document annotation type paired with concept types,    //
    //    "|" is used to differentiate document types,
    //    list the document types in priority order, the left ones have higher priority--the left document type will overwrite the right one if both exist
    //    e.g. "Doc_Yes:Concept1,Concept2|Doc_No:Concept3,Concept4"
    public static final String PARAM_CONCEPT_INFERENCES = "ConceptInference";

    //    if defaultDocType is not set, no doc type annotation will be added if no inference rule is matched
    public static final String PARAM_DEFAULT_DOC_TYPE = "DefaultDocType";

    public HashMap<Class, String> inferenceMap = new HashMap<>();

    private HashMap<String, Integer> definedDocTypePriority = new HashMap<>();

    private HashMap<String, Constructor<? extends Concept>> docTypeConstructorMap = new HashMap<>();

    private String defaultDocTypeName = null;

    private boolean inference = true;

    //    record current document answers, Key for questionid, value for document type
    private HashMap<Integer, String> currentDocTypes = new HashMap<>();

    public void initialize(UimaContext cont) {
        Object obj = cont.getConfigParameterValue(PARAM_DEFAULT_DOC_TYPE);
        if (obj != null) {
            defaultDocTypeName = ((String) obj).trim();
            if (defaultDocTypeName.length() == 0)
                defaultDocTypeName = null;
            defaultDocTypeName = checkTypeDomain(defaultDocTypeName);
        }

        String conceptInferences = (String) cont.getConfigParameterValue(PARAM_CONCEPT_INFERENCES);
        if (conceptInferences.trim().length() == 0)
            inference = false;
        int docPriority = 100;
        for (String inferencePair : conceptInferences.split("\\|")) {
            docPriority = 100;
            String[] pair = inferencePair.split(":");
            if (pair.length < 1) {
                System.err.println("Invalid doc inference rule: " + conceptInferences);
                inference = false;
            }
            try {
                String docTypeName = checkTypeDomain(pair[0].trim());
                buildConstructor(docTypeName);
                definedDocTypePriority.put(docTypeName, docPriority);
                docPriority--;
                for (String conceptName : pair[1].split(",")) {
                    conceptName = checkTypeDomain(conceptName.trim());
                    Class conceptType = Class.forName(conceptName).asSubclass(Concept.class);
                    inferenceMap.put(conceptType, docTypeName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (defaultDocTypeName != null) {
            buildConstructor(defaultDocTypeName);
            definedDocTypePriority.put(defaultDocTypeName, docPriority);
        }
    }

    private void buildConstructor(String docTypeName) {
        Class docType = null;
        try {
            docType = Class.forName(docTypeName).asSubclass(Concept.class);
            Constructor cc = docType.getConstructor(JCas.class, int.class, int.class);
            docTypeConstructorMap.put(docTypeName, cc);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        if (!inference)
            return;
        String currentDocTypeName = null;
        for (Class conceptType : inferenceMap.keySet()) {
            FSIndex annoIndex = jCas.getAnnotationIndex(conceptType);
            Iterator annoIter = annoIndex.iterator();
            if (annoIter.hasNext()) {
                String docTypeName = inferenceMap.get(conceptType);
                int thisPriority = definedDocTypePriority.get(docTypeName);
                if (currentDocTypeName == null || thisPriority > definedDocTypePriority.get(currentDocTypeName))
                    currentDocTypeName = docTypeName;
            }
        }
        if (currentDocTypeName == null && defaultDocTypeName != null)
            currentDocTypeName = defaultDocTypeName;
        if (currentDocTypeName != null) {
            Constructor<? extends Concept> cs = docTypeConstructorMap.get(currentDocTypeName);
            try {
                Concept docAnno = cs.newInstance(jCas, 0, 1);
                docAnno.addToIndexes();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }

    }

}
