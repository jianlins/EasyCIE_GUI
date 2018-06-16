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
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.joda.time.DateTime;
import org.pojava.datetime.DateTimeConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

//import org.pojava.datetime.DateTime;


/**
 * This is an AE to use FastCNER.java to identify the datetime mentions.
 *
 * @author Jianlin Shi
 */
public class TemporalAnnotator_AE extends FastCNER_AE_General {
	public static Logger logger = IOUtil.getLogger(TemporalAnnotator_AE.class);

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


	public void process(JCas jcas) {
		DateTime recordDate = readReferenceDate(jcas, recordDateColumnName);
		referenceDate = readReferenceDate(jcas, referenceDateColumnName);
		if(referenceDate==null)
			referenceDate=new DateTime(System.currentTimeMillis());
		dateAnnos.clear();
		String docText = jcas.getDocumentText();
		HashMap<String, ArrayList<Span>> dates = ((FastCNER) fastNER).processString(docText);
		parseDateMentions(jcas, dates, recordDate);
	}

	private DateTime readReferenceDate(JCas jcas, String referenceDateColumnName) {
		if(referenceDateColumnName==null || referenceDateColumnName.trim().length()==0)
			return null;
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
		return parseDateString(dateString, referenceDate);
	}


	private DateTime parseDateString(String dateString, DateTime recordDate) {
		Date utilDate = null;
		try {
			if (recordDate != null) {
				utilDate = new org.pojava.datetime.DateTime(dateString, DateTimeConfig.getDateTimeConfig(recordDate.toDate())).toDate();
			} else {
				utilDate = new org.pojava.datetime.DateTime(dateString, DateTimeConfig.getDateTimeConfig(referenceDate.toDate())).toDate();
			}

		} catch (Exception e) {
			logger.fine("Illegal date string: " + dateString);
			logger.fine(e.getMessage());
		}


		DateTime date = new DateTime(utilDate);
		return date;
	}


	/**
	 * For parse date mentions and save as annotations.
	 *
	 * @param jcas
	 * @param dates
	 * @param recordDate
	 */
	private void parseDateMentions(JCas jcas, HashMap<String, ArrayList<Span>> dates,
								   DateTime recordDate) {
		String text = jcas.getDocumentText();
		for (Map.Entry<String, ArrayList<Span>> entry : dates.entrySet()) {
			String typeOfDate = entry.getKey();
			switch (typeOfDate) {
				case "Date":
					ArrayList<Span> dateMentions = entry.getValue();
					for (Span span : dateMentions) {
						String certainty = recordDate == null ? "uncertain" : "certain";
						if (fastNER.getMatchedNEType(span) == DeterminantValueSet.Determinants.PSEUDO)
							continue;
						String dateMention = text.substring(span.begin, span.end).trim();
						DateTime dt = null;
						try {
							dt = parseDateString(dateMention, recordDate);
						} catch (Exception e) {
//                    e.printStackTrace();
						}
						if (dt == null) {
							certainty="uncertain";
							dt = handleAmbiguousCase(dateMention, recordDate);
						}
						logger.finest("Parse '" + dateMention + "' as: '" + dt.toString() + "'");
						saveDateConcept(jcas, ConceptTypeConstructors, typeOfDate, certainty, span, dt.toString(), getRuleInfo(span));
					}
					break;
				case "Yeard":
					for (Span span : entry.getValue())
						inferDateFromRelativeNumericTime(jcas, typeOfDate, text, span, recordDate, 365);
					break;
				case "Monthd":
					for (Span span : entry.getValue())
						inferDateFromRelativeNumericTime(jcas, typeOfDate, text, span, recordDate, 30);
					break;
				case "Weekd":
					for (Span span : entry.getValue())
						inferDateFromRelativeNumericTime(jcas, typeOfDate, text, span, recordDate, 7);
					break;
				case "Dayd":
					for (Span span : entry.getValue())
						inferDateFromRelativeNumericTime(jcas, typeOfDate, text, span, recordDate, 1);
					break;
				case "Yearw":
					for (Span span : entry.getValue())
						inferDateFromRelativeLiteralTime(jcas, typeOfDate, text, span, recordDate, 365);
					break;
				case "Monthw":
					for (Span span : entry.getValue())
						inferDateFromRelativeLiteralTime(jcas, typeOfDate, text, span, recordDate, 30);
					break;
				case "Weekw":
					for (Span span : entry.getValue())
						inferDateFromRelativeLiteralTime(jcas, typeOfDate, text, span, recordDate, 7);
					break;
				case "Dayw":
					for (Span span : entry.getValue())
						inferDateFromRelativeLiteralTime(jcas, typeOfDate, text, span, recordDate, 1);
					break;
			}
		}

	}


	private DateTime inferDateFromRelativeNumericTime(JCas jcas, String typeName, String text, Span span,
													  DateTime recordDate, int unit) {
		DateTime dt = null;
		try {
			int interval = Integer.parseInt(text.substring(span.begin, span.end).trim());
			dt = recordDate.minusDays(interval * unit);
			String certainty = recordDate == null ? "uncertain" : "certain";
			if (unit > 7) {
				certainty = "uncertain";
			}
			saveDateConcept(jcas, ConceptTypeConstructors, typeName, certainty, span, dt.toString(), getRuleInfo(span));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dt;
	}

	private DateTime inferDateFromRelativeLiteralTime(JCas jcas, String typeName, String text, Span span,
													  DateTime recordDate, int unit) {
		DateTime dt = null;
		try {
			String numWord = text.substring(span.begin, span.end).trim().toLowerCase();
			String certainty = recordDate == null ? "uncertain" : "certain";
			if (numberMap.containsKey(numWord)) {
				int interval = numberMap.get(numWord);
				dt = recordDate.minusDays(interval * unit);
				if (interval > 7) {
					certainty = "uncertain";
				}
				saveDateConcept(jcas, ConceptTypeConstructors, typeName, certainty, span, dt.toString(), getRuleInfo(span));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dt;
	}


	private DateTime handleAmbiguousCase(String dateMentions, DateTime recordDate) {
		DateTime dt = null;
		if (patterns[0].matcher(dateMentions).find()) {
			dt = parseDateString(dateMentions + " " + referenceDate.getYear(), recordDate);
			if (dt == null)
				return referenceDate;
			if (dt.getMonthOfYear() > referenceDate.getMonthOfYear())
				dt = dt.minusYears(1);
		} else if (patterns[1].matcher(dateMentions).find()) {
			dateMentions = "01/01/" + dateMentions;
			dt = parseDateString(dateMentions, recordDate);
			if (dt == null)
				dt = referenceDate;
		} else if (patterns[2].matcher(dateMentions).find()) {
			dateMentions = "01/" + dateMentions;
			dt = parseDateString(dateMentions, recordDate);
			if (dt == null)
				dt = referenceDate;
		} else if (patterns[3].matcher(dateMentions).find()) {
			dt = parseDateString(dateMentions + " " + referenceDate.getYear(), recordDate);
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
								   String typeName, String certainty, Span span, String comments, String... rule) {
		if (!saveDateAnnotations || getSpanType(span) != DeterminantValueSet.Determinants.ACTUAL) {
			return;
		}
		Concept anno = null;
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
			anno.setCertainty(certainty);
			if (comments != null)
				anno.setCategory(comments);
			if (rule.length > 0) {
				anno.setNote(String.join("\n", rule));
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