package javafx.util.converter;

import edu.utah.bmi.simple.gui.entry.Setting;
import javafx.util.StringConverter;

import java.util.Map;

public class SettingValueConverter<T> extends StringConverter<Object[]> {
    private Object[]objects;


    public void setItem(Object[]objects) {
        this.objects = objects;
    }

    @Override
    public String toString(Object[] object) {
        this.objects=object;
        return ((Setting)objects[1]).getSettingValue();
    }

    @Override
    public Object[]fromString(String string) {
        ((Setting)objects[1]).setSettingValue(string);
        return objects;
    }

}