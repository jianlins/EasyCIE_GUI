package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.*;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.logging.Logger;

/**
 * Find overlapped annotations with the same annotation type, choose the widest one
 *
 * @author Jianlin Shi
 * Created on 7/6/16.
 */
public class NERCoordinator_AE extends JCasAnnotator_ImplBase implements RuleBasedAEInf {
	private static Logger logger = IOUtil.getLogger(edu.utah.bmi.nlp.uima.ae.NERCoordinator_AE.class);
	public static final String PARAM_RULE_STR = DeterminantValueSet.PARAM_RULE_STR;
	protected LinkedHashSet<Class> inclusions = new LinkedHashSet<>();
	public void initialize(UimaContext cont) {
		String ruleStr = (String) (cont
				.getConfigParameterValue(PARAM_RULE_STR));
		IOUtil ioUtil = new IOUtil(ruleStr, true);
		for (ArrayList<String> row : ioUtil.getRuleCells()) {
			if (row.size() > 1 && row.get(1).trim().length() > 0) {
				String inclusionTypeName = DeterminantValueSet.checkNameSpace(row.get(1).trim());
				try {
					Class annoCls = Class.forName(inclusionTypeName).asSubclass(Annotation.class);
					inclusions.add(annoCls);
				} catch (ClassNotFoundException e) {
					logger.fine(" NERCoordinator_AE rules contain undefined annotation type: " + row.get(1) + " at row " + row.get(0));
				}
			}
		}
	}


	@Override
	public void process(JCas jCas) {
		for (Class annoCls : inclusions) {
			IntervalST<Annotation> existingTree = new IntervalST();
			for(Object annoObj:JCasUtil.select(jCas,annoCls)){
				Annotation anno = (Annotation) annoObj;
				checkOverlap(existingTree, anno);
			}
		}
	}

	/**
	 * Because FastNER and FastCNER may have overlapped matches.
	 *
	 * @param intervalTree
	 * @param concept
	 */
	private void checkOverlap(IntervalST<Annotation> intervalTree, Annotation concept) {
		Interval1D interval = new Interval1D(concept.getBegin(), concept.getEnd());
		Iterator<Annotation> overlaps = intervalTree.getAll(interval).iterator();
		if(!overlaps.hasNext()){
			intervalTree.put(interval, concept);
		}else {
			while (overlaps.hasNext()) {
				Annotation overlapped = overlaps.next();
				if (overlapped != null) {
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
	}

	@Override
	public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
		return new LinkedHashMap<>();
	}
}
