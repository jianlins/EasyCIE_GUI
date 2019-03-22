package edu.utah.bmi.nlp.easycie.writer;

import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import edu.utah.bmi.nlp.uima.common.UIMATypeFunctions;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Jianlin Shi
 * Created on 7/9/16.
 */
public class BratWritter_AE extends XMIWritter_AE {

    private HashMap<Class, LinkedHashSet<Method>> typeMethods;
    private HashMap<String, HashSet<String>> attributeToConcepts;
    private HashMap<String, HashSet<String>> attributeToValues;

    public void initialize(UimaContext cont) {
        String includeTypes = baseInit(cont, "data/output/ehost", "uima");
        attributeToConcepts = new HashMap<>();
        attributeToValues = new HashMap<>();
    }

    @Override
    public void process(JCas jCas) {

        String fileName = readFileIDName(jCas, nameWId);
        ArrayList<String> bratAnnotations = new ArrayList<>();
        int i = 0, attrId = 0;
        FSIndex annoIndex = jCas.getAnnotationIndex(Annotation.type);
        Iterator annoIter = annoIndex.iterator();
        while (annoIter.hasNext()) {
            Annotation con = (Annotation) annoIter.next();
            if (!typeMethods.containsKey(con.getClass())) {
                typeMethods.put(con.getClass(),new LinkedHashSet<>());
                AnnotationOper.getMethods(con.getClass(), typeMethods.get(con.getClass()));
            }
            LinkedHashSet<Method> methods = typeMethods.get(con.getClass());

//            System.out.println(con.getType().getName() + "\t" + con.getCoveredText());
            bratAnnotations.add("T" + i + "\t" + con.getType().getShortName() + " " + con.getBegin() + " " + con.getEnd()
                    + "\t" + con.getCoveredText().replaceAll("[\n\r]", " "));
            attrId = readAttributes(con, methods, bratAnnotations, attrId, "T" + i);
            i++;
        }

        File outputFile = new File(outputDirectory, fileName + ".ann");
        try {
            FileUtils.writeLines(outputFile, bratAnnotations);
            FileUtils.writeStringToFile(new File(outputDirectory, fileName + ".txt"), jCas.getDocumentText(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private int readAttributes(Annotation con, LinkedHashSet<Method> methods, ArrayList<String> bratAnnotations, int attrId, String conceptID) {
        for (Method method : methods) {
            String attribute = method.getName().substring(3);
            String value = "";
            try {
                Object valueObj = (String) method.invoke(con);
                if (valueObj == null)
                    continue;
                if (valueObj instanceof FSArray) {
                    value = serilizeFSArray((FSArray) valueObj);
                } else {
                    value = valueObj + "";
                }
                if (value.trim().length() == 0)
                    continue;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            bratAnnotations.add("A" + attrId + "\t" + attribute + " " + conceptID + " " + value);
            addAttributeValue(con.getClass().getSimpleName(), attribute, value);
            attrId++;
        }
        return attrId;
    }

    private void addAttributeValue(String concept, String attribute, String value) {
        if (!attributeToConcepts.containsKey(attribute)) {
            attributeToConcepts.put(attribute, new HashSet<>());
            attributeToValues.put(attribute, new HashSet<>());
        }
        attributeToConcepts.get(attribute).add(concept);
        attributeToValues.get(attribute).add(value);
    }

    public void collectionProcessComplete() {
        // no default behavior
        File configFile = new File(outputDirectory, "annotation.conf");
        StringBuilder config = new StringBuilder();
        if (!configFile.exists()) {
            config.append("[entities]\n");
            for (Map.Entry<Class, LinkedHashSet<Method>> entry : typeMethods.entrySet()) {
                config.append(entry.getKey().getSimpleName() + "\n");
            }
            config.append("[features]\n");
            for (Map.Entry<String, HashSet<String>> attribute : attributeToValues.entrySet()) {
                if (attribute.getValue().size() > 0 && !(attribute.getValue().size() == 1 && attribute.getValue().contains(""))) {
                    String attributeName = attribute.getKey();
                    config.append(attributeName);
                    config.append("\tArg:");
                    config.append(serializeHashSet(attributeToConcepts.get(attributeName)));
                    String values = serializeHashSet(attribute.getValue());
                    if (values.length() > 0) {
                        config.append(",\tValue:");
                        config.append(values);
                    }
                    config.append("\n");
                }
            }
            config.append("[relations]\n[events]");
            try {
                FileUtils.writeStringToFile(configFile, config.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private String serializeHashSet(HashSet<String> set) {
        if (set.size() == 1) {
            if (set.contains(""))
                return "";
            else
                for (String ele : set)
                    return ele;
        }
        StringBuilder output = new StringBuilder();
        for (String ele : set) {
            if (ele.indexOf(":") != -1 || ele.indexOf(",") != -1)
//                illegal value for brat
                return "";
            output.append(ele);
            output.append("|");
        }
        output.deleteCharAt(output.length() - 1);
        return output.toString();
    }


}
