package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.core.SettingOper;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class TaskTestBase {
    private static TasksFX tasks;
    private static EDAO dao;
    private static String sqliteConfig = "conf/sqliteconfig.xml";
    private static String mysqlConfig = "conf/sqliteconfig.xml";
    private static String dbConfig = sqliteConfig;


    @BeforeClass
    public static void init() {
        SettingOper settingOper = new SettingOper("conf/demo.xml");
        tasks = settingOper.readSettings();
        tasks.getTask("settings").setValue(ConfigKeys.readDBConfigFileName, dbConfig);
        tasks.getTask("settings").setValue(ConfigKeys.writeDBConfigFileName, dbConfig);
        dao = EDAO.getInstance(new File(dbConfig), true, false);
    }


    private void testTask(GUITask task) {
        testTask(task, 5000);
    }

    private void testTask(GUITask task, int seconds) {
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable r = () -> {
            task.guiCall();
            latch.countDown();  //and lets the junit thread when it is done
        };
        Thread th = new Thread(r);
        th.start();
        try {
            assertTrue(latch.await(seconds, TimeUnit.SECONDS)); //force junit to wait until you are done
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testImport() {
        final Import runImport = new Import(tasks, "txt");
        testTask(runImport);
        RecordRow recordRow = dao.queryRecord("SELECT COUNT(*) FROM DOCUMENTS;");
        System.out.println(recordRow);
    }

    @Test
    public void testRun() {
        final RunEasyCIE task = new RunEasyCIE(tasks);
        testTask(task);
        RecordRow recordRow = dao.queryRecord("SELECT COUNT(*) FROM OUTPUT;");
        System.out.println(recordRow);
    }


}
