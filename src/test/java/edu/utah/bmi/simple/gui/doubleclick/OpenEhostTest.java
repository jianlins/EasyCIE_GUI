package edu.utah.bmi.simple.gui.doubleclick;

import edu.utah.bmi.simple.gui.core.SettingOper;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static java.lang.Thread.sleep;
import static org.junit.Assert.*;

public class OpenEhostTest {
    private static TasksFX tasks;

    @BeforeClass
    public static void init() {
        SettingOper oper = new SettingOper("conf/smoke3/smoke_config.xml");
        tasks = oper.readSettings();
    }

    @Test
    public void testOpen() throws Exception {
        OpenEhost open = new OpenEhost(tasks, "exp", "167_r.txt");
        open.guiEnabled = false;
        open.call();
        sleep(5000);
        System.out.println("Close eHOST");
        OpenEhost.closeEhost("127.0.0.1", "8009");
    }

    @Test
    public void testStatus() {
        System.out.println(new OpenEhost(tasks, "exp", "167_r.txt").checkEhostStatus(1,"127.0.0.1", "8009"));
    }


}