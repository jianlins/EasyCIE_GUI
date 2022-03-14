package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.compiler.MemoryClassLoader;
import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;

public class GUICPERunner extends AdaptableCPEDescriptorRunner {

    protected static GUICPERunner lastGUIRunner = null;

    public static GUICPERunner getInstance(TasksFX tasks) {
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        String cpeDescriptor = config.getValue("pipeLineSetting/CpeDescriptor");
        LinkedHashMap<String, String> componentsSettings = readGUIPipelineConfigurations(config.getChildSettings("pipeLineSetting"));
        String annotator = config.getValue(ConfigKeys.annotator);
        String pipelineName = new File(cpeDescriptor).getName();
        pipelineName = pipelineName.substring(0, pipelineName.length() - 4);
        return getInstance(cpeDescriptor, annotator, componentsSettings, "classes", "desc/type/" + pipelineName + "_" + annotator + "_Type.xml");
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

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, String... options) {
        return getInstance(cpeDescriptor, annotator, null, new LinkedHashMap<>(), options);
    }

    /**
     * @param cpeDescriptor location of cpe descripter xml file
     * @param annotator     annotator name
     * @param logger        logger to track the pipeline running log (can be null)
     * @param options       0~3 parameters:
     *                      1. The location of auto-gen type descriptor
     *                      2. The location of compiled classes for auto-gen type systems
     *                      3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, UIMALogger logger, String... options) {
        return getInstance(cpeDescriptor, annotator, logger, new LinkedHashMap<>(), options);
    }

    /**
     * db logger will automatically added if db writer is configured
     *
     * @param cpeDescriptor         location of cpe descripter xml file
     * @param annotator             annotator name
     * @param externalRuleConfigMap external configuration values
     * @param options               0~3 parameters:
     *                              1. The location of auto-gen type descriptor
     *                              2. The location of compiled classes for auto-gen type systems
     *                              3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */
    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, LinkedHashMap<String, String> externalRuleConfigMap, String... options) {
        return getInstance(cpeDescriptor, annotator, null, externalRuleConfigMap, options);
    }

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator,
                                           UIMALogger logger,
                                           LinkedHashMap<String, String> externalSettingMap,
                                           String... options) {
        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap = parseExternalConfigMap(externalSettingMap);
        ArrayList<String> modifiedAes = modifiedChecker.checkModifiedAEs(cpeDescriptor, externalConfigMap);
        return getInstance(cpeDescriptor, annotator, logger, modifiedAes, externalConfigMap, options);
    }

    /**
     * @param cpeDescriptor     location of cpe descripter xml file
     * @param annotator         annotator name
     * @param logger            logger to track the pipeline running log (can be null)
     * @param modifiedAes       a list of modified Ae names
     * @param externalConfigMap external configurations
     * @param options           0~3 parameters:
     *                          1. The location of auto-gen type descriptor
     *                          2. The location of compiled classes for auto-gen type systems
     *                          3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */
    public static GUICPERunner getInstance(String cpeDescriptor, String annotator,
                                           UIMALogger logger, ArrayList<String> modifiedAes,
                                           LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap,
                                           String... options) {
        String cpeName = FilenameUtils.getBaseName(cpeDescriptor) + "_" + annotator;
        if (lastRunner != null && lastRunner.runnerName.equals(cpeName) && modifiedAes != null) {
            if (modifiedAes.size() > 0) {
                for (String aeName : modifiedAes) {
                    classLogger.finest("The configuration of the AE: " + aeName + " has been modified. Re-initiate this AE.");
                    lastGUIRunner.updateProcessorConfigurations(aeName, externalConfigMap.get(aeName));
                }
            }
        } else {
            if (classLogger.isLoggable(Level.FINEST)) {
                if (modifiedAes == null)
                    classLogger.finest("Cpe descriptor modification detected.");
                else
                    classLogger.finest("Configuration modification detected: " + modifiedAes);
            }
            lastGUIRunner = new GUICPERunner(cpeDescriptor, annotator, logger, externalConfigMap, options);
            lastGUIRunner.runnerName = cpeName;
        }
        lastGUIRunner.setUIMALogger(logger);
        return lastGUIRunner;
    }

    public static LinkedHashMap<String, String> readGUIPipelineConfigurations(LinkedHashMap<String, SettingAb> pipelineSettings) {
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

    public GUICPERunner(String cpeDescriptor, String annotator, UIMALogger logger,
                        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap,
                        String... options) {
        this.annotator = annotator;
        this.externalConfigMap = externalConfigMap;
        setUIMALogger(logger);
        initCpe(cpeDescriptor, annotator, options);
    }


}
