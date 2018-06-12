package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.metadata.CpeCasProcessor;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.junit.Test;

import java.io.File;
import java.util.LinkedHashMap;

public class AdaptableUIMACPEDescriptorRunnerTest {
    @Test
    public void test() throws CpeDescriptorException {
        AdaptableUIMACPEDescriptorRunner runner = new AdaptableUIMACPEDescriptorRunner("desc/cpe/Preannotator.xml");
        CollectionProcessingEngine mCPE = runner.mCPE;
        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
            System.out.println(cpeCasProcessor.getName());

        }

    }

    @Test
    public void test2() throws CpeDescriptorException {
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("FastNER", "@fastner\n" +
                "@splitter:|\n" +
                "very concept|ConceptA\n" +
                "tee|ConceptB");
        AdaptableUIMACPEDescriptorRunner runner = new AdaptableUIMACPEDescriptorRunner("desc/cpe/Preannotator.xml", configs);
        CollectionProcessingEngine mCPE = runner.mCPE;
        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
            System.out.println(cpeCasProcessor.getName());
        }
    }


    @Test
    public void test3() throws CpeDescriptorException {
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        AdaptableUIMACPEDescriptorRunner runner = new AdaptableUIMACPEDescriptorRunner("desc/cpe/n2c2_mi2.xml", "classes");
        runner.setLogger(new ConsoleLogger());
//        CollectionProcessingEngine mCPE = runner.mCPE;
//        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
//        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
//            System.out.println(cpeCasProcessor.getName());
//        }
        runner.run();
    }





    public static void main(String[] args) {
//        AdaptableCpmFrame.main(new String[]{});
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("SQL_Text_Reader/DBConfigFile","/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/test/sqliteconfig.xml");
        configs.put("SQLWriter_AE/SQLFile","/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/test/sqliteconfig.xml");
        configs.put("Section_Detector_R_AE/RuleFileOrStr","conf/mi/mi_section.xlsx");
        configs.put("FastNER_AE/RuleFileOrStr","conf/mi/mi_rule.xlsx");
        AdaptableUIMACPEDescriptorRunner runner = new AdaptableUIMACPEDescriptorRunner("desc/cpe/smoke_cpe.xml","classes");
        runner.setLogger(new NLPDBLogger(EDAO.getInstance(new File("/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke/sqliteconfig.xml"),true,false),"LOG","RUN_ID","test"));
//        runner.setLogger(new ConsoleLogger());
        runner.run();
    }
}