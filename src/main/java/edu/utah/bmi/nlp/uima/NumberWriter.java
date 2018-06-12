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
	public static final String PARAM_SPELL_OUTPUT_TYPE = "SpellOutputType";
	public static final String PARMA_ADD_OUTPUT_TYPE = "AdditionOutputType";
	public static final String PARAM_ANNOTATOR = "Annotator";
	public static String resultTableName, outputTypeName, spellOutputTypeName, additionOutputTypeName, annotator;
	public static EDAO dao = null;
	private Type outputType = null, addOutputType = null, spellOutputType = null;
	private int firstGroupSize = 0;


	public void initialize(UimaContext cont) {
		Object parameterObject = cont.getConfigParameterValue(PARAM_SQLFILE);
		String configFile = parameterObject != null ? (String) parameterObject : "conf/sqliteconfig.xml";

		parameterObject = cont.getConfigParameterValue(PARAM_TABLENAME);
		resultTableName = (String) parameterObject;

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

		parameterObject = cont.getConfigParameterValue(PARAM_ANNOTATOR);
		annotator = (String) parameterObject;

		if (dao == null || dao.isClosed()) {
			dao = EDAO.getInstance(new File(configFile));
		}
		dao.initiateTableFromTemplate("NUMBERS_TABLE", resultTableName, false);

	}

	@Override
	public void process(JCas jCas) {
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
		}

		firstGroupSize = 0;
		boolean success = getSampleSize(cas, doc_name);
		if (!success) {
			success = getSpellSampleSize(cas, doc_name);
		}
		if (!success) {
			success = addGroupSizes(jCas, cas, doc_name);
		}
		if (!success && firstGroupSize > 0) {
			dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
					.addCell("ANNOTATOR", annotator)
					.addCell("NUMBER", firstGroupSize)
					.addCell("COMMENTS", "uncertain"));
			success=true;
		}

		if (!success) {
			dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
					.addCell("ANNOTATOR", annotator)
					.addCell("NUMBER", 0)
					.addCell("COMMENTS", ""));
		}

	}

	private boolean getSpellSampleSize(CAS cas, String doc_name) {
		Collection<AnnotationFS> annos = CasUtil.select(cas, spellOutputType);
		for (AnnotationFS anno : annos) {
			if (anno == null)
				continue;
			String numText = anno.getCoveredText();
			int sampleSize = convertTextualNumbersToInteger(numText);
			if (sampleSize > 0) {
				dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
						.addCell("ANNOTATOR", annotator)
						.addCell("NUMBER", sampleSize)
						.addCell("COMMENTS", ""));
				return true;
			} else {
				System.out.println(numText + " cannot be converted in document " + doc_name);
			}
		}
		return false;

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

	private boolean addGroupSizes(JCas jCas, CAS cas, String doc_name) {
		ArrayList<Sentence> sentences = new ArrayList<>();
		IntervalST<Integer> sentenceTree = buildSentenceTree(jCas, sentences);
		Collection<AnnotationFS> annos = CasUtil.select(cas, addOutputType);
		ArrayList<Integer> groupSizes = new ArrayList<>();
		int sampleSize = addGroupSizes(annos, doc_name, sentenceTree, groupSizes);
		if (sampleSize > 0) {
			dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
					.addCell("ANNOTATOR", annotator)
					.addCell("NUMBER", sampleSize)
					.addCell("COMMENTS", groupSizes.toString()));
			return true;
		}
		return false;
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
			if (firstGroupSize == 0)
				firstGroupSize = groupSize;
			if (groupSize > 0) {
				sampleSize += groupSize;
				groupSizes.add(groupSize);
				counter++;
			}
		}
//		make sure there are >2 eligible group size numbers were extracted.
		if (counter > 1)
			return sampleSize;
		else
			return 0;
	}

	public boolean getSampleSize(CAS cas, String doc_name) {
		Collection<AnnotationFS> annos = CasUtil.select(cas, outputType);
		for (AnnotationFS anno : annos) {
			if (anno == null)
				continue;
			String numText = anno.getCoveredText();
			int sampleSize = convertNumber(numText, doc_name);
			if (sampleSize > 0) {
				dao.insertRecord(resultTableName, new RecordRow().addCell("DOC_NAME", doc_name)
						.addCell("ANNOTATOR", annotator)
						.addCell("NUMBER", sampleSize)
						.addCell("COMMENTS", ""));
				return true;
			}
		}
		return false;
	}

	private int convertNumber(String numText, String doc_name) {
		int sampleSize = -1;
		numText = numText.replaceAll("[,| ]", "");
		try {
			sampleSize = NumberUtils.createInteger(numText);
		} catch (Exception error) {
			System.out.println("Cannot parse " + numText + "in document: " + doc_name);
		}
		return sampleSize;
	}
}
