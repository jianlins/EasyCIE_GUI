package edu.utah.bmi.nlp.uima.ae;

import edu.utah.bmi.nlp.core.*;
import edu.utah.bmi.nlp.core.RelationRule.TriggerTypes;
import edu.utah.bmi.nlp.type.system.Relation;
import edu.utah.bmi.nlp.type.system.Sentence;
import edu.utah.bmi.nlp.type.system.Token;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import edu.utah.bmi.nlp.uima.common.UIMATypeFunctions;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.utah.bmi.nlp.uima.common.AnnotationOper.initSetReflections;

/**
 * Identify relations among entities, using similar algorithm as ConText.
 * Compared with ConText, this relation annotator is able to handle one to many, many to many, or many to one pairs.
 * For instance, "A daughter and a son are both in overall good general health"
 *
 * @author Jianlin Shi on 6/24/19.
 */
public class RelationAnnotator extends JCasAnnotator_ImplBase implements RuleBasedAEInf {
    public static final Logger logger = IOUtil.getLogger(RelationAnnotator.class);
    public static final String PARAM_RULE_STR = DeterminantValueSet.PARAM_RULE_STR;
    protected ArrayList<ArrayList<String>> ruleCells = new ArrayList<>();
    protected HashMap<String, String> valueFeatureMap = new HashMap<>();
    protected LinkedHashMap<String, TypeDefinition> typeDefinitions = new LinkedHashMap<>();
    protected HashMap<String, Class<? extends Annotation>> srcAnnoClassMap = new LinkedHashMap<>();
    protected HashMap<String, Class<? extends Annotation>> conclusionAnnoClassMap = new LinkedHashMap<>();

    protected HashMap<Class, HashMap<String, Method>> conclusionConceptSetFeatures = new HashMap<>();
    protected HashMap<String, HashMap<String, Method>> evidenceConceptGetFeatures = new LinkedHashMap<>();

    protected HashMap<Class, HashMap<String, Method>> elementGetFeatures = new HashMap<>();


    protected HashMap<String, Constructor<? extends Annotation>> conclusionConstructorMap = new HashMap<>();

    protected LinkedHashMap<String, IntervalST<Integer>> annoIdx = new LinkedHashMap<>();
    protected LinkedHashMap<String, ArrayList<Annotation>> annos = new LinkedHashMap<>();
    protected HashMap ruleMap = new LinkedHashMap();
    protected HashMap<String, AnnotationDefinition> relationDefs = new HashMap<>();
    public LinkedHashMap<Integer, RelationRule> ruleStore = new LinkedHashMap<>();

    protected static final String ARG1 = "Arg1", ARG2 = "Arg2";

    protected final String END = "<END>";

    @Override
    public void initialize(UimaContext cont) {
        String ruleStr = (String) (cont
                .getConfigParameterValue(PARAM_RULE_STR));
        IOUtil ioUtil = new IOUtil(ruleStr, true);
        parseRules(ioUtil);
    }

    protected void parseRules(IOUtil ioUtil) {
        typeDefinitions = getTypeDefs(ioUtil);
        for (ArrayList<String> init : ioUtil.getInitiations()) {
            if (init.get(1).substring(1).equals("RELATION_DEFINITION")) {
                String relationType = init.get(2);
                relationDefs.put(relationType, new AnnotationDefinition(typeDefinitions.get(relationType)));
                String[] srcTypeNames = init.get(4).split("[,:;]");
                srcAnnoClassMap.put(srcTypeNames[0], AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(srcTypeNames[0])));
                relationDefs.get(relationType).setFeatureValue(ARG1, srcTypeNames[0]);
                srcAnnoClassMap.put(srcTypeNames[1], AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(srcTypeNames[1])));
                relationDefs.get(relationType).setFeatureValue(ARG2, srcTypeNames[1]);
                for (String featureValuePairStr : init.get(3).split("\\s*,\\s*")) {
                    String[] featureValuePair = featureValuePairStr.split("\\s*:\\s*");
                    relationDefs.get(relationType).setFeatureValue(featureValuePair[0], featureValuePair[1]);
                }
            }
        }
        srcAnnoClassMap.put("Token", Token.class);
        srcAnnoClassMap.put("Sentence", Sentence.class);

        //       init methods to set conclusoin feature values.
        initSetReflections(typeDefinitions, conclusionAnnoClassMap, conclusionConstructorMap, conclusionConceptSetFeatures);


        for (ArrayList<String> init : ioUtil.getRuleCells()) {
            int offset = 0;
            if (init.size() < 6)
                offset = -1;
            String scope = init.get(5 + offset);
            String relationType = init.get(4 + offset);
//            System.out.println(init);
            TriggerTypes triggerType = TriggerTypes.valueOf(init.get(3 + offset));
            TriggerTypes direction = TriggerTypes.valueOf(init.get(2 + offset));
            int ruleId = NumberUtils.createInteger(init.get(0));
            ruleStore.put(ruleId, new RelationRule(ruleId, init.get(1), relationType, direction, triggerType));
            AnnotationDefinition relationDef = relationDefs.get(relationType);
            if (!ruleMap.containsKey(relationDef.getFeatureValue(ARG1))) {
                ruleMap.put(relationDef.getFeatureValue(ARG1), new LinkedHashMap<>());
            }
            HashMap tmp = (HashMap) ruleMap.get(relationDef.getFeatureValue(ARG1));
            if (NumberUtils.isParsable(scope)) {
                if (!tmp.containsKey("Token"))
                    tmp.put("Token", new LinkedHashMap<>());
                tmp = (HashMap) tmp.get("Token");
                int num = NumberUtils.createInteger(scope);
                if (!tmp.containsKey(num))
                    tmp.put(num, new HashMap<>());
                tmp = (HashMap) tmp.get(num);
            } else {
                int splitter = scope.indexOf("*");
                if (splitter != -1) {
                    String typeName = scope.substring(0, splitter);
                    int num = NumberUtils.createInteger(scope.substring(splitter + 1));
                    annos.put(typeName, new ArrayList<>());
                    if (!srcAnnoClassMap.containsKey(typeName)) {
                        srcAnnoClassMap.put(typeName, AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(typeName)));
                    }
                    if (!tmp.containsKey(typeName))
                        tmp.put(typeName, new LinkedHashMap<>());
                    tmp = (HashMap) tmp.get(typeName);
                    if (!tmp.containsKey(num))
                        tmp.put(num, new LinkedHashMap<>());
                    tmp = (HashMap) tmp.get(num);
                } else {
                    annos.put(scope, new ArrayList<>());
                    if (!srcAnnoClassMap.containsKey(scope)) {
                        srcAnnoClassMap.put(scope, AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(scope)));
                    }
                    if (!tmp.containsKey(scope))
                        tmp.put(scope, new LinkedHashMap<>());
                    tmp = (HashMap) tmp.get(scope);
                    if (!tmp.containsKey(1))
                        tmp.put(1, new LinkedHashMap<>());
                    tmp = (HashMap) tmp.get(1);
                }
            }
            if (!tmp.containsKey(direction))
                tmp.put(direction, new LinkedHashMap<>());
            tmp = (HashMap) tmp.get(direction);

            if (!tmp.containsKey(relationDef.getFeatureValue(ARG2)))
                tmp.put(relationDef.getFeatureValue(ARG2), new LinkedHashMap<>());
            tmp = (HashMap) tmp.get(relationDef.getFeatureValue(ARG2));
//          if the rule string is empty, match if two elements co-exist.
            if (offset == -1) {
                if (!tmp.containsKey(END))
                    tmp.put(END, new ArrayList<>());
                ((ArrayList<Integer>) tmp.get(END)).add(ruleId);
            } else {
                for (String ele : init.get(1).split("\\s+")) {
                    if (!tmp.containsKey(ele))
                        tmp.put(ele, new HashMap<>());
                    if (ele.startsWith("<") && ele.endsWith(">")) {
                        String annoType = ele.substring(1, ele.length() - 1);
                        getAnnoClass(annoType);
                    }
                    tmp = (HashMap) tmp.get(ele);
                }
                if (!tmp.containsKey(END))
                    tmp.put(END, new ArrayList<>());
                ((ArrayList<Integer>) tmp.get(END)).add(ruleId);
            }
        }

        //      init methods to read source annotation feature values
        initGetRelections(relationDefs, srcAnnoClassMap, evidenceConceptGetFeatures);

    }

    private void initGetRelections(HashMap<String, AnnotationDefinition> relationDefs,
                                   HashMap<String, Class<? extends Annotation>> srcAnnoClassMap,
                                   HashMap<String, HashMap<String, Method>> evidenceConceptGetFeatures) {
        for (String relationTypeName : relationDefs.keySet()) {
            AnnotationDefinition relationTypeDef = relationDefs.get(relationTypeName);
            LinkedHashMap<String, Object> featureValues = relationTypeDef.getFullFeatureValuePairs();
            for (String featureName : featureValues.keySet()) {
                switch (featureName) {
                    case ARG1:
                    case ARG2:
                        break;
                    default:
                        String value = (String) featureValues.get(featureName);
                        if (srcAnnoClassMap.containsKey(value)) {
                            if (!evidenceConceptGetFeatures.containsKey(value)) {
                                evidenceConceptGetFeatures.put(value, new HashMap<>());
                            }
                            evidenceConceptGetFeatures.get(value).put(featureName,
                                    AnnotationOper.getFeatureMethod(srcAnnoClassMap.get(value), featureName));
                        }
                        break;
                }
            }
        }

    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        annoIdx.clear();
        annos.clear();
        indexClues(aJCas, srcAnnoClassMap.keySet(), annoIdx, annos);
        HashMap<String, HashMap<String, RelationRule>> matches = new HashMap<>();
        for (Object eleObj : ruleMap.keySet()) {
            String ele1Type = (String) eleObj;
            ArrayList<Annotation> ele1s = annos.get(ele1Type);
            for (int ele1Id = 0; ele1Id < ele1s.size(); ele1Id++) {
                Annotation ele1 = ele1s.get(ele1Id);
//                println(ele1.getCoveredText());
                HashMap ele1tmp = (HashMap) ruleMap.get(eleObj);

                for (Object scopeObj : ele1tmp.keySet()) {
                    String scopeType = (String) scopeObj;
                    if (!annoIdx.containsKey(scopeType) || annoIdx.get(scopeType).size() == 0)
                        continue;
                    int endScopeId = annoIdx.get(scopeType).get(new Interval1D(ele1.getEnd()-1, ele1.getEnd()));
                    int beginScopeId = annoIdx.get(scopeType).get(new Interval1D(ele1.getBegin(), ele1.getBegin()+1));
                    HashMap scopetmp = (HashMap) ele1tmp.get(scopeObj);
                    for (Object scopeNumObj : scopetmp.keySet()) {
                        int scopeNum = (int) scopeNumObj;
                        HashMap numtmp = (HashMap) scopetmp.get(scopeNum);
                        for (Object directionObj : numtmp.keySet()) {
                            TriggerTypes direction = (TriggerTypes) directionObj;
                            HashMap directiontmp = (HashMap) numtmp.get(direction);
                            for (Object ele2Obj : directiontmp.keySet()) {
                                String ele2Type = (String) ele2Obj;
                                Iterable<Integer> ele2s = new ArrayList<>();
                                Interval1D scopeInv = null;
                                switch (direction) {
                                    case forward:
                                        int scopebegin = ele1.getEnd();
                                        int scopeEndId = endScopeId + scopeNum;
                                        int total = annos.get(scopeType).size();
                                        if (scopeEndId >= total)
                                            scopeEndId = total - 1;
                                        int scopeend = annos.get(scopeType).get(scopeEndId).getEnd();
                                        scopeInv = new Interval1D(scopebegin, scopeend);
                                        if (!annoIdx.containsKey(ele2Type))
                                            continue;
                                        ele2s = annoIdx.get(ele2Type).getAll(scopeInv);
                                        break;
                                    case backward:
                                        scopeend = ele1.getBegin();
                                        int scopeBeginId = beginScopeId - scopeNum;
                                        if (scopeBeginId < 0)
                                            scopeBeginId = 0;
                                        scopebegin = annos.get(scopeType).get(scopeBeginId).getBegin();
                                        if (scopebegin > scopeend) {
                                            logger.warning(AnnotationOper.deserializeDocSrcInfor(aJCas).toString());
                                            logger.warning(ele1.getType().getShortName()+"\t"+ele1.getCoveredText());
                                            logger.warning(annos.get(scopeType).get(scopeBeginId).getCoveredText());
                                            logger.warning(annos.get(scopeType).get(beginScopeId).getCoveredText());
                                            logger.warning(scopebegin + "-" + scopeend);
                                        }
                                        scopeInv = new Interval1D(scopebegin, scopeend);
                                        if (!annoIdx.containsKey(ele2Type))
                                            continue;
                                        ele2s = annoIdx.get(ele2Type).getAll(scopeInv);
                                        break;
                                    case both:
                                        scopeBeginId = beginScopeId - scopeNum;
                                        if (scopeBeginId < 0)
                                            scopeBeginId = 0;
                                        scopebegin = annos.get(scopeType).get(scopeBeginId).getBegin();
                                        scopeEndId = endScopeId + scopeNum;
                                        total = annos.get(scopeType).size();
                                        if (scopeEndId >= total)
                                            scopeEndId = total - 1;
                                        scopeend = annos.get(scopeType).get(scopeEndId).getEnd();
                                        scopeInv = new Interval1D(scopebegin, scopeend);
                                        if (!annoIdx.containsKey(ele2Type))
                                            continue;
                                        ele2s = annoIdx.get(ele2Type).getAll(scopeInv);
                                        break;
                                }
                                for (int ele2Id : ele2s) {
                                    Annotation ele2 = annos.get(ele2Type).get(ele2Id);
                                    if (directiontmp.containsKey(END)) {
                                        ArrayList<Integer> relationRuleIds = (ArrayList<Integer>) directiontmp.get(END);
                                        for (int relationRuleId : relationRuleIds) {
                                            RelationRule currentRule = ruleStore.get(relationRuleId).clone();
                                            if (!matches.containsKey(currentRule.ruleName))
                                                matches.put(currentRule.ruleName, new HashMap<>());
                                            String matchedId = ele1Id + "_" + ele2Id;
                                            if (matches.get(currentRule.ruleName).containsKey(matchedId)) {
                                                RelationRule previousMatch = matches.get(currentRule.ruleName).get(matchedId);
                                                if (currentRule.triggerType == TriggerTypes.trigger) {
                                                    if (currentRule.rule.length() > previousMatch.rule.length()) {
                                                        matches.get(currentRule.ruleName).put(matchedId, currentRule);
                                                    }
                                                }
                                            } else
                                                matches.get(currentRule.ruleName).put(ele1Id + "_" + ele2Id, currentRule);
                                        }
                                    } else if (directiontmp.containsKey(ele2Type)) {
                                        if (ele1.getBegin() < ele2.getBegin()) {
                                            int tokenScopeBegin = annoIdx.get("Token").get(new Interval1D(ele1.getEnd() - 1, ele1.getEnd())) + 1;
                                            int tokenScopeEnd = annoIdx.get("Token").get(new Interval1D(ele2.getBegin(), ele2.getBegin() + 1));
                                            logger.finest(String.format("%d: %s\t---\t%d: %s", tokenScopeBegin, annos.get("Token").get(tokenScopeBegin).getCoveredText(), tokenScopeEnd, annos.get("Token").get(tokenScopeEnd).getCoveredText()));
                                            processRelationRules((HashMap) directiontmp.get(ele2Type), matches, new Interval1D(ele1.getEnd(), ele2.getBegin()), ele1Id, ele2Id, ele1Type, ele2Type, direction, tokenScopeBegin, tokenScopeEnd, true);
                                        } else {
                                            int tokenScopeBegin = annoIdx.get("Token").get(new Interval1D(ele2.getEnd() - 1, ele2.getEnd())) + 1;
                                            int tokenScopeEnd = annoIdx.get("Token").get(new Interval1D(ele1.getBegin(), ele1.getBegin() + 1));
                                            logger.finest(String.format("%d: %s\t---\t%d: %s", tokenScopeBegin, annos.get("Token").get(tokenScopeBegin).getCoveredText(), tokenScopeEnd, annos.get("Token").get(tokenScopeEnd).getCoveredText()));
                                            if (ele2.getEnd() > ele1.getBegin()) {
                                                logger.warning(AnnotationOper.deserializeDocSrcInfor(aJCas).toString());
                                                logger.warning(String.format("%d: %s\t---\t%d: %s", tokenScopeBegin, annos.get("Token").get(tokenScopeBegin).getCoveredText(), tokenScopeEnd, annos.get("Token").get(tokenScopeEnd).getCoveredText()));
                                            }
                                            processRelationRules((HashMap) directiontmp.get(ele2Type), matches, new Interval1D(ele2.getEnd(), ele1.getBegin()), ele1Id, ele2Id, ele1Type, ele2Type, direction, tokenScopeBegin, tokenScopeEnd, true);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (String relationType : matches.keySet()) {
            for (String key : matches.get(relationType).keySet()) {
                RelationRule rule = matches.get(relationType).get(key);
                if (rule.triggerType == TriggerTypes.trigger) {
                    String ele1Type = (String) relationDefs.get(rule.ruleName).getFeatureValue(ARG1);
                    String ele2Type = (String) relationDefs.get(rule.ruleName).getFeatureValue(ARG2);
                    String[] eleIds = key.split("_");
                    Integer ele1Id = NumberUtils.createInteger(eleIds[0]);
                    Integer ele2Id = NumberUtils.createInteger(eleIds[1]);
                    addRelation(aJCas, rule.id, annos.get(ele1Type).get(ele1Id), annos.get(ele2Type).get(ele2Id));
                }
            }
        }
    }

    private void logTokens(int tokenBegin, int tokenEnd) {
        if (logger.isLoggable(Level.FINEST)) {
            ArrayList<String> tokens = new ArrayList<>();
            for (Annotation token : annos.get("Token").subList(tokenBegin, tokenEnd))
                tokens.add(token.getCoveredText());
            logger.finest(String.format("Token (%d-%d): %s", tokenBegin, tokenEnd, tokens.toString().replaceAll(", ", " ")));
        }
    }

    private void processRelationRules(HashMap tmp, HashMap<String, HashMap<String, RelationRule>> matches, Interval1D scopeInv,
                                      int ele1Id, int ele2Id, String ele1Type, String ele2Type, TriggerTypes direction, int tokenScopeBegin, int tokenScopeEnd, boolean previousEleIsType) {
//        println(annos.get(ele1Type).get(ele1Id).getCoveredText() + "\t---\t" + annos.get(ele2Type).get(ele2Id).getCoveredText() + "\t" + direction);
//        println(tokenScopeBegin + "-" + tokenScopeEnd);
        logTokens(tokenScopeBegin, tokenScopeEnd);
        for (Object next : tmp.keySet()) {
            if (next == END) {
//                println(tmp.get(next));
                ArrayList<Integer> relationRuleIds = (ArrayList<Integer>) tmp.get(END);
                for (int relationRuleId : relationRuleIds) {
                    RelationRule currentRule = ruleStore.get(relationRuleId).clone();
                    Annotation ele1 = annos.get(ele1Type).get(ele1Id);
                    if (!annos.containsKey(ele2Type)) {
                        continue;
                    }
                    Annotation ele2 = annos.get(ele2Type).get(ele2Id);
                    if (!matches.containsKey(currentRule.ruleName))
                        matches.put(currentRule.ruleName, new HashMap<>());
                    String matchedId = ele1Id + "_" + ele2Id;
                    if (matches.get(currentRule.ruleName).containsKey(matchedId)) {
                        RelationRule previousMatch = matches.get(currentRule.ruleName).get(matchedId);
                        switch (currentRule.triggerType) {
                            case trigger:
                                if (currentRule.rule.length() > previousMatch.rule.length()) {
                                    switch (currentRule.direction) {
                                        case forward:
                                            if (ele1.getEnd() <= ele2.getBegin()) {
                                                matches.get(currentRule.ruleName).put(matchedId, currentRule);
                                            }
                                            break;
                                        case backward:
                                            if (ele1.getBegin() >= ele2.getEnd()) {
                                                matches.get(currentRule.ruleName).put(matchedId, currentRule);
                                            }
                                            break;
                                        case both:
                                            matches.get(currentRule.ruleName).put(matchedId, currentRule);
                                            break;
                                    }
                                }
                                break;
                            case termination:
                                switch (currentRule.direction) {
                                    case forward:
                                        if (ele1.getEnd() <= ele2.getBegin()) {
                                            matches.get(currentRule.ruleName).put(matchedId, currentRule);
                                        }
                                        break;
                                    case backward:
                                        if (ele1.getBegin() >= ele2.getEnd()) {
                                            matches.get(currentRule.ruleName).put(matchedId, currentRule);
                                        }
                                        break;
                                    case both:
                                        matches.get(currentRule.ruleName).put(matchedId, currentRule);
                                        break;
                                }
                                break;
                        }
                    } else
                        matches.get(currentRule.ruleName).put(ele1Id + "_" + ele2Id, currentRule);
                }
            } else {
                String nextKey = (String) next;
                if (nextKey.startsWith("<") && nextKey.endsWith(">")) {
                    String clueType = nextKey.substring(1, nextKey.length() - 1);
                    Iterable<Integer> clues = annoIdx.get(clueType).getAll(scopeInv);
                    ArrayList<Integer> sortedClueIds = new ArrayList<>();
                    for (int id : clues) {
                        sortedClueIds.add(id);
                    }
                    Collections.sort(sortedClueIds);
                    for (int cludId : sortedClueIds) {
                        Integer tokenClueId = annoIdx.get("Token").get(new Interval1D(annos.get(clueType).get(cludId).getEnd() - 1, annos.get(clueType).get(cludId).getEnd()));
                        int localTokenScopeBegin = tokenClueId + 1;
                        if (localTokenScopeBegin > tokenScopeEnd)
                            continue;
                        if (localTokenScopeBegin >= annos.get("Token").size())
                            localTokenScopeBegin = annos.get("Token").size() - 1;
                        int scopeBegin = annos.get("Token").get(localTokenScopeBegin).getEnd();
                        if (scopeInv.max <= scopeBegin) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.finest(annos.get("Token").get(localTokenScopeBegin).getCoveredText() + "\t" + annos.get("Token").get(annoIdx.get("Token").get(new Interval1D(scopeInv.max - 1, scopeInv.max))).getCoveredText());
                                logger.finest(scopeBegin + "\t" + scopeInv.max + "\t" + cludId);
                            }
                            continue;
                        }
                        processRelationRules((HashMap) tmp.get(next), matches, new Interval1D(scopeBegin, scopeInv.max), ele1Id, ele2Id, ele1Type, ele2Type, direction, localTokenScopeBegin, tokenScopeEnd, true);
                    }
                } else {
                    if (previousEleIsType) {
                        for (int tokenId = tokenScopeBegin; tokenId <= tokenScopeEnd; tokenId++) {
                            logTokens(tokenId, tokenScopeEnd);
                            Annotation token = annos.get("Token").get(tokenId);
                            if (token.getCoveredText().equals(nextKey)) {
//                                TODO differentiate directions
                                if (scopeInv.max <= token.getEnd()) {
                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.finest(token.getCoveredText() + "\t" + annos.get("Token").get(annoIdx.get("Token").get(new Interval1D(scopeInv.max - 1, scopeInv.max))).getCoveredText());
                                        logger.finest(token.getEnd() + "\t" + scopeInv.max + "\t" + tokenId);
                                    }
                                    continue;
                                }
                                processRelationRules((HashMap) tmp.get(next), matches, new Interval1D(token.getEnd(), scopeInv.max), ele1Id, ele2Id, ele1Type, ele2Type, direction, tokenId, tokenScopeEnd, false);
                            }
                        }
                    } else {
                        Annotation token = annos.get("Token").get(tokenScopeBegin);
                        if (token.getCoveredText().equals(nextKey)) {
                            processRelationRules((HashMap) tmp.get(next), matches, new Interval1D(token.getEnd(), scopeInv.max), ele1Id, ele2Id, ele1Type, ele2Type, direction, tokenScopeBegin + 1, tokenScopeEnd, false);
                        }
                    }
                }
            }
        }
    }


    protected void addRelation(JCas jCas, int relationRuleId, Annotation ele1, Annotation ele2) {
        Class<? extends Annotation> relationCls = getAnnoClass(ruleStore.get(relationRuleId).ruleName);
        Constructor<? extends Annotation> constructor = getConstructor(relationCls);
        try {
            int begin = ele1.getBegin() < ele2.getBegin() ? ele1.getBegin() : ele2.getBegin();
            int end = ele2.getEnd() > ele1.getEnd() ? ele2.getEnd() : ele1.getEnd();
            Annotation anno = constructor.newInstance(jCas, begin, end);
            if (anno instanceof Relation) {
                Relation relationAnno = (Relation) anno;
                String relationTypeName = relationAnno.getType().getShortName();
                relationAnno.setNote("Rule(" + relationRuleId + "):" + ruleStore.get(relationRuleId).rule);
                relationAnno.setArg1(ele1);
                relationAnno.setArg2(ele2);
                HashMap<String, Annotation> args = new HashMap<>();
                args.put(ele1.getClass().getSimpleName(), ele1);
                args.put(ele2.getClass().getSimpleName(), ele2);
                LinkedHashMap<String, Object> featureValues = relationDefs.get(relationTypeName).getFullFeatureValuePairs();
                for (String featureName : featureValues.keySet()) {
                    switch (featureName) {
                        case ARG1:
                        case ARG2:
                            break;
                        default:
                            String value = (String) featureValues.get(featureName);
                            if (srcAnnoClassMap.containsKey(value)) {
                                Class<? extends Annotation> srcCls = srcAnnoClassMap.get(value);
                                Method getMethod = this.evidenceConceptGetFeatures.get(value).get(featureName);
                                Object featureValue = getMethod.invoke(args.get(value));
                                AnnotationOper.setFeatureValue(conclusionConceptSetFeatures.get(relationAnno.getClass()).get(featureName), relationAnno, featureValue == null ? null : featureValue.toString());
                            } else {
                                AnnotationOper.setFeatureValue(conclusionConceptSetFeatures.get(relationAnno.getClass()).get(featureName), relationAnno, value);
                            }
                            break;
                    }
                }
                relationAnno.addToIndexes();
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    protected Constructor<? extends Annotation> getConstructor(Class<? extends Annotation> relationCls) {
        String typeName = relationCls.getSimpleName();
        if (conclusionConstructorMap.containsKey(typeName)) {
            return conclusionConstructorMap.get(typeName);
        }
        Constructor<? extends Annotation> constructor = null;
        try {
            constructor = relationCls.getConstructor(JCas.class);
            conclusionConstructorMap.put(typeName, constructor);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return constructor;
    }

    protected void indexClues(JCas jCas, Set<String> types, LinkedHashMap<String, IntervalST<Integer>> annoIdx,
                              LinkedHashMap<String, ArrayList<Annotation>> annos) {

        for (String typeName : types) {

            Class<? extends Annotation> cls = getAnnoClass(typeName);
            if (!annoIdx.containsKey(typeName)) {
                annoIdx.put(typeName, new IntervalST());
                annos.put(typeName, new ArrayList<>());
            }
            for (Annotation anno : JCasUtil.select(jCas, cls)) {
                annoIdx.get(typeName).put(new Interval1D(anno.getBegin(), anno.getEnd()), annos.get(typeName).size());
                annos.get(typeName).add(anno);

            }


        }
    }

    protected Class<? extends Annotation> getAnnoClass(String className) {
        if (srcAnnoClassMap.containsKey(className) && srcAnnoClassMap.get(className) != null) {
            return srcAnnoClassMap.get(className);
        } else {
            Class<? extends Annotation> cls = AnnotationOper.getTypeClass(DeterminantValueSet.checkNameSpace(className));
            srcAnnoClassMap.put(className, cls);
            srcAnnoClassMap.put(cls.getSimpleName(), cls);
            return cls;
        }
    }


    @Override
    public LinkedHashMap<String, TypeDefinition> getTypeDefs(String ruleStr) {
        IOUtil ioUtil = new IOUtil(ruleStr, true);
        return getTypeDefs(ioUtil);
    }

    protected LinkedHashMap<String, TypeDefinition> getTypeDefs(IOUtil ioUtil) {
        if (ioUtil.getInitiations().size() > 1) {
            UIMATypeFunctions.getTypeDefinitions(ioUtil, ruleCells,
                    valueFeatureMap, new HashMap<>(), new HashMap<>(), typeDefinitions);

        }
        return typeDefinitions;
    }
}
