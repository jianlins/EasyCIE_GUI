package edu.utah.bmi.simple.gui.core;

import edu.utah.blulab.domainontology.Anchor;
import edu.utah.blulab.domainontology.DomainOntology;
import edu.utah.blulab.domainontology.LexicalItem;
import edu.utah.blulab.domainontology.Modifier;
import edu.utah.bmi.nlp.context.common.ContextValueSet.TriggerTypes;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class OntologyOperator {

    private DomainOntology domain;
    private String uri = "edu.utah.bmi.nlp";
    private HashMap<String, ArrayList<String>> mappingTypes = new HashMap<>();

    public OntologyOperator(String filePath) {
        init(filePath, true);
    }

    public OntologyOperator(String filePath, boolean useLocalFiles) {
        init(filePath, useLocalFiles);
    }

    private void init(String filePath, boolean useLocalFiles) {
        try {
            domain = new DomainOntology(filePath, useLocalFiles);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void exportModifiers(File tokenRuleExcelFile, File characterRuleExcelFile, File contextExcelFile) {
        ArrayList<List<String>> modifiers = readModifiers();
        ArrayList<List<String>> tokenAnchors = new ArrayList<>();
        ArrayList<List<String>> regexAnchors = new ArrayList<>();
        readAnchors(tokenAnchors, regexAnchors);
        writeExcelFile(contextExcelFile, modifiers);
        writeExcelFile(tokenRuleExcelFile, tokenAnchors);
        if (regexAnchors.size() > 0)
            writeExcelFile(characterRuleExcelFile, regexAnchors);

    }

    public void setMappingTypes(String mappingFile) {
        File file = new File(mappingFile);
        try {
            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                if (line.length() > 0 && !line.startsWith("#")) {
                    String[] pair = line.split("\t");
                    if (!mappingTypes.containsKey(pair[0]))
                        mappingTypes.put(pair[0], new ArrayList<>());
                    mappingTypes.get(pair[0]).add(pair[1]);
                }
            }
        } catch (IOException e) {

        }
    }

    private void readAnchors(ArrayList<List<String>> tokenAnchors, ArrayList<List<String>> regexAnchors) {
        try {
            ArrayList<Anchor> anchorDictionary = domain.createAnchorDictionary();
            for (Anchor anchor : anchorDictionary) {
                readAnchor(tokenAnchors, regexAnchors, anchor, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readAnchor(ArrayList<List<String>> tokenAnchors, ArrayList<List<String>> regexAnchors, Anchor anchor, boolean isPseudo) {
        String pseudoType = isPseudo ? "PSEUDO" : "ACTUAL";
        String str = anchor.toString();
        for (String type : anchor.getSemanticType()) {
            type = type.replaceAll("\\s+", "_");
            tokenAnchors.add(Arrays.asList(anchor.getPrefTerm(), type, pseudoType));
        }
        readdSynonyms(tokenAnchors, anchor, anchor.getSynonym(), false);
        readdSynonyms(tokenAnchors, anchor, anchor.getAbbreviation(), false);
        readdSynonyms(tokenAnchors, anchor, anchor.getMisspelling(), false);
        readdSynonyms(regexAnchors, anchor, anchor.getRegex(), false);
        for (Anchor child : anchor.getDirectChildren()) {
            readAnchor(tokenAnchors, regexAnchors, child, isPseudo);
        }
        for (Anchor pseudo : anchor.getPseudos()) {
            readAnchor(tokenAnchors, regexAnchors, pseudo, true);
        }
    }

    private void readdSynonyms(ArrayList<List<String>> anchors, Anchor anchor, ArrayList<String> synonyms, boolean isPseudo) {
        String pseudoType = isPseudo ? "PSEUDO" : "ACTUAL";
        for (String term : synonyms) {
            for (String type : anchor.getSemanticType()) {
                type = type.replaceAll("\\s+", "_");
                anchors.add(Arrays.asList(term, type, pseudoType));
            }
        }
    }

    public ArrayList<List<String>> readModifiers() {
        ArrayList<List<String>> modifiers = new ArrayList<>();
        try {
            ArrayList<Modifier> modifierDictionary = domain.createModifierDictionary();
            for (Modifier modifier : modifierDictionary) {
                String modifierName = modifier.getModName();
                modifier.getItems();
                readdModifierLexicalItems(modifiers, modifierName, TriggerTypes.trigger, modifier.getItems());
                for (Modifier pseudos : modifier.getPseudos()) {
                    readdModifierLexicalItems(modifiers, modifierName, TriggerTypes.pseudo, pseudos.getItems());
                }
                for (Modifier terminations : modifier.getClosures()) {
                    readdModifierLexicalItems(modifiers, modifierName, TriggerTypes.termination, terminations.getItems());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return modifiers;
    }


    private void writeExcelFile(File contextExcelFile, ArrayList<List<String>> content) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet();
        int rowNum = 0;
        for (List<String> rowStr : content) {
            XSSFRow row = sheet.createRow(rowNum++);
            int cellNum = 0;
            for (String cellStr : rowStr) {
                Cell cell = row.createCell(cellNum++);
                cell.setCellValue(cellStr);
            }
        }
        try {
            FileOutputStream out = new FileOutputStream(contextExcelFile);
            workbook.write(out);
            out.close();
            System.out.println(contextExcelFile.getAbsolutePath() + " is written successfully on disk.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readdModifierLexicalItems(ArrayList<List<String>> modifiers, String modifierName,
                                           TriggerTypes triggerType, ArrayList<LexicalItem> lexicalItems) {
        for (LexicalItem mitem : lexicalItems) {
            ArrayList<String> newItem = new ArrayList<>();
            newItem.add(mitem.getPrefTerm());
            String directionStr = mitem.getActionEn(true);
            int windowSize = mitem.getWindowSize();
            if (directionStr == null || directionStr.length() < 2) {
                System.err.println(mitem);
                continue;
            }
            switch (directionStr.charAt(1)) {
                case 'o':
                case 'a':
                    break;
                default:
//                   modifier ontology use "bidirectional"
                    directionStr = "both";
            }
            newItem.add(directionStr);
            newItem.add(triggerType.name());
            newItem.add(modifierName);
            newItem.add(windowSize + "");
            readTerm(modifiers, newItem);
            readTerms(modifiers, newItem, mitem.getSynonym());
            readTerms(modifiers, newItem, mitem.getAbbreviation());
            readTerms(modifiers, newItem, mitem.getSubjExp());
            readTerms(modifiers, newItem, mitem.getMisspelling());
        }
    }

    private void readTerms(ArrayList<List<String>> modifiers, ArrayList<String> newItem, ArrayList<String> terms) {
        for (String term : terms) {
            newItem.set(0, term);
            readTerm(modifiers, newItem);
        }
    }

    private void readTerm(ArrayList<List<String>> modifiers, ArrayList<String> newItem) {
        if (mappingTypes.size() > 0 && mappingTypes.containsKey(newItem.get(3))) {
            for (String type : mappingTypes.get(newItem.get(3))) {
                newItem.set(3, type);
                modifiers.add((ArrayList<String>) newItem.clone());
            }
        } else if (mappingTypes.size() == 0) {
            modifiers.add((ArrayList<String>) newItem.clone());
        }
    }

//    private void addAnchor(String semanticType, ArrayList<String> anchorRow, ArrayList<String> pseudos) {
//        Anchor anchor = new Anchor(uri, domain);
//        try {
//            anchor.setPrefTerm(anchorRow.get(0));
//            anchor.setSemanticType(Arrays.asList(new String[]{semanticType}));
//            anchor.setSynonym(anchorRow.subList(1, anchorRow.size()));
//            if (pseudos != null && pseudos.size() > 0) {
//                LogicExpression logicExpression = new LogicExpression();
//                logicExpression.setType(LogicExpression.SINGLE);
//                logicExpression.addAll(pseudos);
//                anchor.setPseudo(logicExpression);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void addModifiers() {
//        Modifier modifier=new Modifier(uri,domain);
//
//
//    }
//
//    public void save() {
//        try {
//            domain.saveDomainOntology();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

}
