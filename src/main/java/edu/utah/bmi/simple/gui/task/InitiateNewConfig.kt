package edu.utah.bmi.simple.gui.task

import edu.utah.bmi.simple.gui.core.SettingOper
import java.io.File

/**
 * Initiate EasyCIE configuration file and db configuration file
 * Import text documents and gold standard annotations
 *
 */

class InitiateNewConfig(val configId: Int, val configDir: String, val configPrefix: String, val dbPrefix: String,
                        val configTemp: String = "", val dbTemp: String = "", val annotator: String = "v1") {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty() || args.size < 4) {
                println("This is a commandline interface to create EasyCIE configuration and corresponding sqlite database configuration from template xml files.\n" +
                        "It takes 4 arguments:\n" +
                        "1: configuration Id\n" +
                        "2: configuration directory (where the configuration files will be stored)\n" +
                        "3: configuration file prefix\n" +
                        "4: sqlite database configuration file prefix\n")
            } else {
                var id = args[0].toInt()
                println(args.toList())
                var init: InitiateNewConfig? = null
                when (args.size) {
                    4 -> init = InitiateNewConfig(id, args[1], args[2], args[3])
                    5 -> init = InitiateNewConfig(id, args[1], args[2], args[3], args[4])
                    6 -> init = InitiateNewConfig(id, args[1], args[2], args[3], args[4], args[5])
                    7 -> init = InitiateNewConfig(id, args[1], args[2], args[3], args[4], args[5], args[6])
                }
                if (init != null) {
                    init.gen()
//                    init.importData()
                }
            }
        }
    }

    fun gen() {
        var configTxt: String
        var dbConfigTxt: String
        if (configTemp.isEmpty()) {
            configTxt = InitiateNewConfig::class.java.getResource("/$configPrefix.xml").readText()
            dbConfigTxt = InitiateNewConfig::class.java.getResource("/$dbPrefix.xml").readText()
        } else {
            configTxt = File(configTemp).readText()
            dbConfigTxt = File(dbTemp).readText()
        }
        var dbConfigFileName = "$configDir/$dbPrefix$configId.xml"
        configTxt = configTxt.replace("{batchId}", "$configId")
        configTxt = configTxt.replace("{configDir}", "$configDir")
        configTxt = configTxt.replace("{dbPrefix}", "$dbPrefix")
        configTxt = configTxt.replace("{annotator}", "$annotator")
        dbConfigTxt = dbConfigTxt.replace("{batchId}", "$configId")

        File(configDir, "$configPrefix$configId.xml").writeText(configTxt)
        File(configDir, "$dbPrefix$configId.xml").writeText(dbConfigTxt)
    }

    fun importData() {
        val settingOper = SettingOper(File(configDir, "$configPrefix$configId.xml").getAbsolutePath())
        val tasks = settingOper.readSettings()
        Import(tasks, "txt").call()
        Import(tasks, "anno").call()
    }


}

