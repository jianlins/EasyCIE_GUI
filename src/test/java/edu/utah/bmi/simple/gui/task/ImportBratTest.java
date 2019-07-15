package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.TestGUITask;
import org.junit.Test;

import static org.junit.Assert.*;

public class ImportBratTest {
    @Test
    public void test() {
        ImportBrat importBrat = new ImportBrat("conf/test1/test1_sqlite_config.xml", new TestGUITask(), true);
        importBrat.run("/home/brokenjade/Desktop/tmp/training_v2", "gold", 1, "REFERENCE", "src/main/resources/demo_configurations/10_RuSH_AE.tsv", "");
    }

    @Test
    public void test2() {
        ImportBrat importBrat = new ImportBrat("conf/test1/test1_sqlite_config.xml", new TestGUITask(), true);
        importBrat.run("/home/brokenjade/Desktop/tmp/training_v2", "gold", 1, "REFERENCE", "", "");
    }

}