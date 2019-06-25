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
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.JCasUtil;
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
 * This is an AE to use FastCNER.java to identify the datetime mentions.
 *
 * @author Jianlin Shi
 */
public class TemporalAnnotator_AE extends FastCNER_AE_General {
    public static Logger logger = IOUtil.getLogger(TemporalAnnotator_AE.class);

    //  specify which type of annotations as the target concept
    public static final String PARAM_SAVE_INFERRED_RECORD_DATE = "SaveInferredRecordDate";

    //  read from record table, specify which column is the reference datetime, usually is set to admission datetime.
    public static final String PARAM_REFERENCE_DATE_COLUMN_NAME = "ReferenceDateColumnName";
    public static final String PARAM_RECORD_DATE_COLUMN_NAME = "RecordDateColumnName";


    protected String referenceDateColumnName, recordDateColumnName;


    protected HashMap<String, IntervalST<Span>> dateAnnos = new HashMap();

    protected HashMap<String, Integer> numberMap = new HashMap<>();

    protected Pattern[] patterns = new Pattern[5];

    protected DateTime referenceDate;
    protected boolean globalCertainty = false;
    protected boolean saveInferredRecordDate = false;

    public void initialize(UimaContext cont) {
        super.initialize(cont);
        initPatterns();
        initNumerMap();
        Object obj;
        obj = cont.getConfigParameterValue(PARAM_SAVE_INFERRED_RECORD_DATE);
        if (obj != null && obj instanceof Boolean)
            saveInferredRecordDate = (Boolean) obj;

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


    public void process(JCas jcas) throws AnalysisEngineProcessException {
        DateTime recordDate = readReferenceDate(jcas, recordDateColumnName);
        referenceDate = readReferenceDate(jcas, referenceDateColumnName);
        if (referenceDate == null)
            referenceDate = new DateTime(System.currentTimeMillis());

        globalCertainty = recordDate != null;
        dateAnnos.clear();
        String docText = jcas.getDocumentText();
        HashMap<String, ArrayList<Span>> dates = ((FastCNER) fastNER).processString(docText);
        ArrayList<Annotation> allDateMentions = parseDateMentions(jcas, dates, recordDate);
        coordinateDateMentions(allDateMentions, jcas.getDocumentText().length());
    }


    protected DateTime readReferenceDate(JCas jcas, String referenceDateColumnName) {
        if (referenceDateColumnName == null || referenceDateColumnName.trim().length() == 0)
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


    protected DateTime parseDateString(String dateString, DateTime recordDate) {
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
     * @param jcas  JCas object
     * @param dates   List of date spans grouped by types
     * @param recordDate  document record date
     * @return a list of date mention annotations
     */
    protected ArrayList<Annotation> parseDateMentions(JCas jcas, HashMap<String, ArrayList<Span>> dates,
                                                    DateTime recordDate) {
        String text = jcas.getDocumentText();
        String latestDateMention = "";
        ArrayList<Annotation> allDateMentions = new ArrayList<>();
        if (recordDate == null) {
            if (dates.containsKey("CERTAIN_DATE"))
                for (Span span : dates.get("CERTAIN_DATE")) {
                    DateTime dt = null;
                    String dateMention = text.substring(span.begin, span.end).trim();
                    try {
                        dt = parseDateString(dateMention, recordDate);
                        if (recordDate == null || dt.isAfter(recordDate)) {
                            recordDate = dt;
                            latestDateMention = dateMention;
                        }
                    } catch (Exception e) {
//                    e.printStackTrace();
                    }
                }
        }
        if (recordDate == null) {
            if (dates.containsKey("CERTAIN_YEAR"))
                for (Span span : dates.get("CERTAIN_YEAR")) {
                    DateTime dt = null;
                    String dateMention = text.substring(span.begin, span.end).trim();
                    try {
                        dt = parseDateString(dateMention, recordDate);
                        if (recordDate == null || dt.isAfter(recordDate)) {
                            recordDate = dt;
                            latestDateMention = dateMention;
                        }
                    } catch (Exception e) {
//                    e.printStackTrace();
                    }
                }
        }
        logger.finest(latestDateMention.length() > 0 ? "Record date is not set, inferred from the mention: \"" + latestDateMention + "\" as " + recordDate : "");
        if (saveInferredRecordDate && recordDate != null) {
            SourceDocumentInformation meta = JCasUtil.select(jcas, SourceDocumentInformation.class).iterator().next();
            RecordRow metaRecord = new RecordRow();
            metaRecord.deserialize(meta.getUri());
            metaRecord.addCell(recordDateColumnName, recordDate);
            meta.setUri(metaRecord.serialize());
        }
        for (Map.Entry<String, ArrayList<Span>> entry : dates.entrySet()) {
            String typeOfDate = entry.getKey();
            switch (typeOfDate) {
                case "Date":
                case "CERTAIN_YEAR":
                case "CERTAIN_DATE":
                    ArrayList<Span> dateMentions = entry.getValue();
                    for (Span span : dateMentions) {
                        String certainty = globalCertainty || typeOfDate.startsWith("CERTAIN") ? "certain" : "uncertain";
                        if (fastNER.getMatchedNEType(span) == DeterminantValueSet.Determinants.PSEUDO)
                            continue;
                        String dateMention = text.substring(span.begin, span.end).trim();
                        DateTime dt = null;
                        try {
                            dt = parseDateString(dateMention, recordDate);
                        } catch (Exception e) {
//                    		e.printStackTrace();
                        }
                        if (dt == null) {
                            certainty = "uncertain";
                            dt = handleAmbiguousCase(dateMention, recordDate);
                        }
                        logger.finest("Parse '" + dateMention + "' as: '" + dt.toString() + "'");
                        addDateMentions(jcas, ConceptTypeConstructors, allDateMentions,
                                typeOfDate, certainty, span, dt.toString(), getRuleInfo(span));
                    }
                    break;
                case "Yeard":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeNumericTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 365);
                    break;
                case "Monthd":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeNumericTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 30);
                    break;
                case "Weekd":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeNumericTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 7);
                    break;
                case "Dayd":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeNumericTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 1);
                    break;
                case "Yearw":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeLiteralTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 365);
                    break;
                case "Monthw":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeLiteralTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 30);
                    break;
                case "Weekw":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeLiteralTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 7);
                    break;
                case "Dayw":
                    for (Span span : entry.getValue())
                        inferDateFromRelativeLiteralTime(jcas, allDateMentions, typeOfDate, text, span, recordDate, 1);
                    break;
            }
        }
        return allDateMentions;
    }

    protected void addDateMentions(JCas jcas, HashMap<String, Constructor<? extends Concept>> ConceptTypeConstructors,
                                 ArrayList<Annotation> allDateMentions, String typeName,
                                 String certainty, Span span, String dateStr, String... ruleInfo) {
        if (getSpanType(span) != DeterminantValueSet.Determinants.ACTUAL) {
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
            if (dateStr != null)
                anno.setCategory(dateStr);
            if (ruleInfo.length > 0) {
                anno.setNote(String.join("\n", ruleInfo));
            }
            allDateMentions.add(anno);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }


    protected DateTime inferDateFromRelativeNumericTime(JCas jcas, ArrayList<Annotation> allDateMentions, String typeName, String text, Span span,
                                                      DateTime recordDate, int unit) {
        DateTime dt = null;
        try {

            String certainty = globalCertainty ? "certain" : "uncertain";
            int interval = Integer.parseInt(text.substring(span.begin, span.end).trim());
            if (recordDate == null)
                recordDate = referenceDate;
            dt = recordDate.minusDays(interval * unit);
            if (unit > 7) {
                certainty = "uncertain";
            }
            addDateMentions(jcas, ConceptTypeConstructors, allDateMentions, typeName, certainty, span, dt.toString(), getRuleInfo(span));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dt;
    }

    protected DateTime inferDateFromRelativeLiteralTime(JCas jcas, ArrayList<Annotation> allDateMentions, String typeName, String text, Span span,
                                                      DateTime recordDate, int unit) {
        DateTime dt = null;
        try {
            String numWord = text.substring(span.begin, span.end).trim().toLowerCase();
            String certainty = globalCertainty ? "certain" : "uncertain";
            if (numberMap.containsKey(numWord)) {
                int interval = numberMap.get(numWord);
                if (recordDate == null)
                    recordDate = referenceDate;
                dt = recordDate.minusDays(interval * unit);
                if (interval > 7) {
                    certainty = "uncertain";
                }
                addDateMentions(jcas, ConceptTypeConstructors, allDateMentions, typeName, certainty, span, dt.toString(), getRuleInfo(span));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dt;
    }


    protected DateTime handleAmbiguousCase(String dateMentions, DateTime recordDate) {
        DateTime dt = null;
        if (recordDate == null)
            recordDate = referenceDate;
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

    protected void coordinateDateMentions(ArrayList<Annotation> allDateMentions, int length) {
        IntervalST<Integer> checker = new IntervalST<>();
        HashSet<Integer> scheduleToRemoveIds = new HashSet<>();
        for (int i = 0; i < allDateMentions.size(); i++) {
            Annotation anno = allDateMentions.get(i);
            Interval1D span = new Interval1D(anno.getBegin(), anno.getEnd());
            if (checker.contains(span)) {
                for (Integer existingId : checker.getAll(span)) {
                    Annotation existingAnno = allDateMentions.get(existingId);
                    if ((existingAnno.getEnd() - existingAnno.getBegin()) <= (anno.getEnd() - anno.getBegin())) {
                        logger.finest("DateMention: \"" + anno.getCoveredText() + "\"(as " + anno.getClass().getSimpleName()
                                + ") replaces the existing DateMention: \"" + existingAnno.getCoveredText() + "\"(as "
                                + existingAnno.getClass().getSimpleName() + ")");
                        checker.remove(span);
                        scheduleToRemoveIds.add(existingId);
                        checker.put(span, i);
                    } else {
                        logger.finest("DateMention: \"" + anno.getCoveredText() + "\"(as " + anno.getClass().getSimpleName()
                                + ") is covered by the existing DateMention: \"" + existingAnno.getCoveredText() + "\"(as "
                                + existingAnno.getClass().getSimpleName() + ")");
                        scheduleToRemoveIds.add(i);
                    }
                }
            } else {
                logger.finest("DateMention: \"" + anno.getCoveredText() + "\"(as " + anno.getClass().getSimpleName() + ") doesn't have any overlap.");
                checker.put(span, i);
            }
        }
        for (int i = 0; i < allDateMentions.size(); i++) {
            if (!scheduleToRemoveIds.contains(i)) {
                Annotation anno = allDateMentions.get(i);
                anno.addToIndexes();
                logger.finest("DateMention: \"" + anno.getCoveredText() + "\"(as " + anno.getClass().getSimpleName() + ")");
            }
        }

    }


}