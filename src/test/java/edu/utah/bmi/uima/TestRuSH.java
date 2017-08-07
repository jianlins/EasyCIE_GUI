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


import edu.utah.bmi.nlp.Span;
import edu.utah.bmi.rush.ss.RuSH;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * This rule set are more inclusive that is trying to include all non-whitespace characters in a sentence.
 * @Author Jianlin Shi
 */
public class TestRuSH {
    private RuSH segmenter;
    private boolean debug = true;

    public static void printDetails(ArrayList<Span> sentences, String input, boolean debug) {
        if (debug) {
            for (Span sentence : sentences) {
                System.out.println(sentence.begin + "-" + sentence.end + "\t" + ">" + input.substring(sentence.begin, sentence.end) + "<");
            }
            for (int i = 0; i < sentences.size(); i++) {
                Span sentence = sentences.get(i);
                System.out.println("assert (sentences.get(" + i + ").begin == " + sentence.begin + " &&" +
                        " sentences.get(" + i + ").end == " + sentence.end + ");");
            }
        }
    }

    @Before
    public void initiate() {
        segmenter = new RuSH("conf/rush.csv");
        segmenter.setDebug(debug);
        segmenter.setSpecialCharacterSupport(true);
    }


    @Test
    public void test1() throws Exception {
        String input = "     REASON FOR THIS EXAMINATION:\n" +
                "      please assess for effusions/pneumonia                                           \n" +
                "     ______________________________________________________________________________\n" +
                "                                     FINAL REPORT\n" +
                "     HISTORY: Patient with hypotension, cardiac arrest, RIJ line insertion.";
        ArrayList<Span> sentences = segmenter.segToSentenceSpans(input);
        input = input.replaceAll("\\n", " ");
        printDetails(sentences, input, debug);

    }

    @Test
    public void test2() {
        String input = "     REASON FOR THIS EXAMINATION:\n" +
                "      eval for CHF                                                                    \n" +
                "     ______________________________________________________________________________\n" +
                "                                     FINAL REPORT\n" +
                "     INDICATION:  35-year-old female with supraventricular tachycardia.\n" +
                "     COMPARISON:  AP upright and lateral chest x-ray dated [**2961-11-21**].\n" +
                "     ";
        ArrayList<Span> sentences = segmenter.segToSentenceSpans(input);
        input = input.replaceAll("\n", " ");
        printDetails(sentences, input, debug);

    }

}