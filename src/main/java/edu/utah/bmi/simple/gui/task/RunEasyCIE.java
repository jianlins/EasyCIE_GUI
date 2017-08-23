package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.ae.DocInferenceAnnotator;
import edu.utah.bmi.nlp.ae.FeatureInferenceAnnotator;
import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.easycie.CoordinateNERResults_AE;
import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.runner.RunPipe;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.SentenceOdd;
import edu.utah.bmi.nlp.uima.*;
import edu.utah.bmi.nlp.uima.ae.AnnotationPrinter;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.nlp.writer.BratWritter_AE;
import edu.utah.bmi.nlp.writer.EhostWriter_AE;
import edu.utah.bmi.nlp.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.writer.XMIWritter_AE;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunEasyCIE extends GUITask {
    public static boolean debug = false;
    protected String readDBConfigFile, writeConfigFileName, inputTableName, outputTableName,
            ehostDir, bratDir, xmiDir, annotator, datasetId;
    public boolean report = false, fastNerCaseSensitive = true;
    protected String rushRule = "", fastNERRule = "", fastCNERRule = "", contextRule = "",
            featureInfRule = "", docInfRule = "";
    public AdaptableUIMACPETaskRunner runner;
    protected DAO rdao, wdao;
    public boolean ehost = false, brat = false, xmi = false;
    protected String exporttypes;
    protected String customTypeDescriptor;

    public static void main(String[] args) {
        RunPipe runPipe = new RunPipe(args);
        runPipe.run();
    }

    public RunEasyCIE() {

    }


    public RunEasyCIE(TasksFX tasks) {
        initiate(tasks, "db");
    }

    public RunEasyCIE(TasksFX tasks, String paras) {
        if (paras == null || paras.length() == 0)
            initiate(tasks, "db");
        initiate(tasks, paras);
    }

    public void init(GUITask task, String annotator, String rushRule, String fastNERRule, String fastCNERRule, String contextRule,
                     String featureInfRule, String docInfRule, boolean report, boolean fastNerCaseSensitive,
                     String readDBConfigFile, String inputTableName, String datasetId, String writeConfigFileName,
                     String outputTableName, String ehostDir, String bratDir, String xmiDir, String exporttypes, String option) {
        this.annotator = annotator;
        this.rushRule = rushRule;
        this.fastNERRule = fastNERRule;
        this.fastCNERRule = fastCNERRule;
        this.contextRule = contextRule;
        this.featureInfRule = featureInfRule;
        this.docInfRule = docInfRule;
        this.report = report;
        this.fastNerCaseSensitive = fastNerCaseSensitive;
        this.readDBConfigFile = readDBConfigFile;
        this.inputTableName = inputTableName;
        this.datasetId = datasetId;
        this.writeConfigFileName = writeConfigFileName;
        this.outputTableName = outputTableName;
        this.ehostDir = ehostDir;
        this.bratDir = bratDir;
        this.xmiDir = xmiDir;
        this.exporttypes = exporttypes.replaceAll("\\s+","");
        switch (option) {
            case "ehost":
                ehost = true;
                break;
            case "brat":
                brat = true;
                break;
            case "xmi":
                xmi = true;
                break;
            default:
                ehost = false;
                brat = false;
                xmi = false;
        }
        if (ehostDir == null || ehostDir.length() == 0)
            ehost = false;
        if (bratDir == null || bratDir.length() == 0)
            brat = false;
        if (xmiDir == null || xmiDir.length() == 0)
            xmi = false;
        initPipe(task, readDBConfigFile, datasetId, annotator);
    }

    protected void initiate(TasksFX tasks, String option) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);
        fastNERRule = config.getValue(ConfigKeys.tRuleFile);
        fastCNERRule = config.getValue(ConfigKeys.cRuleFile);
        contextRule = config.getValue(ConfigKeys.contextRule);
        featureInfRule = config.getValue(ConfigKeys.featureInfRule);
        docInfRule = config.getValue(ConfigKeys.docInfRule);

        String reportString = config.getValue(ConfigKeys.reportPreannotating);
        report = reportString.length() > 0 && (reportString.charAt(0) == 't' || reportString.charAt(0) == 'T' || reportString.charAt(0) == '1');
        reportString = config.getValue(ConfigKeys.fastNerCaseSensitive);
        fastNerCaseSensitive = reportString.length() > 0 && (reportString.charAt(0) == 't' || reportString.charAt(0) == 'T' || reportString.charAt(0) == '1');


        config = tasks.getTask("settings");
        readDBConfigFile = config.getValue(ConfigKeys.readDBConfigFile);
        inputTableName = config.getValue(ConfigKeys.inputTableName);
        datasetId = config.getValue(ConfigKeys.datasetId);
        writeConfigFileName = config.getValue(ConfigKeys.writeConfigFileName);
        outputTableName = config.getValue(ConfigKeys.outputTableName);
        rushRule = config.getValue(ConfigKeys.rushRule);


        TaskFX exportConfig = tasks.getTask("export");
        ehostDir = exportConfig.getValue(ConfigKeys.outputEhostDir);
        bratDir = exportConfig.getValue(ConfigKeys.outputBratDir);
        xmiDir = exportConfig.getValue(ConfigKeys.outputXMIDir);
        exporttypes = exportConfig.getValue(ConfigKeys.exportTypes);

        switch (option) {
            case "ehost":
                ehost = true;
                break;
            case "brat":
                brat = true;
                break;
            case "xmi":
                xmi = true;
                break;
            default:
                ehost = false;
                brat = false;
                xmi = false;
        }

        initPipe(this, readDBConfigFile, datasetId, annotator);

    }

    @Override
    protected Object call() throws Exception {
        runner.run();
        return null;
    }


    protected void initPipe(GUITask task, String readDBConfigFile, String datasetId, String annotator) {
        rdao = new DAO(new File(readDBConfigFile), true, false);
        if (writeConfigFileName.equals(readDBConfigFile)) {
            wdao = rdao;
        } else {
            File writeFile = new File(writeConfigFileName);
            if (writeFile.exists() && writeFile.isFile() &&
                    (writeConfigFileName.toLowerCase().endsWith(".xml")) ||
                    (writeConfigFileName.toLowerCase().endsWith(".json")))
                wdao = new DAO(new File(writeConfigFileName));
        }
        UIMALogger logger = addLogger(wdao, annotator);
        logger.logStartTime();
        String runId = logger.getRunid() + "";


        String defaultTypeDescriptor = "desc/type/All_Types";
//        JXTransformer jxTransformer;
        customTypeDescriptor = "desc/type/pipeline_" + annotator;

        if (new File(customTypeDescriptor + ".xml").exists())
            runner = new AdaptableUIMACPETaskRunner(customTypeDescriptor, "./classes/");
        else
            runner = new AdaptableUIMACPETaskRunner(defaultTypeDescriptor, "./classes/");
        runner.setLogger(logger);
        runner.setTask(task);

        initTypes(customTypeDescriptor);
        addReader(readDBConfigFile, datasetId);
        addAnalysisEngines(runner);
//        SQLWriterCasConsumer.debug=true;

        addWriter(runId, annotator);
    }


    protected UIMALogger addLogger(DAO dao, String annotator) {
        if (debug)
            return new ConsoleLogger();
        else
            return new NLPDBLogger(dao, "LOG", "RUN_ID", annotator);
    }

    /**
     * Read through all the annotations, iterate all the types and features,
     * check to see if the type descriptor has included all of them, if not create
     * the missed types and features
     */
    protected void initTypes(String customTypeDescriptor) {


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

    public void setReader(Class readerClass, Object[] configurations) {
        runner.setCollectionReader(readerClass, configurations);
    }

    public void addReader(String readDBConfigFile, String datasetId) {
        SQLTextReader.dao = rdao;
        runner.setCollectionReader(SQLTextReader.class, new Object[]{SQLTextReader.PARAM_DB_CONFIG_FILE, readDBConfigFile,
                SQLTextReader.PARAM_DATASET_ID, datasetId,
                SQLTextReader.PARAM_DOC_TABLE_NAME, inputTableName,
                SQLTextReader.PARAM_QUERY_SQL_NAME, "masterInputQuery",
                SQLTextReader.PARAM_COUNT_SQL_NAME, "masterCountQuery",
                SQLTextReader.PARAM_DOC_COLUMN_NAME, "TEXT"});
    }


    public void addWriter(String runId, String annotator) {
        File output = new File(writeConfigFileName);
        try {
            if (!output.exists()) {
                FileUtils.forceMkdir(output);
            } else if (output.isFile()) {
                output = new File("data/output");
                if (!output.exists())
                    FileUtils.forceMkdir(output.getParentFile());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (ehost)
            runner.addAnalysisEngine(EhostWriter_AE.class,
                    new Object[]{EhostWriter_AE.PARAM_OUTPUTDIR, new File(output, "ehost").getAbsolutePath(),
                            EhostWriter_AE.PARAM_ANNOTATOR, annotator,
                            EhostWriter_AE.PARAM_UIMATYPES, exporttypes});
        if (brat)
            runner.addAnalysisEngine(BratWritter_AE.class,
                    new Object[]{BratWritter_AE.PARAM_OUTPUTDIR, new File(output, "brat").getAbsolutePath(),
                            BratWritter_AE.PARAM_ANNOTATOR, annotator,
                            BratWritter_AE.PARAM_UIMATYPES, exporttypes});
        if (xmi)
            runner.addAnalysisEngine(XMIWritter_AE.class,
                    new Object[]{XMIWritter_AE.PARAM_OUTPUTDIR, new File(output, "xmi").getAbsolutePath(),
                            "Annotator", annotator,
                            XMIWritter_AE.PARAM_UIMATYPES, exporttypes});

        if (!(ehost || brat || xmi)) {
            SQLWriterCasConsumer.dao = wdao;
            if (!wdao.checkExits("checkAnnotatorExist", annotator)) {
                wdao.insertRecord("ANNOTATORS", new RecordRow(annotator));
            }
            runner.addAnalysisEngine(SQLWriterCasConsumer.class, new Object[]{
                    SQLWriterCasConsumer.PARAM_SQLFILE, writeConfigFileName,
                    SQLWriterCasConsumer.PARAM_TABLENAME, outputTableName,
                    SQLWriterCasConsumer.PARAM_ANNOTATOR, annotator,
                    SQLWriterCasConsumer.PARAM_VERSION, runId,
                    SQLWriterCasConsumer.PARAM_WRITE_CONCEPT,exporttypes,
                    SQLWriterCasConsumer.PARAM_OVERWRITETABLE, false, SQLWriterCasConsumer.PARAM_BATCHSIZE, 150});
        }

    }

    protected void addAnalysisEngines(AdaptableUIMACPETaskRunner runner) {
        if (rushRule.length() > 0) {
            if (debug)
                System.out.println("add engine RuSH_AE");
            if (exporttypes == null || exporttypes.indexOf("Sentence") == -1)
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true});
            else
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true,
                        RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, SentenceOdd.class.getCanonicalName()});
//			if (debug)
//				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
//						DeterminantValueSet.defaultNameSpace + "Sentence"});
        }

        if (fastCNERRule.length() > 0) {
            if (debug)
                System.out.println("add engine FastCNER_AE_General");
            runner.addAnalysisEngine(FastCNER_AE_General.class, new Object[]{FastCNER_AE_General.PARAM_RULE_FILE_OR_STR, fastCNERRule,
                    FastCNER_AE_General.PARAM_MARK_PSEUDO, false,
            });
            if (debug) {
                System.out.println("add engine FastNER_AE_Diff_SP_Concepts");
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept"});
            }
        }

        if (fastNERRule.length() > 0) {
            if (debug)
                System.out.println("add engine FastNER_AE_General");
            runner.addAnalysisEngine(FastNER_AE_General.class, new Object[]{FastNER_AE_General.PARAM_RULE_FILE_OR_STR, fastNERRule,
                    FastNER_AE_General.PARAM_CASE_SENSITIVE, false,
                    FastNER_AE_General.PARAM_MARK_PSEUDO, true});
        }

        if (debug)
            System.out.println("add engine CoordinateNERResults_AE");
        runner.addAnalysisEngine(CoordinateNERResults_AE.class, null);


//        System.out.println("Read Context rules from " + contextRule);
        if (contextRule.length() > 0) {
            if (debug)
                System.out.println("add engine FastContext_General_AE ");
            runner.addAnalysisEngine(FastContext_General_AE.class, new Object[]{FastContext_General_AE.PARAM_CONTEXT_RULES_STR, contextRule,
                    FastContext_General_AE.PARAM_AUTO_EXPAND_SCOPE,false});
        }

        if (featureInfRule.length() > 0)
            runner.addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{FeatureInferenceAnnotator.PARAM_RULE_FILE_OR_STR, featureInfRule});
        if (docInfRule.length() > 0)
            runner.addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_RULE_FILE_OR_STR, docInfRule});
    }
}
