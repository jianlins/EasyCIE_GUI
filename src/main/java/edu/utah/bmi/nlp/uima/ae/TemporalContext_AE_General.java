package edu.utah.bmi.nlp.uima.ae;

/*******************************************************************************
 * Copyright  Apr 11, 2015  Department of Biomedical Informatics, University of Utah
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/


import edu.utah.bmi.nlp.core.*;
import edu.utah.bmi.nlp.fastcner.FastCNER;
import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastner.FastNER;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.joda.time.DateTime;
import org.pojava.datetime.DateTimeConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

//import org.pojava.datetime.DateTime;


/**
 * This is an AE to use FastCNER.java to identify the datetime mentions and compare with the reference date, to infer whether the context is historical or current.
 *
 * @author Jianlin Shi
 */
public class TemporalContext_AE_General extends FastCNER_AE_General {
	public static Logger logger = IOUtil.getLogger(TemporalContext_AE_General.class);

	//  specify which type of annotations as the target concept
	public static final String PARAM_TARGET_CONCEPT_TYPE_NAME = "TargetConceptTypeName";

	public static final String PARAM_INTERVAL_DAYS = "IntervalDaysBeforeReferenceDate";

	public static final String PARAM_INFER_ALL = "InferTemporalStatusForAllTargetConcept";
	//    @ConfigurationParameter(name = CONCEPT_TYPE_NAME)
	private String targetConceptTypeName;
	private int targetConceptId;
	//  read from record table, specify which column is the reference datetime, usually is set to admission datetime.
	public static final String PARAM_REFERENCE_DATE_COLUMN_NAME = "ReferenceDateColumnName";
	public static final String PARAM_RECORD_DATE_COLUMN_NAME = "RecordDateColumnName";
	public static final String PARAM_SAVE_DATE_ANNO = "SaveDateAnnotations";


	private String referenceDateColumnName, recordDateColumnName;




	// number of days before admission that still will be considered as current

	private int intervalDaysBeforeReferenceDate;
	protected boolean inferAll = false, saveDateAnnotations = false;


	private HashMap<String, IntervalST<Span>> dateAnnos = new HashMap();

	protected HashMap<String, Integer> numberMap = new HashMap<>();

	private Pattern[] patterns = new Pattern[5];

	private DateTime referenceDate;

	public void initialize(UimaContext cont) {
		super.initialize(cont);
		initPatterns();
		initNumerMap();
		Object obj;
		obj = cont.getConfigParameterValue(PARAM_TARGET_CONCEPT_TYPE_NAME);
		if (obj == null)
			targetConceptTypeName = DeterminantValueSet.defaultNameSpace + "Concept";
		else
			targetConceptTypeName = (String) obj;

		obj = cont.getConfigParameterValue(PARAM_REFERENCE_DATE_COLUMN_NAME);
		if (obj == null)
			referenceDateColumnName = "REF_DTM";
		else
			referenceDateColumnName = (String) obj;

		obj = cont.getConfigParameterValue(PARAM_RECORD_DATE_COLUMN_NAME);
		if (obj == null)
			recordDateColumnName = "DATE";
		else
			recordDateColumnName = (String) obj;



		obj = cont.getConfigParameterValue(PARAM_INTERVAL_DAYS);
		if (obj == null)
			intervalDaysBeforeReferenceDate = 14;
		else
			intervalDaysBeforeReferenceDate = (int) obj;
		targetConceptId = AnnotationOper.getTypeId(targetConceptTypeName);

		obj = cont.getConfigParameterValue(PARAM_INFER_ALL);
		if (obj != null && obj instanceof Boolean && (Boolean) obj == true)
			inferAll = true;
		obj = cont.getConfigParameterValue(PARAM_SAVE_DATE_ANNO);
		if (obj != null && obj instanceof Boolean && (Boolean) obj == true)
			saveDateAnnotations = true;
	}

	public void initPatterns() {
		patterns[0] = Pattern.compile("^\\d{1,2}/\\d{1,2}$");
		patterns[1] = Pattern.compile("^\\d{4}$");
		patterns[2] = Pattern.compile("^\\d{1,2}/\\d{4}$");
		patterns[3] = Pattern.compile("^[JFMASODN][a-z]+");
	}

	public void initNumerMap() {
		String wordstr = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty";
		int i = 1;
		for (String word : wordstr.split("\\|")) {
			numberMap.put(word, i);
			i++;
		}
	}


	public void process(JCas jcas)throws AnalysisEngineProcessException {
		ArrayList<Annotation> sentences = new ArrayList<>();
		ArrayList<ConceptBASE> targetConcepts = new ArrayList<>();
		referenceDate = readReferenceDate(jcas, referenceDateColumnName);
		DateTime recordDate = readReferenceDate(jcas, recordDateColumnName);
		dateAnnos.clear();
		if (recordDate == null)
			recordDate = referenceDate;
		if (referenceDate == null) {
			logger.fine("No value in Reference date column: '" + referenceDateColumnName + "'. Skip the TemporalConTextDetector.");
			return;
		}

		FSIndex annoIndex = jcas.getAnnotationIndex(targetConceptId);
		Iterator annoIter = annoIndex.iterator();
		while (annoIter.hasNext()) {
			ConceptBASE concept = (ConceptBASE) annoIter.next();
//          process the concept only if it has not been identified as "historical" in context
			if (concept.getTemporality() == null || concept.getTemporality().equals("present"))
				targetConcepts.add(concept);
		}
		if (targetConcepts.size() > 0) {
			annoIndex = jcas.getAnnotationIndex(SentenceType);
			annoIter = annoIndex.iterator();
			IntervalST sentenceTree = new IntervalST();
			while (annoIter.hasNext()) {
				Annotation sentence = (Annotation) annoIter.next();
				sentenceTree.put(new Interval1D(sentence.getBegin(), sentence.getEnd()), sentence);
			}
			for (ConceptBASE concept : targetConcepts) {
				processCase(jcas, concept, (Annotation) sentenceTree.get(new Interval1D(concept.getBegin(),
						concept.getEnd())), recordDate, referenceDate.minusDays(intervalDaysBeforeReferenceDate));
			}
		}
	}

	private DateTime readReferenceDate(JCas jcas, String referenceDateColumnName) {
		FSIterator it = jcas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
		RecordRow recordRow = new RecordRow();
		if (it.hasNext()) {
			SourceDocumentInformation e = (SourceDocumentInformation) it.next();
			String serializedString = e.getUri();
			recordRow.deserialize(serializedString);
		}
		String dateString = (String) recordRow.getValueByColumnName(referenceDateColumnName);
		if (dateString == null)
			return null;
		return parseDateString(dateString, referenceDate, referenceDate);
	}


	private DateTime parseDateString(String dateString, DateTime recordDate, DateTime referenceDate) {
		Date utilDate = null;
		try {
			if (recordDate != null) {
				utilDate = new org.pojava.datetime.DateTime(dateString, DateTimeConfig.getDateTimeConfig(recordDate.toDate())).toDate();
			} else {
				utilDate = new org.pojava.datetime.DateTime(dateString, DateTimeConfig.getDateTimeConfig(this.referenceDate == null ? null : this.referenceDate.toDate())).toDate();
			}

		} catch (Exception e) {
			logger.fine("Illegal date string: " + dateString);
			logger.fine(e.getMessage());
		}
//        try {
//            if (utilDate == null) {
//                utilDate = new org.pojava.datetime.DateTime(dateString).toDate();
//                System.out.println(referenceDate.getYear());
//                utilDate.setYear(referenceDate.getYear());
//            }
//        } catch (Exception e) {
//            logger.fine("Illegal date string: " + dateString);
//        }


		DateTime date = new DateTime(utilDate);
		return date;
	}


	private void processCase(JCas jcas, ConceptBASE concept, Annotation sentence,
							 DateTime recordDate, DateTime referenceDate) {
		HashMap<String, ArrayList<Span>> dates = ((FastCNER) fastNER).processAnnotation(sentence);

		String temporalStatus = inferTemporalStatus(jcas, dates, recordDate, referenceDate);
		if (temporalStatus.length() > 0)
			concept.setTemporality(temporalStatus);

	}

	/**
	 * For now only infer historical, current or uncertain.
	 * Hypothetical will be handled later if necessary.
	 *
	 * @param jcas
	 * @param dates
	 * @param recordDate
	 * @param referenceDate @return
	 */
	private String inferTemporalStatus(JCas jcas, HashMap<String, ArrayList<Span>> dates,
									   DateTime recordDate, DateTime referenceDate) {
		String temporalStatus = "";
		String text = jcas.getDocumentText();
		if (inferAll && (dates.size() == 0) || !hasDateMentions(dates)) {
			return updateTemporalStatus(recordDate, referenceDate, temporalStatus);
		}
		for (Map.Entry<String, ArrayList<Span>> entry : dates.entrySet()) {
			String typeOfDate = entry.getKey();
			switch (typeOfDate) {
				case "Date":
					ArrayList<Span> dateMentions = entry.getValue();
					for (Span span : dateMentions) {
						if (fastNER.getMatchedNEType(span) == DeterminantValueSet.Determinants.PSEUDO)
							continue;
						String dateMention = text.substring(span.begin, span.end).trim();
						DateTime dt = null;
						try {
							dt = parseDateString(dateMention, recordDate, referenceDate);
						} catch (Exception e) {
//                    e.printStackTrace();
						}
						if (dt == null) {
							dt = handleAmbiguousCase(dateMention, recordDate, referenceDate);
						}
						logger.finest("Parse '" + dateMention + "' as: '" + dt.toString() + "'");
						temporalStatus = updateTemporalStatus(dt, referenceDate, temporalStatus);
						saveDateConcept(jcas, ConceptTypeConstructors, typeOfDate, span, temporalStatus, "ParsedDate:\t" + dt.toString(), getRuleInfo(span));
					}
					break;
				case "Yeard":
					temporalStatus = inferTemporalStatusFromRelativeNumericTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 365);
					break;
				case "Monthd":
					temporalStatus = inferTemporalStatusFromRelativeNumericTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 30);
					break;
				case "Weekd":
					temporalStatus = inferTemporalStatusFromRelativeNumericTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 7);
					break;
				case "Dayd":
					temporalStatus = inferTemporalStatusFromRelativeNumericTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 1);
					break;
				case "Yearw":
					temporalStatus = inferTemporalStatusFromRelativeLiteralTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 365);
					break;
				case "Monthw":
					temporalStatus = inferTemporalStatusFromRelativeLiteralTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 30);
					break;
				case "Weekw":
					temporalStatus = inferTemporalStatusFromRelativeLiteralTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 7);
					break;
				case "Dayw":
					temporalStatus = inferTemporalStatusFromRelativeLiteralTime(jcas, typeOfDate, text, entry.getValue(), referenceDate, recordDate, temporalStatus, 1);
					break;
			}
		}
		return temporalStatus;
	}

	public boolean hasDateMentions(HashMap<String, ArrayList<Span>> dates) {
		for (ArrayList<Span> mentions : dates.values()) {
			if (mentions.size() > 0)
				return true;
		}
		return false;
	}

	private String inferTemporalStatusFromRelativeNumericTime(JCas jcas, String typeName, String text, ArrayList<Span> spans,
															  DateTime referenceDate, DateTime recordDate, String temporalStatus, int unit) {
		for (Span span : spans) {
			try {
				int interval = Integer.parseInt(text.substring(span.begin, span.end).trim());
				DateTime dt = recordDate.minusDays(interval * unit);
				temporalStatus = updateTemporalStatus(dt, referenceDate, temporalStatus);
				saveDateConcept(jcas, ConceptTypeConstructors, typeName, span, temporalStatus, "ParsedDate:\t" + dt.toString(), getRuleInfo(span));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return temporalStatus;
	}

	private String inferTemporalStatusFromRelativeLiteralTime(JCas jcas, String typeName, String text, ArrayList<Span> spans,
															  DateTime referenceDate, DateTime recordDate, String temporalStatus, int unit) {
		for (Span span : spans) {
			try {
				String numWord = text.substring(span.begin, span.end).trim().toLowerCase();
				if (numberMap.containsKey(numWord)) {
					int interval = numberMap.get(numWord);
					DateTime dt = recordDate.minusDays(interval * unit);
					temporalStatus = updateTemporalStatus(dt, referenceDate, temporalStatus);
					saveDateConcept(jcas, ConceptTypeConstructors, typeName, span, temporalStatus, "ParsedDate:\t" + dt.toString(), getRuleInfo(span));


				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return temporalStatus;
	}

	private String updateTemporalStatus(DateTime dt, DateTime referenceDate, String temporalStatus) {
		switch (temporalStatus) {
			case "":
				temporalStatus = dt.isBefore(referenceDate) ? "historical" : "present";
				break;
			case "present":
				if (dt.isBefore(referenceDate)) {
					temporalStatus = "historical";
				}
				break;
//						TODO need improve
			case "historical":
				if (!dt.isBefore(referenceDate))
					temporalStatus = "historical";
//							temporalStatus = "tmp_uncertain";
				break;
		}
		return temporalStatus;
	}

	private DateTime handleAmbiguousCase(String dateMentions, DateTime recordDate, DateTime referenceDate) {
		DateTime dt = null;
		if (patterns[0].matcher(dateMentions).find()) {
			dt = parseDateString(dateMentions + " " + referenceDate.getYear(), recordDate, referenceDate);
			if (dt == null)
				return referenceDate;
			if (dt.getMonthOfYear() > referenceDate.getMonthOfYear())
				dt = dt.minusYears(1);
		} else if (patterns[1].matcher(dateMentions).find()) {
			dateMentions = "01/01/" + dateMentions;
			dt = parseDateString(dateMentions, recordDate, referenceDate);
			if (dt == null)
				dt = referenceDate;
		} else if (patterns[2].matcher(dateMentions).find()) {
			dateMentions = "01/" + dateMentions;
			dt = parseDateString(dateMentions, recordDate, referenceDate);
			if (dt == null)
				dt = referenceDate;
		} else if (patterns[3].matcher(dateMentions).find()) {
			dt = parseDateString(dateMentions + " " + referenceDate.getYear(), recordDate, referenceDate);
			if (dt.getMonthOfYear() > referenceDate.getMonthOfYear())
				dt = dt.minusYears(1);
		} else {
			logger.fine("Uncertain date: " + dateMentions + "\t");
			dt = referenceDate;
		}
		logger.fine("Interpret ambigous date mention: " + dateMentions + " as " + dt.toString());
		return dt;
	}


	protected String getRuleInfo(Span span) {
		return span.ruleId + ":\t" + fastNER.getMatchedRuleString(span).rule;
	}

	protected String getMatchedNEName(Span span) {
		return fastNER.getMatchedNEName(span);
	}

	protected DeterminantValueSet.Determinants getSpanType(Span span) {
		return fastNER.getMatchedNEType(span);
	}

	public static LinkedHashMap<String, TypeDefinition> getTypeDefinitions(String ruleFile, boolean caseSenstive) {
		return new FastNER(ruleFile, caseSenstive, false).getTypeDefinitions();
	}


	protected void saveDateConcept(JCas jcas, HashMap<String, Constructor<? extends Concept>> ConceptTypeConstructors,
								   String typeName, Span span, String comments, String... rule) {
		if (!saveDateAnnotations || getSpanType(span) != DeterminantValueSet.Determinants.ACTUAL) {
			return;
		}
		Annotation anno = null;
		if (!dateAnnos.containsKey(typeName)) {
			dateAnnos.put(typeName, new IntervalST<>());
		}
		IntervalST<Span> intervalST = dateAnnos.get(typeName);
		Interval1D interval1D = new Interval1D(span.begin, span.end);
		if (intervalST.contains(interval1D)) {
			return;
		} else {
			intervalST.put(interval1D, span);
		}
		try {
			anno = ConceptTypeConstructors.get(typeName).newInstance(jcas, span.begin, span.end);
			if (anno instanceof ConceptBASE) {
				if (comments != null)
					((ConceptBASE) anno).setCategory(comments);
				if (rule.length > 0) {
					((ConceptBASE) anno).setNote(String.join("\n", rule));
				}
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		anno.addToIndexes();
	}


}