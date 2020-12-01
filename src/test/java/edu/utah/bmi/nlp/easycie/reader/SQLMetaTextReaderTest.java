package edu.utah.bmi.nlp.easycie.reader;

import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorRunner;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SQLMetaTextReaderTest {
    private AdaptableCPEDescriptorRunner runner;
    private JCas jCas;
    private AnalysisEngine printer;
    private String typeDescriptor;

    @Test
    void genXMLDescriptor() throws ResourceInitializationException, IOException, SAXException {
        CollectionReaderDescription readerEngine = CollectionReaderFactory.createReaderDescription(SQLMetaTextReader.class,
                SQLMetaTextReader.PARAM_DB_CONFIG_FILE, "");
        readerEngine.toXML(
                new FileOutputStream("target/generated-test-sources/SQLMetaTextReader.xml"));
    }

    private void init() {
        typeDescriptor = "desc/type/customized";
        if (!new File(typeDescriptor + ".xml").exists()) {
            typeDescriptor = "desc/type/All_Types";
        }


        runner = AdaptableCPEDescriptorRunner.getInstance("src/test/resources/desc/test_reader_cpe.xml", "test", "target/generated-test-sources/");
        runner.setUIMALogger(new ConsoleLogger());
    }

    private void run() {
        init();
        runner.updateReadDescriptorsConfiguration(SQLTextReader.PARAM_DB_CONFIG_FILE, "conf/smoke4/dbconfig/sqliteconfig.xml");
        runner.updateReadDescriptorsConfiguration(SQLTextReader.PARAM_DOC_TABLE_NAME, "HAI_PATOS_TEST");
        runner.updateReadDescriptorsConfiguration(SQLTextReader.PARAM_DATASET_ID, 0);
        runner.run();
    }

    public static void main(String[] args) {
        new SQLMetaTextReaderTest().run();
    }
}