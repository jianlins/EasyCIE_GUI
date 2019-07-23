package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.*;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Bunch_Base;
import edu.utah.bmi.nlp.type.system.Sentence;
import edu.utah.bmi.nlp.type.system.Token;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import edu.utah.bmi.nlp.uima.common.UIMATypeFunctions;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
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
import org.apache.uima.jcas.tcas.Annotation;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.utah.bmi.nlp.core.DeterminantValueSet.checkNameSpace;
import static edu.utah.bmi.nlp.uima.ae.DocInferenceAnnotator.*;

/**
 * Allows the inference based on multiple document types.
 * Rule format:
 * Question name    Bunch Conclusion Type   Evidence Type 1, Evidence Type 2...
 */
public class BunchMixInferenceWriter extends JCasAnnotator_ImplBase implements RuleBasedAEInf {
    public static Logger logger = IOUtil.getLogger(BunchMixInferenceWriter.class);
    public static final String PARAM_SQLFILE = DeterminantValueSet.PARAM_DB_CONFIG_FILE;
    public static final String PARAM_TABLENAME = "ResultTableName";
    public static final String PARAM_AUTO_ID_ENABLED = "AutoIdEnabled";
    public static final String PARAM_RULE_STR = DeterminantValueSet.PARAM_RULE_STR;
    public static final String PARAM_BUNCH_COLUMN_NAME = "BunchColumnName";
    public static final String PARAM_ANNO_POSITION = "AnnotatePosition";

    /**
     * Write to the JCas of the last document in a bunch when switching to the next bunch, instead of directly write to db.
     */
    public static final String PARAM_WRITE_TO_JCAS = "WriteToJCas";

    public static final String PARAM_ANNOTATOR = DeterminantValueSet.PARAM_ANNOTATOR;
    public static final String PARAM_VERSION = DeterminantValueSet.PARAM_VERSION;


    public static String resultTableName, bunchColumnName;
    public static EDAO dao = null;
    //                          topic   rules
    protected LinkedHashMap<String, ArrayList<ArrayList<Object>>> inferenceMap = new LinkedHashMap<>();
    protected HashMap<String, String> defaultBunchType = new HashMap<>();
    protected boolean autoIdEnabled = true, writeToJCas = false;
    //                   type name, Type
    protected HashMap<String, Type> typeMap = new HashMap<>();
    //     type counter
    protected HashMap<String, Integer> typeCounter = new HashMap<>();

    //  a bunch can be used to represent an encounter or a patient that have a bunch of documents bundled with.
    protected int previousBunchId = -1;

    protected RecordRow previousRecordRow = null;
    protected JCas previousJCas = null;

    private String annotator;
    private int runId;


    protected String annotatePosition = FIRSTWORD;
    protected ArrayList<ArrayList<String>> ruleCells = new ArrayList<>();
    protected LinkedHashMap<String, TypeDefinition> typeDefinitions = new LinkedHashMap<>();
    protected HashMap<String, Constructor<? extends Annotation>> bunchTypeConstructorMap = new HashMap<>();
    protected HashMap<String, Class<? extends Annotation>> conclucionClassMap = new HashMap<>();
    protected HashMap<Class, HashMap<String, Method>> conclusionAnnotationSetFeatures = new HashMap<>();
    protected Pattern pattern = Pattern.compile("^\\s*(\\w+)");

    public void initialize(UimaContext cont) {
        Object parameterObject = cont.getConfigParameterValue(PARAM_WRITE_TO_JCAS);
        if (parameterObject != null && parameterObject instanceof Boolean)
            writeToJCas = (Boolean) parameterObject;

        if (!writeToJCas) {
            parameterObject = cont.getConfigParameterValue(PARAM_SQLFILE);
            String configFile = parameterObject != null ? (String) parameterObject : "conf/sqliteconfig.xml";

            parameterObject = cont.getConfigParameterValue(PARAM_VERSION);
            if (parameterObject != null && parameterObject instanceof String && parameterObject.toString().trim().length() > 0)
                runId = Integer.parseInt((String) parameterObject);
            else
                runId = -2;

            parameterObject = cont.getConfigParameterValue(PARAM_AUTO_ID_ENABLED);
            if (parameterObject != null && parameterObject instanceof Boolean && (Boolean) parameterObject != true)
                autoIdEnabled = false;

            parameterObject = cont.getConfigParameterValue(PARAM_TABLENAME);
            resultTableName = parameterObject != null && parameterObject.toString().trim().length() > 0 ? (String) parameterObject : "OUTPUT";

            dao = EDAO.getInstance(new File(configFile));
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", resultTableName, false);
        }


        parameterObject = cont.getConfigParameterValue(PARAM_BUNCH_COLUMN_NAME);
        bunchColumnName = parameterObject != null && parameterObject.toString().trim().length() > 0 ? (String) parameterObject : "BUNCH_ID";

        parameterObject = cont.getConfigParameterValue(PARAM_ANNO_POSITION);
        if (parameterObject != null) {
            annotatePosition = (String) parameterObject;
        }


        String inferenceStr = (String) cont.getConfigParameterValue(PARAM_RULE_STR);
        parameterObject = cont.getConfigParameterValue(PARAM_ANNOTATOR);
        if (parameterObject != null && parameterObject instanceof String)
            annotator = (String) parameterObject;
        else
            annotator = "uima";

        parseRuleStr(inferenceStr);
        this.typeDefinitions = getTypeDefs(inferenceStr);
        AnnotationOper.initSetReflections(typeDefinitions, conclucionClassMap, bunchTypeConstructorMap, conclusionAnnotationSetFeatures);
    }


    protected void parseRuleStr(String ruleStr) {
        IOUtil ioUtil = new IOUtil(ruleStr, true);
        for (ArrayList<String> initRow : ioUtil.getInitiations()) {
            if (initRow.get(1).startsWith("@DefaultBunchConclusion") || initRow.get(1).startsWith("&DefaultBunchConclusion")) {
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
//            0. ruleId; 1. bunch question name (topic); 2. bunch conclusion type; 2. evidence types;
//			add visit conclusion type
            inference.add(ruleId);
            String visitTypeName = row.get(2).trim();
            inference.add(visitTypeName);
            String evidenceDocTypes = row.get(3).trim();
//            save the value space for future counting support
            HashMap<String, Integer> evidencesMap = new HashMap<>();
            for (String evidenceDocType : evidenceDocTypes.split("[,;\\|]")) {
                evidenceDocType = evidenceDocType.trim();
                evidencesMap.put(evidenceDocType, 1);
                typeCounter.put(evidenceDocType, 0);

            }
            inference.add(evidencesMap);
            inferenceMap.get(topic).add(inference);
        }


    }

    public void process(JCas jCas) throws AnalysisEngineProcessException {
        CAS cas = jCas.getCas();
        if (typeMap.size() == 0) {
            initMaps(cas);
        }
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

        for (String typeName : typeCounter.keySet()) {
            Iterator<AnnotationFS> iter = CasUtil.iterator(cas, typeMap.get(typeName));
            while (iter.hasNext()) {
                iter.next();
                if (typeCounter.containsKey(typeName))
                    typeCounter.put(typeName, typeCounter.get(typeName) + 1);
                else
                    typeCounter.put(typeName, 1);

            }
        }
        previousJCas = jCas;

    }

    protected void evaluateVisitCounts(RecordRow previousRecordRow) {
        for (String topic : inferenceMap.keySet()) {
            ArrayList<ArrayList<Object>> rules = inferenceMap.get(topic);
            boolean matched = true;
            for (ArrayList<Object> rule : rules) {
                HashMap<String, Integer> evidencesMap = (HashMap<String, Integer>) rule.get(2);
                matched = true;
                for (String typeName : evidencesMap.keySet()) {
                    if (!typeCounter.containsKey(typeName) || typeCounter.get(typeName) < evidencesMap.get(typeName))
                        matched = false;
                }
                if (matched) {
                    addBunchConclusion(previousRecordRow, rule);
                    break;
                }
            }
            if (!matched) {
                addBunchConclusion(previousRecordRow, Arrays.asList(new String[]{"", defaultBunchType.get(topic), ""}));
            }
        }

    }

    protected void addBunchConclusion(RecordRow previousRecordRow, List<Object> rule) {
        String typeName = (String) rule.get(1);
        if (runId == -2 && previousRecordRow.getValueByColumnName("RUN_ID") != null)
            runId = Integer.parseInt(previousRecordRow.getStrByColumnName("RUN_ID"));
        RecordRow recordRow = new RecordRow()
                .addCell("RUN_ID", runId)
                .addCell("DOC_NAME", previousRecordRow.getValueByColumnName(bunchColumnName))
                .addCell("TYPE", typeName)
                .addCell("ANNOTATOR", annotator)
                .addCell("BEGIN", 0)
                .addCell("END", 1)
                .addCell("SNIPPET_BEGIN", 1)
                .addCell("TEXT", "B")
                .addCell("FEATURES", "")
                .addCell("SNIPPET", "B");
//        String visitConclusion = (String) rule.get(1);
        if (runId > -1) {
            if (!writeToJCas) {
                dao.insertRecord(resultTableName, recordRow);
            } else {
                Span position = getAnnotationPosition(previousJCas);
                Annotation conclusion = AnnotationOper.createAnnotation(previousJCas, new AnnotationDefinition(typeDefinitions.get(typeName)), this.bunchTypeConstructorMap.get(typeName),
                        position.getBegin(), position.getEnd(), this.conclusionAnnotationSetFeatures.get(typeName));
                conclusion.addToIndexes();
            }
        } else
            AnnotationLogger.records.add(recordRow);

    }

    protected Span getAnnotationPosition(JCas jCas) {
        Annotation anno = null;
        Span span = null;
        switch (annotatePosition) {
            case FIRSTWORD:
                anno = JCasUtil.selectByIndex(jCas, Token.class, 0);
                break;
            case FIRSTSENTENCE:
                anno = JCasUtil.selectByIndex(jCas, Sentence.class, 0);
                break;
            case LASTWORD:
                anno = JCasUtil.selectByIndex(jCas, Token.class, -1);
                break;
            case LASTSENTENCE:
                anno = JCasUtil.selectByIndex(jCas, Sentence.class, -1);
                break;
            default:
//                check if the configured value is a type name or a snippet of text
                if (annotatePosition.startsWith("edu.") || annotatePosition.startsWith("org.")
                        || annotatePosition.startsWith("com.") || annotatePosition.startsWith("net.")) {
                    anno = JCasUtil.selectByIndex(jCas, AnnotationOper.getTypeClass(annotatePosition), 0);
                } else {
                    String docText = jCas.getDocumentText();
                    int begin = docText.indexOf(annotatePosition);
                    int end = begin + annotatePosition.length();
                    span = new Span(begin, end);
                }
                break;
        }
        if (anno != null)
            return new Span(anno.getBegin(), anno.getEnd());
        else {
            String txt = jCas.getDocumentText();
            Matcher matched = pattern.matcher(jCas.getDocumentText());
            if (matched.find()) {
                span = new Span(matched.start(1), matched.end(1));
            }
            return span;
        }
    }

    /**
     * Initiate typeMap and featureMap, so that Type and Feature can be easier and faster called
     *
     * @param cas UIMA CAS object
     */
    protected void initMaps(CAS cas) {
        for (String typeName : typeCounter.keySet()) {
            Type type = CasUtil.getAnnotationType(cas, DeterminantValueSet.checkNameSpace(typeName));
            if (!typeMap.containsKey(typeName))
                typeMap.put(typeName, type);
        }
    }

    protected void clearCounter() {
        for (String typeName : typeCounter.keySet()) {
            typeCounter.put(typeName, 0);
        }
    }


    public void collectionProcessComplete() {
        if (previousBunchId != -1 && previousRecordRow != null) {
            evaluateVisitCounts(previousRecordRow);
        }
    }

    @Override
    public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
        IOUtil ioUtil = new IOUtil(ruleStr, true);
        if (ioUtil.getInitiations().size() > 0) {
//          check format of previous version, if the rules are from previous version, skip this function
            for (ArrayList<String> row : ioUtil.getInitiations()) {
                if (row.get(1).endsWith("DefaultBunchConclusion")) {
                    return new LinkedHashMap<>();
                }
            }
            UIMATypeFunctions.getTypeDefinitions(ioUtil, ruleCells,
                    new HashMap<>(), new HashMap<>(), defaultBunchType, typeDefinitions);
        }
        for (ArrayList<String> row : ioUtil.getRuleCells()) {
            logger.finest("Parse rule:" + row);
            String bunchTypeName = checkNameSpace(row.get(2).trim());
            String shortName = DeterminantValueSet.getShortName(bunchTypeName);
            if (!typeDefinitions.containsKey(shortName))
                typeDefinitions.put(shortName, new TypeDefinition(bunchTypeName, Bunch_Base.class.getCanonicalName()));
        }
        return typeDefinitions;
    }
}
