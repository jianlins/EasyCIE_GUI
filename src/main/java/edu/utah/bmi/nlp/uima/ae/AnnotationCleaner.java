package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.*;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.logging.Logger;

/**
 * Find overlapped annotations with the same annotation type, choose the widest one
 *
 * @author Jianlin Shi
 * Created on 7/6/16.import sqlite3
import pandas as pd

conn = sqlite3.connect('n2c2fmx.sqlite')

df = pd.read_sql_query("select * from RESULT_SNIPPET limit 5;", conn)
 */
public class AnnotationCleaner extends JCasAnnotator_ImplBase implements RuleBasedAEInf {
	private static Logger logger = IOUtil.getLogger(AnnotationCleaner.class);
	public static final String PARAM_RULE_STR = DeterminantValueSet.PARAM_RULE_STR;
	public static final String PARAM_INCLUDE_SUBTYPES = "IncludeSubtypes";
	private boolean includeSubType=true;

	protected LinkedHashSet<Class> inclusions = new LinkedHashSet<>();

	public void initialize(UimaContext cont) {
		String ruleStr = (String) (cont
				.getConfigParameterValue(PARAM_RULE_STR));
		Object value=cont.getConfigParameterValue(PARAM_INCLUDE_SUBTYPES);
		if(value instanceof Boolean && (Boolean)value==false){
			includeSubType=false;
		}
		IOUtil ioUtil = new IOUtil(ruleStr, true);
		for (ArrayList<String> row : ioUtil.getRuleCells()) {
			if (row.size() > 1 && row.get(1).trim().length() > 0) {
				String inclusionTypeName = DeterminantValueSet.checkNameSpace(row.get(1).trim());
				try {
					Class cls = Class.forName(inclusionTypeName);
					inclusions.add(cls);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}

			}
		}
	}


	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		for (Object annoObj : JCasUtil.select(jCas, Annotation.class)) {
			if (inclusions.contains(annoObj.getClass())) {
				continue;
			}
			if (!includeSubType || !isSubClassOfInclusions(annoObj)) {
				Annotation anno = (Annotation) annoObj;
				anno.removeFromIndexes();
			}
		}
	}

	private boolean isSubClassOfInclusions(Object anno) {
		for (Class cls : inclusions) {
			if (cls.isInstance(anno)) {
				return true;
			}
		}
		return false;

	}

	/**
	 * Because FastNER and FastCNER may have overlapped matches.
	 *
	 * @param intervalTree
	 * @param concept
	 */
	private void checkOverlap(IntervalST intervalTree, Annotation concept) {
		Interval1D interval = new Interval1D(concept.getBegin(), concept.getEnd());
		Annotation overlapped = (Annotation) intervalTree.get(interval);
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

	@Override
	public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
		return new LinkedHashMap<>();
	}
}
