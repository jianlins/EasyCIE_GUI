package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.type.system.SentenceOdd;
import edu.utah.bmi.nlp.easycie.CoordinateNERResults_AE;
import edu.utah.bmi.nlp.uima.ae.AnnotationPrinter;
import edu.utah.bmi.nlp.uima.ae.DocInferenceAnnotator;
import edu.utah.bmi.nlp.uima.ae.FeatureInferenceAnnotator;
import edu.utah.bmi.nlp.uima.reader.StringMetaReader;
import edu.utah.bmi.nlp.easycie.writer.XMIWritter_AE;
import edu.utah.bmi.sectiondectector.SectionDetectorR_AE;
import edu.utah.bmi.simple.gui.controller.GUILogger;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunDebugPipe extends RunEasyCIE {

    protected TasksFX tasks;
    protected String sectionType, rushType, cNERType, tNERType, contextType, featureInfType, mergedType, docInfType;
    protected String inputStr="";
    protected boolean fastNerCaseSensitive;
    public static final String exportXmiFile = "snippet_test.txt";
    public static String exportDir;
    public GUITask task;

    public RunDebugPipe() {

    }

    public RunDebugPipe(TasksFX tasks) {
        this.tasks = tasks;
        readDebugConfigs(tasks);
        initiate(tasks, "db");
    }


    protected void initUIMALogger() {
        uimaLogger = new GUILogger(this, "target/generated-test-sources",
                "desc/type/pipeline_" + annotator);
        uimaLogger.logStartTime();
    }

    public RunDebugPipe(TasksFX tasks, String paras) {
        if (paras == null || paras.length() == 0)
            initiate(tasks, "db");
        readDebugConfigs(tasks);
        initiate(tasks, paras);
    }

    private void readDebugConfigs(TasksFX tasks) {
        TaskFX debugConfig = tasks.getTask("debug");
        sectionType = debugConfig.getValue(ConfigKeys.sectionType).trim();
        rushType = debugConfig.getValue(ConfigKeys.rushType).trim();
        cNERType = debugConfig.getValue(ConfigKeys.cNERType).trim();
        tNERType = debugConfig.getValue(ConfigKeys.tNERType).trim();
        contextType = debugConfig.getValue(ConfigKeys.contextType).trim();
        featureInfType = debugConfig.getValue(ConfigKeys.featureInfType).trim();
        docInfType = debugConfig.getValue(ConfigKeys.docInfType).trim();
        exportDir = "target/generated-test-sources";

        exporttypes = rushType + (cNERType.length() > 0 ? "," + cNERType : "")
                + (tNERType.length() > 0 ? "," + tNERType : "")
                + (contextType.length() > 0 ? "," + contextType : "")
                + (featureInfType.length() > 0 ? "," + featureInfType : "")
                + (docInfType.length() > 0 ? "," + docInfType : "");

    }


    @Override
    protected Object call() throws Exception {
        AnnotationLogger.reset();
        if (guiEnabled)
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    AnnotationLogger.reset();
                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Pipeline Debugger");
                    dialog.setHeaderText("Enter your snippet string here:");
                    TextArea textField = new TextArea();
                    dialog.setHeight(400);
                    dialog.setResizable(true);
                    dialog.getDialogPane().setContent(textField);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
                    textField.setEditable(true);
                    textField.setWrapText(true);

                    Optional<String> result = dialog.showAndWait();
                    String entered = "";

                    if (result.isPresent()) {
                        entered = textField.getText();
                    }
                    inputStr = entered;
                    if (entered.trim().length() > 0) {
                        addReader();
//                        initiate(tasks, "xmi");
                        updateGUIMessage("Execute pipeline...");
                        runner.run();
                    } else {
                        updateGUIMessage("No string entered.");
                        updateGUIProgress(1, 1);
                    }

                }
            });
        return null;
    }


    public void execute() {
        AnnotationLogger.reset();
        runner.run();
    }

    public void addReader(String inputStr) {
        this.inputStr=inputStr;
        addReader();
    }
    public void addReader() {
        UpdateMessage("Add String reader...");
        if (!inputStr.endsWith(".xml"))
            runner.setCollectionReader(StringMetaReader.class, new Object[]{StringMetaReader.PARAM_INPUT, inputStr,
                    StringMetaReader.PARAM_META, "DOC_ID,-1|DATASETID,-1|DOC_NAME,debug.dco"});
    }


    public void addWriter(String runId) {
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


    //    protected void addAnalysisEngines(AdaptableUIMACPETaskRunner runner) {
//
//        if (sectionRule.length() > 0) {
//            logger.finer("add engine SectionDetectorR_AE");
//            runner.addAnalysisEngine(SectionDetectorR_AE.class, new Object[]{SectionDetectorR_AE.PARAM_RULE_FILE_OR_STR, sectionRule});
//            if (sectionType.length()>0)
//                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
//                        SectionHeader.class.getCanonicalName(),AnnotationPrinter.PARAM_INDICATION,"After sectiondetector"});
//        }
//        if (rushRule.length() > 0) {
//            logger.finer("add engine RuSH_AE");
//            if (exporttypes == null || exporttypes.indexOf("Sentence") == -1)
//                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
//                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true});
//            else
//                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
//                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true,
//                        RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, SentenceOdd.class.getCanonicalName()});
////			if (debug)
////				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
////						DeterminantValueSet.defaultNameSpace + "Sentence"});
//            if (rushType.length() > 0)
//                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{AnnotationLogger.PARAM_INDICATION_HEADER,"RuSH",
//                        AnnotationLogger.PARAM_INDICATION,
//                        "After being processed by Rush (sentence segmenter):",
//                        AnnotationLogger.PARAM_TYPE_NAMES, rushType});
//        }
//
//        if (fastCNERRule.length() > 0) {
//            logger.finer("add engine FastCNER_AE_General");
//            runner.addAnalysisEngine(FastCNER_AE_General.class, new Object[]{FastCNER_AE_General.PARAM_RULE_FILE_OR_STR, fastCNERRule,
//                    FastCNER_AE_General.PARAM_MARK_PSEUDO, false});
//            if (cNERType.length() > 0) {
//                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
//                        AnnotationLogger.PARAM_INDICATION_HEADER,"FastCNER",
//                        AnnotationLogger.PARAM_TYPE_NAMES, cNERType,
//                        AnnotationLogger.PARAM_INDICATION,
//                        "After being processed by FastCNER (character based rule NER):"});
//            }
//        }
//
//        if (fastNERRule.length() > 0) {
//            logger.finer("add engine FastNER_AE_General");
//            runner.addAnalysisEngine(FastNER_AE_General.class, new Object[]{FastNER_AE_General.PARAM_RULE_FILE_OR_STR, fastNERRule,
//                    FastNER_AE_General.PARAM_MARK_PSEUDO, true, FastNER_AE_General.PARAM_CASE_SENSITIVE, fastNERCaseSensitive,
//                    FastNER_AE_General.PARAM_INCLUDE_SECTIONS, includesections});
//            if (tNERType.length() > 0) {
//                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
//                        AnnotationLogger.PARAM_INDICATION_HEADER,"FastNER",
//                        AnnotationLogger.PARAM_TYPE_NAMES, tNERType,
//                        AnnotationLogger.PARAM_INDICATION,
//                        "After being processed by FastNER (token based rule NER):"});
//            }
//        }
//
//        logger.finer("add engine CoordinateNERResults_AE");
//        runner.addAnalysisEngine(CoordinateNERResults_AE.class, null);
//
//
////        System.out.println("Read Context rules from " + contextRule);
//        if (contextRule.length() > 0) {
//            logger.finer("add engine FastContext_General_AE ");
//            runner.addAnalysisEngine(FastContext_General_AE.class, new Object[]{
//                    FastContext_General_AE.PARAM_CONTEXT_RULES_STR, contextRule});
//            if (logger.isLoggable(Level.FINER)) {
//                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
//                        DeterminantValueSet.defaultNameSpace + "Concept",
//                        AnnotationPrinter.PARAM_INDICATION, "After FastContext_General_AE\n"});
//            }
//        }
//
//        if (featureInfRule.length() > 0) {
//            logger.finer("add engine FeatureInferenceAnnotator");
//
//            runner.addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{
//                    FeatureInferenceAnnotator.PARAM_INFERENCE_STR, featureInfRule});
//            if (logger.isLoggable(Level.FINER)) {
//                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
//                        DeterminantValueSet.defaultNameSpace + "Concept",
//                        AnnotationPrinter.PARAM_INDICATION, "After FeatureInferenceAnnotator\n"});
//            }
//        }
//
//        if (featureMergerRule.length() > 0) {
//            logger.finer("add engine FeatureInferenceAnnotator");
//
//            runner.addAnalysisEngine(AnnotationFeatureMergerAnnotator.class, new Object[]{
//                    AnnotationFeatureMergerAnnotator.PARAM_INFERENCE_STR, featureMergerRule,
//                    AnnotationFeatureMergerAnnotator.PARAM_IN_SITU, false});
//            if (logger.isLoggable(Level.FINER)) {
//                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
//                        DeterminantValueSet.defaultNameSpace + "Concept",
//                        AnnotationPrinter.PARAM_INDICATION, "After AnnotationFeatureMergerAnnotator\n"});
//            }
//        }
//
//        if (docInfRule.length() > 0) {
//            logger.finer("add engine DocInferenceAnnotator");
//            runner.addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_INFERENCE_STR, docInfRule});
//        }
//        if (logger.isLoggable(Level.FINER)) {
//            runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME, Doc_Base.class.getCanonicalName(),
//                    AnnotationPrinter.PARAM_INDICATION, "After DocInferenceAnnotator\n"});
//        }
//    }
    protected void addAnalysisEngines() {
        UpdateMessage("Add pipeline components...");
        if (sectionRule.length() > 0) {
            logger.finer("add engine SectionDetectorR_AE");
            runner.addAnalysisEngine(SectionDetectorR_AE.class, new Object[]{SectionDetectorR_AE.PARAM_RULE_FILE_OR_STR, sectionRule});
            if (sectionType.length() > 0)
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{AnnotationLogger.PARAM_INDICATION_HEADER, "SectionDetector",
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by Setion Detector:",
                        AnnotationLogger.PARAM_TYPE_NAMES, sectionType});
        }
        if (rushRule.length() > 0) {
            if (rushType.indexOf("Sentence") == -1)
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true});
            else
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true,
                        RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, SentenceOdd.class.getCanonicalName()});
            if (rushType.length() > 0)
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{AnnotationLogger.PARAM_INDICATION_HEADER, "RuSH",
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by Rush (sentence segmenter):",
                        AnnotationLogger.PARAM_TYPE_NAMES, rushType});
        }

        if (fastCNERRule.length() > 0) {
            runner.addAnalysisEngine(FastCNER_AE_General.class, new Object[]{FastCNER_AE_General.PARAM_RULE_FILE_OR_STR, fastCNERRule,
                    FastCNER_AE_General.PARAM_MARK_PSEUDO, false,
            });
            if (cNERType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER, "FastCNER",
                        AnnotationLogger.PARAM_TYPE_NAMES, cNERType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FastCNER (character based rule NER):"});
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FastCNER\n"});
            }
        }

        if (fastNERRule.length() > 0) {
            runner.addAnalysisEngine(FastNER_AE_General.class, new Object[]{FastNER_AE_General.PARAM_RULE_FILE_OR_STR, fastNERRule,
                    FastNER_AE_General.PARAM_CASE_SENSITIVE, false,
                    FastNER_AE_General.PARAM_MARK_PSEUDO, true});
            if (tNERType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER, "FastNER",
                        AnnotationLogger.PARAM_TYPE_NAMES, tNERType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FastNER (token based rule NER):"});
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FastNER\n"});
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
                        AnnotationLogger.PARAM_INDICATION_HEADER, "FastContext",
                        AnnotationLogger.PARAM_TYPE_NAMES, contextType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FastContext (context detector):"});
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FastContext_General_AE\n"});
            }
        }

        if (featureInfRule.length() > 0) {
            runner.addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{FeatureInferenceAnnotator.PARAM_INFERENCE_STR, featureInfRule});
            if (contextType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER, "FeatureInferenceAnnotator",
                        AnnotationLogger.PARAM_TYPE_NAMES, featureInfType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by FeatureInferenceAnnotator:"});
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FeatureInferenceAnnotator\n"});
            }
        }
        if (docInfRule.length() > 0) {
            runner.addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_INFERENCE_STR, docInfRule});
            if (contextType.length() > 0) {
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER, "DocInferenceAnnotator",
                        AnnotationLogger.PARAM_TYPE_NAMES, docInfType,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by DocInferenceAnnotator:"});
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        Doc_Base.class.getCanonicalName(),
                        AnnotationPrinter.PARAM_INDICATION, "After DocInferenceAnnotator\n"});
            }
        }
    }

    protected void UpdateMessage(String msg) {
        updateGUIMessage(msg);
    }
}
