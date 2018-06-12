package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.simple.gui.core.SettingOper;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.GenericAdaptableCPERunner;
import org.junit.Test;

/**
 * Created by
 *
 * @Author Jianlin Shi on 4/8/17.
 */
public class GenericAdaptableCPERunnerTest {
    @Test
    public void test() {
        SettingOper settingOper = new SettingOper("conf/sqliteconfig.xml");
        TasksFX tasksFX = settingOper.readSettings();
        GenericAdaptableCPERunner runner = new GenericAdaptableCPERunner(tasksFX, "testPipeline");
        runner.run();

    }

}