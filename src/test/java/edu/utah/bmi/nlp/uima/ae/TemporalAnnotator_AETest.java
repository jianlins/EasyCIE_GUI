package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sectiondectector.SectionDetectorR_AE;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.Date;
import edu.utah.bmi.nlp.uima.AdaptableUIMACPERunner;
import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalAnnotator_AETest {
    protected AnalysisEngine sectionDetector, sentenceSegmentor;
    protected AdaptableUIMACPERunner runner;
    protected JCas jCas;
    protected CAS cas;
    protected AnalysisEngine temporalAnnotatorAE, nerAE, cnerTestAe;

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
        Collection<TypeDefinition> types = new FastNER_AE_General().getTypeDefs(ruleStrs[0]).values();
        runner.addConceptTypes(types);
        types=new SectionDetectorR_AE().getTypeDefs(sectionRule).values();
        runner.addConceptTypes(types);
        types=new TemporalAnnotator_AE().getTypeDefs(ruleStrs[1]).values();
        runner.addConceptTypes(types);

        runner.reInitTypeSystem("target/generated-test-sources/customized", "target/generated-test-sources/");
        sectionDetector = AnalysisEngineFactory.createEngine(SectionDetectorR_AE.class, SectionDetectorR_AE.PARAM_RULE_STR, sectionRule);
        sentenceSegmentor = AnalysisEngineFactory.createEngine(RuSH_AE.class, RuSH_AE.PARAM_RULE_STR, rushRule,RuSH_AE.PARAM_TOKEN_TYPE_NAME,"Token");
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

        init(new String[]{nerRule, tempRuleStr});

        nerAE = AnalysisEngineFactory.createEngine(FastNER_AE_General.class, FastNER_AE_General.PARAM_RULE_STR, nerRule);

        temporalAnnotatorAE = AnalysisEngineFactory.createEngine(TemporalAnnotator_AE.class,
                TemporalAnnotator_AE.PARAM_RULE_STR, tempRuleStr,
                TemporalAnnotator_AE.PARAM_RECORD_DATE_COLUMN_NAME, "DATE",
                TemporalAnnotator_AE.PARAM_REFERENCE_DATE_COLUMN_NAME, "REF_DTM",
                TemporalAnnotator_AE.PARAM_INCLUDE_SECTIONS, "PresentHistory",
                TemporalAnnotator_AE.PARAM_AROUND_CONCEPTS, "INFECTION");
        jCas = addMeta(inputText, recordDate, referenceDate);
        sectionDetector.process(jCas);
        sentenceSegmentor.process(jCas);
        nerAE.process(jCas);
        System.out.println(JCasUtil.select(jCas, Concept.class));
        temporalAnnotatorAE.process(jCas);
        System.out.println(JCasUtil.select(jCas, Date.class));
        assertTrue(JCasUtil.select(jCas, Date.class).size() == 1);
        Date anno = JCasUtil.select(jCas, Date.class).iterator().next();
        System.out.println(inputText.substring(anno.getBegin(), anno.getEnd()));
    }


    protected JCas addMeta(String text, String recordDate, String referenceDate) {
        JCas jCas = runner.initJCas();
        jCas.setDocumentText(text);
        RecordRow recordRow = new RecordRow().addCell("DATE", recordDate).addCell("REF_DTM", referenceDate);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jCas, 0, jCas.getDocumentText().length());
        srcDocInfo.setUri(recordRow.serialize());
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(jCas.getDocumentText().length());
        srcDocInfo.addToIndexes();
        return jCas;
    }

}