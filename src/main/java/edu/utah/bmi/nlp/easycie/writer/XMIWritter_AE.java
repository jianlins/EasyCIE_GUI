package edu.utah.bmi.nlp.easycie.writer;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/**
 * @author Jianlin Shi
 * Created on 7/9/16.
 */
public class XMIWritter_AE extends JCasAnnotator_ImplBase {

    public static final String PARAM_OUTPUTDIR = "OutputDirectory";

    public static final String PARAM_UIMATYPES = "UIMATypes";

    public static final String PARAM_ANNOTATOR = "Annotator";

    public static final String PARAM_NAME_W_ID = "NameWithId";

    protected String annotator;

    protected HashMap<Class, Boolean> uimaTypes = new HashMap<>();

    public File outputDirectory;

    protected boolean nameWId = false;

    public void initialize(UimaContext cont) {
        String includeTypes = baseInit(cont, "data/output/xmi", "uima");
        uimaTypes = new HashMap<>();
        if (includeTypes.length() > 0) {
            String[] types = includeTypes.split(",");
            Arrays.asList(types).forEach(e -> {
                try {
                    if (e.trim().length() > 0)
                        uimaTypes.put(Class.forName(DeterminantValueSet.checkNameSpace(e.trim())), true);
                } catch (ClassNotFoundException e1) {
                    e1.printStackTrace();
                }
            });
        }
    }

    protected String baseInit(UimaContext cont, String defaultDir, String defaultAnnotator) {
        outputDirectory = new File(readConfigureString(cont, PARAM_OUTPUTDIR, defaultDir));
        System.out.println("UIMA annotations will be exported to: " + outputDirectory.getAbsolutePath());
        Object tmpObj = cont.getConfigParameterValue(PARAM_NAME_W_ID);
        if (tmpObj != null && tmpObj instanceof Boolean) {
            nameWId = (Boolean) tmpObj;
        }
        if (!outputDirectory.exists())
            try {
                FileUtils.forceMkdir(outputDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        annotator = readConfigureString(cont, PARAM_ANNOTATOR, defaultAnnotator);
        String includeTypes = readConfigureString(cont, PARAM_UIMATYPES, "");
        includeTypes = includeTypes.replaceAll("\\s+", "");
        return includeTypes;

    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {

        String FileName = readFileIDName(jCas,nameWId);
        if (uimaTypes.size() > 0 && !uimaTypes.containsKey("")) {
            Iterator<Annotation> iterator = JCasUtil.iterator(jCas, Annotation.class);
            while (iterator.hasNext()) {
                Annotation anno = iterator.next();
                if (!included(anno))
                    anno.removeFromIndexes();
                if (anno instanceof Concept) {
                    ((Concept) anno).setAnnotator(annotator);
                }
            }
        }

        try {
            CasIOUtil.writeXmi(jCas, new File(outputDirectory, FileName + ".xmi"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected boolean included(Annotation anno) {
        Class thisTypeClass = anno.getClass();
        if (uimaTypes.containsKey(thisTypeClass)) {
            return uimaTypes.get(thisTypeClass);
        } else {
            for (Class type : uimaTypes.keySet()) {
                if (type.isInstance(anno)) {
                    uimaTypes.put(thisTypeClass, true);
                    return true;
                }
            }
            uimaTypes.put(thisTypeClass, false);
        }
        return false;
    }

    public static String readFileIDName(JCas jCas, boolean includeId) {
        RecordRow baseRecordRow = new RecordRow();
        FSIterator it = jCas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
        if (it.hasNext()) {
            SourceDocumentInformation e = (SourceDocumentInformation) it.next();
            String serializedString = new File(e.getUri()).getName();
            baseRecordRow.deserialize(serializedString);

        }
        String fileName = (includeId ? baseRecordRow.getValueByColumnName("DOC_ID") + "_" : "") +
                baseRecordRow.getValueByColumnName("DOC_NAME");
        return fileName;
    }

    public static String readConfigureString(UimaContext cont, String parameterName, String defaultValue) {
        Object tmpObj = cont.getConfigParameterValue(parameterName);
        String value;
        if (tmpObj == null) {
            value = defaultValue;
        } else {
            value = (tmpObj + "").trim();
        }
        return value;
    }
}
