package edu.utah.bmi.simple.gui.entry;


import javafx.beans.property.SimpleStringProperty;

public class Setting implements SettingAb {
    protected SimpleStringProperty settingName;


    protected SimpleStringProperty settingValue;
    protected SimpleStringProperty settingDesc;
    protected SimpleStringProperty doubleClick;
    protected SimpleStringProperty openClick;

    public Setting(String settingName, String settingValue, String settingDesc, String doubleClick) {
        init(settingName, settingValue, settingDesc, doubleClick, "");
    }

    public Setting(String settingName, String settingValue, String settingDesc, String doubleClick, String openClick) {
        init(settingName, settingValue, settingDesc, doubleClick, openClick);
    }

    public void init(String settingName, String settingValue, String settingDesc, String doubleClick, String openClick) {
        this.settingName = new SimpleStringProperty(settingName);
        this.settingValue = new SimpleStringProperty(settingValue);
        this.settingDesc = new SimpleStringProperty(settingDesc);
        this.doubleClick = new SimpleStringProperty(doubleClick);
        this.openClick = new SimpleStringProperty(openClick);
    }

    public String getSettingName() {
        return this.settingName.get();
    }

    public String getSettingValue() {
        return this.settingValue.get();
    }

    public String getSettingDesc() {
        return this.settingDesc.get();
    }

    public String getDoubleClick() {
        return this.doubleClick.get();
    }

    public String getOpenClick() {
        return this.openClick.get();
    }


    public SimpleStringProperty settingNameProperty() {
        return this.settingName;
    }

    public SimpleStringProperty settingValueProperty() {
        return this.settingValue;
    }

    public void setSettingValue(String settingValue){
        this.settingValue=new SimpleStringProperty(settingValue);
    }

    public SimpleStringProperty settingDescProperty() {
        return this.settingDesc;
    }

    public SimpleStringProperty doubleClickProperty() {
        return this.doubleClick;
    }

    public SimpleStringProperty openClickProperty() {
        return this.openClick;
    }

    public String serialize() {
        return this.settingName.get() + "|" + this.settingValue.get() + "|" + this.settingDesc.get()
                + "|" + this.doubleClick.get() + "|" + this.openClick.get();
    }

    public String toString() {
        return getSettingName();
    }

    public void setSettingNameProperty(String settingName) {
        this.settingName = new SimpleStringProperty(settingName);
    }


}
