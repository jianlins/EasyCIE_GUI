package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class BunchInferencer extends JCasAnnotator_ImplBase implements RuleBasedAEInf {
    public static Logger logger = IOUtil.getLogger(BunchInferencer.class);
    public static final String PARAM_SQLFILE = "DBConfigFile";
    public static final String PARAM_TABLENAME = "ResultTableName";
    public static final String PARAM_AUTO_ID_ENABLED = "AutoIdEnabled";
    public static final String PARAM_RULE_STR = "RuleFileOrStr";
    public static final String PARAM_BUNCH_COLUMN_NAME = "BUNCH_COLUMN_NAME";
    public static final String PARAM_ANNOTATOR = "Annotator";
    public static final String PARAM_VERSION = "Version";


    public static String resultTableName, bunchColumnName;
    public static EDAO dao = null;
    //                          topic   rules
    protected LinkedHashMap<String, ArrayList<ArrayList<Object>>> inferenceMap = new LinkedHashMap<>();
    protected HashMap<String, String> defaultBunchType = new HashMap<>();
    protected boolean autoIdEnabled;
    //                   type name, Type
    protected HashMap<String, Type> typeMap = new HashMap<>();
    //                   value,   feature name
    protected HashMap<String, String> valueFeatureMap = new HashMap<>();
    //                          rule id, count
    protected LinkedHashMap<Integer, Integer> counter = new LinkedHashMap<>();

    //  a bunch can be used to represent an encounter or a patient that have a bunch of documents bundled with.
    protected int previousBunchId = -1;

    protected RecordRow previousRecordRow = null;
    private String annotator;
    private int runId;


    public void initialize(UimaContext cont) {
        Object parameterObject = cont.getConfigParameterValue(PARAM_SQLFILE);
        String configFile = parameterObject != null ? (String) parameterObject : "conf/sqliteconfig.xml";

        parameterObject = cont.getConfigParameterValue(PARAM_TABLENAME);
        resultTableName = parameterObject != null ? (String) parameterObject : "OUTPUT";
        parameterObject = cont.getConfigParameterValue(PARAM_BUNCH_COLUMN_NAME);
        bunchColumnName = parameterObject != null ? (String) parameterObject : "BUNCH_ID";
        parameterObject = cont.getConfigParameterValue(PARAM_AUTO_ID_ENABLED);
        if (parameterObject != null && parameterObject instanceof Boolean && (Boolean) parameterObject != true)
            autoIdEnabled = false;
        String inferenceStr = (String) cont.getConfigParameterValue(PARAM_RULE_STR);
        parameterObject = cont.getConfigParameterValue(PARAM_ANNOTATOR);
        if (parameterObject != null && parameterObject instanceof String)
            annotator = (String) parameterObject;
        else
            annotator = "uima";
        parameterObject = cont.getConfigParameterValue(PARAM_VERSION);
        if (parameterObject != null && parameterObject instanceof String)
            runId = Integer.parseInt((String) parameterObject);
        else
            runId = -2;
        if (dao == null || dao.isClosed()) {
            dao = EDAO.getInstance(new File(configFile));
        }
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", resultTableName, false);
        parseRuleStr(inferenceStr);
    }


    private void parseRuleStr(String ruleStr) {
        IOUtil ioUtil = new IOUtil(ruleStr, true);
        for (ArrayList<String> initRow : ioUtil.getInitiations()) {
            if (initRow.get(1).startsWith("@FEATURE_VALUES")) {
                String featureName = initRow.get(2);
                for (int i = 3; i < initRow.size(); i++) {
                    String value = initRow.get(i);
                    if (valueFeatureMap.containsKey(value)) {
                        logger.info("You have more than one features have the value: " + value +
                                ". You won't be able to use the value alone to define the conditions." +
                                "Instead, you will need to use 'FeatureName:value' format.");
                    } else {
                        valueFeatureMap.put(value, featureName);
                    }
                }
            } else if (initRow.get(1).startsWith("@DefaultBunchConclusion") || initRow.get(1).startsWith("&DefaultBunchConclusion")) {
                String topic = initRow.get(2).trim();
                String defaultDocTypeName = initRow.get(3).trim();
                defaultBunchType.put(topic, defaultDocTypeName);
            }
        }
        for (ArrayList<String> row : ioUtil.getRuleCells()) {
            if (row.size() < 4) {
                System.err.println("Format error in the visit inference rule " + row.get(0) + "." +
                        "\n\t" + row);
            }
            int ruleId = Integer.parseInt(row.get(0));
            String topic = row.get(1).trim();
            if (!inferenceMap.containsKey(topic))
                inferenceMap.put(topic, new ArrayList<>());
            ArrayList<Object> inference = new ArrayList<>();
//            0. ruleId; 1. visit type; 2. evidence type; 3. evidence feature values; 4. count condition; 5. count number
//			add visit conclusion type
            inference.add(ruleId);
            String visitTypeName = row.get(2).trim();
            inference.add(visitTypeName);
            String evidenceDocType = row.get(3).trim();
            inference.add(evidenceDocType);

//
            if (row.size() > 4) {
                String counterCondition = row.get(4).trim();
//          if only a number exist, consider it as a condition that count=that number
                if (counterCondition.length() == 1) {
                    inference.add("=");
                    inference.add(Integer.parseInt(counterCondition));
                } else {
//          if the condition format like ">#" or "<#"
                    inference.add(counterCondition.substring(0, 1));
                    inference.add(Integer.parseInt(counterCondition.substring(1, 2)));
                }
            } else {
//              by default, consider a match if count >0
                inference.add(">");
                inference.add(0);
            }
            inferenceMap.get(topic).add(inference);
        }


    }

    public void process(JCas jCas) {
        CAS cas = jCas.getCas();
        String serializedString;
        RecordRow recordRow = new RecordRow();
        FSIterator it = jCas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
        SourceDocumentInformation e;
        if (it.hasNext()) {
            e = (SourceDocumentInformation) it.next();
            serializedString = e.getUri();
            recordRow.deserialize(serializedString);
        }
        Object value = recordRow.getValueByColumnName(bunchColumnName);
        int currentBunchId = value == null ? 0 : Integer.parseInt(value.toString());
        if (previousBunchId == -1) {
            previousBunchId = currentBunchId;
            previousRecordRow = recordRow;
            clearCounter();
        } else if (previousBunchId != currentBunchId) {
            evaluateVisitCounts(previousRecordRow);
            previousBunchId = currentBunchId;
            previousRecordRow = recordRow;
            clearCounter();
        }


        if (typeMap.size() == 0) {
            initMaps(cas);
        }

//       count annotations
        for (ArrayList<ArrayList<Object>> rules : inferenceMap.values()) {
            for (ArrayList<Object> rule : rules) {
                int ruleId = (Integer) rule.get(0);
                String typeName = (String) rule.get(2);
                Iterator<AnnotationFS> iter = CasUtil.iterator(cas, typeMap.get(typeName));
                while (iter.hasNext()) {
                    AnnotationFS anno = iter.next();
                    if (counter.containsKey(ruleId))
                        counter.put(ruleId, counter.get(ruleId) + 1);
                    else
                        counter.put(ruleId, 1);

                }
            }
        }
    }

    private void evaluateVisitCounts(RecordRow previousRecordRow) {
        for (String topic : inferenceMap.keySet()) {
            ArrayList<ArrayList<Object>> rules = inferenceMap.get(topic);
            boolean matched = false;
            for (ArrayList<Object> rule : rules) {
                int ruleId = (Integer) rule.get(0);
                if (counter.containsKey(ruleId)) {
                    int actualCount = counter.get(ruleId);
                    int expectedCount = (Integer) rule.get(4);
                    String operator = (String) rule.get(3);
                    if (operator.equals("=") && actualCount == expectedCount) {
                        addVisitConclusion(previousRecordRow, rule);
                        matched = true;
                        break;
                    } else if (operator.equals(">") && actualCount > expectedCount) {
                        addVisitConclusion(previousRecordRow, rule);
                        matched = true;
                        break;
                    } else if (operator.equals("<") && actualCount < expectedCount) {
                        addVisitConclusion(previousRecordRow, rule);
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                addVisitConclusion(previousRecordRow, Arrays.asList(new String[]{"", defaultBunchType.get(topic), ""}));
            }
        }

    }

    private void addVisitConclusion(RecordRow previousRecordRow, List<Object> rule) {
        String typeName = (String) rule.get(1);
        if (runId == -2 && previousRecordRow.getValueByColumnName("RUN_ID") != null)
            runId = Integer.parseInt(previousRecordRow.getStrByColumnName("RUN_ID"));
        RecordRow recordRow = new RecordRow()
                .addCell("RUN_ID", runId)
                .addCell("DOC_NAME", previousRecordRow.getValueByColumnName("BUNCH_ID"))
                .addCell("TYPE", typeName)
                .addCell("ANNOTATOR", annotator)
                .addCell("BEGIN", 0)
                .addCell("END", 1)
                .addCell("SNIPPET_BEGIN", 1)
                .addCell("TEXT", "B")
                .addCell("FEATURES", "")
                .addCell("SNIPPET", "B");
//        String visitConclusion = (String) rule.get(1);
        if (runId > -1)
            dao.insertRecord(resultTableName, recordRow);
        else
            AnnotationLogger.records.add(recordRow);
    }

    /**
     * Initiate typeMap and featureMap, so that Type and Feature can be easier and faster called
     *
     * @param cas UIMA CAS object
     */
    private void initMaps(CAS cas) {
        for (ArrayList<ArrayList<Object>> rules : inferenceMap.values()) {
            for (ArrayList<Object> rule : rules) {
//            0. ruleId; 1. visit type; 2. evidence type; 3. evidence feature values; 4. count condition; 5. count number
                String typeName = (String) rule.get(2);
                Type type = CasUtil.getAnnotationType(cas, DeterminantValueSet.checkNameSpace(typeName));
                if (!typeMap.containsKey(typeName))
                    typeMap.put(typeName, type);
            }
        }
    }

    private void clearCounter() {
        for (Integer key : counter.keySet()) {
            counter.put(key, 0);
        }
    }

    private boolean checkFeatureValues(Type type, AnnotationFS anno, ArrayList<String> values) {
        for (String expectedValue : values) {
            String featureName = valueFeatureMap.get(expectedValue);
            Feature fObj = type.getFeatureByBaseName(featureName);
            if (fObj == null)
                return false;
            String actualValue = anno.getFeatureValueAsString(fObj);
            if (!expectedValue.equals(actualValue))
                return false;
        }
        return true;
    }

    public void collectionProcessComplete() {
        if (previousBunchId != -1 && previousRecordRow != null) {
            evaluateVisitCounts(previousRecordRow);
        }
    }

    @Override
    public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
        return new LinkedHashMap<>();
    }
}
