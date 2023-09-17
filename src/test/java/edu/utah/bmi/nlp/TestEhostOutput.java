package edu.utah.bmi.nlp;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TestEhostOutput {

    @Test
    public void test() throws IOException {
        String text=FileUtils.readFileToString(new File("data/output/ehost/uima/corpus/1_1160_fulltxt.txt"),StandardCharsets.UTF_8);
//        System.out.println(text.substring(1501,1508));
        for (char ch : text.toCharArray()){
            if(Character.isAlphabetic(ch) || Character.isDigit(ch) ||  ch==','|| ch=='.'|| ch==':'|| ch=='"'|| ch=='\'')
                continue;
            int a=(int)ch;
            System.out.println(ch+"\t"+a);
        }

    }
}
