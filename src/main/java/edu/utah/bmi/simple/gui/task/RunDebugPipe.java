package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.ae.DocInferenceAnnotator;
import edu.utah.bmi.nlp.ae.FeatureInferenceAnnotator;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.easycie.CoordinateNERResults_AE;
import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.runner.RunPipe;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.type.system.SentenceOdd;
import edu.utah.bmi.nlp.uima.AdaptableUIMACPERunner;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.nlp.uima.reader.StringMetaReader;
import edu.utah.bmi.nlp.writer.XMIWritter_AE;
import edu.utah.bmi.simple.gui.controller.GUILogger;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunDebugPipe extends RunPipe {
    protected String rushType, cNERType, tNERType, contextType, featureInfType, docInfType, inputStr, annotator;
    protected boolean fastNerCaseSensitive;
    public static final String exportXmiFile = "snippet_test.txt";
    public static String exportDir;
    public GUITask task;

    public RunDebugPipe() {

    }

    public RunDebugPipe(GUITask task, String inputStr, String annotator, boolean fastNerCaseSensitive,
                        String rushRule, String fastNERRule, String fastCNERRule, String contextRule,
                        String featureInfRule, String docInfRule,
                        String exportDir, String exportTypes,
                        String rushType, String cNERType, String tNERType,
                        String contextType, String featureInfType, String docInfType) {
        this.task = task;
        initPipe(inputStr, annotator, fastNerCaseSensitive,
                rushRule, fastNERRule, fastCNERRule, contextRule, featureInfRule,
                docInfRule, exportDir, exportTypes, rushType, cNERType, tNERType, contextType,
                featureInfType, docInfType);
    }

    public void run() {
        AnnotationLogger.reset();
        runner.run();
    }


    public void initPipe(String inputStr, String annotator, boolean fastNerCaseSensitive,
                         String rushRule, String fastNERRule, String fastCNERRule, String contextRule,
                         String featureInfRule, String docInfRule,
                         String exportDir, String exportTypes,
                         String rushType, String cNERType, String tNERType,
                         String contextType, String featureInfType, String docInfType) {
        this.inputStr = inputStr;

        this.annotator = annotator;
        this.rushRule = rushRule;
        this.fastNERRule = fastNERRule;
        this.fastCNERRule = fastCNERRule;
        this.contextRule = contextRule;
        this.featureInfRule = featureInfRule;
        this.docInfRule = docInfRule;

        this.fastNerCaseSensitive = fastNerCaseSensitive;
        this.exportDir = exportDir;

        this.rushType = rushType;
        this.cNERType = cNERType;
        this.tNERType = tNERType;
        this.contextType = contextType;
        this.featureInfType = featureInfType;
        this.docInfType = docInfType;

        this.exporttypes = exportTypes;


        this.rushRule = rushRule;
        this.fastNERRule = fastNERRule;
        this.fastCNERRule = fastCNERRule;
        this.contextRule = contextRule;
        this.featureInfRule = featureInfRule;
        this.docInfRule = docInfRule;
        xmi = true;
        ehost = false;
        brat = false;
        initPipe(task);
    }

    protected void initPipe(GUITask task) {
        String defaultTypeDescriptor = "desc/type/All_Types";
//        JXTransformer jxTransformer;
        customTypeDescriptor = "desc/type/pipeline_" + annotator;

        if (!new File(customTypeDescriptor + ".xml").exists())
            customTypeDescriptor = defaultTypeDescriptor;
        runner = new AdaptableUIMACPERunner(customTypeDescriptor, "./classes/");
        UIMALogger logger = new GUILogger(task, exportDir, customTypeDescriptor + ".xml");
        logger.logStartTime();
        runner.setLogger(logger);
        initTypes(customTypeDescriptor);
        addReader(inputStr, exportXmiFile);
        addAnalysisEngines(runner);
//        SQLWriterCasConsumer.debug=true;

        addWriter("-1", annotator);
    }


    /**
     * Read through all the annotations, iterate all the types and features,
     * check to see if the type descriptor has included all of them, if not create
     * the missed types and features
     */
    protected void initTypes(String customTypeDescriptor) {
        UpdateMessage("Initiate types...");
        if (fastNERRule.length() > 0)
            runner.addConceptTypes(FastNER_AE_General.getTypeDefinitions(fastNERRule, false).values());

        if (fastCNERRule.length() > 0)
            runner.addConceptTypes(FastCNER_AE_General.getTypeDefinitions(fastCNERRule, true).values());

        if (contextRule.length() > 0)
            runner.addConceptTypes(FastContext_General_AE.getTypeDefinitions(contextRule, false).values());

        if (featureInfRule.length() > 0)
            runner.addConceptTypes(FeatureInferenceAnnotator.getTypeDefinitions(featureInfRule).values());
        if (docInfRule.length() > 0)
            runner.addConceptTypes(DocInferenceAnnotator.getTypeDefinitions(docInfRule).values());

        runner.reInitTypeSystem(customTypeDescriptor);
    }

    public void initTypes(Collection<TypeDefinition> typeDefinitions) {
        runner.addConceptTypes(typeDefinitions);
        runner.reInitTypeSystem(customTypeDescriptor);
    }


    public void addReader(String inputStr, String fileName) {
        UpdateMessage("Add String reader...");
        runner.setCollectionReader(StringMetaReader.class, new Object[]{StringMetaReader.PARAM_INPUT, inputStr,
                StringMetaReader.PARAM_META, "DOC_ID,-1|DATASETID,-1|DOC_NAME," + fileName});
    }


    public void addWriter(String runId, String annotator) {
        File output = new File(exportDir);
        try {
            if (!output.exists()) {
                FileUtils.forceMkdir(output);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (xmi)
            runner.addAnalysisEngine(XMIWritter_AE.class,
                    new Object[]{XMIWritter_AE.PARAM_OUTPUTDIR, output.getAbsolutePath(),
                            "Annotator", annotator,
                            XMIWritter_AE.PARAM_UIMATYPES, exporttypes});

    }


    protected void addAnalysisEngines(AdaptableUIMACPERunner runner) {
        UpdateMessage("Add pipeline components...");
        if (rushRule.length() > 0) {
            if (rushType.indexOf("Sentence") == -1)
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true});
            else
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true,
                        RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, SentenceOdd.class.getCanonicalName()});
            if (rushType.length() > 0)
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{AnnotationLogger.PARAM_INDICATION_HEADER,"RuSH",
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processeb by Rush (sentence segmenter):",
                        AnnotationLogger.PARAM_TYPE_NAMES, rushType});
        }

        if (fastCNERRule.length() > 0) {
            runner.addAnalysisEngine(FastCNER_AE_General.class, new Object[]{FastCNER_AE_General.PARAM_RULE_FILE_OR_STR, fastCNERRule,
                    FastCNER_AE_General.PARAM_MARK_PSEUDO, false,
            });
            if (cNERType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER,"FastCNER",
                        AnnotationLogger.PARAM_TYPE_NAMES, cNERType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FastCNER (character based rule NER):"});
            }
        }

        if (fastNERRule.length() > 0) {
            runner.addAnalysisEngine(FastNER_AE_General.class, new Object[]{FastNER_AE_General.PARAM_RULE_FILE_OR_STR, fastNERRule,
                    FastNER_AE_General.PARAM_CASE_SENSITIVE, false,
                    FastNER_AE_General.PARAM_MARK_PSEUDO, true});
            if (tNERType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER,"FastNER",
                        AnnotationLogger.PARAM_TYPE_NAMES, tNERType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FastNER (token based rule NER):"});
            }
        }

        runner.addAnalysisEngine(CoordinateNERResults_AE.class, null);


//        System.out.println("Read Context rules from " + contextRule);
        if (contextRule.length() > 0) {
            runner.addAnalysisEngine(FastContext_General_AE.class, new Object[]{FastContext_General_AE.PARAM_CONTEXT_RULES_STR, contextRule,
                    FastContext_General_AE.PARAM_AUTO_EXPAND_SCOPE, false,
                    FastContext_General_AE.PARAM_MARK_CLUE, true,
                    FastContext_General_AE.PARAM_DEBUG, true
            });
            if (contextType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER,"FastContext",
                        AnnotationLogger.PARAM_TYPE_NAMES, contextType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FastContext (context detector):"});
            }
        }

        if (featureInfRule.length() > 0) {
            runner.addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{FeatureInferenceAnnotator.PARAM_RULE_FILE_OR_STR, featureInfRule});
            if (contextType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER,"FeatureInferenceAnnotator",
                        AnnotationLogger.PARAM_TYPE_NAMES, featureInfType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FeatureInferenceAnnotator:"});
            }
        }
        if (docInfRule.length() > 0) {
            runner.addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_RULE_FILE_OR_STR, docInfRule});
            if (contextType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER,"DocInferenceAnnotator",
                        AnnotationLogger.PARAM_TYPE_NAMES, docInfType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by DocInferenceAnnotator:"});
            }
        }
    }

    protected void UpdateMessage(String msg) {
        task.updateGUIMessage(msg);
    }
}
