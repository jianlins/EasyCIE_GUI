package edu.utah.bmi.nlp;


import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import edu.utah.bmi.nlp.uima.DynamicTypeGenerator;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCreationUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Jianlin Shi
 *         Created on 1/27/17.
 */
public class JCasTest {
    DynamicTypeGenerator dynamicTypeGenerator;
    AnalysisEngine RuSHae, FastCNERae, FastNERae, FastContext_Generalae, CoordinateNERResultsae;
    JCas jCas;

    @Before
    public void init() throws ResourceInitializationException, AnalysisEngineProcessException {
        String typeDescriptorURI = "desc/type/customized";
        dynamicTypeGenerator = new DynamicTypeGenerator(typeDescriptorURI);
        jCas = initJCas();
        initEngines();
    }

    private JCas process(JCas jCas) throws AnalysisEngineProcessException {
        RuSHae.process(jCas);
        FastCNERae.process(jCas);
        FastNERae.process(jCas);
        FastContext_Generalae.process(jCas);
        return jCas;
    }

    private void printAnnotations(JCas jCas) {
        FSIterator it = jCas.getAnnotationIndex(ConceptBASE.type).iterator();
        while (it.hasNext()) {
            ConceptBASE thisAnnotation = (ConceptBASE) it.next();
            System.out.println(thisAnnotation.getClass().getSimpleName());
            System.out.println("\t" + thisAnnotation.getCoveredText());
            System.out.println("\t" + thisAnnotation.getBegin());
            System.out.println("\t" + thisAnnotation.getEnd());
            System.out.println("\t" + thisAnnotation.getNegation());
            System.out.println("\t" + thisAnnotation.getTemporality());
            System.out.println("\t" + thisAnnotation.getExperiencer());
        }
    }


    public JCas initJCas() {
        JCas jCas = null;
        try {
            jCas = CasCreationUtils.createCas(dynamicTypeGenerator.getTypeSystemDescription(), null, null).getJCas();
        } catch (CASException e) {
            e.printStackTrace();
        } catch (ResourceInitializationException e) {
            e.printStackTrace();
        }
        return jCas;
    }

    public void initEngines() throws ResourceInitializationException {
        RuSHae = AnalysisEngineFactory.createEngine(
                RuSH_AE.class,
                RuSH_AE.PARAM_SENTENCE_TYPE_NAME, "edu.utah.bmi.type.system.Sentence",
                RuSH_AE.PARAM_TOKEN_TYPE_NAME, "edu.utah.bmi.type.system.Token",
                RuSH_AE.PARAM_RULE_STR, "conf/rush.csv",
                RuSH_AE.PARAM_INCLUDE_PUNCTUATION,true,
                RuSH_AE.PARAM_FIX_GAPS, true);
        FastCNERae = AnalysisEngineFactory.createEngine(
                FastCNER_AE_General.class, FastCNER_AE_General.PARAM_RULE_STR, "conf/fever_crule.csv", FastCNER_AE_General.PARAM_MARK_PSEUDO, true);
        FastNERae = AnalysisEngineFactory.createEngine(
                FastNER_AE_General.class, FastNER_AE_General.PARAM_RULE_STR, "conf/fever_rule.csv", FastNER_AE_General.PARAM_MARK_PSEUDO, true);
        FastContext_Generalae = AnalysisEngineFactory.createEngine(
                FastContext_General_AE.class, "RuleFile", "conf/context.csv",
                "Windowsize", 8,
                "SentenceTypeName", "edu.utah.bmi.type.system.Sentence",
                "TokenTypeName", "edu.utah.bmi.type.system.Token",
                "ConceptTypeName", "edu.utah.bmi.type.system.Concept",
                "ContextTypeName", "edu.utah.bmi.type.system.Concept",
                "NegationFeatureName", "Negation",
                "TemporalityFeatureName", "Temporality",
                "ExperiencerFeatureName", "Experiencer",
                "Debug", true, "CaseInsensitive", true);
    }

    @Test
    public void test1() throws AnalysisEngineProcessException {
        jCas.reset();
        jCas.setDocumentText("The patient was admitted because of persistent high fevers without a clear-cut source of infection. She had been having temperatures of up to 103 for 8-10 days.");
        process(jCas);
        printAnnotations(jCas);
    }




}
