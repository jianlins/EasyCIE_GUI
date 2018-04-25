package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.easycie.CoordinateNERResults_AE;
import edu.utah.bmi.nlp.easycie.writer.XMIWritter_AE;
import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.type.system.SentenceOdd;
import edu.utah.bmi.nlp.uima.AdaptableUIMACPERunner;
import edu.utah.bmi.nlp.uima.AdaptableUIMACPETaskJCasRunner;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.nlp.uima.TemporalContext_AE_General;
import edu.utah.bmi.nlp.uima.ae.AnnotationPrinter;
import edu.utah.bmi.nlp.uima.ae.DocInferenceAnnotator;
import edu.utah.bmi.nlp.uima.ae.FeatureInferenceAnnotator;
import edu.utah.bmi.nlp.uima.reader.StringMetaReader;
import edu.utah.bmi.sectiondectector.SectionDetectorR_AE;
import edu.utah.bmi.simple.gui.controller.GUILogger;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.LogManager;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class FastDebugPipe extends RunEasyCIE {

    protected static FastDebugPipe fastDebugPipe = null;
    protected TasksFX tasks;
    protected String sectionType, rushType, cNERType, tNERType, contextType, dateType, featureInfType, mergedType, docInfType, metaStr;
    protected String inputStr = "";
    protected boolean fastNerCaseSensitive;
    public static final String exportXmiFile = "snippet_test.txt";
    public static String exportDir;
    public GUITask guitask;
    private JCas jCas;
    private AnalysisEngine aggregateAE;


    public static FastDebugPipe getInstance(TasksFX tasks, GUITask guiTask) {
        if (fastDebugPipe == null) {
            fastDebugPipe = new FastDebugPipe(tasks, guiTask);
        }
        return fastDebugPipe;
    }

    public FastDebugPipe() {

    }

    public FastDebugPipe(TasksFX tasks, GUITask guiTask) {
        this.tasks = tasks;
        this.guitask = guiTask;
        refreshPipe();
    }


    public void refreshPipe() {
        readDebugConfigs(tasks);
        initiate(tasks, "db");
        fastDebugPipe = this;
    }

    protected void initPipe(GUITask task) {

        String runId = uimaLogger.getRunid() + "";


        String defaultTypeDescriptor = "desc/type/All_Types";
//        JXTransformer jxTransformer;
        customTypeDescriptor = "desc/type/pipeline_" + annotator;

        if (new File(customTypeDescriptor + ".xml").exists())
            runner = new AdaptableUIMACPETaskJCasRunner(customTypeDescriptor, "./classes/");
        else
            runner = new AdaptableUIMACPETaskJCasRunner(defaultTypeDescriptor, "./classes/");
        runner.setLogger(uimaLogger);
        runner.setTask(task);

        initTypes(customTypeDescriptor);

        addAnalysisEngines();
        updateGUIMessage("Compile pipeline...");
        aggregateAE = runner.genAEs();
        jCas = runner.initJCas();

    }


    protected void initUIMALogger() {
        uimaLogger = new GUILogger(guitask, "target/generated-test-sources",
                "desc/type/pipeline_" + annotator);
        if (this.tasks.getTask("debug").getValue("log/ShowUimaViewer").toLowerCase().startsWith("t"))
            ((GUILogger) uimaLogger).setUIMAViewer(true);
        uimaLogger.logStartTime();
    }

    private void readDebugConfigs(TasksFX tasks) {
        updateGUIMessage("read pipeline configurations...");
        TaskFX debugConfig = tasks.getTask("debug");
        sectionType = debugConfig.getValue(ConfigKeys.sectionType).trim();
        rushType = debugConfig.getValue(ConfigKeys.rushType).trim();
        cNERType = debugConfig.getValue(ConfigKeys.cNERType).trim();
        tNERType = debugConfig.getValue(ConfigKeys.tNERType).trim();
        contextType = debugConfig.getValue(ConfigKeys.contextType).trim();
        dateType = debugConfig.getValue(ConfigKeys.dateType).trim();
        featureInfType = debugConfig.getValue(ConfigKeys.featureInfType).trim();
        docInfType = debugConfig.getValue(ConfigKeys.docInfType).trim();
        metaStr = debugConfig.getValue(ConfigKeys.metaStr).trim();
        if (metaStr.trim().length() == 0)
            metaStr = "DOC_ID,-1|DATASETID,-1|DOC_NAME,debug.dco|DATE,2108-01-01 00:00:00";
        exportDir = "target/generated-test-sources";

        exporttypes = rushType + (cNERType.length() > 0 ? "," + cNERType : "")
                + (tNERType.length() > 0 ? "," + tNERType : "")
                + (contextType.length() > 0 ? "," + contextType : "")
                + (featureInfType.length() > 0 ? "," + featureInfType : "")
                + (docInfType.length() > 0 ? "," + docInfType : "");

    }


    public void process(String inputStr, String metaStr) {
        AnnotationLogger.reset();
        jCas.reset();
        jCas.setDocumentText(inputStr);
        RecordRow recordRow = new RecordRow();
        if (metaStr != null)
            for (String metaInfor : metaStr.split("\\|")) {
                String[] pair = metaInfor.split(",");
                recordRow.addCell(pair[0], pair[1]);
            }
        String metaInfor = recordRow.serialize();
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jCas, 0, inputStr.length());
        srcDocInfo.setUri(metaInfor);
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(inputStr.length());
        srcDocInfo.setLastSegment(true);
        srcDocInfo.addToIndexes();
        try {
            aggregateAE.process(jCas);
        } catch (AnalysisEngineProcessException e) {
            e.printStackTrace();
        }
    }


    public void process(RecordRow recordRow, String textColumnName, String... excludeColumns) {
        AnnotationLogger.reset();
        String inputStr = recordRow.getStrByColumnName(textColumnName);
        jCas.reset();
        jCas.setDocumentText(inputStr);
        RecordRow newRecordRow = new RecordRow();
        HashSet<String> exclusions = new HashSet<>();
        exclusions.addAll(Arrays.asList(excludeColumns));
        for (Map.Entry<String, Object> entry : recordRow.getColumnNameValues().entrySet()) {
            if (!exclusions.contains(entry.getKey()) && !entry.getKey().equals(textColumnName) && entry.getValue() != null && entry.getValue().toString().length() > 0)
                newRecordRow.addCell(entry.getKey(), entry.getValue());
        }
        String metaInfor = newRecordRow.serialize();
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jCas, 0, inputStr.length());
        srcDocInfo.setUri(metaInfor);
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(inputStr.length());
        srcDocInfo.setLastSegment(true);
        srcDocInfo.addToIndexes();
        try {
            aggregateAE.process(jCas);
        } catch (AnalysisEngineProcessException e) {
            e.printStackTrace();
        }
    }

    public void showResults() {
        uimaLogger.logCompleteTime();
    }


    protected void addAnalysisEngines() {
        UpdateMessage("Add pipeline components...");
        if (sectionRule.length() > 0) {
            updateGUIMessage("add engine SectionDetectorR_AE");
            runner.addAnalysisEngine(SectionDetectorR_AE.class, new Object[]{SectionDetectorR_AE.PARAM_RULE_FILE_OR_STR, sectionRule});
            if (sectionType.length() > 0 && guiEnabled)
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{AnnotationLogger.PARAM_INDICATION_HEADER, "SectionDetector",
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by Setion Detector:",
                        AnnotationLogger.PARAM_TYPE_NAMES, sectionType});
        }
        if (rushRule.length() > 0) {
            updateGUIMessage("Add sentence splitter...");
            if (rushType.indexOf("Sentence") == -1)
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INSIDE_SECTIONS, includesections,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true});
            else
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true,
                        RuSH_AE.PARAM_INSIDE_SECTIONS, includesections,
                        RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, SentenceOdd.class.getCanonicalName()});
            if (rushType.length() > 0 && guiEnabled)
                runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{AnnotationLogger.PARAM_INDICATION_HEADER, "RuSH",
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by Rush (sentence segmenter):",
                        AnnotationLogger.PARAM_TYPE_NAMES, rushType});
        }

        if (fastCNERRule.length() > 0) {
            updateGUIMessage("Add fastcner...");
            runner.addAnalysisEngine(FastCNER_AE_General.class, new Object[]{FastCNER_AE_General.PARAM_RULE_FILE_OR_STR, fastCNERRule,
                    FastCNER_AE_General.PARAM_MARK_PSEUDO, false,
                    FastCNER_AE_General.PARAM_INCLUDE_SECTIONS, includesections
            });
            if (cNERType.length() > 0) {
                if (guiEnabled)
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
            updateGUIMessage("Add fastner...");
            runner.addAnalysisEngine(FastNER_AE_General.class, new Object[]{FastNER_AE_General.PARAM_RULE_FILE_OR_STR, fastNERRule,
                    FastNER_AE_General.PARAM_CASE_SENSITIVE, false, FastNER_AE_General.PARAM_CASE_SENSITIVE, fastNERCaseSensitive,
                    FastNER_AE_General.PARAM_INCLUDE_SECTIONS, includesections,
                    FastNER_AE_General.PARAM_MARK_PSEUDO, true});
            if (tNERType.length() > 0) {
                if (guiEnabled)
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
            updateGUIMessage("Add FastContext...");
            runner.addAnalysisEngine(FastContext_General_AE.class, new Object[]{FastContext_General_AE.PARAM_CONTEXT_RULES_STR, contextRule,
                    FastContext_General_AE.PARAM_AUTO_EXPAND_SCOPE, false,
                    FastContext_General_AE.PARAM_MARK_CLUE, true,
                    FastContext_General_AE.PARAM_DEBUG, true
            });
            if (contextType.length() > 0) {
                if (guiEnabled)
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

        if (dateRule.length() > 0) {
            updateGUIMessage("add Temporal Context detector...");
            runner.addAnalysisEngine(TemporalContext_AE_General.class, new Object[]{
                    TemporalContext_AE_General.PARAM_RULE_FILE_OR_STR, dateRule,
                    TemporalContext_AE_General.PARAM_MARK_PSEUDO, false,
                    TemporalContext_AE_General.PARAM_RECORD_DATE_COLUMN_NAME, "DATE",
                    TemporalContext_AE_General.PARAM_REFERENCE_DATE_COLUMN_NAME, "REF_DATE",
                    TemporalContext_AE_General.PARAM_INFER_ALL, inferAllTemporal,
                    TemporalContext_AE_General.PARAM_INTERVAL_DAYS, dayInterval,});
            if (dateType.length() > 0) {
                if (guiEnabled)
                    runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
                            AnnotationLogger.PARAM_INDICATION_HEADER, "TemporalContext_AE",
                            AnnotationLogger.PARAM_TYPE_NAMES, dateType,
                            AnnotationLogger.PARAM_INDICATION,
                            "After being processed by TemporalContext_AE:"});
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After TemporalContext_AE\n"});
            }
        }

        if (featureInfRule.length() > 0) {
            updateGUIMessage("Add feature inferencer...");
            runner.addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{FeatureInferenceAnnotator.PARAM_INFERENCE_STR, featureInfRule});
            if (featureInfType.length() > 0) {
                if (guiEnabled)
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
            updateGUIMessage("Add feature inferencer...");
            runner.addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_INFERENCE_STR, docInfRule});
            if (docInfType.length() > 0) {
                if (guiEnabled)
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
        uimaLogger.logString(msg);
    }
}
