package edu.utah.bmi.nlp.easycie.reader;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.utah.bmi.nlp.uima.common.AnnotationOper.initSetReflections;

/**
 * @author Jianlin Shi on 5/20/16.
 */
public class BratReader extends AbFileCollectionReader {

    public static Logger logger = IOUtil.getLogger(BratReader.class);

    public static final String PARAM_READ_TYPES = "ReadTypes";
    protected HashMap<Class, HashMap<String, Method>> typeSetMethods = new HashMap<>();
    protected HashMap<String, Constructor<? extends Annotation>> typeConstructors = new HashMap<>();
    protected HashMap<String, Class<? extends Annotation>> typeClasses = new HashMap<>();
    protected int id = 0;
    protected static final String beginOffset = "<begin>";
    protected static final String endOffset = "<end>";
    protected static final String typeName = "<typeName>";
    private String readTypes = "";
    private LinkedHashMap<String, String> unconfiguredAnnotationTypes = new LinkedHashMap<>();
    private String currentFileName = "";


    public void initialize() throws ResourceInitializationException {
        File directory = new File(((String) getConfigParameterValue(PARAM_INPUTDIR)).trim());
        // if input directory does not exist or is not a directory, throw exception
        if (!directory.exists() || !directory.isDirectory()) {
            throw new ResourceInitializationException(ResourceConfigurationException.DIRECTORY_NOT_FOUND,
                    new Object[]{PARAM_INPUTDIR, this.getMetaData().getName(), directory.getPath()});
        }

        overWriteAnnotatorName = "brat";
        Object para = getConfigParameterValue(PARAM_OVERWRITE_ANNOTATOR_NAME);
        if (para != null && para instanceof String && ((String) para).trim().length() > 0) {
            overWriteAnnotatorName = ((String) para).trim();
        }
        para = getConfigParameterValue(PARAM_READ_TYPES);
        if (para != null && para instanceof String && ((String) para).trim().length() > 0) {
            readTypes = ((String) para).trim();
        }

        mRecursive = true;


        mFiles = new ArrayList<>(FileUtils.listFiles(directory, new String[]{"txt"}, true));
        mCurrentIndex = 0;
        mEncoding = "UTF-8";

        LinkedHashMap<String, TypeDefinition> typeDefinitions = getTypeDefinitionMap(directory.getAbsolutePath());
        initSetReflections(typeDefinitions, typeClasses, typeConstructors, typeSetMethods);
    }


    public void getNext(CAS aCAS) throws IOException, CollectionException {
        JCas jcas;
        List annoContent;
        String text;
        String fileName;
        try {
            jcas = aCAS.getJCas();
        } catch (CASException e) {
            throw new CollectionException(e);
        }

        // open input stream to file
        File file = mFiles.get(mCurrentIndex++);
        currentFileName = file.getName();
        currentFileName = currentFileName.substring(0, currentFileName.length() - 4);
        text = FileUtils.readFileToString(file, mEncoding);
        // put document in CAS
        jcas.setDocumentText(text);


        // set language if it was explicitly specified as a configuration parameter
        if (mLanguage != null) {
            jcas.setDocumentLanguage(mLanguage);
        }

        File annotationFile = new File(file.getParentFile(), currentFileName + ".ann");
        annoContent = FileUtils.readLines(annotationFile, mEncoding);
        // Also store location of source document in CAS. This information is critical
        // if CAS Consumers will need to know where the original document contents are located.
        // For example, the Semantic Search CAS Indexer writes this information into the
        // search index that it creates, which allows applications that use the search index to
        // locate the documents that satisfy their semantic queries.
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jcas);
        srcDocInfo.setUri(genURIStr(file));
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize((int) file.length());
        srcDocInfo.setLastSegment(mCurrentIndex == mFiles.size());
        srcDocInfo.addToIndexes();
        parseAnn(jcas, text, annoContent);
        if (mCurrentIndex >= mFiles.size()) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("The following types are not configured in Brat annotation.conf files");
                for (String type : unconfiguredAnnotationTypes.keySet()) {
                    logger.info(type + "\t" + unconfiguredAnnotationTypes.get(type));
                }
            }
        }

    }

    public void parseAnn(JCas jcas, String txtContent, List<String> annoContent) {
//                     concept id,      attribute names, values
        LinkedHashMap<String, LinkedHashMap<String, String>> annotations = new LinkedHashMap<>();
        for (int i = 0; i < annoContent.size(); i++) {
            String line = annoContent.get(i);
            String[] elements = line.split("\\t");
            switch (line.charAt(0)) {
                case 'T':
                    annotations.put(elements[0], new LinkedHashMap<>());
                    String[] properties = elements[1].split("\\s+");
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

        for (LinkedHashMap<String, String> annotation : annotations.values()) {
            String type = annotation.get(typeName);
            annotation.remove(typeName);
            try {
                addAnnotation(jcas, type, annotation);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }


    }

    protected void addAnnotation(JCas jcas, String typeName, LinkedHashMap<String, String> attributes)
            throws IllegalAccessException, InvocationTargetException, InstantiationException {
        typeName = DeterminantValueSet.checkNameSpace(typeName);
        int begin = Integer.parseInt(attributes.get(beginOffset));
        int end = Integer.parseInt(attributes.get(endOffset));
        attributes.remove(beginOffset);
        attributes.remove(endOffset);
        if (!typeClasses.containsKey(typeName)) {
            unconfiguredAnnotationTypes.put(typeName, currentFileName);
            return;
        }

        Annotation annotation = typeConstructors.get(typeName).newInstance(jcas);
        annotation.setBegin(begin);
        annotation.setEnd(end);

//      Set feature values
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            String featureName = attribute.getKey();
            String value = attribute.getValue();
            String methodName = "set" + featureName.substring(0, 1).toUpperCase() + featureName.substring(1);
            if (typeClasses.containsKey(typeName) && typeSetMethods.get(typeName).containsKey(methodName)) {
                Method featureMethod = typeSetMethods.get(typeClasses.get(typeName)).get(methodName);
                featureMethod.invoke(annotation, value);
            } else {
                logger.info(methodName + "doesn't exist in " + typeName);
            }
        }
        annotation.addToIndexes();
    }


    @Override
    public void close() {

    }

    public static LinkedHashMap<String, TypeDefinition> getTypeDefinitionMap(String projectDir) {
        File inputDir = new File(projectDir);
        if (!inputDir.exists() || inputDir.isFile()) {
            logger.warning("Project Directory " + projectDir + " does not exist.");
            return null;
        }
        LinkedHashMap<String, TypeDefinition> typeDefinitions = new LinkedHashMap<>();
        for (Map.Entry<String, HashSet<String>> entry : readBratTypes(inputDir).entrySet()) {
            if (entry.getKey().toLowerCase().endsWith("_doc")) {
                typeDefinitions.put(entry.getKey(), new TypeDefinition(entry.getKey(), Doc_Base.class.getCanonicalName(), entry.getValue()));
            } else
                typeDefinitions.put(entry.getKey(), new TypeDefinition(entry.getKey(), Concept.class.getCanonicalName(), entry.getValue()));
        }
        return typeDefinitions;
    }

    public static Collection<TypeDefinition> getTypeDefinitions(String projectDir) {
        LinkedHashMap<String, TypeDefinition> typeDefinitions = getTypeDefinitionMap(projectDir);
        return typeDefinitions.values();
    }


    protected static LinkedHashMap<String, HashSet<String>> readBratTypes(File inputDirectory) {
        Collection<File> schemaFiles = FileUtils.listFiles(inputDirectory, new NameFileFilter("annotation.conf"), TrueFileFilter.INSTANCE);
        LinkedHashMap<String, HashSet<String>> types = new LinkedHashMap<>();
        boolean typeReady = false, attributeReady = false;
        for (File schemaFile : schemaFiles) {
            try {
                List<String> lines = FileUtils.readLines(schemaFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.trim().length() == 0 || line.charAt(0) == '#')
                        continue;
//                    entities  or spans
                    if (line.startsWith("[en") || line.startsWith("[s")) {
                        typeReady = true;
                        continue;
                    } else if (line.startsWith("[a")) {
                        typeReady = false;
                        attributeReady = true;
                        continue;
                    } else if (line.charAt(0) == '[') {
                        attributeReady = false;
                        typeReady = false;
                        continue;
                    }
                    if (typeReady) {
                        types.put(line.trim(), new HashSet<>());
                    } else if (attributeReady) {
                        String[] elements = line.split("\\s+");
                        String attributeName = elements[0];
                        elements[1] = elements[1].trim();
                        String typesString = elements[1].substring(4);
                        if (typesString.endsWith(",")) {
                            typesString = typesString.substring(0, typesString.length() - 1);
                        }
                        for (String type : typesString.split("\\|")) {
                            if (!types.containsKey(type)) {
                                types.put(type, new HashSet<>());
                            }
                            types.get(type).add(attributeName);
                        }

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return types;
    }
}

