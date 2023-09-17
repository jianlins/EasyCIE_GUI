//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.logging.Level;

public class GUICPERunner extends AdaptableCPEDescriptorRunner {
    protected static GUICPERunner lastGUIRunner = null;

    public static GUICPERunner getInstance(TasksFX tasks) {
        TaskFX config = tasks.getTask("easycie");
        String cpeDescriptor = config.getValue("pipeLineSetting/CpeDescriptor");
        LinkedHashMap<String, String> componentsSettings = readGUIPipelineConfigurations(config.getChildSettings("pipeLineSetting"));
        String annotator = config.getValue("annotators/current");
        String pipelineName = (new File(cpeDescriptor)).getName();
        pipelineName = pipelineName.substring(0, pipelineName.length() - 4);
        return getInstance(cpeDescriptor, annotator, componentsSettings, "classes", "desc/type/" + pipelineName + "_" + annotator + "_Type.xml");
    }

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, String... options) {
        return getInstance(cpeDescriptor, annotator, (UIMALogger)null, new LinkedHashMap(), options);
    }

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, UIMALogger logger, String... options) {
        return getInstance(cpeDescriptor, annotator, logger, new LinkedHashMap(), options);
    }

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, LinkedHashMap<String, String> externalRuleConfigMap, String... options) {
        return getInstance(cpeDescriptor, annotator, (UIMALogger)null, externalRuleConfigMap, options);
    }

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, UIMALogger logger, LinkedHashMap<String, String> externalSettingMap, String... options) {
        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap = parseExternalConfigMap(externalSettingMap);
        ArrayList<String> modifiedAes = modifiedChecker.checkModifiedAEs(cpeDescriptor, externalConfigMap);
        return getInstance(cpeDescriptor, annotator, logger, modifiedAes, externalConfigMap, options);
    }

    public static GUICPERunner getInstance(String cpeDescriptor, String annotator, UIMALogger logger, ArrayList<String> modifiedAes, LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap, String... options) {
        String cpeName = FilenameUtils.getBaseName(cpeDescriptor) + "_" + annotator;
        if (lastRunner != null && lastRunner.runnerName.equals(cpeName) && modifiedAes != null) {
            if (modifiedAes.size() > 0) {
                Iterator var7 = modifiedAes.iterator();

                while(var7.hasNext()) {
                    String aeName = (String)var7.next();
                    classLogger.finest("The configuration of the AE: " + aeName + " has been modified. Re-initiate this AE.");
                    lastGUIRunner.updateProcessorConfigurations(aeName, (LinkedHashMap)externalConfigMap.get(aeName));
                }
            }
        } else {
            if (classLogger.isLoggable(Level.FINEST)) {
                if (modifiedAes == null) {
                    classLogger.finest("Cpe descriptor modification detected.");
                } else {
                    classLogger.finest("Configuration modification detected: " + modifiedAes);
                }
            }

            lastGUIRunner = new GUICPERunner(cpeDescriptor, annotator, logger, externalConfigMap, options);
            lastGUIRunner.runnerName = cpeName;
        }

        lastGUIRunner.setUIMALogger(logger);
        return lastGUIRunner;
    }

    public static LinkedHashMap<String, String> readGUIPipelineConfigurations(LinkedHashMap<String, SettingAb> pipelineSettings) {
        LinkedHashMap<String, String> componentsSettings = new LinkedHashMap();
        Iterator var2 = pipelineSettings.values().iterator();

        while(var2.hasNext()) {
            SettingAb setting = (SettingAb)var2.next();
            String[] componentConfigure = setting.getSettingName().split("/");
            if (componentConfigure.length >= 3) {
                String key = componentConfigure[1] + "/" + componentConfigure[2];
                String value = setting.getSettingValue();
                componentsSettings.put(key, value);
            }
        }

        return componentsSettings;
    }

    public GUICPERunner(String cpeDescriptor, String annotator, UIMALogger logger, LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap, String... options) {
        this.annotator = annotator;
        this.externalConfigMap = externalConfigMap;
        this.setUIMALogger(logger);
        this.initCpe(cpeDescriptor, annotator, options);
    }
}
