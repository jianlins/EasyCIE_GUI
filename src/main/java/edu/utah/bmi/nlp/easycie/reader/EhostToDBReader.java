package edu.utah.bmi.nlp.easycie.reader;

import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.Interval1D;
import edu.utah.bmi.nlp.core.IntervalST;
import edu.utah.bmi.nlp.core.Span;
import edu.utah.bmi.nlp.rush.core.RuSH;
import edu.utah.bmi.nlp.sql.RecordRow;
import org.apache.commons.io.FileUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jianlin Shi on 5/20/16.
 */
public class EhostToDBReader {
    public static Logger logger = IOUtil.getLogger(EhostToDBReader.class);
    protected HashMap<String, String> convertTypes;
    protected LinkedHashMap<String, Integer> metaPos = new LinkedHashMap<>();
    private Pattern pattern = null;
    protected int id = 0;
    protected boolean print = false;
    private String readTypes = "";
    protected Charset charset;
    protected String overWriteAnnotatorName = "";
    protected RuSH rush;
    protected int runId=0;

    public EhostToDBReader(String metaRegexString, LinkedHashMap<String, Integer> metaPos, String converTypeString,
                           String typeFilterString, String CharacterSet, String overWriteAnnotatorName,
                           String rushRuleStr, int runId) {
        pattern = Pattern.compile(metaRegexString);
        this.runId=runId;
        convertTypes = getConvertTypes(converTypeString);
        this.metaPos = metaPos;
        if (!typeFilterString.isEmpty()) {
            HashSet<String> keepTypes = new HashSet<>();
            HashSet<String> toRemoveTypes = new HashSet<>();
            keepTypes.addAll(Arrays.asList(typeFilterString.split("[,;\\|]")));
            for (String cType : convertTypes.keySet()) {
                if (!keepTypes.contains(cType)) {
                    toRemoveTypes.add(cType);
                }
            }
            for (String dType : toRemoveTypes)
                convertTypes.remove(dType);
            for (String directReadType : keepTypes) {
                if (!convertTypes.containsKey(directReadType)) {
                    convertTypes.put(directReadType, directReadType);
                }
            }
        }
        if (CharacterSet == null || CharacterSet.length() == 0)
            CharacterSet = "UTF-8";
        charset = Charset.forName(CharacterSet);
        this.overWriteAnnotatorName = overWriteAnnotatorName;
        if (!rushRuleStr.isEmpty()) {
            rush = new RuSH(rushRuleStr);
        }
    }

    public ArrayList<RecordRow> parseSingleDoc(File txtFile) {
        String subDir = "saved";
        if (new File(txtFile.getParentFile().getParentFile(), "adjudication").exists()) {
            subDir = "adjudication";
        }
        File annoDir = new File(txtFile.getParentFile().getParentFile(), subDir);
        File knowtatorFile = new File(annoDir, txtFile.getName() + ".knowtator.xml");
        ArrayList<RecordRow> annotations = new ArrayList<>();
        try {
            String xmlContent = FileUtils.readFileToString(knowtatorFile, charset);
            String txtContent = readTextAsEhost(txtFile);
            RecordRow baseRecordRow = getMetaRecordRow(knowtatorFile);
            parseXML(baseRecordRow, txtContent, xmlContent, annotations);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return annotations;
    }

    public ArrayList<RecordRow> parseSingleDoc(String txtFilePath) {
        return parseSingleDoc(new File(txtFilePath));
    }

    protected RecordRow getMetaRecordRow(File knowtatorFile) {
        String fileName = knowtatorFile.getName();
        RecordRow recordRow = new RecordRow();
        if (pattern != null && metaPos.size() > -1) {
            Matcher matches = pattern.matcher(fileName);
            if (matches.find()) {
                for (String metaName : metaPos.keySet()) {
                    int pos = metaPos.get(metaName);
                    String value = matches.group(pos);
                    String metaNameLower = metaName.toLowerCase().substring(metaName.length() - 4);
                    if (metaNameLower.equals("date") || metaNameLower.endsWith("dtm")) {
                        if (!value.endsWith("00:00:00"))
                            value = value + " 00:00:00";
                    }
                    recordRow.addCell(metaName, value);
                }
            }
        }
        recordRow.addCell("ANNOTATOR", overWriteAnnotatorName);
        recordRow.addCell("RUN_ID", runId);
        return recordRow;
    }

    public static HashMap<String, String> getConvertTypes(String configStr) {
        HashMap<String, String> convertTypes = new HashMap<>();
        IOUtil ioUtil = new IOUtil(configStr, false);
        for (ArrayList<String> row : ioUtil.getRuleCells()) {
            convertTypes.put(row.get(0).trim(), row.get(1).trim());
        }
        return convertTypes;
    }


    public String readTextAsEhost(File file) {
        StringBuilder content = new StringBuilder();
        ArrayList<String> contents = new ArrayList();
        try {

            BufferedReader f = new BufferedReader(new FileReader(file));
            String line = f.readLine();

            while (line != null) {

                // #### following code is designed to remove special
                // #### characters form strings
                // #### WARNING #### These codes will delete a blank line
                //                   from your document to display
//                if (line == null) {
//                    line = f.readLine();
//                    continue;
//                }
//
//                //int specialcharacternumbers = 0;
//                char[] linetoCharArray = line.toCharArray();
//                if ((linetoCharArray == null) || (linetoCharArray.length < 1)) {
//                    line = f.readLine();
//                    continue;
//                }
//
//                int size = linetoCharArray.length;
//
//                for (int i = 0; i < size; i++) {
//                    if (Integer.valueOf(linetoCharArray[i]) > 127) {
//                        System.out.println("~~~~ INFO ~~~~::found special character=["
//                                + Integer.valueOf(linetoCharArray[i])
//                                + "] in file ["
//                                + file.getName()
//                                + "]");
//                        // linetoCharArray[i]= '\u0000';
//                        linetoCharArray[i] = '\u0020';
//                        //specialcharacternumbers++;
//                    }
//                }
//
//
//                String filteredLine = "";
//                for (int j = 0; j < size; j++) {
//                    if (linetoCharArray[j] != '\u0000')
//                        filteredLine = filteredLine + linetoCharArray[j];
//                }


                contents.add(line);
                //contents.add( line );
                line = f.readLine();
            }
            f.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        for (String line : contents) {
            content.append(line + " ");
        }
        return content.toString();
    }

    public void parseXML(RecordRow baseRecordRow, String txtContent, String xmlContent, ArrayList<RecordRow> annotations) {
        ArrayList<Span> sents = rush.segToSentenceSpans(txtContent);
        IntervalST<Integer> sentTree = new IntervalST<>();
        for (int i = 0; i < sents.size(); i++) {
            Span sent = sents.get(i);
            sentTree.put(sent.begin, sent.end, i);
        }


        InputStream inputStream = new ByteArrayInputStream(xmlContent.getBytes());
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        try {
            XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(inputStream);
            String featureName = "", typeName = "";
            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.nextEvent();
                if (xmlEvent.isStartElement()) {
                    StartElement startElement = xmlEvent.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
//                        case "annotation":
//                            features.clear();
//                            break;
                        case "annotator":
                            xmlEvent = xmlEventReader.nextEvent();
                            if (overWriteAnnotatorName.length() > 0)
                                attributes.put("Annotator", overWriteAnnotatorName);
                            else if (xmlEvent.isCharacters())
                                attributes.put("Annotator", xmlEvent.asCharacters().getData());
                            break;
                        case "span":
                            Attribute beginAttr = startElement.getAttributeByName(new QName("start"));
                            if (beginAttr != null) {
                                attributes.put("Begin", beginAttr.getValue());
                            }
                            Attribute endAttr = startElement.getAttributeByName(new QName("end"));
                            if (endAttr != null) {
                                attributes.put("End", endAttr.getValue());
                            }
                            break;
                        case "spannedText":
                            xmlEvent = xmlEventReader.nextEvent();
                            if (xmlEvent.isCharacters())
                                attributes.put("Text", xmlEvent.asCharacters().getData());
                            break;
                        case "mentionSlot":

                            Attribute idAttr = startElement.getAttributeByName(new QName("id"));
                            if (idAttr != null) {
                                featureName = idAttr.getValue();
                            }
                            break;
                        case "stringSlotMentionValue":
                            Attribute valueAttr = startElement.getAttributeByName(new QName("value"));
                            if (valueAttr != null) {
                                attributes.put(featureName, valueAttr.getValue());
                            }
                            break;
                        case "mentionClass":
                            idAttr = startElement.getAttributeByName(new QName("id"));
                            if (idAttr != null) {
                                typeName = idAttr.getValue().trim();
                                if (typeName.indexOf(" ") != -1)
                                    typeName = typeName.replaceAll(" +", "_");
                                if (convertTypes.containsKey(typeName)) {
                                    typeName = convertTypes.get(typeName);
                                    annotations.add(addAnnotation(baseRecordRow, typeName, attributes, txtContent, sentTree, sents));
                                    attributes.clear();
                                }
                            }
                            break;
                    }

                }
            }
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    protected RecordRow addAnnotation(RecordRow baseRecordRow, String typeName, LinkedHashMap<String, String> attributes, String txtContent,
                                      IntervalST<Integer> sentTree, ArrayList<Span> sents) {
        int begin = Integer.parseInt(attributes.get("Begin"));
        int end = Integer.parseInt(attributes.get("End"));
        attributes.remove("Begin");
        attributes.remove("End");
        RecordRow tmp = baseRecordRow.clone();
        tmp.addCell("TYPE", typeName);
        if (overWriteAnnotatorName.length() > 0)
            tmp.addCell("ANNOTATOR", overWriteAnnotatorName);

        LinkedList<Integer> spans = sentTree.getAllAsList(new Interval1D(begin, end));
        Collections.sort(spans);
        int snippetBegin = begin - 50;
        snippetBegin = snippetBegin > 0 ? snippetBegin : 0;
        int snippetEnd = end + 50;
        snippetEnd = snippetEnd < txtContent.length() ? snippetEnd : txtContent.length();
        if (spans.size() > 0) {
            snippetBegin = sents.get(spans.getFirst()).begin;
            snippetEnd = sents.get(spans.getLast()).end;
        }
        String snippetText = txtContent.substring(snippetBegin, snippetEnd);
        begin = begin - snippetBegin;
        end = end - snippetBegin;
        tmp.addCell("BEGIN", begin);
        tmp.addCell("END", end);
        tmp.addCell("SNIPPET_BEGIN", snippetBegin);
        tmp.addCell("SNIPPET", snippetText);


//      Set feature values
        ArrayList<String> features = new ArrayList<>();
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            String featureName = attribute.getKey();
            if (featureName.equals("ANNOTATOR") || featureName.equals("TEXT"))
                continue;
            String value = attribute.getValue();
            value = value.replaceAll("\\n", " ");
            features.add(featureName + ": " + value);
        }
        tmp.addCell("FEATURES", String.join("\n",features));
        return tmp;
    }


}
