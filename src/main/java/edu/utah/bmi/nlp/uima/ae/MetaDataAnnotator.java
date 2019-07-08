package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Token;
import edu.utah.bmi.nlp.uima.common.UIMATypeFunctions;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * Use meta data to create annotations for down steam inferences
 */
public class MetaDataAnnotator extends JCasAnnotator_ImplBase implements RuleBasedAEInf {
	public static Logger logger = IOUtil.getLogger(MetaDataAnnotator.class);
	public static final String PARAM_RULE_STR = DeterminantValueSet.PARAM_RULE_STR;
	private ArrayList<ArrayList<Object>> rules;
	private final static int NUMERIC_RULE = 1, CATEGORICAL_RULE = 0;
	private HashMap<String, Constructor<? extends Annotation>> docTypeConstructorMap = new HashMap<>();
	private HashMap<String, String> defaultDocTypes = new HashMap<>();
	private HashMap<String, String> valueFeatureMap = new HashMap<>();
	private LinkedHashMap<String, TypeDefinition> typeDefinitions = new LinkedHashMap<>();
	private ArrayList<ArrayList<String>> ruleCells = new ArrayList<>();

	public void initialize(UimaContext cont) {
		/***
		 * Rule format
		 * ConclusionType|tColumnName\tCondition
		 * Condition examples:
		 * 1.   >3
		 * 2.   Progress_Note;H&P
		 *
		 */

		String inferenceStr = (String) cont.getConfigParameterValue(PARAM_RULE_STR);
		rules = parseRuleStr(inferenceStr);
	}

	protected ArrayList<ArrayList<Object>> parseRuleStr(String ruleStr) {
		ArrayList<ArrayList<Object>> rules = new ArrayList<>();
		IOUtil ioUtil = new IOUtil(ruleStr, true);
		typeDefinitions = getTypeDefs(ruleStr, ioUtil);
		for (TypeDefinition typeDefinition : typeDefinitions.values()) {
			buildConstructor(typeDefinition);
		}

		for (ArrayList<String> row : ioUtil.getRuleCells()) {
			String conclusion = row.get(1).trim();
			String columnName = row.get(2).trim();
			String condition = row.get(3).trim();
			int conditionType = isNumbericRule(condition);
			ArrayList<Object> rule = new ArrayList<>();
			rule.add(columnName);
			rule.add(conditionType);
			if (conditionType == NUMERIC_RULE) {
				ArrayList<Object> numeric_conditions = parseNumericCondition(condition);
				rule.add(numeric_conditions);
			} else {
				HashSet<String> valueSet = new HashSet<>();
				for (String value : condition.split("[;,\\|]")) {
					valueSet.add(value);
				}
				rule.add(valueSet);
			}
			rule.add(conclusion);
			rules.add(rule);
		}
		return rules;
	}

	protected ArrayList<Object> parseNumericCondition(String condition) {
		ArrayList<Object> conditions = new ArrayList<>();
		int whitespace = 0, num = 1, operator = 2;
		int previousCharType = 0;
		StringBuilder sb = new StringBuilder();
		for (char c : condition.toCharArray()) {
			if (Character.isDigit(c) || c == '.') {
				if (previousCharType == num || previousCharType == operator)
					sb.append(c);
				previousCharType = num;
			} else if (Character.isWhitespace(c)) {
				if (previousCharType == num && sb.length() > 0)
					conditions.add(Double.parseDouble(sb.toString()));
				sb.setLength(0);
				previousCharType = whitespace;
				continue;
			} else if (c == '<' || c == '>' || c == '=') {
				if (previousCharType == num)
					conditions.add(Double.parseDouble(sb.toString()));
				conditions.add(c);
				sb.setLength(0);
				previousCharType = operator;
			}

		}
		if (sb.length() > 0)
			conditions.add(Double.parseDouble(sb.toString()));
		return conditions;
	}

	private void buildConstructor(TypeDefinition typeDefinition) {
		Class docType;
		try {
			if (!docTypeConstructorMap.containsKey(typeDefinition.shortTypeName)) {
				docType = Class.forName(typeDefinition.fullTypeName);
				Constructor cc = docType.getConstructor(JCas.class, int.class, int.class);
				docTypeConstructorMap.put(typeDefinition.shortTypeName, cc);
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}

	}


	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		RecordRow baseRecordRow = new RecordRow();
		FSIterator it = aJCas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
		if (it.hasNext()) {
			SourceDocumentInformation e = (SourceDocumentInformation) it.next();
			String serializedString = new File(e.getUri()).getName();
			baseRecordRow.deserialize(serializedString);
		}
		ArrayList<String> conclusionTypes = getAnnotationTypes(baseRecordRow);
		for (String typeName : conclusionTypes) {
			saveAnnotation(aJCas, typeName);
		}
	}

	private void saveAnnotation(JCas aJCas, String typeName) {
		Constructor<? extends Annotation> annoConstructor = docTypeConstructorMap.get(typeName);
		Token anno = JCasUtil.selectByIndex(aJCas, Token.class, 0);
		try {
			Annotation conclusionAnno = annoConstructor.newInstance(aJCas, anno.getBegin(), anno.getEnd());
			conclusionAnno.addToIndexes();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	private ArrayList<String> getAnnotationTypes(RecordRow baseRecordRow) {
		ArrayList<String> types = new ArrayList<>();
		for (ArrayList<Object> rule : rules) {
			String columnName = (String) rule.get(0);
			String value = baseRecordRow.getStrByColumnName(columnName);
			if (value == null || value.length() == 0) {
				logger.finest("Column: "+columnName+" is not included  in the meta data.");
				continue;
			}
			if (NUMERIC_RULE == (int) rule.get(1)) {
				if (evalNumericCondition((ArrayList<Object>) rule.get(2), Double.parseDouble(value))) {
					types.add((String) rule.get(3));
				}
			} else {
				if (evalCategoricalCondition((HashSet<String>) rule.get(2), value)) {
					types.add((String) rule.get(3));
				}
			}
		}
		return types;
	}

	private boolean evalCategoricalCondition(HashSet<String> valueSet, String value) {
		if (valueSet.contains(value))
			return true;
		return false;
	}

	private boolean evalNumericCondition(ArrayList<Object> conditions, double value) {
		for (int i = 0; i < conditions.size() - 1; i += 2) {
			char operator = (char) conditions.get(i);
			double boundaryValue = (double) conditions.get(i + 1);
			switch (operator) {
				case '<':
					if (value >= boundaryValue)
						return false;
					break;
				case '>':
					if (value <= boundaryValue)
						return false;
					break;
				case '=':
					if (value != boundaryValue)
						return false;
					break;
			}

		}
		return true;
	}

	private int isNumbericRule(String condition) {
		if (condition.indexOf("<") > -1 || condition.indexOf(">") > -1 || condition.indexOf("=") > -1) {
			return NUMERIC_RULE;
		}
		return CATEGORICAL_RULE;
	}

	@Override
	public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
		if (ruleStr.indexOf("|") != -1) {
			ruleStr = ruleStr.replaceAll("\\|", "\n");
		}
		IOUtil ioUtil = new IOUtil(ruleStr, true);
		typeDefinitions = getTypeDefs(ruleStr, ioUtil);
		return typeDefinitions;
	}

	protected LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr, IOUtil ioUtil) {
		if (ioUtil.getInitiations().size() > 1) {
			UIMATypeFunctions.getTypeDefinitions(ruleStr, ruleCells,
					valueFeatureMap, new HashMap<>(), defaultDocTypes, typeDefinitions);

		}
		return typeDefinitions;
	}
}
