package edu.utah.bmi.simple.gui.entry;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class TaskFX {
    protected ObservableMap<String, SettingAb> paras;
    protected final LinkedHashMap<String, SettingAb> map = new LinkedHashMap<String, SettingAb>();
    protected final LinkedHashMap<String, SettingAb> executes = new LinkedHashMap<String, SettingAb>();
    protected String taskName;
    protected final String generalTask = "General";

    public TaskFX() {
        init(generalTask);
    }

    public TaskFX(String taskName) {

        init(taskName);
    }

    public ObservableList<Map.Entry<String, SettingAb>> getSettings() {
        return FXCollections.observableArrayList(paras.entrySet());
    }

    public void init(String taskName) {
        this.taskName = taskName;
        paras = FXCollections.observableMap(map);
    }

    public String getValue(String key) {
        if (map.containsKey(key)) {
            return map.get(key).getSettingValue();
        } else {
            return "";
        }
    }

    public String getValue(String key, String defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key).getSettingValue();
        } else {
            return defaultValue;
        }
    }

    public String getDesc(String key) {
        if (map.containsKey(key)) {
            return map.get(key).getSettingDesc();
        } else {
            return "";
        }
    }

    public String getName(String key) {
        if (map.containsKey(key)) {
            return map.get(key).getSettingName();
        } else {
            return "";
        }
    }


    public void setValue(String key, String value, String desc, String doubleClick) {
        setValue(key, value, desc, doubleClick, "");
    }

    public void setValue(String key, String value, String desc, String doubleClick, String openClick) {
        if (desc == null) {
            desc = "";
        }
        paras.put(key, new Setting(key, value, desc, doubleClick, openClick));
        map.put(key, new Setting(key, value, desc, doubleClick, openClick));
    }

    public void setValue(String key, String value) {
        setValue(key, value, "", "");
    }

    public String getTaskName() {
        return this.taskName;
    }

    public void addExecute(String label, String className, String memo) {
        executes.put(label, new Setting(label, className, memo, ""));
    }

    public ObservableList<Map.Entry<String, SettingAb>> getExecutes() {
        return FXCollections.observableArrayList(executes.entrySet());
    }

    public String getExecuteValue(String key) {
        if (executes.containsKey(key)) {
            return executes.get(key).getSettingValue();
        } else {
            return "";
        }
    }

    public String getExecuteDesc(String key) {
        if (executes.containsKey(key)) {
            return executes.get(key).getSettingDesc();
        } else {
            return "";
        }
    }

    public LinkedHashMap<String, SettingAb> getChildSettings(String parentKey) {
        LinkedHashMap<String, SettingAb> child = new LinkedHashMap<>();
        int parentKeyLength = parentKey.length() + 1;
        for (Map.Entry<String, SettingAb> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(parentKey) && key.length() > parentKeyLength) {
                child.put(key.substring(parentKeyLength), entry.getValue());
            }
        }
        return child;
    }

}
