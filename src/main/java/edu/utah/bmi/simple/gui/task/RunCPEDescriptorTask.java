package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.easycie.reader.SQLTextReader;
import edu.utah.bmi.nlp.easycie.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorRunner;
import edu.utah.bmi.nlp.uima.AdaptableUIMACPETaskJCasRunner;
import edu.utah.bmi.nlp.uima.BunchMixInferenceWriter;
import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.scene.control.Button;
import org.apache.uima.collection.impl.cpm.container.CPEFactory;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunCPEDescriptorTask extends GUITask {
    public static Logger logger = Logger.getLogger(RunCPEDescriptorTask.class.getCanonicalName());
    public static Button button;
    protected String readerDBConfigFileName, writerDBConfigFileName, inputTableName, snippetResultTable, docResultTable, bunchResultTable,
            ehostDir, bratDir, xmiDir, annotator, datasetId;
    public boolean report = false;
    public AdaptableCPEDescriptorRunner runner;
    protected LinkedHashMap<String, String> componentsSettings;
    private String cpeDescriptor;
    private TasksFX tasks;


    public RunCPEDescriptorTask() {

    }


    public RunCPEDescriptorTask(TasksFX tasks, Button button) {
        this.button = button;
        button.setDisable(true);
        this.tasks = tasks;
    }

    public RunCPEDescriptorTask(TasksFX tasks) {
        initiate(tasks, "db");
    }

    public RunCPEDescriptorTask(TasksFX tasks, String paras) {
        if (paras == null || paras.length() == 0)
            initiate(tasks, "db");
        initiate(tasks, paras);
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
        cpeDescriptor = config.getValue("pipeLineSetting/CpeDescriptor");
        componentsSettings = readPipelineConfigurations(config.getChildSettings("pipeLineSetting"));

        annotator = config.getValue(ConfigKeys.annotator);
        String rawStringValue = config.getValue(ConfigKeys.reportAfterProcessing);
        report = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');


        config = tasks.getTask("settings");
        readerDBConfigFileName = config.getValue(ConfigKeys.readDBConfigFileName);
        inputTableName = config.getValue(ConfigKeys.inputTableName);
        datasetId = config.getValue(ConfigKeys.datasetId);
        writerDBConfigFileName = config.getValue(ConfigKeys.writeDBConfigFileName);
        snippetResultTable = config.getValue(ConfigKeys.snippetResultTableName);
        docResultTable = config.getValue(ConfigKeys.docResultTableName);
        bunchResultTable = config.getValue(ConfigKeys.bunchResultTableName);
        String pipelineName = new File(cpeDescriptor).getName();
        pipelineName = pipelineName.substring(0, pipelineName.length() - 4);
        runner = AdaptableCPEDescriptorRunner.getInstance(cpeDescriptor, annotator, new NLPDBLogger(writerDBConfigFileName, annotator),
                componentsSettings, "classes", "desc/type/" + pipelineName + "_" + annotator + "_Type.xml");
        ((NLPDBLogger) runner.getLogger()).setReportable(report);
        ((NLPDBLogger) runner.getLogger()).setTask(this);
        updateReaderConfigurations(runner);
        updateWriterConfigurations(runner);
    }

    protected void updateReaderConfigurations(AdaptableCPEDescriptorRunner runner) {
        runner.updateReadDescriptorsConfiguration(DeterminantValueSet.PARAM_DB_CONFIG_FILE, readerDBConfigFileName);
        runner.updateReadDescriptorsConfiguration(DeterminantValueSet.PARAM_ANNOTATOR, annotator);
        runner.updateReadDescriptorsConfiguration(SQLTextReader.PARAM_DATASET_ID, datasetId);
        runner.updateReadDescriptorsConfiguration(SQLTextReader.PARAM_DOC_TABLE_NAME, inputTableName);
    }


    protected void updateWriterConfigurations(AdaptableCPEDescriptorRunner runner) {
//        if (CPEFactory.lastCpeDescriptorUrl.length() > 0 ||
//                new File(CPEFactory.lastCpeDescriptorUrl).getAbsolutePath().equals(new File(cpeDescriptor).getAbsolutePath())) {
//            for (int writerId : runner.getWriterIds().values()) {
//                runner.updateCpeProcessorConfiguration(writerId, DeterminantValueSet.PARAM_DB_CONFIG_FILE, writerDBConfigFileName);
//                runner.updateCpeProcessorConfiguration(writerId, DeterminantValueSet.PARAM_VERSION, runner.getLogger().getRunid() + "");
//                runner.updateCpeProcessorConfiguration(writerId, DeterminantValueSet.PARAM_ANNOTATOR, annotator);
//                runner.updateCpeProcessorConfiguration(writerId, SQLWriterCasConsumer.PARAM_SNIPPET_TABLENAME, snippetResultTable);
//                runner.updateCpeProcessorConfiguration(writerId, SQLWriterCasConsumer.PARAM_DOC_TABLENAME, docResultTable);
//                runner.updateCpeProcessorConfiguration(writerId, BunchMixInferenceWriter.PARAM_TABLENAME, bunchResultTable);
//            }
//        } else {
//        changed to the compiled processors will be handled in cached CPEFactory
            for (int writerId : runner.getWriterIds().values()) {
                runner.updateDescriptorConfiguration(writerId, DeterminantValueSet.PARAM_DB_CONFIG_FILE, writerDBConfigFileName);
                runner.updateDescriptorConfiguration(writerId, DeterminantValueSet.PARAM_VERSION, runner.getLogger().getRunid() + "");
                runner.updateDescriptorConfiguration(writerId, DeterminantValueSet.PARAM_ANNOTATOR, annotator);
                runner.updateDescriptorConfiguration(writerId, SQLWriterCasConsumer.PARAM_SNIPPET_TABLENAME, snippetResultTable);
                runner.updateDescriptorConfiguration(writerId, SQLWriterCasConsumer.PARAM_DOC_TABLENAME, docResultTable);
                runner.updateDescriptorConfiguration(writerId, BunchMixInferenceWriter.PARAM_TABLENAME, bunchResultTable);
            }
//        }
    }

    protected LinkedHashMap<String, String> readPipelineConfigurations(LinkedHashMap<String, SettingAb> pipelineSettings) {
        LinkedHashMap<String, String> componentsSettings = new LinkedHashMap<>();
        for (SettingAb setting : pipelineSettings.values()) {
            String[] componentConfigure = setting.getSettingName().split("/");
            if (componentConfigure.length < 3)
                continue;
            String key = componentConfigure[1] + "/" + componentConfigure[2];
            String value = setting.getSettingValue();
            componentsSettings.put(key, value);
        }
        return componentsSettings;
    }


    @Override
    protected Object call() throws Exception {
        initiate(tasks, "db");
        runner.run();
        return null;
    }


}
