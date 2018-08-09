package edu.utah.bmi.simple.gui.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Jianlin Shi
 *         Created on 6/28/16.
 */
public class OSChecker {
    protected HashMap<String, String> checker = new HashMap<>();

    public OSChecker(Properties comProp) {
        for (Map.Entry<Object, Object> entry : comProp.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("os.")) {
                String values = entry.getValue().toString().trim();
                for (String value : values.split("\\|")) {
                    checker.put(value, key.substring(3));
                }
            }
        }
    }

    /**
     * Input the cat /etc/*-release raw output, can return the os type.
     *
     * @param catReleaseInfo catReleaseInfo
     * @return os type name
     */
    public String getOSType(String catReleaseInfo) {
        catReleaseInfo = catReleaseInfo.toLowerCase();
        String sysinfo = "";
        if (catReleaseInfo.indexOf("id=") != -1) {
            sysinfo = catReleaseInfo.substring(catReleaseInfo.indexOf("id=") + 3);
            sysinfo = sysinfo.substring(0, sysinfo.indexOf("\n"));
            sysinfo = sysinfo.replaceAll("\"", "");
        } else {
            if (catReleaseInfo.indexOf("centos") != -1) {
                sysinfo = "centos";
            }
        }
        return checker.get(sysinfo);
    }
}
