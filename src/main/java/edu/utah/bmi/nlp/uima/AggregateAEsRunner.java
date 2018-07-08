package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.easycie.reader.SQLTextReader;
import edu.utah.bmi.nlp.easycie.writer.BratWritter_AE;
import edu.utah.bmi.nlp.easycie.writer.EhostWriter_AE;
import edu.utah.bmi.nlp.easycie.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.easycie.writer.XMIWritter_AE;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.ae.AnnotationPrinter;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.nlp.uima.loggers.GUILogger;
import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.core.SettingOper;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.ConfigurationParameter;
import org.apache.uima.resource.metadata.ConfigurationParameterDeclarations;
import org.apache.uima.util.InvalidXMLException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.apache.commons.io.comparator.NameFileComparator.NAME_COMPARATOR;

/**
 * @author Jianlin Shi on 6/11/18.
 */
public class AggregateAEsRunner {
	private static Logger logger = IOUtil.getLogger(AggregateAEsRunner.class);
	protected static HashMap<String, AggregateAEsRunner> AESRunners = new HashMap<>();
	protected TasksFX tasks;
	protected String customTypeDescriptor;
	protected String metaStr;
	protected String aesDir;
	public AdaptableUIMACPEJCasRunner runner;
	private JCas jCas;
	private AnalysisEngine aggregateAE;
	protected String runId = "-1";
	protected TaskFX debugConfig;
	protected String option = "";
	public boolean ready = false;
	protected UIMALogger uimaLogger;

	public boolean report = false;
	protected String readDBConfigFileName, writeConfigFileName, inputTableName, snippetResultTable, docResultTable, bunchResultTable,
			ehostDir, bratDir, xmiDir, annotator, datasetId;
	public boolean ehost = false, brat = false, xmi = true;
	protected LinkedHashMap<String, LinkedHashMap<String, String>> componentsSettings;

	public static AggregateAEsRunner getInstance(TasksFX tasks) {
		return getInstance(tasks, "debug");
	}


	public static AggregateAEsRunner getInstance(TasksFX tasks, String option) {
		AggregateAEsRunner AESRunner;
		if (!AESRunners.containsKey(option)) {
			AESRunner = new AggregateAEsRunner(tasks, option);
			AESRunners.put(option, AESRunner);
		} else {
			AESRunner = AESRunners.get(option);
		}
		return AESRunner;
	}

	public static AggregateAEsRunner getInstance(String configFile) {
		return getInstance(configFile, "debug");
	}

	public static AggregateAEsRunner getInstance(String configFile, String option) {
		AggregateAEsRunner AESRunner;
		if (!AESRunners.containsKey(option)) {
			AESRunner = new AggregateAEsRunner(configFile, option);
			AESRunners.put(option, AESRunner);
		} else {
			AESRunner = AESRunners.get(option);
		}
		return AESRunner;
	}

	public AggregateAEsRunner(String configFile, String option) {
		setOption(option);
		TasksFX tasks = new SettingOper(configFile).readSettings();
		init(tasks);
	}


	public AggregateAEsRunner(TasksFX tasks) {
		init(tasks);
	}

	public AggregateAEsRunner(TasksFX tasks, String option) {

		setOption(option);
		init(tasks);
	}

	public void init(TasksFX tasks) {
		this.tasks = tasks;
		refreshPipe();
	}



	public void setOption(String option) {
		this.option = option;
	}

	public void refreshPipe(String... options) {
		if (options.length > 0 && options[0].length() > 0)
			option = options[0];
		ready = false;
		readDebugConfigs(tasks);
		initiate(tasks, option);
		AESRunners.put(option, this);
		updateGUIMessage("Debug pipeline refreshed.");
		updateGUIProgress(1, 1);

	}

	protected void initiate(TasksFX tasks, String option) {
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
		LinkedHashMap<String, SettingAb> pipelineSettings = config.getChildSettings("pipeLineSetting");
		componentsSettings = new LinkedHashMap<>();
		for (SettingAb setting : pipelineSettings.values()) {
			String[] componentConfigure = setting.getSettingName().split("/");
			if (componentConfigure.length < 3)
				continue;
			String componentName = componentConfigure[1];
			String configureName = componentConfigure[2];
			String value = setting.getSettingValue();
			if (!componentsSettings.containsKey(componentName)) {
				componentsSettings.put(componentName, new LinkedHashMap<>());
			}
			componentsSettings.get(componentName).put(configureName, value);
		}

		aesDir = config.getValue("pipeLineSetting/AesDir");
		annotator = config.getValue(ConfigKeys.annotator);

		String rawStringValue = config.getValue(ConfigKeys.reportAfterProcessing);

		report = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');


		config = tasks.getTask("settings");
		readDBConfigFileName = config.getValue(ConfigKeys.readDBConfigFileName);
		inputTableName = config.getValue(ConfigKeys.inputTableName);
		datasetId = config.getValue(ConfigKeys.datasetId);
		writeConfigFileName = config.getValue(ConfigKeys.writeDBConfigFileName);
		snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
		docResultTable = config.getValue(ConfigKeys.docResultTableName);
		bunchResultTable = config.getValue(ConfigKeys.bunchResultTableName);


		TaskFX exportConfig = tasks.getTask("export");
		ehostDir = exportConfig.getValue(ConfigKeys.outputEhostDir);
		bratDir = exportConfig.getValue(ConfigKeys.outputBratDir);
		xmiDir = exportConfig.getValue(ConfigKeys.outputXMIDir);

		this.option = option;

		initUIMALogger();
		initPipe();
		ready = true;

	}


	protected void initPipe() {
		if (uimaLogger != null)
			runId = uimaLogger.getRunid() + "";

		String defaultTypeDescriptor = "desc/type/All_Types";
//        JXTransformer jxTransformer;
		customTypeDescriptor = "desc/type/pipeline_" + annotator;

		if (new File(customTypeDescriptor + ".xml").exists())
			runner = new AdaptableUIMACPEJCasRunner(customTypeDescriptor, "./classes/");
		else
			runner = new AdaptableUIMACPEJCasRunner(defaultTypeDescriptor, "./classes/");
		runner.setLogger(uimaLogger);


		initTypes(customTypeDescriptor);
		addReader();
		runner.getAEDesriptors().clear();
		addAnalysisEngines();
		if (!option.equals("debug") && !option.equals("")) {
			addWriter();
		}
		UpdateMessage("Compile pipeline...");
		if (!option.equals("db"))
			aggregateAE = runner.genAEs();
		jCas = runner.initJCas();

	}

	private void addWriter() {
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
		switch (option) {
			case "ehost":
				runner.addAnalysisEngine(EhostWriter_AE.class,
						new Object[]{EhostWriter_AE.PARAM_OUTPUTDIR, new File(output, "ehost").getAbsolutePath(),
								EhostWriter_AE.PARAM_ANNOTATOR, annotator});
				break;
			case "brat":
				runner.addAnalysisEngine(BratWritter_AE.class,
						new Object[]{BratWritter_AE.PARAM_OUTPUTDIR, new File(output, "brat").getAbsolutePath(),
								BratWritter_AE.PARAM_ANNOTATOR, annotator});
				break;
			case "xmi":
				runner.addAnalysisEngine(XMIWritter_AE.class,
						new Object[]{XMIWritter_AE.PARAM_OUTPUTDIR, new File(output, "xmi").getAbsolutePath(),
								"Annotator", annotator});
				break;
			case "db":
				runner.addAnalysisEngine(SQLWriterCasConsumer.class, new Object[]{
						SQLWriterCasConsumer.PARAM_DB_CONFIG_FILE, writeConfigFileName,
						SQLWriterCasConsumer.PARAM_SNIPPET_TABLENAME, snippetResultTable,
						SQLWriterCasConsumer.PARAM_DOC_TABLENAME, docResultTable,
						SQLWriterCasConsumer.PARAM_ANNOTATOR, annotator,
						SQLWriterCasConsumer.PARAM_VERSION, runId,
						SQLWriterCasConsumer.PARAM_OVERWRITETABLE, false, SQLWriterCasConsumer.PARAM_BATCHSIZE, 150});
				break;
			default:
				break;
		}

	}

	public void setUIMALogger(GUILogger logger) {
		uimaLogger = logger;
	}

	protected void initUIMALogger() {
		if (!option.equalsIgnoreCase("db")) {
			if (TasksOverviewController.currentTasksOverviewController != null && TasksOverviewController.currentTasksOverviewController.currentGUITask != null) {
				GUITask guitask = TasksOverviewController.currentTasksOverviewController.currentGUITask;
				uimaLogger = new GUILogger(guitask, "target/generated-test-sources",
						"desc/type/pipeline_" + annotator);
				if (this.tasks.getTask("debug").getValue("log/ShowUimaViewer").toLowerCase().startsWith("t"))
					((GUILogger) uimaLogger).setUIMAViewer(true);
				((GUILogger) uimaLogger).setTabViewName(TasksOverviewController.DebugView);
				((GUILogger) uimaLogger).setReportable(false);
			} else {
				uimaLogger = new ConsoleLogger();
			}
			uimaLogger.logStartTime();
		} else {
			this.uimaLogger = new NLPDBLogger(this.writeConfigFileName, "LOG", "RUN_ID", this.annotator);
			((NLPDBLogger) uimaLogger).setReportable(report);
		}
	}

	public void setGuiTask(GUITask task) {
		if (uimaLogger != null && uimaLogger instanceof GUILogger) {
			((GUILogger) uimaLogger).setTask(task);
		}
	}

	public UIMALogger getUimaLogger() {
		return uimaLogger;
	}

	private void readDebugConfigs(TasksFX tasks) {
		UpdateMessage("Initiating debug pipeline...");
		debugConfig = tasks.getTask("debug");
		metaStr = debugConfig.getValue(ConfigKeys.metaStr).trim();
		if (metaStr.trim().length() == 0)
			metaStr = "DOC_ID,-1|DATASETID,-1|DOC_NAME,debug.dco|DATE,2108-01-01 00:00:00";


	}

	public void addReader() {
		setReader(SQLTextReader.class, new Object[]{SQLTextReader.PARAM_DB_CONFIG_FILE, readDBConfigFileName,
				SQLTextReader.PARAM_DATASET_ID, datasetId,
				SQLTextReader.PARAM_DOC_TABLE_NAME, inputTableName,
				SQLTextReader.PARAM_QUERY_SQL_NAME, "masterInputQuery",
				SQLTextReader.PARAM_COUNT_SQL_NAME, "masterCountQuery",
				SQLTextReader.PARAM_DOC_COLUMN_NAME, "TEXT"});
	}

	public void setReader(Class readerClass, Object[] configurations) {
		runner.setCollectionReader(readerClass, configurations);
	}


	/**
	 * @param inputStr input text
	 * @param metaStr  metadata string--optional (each item separated by |, and each name-value pair separated by ,)
	 */
	public JCas process(String inputStr, String... metaStr) {
		AnnotationLogger.reset();
		jCas.reset();
		jCas.setDocumentText(inputStr);
		RecordRow recordRow = new RecordRow();
		if (metaStr != null && metaStr.length > 0)
			for (String metaInfor : metaStr[0].split("\\|")) {
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

		UpdateMessage("Text processed.");
		return jCas;
	}


	public JCas process(RecordRow recordRow, String textColumnName, String... excludeColumns) {
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
			aggregateAE.collectionProcessComplete();
		} catch (AnalysisEngineProcessException e) {
			e.printStackTrace();
		}
		return jCas;
	}


	protected void addAnalysisEngines() {
		UpdateMessage("Add pipeline components...");
		File[] aeFiles = new File(aesDir).listFiles();
		Arrays.sort(aeFiles, NAME_COMPARATOR);
		for (File aeFile : aeFiles) {
			if (aeFile.isFile()) {
				AnalysisEngineDescription aed = runner.addAnalysisEngineFromDescriptor(aeFile.getAbsolutePath(), new Object[]{});
				String value = (String) aed.getAttributeValue("annotatorImplementationName");
				String aeName = aed.getMetaData().getName();
				updateAEConfiguration(aed, aeName);
				String groupName = "EasyCIE";
				if (value.indexOf("ctakes") != -1) {
					groupName = "cTakes";
				}
				logger.finest("Add " + groupName + " ae: \"" + aeName + "\" \tfrom AE descriptor file: " + aeFile.getName());
				String logTypes = debugConfig.getValue("log/" + aeName).trim();
				if (option.equals("debug") && logTypes.length() > 0) {
					if (uimaLogger instanceof GUILogger || uimaLogger instanceof NLPDBLogger) {
						runner.addAnalysisEngine(AnnotationLogger.class, new Object[]{
								AnnotationLogger.PARAM_INDICATION_HEADER, aeName,
								AnnotationLogger.PARAM_TYPE_NAMES, logTypes,
								AnnotationLogger.PARAM_INDICATION,
								"After being processed by :" + aeName});
					} else {
						runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{
								AnnotationPrinter.PARAM_INDICATION, aeName,
								AnnotationPrinter.PARAM_TYPE_NAME, logTypes
						});
					}
				}
			}
		}
	}

	private void updateAEConfiguration(AnalysisEngineDescription aed, String aeName) {
		LinkedHashMap<String, String> configurations = componentsSettings.get(aeName);
		ConfigurationParameterDeclarations declarations = aed.getMetaData().getConfigurationParameterDeclarations();
		for (ConfigurationParameter para : declarations.getConfigurationParameters()) {
			String type = para.getType();
			String name = para.getName();
			if (configurations != null && configurations.containsKey(name)) {
				String valueStr = configurations.get(name).trim();
				logger.finest("update configuration of " + aeName + " parameter: \"" + name + "\"+(" + type + ")" + " to " + valueStr);
				switch (type) {
					case "String":
						aed.getMetaData().getConfigurationParameterSettings().setParameterValue(name, valueStr);
						break;
					case "Integer":
						aed.getMetaData().getConfigurationParameterSettings().setParameterValue(name, Integer.parseInt(valueStr));
						break;
					case "Boolean":
						aed.getMetaData().getConfigurationParameterSettings().setParameterValue(name, valueStr.toLowerCase().startsWith("t"));
						break;
					case "Double":
						aed.getMetaData().getConfigurationParameterSettings().setParameterValue(name, Double.parseDouble(valueStr));
						break;
					default:
						logger.info("Uima type: " + type + " is not supported in the current configuration setting.");
						break;
				}
			}
		}
	}

	protected void UpdateMessage(String msg) {
		if (TasksOverviewController.currentTasksOverviewController != null && TasksOverviewController.currentTasksOverviewController.currentGUITask != null)
			TasksOverviewController.currentTasksOverviewController.currentGUITask.updateGUIMessage(msg);
	}

	protected void initTypes(String customTypeDescriptor) {
		this.updateGUIMessage("Read and initiate type system...");
		File[] aeFiles = new File(aesDir).listFiles();
		Arrays.sort(aeFiles, NAME_COMPARATOR);
		for (File aeFile : aeFiles) {
			if (aeFile.isFile()) {
				try {
					AnalysisEngineDescription aed = AnalysisEngineFactory.createEngineDescriptionFromPath(aeFile.getAbsolutePath());
					String className = (String) aed.getAttributeValue("annotatorImplementationName");
					Class<?> aeCls = Class.forName(className);
					if (RuleBasedAEInf.class.isAssignableFrom(aeCls)) {
						String ruleStr = (String) aed.getMetaData().getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR);
						logger.info("Add type system for " + aed.getMetaData().getName() + "\t from file: " + ruleStr);
						this.runner.addConceptTypes(((RuleBasedAEInf) aeCls.newInstance()).getTypeDefs(ruleStr).values());
					}
				} catch (InvalidXMLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InstantiationException e) {
					e.printStackTrace();
				}
			}
		}
		this.runner.reInitTypeSystem(customTypeDescriptor);
	}

	public void run() throws Exception {
		updateGUIMessage("Compile pipeline...");
		runner.run();
	}

	public void showResults() {
		uimaLogger.collectionProcessComplete("");
	}

	private void updateGUIMessage(String msg) {
		if (uimaLogger instanceof GUILogger) {
			GUITask task = ((GUILogger) uimaLogger).getTask();
			if (task != null)
				task.updateGUIMessage(msg);
		}
	}

	private void updateGUIProgress(int a, int b) {
		if (uimaLogger instanceof GUILogger) {
			GUITask task = ((GUILogger) uimaLogger).getTask();
			if (task != null)
				task.updateGUIProgress(a, b);
		}
	}
}
