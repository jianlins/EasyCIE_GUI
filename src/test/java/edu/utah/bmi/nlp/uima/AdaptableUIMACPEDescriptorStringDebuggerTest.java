package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import org.apache.uima.collection.metadata.CpeCasProcessor;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.junit.Test;

import java.util.LinkedHashMap;

import static java.lang.Thread.sleep;

public class AdaptableUIMACPEDescriptorStringDebuggerTest {

    public static void main(String[] args) throws InterruptedException {
//        AdaptableCpmFrame.main(new String[]{});
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("SQL_Text_Reader/DBConfigFile", "/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml");
        configs.put("SQLWriter/DBConfigFile", "/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml");
//        AdaptableCPEDescriptorStringDebugger runner = new AdaptableCPEDescriptorStringDebugger("desc/cpe/smoke_cpe.xml",
//                new NLPDBLogger("/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml", "test"), configs
//                , "classes");



    }
}