package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.easycie.CoordinateNERResults_AE;
import edu.utah.bmi.nlp.uima.ae.*;
import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.type.system.SectionHeader;
import edu.utah.bmi.nlp.type.system.SentenceOdd;
import edu.utah.bmi.nlp.uima.*;
import edu.utah.bmi.nlp.easycie.reader.SQLTextReader;
import edu.utah.bmi.nlp.easycie.writer.BratWritter_AE;
import edu.utah.bmi.nlp.easycie.writer.EhostWriter_AE;
import edu.utah.bmi.nlp.easycie.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.easycie.writer.XMIWritter_AE;
import edu.utah.bmi.nlp.uima.loggers.GUILogger;
import edu.utah.bmi.nlp.sectiondectector.SectionDetectorR_AE;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunEasyCIE extends GUITask {
	public static Logger logger = Logger.getLogger(RunEasyCIE.class.getCanonicalName());
	protected String readDBConfigFileName, writeConfigFileName, inputTableName, snippetResultTable, docResultTable, bunchResultTable,
			ehostDir, bratDir, xmiDir, annotator, datasetId;
	public boolean report = false, fastNERCaseSensitive = true, forceAssignSection = false;
	protected String sectionRule = "", rushRule = "", fastNERRule = "", fastCNERRule = "", includesections = "", excludesections = "", contextRule = "",
			dateRule = "", featureInfRule = "", featureMergerRule = "", docInfRule = "", bunchInfRule = "";
	protected int dayInterval = 0;
	public AdaptableUIMACPETaskJCasRunner runner;
	protected EDAO rdao, wdao;
	protected boolean inferAllTemporal = false, saveDateAnnotation = false;
	public boolean ehost = false, brat = false, xmi = true;
	protected String exporttypes;
	protected String customTypeDescriptor;
	protected GUILogger uimaLogger;

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
					 String annotationInferenceRule, String docInferenceRule, String bunchInfRule, boolean report, boolean fastNerCaseSensitive,
					 String readDBConfigFile, String inputTableName, String datasetId, String writeConfigFileName,
					 String outputTableName, String ehostDir, String bratDir, String xmiDir, String exporttypes, String option) {
		this.annotator = annotator;
		this.rushRule = rushRule;
		this.fastNERRule = fastNERRule;
		this.fastCNERRule = fastCNERRule;
		this.contextRule = contextRule;
		this.featureInfRule = annotationInferenceRule;
		this.docInfRule = docInferenceRule;
		this.bunchInfRule = bunchInfRule;
		this.report = report;
		this.fastNERCaseSensitive = fastNerCaseSensitive;
		this.readDBConfigFileName = readDBConfigFile;
		this.inputTableName = inputTableName;
		this.datasetId = datasetId;
		this.writeConfigFileName = writeConfigFileName;
		this.snippetResultTable = outputTableName;
		this.ehostDir = ehostDir;
		this.bratDir = bratDir;
		this.xmiDir = xmiDir;
		this.exporttypes = exporttypes.replaceAll("\\s+", "");
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
		initUIMALogger();
		initPipe(task);
	}

	protected void initiate(TasksFX tasks, String option) {
		if (!Platform.isFxApplicationThread()) {
			guiEnabled = false;
		}
		if (System.getProperty("java.util.logging.config.file") == null &&
				new File("logging.properties").exists()) {
			System.setProperty("java.util.logging.config.file", "logging.properties");
		}
		try {
			LogManager.getLogManager().readConfiguration();
		} catch (IOException e) {
			{
				{
					System.setProperty("java.util.logging.config.file", "logging.properties");
				}
				System.setProperty("java.util.logging.config.file", "logging.properties");
			}
			e.printStackTrace();
		}
		updateGUIMessage("Initiate configurations..");
		TaskFX config = tasks.getTask(ConfigKeys.maintask);
		annotator = config.getValue(ConfigKeys.annotator);
		sectionRule = config.getValue(ConfigKeys.sectionRule);
		fastNERRule = config.getValue(ConfigKeys.tRuleFile);

		fastCNERRule = config.getValue(ConfigKeys.cRuleFile);

		includesections = config.getValue(ConfigKeys.includesections);
		excludesections = config.getValue(ConfigKeys.excludesections);
		contextRule = config.getValue(ConfigKeys.contextRule);
		dateRule = config.getValue(ConfigKeys.dateRule);
		String value=config.getValue(ConfigKeys.dayInterval).trim();
		dayInterval = value.length()==0?0:Integer.parseInt(value);
		String rawStringValue = config.getValue(ConfigKeys.inferAllTemporal);
		inferAllTemporal = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');
		rawStringValue = config.getValue(ConfigKeys.saveDateAnnotation);
		saveDateAnnotation = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');

		featureInfRule = config.getValue(ConfigKeys.featureInfRule);
		featureMergerRule = config.getValue(ConfigKeys.featureMergerRule);
		docInfRule = config.getValue(ConfigKeys.docInfRule);
		bunchInfRule = config.getValue(ConfigKeys.bunchInfRule);

		rawStringValue = config.getValue(ConfigKeys.reportAfterProcessing);
		report = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');
		rawStringValue = config.getValue(ConfigKeys.fastNerCaseSensitive);
		fastNERCaseSensitive = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');
		rawStringValue = config.getValue(ConfigKeys.forceAssignSection);
		forceAssignSection = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');


		config = tasks.getTask("settings");
		readDBConfigFileName = config.getValue(ConfigKeys.readDBConfigFileName);
		inputTableName = config.getValue(ConfigKeys.inputTableName);
		datasetId = config.getValue(ConfigKeys.datasetId);
		writeConfigFileName = config.getValue(ConfigKeys.writeDBConfigFileName);
		snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
		docResultTable = config.getValue(ConfigKeys.docResultTableName);
		bunchResultTable = config.getValue(ConfigKeys.bunchResultTableName);
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

		initUIMALogger();
		initPipe(this);
	}

	@Override
	protected Object call() throws Exception {
		updateGUIMessage("Compile pipeline...");
		runner.run();
		return null;
	}

	protected void initUIMALogger() {
		uimaLogger = new NLPDBLogger(writeConfigFileName, "LOG", "RUN_ID", annotator);
		uimaLogger.setReportable(report);
		uimaLogger.logStartTime();
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
		addReader();
		addAnalysisEngines();
//        SQLWriterCasConsumer.debug=true;

		addWriter(runId);
	}


	/**
	 * Read through all the annotations, iterate all the types and features,
	 * check to see if the type descriptor has included all of them, if not create
	 * the missed types and features
	 * @param customTypeDescriptor customized type descriptor file path
	 */
	protected void initTypes(String customTypeDescriptor) {
		updateGUIMessage("Read and initiate type system...");
		if (sectionRule.length() > 0)
			runner.addConceptTypes(SectionDetectorR_AE.getTypeDefinitions(sectionRule).values());

		if (fastNERRule.length() > 0)
			runner.addConceptTypes(FastNER_AE_General.getTypeDefinitions(fastNERRule, fastNERCaseSensitive).values());

		if (fastCNERRule.length() > 0)
			runner.addConceptTypes(FastCNER_AE_General.getTypeDefinitions(fastCNERRule, true).values());

		if (contextRule.length() > 0)
			runner.addConceptTypes(FastContext_General_AE.getTypeDefinitions(contextRule, false).values());

		if (dateRule.length() > 0)
			runner.addConceptTypes(TemporalContext_AE_General.getTypeDefinitions(dateRule, true).values());

		if (featureInfRule.length() > 0)
			runner.addConceptTypes(FeatureInferenceAnnotator.getTypeDefinitions(featureInfRule).values());

		if (featureMergerRule.length() > 0)
			runner.addConceptTypes(AnnotationFeatureMergerAnnotator.getTypeDefinitions(featureMergerRule).values());

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

	public void addReader() {
		setReader(SQLTextReader.class, new Object[]{SQLTextReader.PARAM_DB_CONFIG_FILE, readDBConfigFileName,
				SQLTextReader.PARAM_DATASET_ID, datasetId,
				SQLTextReader.PARAM_DOC_TABLE_NAME, inputTableName,
				SQLTextReader.PARAM_QUERY_SQL_NAME, "masterInputQuery",
				SQLTextReader.PARAM_COUNT_SQL_NAME, "masterCountQuery",
				SQLTextReader.PARAM_DOC_COLUMN_NAME, "TEXT"});
	}


	public void addWriter(String runId) {
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
			runner.addAnalysisEngine(SQLWriterCasConsumer.class, new Object[]{
					SQLWriterCasConsumer.PARAM_DB_CONFIG_FILE, writeConfigFileName,
					SQLWriterCasConsumer.PARAM_SNIPPET_TABLENAME, snippetResultTable,
					SQLWriterCasConsumer.PARAM_DOC_TABLENAME, docResultTable,
					SQLWriterCasConsumer.PARAM_ANNOTATOR, annotator,
					SQLWriterCasConsumer.PARAM_VERSION, runId,
					SQLWriterCasConsumer.PARAM_WRITE_CONCEPT, exporttypes,
					SQLWriterCasConsumer.PARAM_OVERWRITETABLE, false, SQLWriterCasConsumer.PARAM_BATCHSIZE, 150});
//			NumberWriter.ldao = wdao;
//			runner.addAnalysisEngine(NumberWriter.class, new Object[]{NumberWriter.PARAM_OUTPUT_TYPE, "SAMPLE_SIZE",
//					NumberWriter.PARAM_SQLFILE, writerDBConfigFileName, NumberWriter.PARAM_TABLENAME, "NUMBERS",
//					NumberWriter.PARAM_ANNOTATOR, annotator,
//					NumberWriter.PARMA_ADD_OUTPUT_TYPE, "GROUP_SIZE"});
			if (bunchInfRule.length() > 0) {
				runner.addAnalysisEngine(BunchMixInferenceWriter.class, new Object[]{BunchMixInferenceWriter.PARAM_BUNCH_COLUMN_NAME, "BUNCH_ID",
						BunchMixInferenceWriter.PARAM_SQLFILE, writeConfigFileName,
						BunchMixInferenceWriter.PARAM_RULE_STR, bunchInfRule,
						BunchMixInferenceWriter.PARAM_TABLENAME, bunchResultTable,
						BunchMixInferenceWriter.PARAM_ANNOTATOR, annotator,
						BunchMixInferenceWriter.PARAM_VERSION, runId});
			}
		}

	}

	protected void addAnalysisEngines() {
		if (sectionRule.length() > 0) {
			updateGUIMessage("add engine SectionDetectorR_AE");
			runner.addAnalysisEngine(SectionDetectorR_AE.class, new Object[]{SectionDetectorR_AE.PARAM_RULE_STR, sectionRule});
			if (logger.isLoggable(Level.FINE))
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						SectionHeader.class.getCanonicalName(), AnnotationPrinter.PARAM_INDICATION, "After sectiondetector"});
		}

		if (rushRule.length() > 0) {
			updateGUIMessage("add engine RuSH_AE");
			if (exporttypes == null || exporttypes.indexOf("Sentence") == -1)
				runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
//                        RuSH_AE.PARAM_INSIDE_SECTIONS, includesections,
						RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true});
			else
				runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
						RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true,
//                        RuSH_AE.PARAM_INSIDE_SECTIONS, includesections,
						RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, SentenceOdd.class.getCanonicalName()});
			if (logger.isLoggable(Level.FINER))
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						DeterminantValueSet.defaultNameSpace + "Sentence"});
		}


		if (fastCNERRule.length() > 0) {
			updateGUIMessage("add engine FastCNER_AE_General");
			runner.addAnalysisEngine(FastCNER_AE_General.class, new Object[]{FastCNER_AE_General.PARAM_RULE_STR, fastCNERRule,
					FastCNER_AE_General.PARAM_MARK_PSEUDO, false,
					FastCNER_AE_General.PARAM_INCLUDE_SECTIONS, includesections,
					FastCNER_AE_General.PARAM_INCLUDE_SECTIONS, excludesections,
					FastCNER_AE_General.PARAM_ASSIGN_SECTIONS, forceAssignSection});
			if (logger.isLoggable(Level.FINE)) {
				logger.finer("add engine FastNER_AE_Diff_SP_Concepts");
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						DeterminantValueSet.defaultNameSpace + "Concept",
						AnnotationPrinter.PARAM_INDICATION, "After FastCNER_AE_General\n"});
			}
		}

		if (fastNERRule.length() > 0) {
			updateGUIMessage("add engine FastNER_AE_General");
			runner.addAnalysisEngine(FastNER_AE_General.class, new Object[]{FastNER_AE_General.PARAM_RULE_STR, fastNERRule,
					FastNER_AE_General.PARAM_MARK_PSEUDO, true, FastNER_AE_General.PARAM_CASE_SENSITIVE, fastNERCaseSensitive,
					FastNER_AE_General.PARAM_INCLUDE_SECTIONS, includesections,
					FastNER_AE_General.PARAM_INCLUDE_SECTIONS, excludesections,
					FastNER_AE_General.PARAM_ASSIGN_SECTIONS, forceAssignSection});
			if (logger.isLoggable(Level.FINE)) {
				logger.finer("add engine FastNER_AE_Diff_SP_Concepts");
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						DeterminantValueSet.defaultNameSpace + "Concept",
						AnnotationPrinter.PARAM_INDICATION, "After FastNER_AE_General\n"});
			}
		}

		updateGUIMessage("add engine CoordinateNERResults_AE");
		runner.addAnalysisEngine(CoordinateNERResults_AE.class, null);


//        System.out.println("Read Context rules from " + contextRule);
		if (contextRule.length() > 0) {
			updateGUIMessage("add engine FastContext_General_AE ");
			runner.addAnalysisEngine(FastContext_General_AE.class, new Object[]{FastContext_General_AE.PARAM_RULE_STR, contextRule,
					FastContext_General_AE.PARAM_AUTO_EXPAND_SCOPE, false,});
			if (logger.isLoggable(Level.FINE)) {
				logger.finer("print final Concepts");
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						DeterminantValueSet.defaultNameSpace + "Concept",
						AnnotationPrinter.PARAM_INDICATION, "After FastContext_General_AE\n"});
			}
		}

		if (dateRule.length() > 0) {
			logger.fine("add engine TemporalContext_AE_General");
			runner.addAnalysisEngine(TemporalContext_AE_General.class, new Object[]{
					TemporalContext_AE_General.PARAM_RULE_STR, dateRule,
					TemporalContext_AE_General.PARAM_MARK_PSEUDO, false,
					TemporalContext_AE_General.PARAM_RECORD_DATE_COLUMN_NAME, "DATE",
					TemporalContext_AE_General.PARAM_REFERENCE_DATE_COLUMN_NAME, "REF_DATE",
					TemporalContext_AE_General.PARAM_INFER_ALL, inferAllTemporal,
					TemporalContext_AE_General.PARAM_INTERVAL_DAYS, dayInterval,
					TemporalContext_AE_General.PARAM_SAVE_DATE_ANNO, saveDateAnnotation});
			if (logger.isLoggable(Level.FINE)) {
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						DeterminantValueSet.defaultNameSpace + "Concept",
						AnnotationPrinter.PARAM_INDICATION, "After TemporalContext_AE_General\n"});
			}
		}

		if (featureInfRule.length() > 0) {
			updateGUIMessage("add engine FeatureInferenceAnnotator");

			runner.addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{
					FeatureInferenceAnnotator.PARAM_RULE_STR, featureInfRule});
			if (logger.isLoggable(Level.FINE)) {
				logger.finer("print annotation inferenced Concepts");
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						DeterminantValueSet.defaultNameSpace + "Concept",
						AnnotationPrinter.PARAM_INDICATION, "After FeatureInferenceAnnotator\n"});
			}
		}

		if (featureMergerRule.length() > 0) {
			updateGUIMessage("add engine FeatureMergeAnnotator");

			runner.addAnalysisEngine(AnnotationFeatureMergerAnnotator.class, new Object[]{
					AnnotationFeatureMergerAnnotator.PARAM_RULE_STR, featureMergerRule,
					AnnotationFeatureMergerAnnotator.PARAM_IN_SITU, false});
			if (logger.isLoggable(Level.FINE)) {
				logger.finer("print annotation inferenced Concepts");
				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
						DeterminantValueSet.defaultNameSpace + "Concept",
						AnnotationPrinter.PARAM_INDICATION, "After AnnotationFeatureMergerAnnotator\n"});
			}
		}

		if (docInfRule.length() > 0) {
			updateGUIMessage("add engine DocInferenceAnnotator");
			runner.addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_RULE_STR, docInfRule});
		}
		if (logger.isLoggable(Level.FINE)) {
			logger.finer("print final Doc Concepts");
			runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME, Doc_Base.class.getCanonicalName(),
					AnnotationPrinter.PARAM_INDICATION, "After DocInferenceAnnotator\n"});
		}
	}
}
