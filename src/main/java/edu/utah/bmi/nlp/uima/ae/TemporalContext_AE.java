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
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import edu.utah.bmi.nlp.type.system.Date;
import edu.utah.bmi.nlp.type.system.Sentence;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.joda.time.DateTime;
import org.pojava.datetime.DateTimeConfig;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;


/**
 * This is an AE to assign temporal context values to concepts based on the TemporalAnnotator identified date mentions.
 * If InferAll is true, the default temporal value will be set based on the difference between record date and reference date.
 *
 * @author Jianlin Shi
 */
public class TemporalContext_AE extends JCasAnnotator_ImplBase implements RuleBasedAEInf {

    @Deprecated
    public static final Object PARAM_MARK_PSEUDO = "MarkPseudo";
    @Deprecated
    public static final String PARAM_INTERVAL_DAYS = "IntervalDaysBeforeReferenceDate";

    @Deprecated
    public static final String PARAM_SAVE_DATE_ANNO = "SaveDateAnnotations";

    @Deprecated
    public static final Object PARAM_LOG_RULE_INFO = "LogRuleInfo";

    public static Logger logger = IOUtil.getLogger(TemporalContext_AE.class);


    public static final String PARAM_RULE_STR = DeterminantValueSet.PARAM_RULE_STR;

    public static final String PARAM_INFER_ALL = "InferTemporalStatusForAllTargetConcept";

    public static final String PARAM_SAVE_DATE_DIFF = "SaveDateDifference";

    public static final String DIFFMETHOD = "AfterRefInHours";

    protected LinkedHashMap<String, Method> setDiffMethods = new LinkedHashMap<>();
    protected LinkedHashMap<String, Class<? extends Annotation>> targetConceptClasses = new LinkedHashMap<>();

    //  read from record table, specify which column is the reference datetime, usually is set to admission datetime.
    public static final String PARAM_REFERENCE_DATE_COLUMN_NAME = "ReferenceDateColumnName";
    public static final String PARAM_RECORD_DATE_COLUMN_NAME = "RecordDateColumnName";

    public static final String PARAM_DATE_PRIORITY = "DatePriority";
    public static final String EARLYFIRST = "EARLYFIRST", LATEFIRST = "LATEFIRST", CLOSEFIRST = "CLOSEFIRST";

    protected String referenceDateColumnName, recordDateColumnName;


    // number of days before admission that still will be considered as current

    protected boolean inferAll = false, saveDateDifference = false;
    protected String datePriority = CLOSEFIRST;


    protected DateTime referenceDate;


    protected LinkedHashMap<Double, String> categories = new LinkedHashMap<>();

    @Deprecated
    public static HashMap<String, TypeDefinition> getTypeDefinitions(String dateRule, boolean b) {
        return new HashMap<String, TypeDefinition>();
    }


    public void initialize(UimaContext cont) {
        Object obj;
        String ruleStr = (String) (cont
                .getConfigParameterValue(PARAM_RULE_STR));


        LinkedHashMap<String, TypeDefinition> typeDefs = getTypeDefs(ruleStr);
        for (String typeName : typeDefs.keySet()) {
            Class<? extends Annotation> conceptClass = AnnotationOper.getTypeClass(typeDefs.get(typeName).getFullTypeName());
            if (Date.class.isAssignableFrom(conceptClass))
                continue;
            targetConceptClasses.put(typeName, conceptClass);
            Method setDiffMethod = AnnotationOper.getDefaultSetMethod(conceptClass, DIFFMETHOD);
            if (setDiffMethod != null)
                setDiffMethods.put(typeName, setDiffMethod);
        }
        if (targetConceptClasses.size() == 0) {
            targetConceptClasses.put("Concept", Concept.class);
        } else {
            targetConceptClasses = purgeTargetConceptClasses(targetConceptClasses);
        }


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


        obj = cont.getConfigParameterValue(PARAM_INFER_ALL);
        if (obj instanceof Boolean && (Boolean) obj)
            inferAll = true;

        obj = cont.getConfigParameterValue(PARAM_SAVE_DATE_DIFF);
        if (obj instanceof Boolean && (Boolean) obj)
            saveDateDifference = true;

        obj = cont.getConfigParameterValue(PARAM_DATE_PRIORITY);
        if (obj instanceof String)
            datePriority = (String) obj;

        for (ArrayList<String> row : new IOUtil(ruleStr).getInitiations()) {
            if (row.get(1).endsWith(DeterminantValueSet.TEMPORAL_CATEGORIES1.substring(1))) {
                String value = row.get(2);
                double upperBound = Double.parseDouble(row.get(3));
                categories.put(upperBound, value);
            }
        }
    }


    /**
     * Remove child classes if their parent classes are included
     *
     * @param targetConceptClasses input type class name 2 Class map.
     * @return cleaned targetConceptClasses
     */
    protected LinkedHashMap<String, Class<? extends Annotation>> purgeTargetConceptClasses(
            LinkedHashMap<String, Class<? extends Annotation>> targetConceptClasses) {
        ArrayList<String> classToRemove = new ArrayList<>();
        LinkedHashMap<String, Class<? extends Annotation>> newTargetConceptClasses = new LinkedHashMap<>();
        for (String typeName : targetConceptClasses.keySet()) {
            Class<? extends Annotation> cls = targetConceptClasses.get(typeName);
            int value = checkOverlap(cls, newTargetConceptClasses);
            switch (value) {
                case 0:
                    break;
                case 2:
                    classToRemove.add(typeName);
                case 1:
                    newTargetConceptClasses.put(typeName, cls);
                    break;
            }
        }
        for (String removeClassName : classToRemove) {
            newTargetConceptClasses.remove(removeClassName);
        }
        return newTargetConceptClasses;
    }

    protected int checkOverlap(Class<? extends Annotation> newClass,
                               LinkedHashMap<String, Class<? extends Annotation>> newTargetConceptClasses) {
        for (String addedTypeName : newTargetConceptClasses.keySet()) {
            Class<? extends Annotation> addedCls = newTargetConceptClasses.get(addedTypeName);
            int value = classRelationShip(addedCls, newClass);
            switch (value) {
                case 0:
                    return 0;
                case 2:
                    return 2;
            }
        }
        return 1;
    }

    protected int classRelationShip(Class<? extends Annotation> existingCls, Class<? extends Annotation> newClass) {
        if (existingCls.isAssignableFrom(newClass)) {
            return 0;
        }
        if (newClass.isAssignableFrom(existingCls)) {
            return 2;
        }
        return 1;

    }


    public void process(JCas jCas) throws AnalysisEngineProcessException {
        HashMap<Integer, Long> sentenceTempDiffCache = new HashMap<>();
        HashMap<Integer, String> sentenceStatusCache = new HashMap<>();
        RecordRow metaRecordRow = AnnotationOper.deserializeDocSrcInfor(jCas);
        referenceDate = readReferenceDate(metaRecordRow, referenceDateColumnName);
        DateTime recordDate = readReferenceDate(metaRecordRow, recordDateColumnName);

        if (recordDate == null) {
            logger.info("Document" + metaRecordRow.getValueByColumnId(0) + " doesn't have record date meta in JCas.");
            recordDate = referenceDate;
        }

        long docElapse = getDiffHours(recordDate, referenceDate);
        String docTempStatus = "";
        if (categories.size() > 0) {
            for (double upperBound : categories.keySet()) {
                if (docElapse < upperBound) {
                    docTempStatus = categories.get(upperBound);
                    break;
                }
            }
        }

        IntervalST<Integer> sentenceTree = new IntervalST<>();
        ArrayList<Annotation> sentences = new ArrayList<>();
        indexSegments(jCas, Arrays.asList(Sentence.class), sentenceTree, sentences, false, null);

        IntervalST<Integer> dateTree = new IntervalST<>();
        ArrayList<Annotation> dates = new ArrayList<>();
        indexSegments(jCas, Arrays.asList(Date.class), dateTree, dates, true, null);

        if (referenceDate == null) {
            logger.fine("No value in Reference date column: '" + referenceDateColumnName + "'. Skip the TemporalConTextDetector.");
            return;
        }
        for (Class<? extends Annotation> cls : targetConceptClasses.values()) {
            for (Annotation anno : JCasUtil.select(jCas, cls)) {
                if (anno instanceof Concept) {
                    if (anno instanceof Date) {
                        logger.finest(anno.getType().getShortName() + " is a Date, skip checking its temporal context.");
                        continue;
                    }
                    Concept concept = (Concept) anno;
                    if (concept.getTemporality() == null || concept.getTemporality().equals("present") || concept.getTemporality().length() == 0) {
                        int sentenceId = sentenceTree.get(new Interval1D(concept.getBegin(),
                                concept.getEnd()));
                        processCase(concept, sentenceId, sentences.get(sentenceId), sentenceTempDiffCache,
                                sentenceStatusCache, dateTree, dates, docElapse, docTempStatus);

                    }
                } else {
                    logger.warning(cls.getCanonicalName() + " is not an instance of ConceptBASE. So it cannot be processed through TemporalContext_AE_General2");
                }
            }
        }

    }

    protected void processCase(Concept concept, int sentenceId, Annotation sentence,
                               HashMap<Integer, Long> sentenceTempDiffCache,
                               HashMap<Integer, String> sentenceStatusCache,
                               IntervalST<Integer> dateTree,
                               ArrayList<Annotation> dates, long docElapse, String docTempStatus) {
        if (!sentenceStatusCache.containsKey(sentenceId)) {
            long elapse = datePriority.equals(EARLYFIRST) ? 1000000 : -1000000;
            String tempStatus = "";
            int closestDistance = 1000000;
            int counter = 0;
            for (int dateId : dateTree.getAll(new Interval1D(sentence.getBegin(), sentence.getEnd()))) {
                counter++;
                Date date = (Date) dates.get(dateId);
                long diff = date.getElapse();
                String status = date.getTemporality();
                int currentDistance = getDistance(date, concept);
                switch (datePriority) {
                    case EARLYFIRST:
                        if (diff < elapse) {
                            elapse = diff;
                            tempStatus = status;
                        }
                        break;
                    case LATEFIRST:
                        if (diff > elapse) {
                            elapse = diff;
                            tempStatus = status;
                        }
                        break;
                    case CLOSEFIRST:
                        if (currentDistance < closestDistance) {
                            elapse = diff;
                            tempStatus = status;
                            closestDistance = currentDistance;
                        }
                        break;
                }
            }
//          if not date mention is found in the sentence, skip
            if (counter > 0) {
                sentenceTempDiffCache.put(sentenceId, elapse);
                sentenceStatusCache.put(sentenceId, tempStatus);
            } else {
                sentenceTempDiffCache.put(sentenceId, null);
                sentenceStatusCache.put(sentenceId, null);
            }
        }
        String typeName = concept.getType().getShortName();
        if (sentenceTempDiffCache.containsKey(sentenceId) && sentenceTempDiffCache.get(sentenceId) != null) {
            if (saveDateDifference && setDiffMethods.containsKey(typeName))
                AnnotationOper.setFeatureValue(setDiffMethods.get(typeName), concept, sentenceTempDiffCache.get(sentenceId) + "");
            concept.setTemporality(sentenceStatusCache.get(sentenceId));
        } else if (inferAll) {
            if (saveDateDifference && setDiffMethods.containsKey(typeName))
                AnnotationOper.setFeatureValue(setDiffMethods.get(typeName), concept, docElapse + "");
            if (docTempStatus.length() > 0)
                concept.setTemporality(docTempStatus);
        }
    }


    protected long getDiffHours(DateTime dt, DateTime referenceDate) {
        long diff = dt.getMillis() - referenceDate.getMillis();
        diff = diff / 3600000;
        return diff;
    }


    protected DateTime readReferenceDate(RecordRow recordRow, String referenceDateColumnName) {
        String dateString = (String) recordRow.getValueByColumnName(referenceDateColumnName);
        if (dateString == null)
            return null;
        return parseDateString(dateString, referenceDate);
    }

    protected DateTime parseDateString(String dateString, DateTime recordDate) {
        java.util.Date utilDate = null;
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


        DateTime date = new DateTime(utilDate);
        return date;
    }

    protected boolean checkExclusionClass(Annotation anno, Collection<Class<? extends Annotation>> exclusionClasses) {
        for (Class<? extends Annotation> typeCls : exclusionClasses) {
            if (typeCls.isAssignableFrom(anno.getClass()))
                return false;
        }
        return true;
    }

    protected void indexSegments(JCas jCas,
                                 Collection<Class<? extends Annotation>> includeClasses,
                                 IntervalST<Integer> segementTree, ArrayList<Annotation> segments,
                                 boolean checkOverlap, Collection<Class<? extends Annotation>> exclusionClasses) {
        for (Class segClass : includeClasses) {
            FSIndex annoIndex = jCas.getAnnotationIndex(segClass);
            Iterator annoIter = annoIndex.iterator();
            while (annoIter.hasNext()) {
                Annotation segAnno = (Annotation) annoIter.next();
                if (exclusionClasses != null && checkExclusionClass(segAnno, exclusionClasses)) {
                    logger.finest(segAnno.getType().getShortName() + " belongs to one of exclusion Types, skip indexing.");
                    continue;
                }
                Interval1D interval = new Interval1D(segAnno.getBegin(), segAnno.getEnd());
                if (checkOverlap && segementTree.get(interval) != null) {
                    int existingSegId = segementTree.get(interval);
                    Annotation existingSeg = segments.get(existingSegId);
                    if ((existingSeg.getEnd() - existingSeg.getBegin()) > (segAnno.getEnd() - segAnno.getBegin())) {
                        continue;
                    } else {
                        segementTree.remove(interval);
                        segments.set(existingSegId, segAnno);
                        segementTree.put(interval, existingSegId);
                    }
                } else {
                    segementTree.put(interval, segments.size());
                    segments.add(segAnno);
                }
            }
        }
    }


    protected int getDistance(Date date, ConceptBASE concept) {
        if (date.getBegin() > concept.getEnd()) {
            return date.getBegin() - concept.getEnd();
        } else if (date.getEnd() < concept.getBegin()) {
            return concept.getBegin() - date.getEnd();
        }
        return 0;
    }


    public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
        LinkedHashMap<String, TypeDefinition> typeDefs = new FastNER_AE_General().getTypeDefs(ruleStr);
        for (String typeName : typeDefs.keySet()) {
            TypeDefinition typeDef = typeDefs.get(typeName);
            if (!typeDef.getFeatureValuePairs().containsKey(DIFFMETHOD)) {
                typeDef.addFeatureName(DIFFMETHOD);
            }
        }
        typeDefs.remove(DeterminantValueSet.TEMPORAL_CATEGORIES1.substring(1));
        return typeDefs;
    }


}
