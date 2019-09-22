package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.type.system.Date;
import edu.utah.bmi.nlp.type.system.SectionBody;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class TemporalContextChunker_AETest extends TemporalContext_AE_Test {

    protected AnalysisEngine temporalContextAE, temporalContextChunkAE;

    @BeforeAll
    public static void clearTargetDir() {
        try {
            FileUtils.deleteDirectory(new File("target/target/generated-test-classes"));
            FileUtils.deleteDirectory(new File("target/generated-test-sources"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Test
    void test1() throws ResourceInitializationException, AnalysisEngineProcessException {
        String inputText = "Record Date 1/27/2015\n" +
                "HPI:\n" +
                "Soft tissue infection 1/10/2015. Developed abdomen infection later. On 1/19/2015, the pt went to urgent care. The infection exacerbated. " +
                "The patient visited the ED on 1/22. And then he was admitted to the S Hospital for infection treatement.\n";
        String recordDate = "01/25/2015", referenceDate = "01/02/2015";

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
        temporalContextChunkAE = AnalysisEngineFactory.createEngine(TemporalContextChunker_AE.class,
                TemporalContextChunker_AE.PARAM_RULE_STR, tempContextRuleStr,
                TemporalContextChunker_AE.PARAM_RECORD_DATE_COLUMN_NAME, "DATE",
                TemporalContextChunker_AE.PARAM_REFERENCE_DATE_COLUMN_NAME, "REF_DTM",
                TemporalContextChunker_AE.PARAM_INCLUDE_SECTIONS, "PresentHistory",
                TemporalContext_AE.PARAM_SAVE_DATE_DIFF, true
        );
        jCas = addMeta(inputText, recordDate, referenceDate);
        sectionDetector.process(jCas);

        System.out.println(JCasUtil.select(jCas, SectionBody.class));
        sentenceSegmentor.process(jCas);
        nerAE.process(jCas);
        temporalAnnotatorAE.process(jCas);
        for (Date date : JCasUtil.select(jCas, Date.class)) {
            System.out.println(date.getBegin() + ":" + date.getNormDate() + "---" + date.getElapse());
        }

        temporalContextAE.process(jCas);
        Class<? extends Annotation> infectionCls = AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace("INFECTION"));
        Method diffMethod = AnnotationOper.getFeatureMethod(infectionCls, "AfterRefInHours");

        System.out.println("\n\n\n\n---------------------------------------------\n");
        for (Annotation concept : JCasUtil.select(jCas, infectionCls)) {
            System.out.println(concept.getBegin() + ":" + inputText.substring(concept.getBegin() - 10, concept.getEnd() + 5) + "---" + AnnotationOper.getFeatureValue(diffMethod, concept));
        }

        temporalContextChunkAE.process(jCas);
        System.out.println("\n\n\n\n---------------------------------------------\n");
        for (Annotation concept : JCasUtil.select(jCas, infectionCls)) {
            System.out.println(concept.getBegin() + ":" + inputText.substring(concept.getBegin() - 10, concept.getEnd() + 5) + "---" + AnnotationOper.getFeatureValue(diffMethod, concept));
        }

    }


}