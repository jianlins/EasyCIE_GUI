package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.simple.gui.core.SettingOper;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExportEhostFromDBTest {
    @Test
    public void test() throws Exception {
        SettingOper settingOper = new SettingOper("conf/smoke3/smoke_config.xml");
        TasksFX tasks = settingOper.readSettings();
        ExportEhostFromDB ex = new ExportEhostFromDB(tasks);
        ex.call();
    }

}