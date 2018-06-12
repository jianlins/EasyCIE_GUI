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
package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.*;
import edu.utah.bmi.nlp.rush.core.RuSH;
import edu.utah.bmi.nlp.rush.core.SmartChineseCharacterSplitter;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.logging.Logger;


/**
 * This is RuSH Wrapper into UIMA analyses engine.
 *
 * @author Jianlin Shi
 */
public class OpenNLPSentDetector extends JCasAnnotator_ImplBase {

	private static Logger logger = IOUtil.getLogger(OpenNLPSentDetector.class);


	//	a list of section names that limit the scope of sentence detection
	// (can use short name if name space is "edu.utah.bmi.nlp.type.system."),
	// separated by "|", ",", or ";".
	public static final String PARAM_INSIDE_SECTIONS = "InsideSections";

	public static final String PARAM_MODEL_LOC = "ModelLocation";
	public static final String PARAM_SENTENCE_TYPE_NAME = "SentenceTypeName";

	//  if this parameter is set, then the adjacent sentences will be annotated in different color-- easy to review
	public static final String PARAM_ALTER_SENTENCE_TYPE_NAME = "AlterSentenceTypeName";

	public static final String PARAM_TOKEN_TYPE_NAME = "TokenTypeName";

	public static final String PARAM_INCLUDE_PUNCTUATION = "IncludePunctuation";

	public static final String PARAM_LANGUAGE = "Language";


	protected Class<? extends Annotation> SentenceType, AlterSentenceType, TokenType;
	protected boolean includePunctuation = false, differentColoring = false, colorIndicator = false;
	protected static Constructor<? extends Annotation> SentenceTypeConstructor, AlterSentenceTypeConstructor, TokenTypeConstructor;

	private SentenceDetectorME sdetector = null;
	private String mLanguage;

	private LinkedHashSet<Class> sectionClasses = new LinkedHashSet<>();

	public void initialize(UimaContext cont) {
		String modelLoc = "src/main/resources/en-sent.bin";
		Object obj = cont
				.getConfigParameterValue(PARAM_MODEL_LOC);
		if (obj != null)
			modelLoc = (String) obj;
		InputStream is = null;
		try {
			is = new FileInputStream(modelLoc);
			SentenceModel model = new SentenceModel(is);
			is.close();
			sdetector = new SentenceDetectorME(model);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}


		String sentenceTypeName, alterSentenceTypeName = null, tokenTypeName;
		obj = cont.getConfigParameterValue(PARAM_SENTENCE_TYPE_NAME);
		if (obj != null && obj instanceof String) {
			sentenceTypeName = ((String) obj).trim();
			sentenceTypeName = checkTypeDomain(sentenceTypeName);
		} else {
			sentenceTypeName = DeterminantValueSet.defaultNameSpace + "Sentence";
		}
		obj = cont.getConfigParameterValue(PARAM_TOKEN_TYPE_NAME);
		if (obj != null && obj instanceof String) {
			tokenTypeName = ((String) obj).trim();
			tokenTypeName = checkTypeDomain(tokenTypeName);
		} else {
			tokenTypeName = DeterminantValueSet.defaultNameSpace + "Token";
		}


		obj = cont.getConfigParameterValue(PARAM_ALTER_SENTENCE_TYPE_NAME);
		if (obj != null && obj instanceof String) {
			alterSentenceTypeName = ((String) obj).trim();
			alterSentenceTypeName = checkTypeDomain(alterSentenceTypeName);
			if (alterSentenceTypeName.length() > 0)
				differentColoring = true;
		}
		obj = cont.getConfigParameterValue(PARAM_INCLUDE_PUNCTUATION);
		if (obj != null && obj instanceof Boolean && (Boolean) obj != false)
			includePunctuation = true;

		obj = cont.getConfigParameterValue(PARAM_LANGUAGE);
		if (obj != null && obj instanceof String) {
			mLanguage = ((String) obj).trim().toLowerCase();
		} else {
			mLanguage = "en";
		}

		obj = cont.getConfigParameterValue(PARAM_INSIDE_SECTIONS);
		if (obj == null || obj.toString().trim().length() == 0)
			sectionClasses.add(SourceDocumentInformation.class);
		else {
			for (String sectionName : ((String) obj).split("[\\|,;]")) {
				sectionName = sectionName.trim();
				if (sectionName.length() > 0) {
					sectionClasses.add(AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(sectionName)));
				}
			}
		}

		try {
			SentenceType = Class.forName(sentenceTypeName).asSubclass(Annotation.class);
			TokenType = Class.forName(tokenTypeName).asSubclass(Annotation.class);
			SentenceTypeConstructor = SentenceType.getConstructor(JCas.class, int.class, int.class);
			TokenTypeConstructor = TokenType.getConstructor(JCas.class, int.class, int.class);
			if (differentColoring) {
				AlterSentenceType = Class.forName(alterSentenceTypeName).asSubclass(Annotation.class);
				AlterSentenceTypeConstructor = AlterSentenceType.getConstructor(JCas.class, int.class, int.class);
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}

	}

	public void process(JCas jCas) {
		for (Class sectionClass : sectionClasses) {
			FSIndex annoIndex = jCas.getAnnotationIndex(sectionClass);
			Iterator annoIter = annoIndex.iterator();
			while (annoIter.hasNext()) {
				Annotation section = (Annotation) annoIter.next();
				processOneSection(jCas, section);
			}
		}
	}

	private void processOneSection(JCas jCas, Annotation section) {
		String text = section.getCoveredText();
		int sectionBegin = section.getBegin();
		opennlp.tools.util.Span[] sentences = sdetector.sentPosDetect(text);

		for (opennlp.tools.util.Span sentence : sentences) {
			ArrayList<Span> tokens;

			switch (mLanguage) {
				case "en":
					tokens = SimpleParser.tokenizeDecimalSmart(text.substring(sentence.getStart(), sentence.getEnd()), includePunctuation);
					saveTokens(jCas, sentence.getStart(), tokens, sectionBegin);
					break;
				case "cn":
					tokens = SmartChineseCharacterSplitter.tokenizeDecimalSmart(text.substring(sentence.getStart(), sentence.getEnd()), includePunctuation);
					saveTokens(jCas, sentence.getStart(), tokens, sectionBegin);
					break;
			}

			saveSentence(jCas, sentence.getStart() + sectionBegin, sentence.getEnd() + sectionBegin);
		}

	}


	protected void saveTokens(JCas jcas, int sentBegin, ArrayList<Span> tokens, int sectionBegin) {
		for (int i = 0; i < tokens.size(); i++) {
			Span thisSpan = tokens.get(i);
			saveToken(jcas, thisSpan.begin + sentBegin, thisSpan.end + sentBegin, sectionBegin);
		}
	}


	protected void saveToken(JCas jcas, int begin, int end, int offset) {
		saveAnnotation(jcas, TokenTypeConstructor, begin + offset, end + offset);
	}

	protected void saveSentence(JCas jcas, int begin, int end) {
		if (differentColoring) {
			colorIndicator = !colorIndicator;
			if (!colorIndicator) {
				saveAnnotation(jcas, AlterSentenceTypeConstructor, begin, end);
				return;
			}
		}
		saveAnnotation(jcas, SentenceTypeConstructor, begin, end);

	}

	protected void saveAnnotation(JCas jcas, Constructor<? extends Annotation> annoConstructor, int begin, int end) {
		Annotation anno = null;
		try {
			anno = annoConstructor.newInstance(jcas, begin, end);
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		anno.addToIndexes();
	}

	public static String checkTypeDomain(String typeName) {
		if (typeName.indexOf(".") == -1) {
			typeName = "edu.utah.bmi.nlp.type.system." + typeName;
		}
		return typeName;
	}
}