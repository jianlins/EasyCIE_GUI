package edu.utah.bmi.simple.gui.entry;

/**
 * @author Jianlin Shi
 * Created on 1/23/16.
 */
public interface SettingAb {


    public void init(String settingName, String settingValue, String settingDesc, String doubleClick, String openClick);

    public String getSettingName();

    public String getSettingValue();

    public String getSettingDesc();

    public String getDoubleClick();

    public String getOpenClick();
}
