package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Check if cpe, external configuration or rule-based AEs' rule files have been checkModifiedAEs.
 */
public class ModifiedChecker {
    protected HashMap<String, Long> cpeDescriptorsLastModified = new HashMap<>();
    protected LinkedHashMap<String, Long> aeRuleFileLastModified = new LinkedHashMap<>();
    protected LinkedHashMap<String, LinkedHashMap<String, String>> previousExternalConfigMap = new LinkedHashMap<>();
    protected HashMap<String, String> logTypes;

    public void ModifiedChecker() {


    }


//    if not modified, return empty arraylist
//    if cpe modified, return null;
//    if ae modified, return a list of ae names

    public ArrayList<String> checkModifiedAEs(String cpeDescriptor, LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap) {
        ArrayList<String> modifiedAEs = new ArrayList<>();
        File cpeFile = new File(cpeDescriptor);
        boolean cpeModified = false;
        if (!cpeDescriptorsLastModified.containsKey(cpeDescriptor)
                || cpeDescriptorsLastModified.get(cpeDescriptor) != new File(cpeDescriptor).lastModified()) {
            cpeDescriptorsLastModified.put(cpeDescriptor, cpeFile.lastModified());
            cpeModified = true;
        }
        for (String aeName : externalConfigMap.keySet()) {
            if (!previousExternalConfigMap.containsKey(aeName)) {
                modifiedAEs.add(aeName);
                for (String configName : externalConfigMap.get(aeName).keySet()) {
                    if (configName.equals(DeterminantValueSet.PARAM_RULE_STR)) {
                        String file = externalConfigMap.get(aeName).get(configName);
                        long lastModified = new File(file).lastModified();
                        aeRuleFileLastModified.put(file, lastModified);
                    }
                }
                continue;
            }
            for (String configName : externalConfigMap.get(aeName).keySet()) {
                if (!previousExternalConfigMap.get(aeName).containsKey(configName)
                        || !previousExternalConfigMap.get(aeName).get(configName).equals(externalConfigMap.get(aeName).get(configName))) {
                    modifiedAEs.add(aeName);
                    break;
                }
                if (configName.equals(DeterminantValueSet.PARAM_RULE_STR) || configName.toLowerCase().endsWith("file")) {
                    String fileName = externalConfigMap.get(aeName).get(configName);
                    File file = new File(fileName);
                    if (file.exists()) {
                        long lastModified = new File(fileName).lastModified();
                        if (!aeRuleFileLastModified.containsKey(fileName) ||
                                aeRuleFileLastModified.get(fileName) != lastModified) {
                            modifiedAEs.add(aeName);
                            aeRuleFileLastModified.put(fileName, lastModified);
                            break;
                        }
                    }
                }
            }
        }
        previousExternalConfigMap = externalConfigMap;
        if (cpeModified)
            modifiedAEs = null;
        return modifiedAEs;
    }

    /**
     * If checkModifiedAEs, return a list of checkModifiedAEs aes
     * Otherwise, return an empty ArrayList.
     *
     * @param logTypes log types of each ae
     * @return a list of checkModifiedAEs aes
     */
    public ArrayList<String> checkModifiedLoggers(HashMap<String, String> logTypes) {
        ArrayList<String> modifiedLogger = new ArrayList<>();
        if (this.logTypes == null) {
            this.logTypes = logTypes;
            modifiedLogger.addAll(logTypes.keySet());
            return modifiedLogger;
        }

        for (String aeName : logTypes.keySet()) {
            if (!this.logTypes.containsKey(aeName)
                    || !this.logTypes.get(aeName).equals(logTypes.get(aeName))) {
                modifiedLogger.add(aeName);
            }
        }
        return modifiedLogger;

    }


}
