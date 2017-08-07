/*******************************************************************************
 * Copyright  2016  Department of Biomedical Informatics, University of Utah
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package edu.utah.bmi.uima;


import edu.utah.bmi.rush.uima.RuSH_AE;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.examples.cpe.AnnotationPrinter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.Before;
import org.junit.Test;

/**
 * @Author Jianlin Shi
 */
public class TestRuSH_AE {
    JCas jCas;
    AnalysisEngine analysisEngine, testAnalysisEngine;

    @Before
    public void init() throws UIMAException {
        String typeDescriptor = "desc/type/All_Types";
        jCas = JCasFactory.createJCas(typeDescriptor);
        analysisEngine = AnalysisEngineFactory.createEngine(
                RuSH_AE.class,
                RuSH_AE.PARAM_RULE_FILE, "conf/rush.csv",
                RuSH_AE.PARAM_FIX_GAPS, true);
    }

    private void print(JCas jCas, String type) {
        FSIterator<Annotation> annoIter = jCas.getAnnotationIndex(AnnotationOper.getTypeId(type)).iterator();
        while (annoIter.hasNext()) {

            Annotation anno = annoIter.next();
            System.out.println(anno.getType().getName()+"\t:");
            System.out.println(anno.getCoveredText());
        }

    }


    @Test
    public void test1() throws AnalysisEngineProcessException, ResourceInitializationException {
        String text = "    REASON FOR THIS EXAMINATION:\n" +
                "      eval for CHF                                                                    \n" +
                "     ______________________________________________________________________________\n" +
                "                                     FINAL REPORT\n" +
                "     INDICATION:  35-year-old female with supraventricular tachycardia.\n" +
                "     COMPARISON:  AP upright and lateral chest x-ray dated [**2961-11-21**].";
        jCas.reset();
        jCas.setDocumentText(text);
        SourceDocumentInformation sourceDocumentInformation = new SourceDocumentInformation(jCas, 0, text.length());
        sourceDocumentInformation.addToIndexes();
        analysisEngine.process(jCas);
        print(jCas, "edu.utah.bmi.type.system.Sentence");

    }

    @Test
    public void test2() throws AnalysisEngineProcessException, ResourceInitializationException {
        String text = "     REASON FOR THIS EXAMINATION:\n" +
                "      eval of chest pain                                                              \n" +
                "     ______________________________________________________________________________\n" +
                "                                     FINAL REPORT";
        jCas.reset();
        jCas.setDocumentText(text);
        SourceDocumentInformation sourceDocumentInformation = new SourceDocumentInformation(jCas, 0, text.length());
        sourceDocumentInformation.addToIndexes();
        analysisEngine.process(jCas);
        print(jCas, "edu.utah.bmi.type.system.Sentence");

    }
}
