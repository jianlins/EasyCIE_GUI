//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
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
import java.util.logging.Logger;

public class GUICPEStringDebugger implements Processable, StatusSetable {
    public static Logger classLogger = IOUtil.getLogger(GUICPEStringDebugger.class);
    protected GUICPERunner runner;
    public static HashMap<String, GUICPEStringDebugger> debuggers = new HashMap();
    protected ArrayList<AnalysisEngine> aes = new ArrayList();
    protected ArrayList<AnalysisEngine> logAes = new ArrayList();
    protected HashMap<String, String> logTypes = new HashMap();
    private JCas jCas;
    private GUITask guiTask;

    protected GUICPEStringDebugger() {
    }

    public static GUICPEStringDebugger getInstance(TasksFX tasks) {
        TaskFX config = tasks.getTask("easycie");
        String cpeDescriptor = config.getValue("pipeLineSetting/CpeDescriptor");
        LinkedHashMap<String, String> componentsSettings = GUICPERunner.readGUIPipelineConfigurations(config.getChildSettings("pipeLineSetting"));
        String annotator = config.getValue("annotators/current");
        config = tasks.getTask("debug");
        LinkedHashMap<String, String> loggerSettings = readLoggerConfigurations(config.getChildSettings("log"));
        String pipelineName = (new File(cpeDescriptor)).getName();
        pipelineName = pipelineName.substring(0, pipelineName.length() - 4);
        return getInstance(cpeDescriptor, annotator, componentsSettings, loggerSettings, "desc/type/" + pipelineName + "_" + annotator + "_Type.xml");
    }

    public static GUICPEStringDebugger getInstance(String cpeDescriptor, String annotator, String... options) {
        return getInstance(cpeDescriptor, annotator, new LinkedHashMap(), new LinkedHashMap(), options);
    }

    public static GUICPEStringDebugger getInstance(String cpeDescriptor, String annotator, LinkedHashMap<String, String> externalSettingMap, HashMap<String, String> logTypes, String... options) {
        String cpeName = FilenameUtils.getBaseName(cpeDescriptor) + "_" + annotator;
        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap = GUICPERunner.parseExternalConfigMap(externalSettingMap);
        Iterator var8 = externalConfigMap.keySet().iterator();

        while(true) {
            String aeName;
            String lowerName;
            do {
                if (!var8.hasNext()) {
                    ArrayList<String> modifiedAes = GUICPERunner.modifiedChecker.checkModifiedAEs(cpeDescriptor, externalConfigMap);
                    ArrayList<String> modifiedLoggers = GUICPERunner.modifiedChecker.checkModifiedLoggers(logTypes);
                    GUICPERunner runner = GUICPERunner.getInstance(cpeDescriptor, annotator, new ConsoleLogger(), modifiedAes, externalConfigMap, options);
                    GUICPEStringDebugger debugger;
                    if (debuggers.containsKey(cpeName) && modifiedAes != null) {
                        Iterator var11;
                        String cpeProcessorName;
                        if (modifiedAes != null && modifiedAes.size() > 0) {
                            debugger = (GUICPEStringDebugger)debuggers.get(cpeName);
                            if (modifiedLoggers == null) {
                                classLogger.warning("Logger configurations haven't been initiated during the 1st run.");
                            } else if (modifiedLoggers.size() > 0) {
                                classLogger.finest("External configuration modification detected: " + modifiedAes);
                                var11 = modifiedLoggers.iterator();

                                while(var11.hasNext()) {
                                    cpeProcessorName = (String)var11.next();
                                    debugger.updateLoggerConfiguration(cpeProcessorName, (String)logTypes.get(cpeName));
                                }
                            }

                            if (modifiedLoggers != null) {
                                classLogger.finest("Loggers' configuration modification detected: " + modifiedLoggers);
                                var11 = modifiedLoggers.iterator();

                                while(var11.hasNext()) {
                                    cpeProcessorName = (String)var11.next();
                                    debugger.updateLoggerConfiguration(cpeProcessorName, (String)logTypes.get(cpeProcessorName));
                                }
                            }
                        } else {
                            classLogger.finest("External configuration modification detected: " + modifiedAes);
                            debugger = (GUICPEStringDebugger)debuggers.get(cpeName);
                            var11 = modifiedAes.iterator();

                            while(var11.hasNext()) {
                                cpeProcessorName = (String)var11.next();
                                classLogger.finest("The configuration of the AE: " + cpeProcessorName + " has been checkModifiedAEs. Re-initiate this AE.");
                                debugger.updateAEProcessorConfigurations(cpeProcessorName, (LinkedHashMap)externalConfigMap.get(cpeProcessorName));
                            }

                            if (modifiedLoggers != null) {
                                classLogger.finest("Loggers' configuration modification detected: " + modifiedLoggers);
                                var11 = modifiedLoggers.iterator();

                                while(var11.hasNext()) {
                                    cpeProcessorName = (String)var11.next();
                                    debugger.updateLoggerConfiguration(cpeProcessorName, (String)logTypes.get(cpeProcessorName));
                                }
                            }
                        }
                    } else {
                        classLogger.finest("Cpe descriptor modification detected.");
                        debugger = new GUICPEStringDebugger(runner, logTypes);
                        debugger.buildAEs();
                        debuggers.put(cpeName, debugger);
                    }

                    runner.setStatus(1);
                    return debugger;
                }

                aeName = (String)var8.next();
                lowerName = aeName.toLowerCase();
            } while(!lowerName.contains("reader") && !lowerName.contains("writer"));

            ((LinkedHashMap)externalConfigMap.get(aeName)).put("OverWriteTable", "false");
        }
    }

    public GUICPEStringDebugger(GUICPERunner runner, HashMap<String, String> logTypes) {
        this.runner = runner;
        this.logTypes = logTypes;
    }

    public void setExternalConfigMap(LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap) {
        this.runner.setExternalConfigMap(externalConfigMap);
    }

    public void updateAeConfigurations(ArrayList<AnalysisEngine> aes, String cpeName, LinkedHashMap configurations) {
        if (this.runner.getCpeProcessorIds().containsKey(cpeName)) {
            int id = (Integer)this.runner.getCpeProcessorIds().get(cpeName);
            AnalysisEngine ae = (AnalysisEngine)aes.get(id);
            this.guiTask.updateGUIMessage("Update " + cpeName);
            if (ae instanceof PrimitiveAnalysisEngine_impl) {
                try {
                    PrimitiveAnalysisEngine_impl processor = (PrimitiveAnalysisEngine_impl)ae;
                    UimaContext uimaContext = ae.getUimaContext();
                    if (uimaContext instanceof ChildUimaContext_impl) {
                        ChildUimaContext_impl uimaContext_impl = (ChildUimaContext_impl)uimaContext;
                        Iterator var9 = configurations.keySet().iterator();

                        while(var9.hasNext()) {
                            Object configName = var9.next();
                            Object value = configurations.get(configName);
                            uimaContext_impl.setSharedParam("/" + cpeName + "/" + configName, value);
                            AnalysisComponent aeEngine = processor.getAnalysisComponent();
                            if (configName.equals("RuleFileOrStr") && RuleBasedAEInf.class.isAssignableFrom(aeEngine.getClass())) {
                                RuleBasedAEInf ruleAeEngine = (RuleBasedAEInf)aeEngine;
                                this.runner.addConceptTypes(ruleAeEngine.getTypeDefs(value.toString()).values());
                                this.runner.reInitTypeSystem();
                                aeEngine.initialize(uimaContext);
                            }
                        }
                    }

                    processor.setmInitialized(false);
                } catch (ResourceInitializationException var14) {
                    var14.printStackTrace();
                }
            }
        } else {
            classLogger.warning(cpeName + " doesn't exist in this CPE descriptor.");
        }

    }

    public void updateAeConfiguration(ArrayList<AnalysisEngine> aes, String cpeName, String configName, Object value) {
        LinkedHashMap<String, Object> config = new LinkedHashMap();
        config.put(configName, value);
        this.updateAeConfigurations(aes, cpeName, config);
    }

    public void updateAEProcessorConfigurations(String cpeName, LinkedHashMap configs) {
        this.updateAeConfigurations(this.aes, cpeName, configs);
    }

    public void updateAEProcessorConfiguration(String cpeName, String parameter, Object value) {
        this.updateAeConfiguration(this.aes, cpeName, parameter, value);
    }

    public void updateLoggerConfiguration(String cpeName, String typeNames) {
        this.updateAeConfiguration(this.logAes, cpeName, "TypeNames", typeNames);
    }

    public void buildAEs() {
        classLogger.finest("Compile AEs and Loggers.");
        if (this.runner == null) {
            classLogger.warning("CPE pipeline configuration error, no runner generated.");
        } else {
            if (this.runner.getmCPE() == null) {
                this.runner.compileCPE();
            }

            if (this.aes.size() == 0) {
                CasProcessor[] processors = this.runner.getmCPE().getCasProcessors();
                CasProcessor[] var2 = processors;
                int var3 = processors.length;

                for(int var4 = 0; var4 < var3; ++var4) {
                    CasProcessor casProcessor = var2[var4];
                    String cpeName = casProcessor.getProcessingResourceMetaData().getName().toLowerCase();
                    classLogger.finest("Add processor: " + cpeName);
                    if (cpeName.indexOf("writer") != -1) {
                        classLogger.finest("Skip AE:\"" + cpeName + "\". Not needed in debugging. ");
                    } else {
                        if (casProcessor instanceof AnalysisEngine) {
                            classLogger.finest("Add AE:\"" + cpeName + "\"");
                            this.aes.add((AnalysisEngine)casProcessor);
                        }

                        String aeName = casProcessor.getProcessingResourceMetaData().getName();
                        String types = "";
                        if (this.logTypes.containsKey(aeName)) {
                            types = ((String)this.logTypes.get(aeName)).trim();
                        } else {
                            this.logTypes.put(aeName, "");
                        }

                        if (types.indexOf("Stbegin") == -1 && types.indexOf("Stend") != -1) {
                        }

                        classLogger.finest("Add logger for AE:\"" + cpeName + "\". Will log annotation types: " + types);
                        this.logAes.add(this.createAnalysisEngine(AnnotationLogger.class, new Object[]{"IndicationHeader", aeName, "TypeNames", types, "Indication", "After being processed by :" + aeName}));
                    }
                }

                this.jCas = this.runner.initJCas();
            }

        }
    }

    public AnalysisEngine createAnalysisEngine(Class analysisEngineClass, Object[] configurations) {
        AnalysisEngine ae = null;

        try {
            ae = AnalysisEngineFactory.createEngine(analysisEngineClass, configurations);
        } catch (ResourceInitializationException var5) {
            var5.printStackTrace();
        }

        return ae;
    }

    public JCas process(String inputStr, String... metaStr) {
        AnnotationLogger.reset();
        this.jCas.reset();
        inputStr = inputStr.replaceAll(" ", " ");
        this.jCas.setDocumentText(inputStr);
        RecordRow recordRow = new RecordRow();
        if (metaStr != null && metaStr.length > 0) {
            String[] var4 = metaStr[0].split("\\|");
            int var5 = var4.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                String metaInfor = var4[var6];
                String[] pair = metaInfor.split(",");
                recordRow.addCell(pair[0], pair[1]);
            }
        }

        String metaInfor = recordRow.serialize(new String[0]);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(this.jCas, 0, inputStr.length());
        srcDocInfo.setUri(metaInfor);
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(inputStr.length());
        srcDocInfo.setLastSegment(true);
        srcDocInfo.addToIndexes();
        this.process(this.jCas);
        this.showResults();
        return this.jCas;
    }

    private void process(JCas jCas) {
        for(int i = 0; i < this.aes.size(); ++i) {
            try {
                AnalysisEngine ae = (AnalysisEngine)this.aes.get(i);
                classLogger.finest("Processing using AE:\"" + ae.getMetaData().getName() + "\"");
                ae.process(jCas);
                AnalysisEngine aelogger = (AnalysisEngine)this.logAes.get(i);
                classLogger.finest("Logging output for AE:\"" + ae.getMetaData().getName() + "\"");
                aelogger.process(jCas);
            } catch (AnalysisEngineProcessException var5) {
                var5.printStackTrace();
            }
        }

        this.runner.setStatus(2);
    }

    public JCas process(RecordRow recordRow, String textColumnName, String... excludeColumns) {
        if (this.guiTask != null) {
            this.guiTask.updateGUIMessage("Processing ...");
        }

        AnnotationLogger.reset();
        String inputStr = recordRow.getStrByColumnName(textColumnName);
        this.jCas.reset();
        this.jCas.setDocumentText(inputStr);
        RecordRow newRecordRow = new RecordRow();
        HashSet<String> exclusions = new HashSet();
        exclusions.addAll(Arrays.asList(excludeColumns));
        Iterator var7 = recordRow.getColumnNameValues().entrySet().iterator();

        while(var7.hasNext()) {
            Map.Entry<String, Object> entry = (Map.Entry)var7.next();
            if (!exclusions.contains(entry.getKey()) && !((String)entry.getKey()).equals(textColumnName) && entry.getValue() != null && entry.getValue().toString().length() > 0) {
                newRecordRow.addCell((String)entry.getKey(), entry.getValue());
            }
        }

        String metaInfor = newRecordRow.serialize(new String[0]);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(this.jCas, 0, inputStr.length());
        srcDocInfo.setUri(metaInfor);
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(inputStr.length());
        srcDocInfo.setLastSegment(true);
        srcDocInfo.addToIndexes();
        this.process(this.jCas);
        this.showResults();
        return this.jCas;
    }

    public void setGuiTask(GUITask guiTask) {
        this.guiTask = guiTask;
    }

    public void showResults() {
        if (TasksOverviewController.currentTasksOverviewController != null) {
            Platform.runLater(() -> {
                if (this.guiTask == null) {
                    if (TasksOverviewController.currentTasksOverviewController == null) {
                        return;
                    }

                    this.guiTask = TasksOverviewController.currentTasksOverviewController.currentGUITask;
                }

                TasksOverviewController.currentTasksOverviewController.refreshDebugView();
                this.guiTask.updateGUIMessage("Process complete.");
                this.guiTask.updateGUIProgress(1, 1);
            });
        }

    }

    public static LinkedHashMap<String, String> readLoggerConfigurations(LinkedHashMap<String, SettingAb> loggerSettings) {
        LinkedHashMap<String, String> componentsSettings = new LinkedHashMap();
        Iterator var2 = loggerSettings.keySet().iterator();

        while(var2.hasNext()) {
            String key = (String)var2.next();
            SettingAb setting = (SettingAb)loggerSettings.get(key);
            String value = setting.getSettingValue();
            componentsSettings.put(key, value);
        }

        return componentsSettings;
    }

    public void setStatus(int status) {
        this.runner.setStatus(status);
    }

    public int getStatus() {
        return this.runner.getStatus();
    }
}
