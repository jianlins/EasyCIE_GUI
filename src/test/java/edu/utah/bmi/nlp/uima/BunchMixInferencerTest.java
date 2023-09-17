package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.type.system.Bunch_Base;
import edu.utah.bmi.nlp.uima.ae.BunchMixInferencer;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.Test;

import java.io.File;

import static edu.utah.bmi.nlp.uima.BunchMixInferenceWriterTest.processDoc;

public class BunchMixInferencerTest {
    protected AdaptableUIMACPERunner runner;
    protected JCas jCas;
    protected CAS cas;
    protected AnalysisEngine bunchInferer;

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
            runner.addConceptTypes(new BunchMixInferencer().getTypeDefs(ruleStr[0]).values());
        } else {
            runner.addConceptType("ASP_FOR_MI_MET", "Bunch_Base");
            runner.addConceptType("ASP_FOR_MI_NOT_MET", "Bunch_Base");
        }
        runner.reInitTypeSystem("target/generated-test-sources/customized");

    }

    @Test
    public void process3() throws AnalysisEngineProcessException, ClassNotFoundException, ResourceInitializationException {
        init();
        String ruleStr = "&DEFAULT_DOC_TYPE\tASP_FOR_MI_MET\tASP_FOR_MI_NOT_MET\n" +
                "ASP_FOR_MI_MET\tASP_FOR_MI_MET\tMI_DOC,ASP_DOC";

        bunchInferer = AnalysisEngineFactory.createEngine(BunchMixInferencer.class,
                BunchMixInferencer.PARAM_BUNCH_COLUMN_NAME, "BUNCH_ID",
                BunchMixInferencer.PARAM_RULE_STR, ruleStr);
        jCas = processDoc(runner, bunchInferer, 11, "MI_DOC");
        jCas = processDoc(runner, bunchInferer, 11, "Neg_MI_DOC");
        JCas previousJcas = jCas;
        jCas = processDoc(runner, bunchInferer, 12, "Neg_MI_DOC");
        System.out.println(JCasUtil.select(previousJcas, Bunch_Base.class));

    }

    @Test
    public void process4() throws AnalysisEngineProcessException, ClassNotFoundException, ResourceInitializationException {

        String ruleStr = "&DEFAULT_BUNCH_TYPE\tASP_FOR_MI_MET\tASP_FOR_MI_NOT_MET\n" +
                "ASP_FOR_MI_MET\tASP_FOR_MI_MET\tMI_DOC,ASP_DOC";
        init(ruleStr);
        bunchInferer = AnalysisEngineFactory.createEngine(BunchMixInferencer.class,
                BunchMixInferencer.PARAM_BUNCH_COLUMN_NAME, "BUNCH_ID",
                BunchMixInferencer.PARAM_RULE_STR, ruleStr);
        jCas = processDoc(runner, bunchInferer, 11, "MI_DOC");
        jCas = processDoc(runner, bunchInferer, 11, "ASP_DOC");
        JCas previousJcas = jCas;
        jCas = processDoc(runner, bunchInferer, 12, "Neg_MI_DOC");
        assert (JCasUtil.select(previousJcas, Bunch_Base.class).size() == 1);
    }


    @Test
    public void process5() throws AnalysisEngineProcessException, ClassNotFoundException, ResourceInitializationException {

        String ruleStr = "&DEFAULT_BUNCH_TYPE\tASP_FOR_MI_MET\tASP_FOR_MI_NOT_MET\n" +
                "&CONCEPT_FEATURES\tASP_FOR_MI_MET\tBunch_Base\tNote:null\n" +
                "ASP_FOR_MI_MET\tASP_FOR_MI_MET\tMI_DOC,ASP_DOC";
        init(ruleStr);
        bunchInferer = AnalysisEngineFactory.createEngine(BunchMixInferencer.class,
                BunchMixInferencer.PARAM_BUNCH_COLUMN_NAME, "BUNCH_ID", BunchMixInferencer.PARAM_SAVE_EVIDENCES, true,
                BunchMixInferencer.PARAM_RULE_STR, ruleStr);
        jCas = processDoc(runner, bunchInferer, 11, "MI_DOC");
        jCas = processDoc(runner, bunchInferer, 11, "ASP_DOC");
        bunchInferer.collectionProcessComplete();
        System.out.println(JCasUtil.select(jCas, Bunch_Base.class));
        assert (JCasUtil.select(jCas, Bunch_Base.class).size() == 1);
    }

}