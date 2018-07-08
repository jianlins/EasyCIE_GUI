package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import org.apache.uima.collection.base_cpm.CasProcessor;
import org.apache.uima.collection.metadata.CpeCasProcessor;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.junit.Test;

import java.util.LinkedHashMap;

import static java.lang.Thread.sleep;

public class AdaptableUIMACPEDescriptorRunnerTest {
    @Test
    public void testBasic() throws CpeDescriptorException {
        AdaptableCPEDescriptorRunner runner = new AdaptableCPEDescriptorRunner("desc/cpe/n2c2_mi2.xml", "test",null);
        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
            System.out.println(cpeCasProcessor.getName());
        }

    }

    @Test
    public void testExteranlConfigureMap() throws CpeDescriptorException {
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("FastNER", "@fastner\n" +
                "@splitter:|\n" +
                "very concept|ConceptA\n" +
                "tee|ConceptB");
        AdaptableCPEDescriptorRunner runner = AdaptableCPEDescriptorRunner.getInstance("desc/cpe/n2c2_mi2.xml", null, configs);
        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
            System.out.println(cpeCasProcessor.getName());
        }
    }

//
//    public static void main(String[] args) {
//        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
//        configs.put("SQLWriter_AE/Annotator", "test1");
//        configs.put("BunchInferenceWriter_AE/Annotator", "test1");
//        AdaptableCPEDescriptorRunner runner = AdaptableCPEDescriptorRunner.getInstance("desc/cpe/n2c2_mi2.xml",
//                configs, "classes");
//        runner.compileCPE();
//        CasProcessor[] processors = runner.getmCPE().getCasProcessors();
//        processors[processors.length - 1] = null;
//        runner.run();
//    }


    public static void main(String[] args) throws InterruptedException {
//        AdaptableCpmFrame.main(new String[]{});
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("SQL_Text_Reader/DBConfigFile", "/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml");
        configs.put("SQLWriter/DBConfigFile", "/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml");
        AdaptableCPEDescriptorRunner runner = AdaptableCPEDescriptorRunner.getInstance("desc/cpe/smoke_cpe.xml", "test",
                new NLPDBLogger("/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml", "test"), configs
                , "classes");
//        runner.setUIMALogger(new ConsoleLogger());
//        runner.compileCPE();
//        runner.updateCpeProcessorConfiguration("Section_Detector_R_AE", SectionDetectorR_AE.PARAM_RULE_STR, "conf/asp/asp_section.xlsx");
//        runner.updateCpeProcessorConfiguration("FastNER_AE", SectionDetectorR_AE.PARAM_RULE_STR, "@fastner\t\t\n" +
//                "ASA\torg.apache.ctakes.type.system.ASP");
//        runner.compileCPE();
        runner.run();
        sleep(20000);
        runner.run();

    }
}