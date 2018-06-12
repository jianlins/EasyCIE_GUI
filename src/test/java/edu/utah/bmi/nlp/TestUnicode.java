package edu.utah.bmi.nlp;


import edu.utah.bmi.nlp.easycie.reader.EhostReader;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jianlin Shi
 * Created on 3/21/17.
 */
public class TestUnicode {

    @Test
    public void test() {
        EhostReader er = new EhostReader();
        String content = er.readTextAsEhost(new File("/media/brokenjade/Data/Dropbox/JLS_HF_AnnotationTask/JLS_Ann_Training/Training_1/corpus/HBP Intro UTDOL.txt"));
        StringBuffer sb = removeUTFCharacters(content);

        System.out.println(content.substring(2861, 2875));


    }

    public static StringBuffer removeUTFCharacters(String data) {
        Pattern p = Pattern.compile("\\\\u(\\p{XDigit}{4})");
        Matcher m = p.matcher(data);
        StringBuffer buf = new StringBuffer(data.length());
        while (m.find()) {
            String ch = String.valueOf((char) Integer.parseInt(m.group(1), 16));
            m.appendReplacement(buf, Matcher.quoteReplacement(ch));
        }
        m.appendTail(buf);
        return buf;
    }

    @Test
    public void testCh() {

        System.out.println(Character.isAlphabetic('1'));
        System.out.println("a- b-".replaceAll("-", "_"));
    }
}
