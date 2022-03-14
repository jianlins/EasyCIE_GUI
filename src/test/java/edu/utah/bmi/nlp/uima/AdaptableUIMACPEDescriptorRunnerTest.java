package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import org.apache.uima.collection.impl.CollectionReaderDescription_impl;
import org.apache.uima.collection.metadata.CpeCasProcessor;
import org.apache.uima.collection.metadata.CpeCollectionReader;
import org.apache.uima.collection.metadata.CpeComponentDescriptor;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.junit.Test;

import java.io.File;
import java.sql.SQLException;
import java.util.LinkedHashMap;

import static java.lang.Thread.sleep;

public class AdaptableUIMACPEDescriptorRunnerTest {
    @Test
    public void testBasic() throws CpeDescriptorException {
        AdaptableCPEDescriptorGUIRunner runner = new AdaptableCPEDescriptorGUIRunner("desc/cpe/n2c2_mi2.xml", "test", null);
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
        AdaptableCPEDescriptorGUIRunner runner = AdaptableCPEDescriptorGUIRunner.getInstance("desc/cpe/n2c2_mi2.xml", null, configs);
        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
            System.out.println(cpeCasProcessor.getName());
        }
        runner.setUIMALogger(new ConsoleLogger());
        runner.compileCPE();

    }

    @Test
    public void testMetaReader() throws CpeDescriptorException {
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("FastNER/RuleFileOrStr", "@fastner\n" +
                "@splitter:|\n" +
                "very concept|ConceptA\n" +
                "tee|ConceptB");
        configs.put("SQL_Meta_Text_Reader/MetaColumns", "Code,Label");

        AdaptableCPEDescriptorGUIRunner runner = AdaptableCPEDescriptorGUIRunner.getInstance("desc/cpe/demo_meta_cpe.xml", null, configs);
        LinkedHashMap<String, LinkedHashMap<String, String>> configMap = new LinkedHashMap<>();
        CpeCollectionReader[] readers = runner.currentCpeDesc.getAllCollectionCollectionReaders();
        for (CpeCollectionReader reader : readers) {
            System.out.println(reader instanceof CollectionReaderDescription_impl);
            System.out.println(reader.getDescriptor().getSourceUrlString());
            System.out.println(reader.getClass().getSimpleName());
            CpeComponentDescriptor desc = reader.getCollectionIterator().getDescriptor();
            System.out.println(desc.getImport().getLocation());
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


    public static void main(String[] args) throws InterruptedException, SQLException {
//        AdaptableCpmFrame.main(new String[]{});
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("SQL_Text_Reader/DBConfigFile", "/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml");
        configs.put("SQL_Text_Reader/DocTableName", "SAMPLES");
        configs.put("SQLWriter/DBConfigFile", "/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml");
//        configs.put("FastContext/"+DeterminantValueSet.PARAM_RULE_STR, "");
//        configs.put("FeatureInference/"+DeterminantValueSet.PARAM_RULE_STR, "");
//        configs.put("SentenceInferencer/"+DeterminantValueSet.PARAM_RULE_STR, "");+
        File dbConfigFile = new File("/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml");
        EDAO dao = EDAO.getInstance(dbConfigFile, true, false);
        AdaptableCPEDescriptorGUIRunner runner = AdaptableCPEDescriptorGUIRunner.getInstance("desc/cpe/smoke_cpe2.xml", "test",
                new NLPDBLogger("/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml", "test"), configs
                , "classes");
//        runner.setUIMALogger(new ConsoleLogger());
//        runner.compileCPE();
//        runner.updateCpeProcessorConfiguration("Section_Detector_R_AE", SectionDetectorR_AE.PARAM_RULE_STR, "conf/asp/asp_section.xlsx");
//        runner.updateCpeProcessorConfiguration("FastNER_AE", SectionDetectorR_AE.PARAM_RULE_STR, "@fastner\t\t\n" +
//                "ASA\torg.apache.ctakes.type.system.ASP");
//        runner.compileCPE();

//        dao.stmt.execute("DELETE FROM RESULT_SNIPPET;");
//        dao.stmt.execute("DELETE FROM LOG;");
//        RecordRow record = dao.queryRecord("SELECT COUNT(*) FROM RESULT_SNIPPET;");
//        System.out.println(record);
//        SQLWriterCasConsumer.dao=new TDAO();
        runner.run();
        for (int i = 2; i > 0; i--) {
            sleep(1000);
//            System.out.println("count down: " + i);
        }
        dao = EDAO.getInstance(dbConfigFile, true, false);
        RecordRow record = dao.queryRecord("SELECT COUNT(*) FROM RESULT_SNIPPET;");
        System.out.println(record);


        System.out.println("2nd run");
//        dao.stmt.execute("DELETE FROM RESULT_SNIPPET;");
        runner.run();
        for (int i = 2; i > 0; i--) {
            sleep(1000);
//            System.out.println("count down: " + i);
        }

        dao = EDAO.getInstance(dbConfigFile, true, false);
        record = dao.queryRecord("SELECT COUNT(*) FROM RESULT_SNIPPET;");
        System.out.println(record);

        System.out.println("3rd run");
//        dao.stmt.execute("DELETE FROM RESULT_SNIPPET;");
        runner.run();
        for (int i = 2; i > 0; i--) {
            sleep(1000);
//            System.out.println("count down: " + i);
        }
        dao = EDAO.getInstance(new File("/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml"), true, false);
        record = dao.queryRecord("SELECT COUNT(*) FROM RESULT_SNIPPET;");
        System.out.println(record);

        System.out.println("4th run");
//        dao.stmt.execute("DELETE FROM RESULT_SNIPPET;");
        runner.run();
        for (int i = 2; i > 0; i--) {
            sleep(1000);
            System.out.println("count down: " + i);
        }
        dao = EDAO.getInstance(new File("/home/brokenjade/Documents/IdeaProjects/EasyCIE_GUI/conf/smoke3/sqliteconfig.xml"), true, false);
        record = dao.queryRecord("SELECT COUNT(*) FROM RESULT_SNIPPET;");
        System.out.println(record);

    }
}