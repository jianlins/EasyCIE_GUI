package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.fastcner.FastCNER;
import edu.utah.bmi.fastcner.demo.FastCNER_AE_General;
import edu.utah.bmi.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.fastner.FastNER;
import edu.utah.bmi.fastner.demo.FastNER_AE_General;
import edu.utah.bmi.rush.uima.RuSHTestAE_General;
import edu.utah.bmi.rush.uima.RuSH_AE;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.uima.*;
import edu.utah.bmi.uima.ae.DocInferenceAnnotator;
import edu.utah.bmi.uima.ae.FeatureInferenceAnnotator;
import edu.utah.bmi.uima.loggers.ConsoleLogger;
import edu.utah.bmi.uima.writer.XMIWritter_AE;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.TreeSet;


/**
 * Created by u0876964 on 9/19/16.
 */
public class RunEasyCIE extends AdaptableUIMACPETaskRunner {
    protected String inputDB, inputTable, outputDB, outputTable, ruleFile, cRuleFile, ehostDir, bratDir, xmiDir, annotator;
    protected String featureInfEvidenceConcept, featureInfRule, docInfRule, docInfDefault;
    protected boolean overwrite = false;
    protected String[] filters;
    public static boolean debug = false;
    public boolean report = false, fastNerCaseSensitive = true;
    private String defaultDomain = "edu.utah.bmi.type.system.";


    /**
     * By default, it will write to database rather than export to other format xmls.
     *
     * @param tasks
     */
    public RunEasyCIE(TasksFX tasks) {
        initiate(tasks, "db");
    }

    public RunEasyCIE(TasksFX tasks, String paras) {
        if (paras == null || paras.length() == 0)
            initiate(tasks, "db");
        initiate(tasks, paras);
    }

    private void initiate(TasksFX tasks, String option) {
        updateMessage("Initiate configurations..");


        TaskFX config = tasks.getTask(ConfigKeys.maintask);

        annotator = config.getValue(ConfigKeys.annotator);
        ruleFile = config.getValue(ConfigKeys.ruleFile);
        cRuleFile = config.getValue(ConfigKeys.cRuleFile);
        String reportString = config.getValue(ConfigKeys.reportPreannotating);
        report = reportString.length() > 0 && (reportString.charAt(0) == 't' || reportString.charAt(0) == 'T' || reportString.charAt(0) == '1');
        reportString = config.getValue(ConfigKeys.fastNerCaseSensitive);
        fastNerCaseSensitive = reportString.length() > 0 && (reportString.charAt(0) == 't' || reportString.charAt(0) == 'T' || reportString.charAt(0) == '1');

        featureInfEvidenceConcept = config.getValue(ConfigKeys.featureInfEvidenceConcept);
        featureInfRule = config.getValue(ConfigKeys.featureInfRule);
        docInfRule = config.getValue(ConfigKeys.docInfRule);
        docInfDefault = config.getValue(ConfigKeys.docInfDefault);

        config = tasks.getTask("settings");
        inputDB = config.getValue(ConfigKeys.corpusDBFile);
        inputTable = config.getValue(ConfigKeys.corpusDBTable);
        outputDB = config.getValue(ConfigKeys.outputDBFile);
        outputTable = config.getValue(ConfigKeys.outputDBTable);
        TaskFX exportConfig = tasks.getTask("export");
        ehostDir = exportConfig.getValue(ConfigKeys.outputEhostDir);
        bratDir = exportConfig.getValue(ConfigKeys.outputBratDir);
        xmiDir = exportConfig.getValue(ConfigKeys.outputXMIDir);


        String typeDescriptor = "desc/type/customized";
        if (!new File(typeDescriptor + ".xml").exists()) {
            typeDescriptor = "desc/type/All_Types";
        }
        updateMessage("Initiate preannotator...");
        if (report)
            setLogger(new ConsoleLogger());
        dynamicTypeGenerator = new DynamicTypeGenerator(typeDescriptor);
        initTypes();

        File input = new File(inputDB);
        if (!input.exists()) {
            System.out.println("File " + input.getAbsolutePath() + " doesn't exist. ");
        }

        setCollectionReader(SQLTextReader.class, new Object[]{"SQLFile", inputDB, "TableName", inputTable});
        addAnalysisEngines();
        addWriter(option);
    }


    /**
     * Read through all the annotations, iterate all the types and attributes,
     * check to see if the type descriptor has included all of them, if not create
     * the missed types and attributes
     */
    protected void initTypes() {
        FastCNER fastCNER = new FastCNER(cRuleFile);
        TreeSet<String> conceptNames = fastCNER.getNENameList();
        FastNER fastNER = new FastNER(ruleFile, fastNerCaseSensitive);
        conceptNames.addAll(fastNER.getNENameList());

        getNewConceptFromFeatureInf(featureInfRule,conceptNames);
        getNewConceptFromDocInf(docInfRule,conceptNames);

        TreeSet<String> importedTypes = getImportedTypeNames();
        conceptNames.removeAll(importedTypes);

        conceptNames.forEach(e -> {
            if (e.indexOf(".") == -1) {
                e = "edu.utah.bmi.type.system." + e;
            }
            addConceptType(e);
        });


        if (conceptNames.size() > 0)
            reInitTypeSystem("desc/type/customized.xml");
    }

    public void addWriter(String option) {
        switch (option) {
            case "ehost":
                addAnalysisEngine(EhostWriter_AE.class, new Object[]{EhostWriter_AE.PARAM_OUTPUTDIR, mkDir(ehostDir), EhostWriter_AE.PARAM_ANNOTATOR, annotator});
                break;
            case "brat":
                addAnalysisEngine(BratConceptAnnotationWritter_AE.class, new Object[]{"OutputDirectory", mkDir(bratDir)});
                break;
            case "xmi":
                addAnalysisEngine(XMIWritter_AE.class, new Object[]{"OutputDirectory", mkDir(xmiDir), "Annotator", annotator});
                break;
            default:
                File outputDBFile = new File(outputDB);
                if (outputDB.toLowerCase().endsWith(".sqlite")) {
                    mkDir(outputDBFile.getParentFile());
                }
                addAnalysisEngine(SQLWriterCasConsumer.class, new Object[]{SQLWriterCasConsumer.PARAM_SQLFILE, outputDBFile.getAbsolutePath(), SQLWriterCasConsumer.PARAM_TABLENAME, outputTable,
                        SQLWriterCasConsumer.PARAM_ANNOTATOR, annotator,
                        SQLWriterCasConsumer.PARAM_OVERWRITETABLE, false, SQLWriterCasConsumer.PARAM_BATCHSIZE, 150});
                break;
        }
    }


    protected String mkDir(File dir) {
        if (!dir.exists()) {
            try {
                FileUtils.forceMkdir(dir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return dir.getAbsolutePath();
    }

    protected String mkDir(String dirString) {
        File dir = new File(dirString);
        return mkDir(dir);
    }

    private void addAnalysisEngines() {
        if (debug)
            System.out.println("add engine FastCNER_AE_Diff_Concepts");
        addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_FILE, "conf/rush.csv",
                RuSH_AE.PARAM_SENTENCE_TYPE_NAME, "edu.utah.bmi.type.system.Sentence",
                RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, "edu.utah.bmi.type.system.SentenceOdd",
                RuSH_AE.PARAM_TOKEN_TYPE_NAME, "edu.utah.bmi.type.system.Token",
                RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true, RuSH_AE.PARAM_FIX_GAPS, true});
        if (debug)
            addAnalysisEngine(RuSHTestAE_General.class, new Object[]{RuSHTestAE_General.PARAM_PRINT, true});

        Object[] configurationData = new Object[]{FastNER_AE_General.PARAM_RULE_FILE_NAME, cRuleFile,
                FastNER_AE_General.PARAM_SENTENCE_TYPE_NAME, "edu.utah.bmi.type.system.Sentence",
                FastNER_AE_General.PARAM_TOKEN_TYPE_NAME, "edu.utah.bmi.type.system.Token",
                FastNER_AE_General.PARAM_MARK_PSEUDO, true,
                FastNER_AE_General.PARAM_LOG_RULE_INFO, true, FastNER_AE_General.PARAM_CASE_SENSITIVE, fastNerCaseSensitive};

        addAnalysisEngine(FastCNER_AE_General.class, configurationData);
        if (debug) {
            System.out.println("add engine FastNER_AE_Diff_SP_Concepts");
//            runner.addAnalysisEngine(RuSHTestAE_General.class, new Object[]{RuSHTestAE_General.PARAM_PRINT, true});
        }
        configurationData[1] = ruleFile;
        addAnalysisEngine(FastNER_AE_General.class, configurationData);
        if (debug) {
            System.out.println("add engine FastContext_General_AE");
            addAnalysisEngine(RuSHTestAE_General.class, new Object[]{RuSHTestAE_General.PARAM_PRINT, true});
        }
        String contextRule = "conf/context.csv";
        if (!new File(contextRule).exists())
            contextRule = "conf/context.owl";
        System.out.println("Read Context rules from " + contextRule);
        edu.utah.bmi.type.system.Concept c;
        addAnalysisEngine(FastContext_General_AE.class, new Object[]{"RuleFile", contextRule,
                "Windowsize", 8,
                "SentenceTypeName", "edu.utah.bmi.type.system.Sentence",
                "TokenTypeName", "edu.utah.bmi.type.system.Token",
                "ConceptTypeName", "edu.utah.bmi.type.system.Concept",
                "ContextTypeName", "edu.utah.bmi.type.system.Concept",
                "NegationFeatureName", "Negation",
                "TemporalityFeatureName", "Temporality",
                "ExperiencerFeatureName", "Experiencer",
                "Debug", false, "CaseInsensitive", true});
        if (debug)
            System.out.println("add engine CheckModifiers_AE");
        addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{FeatureInferenceAnnotator.PARAM_EVIDENCE_CONCEPT, featureInfEvidenceConcept,
                FeatureInferenceAnnotator.PARAM_OVERWRITE_CONCEPT, true, FeatureInferenceAnnotator.PARAM_REMOVE_OVERLAP, true,
                FeatureInferenceAnnotator.PARAM_INFERENCES, featureInfRule});
        addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_CONCEPT_INFERENCES, docInfRule,
                DocInferenceAnnotator.PARAM_DEFAULT_DOC_TYPE, docInfDefault});
    }


    protected void getNewConceptFromFeatureInf(String featureInfRules, TreeSet<String> conceptNames) {
//        NegatedConcept:Negation,negated|PastConcept:Negation,affirmed&amp;Temporality,historical&amp;Experiencer,patient|NonPatientConcept:Negation,affirmed&amp;Experiencer,nonpatient
        for (String inferenceRule : featureInfRules.split("\\|")) {
            String[] rule = inferenceRule.split(":");
            if (rule.length < 2) {
                System.err.println("Invalid rule: " + inferenceRule);
            }
            conceptNames.add(checkTypeDomain(rule[0]));
        }
    }

    protected void getNewConceptFromDocInf(String docInfRules, TreeSet<String> conceptNames) {
//        Pres_DOC:Concept1, Concept2|PastPres_DOC:PastConcept
        for (String inferenceRule : docInfRules.split("\\|")) {
            String[] rule = inferenceRule.split(":");
            if (rule.length < 2) {
                System.err.println("Invalid rule: " + inferenceRule);
            }
            conceptNames.add(checkTypeDomain(rule[0]));
            for (String evidenceConcept : rule[1].split(",")) {
                conceptNames.add(checkTypeDomain(evidenceConcept));
            }
        }
        if(docInfDefault.trim().length()>0){
            conceptNames.add(checkTypeDomain(docInfDefault));
        }
    }

    private String checkTypeDomain(String conceptName) {
        if (conceptName.indexOf(".") == -1) {
            conceptName = defaultDomain + conceptName.trim();
        }
        return conceptName;
    }

}
