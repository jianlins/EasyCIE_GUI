package edu.utah.bmi.nlp.easycie;

import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.type.system.ConceptBASE;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Find overlapped annotations with the same annotation type, choose the widest one
 * @author Jianlin Shi
 *         Created on 7/6/16.
 */
public class CoordinateNERResults_AE extends JCasAnnotator_ImplBase {


    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException{

        ArrayList<ConceptBASE> concepts = new ArrayList<>();

        FSIndex annoIndex = jCas.getAnnotationIndex(ConceptBASE.type);
        Iterator annoIter = annoIndex.iterator();
        while (annoIter.hasNext()) {
            concepts.add((ConceptBASE) annoIter.next());
        }
        IntervalST presentTree = new IntervalST();
        IntervalST absentTree = new IntervalST();
        for (ConceptBASE concept : concepts) {
            if (concept.getClass().getSimpleName().equals("PseudoConcept")) {
                checkOverlap(absentTree, concept);
                continue;
            } else {
                checkOverlap(presentTree, concept);
                continue;
            }
        }
    }

    /**
     * Because FastNER and FastCNER may have overlapped matches.
     *
     * @param intervalTree
     * @param concept
     */
    private void checkOverlap(IntervalST intervalTree, ConceptBASE concept) {
        Interval1D interval = new Interval1D(concept.getBegin(), concept.getEnd());
        ConceptBASE overlapped = (ConceptBASE) intervalTree.get(interval);
        if (overlapped != null && (overlapped.getEnd() != concept.getBegin() && concept.getEnd() != overlapped.getBegin())) {
            if ((overlapped.getEnd() - overlapped.getBegin()) < (concept.getEnd() - concept.getBegin())) {
                overlapped.removeFromIndexes();
                intervalTree.remove(new Interval1D(overlapped.getBegin(), overlapped.getEnd()));
                intervalTree.put(interval, concept);
            } else {
                concept.removeFromIndexes();
            }
        } else {
            intervalTree.put(interval, concept);
        }
    }
}
