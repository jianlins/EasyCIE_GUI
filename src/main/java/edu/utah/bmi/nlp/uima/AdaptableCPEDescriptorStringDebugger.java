
/*
 * Copyright  2017  Department of Biomedical Informatics, University of Utah
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.easycie.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.rush.core.RuSH;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import javafx.application.Platform;
import org.apache.commons.io.FilenameUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.analysis_engine.impl.PrimitiveAnalysisEngine_impl;
import org.apache.uima.collection.base_cpm.CasProcessor;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.impl.ChildUimaContext_impl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jianlin Shi
 * Created on 7/9/17.
 */
public class AdaptableCPEDescriptorStringDebugger implements Processable, StatusSetable {
    public static Logger classLogger = IOUtil.getLogger(AdaptableCPEDescriptorStringDebugger.class);
    protected AdaptableCPEDescriptorRunner runner;
    public static HashMap<String, AdaptableCPEDescriptorStringDebugger> debuggers = new HashMap<>();
    protected ArrayList<AnalysisEngine> aes = new ArrayList<>();
    protected ArrayList<AnalysisEngine> logAes = new ArrayList<>();
    protected HashMap<String, String> logTypes = new HashMap<>();
    private JCas jCas;
    private GUITask guiTask;

    protected AdaptableCPEDescriptorStringDebugger() {

    }


    public static AdaptableCPEDescriptorStringDebugger getInstance(TasksFX tasks) {
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        String cpeDescriptor = config.getValue("pipeLineSetting/CpeDescriptor");
        LinkedHashMap<String, String> componentsSettings = AdaptableCPEDescriptorRunner.readPipelineConfigurations(config.getChildSettings("pipeLineSetting"));
        String annotator = config.getValue(ConfigKeys.annotator);

        config = tasks.getTask("debug");
        LinkedHashMap<String, String> loggerSettings = readLoggerConfigurations(config.getChildSettings("log"));

        String pipelineName = new File(cpeDescriptor).getName();
        pipelineName = pipelineName.substring(0, pipelineName.length() - 4);
        return getInstance(cpeDescriptor, annotator, componentsSettings, loggerSettings, "desc/type/" + pipelineName + "_" + annotator + "_Type.xml");
    }


    /**
     * @param cpeDescriptor location of cpe descripter xml file
     * @param annotator     annotator name
     * @param options       0~3 parameters:
     *                      1. The location of auto-gen type descriptor
     *                      2. The location of compiled classes for auto-gen type systems
     *                      3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */

    public static AdaptableCPEDescriptorStringDebugger getInstance(String cpeDescriptor, String annotator, String... options) {
        return getInstance(cpeDescriptor, annotator, new LinkedHashMap<>(), new LinkedHashMap<>(), options);
    }

    /**
     * @param cpeDescriptor      location of cpe descripter xml file
     * @param annotator          annotator's name
     * @param externalSettingMap external configuration values
     * @param logTypes           type names to be logged for each AE
     * @param options            0~3 parameters:
     *                           1. The location of auto-gen type descriptor
     *                           2. The location of compiled classes for auto-gen type systems
     *                           3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */
    public static AdaptableCPEDescriptorStringDebugger getInstance(String cpeDescriptor, String annotator,
                                                                   LinkedHashMap<String, String> externalSettingMap,
                                                                   HashMap<String, String> logTypes, String... options) {
        AdaptableCPEDescriptorStringDebugger debugger;
        String cpeName = FilenameUtils.getBaseName(cpeDescriptor) + "_" + annotator;
        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap = AdaptableCPEDescriptorRunner.parseExternalConfigMap(externalSettingMap);
        //      make sure to avoid overwrite tables
        for (String aeName : externalConfigMap.keySet()) {
            String lowerName = aeName.toLowerCase();
            if (lowerName.contains("reader") || lowerName.contains("writer")) {
                externalConfigMap.get(aeName).put(SQLWriterCasConsumer.PARAM_OVERWRITETABLE, "false");
            }
        }
        ArrayList<String> modifiedAes = AdaptableCPEDescriptorRunner.modifiedChecker.checkModifiedAEs(cpeDescriptor, externalConfigMap);

        ArrayList<String> modifiedLoggers = AdaptableCPEDescriptorRunner.modifiedChecker.checkModifiedLoggers(logTypes);
        AdaptableCPEDescriptorRunner runner = AdaptableCPEDescriptorRunner.getInstance(cpeDescriptor, annotator, new ConsoleLogger(), modifiedAes,
                externalConfigMap, options);
        if (!debuggers.containsKey(cpeName) || modifiedAes == null) {
            classLogger.finest("Cpe descriptor modification detected.");
            debugger = new AdaptableCPEDescriptorStringDebugger(runner, logTypes);
            debugger.buildAEs();
            debuggers.put(cpeName, debugger);
        } else if (modifiedAes != null && modifiedAes.size() > 0) {
            debugger = debuggers.get(cpeName);
            if (modifiedLoggers == null) {
                classLogger.warning("Logger configurations haven't been initiated during the 1st run.");
            } else if (modifiedLoggers.size() > 0) {
                classLogger.finest("External configuration modification detected: " + modifiedAes);
                for (String cpeProcessorName : modifiedLoggers) {
                    debugger.updateLoggerConfiguration(cpeProcessorName, logTypes.get(cpeName));
                }
            }
            if (modifiedLoggers != null) {
                classLogger.finest("Loggers' configuration modification detected: " + modifiedLoggers);
                for (String cpeProcessorName : modifiedLoggers) {
                    debugger.updateLoggerConfiguration(cpeProcessorName, logTypes.get(cpeProcessorName));
                }
            }
        } else {
            classLogger.finest("External configuration modification detected: " + modifiedAes);
            debugger = debuggers.get(cpeName);
            for (String aeName : modifiedAes) {
                classLogger.finest("The configuration of the AE: " + aeName + " has been checkModifiedAEs. Re-initiate this AE.");
                debugger.updateAEProcessorConfigurations(aeName, externalConfigMap.get(aeName));
            }
            if (modifiedLoggers != null) {
                classLogger.finest("Loggers' configuration modification detected: " + modifiedLoggers);
                for (String cpeProcessorName : modifiedLoggers) {
                    debugger.updateLoggerConfiguration(cpeProcessorName, logTypes.get(cpeProcessorName));
                }
            }
        }
        runner.setStatus(1);
        return debugger;
    }


    public AdaptableCPEDescriptorStringDebugger(AdaptableCPEDescriptorRunner runner, HashMap<String, String> logTypes) {
        this.runner = runner;
        this.logTypes = logTypes;
    }


    /**
     * directly set external configuration map in the nested map format
     *
     * @param externalConfigMap external configurations (nested map format)
     */
    public void setExternalConfigMap(LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap) {
        this.runner.setExternalConfigMap(externalConfigMap);
    }


    /**
     * Try to update the configuration after mCPE is compiled--handy for debugging--reduce recompile time
     *
     * @param aes            a list of AEs
     * @param cpeName        processor name
     * @param configurations configurations
     */
    public void updateAeConfigurations(ArrayList<AnalysisEngine> aes, String cpeName, LinkedHashMap configurations) {
        if (runner.getCpeProcessorIds().containsKey(cpeName)) {
            int id = runner.getCpeProcessorIds().get(cpeName);
            AnalysisEngine ae = aes.get(id);
            guiTask.updateGUIMessage("Update " + cpeName);
            if (ae instanceof PrimitiveAnalysisEngine_impl) {
                try {
                    PrimitiveAnalysisEngine_impl processor = (PrimitiveAnalysisEngine_impl) ae;
                    UimaContext uimaContext = ae.getUimaContext();
                    if (uimaContext instanceof ChildUimaContext_impl) {
                        ChildUimaContext_impl uimaContext_impl = (ChildUimaContext_impl) uimaContext;
                        for (Object configName : configurations.keySet()) {
                            Object value = configurations.get(configName);
                            uimaContext_impl.setSharedParam("/" + cpeName + "/" + configName, value);
                            AnalysisComponent aeEngine = processor.getAnalysisComponent();
                            if (configName.equals(DeterminantValueSet.PARAM_RULE_STR) && RuleBasedAEInf.class.isAssignableFrom(aeEngine.getClass())) {
                                RuleBasedAEInf ruleAeEngine = (RuleBasedAEInf) aeEngine;
                                runner.addConceptTypes(ruleAeEngine.getTypeDefs(value.toString()).values());
                                runner.reInitTypeSystem();
                                aeEngine.initialize(uimaContext);
                            }
                        }
                    }
                    processor.setmInitialized(false);
                } catch (ResourceInitializationException e) {
                    e.printStackTrace();
                }
            }

        } else {
            classLogger.warning(cpeName + " doesn't exist in this CPE descriptor.");
        }
    }

    public void updateAeConfiguration(ArrayList<AnalysisEngine> aes, String cpeName, String configName, Object value) {
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put(configName, value);
        updateAeConfigurations(aes, cpeName, config);
    }

    public void updateAEProcessorConfigurations(String cpeName, LinkedHashMap configs) {
        updateAeConfigurations(aes, cpeName, configs);
    }


    public void updateAEProcessorConfiguration(String cpeName, String parameter, Object value) {
        updateAeConfiguration(aes, cpeName, parameter, value);
    }

    public void updateLoggerConfiguration(String cpeName, String typeNames) {
        updateAeConfiguration(logAes, cpeName, AnnotationLogger.PARAM_TYPE_NAMES, typeNames);
    }


    public void buildAEs() {
        classLogger.finest("Compile AEs and Loggers.");
        if (runner == null) {
            classLogger.warning("CPE pipeline configuration error, no runner generated.");
            return;
        }
//        try {
//            CpeCasProcessors processors = runner.currentCpeDesc.getCpeCasProcessors();
//            ArrayList<Integer>writerIds=new ArrayList<>(runner.writerIds.values());
//            Collections.reverse(writerIds);
//            for(int id:writerIds){
//                processors.removeCpeCasProcessor(id);
//            }
//        } catch (CpeDescriptorException e) {
//            e.printStackTrace();
//        }


        if (runner.getmCPE() == null)
            runner.compileCPE();
        if (aes.size() == 0) {
            CasProcessor[] processors = runner.getmCPE().getCasProcessors();
            for (CasProcessor casProcessor : processors) {
                String cpeName = casProcessor.getProcessingResourceMetaData().getName().toLowerCase();
                classLogger.finest("Add processor: " + cpeName);
//                  skip writer
                if (cpeName.indexOf("writer") != -1) {
                    classLogger.finest("Skip AE:\"" + cpeName + "\". Not needed in debugging. ");
                    continue;
                }
                if (casProcessor instanceof AnalysisEngine) {
                    classLogger.finest("Add AE:\"" + cpeName + "\"");
                    aes.add((AnalysisEngine) casProcessor);
                }
                String aeName = casProcessor.getProcessingResourceMetaData().getName();
                String types = "";
                if (logTypes.containsKey(aeName)) {
                    types = logTypes.get(aeName).trim();
                } else {
                    logTypes.put(aeName, "");
                }
                if (types.indexOf("Stbegin") != -1 || types.indexOf("Stend") != -1) {
                    RuSH_AE.logger.setLevel(Level.FINEST);
                    RuSH.logger.setLevel(Level.FINEST);
                }
                classLogger.finest("Add logger for AE:\"" + cpeName + "\". Will log annotation types: " + types);
                logAes.add(createAnalysisEngine(AnnotationLogger.class, new Object[]{
                        AnnotationLogger.PARAM_INDICATION_HEADER, aeName,
                        AnnotationLogger.PARAM_TYPE_NAMES, types,
                        AnnotationLogger.PARAM_INDICATION,
                        "After being processed by :" + aeName}));
            }
            jCas = runner.initJCas();
        }
    }

    public AnalysisEngine createAnalysisEngine(Class analysisEngineClass, Object[] configurations) {
        AnalysisEngine ae = null;
        try {
            ae = AnalysisEngineFactory.createEngine(analysisEngineClass, configurations);
        } catch (ResourceInitializationException e) {
            e.printStackTrace();
        }
        return ae;
    }


    /**
     * @param inputStr input text
     * @param metaStr  metadata string--optional (each item separated by |, and each name-value pair separated by ,)
     */
    public JCas process(String inputStr, String... metaStr) {
        AnnotationLogger.reset();
        jCas.reset();
//        temp solution to replace char 160
        inputStr = inputStr.replaceAll(" ", " ");
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
        process(jCas);
        showResults();
        return jCas;
    }

    private void process(JCas jCas) {
        for (int i = 0; i < aes.size(); i++) {
            try {
                AnalysisEngine ae = aes.get(i);
                classLogger.finest("Processing using AE:\"" + ae.getMetaData().getName() + "\"");
                ae.process(jCas);
                AnalysisEngine aelogger = logAes.get(i);
                classLogger.finest("Logging output for AE:\"" + ae.getMetaData().getName() + "\"");
                aelogger.process(jCas);
            } catch (AnalysisEngineProcessException e) {
                e.printStackTrace();
            }
        }
        runner.setStatus(2);
    }


    public JCas process(RecordRow recordRow, String textColumnName, String... excludeColumns) {
        if (guiTask != null)
            guiTask.updateGUIMessage("Processing ...");
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
        process(jCas);
        showResults();
        return jCas;
    }

    public void setGuiTask(GUITask guiTask) {
        this.guiTask = guiTask;
    }

    public void showResults() {
        if (TasksOverviewController.currentTasksOverviewController != null) {
            Platform.runLater(() -> {
                if (guiTask == null) {
                    if (TasksOverviewController.currentTasksOverviewController != null)
                        guiTask = TasksOverviewController.currentTasksOverviewController.currentGUITask;
                    else
                        return;
                }

                TasksOverviewController.currentTasksOverviewController.refreshDebugView();

                guiTask.updateGUIMessage("Process complete.");
                guiTask.updateGUIProgress(1, 1);

            });
        }
    }

    public static LinkedHashMap<String, String> readLoggerConfigurations(LinkedHashMap<String, SettingAb> loggerSettings) {
        LinkedHashMap<String, String> componentsSettings = new LinkedHashMap<>();
        for (String key : loggerSettings.keySet()) {
            SettingAb setting = loggerSettings.get(key);
            String value = setting.getSettingValue();
            componentsSettings.put(key, value);
        }
        return componentsSettings;
    }


    @Override
    public void setStatus(int status) {
        runner.setStatus(status);
    }

    @Override
    public int getStatus() {
        return runner.getStatus();
    }
}
