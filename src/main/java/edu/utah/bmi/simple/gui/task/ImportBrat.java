package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.core.Span;
import edu.utah.bmi.nlp.rush.core.RuSH;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ImportBrat {
    protected EDAO dao;
    protected Collection<File> annFiles;
    protected GUITask guiTask;
    protected static final String beginOffset = "<begin>";
    protected static final String endOffset = "<end>";
    protected static final String typeName = "<typeName>";
    protected RuSH rush;
    protected boolean overwrite = false;

    public ImportBrat(String dbConfigFile, GUITask guiTask, boolean overwrite) {
        dao = EDAO.getInstance(new File(dbConfigFile));
        this.guiTask = guiTask;
        this.overwrite = overwrite;
    }

    public ImportBrat(EDAO dao, GUITask guiTask, boolean overwrite) {
        this.dao = dao;
        this.guiTask = guiTask;
        this.overwrite = overwrite;
    }

    public int run(String projectDir, String annotator, Object runId, String tableName, String rushRuleStr, String includeTypes) {
        if (rushRuleStr.length() > 0)
            rush = new RuSH(rushRuleStr);
        else
            rush = null;
        int total = getTotalDocs(projectDir);
        int i = 0;
        this.guiTask.updateGUIMessage("Start importing...");
        HashSet<String> types = new HashSet<>();
        if (includeTypes.trim().length() > 0)
            types.addAll(Arrays.asList(includeTypes.split("\\s*[,;]\\s*")));
        dao.initiateTableFromTemplate("ANNOTATION_TABLE", tableName, overwrite);
        for (File annoFile : annFiles) {
            File txtFile = new File(annoFile.getParentFile(), FilenameUtils.getBaseName(annoFile.getName()) + ".txt");
            try {
                List<String> annoContent = FileUtils.readLines(annoFile, StandardCharsets.UTF_8);
                String txtContent = FileUtils.readFileToString(txtFile, StandardCharsets.UTF_8);
                ArrayList<RecordRow> annotationRecords = parseAnn(txtFile.getName(), annotator, runId, annoContent, txtContent, types);
                saveAnnotationRecords(tableName, annotationRecords);
            } catch (IOException e) {
                e.printStackTrace();
            }
            i++;
            this.guiTask.updateGUIProgress(i, total);
        }
        return i;
    }

    private void saveAnnotationRecords(String tableName, ArrayList<RecordRow> annotationRecords) {
        for (RecordRow record : annotationRecords)
            dao.insertRecord(tableName, record);
    }

    private ArrayList<RecordRow> parseAnn(String fileName, String annotator, Object runId, List<String> annoContent, String txtContent, HashSet<String> types) {
        LinkedHashMap<String, LinkedHashMap<String, String>> annotations = new LinkedHashMap<>();
        for (int i = 0; i < annoContent.size(); i++) {
            String line = annoContent.get(i);
            String[] elements = line.split("\\t");
            switch (line.charAt(0)) {
                case 'T':
                    annotations.put(elements[0], new LinkedHashMap<>());
                    String[] properties = elements[1].split("\\s+");
                    if (types.size() > 0 && !types.contains(properties[0]))
                        continue;
                    annotations.get(elements[0]).put(typeName, properties[0]);
                    annotations.get(elements[0]).put(beginOffset, properties[1]);
                    if (properties[2].indexOf(";") > 0)
                        annotations.get(elements[0]).put(endOffset, properties[3]);
                    else
                        annotations.get(elements[0]).put(endOffset, properties[2]);
                    break;
                case 'A':
                    String[] linkage = elements[1].split("\\s+");
                    if (!annotations.containsKey(linkage[1])) {
//                      if the concept is not added, the put this attribute to the end.
                        if (i < annoContent.size() - 1)
                            annoContent.add(line);
                    } else {
                        annotations.get(linkage[1]).put(linkage[0], linkage[2]);
                    }
                    break;
            }
        }
        IntervalST sentenceTree = new IntervalST();
        if (rush != null) {
            ArrayList<Span> sentences = rush.segToSentenceSpans(txtContent);
            for (Span span : sentences) {
                sentenceTree.put(new Interval1D(span.getBegin(), span.getEnd()), span);
            }
        }
        ArrayList<RecordRow> annotationRecords = new ArrayList<>();
        for (String id : annotations.keySet()) {
            LinkedHashMap<String, String> annotation = annotations.get(id);
            RecordRow recordRow = new RecordRow();
            String type = annotation.get(typeName);
            annotation.remove(typeName);
            int begin = Integer.parseInt(annotation.get(beginOffset));
            int end = Integer.parseInt(annotation.get(endOffset));
            String coveredText = txtContent.substring(begin, end);
            annotation.remove(beginOffset);
            annotation.remove(endOffset);
            Span sentence = null;
            String snippet = null;
            if (rush != null) {
                sentence = (Span) sentenceTree.get(new Interval1D(begin, end));
                snippet = txtContent.substring(sentence.getBegin(), sentence.getEnd());
                begin = begin - sentence.getBegin();
                end = end - sentence.getBegin();
            }
            recordRow.addCell("DOC_NAME", fileName);
            recordRow.addCell("ANNOTATOR", annotator);
            recordRow.addCell("RUN_ID", runId);
//            DOC_NAME,ANNOTATOR, RUN_ID,TYPE,BEGIN,END,SNIPPET_BEGIN, TEXT,
//                FEATURES,SNIPPET,COMMENTS
            recordRow.addCell("TYPE", type);
            recordRow.addCell("BEGIN", begin);
            recordRow.addCell("END", end);
            if (rush != null)
                recordRow.addCell("SNIPPET_BEGIN", sentence.getBegin());
            recordRow.addCell("TEXT", coveredText);
            recordRow.addCell("FEATURES", serializeFeatureValues(annotation));
            if (rush != null)
                recordRow.addCell("SNIPPET", snippet);
            recordRow.addCell("COMMENTS", "");
            annotationRecords.add(recordRow);
        }
        return annotationRecords;
    }

    private String serializeFeatureValues(LinkedHashMap<String, String> annotation) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> attribute : annotation.entrySet()) {
            String featureName = attribute.getKey();
            String value = attribute.getValue();
            sb.append(featureName + ": " + value.replaceAll("[\\n|\\r]", " "));
            sb.append("\n");
        }
        return sb.toString();
    }

    public int getTotalDocs(String projectDir) {
        annFiles = FileUtils.listFiles(new File(projectDir), new String[]{"ann"}, true);
        return annFiles.size();
    }
}
