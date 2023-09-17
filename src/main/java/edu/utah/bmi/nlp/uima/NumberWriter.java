package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Sentence;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import static edu.utah.bmi.simple.gui.core.WordsToNumbersUtils.convertTextualNumbersToInteger;

/**
 * @author Jianlin Shi on 5/12/18.
 */
public class NumberWriter extends JCasAnnotator_ImplBase {
	public static Logger logger = IOUtil.getLogger(NumberWriter.class);
	public static final String PARAM_SQLFILE = "DBConfigFile";
	public static final String PARAM_TABLENAME = "ResultTableName";
	public static final String PARAM_OUTPUT_TYPE = "OutputType";
	public static final String PARAM_PRIO_TYPE = "PrioType";
	public static final String PARAM_SPELL_OUTPUT_TYPE = "SpellOutputType";
	public static final String PARMA_ADD_OUTPUT_TYPE = "AdditionOutputType";
	public static final String PARAM_ANNOTATOR = "Annotator";
	public static final String PARAM_MIN_VALUE = "MinValue";
	public static final String PARAM_MAX_VALUE = "MaxValue";
	public static String resultTableName, prioTypeName, outputTypeName, spellOutputTypeName, additionOutputTypeName, annotator;
	public static EDAO dao = null;
	private Type prioType = null, outputType = null, addOutputType = null, spellOutputType = null;
	private int firstGroupSize = 0, minValue = 9, maxValue = 30000;


	public void initialize(UimaContext cont) {
		Object parameterObject = cont.getConfigParameterValue(PARAM_SQLFILE);
		String configFile = parameterObject != null ? (String) parameterObject : "conf/sqliteconfig.xml";

		parameterObject = cont.getConfigParameterValue(PARAM_TABLENAME);
		resultTableName = (String) parameterObject;

		parameterObject = cont.getConfigParameterValue(PARAM_PRIO_TYPE);
		if (parameterObject != null && parameterObject.toString().trim().length() > 0)
			prioTypeName = (String) parameterObject;
		else
			prioTypeName = "TOTAL_SAMPLE_SIZE";

		parameterObject = cont.getConfigParameterValue(PARAM_OUTPUT_TYPE);
		if (parameterObject != null && parameterObject.toString().trim().length() > 0)
			outputTypeName = (String) parameterObject;
		else
			outputTypeName = "SAMPLE_SIZE";

		parameterObject = cont.getConfigParameterValue(PARAM_SPELL_OUTPUT_TYPE);
		if (parameterObject != null && parameterObject.toString().trim().length() > 0)
			spellOutputTypeName = (String) parameterObject;
		else
			spellOutputTypeName = "SPELL_SAMPLE_SIZE";

		parameterObject = cont.getConfigParameterValue(PARMA_ADD_OUTPUT_TYPE);
		if (parameterObject != null && parameterObject.toString().trim().length() > 0)
			additionOutputTypeName = (String) parameterObject;
		else
			additionOutputTypeName = "GROUP_SIZE";

		parameterObject = cont.getConfigParameterValue(PARAM_MIN_VALUE);
		if (parameterObject != null && parameterObject instanceof Integer)
			minValue = (int) parameterObject;

		parameterObject = cont.getConfigParameterValue(PARAM_MAX_VALUE);
		if (parameterObject != null && parameterObject instanceof Integer)
			maxValue = (int) parameterObject;


		parameterObject = cont.getConfigParameterValue(PARAM_ANNOTATOR);
		annotator = (String) parameterObject;

		dao = new EDAO(new File(configFile));

		dao.initiateTableFromTemplate("NUMBERS_TABLE", resultTableName, false);

	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		RecordRow recordRow = new RecordRow();
		FSIterator it = jCas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
		SourceDocumentInformation e;
		if (it.hasNext()) {
			e = (SourceDocumentInformation) it.next();
			String serializedString = e.getUri();
			recordRow.deserialize(serializedString);
		}
		String doc_name = (String) recordRow.getValueByColumnName("DOC_NAME");

		CAS cas = jCas.getCas();
		if (outputType == null) {
			outputType = CasUtil.getAnnotationType(cas, DeterminantValueSet.checkNameSpace(outputTypeName));
			addOutputType = CasUtil.getAnnotationType(cas, DeterminantValueSet.checkNameSpace(additionOutputTypeName));
			spellOutputType = CasUtil.getAnnotationType(cas, DeterminantValueSet.checkNameSpace(spellOutputTypeName));
			prioType = CasUtil.getAnnotationType(cas, DeterminantValueSet.checkNameSpace(prioTypeName));
		}

		firstGroupSize = 0;
		boolean success = false;
		int sampleSize = getTotalSampleSize(cas, doc_name);
		if (sampleSize > minValue && sampleSize < maxValue) {
			dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
					.addCell("ANNOTATOR", annotator)
					.addCell("NUMBER", sampleSize)
					.addCell("COMMENTS", "certain"));
			return;
		}
		int[] res1 = getSpellSampleSize(cas, doc_name);
		int[] res2 = getSampleSize(cas, doc_name);
		int[] res3 = addGroupSizes(jCas, cas, doc_name);
		ArrayList<int[]> ress = new ArrayList<>();
		ress.add(res1);
		ress.add(res2);
		ress.add(res3);
		Collections.sort(ress, (o1, o2) -> o1[1] - o2[1]);
		int[] res = ress.get(0);

		if (res[1] != 999999) {
			dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
					.addCell("ANNOTATOR", annotator)
					.addCell("NUMBER", res[0])
					.addCell("COMMENTS", "certain"));
			success = true;
		} else if (firstGroupSize > minValue && firstGroupSize < maxValue) {
			dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
					.addCell("ANNOTATOR", annotator)
					.addCell("NUMBER", firstGroupSize)
					.addCell("COMMENTS", "uncertain"));
			success = true;
		}

		if (!success) {
			dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
					.addCell("ANNOTATOR", annotator)
					.addCell("NUMBER", 0)
					.addCell("COMMENTS", ""));
		}

	}

	private int getTotalSampleSize(CAS cas, String doc_name) {
		Collection<AnnotationFS> annos = CasUtil.select(cas, prioType);
		int sampleSize = 0;
		for (AnnotationFS anno : annos) {
			if (anno == null)
				continue;
			String numText = anno.getCoveredText();
			sampleSize = convertNumber(numText, doc_name);
			if (sampleSize > minValue && sampleSize < maxValue) {
//				dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
//						.addCell("ANNOTATOR", annotator)
//						.addCell("NUMBER", sampleSize)
//						.addCell("COMMENTS", ""));
				return sampleSize;
			} else {
				System.out.println(numText + " cannot be converted in document " + doc_name);
			}
		}
		return sampleSize;
	}


	private IntervalST<Integer> buildSentenceTree(JCas jCas, ArrayList<Sentence> sentences) {
		IntervalST<Integer> sentenceTree = new IntervalST<>();
		for (Iterator<Sentence> it = JCasUtil.iterator(jCas, Sentence.class); it.hasNext(); ) {
			Sentence sentence = it.next();
			sentenceTree.put(new Interval1D(sentence.getBegin(), sentence.getEnd()), sentences.size());
			sentences.add(sentence);
		}
		return sentenceTree;
	}

	private int[] addGroupSizes(JCas jCas, CAS cas, String doc_name) {
		ArrayList<Sentence> sentences = new ArrayList<>();
		IntervalST<Integer> sentenceTree = buildSentenceTree(jCas, sentences);
		Collection<AnnotationFS> annos = CasUtil.select(cas, addOutputType);
		ArrayList<Integer> groupSizes = new ArrayList<>();
		int sampleSize = addGroupSizes(annos, doc_name, sentenceTree, groupSizes);
		if (sampleSize > minValue && sampleSize < maxValue) {
			return new int[]{sampleSize, annos.iterator().next().getBegin()};
		}
		return new int[]{-1, 999999};
	}

	private int addGroupSizes(Collection<AnnotationFS> annos, String doc_name, IntervalST<Integer> sentenceTree, ArrayList<Integer> groupSizes) {
		LinkedHashMap<Integer, ArrayList<AnnotationFS>> counter = new LinkedHashMap<>();
		int sampleSize = -1;
		for (AnnotationFS anno : annos) {
			Interval1D annoInterVal = new Interval1D(anno.getBegin(), anno.getEnd());
			if (!sentenceTree.contains(annoInterVal)) {
				logger.warning("Annotation " + anno.getCoveredText() + " doesn't belong to any sentence. ");
				continue;
			}
			int sentenceId = sentenceTree.get(new Interval1D(anno.getBegin(), anno.getEnd()));
			if (!counter.containsKey(sentenceId)) {
				counter.put(sentenceId, new ArrayList<>());
			}
			counter.get(sentenceId).add(anno);
		}
		for (Integer sentenceId : counter.keySet()) {
			if (counter.get(sentenceId).size() > 0) {
				groupSizes.clear();
				sampleSize = computeAddition(counter.get(sentenceId), doc_name, groupSizes);
				if (sampleSize > 0)
					break;
			}
		}
		return sampleSize;
	}

	private int computeAddition(ArrayList<AnnotationFS> annos, String doc_name, ArrayList<Integer> groupSizes) {
		int sampleSize = 0;
		int counter = 0;
		for (AnnotationFS anno : annos) {
			int groupSize = convertNumber(anno.getCoveredText(), doc_name);
			if (groupSize > minValue / 2 && groupSize < maxValue) {
				if (firstGroupSize == 0)
					firstGroupSize = groupSize;
				if (groupSize > 0) {
					sampleSize += groupSize;
					groupSizes.add(groupSize);
					counter++;
				}
			}
		}
//		make sure there are >2 eligible group size numbers were extracted.
		if (counter > 1)
			return sampleSize;
		else
			return 0;
	}

	private int[] getSpellSampleSize(CAS cas, String doc_name) {
		Collection<AnnotationFS> annos = CasUtil.select(cas, spellOutputType);
		for (AnnotationFS anno : annos) {
			if (anno == null)
				continue;
			String numText = anno.getCoveredText();
			int sampleSize = convertTextualNumbersToInteger(numText);
			if (sampleSize > minValue && sampleSize < maxValue) {
//				dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
//						.addCell("ANNOTATOR", annotator)
//						.addCell("NUMBER", sampleSize)
//						.addCell("COMMENTS", ""));
				return new int[]{sampleSize, anno.getBegin()};
			} else {
				System.out.println(numText + " cannot be converted in document " + doc_name);
			}
		}
		return new int[]{-1, 999999};

	}

	public int[] getSampleSize(CAS cas, String doc_name) {
		Collection<AnnotationFS> annos = CasUtil.select(cas, outputType);
		for (AnnotationFS anno : annos) {
			if (anno == null)
				continue;
			String numText = anno.getCoveredText();
			int sampleSize = convertNumber(numText, doc_name);
			if (sampleSize > minValue && sampleSize < maxValue) {
//				dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
//						.addCell("ANNOTATOR", annotator)
//						.addCell("NUMBER", sampleSize)
//						.addCell("COMMENTS", ""));
				return new int[]{sampleSize, anno.getBegin()};
			}
		}
		return new int[]{-1, 999999};
	}

	private int convertNumber(String numText, String doc_name) {
		int sampleSize = 0;
		try {
			sampleSize = NumberUtils.createInteger(numText.replaceAll("[,| ]", ""));
		} catch (Exception error) {

		}
		if (sampleSize == 0) {
			try {
				sampleSize = convertTextualNumbersToInteger(numText);
			} catch (Exception error) {

			}
		}
		if (sampleSize == 0) {
			System.out.println(numText + " in document: " + doc_name + "  is not a spelled out number");
		}
		return sampleSize;
	}
}
