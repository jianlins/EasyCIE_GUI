package edu.utah.bmi;

import edu.utah.bmi.uima.EhostReader;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jianlin Shi
 *         Created on 3/21/17.
 */
public class TestUnicode {

    @Test
    public void test() throws IOException {
        EhostReader er=new EhostReader();
        String content=er.readTextAsEhost(new File("/media/brokenjade/Data/Dropbox/JLS_HF_AnnotationTask/JLS_Ann_Training/Training_1/corpus/HBP Intro UTDOL.txt"));
        StringBuffer sb = removeUTFCharacters(content);

        System.out.println(content.substring(2861,2875));


    }

    public static StringBuffer removeUTFCharacters(String data){
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
}
