package edu.utah.bmi.simple.gui.task

import edu.utah.bmi.nlp.core.GUITask
import edu.utah.bmi.nlp.sql.DAO
import edu.utah.bmi.simple.gui.core.SettingOper
import edu.utah.bmi.simple.gui.entry.TasksFX
import org.junit.BeforeClass
import org.junit.Test

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

class EvalGUITask(val f: () -> Unit) : GUITask() {
    override fun call() {
        f()
    }
}
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

    private fun creatGUITask(sql: String, value: Int): GUITask {
        return EvalGUITask({
            if (value == -1)
                print(dao!!.queryRecord(sql))
            else
                assertTrue(dao!!.queryRecord(sql).getValueByColumnId(0).equals(value))
        })
    }

    private fun testTask(task: GUITask, seconds: Int = 5000, evalTask: GUITask? = null) {
        val latch = CountDownLatch(1)
        val r = {
            task.guiCall()
            latch.countDown()  //and lets the junit thread when it is done
            if (evalTask != null)
                evalTask.guiCall()
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
        testTask(runImport, 5000, creatGUITask("SELECT COUNT(*) FROM DOCUMENTS;", -1))
    }

    @Test
    fun test2Runv1() {
        tasks!!.getTask(ConfigKeys.maintask).setValue(ConfigKeys.annotator,"v1")
        val task = RunEasyCIE(tasks)
        testTask(task, 5000, creatGUITask("SELECT COUNT(*) FROM OUTPUT;", -1))
    }

    @Test
    fun test2Runv2() {
        tasks!!.getTask(ConfigKeys.maintask).setValue(ConfigKeys.annotator,"v2")
        val task = RunEasyCIE(tasks)
        testTask(task, 5000, creatGUITask("SELECT COUNT(*) FROM OUTPUT;", -1))
    }

    @Test
    fun test3CompareTask(){

    }

}

