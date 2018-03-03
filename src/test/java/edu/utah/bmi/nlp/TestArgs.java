package edu.utah.bmi.nlp;

import edu.utah.bmi.simple.gui.core.CommonFunc;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Jianlin Shi
 * Created on 2/26/17.
 */
public class TestArgs {

    public TestArgs() {

    }

    @Test
    public void testArgs() {
        test();
        String a = "\u0097";
        File file = new File("data/output/ehost");
        System.out.println(file.getParentFile().getName());

    }

    private void test(String... args) {
        if (args != null && args.length > 0) {
            for (String arg : args)
                System.out.println(arg);
        }


    }

    @Test
    public void testOSCommand() throws InterruptedException, IOException {
        Runtime r = Runtime.getRuntime();
        Process p = r.exec("uname -a");
        p.waitFor();
        BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = "";

        while ((line = b.readLine()) != null) {
            System.out.println(line);
        }

        b.close();
    }


    @Test
    public void testOSCommandParse() throws Exception {
        String args = "edu.utah.bmi.simple.gui.task.ExecuteOsCommand -x export/ehost";
        String[] argsArray = args.split("(\"*\\s+\"*|^\"|\"$)");

        for (String ele : argsArray) {
            System.out.println(ele);
        }

        args = "edu.utah.bmi.simple.gui.task.ExecuteOsCommand -x \"export/ ehost\"";
//        String myArgs[] = Commandline.translateCommandline("-a hello -b world -c \"Hello world\"");
        String[] myArgs = CommandLineUtils.translateCommandline("-a hello -b world -c \"Hello world\"");
        argsArray = args.split("(\"*\\s+\"*|^\"|\"$)");
        for (String ele : myArgs) {
            System.out.println(ele);
        }
    }




}
