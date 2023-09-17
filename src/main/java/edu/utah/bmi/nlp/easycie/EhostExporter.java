/*
 * Copyright  2017  Department of Biomedical Informatics, University of Utah
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.utah.bmi.nlp.easycie;

import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.nlp.uima.writer.EhostConfigurator;
import edu.utah.bmi.simple.gui.task.ViewOutputDB;
import org.apache.commons.io.FileUtils;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This XMIWriter is derived from the simple CAS consumer provided by apache
 * UIMA. It read the MetaData annotation, which maintains the original source
 * file name, and output the xmi starting with the original source file name.
 * run it through CPE by using desc/casconsumer/XmiWritterCasConsumerPoet.xml
 *
 * @author Jianlin Shi
 * <p> *
 * A simple CAS consumer that writes the CAS to XMI format.
 * </p>
 * <p>
 * This CAS Consumer takes one parameter:
 * </p>
 * <ul>
 * <li><code>OutputDirectory</code> - path to directory into which output files
 * will be written</li>
 * </ul>
 */
public class EhostExporter {
    /**
     * Name of configuration parameter that must be set to the path of a
     * directory into which the output files will be written.
     */
    protected LinkedHashMap<String, String> docs = new LinkedHashMap<>();
    protected LinkedHashMap<String, ArrayList<RecordRow>> annos = new LinkedHashMap<>();
    HashMap<String, LinkedHashSet<String>> typeConfigs = new HashMap<>();

    protected File xmlOutputDir, txtOutputDir, configDir;

    protected String annotator = "uima";

    protected int mDocNum, docCounter = 0, subCorpusCounter = 0;


    protected int elementId = 0;


    protected SimpleDateFormat dateFormat = new SimpleDateFormat(
//            "EEE MMM dd HH:mm:ss zzz yyyy");
            "MM/dd/yy");
    protected File outputDirectory;
    protected EDAO ddao, adao;
    protected String colorPool;
    protected int randomColor;

    public EhostExporter(String outputDirectory, String annotator, String datasetId,
                         String docDBConfig, String annoDBconfig,
                         String docTableName, String snippetResTableName, String docResTableName,
                         String colorPool, int randomColor) {
        initialize(new File(outputDirectory), annotator, datasetId, docDBConfig, annoDBconfig, docTableName,
                snippetResTableName, docResTableName, colorPool, randomColor);

    }

    public EhostExporter() {
    }

    public void initialize(File outputDirectory, String annotator, String datasetId,
                           String docDBConfig, String annoDBconfig,
                           String docTableName, String snippetResTableName, String docResTableName,
                           String colorPool, int randomColor) {
        ddao = EDAO.getInstance(new File(docDBConfig));
        adao = EDAO.getInstance(new File(annoDBconfig));
        this.colorPool = colorPool;
        this.randomColor = randomColor;
        if (this.colorPool.trim().length() == 0)
            this.randomColor = 2;
        readDocs(docTableName, datasetId);
        readAnnos(snippetResTableName, annotator);
        readAnnos(docResTableName, annotator);

        mDocNum = 0;
        System.out.println("Ehost annotations will be exported to: " + outputDirectory);

        outputDirectory = new File(outputDirectory, annotator);
        if (!outputDirectory.exists()) {
            try {
                FileUtils.forceMkdir(outputDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        xmlOutputDir = new File(outputDirectory, "saved");
        txtOutputDir = new File(outputDirectory, "corpus");

        try {
            if (!xmlOutputDir.exists())
                Files.createDirectories(Paths.get(xmlOutputDir.getAbsolutePath()));
            if (!txtOutputDir.exists())
                Files.createDirectories(Paths.get(txtOutputDir.getAbsolutePath()));
            configDir = new File(outputDirectory, "config");
            if (!configDir.exists())
                Files.createDirectories(Paths.get(configDir.getAbsolutePath()));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    protected void readDocs(String docTableName, String datasetId) {
        RecordRowIterator recordIterator = ddao.queryRecordsFromPstmt("masterInputQuery", docTableName, datasetId);
        while (recordIterator.hasNext()) {
            RecordRow recordRow = recordIterator.next();
            String docName = recordRow.getStrByColumnName("DOC_NAME");
            String content = recordRow.getStrByColumnName("TEXT");
            content = content.replaceAll("(\\n[^\\w\\p{Punct}]+\\n)", "\n\n")
                    .replaceAll("(\\n\\s*)+(?:\\n)", "\n\n")
                    .replaceAll("^(\\n\\s*)+(?:\\n)", "")
                    .replaceAll("[^\\w\\p{Punct}\\s]", " ");
            docs.put(docName, content);
        }
    }

    protected void readAnnos(String resultTableName, String annotator) {
        String sourceQuery = adao.queries.get("queryAnnos");
        sourceQuery = sourceQuery.replaceAll("\\{tableName}", resultTableName);
        String runId = ViewOutputDB.getLastRunIdofAnnotator(adao, resultTableName, annotator);
        RecordRowIterator recordIterator = adao.queryRecords(sourceQuery + " WHERE RUN_ID=" + runId);
        while (recordIterator.hasNext()) {
            RecordRow recordRow = recordIterator.next();
            String docName = recordRow.getStrByColumnName("DOC_NAME");
            if (!annos.containsKey(docName)) {
                annos.put(docName, new ArrayList<>());
            }
            annos.get(docName).add(recordRow);
        }
    }

    public void export() {
        for (String docName : docs.keySet()) {
            String content = docs.get(docName);
            ArrayList<RecordRow> anno = annos.get(docName);
            File[] files = initialOutputXml(docName);
            File outputXml = files[1];
            File sourceFile = files[0];
            try {
                writeEhostXML(content, anno, sourceFile, outputXml);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
        }
        setUpSchema();

    }

    protected void writeEhostXML(String content, ArrayList<RecordRow> annotations,
                                 File sourceFile, File outputXml) throws IOException, XMLStreamException {
        try {
            FileUtils.write(sourceFile, content.replace((char) 13, ' '), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        FileOutputStream outputXmlStream = new FileOutputStream(outputXml);
        XMLStreamWriter xtw = initiateWritter(outputXmlStream, sourceFile);
        elementId = 0;
        if (annotations != null)
            for (RecordRow annotation : annotations) {
                writeEhostAnnotation(xtw, annotation);
            }
        //		finish writing
        xtw.writeEndElement();
        xtw.writeEndDocument();
        xtw.flush();
        outputXmlStream.close();
        xtw.close();
    }


    protected void writeEhostAnnotation(XMLStreamWriter xtw, RecordRow annotation) throws XMLStreamException {
        int snippetBegin = Integer.parseInt(annotation.getStrByColumnName("SNIPPET_BEGIN"));
        int begin = Integer.parseInt(annotation.getStrByColumnName("BEGIN")) + snippetBegin;
        int end = Integer.parseInt(annotation.getStrByColumnName("END")) + snippetBegin;
        String type = annotation.getStrByColumnName("TYPE");
        if (!typeConfigs.containsKey(type)) {
            typeConfigs.put(type, new LinkedHashSet<>());
        }
        String coveredText = annotation.getStrByColumnName("TEXT");

        xtw.writeStartElement("annotation");

        xtw.writeStartElement("mention");
        xtw.writeAttribute("id", "EHOST_Instance_" + elementId);

        xtw.writeEndElement();

        xtw.writeStartElement("annotator");
        xtw.writeAttribute("id", annotator);
        xtw.writeCharacters(annotator);
        xtw.writeEndElement();

        xtw.writeStartElement("span");
        xtw.writeAttribute("start", begin + "");
        xtw.writeAttribute("end", end + "");
        xtw.writeEndElement();

        xtw.writeStartElement("spannedText");
        xtw.writeCharacters(coveredText);
        xtw.writeEndElement();

        xtw.writeStartElement("creationDate");
        xtw.writeCharacters(dateFormat.format(new Date()));
        xtw.writeEndElement();

        xtw.writeEndElement();
        int attributeIds = 0;
//        System.out.println(annotation.getType().getName() + "\t" + annotation.getCoveredText());
        for (String featureValue : annotation.getStrByColumnName("FEATURES").split("\n")) {
            int colon = featureValue.indexOf(":");
            String feature, value;
            if (colon == -1) {
                feature = featureValue;
                value = "";
            } else {
                feature = featureValue.substring(0, colon);
                value = featureValue.substring(colon + 1);
            }
            typeConfigs.get(type).add(feature);
            xtw.writeStartElement("stringSlotMention");
            xtw.writeAttribute("id", "EHOST_Instance_" + (elementId + attributeIds));
            attributeIds++;
            xtw.writeStartElement("mentionSlot");
            xtw.writeAttribute("id", feature);
            xtw.writeEndElement();
            xtw.writeStartElement("stringSlotMentionValue");
//            System.out.println("\t"+value);
            xtw.writeAttribute("value", value);
            xtw.writeEndElement();
            xtw.writeEndElement();
        }


        xtw.writeStartElement("classMention");
        xtw.writeAttribute("id", "EHOST_Instance_" + elementId);

        for (int i = 0; i < attributeIds; i++) {
            xtw.writeStartElement("hasSlotMention");
            xtw.writeAttribute("id", "EHOST_Instance_" + (elementId + i));
            xtw.writeEndElement();
        }
        xtw.writeStartElement("mentionClass");
        xtw.writeAttribute("id", type);
        xtw.writeCharacters(coveredText);
        xtw.writeEndElement();
        elementId += attributeIds + 1;

        xtw.writeEndElement();
    }

    protected String getMethodValue(Method method, Annotation annotation) {
        String value = "";
        try {
            Object valueObj = method.invoke(annotation, null);
            if (valueObj instanceof FSArray) {
            } else {
                value = valueObj + "";
            }

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return value;
    }


    protected XMLStreamWriter initiateWritter(FileOutputStream outputFileStream, File sourceFile)
            throws XMLStreamException {
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        XMLStreamWriter xtw = xof.createXMLStreamWriter(outputFileStream, "UTF-8");
//		System.out.println(outputPath			+ sourcefileName + ".knowtator.xml");
        xtw.writeStartDocument("UTF-8", "1.0");
        xtw.writeStartElement("annotations");
        xtw.writeAttribute("textSource", sourceFile.getAbsolutePath());
        return xtw;
    }

    protected File[] initialOutputXml(String originalFileName) {
        File outFile, sourceFile;
        if (originalFileName.length() == 0) {
            originalFileName = "doc" + mDocNum;
        }
        if (!originalFileName.endsWith("txt"))
            originalFileName += ".txt";

        outFile = new File(xmlOutputDir, originalFileName + ".knowtator.xml");
        sourceFile = new File(txtOutputDir, originalFileName);


        return new File[]{sourceFile, outFile};
    }

    public void setUpSchema() {
        // no default behavior
        EhostConfigurator.setUp(new File(configDir, "projectschema.xml"), typeConfigs, colorPool, randomColor);

    }


}
