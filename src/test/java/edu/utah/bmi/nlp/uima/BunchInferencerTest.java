package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.uima.ae.AnnotationEvaluator;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.AnnotationFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.logging.Level;

public class BunchInferencerTest {
    static AdaptableUIMACPERunner runner;
    static JCas jCas;
    static CAS cas;
    static AnalysisEngine bunchInferer;

    @BeforeClass
    public static void init() throws ResourceInitializationException {
        String typeDescriptor = "desc/type/customized";
        if (!new File(typeDescriptor + ".xml").exists()) {
            typeDescriptor = "desc/type/All_Types";
        }
        AdaptableUIMACPERunner runner = new AdaptableUIMACPERunner(typeDescriptor, "target/generated-test-sources/");
        runner.addConceptType("MI_6MOS_MET", "EntityBASE");
        runner.addConceptType("MI_6MOS_NOT_MET", "EntityBASE");
        runner.addConceptType("MI_DOC", "Doc_Base");
        runner.addConceptType("Neg_MI_DOC", "Doc_Base");
        runner.reInitTypeSystem("target/generated-test-sources/customized");
        jCas = runner.initJCas();

        EDAO.logger.setLevel(Level.FINE);

    }

    @Test
    public void test() throws ResourceInitializationException, AnalysisEngineProcessException {
        Concept concept = new Concept(jCas, 1, 2);
        concept.setNegation("negated");
        concept.addToIndexes();
//        runner.addAnalysisEngine(AnnotationEvaluator.class, new Object[]{AnnotationEvaluator.PARAM_TYPE_NAME, "Concept",
//                AnnotationEvaluator.PARAM_ANNO_IND, 0, AnnotationEvaluator.PARAM_FEATURE_NAME, "Negation", AnnotationEvaluator.PARAM_FEATURE_VALUE, "negated"});
        AnalysisEngine evaluator = AnalysisEngineFactory.createEngine(AnnotationEvaluator.class,
                AnnotationEvaluator.PARAM_TYPE_NAME, "Concept",
                AnnotationEvaluator.PARAM_ANNO_IND, 0,
                AnnotationEvaluator.PARAM_FEATURE_NAME, "Negation",
                AnnotationEvaluator.PARAM_FEATURE_VALUE, "negated");
        evaluator.process(jCas);
        System.out.println(AnnotationEvaluator.pass);
    }

    private void addMeta(JCas jCas, int bunchId) {
        RecordRow recordRow = new RecordRow().addCell("BUNCH_ID", bunchId).addCell("RUN_ID", 999);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jCas, 0, jCas.getDocumentText().length());
        srcDocInfo.setUri(recordRow.serialize());
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(jCas.getDocumentText().length());
        srcDocInfo.addToIndexes();
    }

    @Test
    public void process() throws AnalysisEngineProcessException, ClassNotFoundException, ResourceInitializationException {
        bunchInferer = AnalysisEngineFactory.createEngine(BunchInferencer.class,
                BunchInferencer.PARAM_BUNCH_COLUMN_NAME, "BUNCH_ID",
                BunchInferencer.PARAM_RULE_STR, "conf/mi/mi_PatInf.xlsx");

        processDoc(jCas, 11, "MI_DOC");
        processDoc(jCas, 11, "MI_DOC");
        processDoc(jCas, 11, "MI_DOC");
        processDoc(jCas, 11, "Neg_MI_DOC");
        processDoc(jCas, 11, "Neg_MI_DOC");
        processDoc(jCas, 12, "Neg_MI_DOC");
    }

    private void processDoc(JCas jCas, int bunchId, String docType) throws AnalysisEngineProcessException, ClassNotFoundException {
        String input = "test document";
        jCas.reset();
        jCas.setDocumentText(input);
        cas = jCas.getCas();
        addMeta(jCas, bunchId);
        Annotation annotation = AnnotationFactory.createAnnotation(jCas, 0, 1,
                Class.forName(DeterminantValueSet.checkNameSpace(docType)).asSubclass(Annotation.class));
        annotation.addToIndexes();
        bunchInferer.process(jCas);
    }
}