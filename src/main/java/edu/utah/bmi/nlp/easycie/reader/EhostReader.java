package edu.utah.bmi.nlp.easycie.reader;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.uima.common.UIMATypeFunctions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

import static edu.utah.bmi.nlp.uima.common.AnnotationOper.initSetReflections;

/**
 * @author Jianlin Shi on 5/20/16.
 */
public class EhostReader extends AbFileCollectionReader {

    public static Logger logger = IOUtil.getLogger(EhostReader.class);
    public static final String PARAM_READ_TYPES = "ReadTypes";
    protected HashMap<Class, HashMap<String, Method>> typeSetMethods = new HashMap<>();
    protected HashMap<String, Constructor<? extends Annotation>> typeConstructors = new HashMap<>();
    protected HashMap<String, Class<? extends Annotation>> typeClasses = new HashMap<>();
    protected int id = 0;
    protected boolean print = false;
    private String readTypes = "";

    public void initialize() throws ResourceInitializationException {
        File directory = new File(((String) getConfigParameterValue(PARAM_INPUTDIR)).trim());
        // if input directory does not exist or is not a directory, throw exception
        if (!directory.exists() || !directory.isDirectory()) {
            throw new ResourceInitializationException(ResourceConfigurationException.DIRECTORY_NOT_FOUND,
                    new Object[]{PARAM_INPUTDIR, this.getMetaData().getName(), directory.getPath()});
        }
        overWriteAnnotatorName = "ehost";
        Object para = getConfigParameterValue(PARAM_OVERWRITE_ANNOTATOR_NAME);
        if (para != null && para instanceof String && ((String) para).trim().length() > 0) {
            overWriteAnnotatorName = ((String) para).trim();
        }
        para = getConfigParameterValue(PARAM_READ_TYPES);
        if (para != null && para instanceof String && ((String) para).trim().length() > 0) {
            readTypes = ((String) para).trim();
        }

        mRecursive = true;

        para = getConfigParameterValue(PARAM_PRINT);
        if (para != null && para instanceof Boolean) {
            print = (Boolean) para;
        }

        mFiles = new ArrayList<>(FileUtils.listFiles(directory, new String[]{"txt"}, true));

        mCurrentIndex = 0;
        mEncoding = "UTF-8";

        LinkedHashMap<String, TypeDefinition> typeDefinitions = getTypeDefinitionMap(directory.getAbsolutePath());
        initSetReflections(typeDefinitions, typeClasses, typeConstructors, typeSetMethods);
//        String typeDescriptorFile = "desc/type/customized";
//        if (!new File(typeDescriptorFile + ".xml").exists()) {
//            typeDescriptorFile = "desc/type/All_Types";
//        }
    }


    public void getNext(CAS aCAS) throws IOException, CollectionException {
        JCas jcas;
        String xmlContent, text, fileName;
        try {
            jcas = aCAS.getJCas();
        } catch (CASException e) {
            throw new CollectionException(e);
        }

        // open input stream to file
        File file = mFiles.get(mCurrentIndex++);
        logger.fine("Import annotations for file: " + file.getName() + "\t\t");
        fileName = file.getName();
//        text = readTextAsEhost(file);
        // put document in CAS
        text = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        jcas.setDocumentText(text);


        // set language if it was explicitly specified as a configuration parameter
        if (mLanguage != null) {
            jcas.setDocumentLanguage(mLanguage);
        }

        File xmlFile = new File(file.getParentFile().getParentFile(), "saved/" + fileName + ".knowtator.xml");
        xmlContent = FileUtils.readFileToString(xmlFile, mEncoding);
        // Also store location of source document in CAS. This information is critical
        // if CAS Consumers will need to know where the original document contents are located.
        // For example, the Semantic Search CAS Indexer writes this information into the
        // search index that it creates, which allows applications that use the search index to
        // locate the documents that satisfy their semantic queries.
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jcas, 0, text.length());
        srcDocInfo.setUri(genURIStr(file));
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize((int) file.length());
        srcDocInfo.setLastSegment(mCurrentIndex == mFiles.size());
        srcDocInfo.addToIndexes();
        parseXML(jcas, text, xmlContent, fileName);
        logger.fine("Success!");

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

    public void parseXML(JCas jcas, String txtContent, String xmlContent, String docId) {
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
                                typeName = idAttr.getValue();
                                if (typeName.indexOf(" ") != -1)
                                    typeName = typeName.replaceAll(" +", "_");
                                try {
                                    addAnnotation(jcas, typeName, attributes);
                                    attributes.clear();
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                } catch (InvocationTargetException e) {
                                    e.printStackTrace();
                                } catch (InstantiationException e) {
                                    e.printStackTrace();
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

    protected void addAnnotation(JCas jcas, String typeName, LinkedHashMap<String, String> attributes)
            throws IllegalAccessException, InvocationTargetException, InstantiationException {
        typeName = DeterminantValueSet.getShortName(typeName);
        int begin = Integer.parseInt(attributes.get("Begin"));
        int end = Integer.parseInt(attributes.get("End"));
        attributes.remove("Begin");
        attributes.remove("End");
        if (!typeClasses.containsKey(typeName))
            return;
        Annotation annotation = typeConstructors.get(typeName).newInstance(jcas, begin, end);

//      Set feature values
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            String featureName = attribute.getKey();
            String value = attribute.getValue();
            String methodName = "set" + featureName.substring(0, 1).toUpperCase() + featureName.substring(1);
            if (typeClasses.containsKey(typeName)
                    && typeSetMethods.get(typeClasses.get(typeName)).containsKey(methodName)) {
                Method featureMethod = typeSetMethods.get(typeClasses.get(typeName)).get(methodName);
                featureMethod.invoke(annotation, value);
            } else {
                logger.fine(methodName + "  was defined in Java class name, but doesn't exist in Ehost annotation type " + typeName);
            }
        }
        annotation.addToIndexes();
    }


    @Override
    public void close() {

    }

    public static LinkedHashMap<String, TypeDefinition> getTypeDefinitionMap(String projectDir) {
        LinkedHashMap<String, TypeDefinition> typeDefinition = new LinkedHashMap<>();
        File inputDir = new File(projectDir);
        if (!inputDir.exists() || inputDir.isFile()) {
            System.err.println("Project Directory " + projectDir + " does not exist.");
            return null;
        }
        Collection<File> schemaFiles = FileUtils.listFiles(inputDir, new NameFileFilter("projectschema.xml"), TrueFileFilter.INSTANCE);

        for (File schemaFile : schemaFiles) {
            try {
                EhostXMLParser.getTypesFromSchema(typeDefinition, FileUtils.readFileToString(schemaFile, StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return typeDefinition;
    }

    public static Collection<TypeDefinition> getTypeDefinitions(String projectDir) {
        LinkedHashMap<String, TypeDefinition> typeDefinition = getTypeDefinitionMap(projectDir);
        return typeDefinition.values();
    }
}
