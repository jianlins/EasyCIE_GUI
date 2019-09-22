package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sectiondectector.SectionDetectorR_AE;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.Date;
import edu.utah.bmi.nlp.type.system.Token;
import edu.utah.bmi.nlp.uima.AdaptableUIMACPERunner;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalContext_AE_Test extends TemporalAnnotator_AETest {

    protected AnalysisEngine temporalContextAE;

    @BeforeAll
    public static void clearTargetDir() {
        try {
            FileUtils.deleteDirectory(new File("target/target/generated-test-classes"));
            FileUtils.deleteDirectory(new File("target/generated-test-sources"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    protected void init(String[] ruleStrs) throws ResourceInitializationException {

        String typeDescriptor = "desc/type/customized";
        if (!new File(typeDescriptor + ".xml").exists()) {
            typeDescriptor = "desc/type/All_Types";
        }
        String sectionRule = "src/test/resources/edu.utah.bmi.nlp.uima.ae/00_Section_Detector.tsv";
        String rushRule = "src/test/resources/edu.utah.bmi.nlp.uima.ae/10_RuSH_AE.tsv";
        runner = new AdaptableUIMACPERunner(typeDescriptor, "target/generated-test-classes");
        runner.addConceptTypes(new FastNER_AE_General().getTypeDefs(ruleStrs[0]).values());
        runner.addConceptTypes(new SectionDetectorR_AE().getTypeDefs(sectionRule).values());
        runner.addConceptTypes(new TemporalAnnotator_AE().getTypeDefs(ruleStrs[1]).values());
        runner.addConceptTypes(new TemporalContext_AE().getTypeDefs(ruleStrs[2]).values());
        runner.reInitTypeSystem("target/generated-test-sources/customized", "target/generated-test-sources/");
        sectionDetector = AnalysisEngineFactory.createEngine(SectionDetectorR_AE.class, SectionDetectorR_AE.PARAM_RULE_STR, sectionRule);
        sentenceSegmentor = AnalysisEngineFactory.createEngine(RuSH_AE.class, RuSH_AE.PARAM_RULE_STR, rushRule,
                RuSH_AE.PARAM_TOKEN_TYPE_NAME, Token.class.getCanonicalName());
    }

    @Test
    void test1() throws ResourceInitializationException, AnalysisEngineProcessException {
        String inputText = "Record Date 1/27/2015\n" +
                "HPI:\n" +
                "Soft tissue infection 1/19/2015 \n";
        String recordDate = "01/20/2015", referenceDate = "01/02/2015";

        String nerRule = "@fastner\n" +
                "@CONCEPT_FEATURES\tINFECTION\tConcept\n" +
                "infection\tINFECTION";
        String tempRuleStr = "src/test/resources/edu.utah.bmi.nlp.uima.ae/50_TemporalAnnotator_AE.tsv";
        String tempContextRuleStr = "src/test/resources/edu.utah.bmi.nlp.uima.ae/60_TemporalContextAnnotator_AE.tsv";

        init(new String[]{nerRule, tempRuleStr, tempContextRuleStr});

        nerAE = AnalysisEngineFactory.createEngine(FastNER_AE_General.class,
                FastNER_AE_General.PARAM_RULE_STR, nerRule);

        temporalAnnotatorAE = AnalysisEngineFactory.createEngine(TemporalAnnotator_AE.class,
                TemporalAnnotator_AE.PARAM_RULE_STR, tempRuleStr,
                TemporalAnnotator_AE.PARAM_RECORD_DATE_COLUMN_NAME, "DATE",
                TemporalAnnotator_AE.PARAM_REFERENCE_DATE_COLUMN_NAME, "REF_DTM",
                TemporalAnnotator_AE.PARAM_INCLUDE_SECTIONS, "PresentHistory",
                TemporalAnnotator_AE.PARAM_AROUND_CONCEPTS, "INFECTION");
        temporalContextAE = AnalysisEngineFactory.createEngine(TemporalContext_AE.class,
                TemporalContext_AE.PARAM_RULE_STR, tempContextRuleStr,
                TemporalContext_AE.PARAM_RECORD_DATE_COLUMN_NAME, "DATE",
                TemporalContext_AE.PARAM_REFERENCE_DATE_COLUMN_NAME, "REF_DTM",
                TemporalContext_AE.PARAM_SAVE_DATE_DIFF, true
        );
        jCas = addMeta(inputText, recordDate, referenceDate);
        sectionDetector.process(jCas);
        sentenceSegmentor.process(jCas);
        nerAE.process(jCas);
        temporalAnnotatorAE.process(jCas);
        assertTrue(JCasUtil.select(jCas, Date.class).size() == 1);
        Date dt = JCasUtil.select(jCas, Date.class).iterator().next();
        assertTrue(dt.getElapse() == 408);
        assertTrue(dt.getTemporality().equals("in30d"));
        temporalContextAE.process(jCas);
        assertTrue(JCasUtil.select(jCas, Concept.class).size() == 2);
        Concept infection = (Concept) JCasUtil.select(jCas, AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace("INFECTION"))).iterator().next();
        System.out.println(infection);
        assertTrue(infection.getTemporality().equals("in30d"));
    }

}