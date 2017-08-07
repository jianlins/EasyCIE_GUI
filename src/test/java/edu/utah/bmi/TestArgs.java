package edu.utah.bmi;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author Jianlin Shi
 *         Created on 2/26/17.
 */
public class TestArgs {

    public TestArgs() {

    }

    @Test
    public void testArgs() {
        test();
        String a="\u0097";
        File file=new File("data/output/ehost");
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

}
