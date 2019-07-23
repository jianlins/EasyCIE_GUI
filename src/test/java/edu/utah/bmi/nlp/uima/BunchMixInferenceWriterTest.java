package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.TDAO;
import edu.utah.bmi.nlp.type.system.Bunch_Base;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.EntityBASE;
import edu.utah.bmi.nlp.uima.ae.AnnotationEvaluator;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.AnnotationFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.Test;

import java.io.File;
import java.util.logging.Level;

public class BunchMixInferenceWriterTest {
    protected AdaptableUIMACPERunner runner;
    protected JCas jCas;
    protected CAS cas;
    protected AnalysisEngine bunchInferer;
    protected String db = "src/main/resources/demo_configurations/demo_sqlite_config.xml";


    protected void init(String... ruleStr) {

        String typeDescriptor = "desc/type/customized";
        if (!new File(typeDescriptor + ".xml").exists()) {
            typeDescriptor = "desc/type/All_Types";
        }
        runner = new AdaptableUIMACPERunner(typeDescriptor, "target/generated-test-sources/");

        runner.addConceptType("MI_DOC", "Doc_Base");
        runner.addConceptType("Neg_MI_DOC", "Doc_Base");
        runner.addConceptType("ASP_DOC", "Doc_Base");
        if (ruleStr.length > 0) {
            runner.addConceptTypes(new BunchMixInferenceWriter().getTypeDefs(ruleStr[0]).values());
        } else {
            runner.addConceptType("ASP_FOR_MI_MET", "Bunch_Base");
            runner.addConceptType("ASP_FOR_MI_NOT_MET", "Bunch_Base");
        }
        runner.reInitTypeSystem("target/generated-test-sources/customized");
//        jCas = runner.initJCas();
        EDAO.logger.setLevel(Level.FINEST);
        EDAO.instances.put(new File(db).getAbsolutePath(), new TDAO());
    }


    protected static void addMeta(JCas jCas, int bunchId) {
        RecordRow recordRow = new RecordRow().addCell("BUNCH_ID", bunchId).addCell("RUN_ID", 999);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jCas, 0, jCas.getDocumentText().length());
        srcDocInfo.setUri(recordRow.serialize());
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(jCas.getDocumentText().length());
        srcDocInfo.addToIndexes();
    }

    @Test
    public void process() throws AnalysisEngineProcessException, ClassNotFoundException, ResourceInitializationException {
        init();
        String ruleStr = "&DefaultBunchConclusion\tASP_FOR_MI_MET\tASP_FOR_MI_NOT_MET\n" +
                "ASP_FOR_MI_MET\tASP_FOR_MI_MET\tMI_DOC,ASP_DOC";
        bunchInferer = AnalysisEngineFactory.createEngine(BunchMixInferenceWriter.class,
                BunchMixInferenceWriter.PARAM_SQLFILE, "src/main/resources/demo_configurations/demo_sqlite_config.xml",
                BunchMixInferenceWriter.PARAM_BUNCH_COLUMN_NAME, "BUNCH_ID",
                BunchMixInferenceWriter.PARAM_RULE_STR, ruleStr);
        processDoc(runner,bunchInferer,11, "MI_DOC");
        processDoc(runner,bunchInferer,11, "ASP_DOC");
        processDoc(runner,bunchInferer,11, "Neg_MI_DOC");
        processDoc(runner,bunchInferer,12, "Neg_MI_DOC");
        assert (TDAO.getInstance(new File(db)).getLastStatement().contains("ASP_FOR_MI_MET"));

    }

    @Test
    public void process2() throws AnalysisEngineProcessException, ClassNotFoundException, ResourceInitializationException {
        init();
        String ruleStr = "&DefaultBunchConclusion\tASP_FOR_MI_MET\tASP_FOR_MI_NOT_MET\n" +
                "ASP_FOR_MI_MET\tASP_FOR_MI_MET\tMI_DOC,ASP_DOC,Neg_MI_DOC";
        bunchInferer = AnalysisEngineFactory.createEngine(BunchMixInferenceWriter.class,
                BunchMixInferenceWriter.PARAM_SQLFILE, "src/main/resources/demo_configurations/demo_sqlite_config.xml",
                BunchMixInferenceWriter.PARAM_BUNCH_COLUMN_NAME, "BUNCH_ID",
                BunchMixInferenceWriter.PARAM_RULE_STR, ruleStr);
        BunchMixInferenceWriter.dao = new TDAO();
        processDoc(runner,bunchInferer,11, "MI_DOC");
        processDoc(runner,bunchInferer,11, "ASP_DOC");
        processDoc(runner,bunchInferer,11, "Neg_MI_DOC");
        processDoc(runner,bunchInferer,12, "Neg_MI_DOC");
        assert (TDAO.getInstance(new File(db)).getLastStatement().contains("ASP_FOR_MI_MET"));
    }


    protected static JCas processDoc(AdaptableUIMACPERunner runner, AnalysisEngine bunchInferer, int bunchId, String docType) throws AnalysisEngineProcessException, ClassNotFoundException {
        String input = "test document";
        JCas jCas = runner.initJCas();
        jCas.setDocumentText(input);
        CAS cas = jCas.getCas();
        addMeta(jCas, bunchId);
        Annotation annotation = AnnotationFactory.createAnnotation(jCas, 0, 1,
                Class.forName(DeterminantValueSet.checkNameSpace(docType)).asSubclass(Annotation.class));
        annotation.addToIndexes();
        bunchInferer.process(jCas);
        return jCas;
    }
}