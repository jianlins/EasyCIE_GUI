package edu.utah.bmi.simple.gui.entry;

/**
 * @author Jianlin Shi
 * Created on 1/23/16.
 */
public interface SettingAb {


    void init(String settingName, String settingValue, String settingDesc, String doubleClick, String openClick);

    String getSettingName();

    String getSettingValue();

    String getSettingDesc();

    String getDoubleClick();

    String getOpenClick();
}
