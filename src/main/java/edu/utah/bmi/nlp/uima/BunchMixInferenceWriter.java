//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.ae.BunchMixInferencer;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
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
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class BunchMixInferenceWriter extends JCasAnnotator_ImplBase implements RuleBasedAEInf {
    public static Logger logger = IOUtil.getLogger(BunchMixInferenceWriter.class);
    public static final String PARAM_SQLFILE = "DBConfigFile";
    public static final String PARAM_TABLENAME = "ResultTableName";
    public static final String PARAM_AUTO_ID_ENABLED = "AutoIdEnabled";
    public static final String PARAM_RULE_STR = "RuleFileOrStr";
    public static final String PARAM_BUNCH_COLUMN_NAME = "BunchColumnName";
    public static final String PARAM_ANNOTATOR = "Annotator";
    public static final String PARAM_VERSION = "Version";
    public static final String PARAM_OVERWRITETABLE = "OverWriteTable";
    public static String resultTableName;
    public static String bunchColumnName;
    public static EDAO dao = null;
    protected LinkedHashMap<String, ArrayList<ArrayList<Object>>> inferenceMap = new LinkedHashMap();
    protected HashMap<String, String> defaultBunchType = new HashMap();
    protected boolean autoIdEnabled;
    protected HashMap<String, Type> typeMap = new HashMap();
    protected HashMap<String, Integer> typeCounter = new HashMap();
    protected HashMap<Integer, ArrayList<String>> ruleStore = new HashMap();
    protected int previousBunchId = -1;
    protected RecordRow previousRecordRow = null;
    private String annotator;
    private int runId;
    protected boolean overwriteTable = false;

    public BunchMixInferenceWriter() {
    }

    public void initialize(UimaContext cont) {
        Object parameterObject = cont.getConfigParameterValue("DBConfigFile");
        String configFile = parameterObject != null ? (String)parameterObject : "conf/sqliteconfig.xml";
        parameterObject = cont.getConfigParameterValue("ResultTableName");
        resultTableName = parameterObject != null && parameterObject.toString().trim().length() > 0 ? (String)parameterObject : "OUTPUT";
        parameterObject = cont.getConfigParameterValue("BunchColumnName");
        bunchColumnName = parameterObject != null && parameterObject.toString().trim().length() > 0 ? (String)parameterObject : "BUNCH_ID";
        parameterObject = cont.getConfigParameterValue("AutoIdEnabled");
        if (parameterObject != null && parameterObject instanceof Boolean && !(Boolean)parameterObject) {
            this.autoIdEnabled = false;
        }

        String inferenceStr = (String)cont.getConfigParameterValue("RuleFileOrStr");
        parameterObject = cont.getConfigParameterValue("Annotator");
        if (parameterObject != null && parameterObject instanceof String) {
            this.annotator = (String)parameterObject;
        } else {
            this.annotator = "uima";
        }

        parameterObject = cont.getConfigParameterValue("Version");
        if (parameterObject != null && parameterObject instanceof String && parameterObject.toString().trim().length() > 0) {
            this.runId = Integer.parseInt((String)parameterObject);
        } else {
            this.runId = -2;
        }

        Object value = cont.getConfigParameterValue("OverWriteTable");
        if (value instanceof Boolean) {
            this.overwriteTable = (Boolean)value;
        } else {
            this.overwriteTable = value.toString().toLowerCase().startsWith("t");
        }

        dao = EDAO.getInstance(new File(configFile));
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", resultTableName, this.overwriteTable);
        BunchMixInferencer.parseRuleStr(inferenceStr, this.defaultBunchType, this.inferenceMap, this.typeCounter, this.ruleStore);
    }

    public void process(JCas jCas) throws AnalysisEngineProcessException {
        CAS cas = jCas.getCas();
        if (this.typeMap.size() == 0) {
            this.initMaps(cas);
        }

        RecordRow recordRow = new RecordRow();
        FSIterator it = jCas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
        if (it.hasNext()) {
            SourceDocumentInformation e = (SourceDocumentInformation)it.next();
            String serializedString = e.getUri();
            recordRow.deserialize(serializedString);
        }

        Object value = recordRow.getValueByColumnName(bunchColumnName);
        int currentBunchId = value == null ? 0 : Integer.parseInt(value.toString());
        if (this.previousBunchId == -1) {
            this.previousBunchId = currentBunchId;
            this.previousRecordRow = recordRow;
            this.clearCounter();
        } else if (this.previousBunchId != currentBunchId) {
            this.evaluateVisitCounts(this.previousRecordRow);
            this.previousBunchId = currentBunchId;
            this.previousRecordRow = recordRow;
            this.clearCounter();
        }

        if (this.typeMap.size() == 0) {
            this.initMaps(cas);
        }

        Iterator var9 = this.typeCounter.keySet().iterator();

        while(var9.hasNext()) {
            String typeName = (String)var9.next();
            Iterator<AnnotationFS> iter = CasUtil.iterator(cas, (Type)this.typeMap.get(typeName));

            while(iter.hasNext()) {
                iter.next();
                if (this.typeCounter.containsKey(typeName)) {
                    this.typeCounter.put(typeName, (Integer)this.typeCounter.get(typeName) + 1);
                } else {
                    this.typeCounter.put(typeName, 1);
                }
            }
        }

    }

    protected void evaluateVisitCounts(RecordRow previousRecordRow) {
        Iterator var2 = this.inferenceMap.keySet().iterator();

        while(var2.hasNext()) {
            String topic = (String)var2.next();
            ArrayList<ArrayList<Object>> rules = (ArrayList)this.inferenceMap.get(topic);
            boolean matched = true;
            Iterator var6 = rules.iterator();

            label40:
            while(var6.hasNext()) {
                ArrayList<Object> rule = (ArrayList)var6.next();
                HashMap<String, Integer> evidencesMap = (HashMap)rule.get(2);
                matched = true;
                Iterator var9 = evidencesMap.keySet().iterator();

                while(true) {
                    String typeName;
                    do {
                        if (!var9.hasNext()) {
                            if (matched) {
                                this.addBunchConclusion(previousRecordRow, rule);
                                break label40;
                            }
                            continue label40;
                        }

                        typeName = (String)var9.next();
                    } while(this.typeCounter.containsKey(typeName) && (Integer)this.typeCounter.get(typeName) >= (Integer)evidencesMap.get(typeName));

                    matched = false;
                }
            }

            if (!matched) {
                this.addBunchConclusion(previousRecordRow, Arrays.asList("", (String)this.defaultBunchType.get(topic), ""));
            }
        }

    }

    protected void addBunchConclusion(RecordRow previousRecordRow, List<Object> rule) {
        String typeName = (String)rule.get(1);
        if (this.runId == -2 && previousRecordRow.getValueByColumnName("RUN_ID") != null) {
            this.runId = Integer.parseInt(previousRecordRow.getStrByColumnName("RUN_ID"));
        }

        RecordRow recordRow = (new RecordRow()).addCell("RUN_ID", this.runId).addCell(bunchColumnName, previousRecordRow.getValueByColumnName(bunchColumnName)).addCell("TYPE", typeName).addCell("ANNOTATOR", this.annotator).addCell("BEGIN", 0).addCell("END", 1).addCell("SNIPPET_BEGIN", 1).addCell("TEXT", "B").addCell("FEATURES", "").addCell("SNIPPET", "B");
        if (this.runId > -1) {
            dao.insertRecord(resultTableName, recordRow);
        } else {
            AnnotationLogger.records.add(recordRow);
        }

    }

    protected void initMaps(CAS cas) {
        Iterator var2 = this.typeCounter.keySet().iterator();

        while(var2.hasNext()) {
            String typeName = (String)var2.next();
            Type type = CasUtil.getAnnotationType(cas, DeterminantValueSet.checkNameSpace(typeName));
            if (!this.typeMap.containsKey(typeName)) {
                this.typeMap.put(typeName, type);
            }
        }

    }

    protected void clearCounter() {
        Iterator var1 = this.typeCounter.keySet().iterator();

        while(var1.hasNext()) {
            String typeName = (String)var1.next();
            this.typeCounter.put(typeName, 0);
        }

    }

    public void collectionProcessComplete() {
        if (this.previousBunchId != -1 && this.previousRecordRow != null) {
            this.evaluateVisitCounts(this.previousRecordRow);
        }

    }

    public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
        return new LinkedHashMap();
    }
}
