package edu.utah.bmi.simple.gui.task

import edu.utah.bmi.nlp.core.GUITask
import edu.utah.bmi.nlp.sql.DAO
import edu.utah.bmi.simple.gui.core.SettingOper
import edu.utah.bmi.simple.gui.entry.StaticVariables
import edu.utah.bmi.simple.gui.entry.TasksFX
import org.junit.BeforeClass
import org.junit.Test

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters


@FixMethodOrder(value = MethodSorters.NAME_ASCENDING)

class TaskTestBaseK {
    companion object {
        private var tasks: TasksFX? = null
        private var dao: DAO? = null
        private val sqliteConfig = "conf/sqliteconfig.xml"
        private val mysqlConfig = "conf/sqliteconfig.xml"
        private val dbConfig = sqliteConfig


        @BeforeClass
        @JvmStatic
        fun init() {
            val settingOper = SettingOper("conf/demo.xml")
            tasks = settingOper.readSettings()
            tasks!!.getTask("settings")!!.setValue(ConfigKeys.readDBConfigFile, dbConfig)
            tasks!!.getTask("settings")!!.setValue(ConfigKeys.writeConfigFileName, dbConfig)
            dao = DAO(File(dbConfig), true, false)
        }

    }


    private fun testTask(task: GUITask, seconds: Int = 5000) {
        val latch = CountDownLatch(1)
        val r = {
            task.guiCall()
            latch.countDown()  //and lets the junit thread when it is done
        }
        val th = Thread(r)
        th.start()
        try {
            assertTrue(latch.await(seconds.toLong(), TimeUnit.SECONDS)) //force junit to wait until you are done
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    @Test
    fun test1Import() {
        val runImport = Import(tasks, "txt")
        testTask(runImport)

    }

    @Test
    fun test2Runv1() {
        tasks!!.getTask(ConfigKeys.maintask).setValue(ConfigKeys.annotator, "v1")
        val task = RunEasyCIE(tasks)
        testTask(task)
    }

    @Test
    fun test2Runv2() {
        tasks!!.getTask(ConfigKeys.maintask).setValue(ConfigKeys.annotator, "v2")
        val task = RunEasyCIE(tasks)
        testTask(task)
    }

    @Test
    fun test3CompareTask() {
        tasks!!.getTask(ConfigKeys.comparetask).setValue(ConfigKeys.targetAnnotator, "v2")
        tasks!!.getTask(ConfigKeys.comparetask).setValue(ConfigKeys.targetRunId, "")
        tasks!!.getTask(ConfigKeys.comparetask).setValue(ConfigKeys.referenceAnnotator, "v1")
        tasks!!.getTask(ConfigKeys.comparetask).setValue(ConfigKeys.referenceRunId, "")
        val task = CompareTask(tasks)
        task.print = true
        testTask(task)
    }

    @Test
    fun test4Debug() {
        val task = RunEasyCIEDebugger(tasks)
        task.debugRunner.addReader("Resp: sats 94-99 3L NC, lungs coarse upper, diminished lower. strong non-productive cough, coughing reduced in frequency, prn robitussin w/ codeine prn, nebs via resp therapy. pt states no SOB.", "debug.doc")
        task.debugRunner.run()
    }

    @Test
    fun test5ExportEhost() {
        val task = RunEasyCIE(tasks, "ehost")
        testTask(task)
    }

    @Test
    fun test6ExportBrat() {
        val task = RunEasyCIE(tasks, "brat")
        testTask(task)
    }

    @Test
    fun test6ExportXMI() {
        val task = RunEasyCIE(tasks, "xmi")
        testTask(task)
    }

    @Test
    fun test6ExportExcel() {
        for ((i, color) in tasks!!.getTask("settings")!!.getValue("viewer/color_pool").split("|").withIndex()) {
            StaticVariables.colorPool.put(i, color.trim({ it <= ' ' }))
        }
        val task = Export2Excel(tasks)
        testTask(task)
    }

}

