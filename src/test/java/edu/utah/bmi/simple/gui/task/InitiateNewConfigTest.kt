package edu.utah.bmi.simple.gui.task

import org.apache.commons.io.IOUtils
import org.junit.Test

import org.junit.Assert.*
import java.io.*

class InitiateNewConfigTest {

    @Test
    fun getConfigDirResource() {
        var fs = getResourceFiles("/demo_configurations/")
        print(fs)
        for(f in fs){
            var st = getResourceAsStream("/demo_configurations/$f")
            IOUtils.copy(st, FileOutputStream(File("target", f)))
        }


    }
    @Test
    fun getImage(){
        var ins=getResourceFiles("/edu/utah/bmi/simple/gui/")
        print(ins)
    }

    @Throws(IOException::class)
    fun getResourceFiles(path: String): List<String> = getResourceAsStream(path).use {
        return if (it == null) emptyList()
        else BufferedReader(InputStreamReader(it)).readLines().filter { s ->
            !s.toLowerCase().endsWith(".xml")
        }

    }

    private fun getResourceAsStream(resource: String): InputStream? =
            Thread.currentThread().contextClassLoader.getResourceAsStream(resource)
                    ?: resource::class.java.getResourceAsStream(resource)
}