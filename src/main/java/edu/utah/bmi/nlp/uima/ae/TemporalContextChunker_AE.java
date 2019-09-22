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


import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.Date;
import edu.utah.bmi.nlp.type.system.Sentence;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.joda.time.DateTime;

import java.util.*;


/**
 * This is an AE to assign temporal context values to concepts based on the TemporalAnnotator identified date mentions.
 * If InferAll is true, the default temporal value will be set based on the difference between record date and reference date.
 *
 * @author Jianlin Shi
 */
public class TemporalContextChunker_AE extends TemporalContext_AE {
    public static final String PARAM_INCLUDE_SECTIONS = "IncludeSections";
    protected HashSet<Class<? extends Annotation>> includeSectionClasses = new LinkedHashSet<>();

    public static final String PARAM_EXCLUDE_SECTIONS = "ExcludeSections";
    protected HashSet<Class<? extends Annotation>> excludeSectionClasses = new LinkedHashSet<>();

    public void initialize(UimaContext cont) {
        super.initialize(cont);
        Object obj;

        obj = cont.getConfigParameterValue(PARAM_INCLUDE_SECTIONS);
        if (obj != null && ((String) obj).trim().length() > 0) {
            for (String sectionName : ((String) obj).split("[\\|,;]")) {
                sectionName = sectionName.trim();
                includeSectionClasses.add(AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(sectionName)));
            }
        }

        obj = cont.getConfigParameterValue(PARAM_EXCLUDE_SECTIONS);
        if (obj != null && ((String) obj).trim().length() > 0) {
            for (String sectionName : ((String) obj).split("[\\|,;]")) {
                sectionName = sectionName.trim();
                excludeSectionClasses.add(AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(sectionName)));
            }
        }

    }


    public void process(JCas jCas) throws AnalysisEngineProcessException {
        HashMap<Integer, Long> sentenceTempDiffCache = new HashMap<>();
        HashMap<Integer, String> sentenceStatusCache = new HashMap<>();
        RecordRow metaRecordRow = AnnotationOper.deserializeDocSrcInfor(jCas);
        referenceDate = readReferenceDate(metaRecordRow, referenceDateColumnName);
        DateTime recordDate = readReferenceDate(metaRecordRow, recordDateColumnName);

        if (referenceDate == null) {
            logger.fine("No value in Reference date column: '" + referenceDateColumnName + "'. Skip the TemporalConTextDetector.");
            return;
        }

        if (recordDate == null) {
            logger.info("Document" + metaRecordRow.getValueByColumnId(0) + " doesn't have record date meta in JCas.");
            recordDate = referenceDate;
        }


        IntervalST<Integer> sectionTree = new IntervalST<>();
        ArrayList<Annotation> sections = new ArrayList<>();
        indexSegments(jCas, includeSectionClasses, sectionTree, sections, false, excludeSectionClasses);


        IntervalST<Integer> sentenceTree = new IntervalST<>();
        ArrayList<Annotation> sentences = new ArrayList<>();
        indexSegments(jCas, Arrays.asList(Sentence.class), sentenceTree, sentences, false, null);

        IntervalST<Integer> dateTree = new IntervalST<>();
        ArrayList<Annotation> dates = new ArrayList<>();
        indexSegments(jCas, Arrays.asList(Date.class), dateTree, dates, true, null);


        IntervalST<Integer> filteredConceptTree = new IntervalST<>();
        ArrayList<Annotation> filteredConcepts = new ArrayList<>();
        LinkedHashMap<Integer, ArrayList<Date>> toChunkSectionIds = new LinkedHashMap<>();

        indexFilterConcepts(jCas, targetConceptClasses, filteredConceptTree, filteredConcepts, toChunkSectionIds,
                sectionTree, sections, sentenceTree, sentences, dateTree, dates);

        for (int sectionId : toChunkSectionIds.keySet()) {
            Annotation section = sections.get(sectionId);
            int chunkBegin = section.getBegin();
            for (int i = 1; i < toChunkSectionIds.get(sectionId).size(); i++) {
                Date previousDate = toChunkSectionIds.get(sectionId).get(i - 1);
//              Use current sentence begin to set previous chunk end, because the new date will only be updated in this sentence,
//              which has another date mention
                Date date = toChunkSectionIds.get(sectionId).get(i);
                int sentenceId = sentenceTree.get(new Interval1D(date.getBegin(), date.getEnd()));
                int chunkEnd = sentences.get(sentenceId).getBegin() - 1;

//              Go back to check previous sentence to see if there are multiple date mentions, if so, do sub-chunks.
                Interval1D previousDateInterval = new Interval1D(previousDate.getBegin(), previousDate.getEnd());
                int previousSentenceId = sentenceTree.get(previousDateInterval);
                Annotation previousSentence = sentences.get(previousSentenceId);
                Interval1D sentenceInterval = new Interval1D(previousSentence.getBegin(), previousSentence.getEnd());
                LinkedList<Integer> allDateIds = dateTree.getAllAsList(sentenceInterval);
                if (allDateIds.size() > 1) {
//                    This is just approximate,if multiple dates appear in one sentence, split on dates.
                    int counter = 0;
                    while (counter < allDateIds.size()) {
                        date = toChunkSectionIds.get(sectionId).get(i);
                        int subchunkEnd = date.getEnd();
                        Interval1D chunkInterval = new Interval1D(chunkBegin, subchunkEnd);
//                       If the date mention is the last one in the sentence, set the chunk end to the sentence end.
                        if (counter == allDateIds.size() - 1) {
                            if (i == toChunkSectionIds.get(sectionId).size() - 1) {
                                int nextChunkEnd = section.getEnd();
                                Interval1D nextChunkInterval = new Interval1D(chunkEnd+1, nextChunkEnd);
                                assignTemporalValuesInChunk(nextChunkInterval, date, filteredConceptTree, filteredConcepts);
                            }
                            chunkInterval = new Interval1D(chunkBegin, chunkEnd);
                        }
                        assignTemporalValuesInChunk(chunkInterval, previousDate, filteredConceptTree, filteredConcepts);
                        chunkBegin = chunkEnd + 1;
                        counter++;
                        i++;
                    }
                } else {
                    if (i == toChunkSectionIds.get(sectionId).size() - 1) {
                        int nextChunkEnd = section.getEnd();
                        Interval1D nextChunkInterval = new Interval1D(chunkEnd+1, nextChunkEnd);
                        assignTemporalValuesInChunk(nextChunkInterval, date, filteredConceptTree, filteredConcepts);
                    }
                    Interval1D chunkInterval = new Interval1D(chunkBegin, chunkEnd);
                    assignTemporalValuesInChunk(chunkInterval, previousDate, filteredConceptTree, filteredConcepts);
                    chunkBegin = chunkEnd + 1;
                    if (chunkBegin > section.getEnd() + 1)
                        break;
                }
            }

        }


    }

    private void assignTemporalValuesInChunk(Interval1D chunkInterval, Date date,
                                             IntervalST<Integer> filteredConceptTree, ArrayList<Annotation> filteredConcepts) {
        for (int conceptId : filteredConceptTree.getAll(chunkInterval)) {
            Concept concept = (Concept) filteredConcepts.get(conceptId);
            String typeName = concept.getType().getShortName();
            if (saveDateDifference && setDiffMethods.containsKey(typeName))
                AnnotationOper.setFeatureValue(setDiffMethods.get(typeName), concept, date.getElapse() + "");
            concept.setTemporality(date.getTemporality());
        }
    }

    private void indexFilterConcepts(JCas jCas, LinkedHashMap<String, Class<? extends Annotation>> targetConceptClasses,
                                     IntervalST<Integer> filteredConceptTree, ArrayList<Annotation> filteredConcepts,
                                     LinkedHashMap<Integer, ArrayList<Date>> toChunkSectionIds,
                                     IntervalST<Integer> sectionTree, ArrayList<Annotation> sections,
                                     IntervalST<Integer> sentenceTree, ArrayList<Annotation> sentences,
                                     IntervalST<Integer> dateTree, ArrayList<Annotation> dates) {
        for (Class<? extends Annotation> cls : targetConceptClasses.values()) {
            Iterator annoIter = JCasUtil.iterator(jCas, cls);
            while (annoIter.hasNext()) {
                Annotation anno = (Annotation) annoIter.next();
                if (anno instanceof Date) {
                    continue;
                }
                if (!(anno instanceof Concept)) {
                    logger.finest(cls.getCanonicalName() + " is not an instance of ConceptBASE. So it cannot be processed through TemporalContext_AE_General2");
                    continue;
                }

                Concept concept = (Concept) anno;


//              only analyze the concepts within specified sections, the rest will be ignored.
                Interval1D interval = new Interval1D(concept.getBegin(), concept.getEnd());
                if (!sectionTree.contains(interval))
                    continue;


                if (!sentenceTree.contains(interval))
                    continue;

//              If the sentence containing this concept has date mentions, skip---because it should have been assigned
//               temporal status information through TemporalContext_AE_General.
                int sentenceId = sentenceTree.get(interval);
                Annotation sentence = sentences.get(sentenceId);
                if (dateTree.contains(new Interval1D(sentence.getBegin(), sentence.getEnd()))) {
                    continue;
                }

                int sectionId = sectionTree.get(interval);
                if (toChunkSectionIds.containsKey(sectionId) && toChunkSectionIds.get(sectionId) == null)
                    continue;
                if (!toChunkSectionIds.containsKey(sectionId)) {
                    Annotation section = sections.get(sectionId);
                    Interval1D sectionInterval = new Interval1D(section.getBegin(), section.getEnd());
//                  if section doesn't have any date mentions, skip the section
                    if (!dateTree.contains(sectionInterval)) {
                        toChunkSectionIds.put(sectionId, null);
                        continue;
                    }

                    ArrayList<Date> currentDates = new ArrayList<>();
                    for (int dateId : dateTree.getAll(sectionInterval)) {
                        currentDates.add((Date) dates.get(dateId));
                    }
                    currentDates.sort(Comparator.comparingInt(Annotation::getBegin));
                    toChunkSectionIds.put(sectionId, currentDates);

                }
                filteredConceptTree.put(interval, filteredConcepts.size());
                filteredConcepts.add(concept);
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


}
