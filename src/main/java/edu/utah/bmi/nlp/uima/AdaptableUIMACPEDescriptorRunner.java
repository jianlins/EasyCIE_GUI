
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

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.easycie.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CasConsumerDescription;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.CpeCasProcessor;
import org.apache.uima.collection.metadata.CpeCollectionReader;
import org.apache.uima.collection.metadata.CpeDescription;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.*;
import org.apache.uima.resource.metadata.ConfigurationParameter;
import org.apache.uima.resource.metadata.ConfigurationParameterDeclarations;
import org.apache.uima.resource.metadata.ResourceMetaData;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.UriUtils;
import org.apache.uima.util.XMLInputSource;

import javax.xml.stream.*;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

import static edu.utah.bmi.nlp.core.DeterminantValueSet.defaultSuperTypeName;

/**
 * @author Jianlin Shi
 * Created on 7/9/16.
 */
public class AdaptableUIMACPEDescriptorRunner {
    public static Logger classLogger = IOUtil.getLogger(AdaptableUIMACPEDescriptorRunner.class);
    protected CollectionReaderDescription reader;
    protected CpeDescription currentCpeDesc;
    protected File rootFolder;
    protected LinkedHashMap<String, TypeDefinition> conceptTypeDefinitions = new LinkedHashMap<>();
    protected DynamicTypeGenerator dynamicTypeGenerator;
    protected String srcClassRootPath = null, compiledClassPath = null;
    protected String genDescriptorPath;
    protected UIMALogger logger;
    protected final ResourceManager defaultResourceManager = UIMAFramework.newDefaultResourceManager();
    protected CollectionProcessingEngine mCPE = null;
    //  store all the external configuration parameters outside cpe descripter
    private LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap;
    private LinkedHashMap<String, LinkedHashMap<String, String>> aeConfigTypeMap;
    //  store only rule configurations of each rule-based analyses engine
    protected LinkedHashMap<String, Class<? extends JCasAnnotator_ImplBase>> ruleBaseAeClassMap;
    protected ArrayList<Integer> versionedCpProcessorId = new ArrayList<>();

    protected File customTypeDescXmlDir = new File("target/generated-test-sources/uima-descripters");
    private File customTypeDescXmlLoc = null;
    private String cpeDescripterFileName = "";
    private boolean refreshed = false;

    protected AdaptableUIMACPEDescriptorRunner() {

    }


    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor) {
        parseExternalConfigMap(new LinkedHashMap<>());
        initCpe(cpeDescriptor);
    }


    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath) {
        parseExternalConfigMap(new LinkedHashMap<>());
        initCpe(cpeDescriptor, compileRootPath);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath, String srcClassRootPath) {
        parseExternalConfigMap(new LinkedHashMap<>());
        initCpe(cpeDescriptor, compileRootPath, srcClassRootPath);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, LinkedHashMap<String, String> externalRuleConfigMap, String... options) {
        parseExternalConfigMap(externalRuleConfigMap);
        initCpe(cpeDescriptor, options);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath, LinkedHashMap<String, String> externalConfigMap,
                                            String... options) {
        parseExternalConfigMap(externalConfigMap);
        initCpe(cpeDescriptor, options);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath, String srcClassRootPath,
                                            LinkedHashMap<String, String> externalRuleConfigMap, String... optionals) {
        parseExternalConfigMap(externalRuleConfigMap);
        if (optionals != null && optionals.length > 0)
            genDescriptorPath = optionals[1];
        initCpe(cpeDescriptor, compileRootPath, srcClassRootPath);
    }

    public CollectionProcessingEngine getmCPE() {
        return mCPE;
    }

    public void parseExternalConfigMap(LinkedHashMap<String, String> externalSettingMap) {
        externalConfigMap = new LinkedHashMap<>();
        aeConfigTypeMap = new LinkedHashMap<>();
        versionedCpProcessorId.clear();
        for (Map.Entry<String, String> entry : externalSettingMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String[] keys = key.split("/");
            if (keys.length == 1 && !key.equals(value)) {
//              for back compatibility, if only configuration name (cpName) are used
            } else if (keys.length > 1) {
//              support new format, configurations are listed as cpName/configName->value
                String cpName = keys[0];
                String configName = keys[1];
                if (!externalConfigMap.containsKey(cpName)) {
                    externalConfigMap.put(cpName, new LinkedHashMap<>());
                }
                externalConfigMap.get(cpName).put(configName, value);
            }
        }
    }

    public void setExternalConfigMap(LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap) {
        this.externalConfigMap = externalConfigMap;
    }

    /**
     * @param cpeDescriptor location of cpe descripter xml file
     * @param options       0~3 parameters:
     *                      1. The location of compiled classes for auto-gen type systems
     *                      2. The location of auto-gen type descriptor
     *                      3. The location of class source files for auto-gen type systems
     */
    public void initCpe(String cpeDescriptor, String... options) {
        if (options == null || options.length == 0) {
            initCpeDescriptor(cpeDescriptor);
        } else {
            switch (options.length) {
                case 3:
                    this.srcClassRootPath = options[2];
                case 2:
                    this.genDescriptorPath = options[1];
                case 1:
                    this.compiledClassPath = options[0];
                    initCpeDescriptor(cpeDescriptor);
                    break;
                default:
                    this.compiledClassPath = options[0];
                    this.genDescriptorPath = options[1];
                    this.srcClassRootPath = options[2];
                    initCpeDescriptor(cpeDescriptor);
                    break;
            }
        }
    }

    public void initCpeDescriptor(String cpeDescriptor, LinkedHashMap<String, String> externalSettingMap) {
        parseExternalConfigMap(externalSettingMap);
        initCpeDescriptor(cpeDescriptor);
    }

    public void initCpeDescriptor(String cpeDescriptor) {
        refreshed = true;
        ArrayList<TypeSystemDescription> typeSystems = new ArrayList<>();
        ruleBaseAeClassMap = new LinkedHashMap<>();
        cpeDescripterFileName = FilenameUtils.getBaseName(cpeDescriptor);
        customTypeDescXmlDir = new File(customTypeDescXmlDir, cpeDescripterFileName + "_" + Math.abs(new Random().nextInt()));
        try {
            currentCpeDesc = UIMAFramework.getXMLParser().parseCpeDescription(new XMLInputSource(cpeDescriptor));
            removeFailedDescriptors(new File(cpeDescriptor));
            rootFolder = new File(currentCpeDesc.getSourceUrl().getFile()).getParentFile();
            CpeCollectionReader[] collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();
            for (CpeCollectionReader collReader : collRdrs) {
                File descFile = new File(rootFolder + System.getProperty("file.separator") + collReader.getDescriptor().getImport().getLocation());
                CollectionReaderDescription crd = UIMAFramework.getXMLParser().parseCollectionReaderDescription(new XMLInputSource(descFile));
                TypeSystemDescription typeSystem = crd.getCollectionReaderMetaData().getTypeSystem();
                typeSystem.resolveImports();
                typeSystems.add(typeSystem);
//              use external configuration to configure reader
                configureReader(collReader, crd, externalConfigMap);
            }
            CpeCasProcessor[] cpeCasProcessors = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
            ArrayList<CpeCasProcessor> filteredCpeCasProcessors = readAEConfigurations(cpeCasProcessors, ruleBaseAeClassMap,
                    aeConfigTypeMap, typeSystems, externalConfigMap);
//          reset cpe processors
            currentCpeDesc.getCpeCasProcessors().removeAllCpeCasProcessors();
            classLogger.fine("After filter under configured AEs, the following processors are included: ");
            for (CpeCasProcessor cp : filteredCpeCasProcessors) {
                classLogger.fine(cp.getName());
                currentCpeDesc.getCpeCasProcessors().addCpeCasProcessor(cp);
            }
        } catch (InvalidXMLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
        dynamicTypeGenerator = new DynamicTypeGenerator(typeSystems);
        if (compiledClassPath != null)
            dynamicTypeGenerator.setCompiledRootPath(compiledClassPath);
//      if customized type system needs to be generated
        if (conceptTypeDefinitions.size() > 0) {
            reInitTypeSystem(genDescriptorPath);
//       attach the new type descriptor to new cpe descriptor
            try {
                CpeCollectionReader reader = currentCpeDesc.getAllCollectionCollectionReaders()[0];
                String readerxml = reader.getDescriptor().getImport().getLocation();
                readerxml = new File(new File(cpeDescriptor).getParentFile(), readerxml).getAbsolutePath();
                String newReaderXml = updateTypeDescripter(readerxml, customTypeDescXmlDir, customTypeDescXmlLoc);
                reader.getDescriptor().getImport().setLocation(new File(newReaderXml).toURI().toString());
            } catch (CpeDescriptorException e) {
                e.printStackTrace();
            }
        }

    }

    private void configureReader(CpeCollectionReader collReader, CollectionReaderDescription crd,
                                 LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap) {
        String readerName = crd.getMetaData().getName();
        if (readerName == null || readerName.equals("")) {
            readerName = crd.getImplementationName();
            readerName = readerName.substring(readerName.lastIndexOf(".") + 1);
        }
        if (!externalConfigMap.containsKey(readerName))
            return;
//      if external configuration is available
        LinkedHashMap<String, String> paraTypes = new LinkedHashMap<>();
        ConfigurationParameterDeclarations readerParas = crd.getMetaData().getConfigurationParameterDeclarations();
        for (ConfigurationParameter para : readerParas.getConfigurationParameters()) {
            String name = para.getName();
            String type = para.getType();
            paraTypes.put(name, type);
        }
        for (Map.Entry<String, String> externalConfigs : externalConfigMap.get(readerName).entrySet()) {
            String configName = externalConfigs.getKey();
            String valueStr = externalConfigs.getValue();
            String valueType = paraTypes.get(configName);
            switch (valueType) {
                case "Integer":
                    try {
                        int value = Integer.parseInt(valueStr);
                        collReader.getConfigurationParameterSettings().setParameterValue(configName, value);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "Float":
                    try {
                        float value = Float.parseFloat(valueStr);
                        collReader.getConfigurationParameterSettings().setParameterValue(configName, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "Boolean":
                    try {
                        boolean value = Boolean.parseBoolean(valueStr);
                        collReader.getConfigurationParameterSettings().setParameterValue(configName, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "Double":
                    try {
                        double value = Double.parseDouble(valueStr);
                        collReader.getConfigurationParameterSettings().setParameterValue(configName, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "Long":
                    try {
                        long value = Long.parseLong(valueStr);
                        collReader.getConfigurationParameterSettings().setParameterValue(configName, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "Short":
                    try {
                        short value = Short.parseShort(valueStr);
                        collReader.getConfigurationParameterSettings().setParameterValue(configName, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "String":
                    collReader.getConfigurationParameterSettings().setParameterValue(configName, valueStr);
                    break;

            }
        }
        try {
            collReader.setConfigurationParameterSettings(collReader.getConfigurationParameterSettings());
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    private String updateTypeDescripter(String readerxml, File customTypeDescXmlDir, File customTypeDescXmlLoc) {
        File inputFile = new File(readerxml);
        System.out.println(inputFile.exists());
        File outputXmlFile = new File(customTypeDescXmlDir, inputFile.getName());
        XMLInputFactory inFactory = XMLInputFactory.newInstance();
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        try {
            XMLEventReader eventReader = inFactory.createXMLEventReader(new FileInputStream(inputFile));
            XMLEventWriter writer = factory.createXMLEventWriter(new FileWriter(outputXmlFile));
            while (eventReader.hasNext()) {
                XMLEvent e = eventReader.nextEvent();
                writer.add(e);
                if (e.isStartElement() && ((StartElement) e).getName().getLocalPart().equalsIgnoreCase("imports")) {
                    writer.add(eventFactory.createStartElement("", null, "import"));
                    writer.add(eventFactory.createAttribute("location", customTypeDescXmlLoc.getAbsolutePath()));
                    writer.add(eventFactory.createEndElement("", null, "import"));
                }
            }
            eventReader.close();
            writer.close();
        } catch (XMLStreamException | IOException e) {
            e.printStackTrace();
        }
        return outputXmlFile.getAbsolutePath();
    }

    private ArrayList<CpeCasProcessor> readAEConfigurations(
            CpeCasProcessor[] cpeCasProcessors,
            LinkedHashMap<String, Class<? extends JCasAnnotator_ImplBase>> ruleBaseAeClassMap,
            LinkedHashMap<String, LinkedHashMap<String, String>> aeConfigTypeMap, ArrayList<TypeSystemDescription> typeSystems,
            LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap)
            throws IOException, InvalidXMLException {
        ArrayList<CpeCasProcessor> filteredCpeCasProcessors = new ArrayList<>();
        for (CpeCasProcessor cp : cpeCasProcessors) {
            String processorName = cp.getName();
            File descFile = new File(rootFolder + File.separator + cp.getCpeComponentDescriptor().getImport().getLocation());
            AnalysisEngineDescription aed = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(new XMLInputSource(descFile));
            ConfigurationParameterDeclarations aeparas = aed.getMetaData().getConfigurationParameterDeclarations();
            aeConfigTypeMap.put(processorName, new LinkedHashMap<>());
            for (ConfigurationParameter aepara : aeparas.getConfigurationParameters()) {
                String name = aepara.getName();
                String type = aepara.getType();
                aeConfigTypeMap.get(processorName).put(name, type);
            }
            String className = aed.getImplementationName();
//          initiate class Map to read customized types from rule configurations

            Class<? extends JCasAnnotator_ImplBase> aeClass = getJCasAnnotatorClassByName(className);
            ruleBaseAeClassMap.put(processorName, aeClass);
            filterAE(processorName, cp, filteredCpeCasProcessors, aeClass, aeConfigTypeMap.get(processorName), aed, typeSystems, externalConfigMap);
        }
        return filteredCpeCasProcessors;
    }

    private void filterAE(String processorName, CpeCasProcessor cp,
                          ArrayList<CpeCasProcessor> filteredCpeCasProcessors,
                          Class<? extends JCasAnnotator_ImplBase> aeClass, LinkedHashMap<String, String> aeparas,
                          AnalysisEngineDescription aed, ArrayList<TypeSystemDescription> typeSystems,
                          LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap) {
        boolean ruleBasedAE = false;
        String externalRuleConfig = null;
        if (RuleBasedAEInf.class.isAssignableFrom(aeClass)) {
            ruleBasedAE = true;
//          if directly use cpe descriptor without external configuration
            if (externalConfigMap.size() == 0) {
                externalRuleConfig = (String) cp.getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR);
                if (externalRuleConfig == null)
                    externalRuleConfig = (String) aed.getMetaData().getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR);
            } else if (externalConfigMap.containsKey(processorName)) {
                externalRuleConfig = externalConfigMap.get(processorName).get(DeterminantValueSet.PARAM_RULE_STR);
            }
            if (externalRuleConfig == null || externalRuleConfig.equals("")) {
//          if external configured, but rule-based AE's rule is not configured, this AE will be skipped.
                classLogger.fine("The rule for the Rule-based AE: '" + cp.getName() + "' has not be configured, skip this AE.");
                return;
            }
        }
        /* configure cp process with external configuration, if applicable */
        if (externalConfigMap.containsKey(processorName)) {
            for (Map.Entry<String, String> externalConfigs : externalConfigMap.get(processorName).entrySet()) {
                String configName = externalConfigs.getKey();
                String valueStr = externalConfigs.getValue();
                String valueType = aeparas.get(configName);
                switch (valueType) {
                    case "Integer":
                        try {
                            int value = Integer.parseInt(valueStr);
                            cp.getConfigurationParameterSettings().setParameterValue(configName, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "Float":
                        try {
                            float value = Float.parseFloat(valueStr);
                            cp.getConfigurationParameterSettings().setParameterValue(configName, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "Boolean":
                        try {
                            boolean value = Boolean.parseBoolean(valueStr);
                            cp.getConfigurationParameterSettings().setParameterValue(configName, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "Double":
                        try {
                            double value = Double.parseDouble(valueStr);
                            cp.getConfigurationParameterSettings().setParameterValue(configName, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "Long":
                        try {
                            long value = Long.parseLong(valueStr);
                            cp.getConfigurationParameterSettings().setParameterValue(configName, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "Short":
                        try {
                            short value = Short.parseShort(valueStr);
                            cp.getConfigurationParameterSettings().setParameterValue(configName, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "String":
                        cp.getConfigurationParameterSettings().setParameterValue(configName, valueStr);
                        break;

                }
            }


        }
        Object value = aed.getMetaData().getConfigurationParameterSettings().getParameterValue(SQLWriterCasConsumer.PARAM_VERSION);
        if (value != null) {
            if (!externalConfigMap.containsKey(processorName) || !externalConfigMap.get(processorName).containsKey(SQLWriterCasConsumer.PARAM_VERSION))
                versionedCpProcessorId.add(filteredCpeCasProcessors.size());
        }
        filteredCpeCasProcessors.add(cp);
//      add build-in type system for initiating dynamic type generator
        TypeSystemDescription typeSystem = aed.getAnalysisEngineMetaData().getTypeSystem();
        try {
            typeSystem.resolveImports();
        } catch (InvalidXMLException e) {
            e.printStackTrace();
        }
        typeSystems.add(typeSystem);
//      if the AE supports  rule-based customizable type systems
        if (ruleBasedAE) {
            addAutoGenTypesForAE((Class<? extends RuleBasedAEInf>) aeClass, externalRuleConfig);
        }
    }


    public Class<? extends JCasAnnotator_ImplBase> getJCasAnnotatorClassByName(String className) {
        Class<? extends JCasAnnotator_ImplBase> processorClass = null;
        try {
            processorClass = Class.forName(className).asSubclass(JCasAnnotator_ImplBase.class);
        } catch (ClassNotFoundException e) {
            System.err.println("not class for name " + className);
            e.printStackTrace();
        }
        return processorClass;
    }

    public void addAutoGenTypes(LinkedHashMap<String, Class<? extends
            RuleBasedAEInf>> ruleBaseAeClassMap, LinkedHashMap<String, String> ruleBaseAedefaultConfigRuleMap) {
        for (String name : ruleBaseAeClassMap.keySet()) {
            addAutoGenTypesForAE(ruleBaseAeClassMap.get(name), ruleBaseAedefaultConfigRuleMap.get(name));
        }
    }

    public void addAutoGenTypesForAE(Class<? extends RuleBasedAEInf> processClass, String ruleStr) {
        try {
            Constructor<? extends RuleBasedAEInf> constructor = processClass.getConstructor();
            RuleBasedAEInf instance = constructor.newInstance();
            LinkedHashMap<String, TypeDefinition> typeDefs = instance.getTypeDefs(ruleStr);
            addConceptTypes(typeDefs.values());
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    public void setLogger(UIMALogger logger) {
        this.logger = logger;
        for(int i:versionedCpProcessorId){
            CpeCasProcessor cp = null;
            try {
                cp = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors()[i];
            } catch (CpeDescriptorException e) {
                e.printStackTrace();
            }
            cp.getConfigurationParameterSettings().setParameterValue(SQLWriterCasConsumer.PARAM_VERSION, logger.getItem("RUN_ID"));
        }
    }


    public void addConceptType(String conceptName, String superTypeName) {
        TypeDefinition typeDefinition = new TypeDefinition(conceptName, superTypeName);
        conceptTypeDefinitions.put(typeDefinition.fullTypeName, typeDefinition);
    }

    public void addConceptType(TypeDefinition typeDefinition) {
        if (conceptTypeDefinitions.containsKey(typeDefinition.fullTypeName)) {
            LinkedHashMap<String, String> thisFeatureValuePairs = typeDefinition.getFeatureValuePairs();
            LinkedHashMap<String, String> previousFeatureValuePairs = conceptTypeDefinitions.get(typeDefinition.fullTypeName).getFeatureValuePairs();
            for (String featureName : thisFeatureValuePairs.keySet()) {
                if (!previousFeatureValuePairs.containsKey(featureName)) {
                    previousFeatureValuePairs.put(featureName, thisFeatureValuePairs.get(featureName));
                }
            }
        } else {
            conceptTypeDefinitions.put(typeDefinition.fullTypeName, typeDefinition);
        }
    }

    public void addConceptTypes(Collection<TypeDefinition> typeDefinitions) {
        for (TypeDefinition typeDefinition : typeDefinitions)
            addConceptType(typeDefinition);
    }

    public void addConceptType(String conceptName, Collection<String> featureNames, String superTypeName) {
        ArrayList<String> features = new ArrayList<>();
        features.addAll(featureNames);
        TypeDefinition typeDefinition = new TypeDefinition(conceptName, superTypeName, features);
        addConceptType(typeDefinition);
    }

    public void addConceptType(String conceptName) {
        addConceptType(conceptName, defaultSuperTypeName);
    }

    public void addConceptType(String conceptName, Collection<String> featureNames) {
        addConceptType(conceptName, featureNames, defaultSuperTypeName);
    }

    public void reInitTypeSystem(String customTypeDescXml, String srcPath) {
        if (customTypeDescXml == null || customTypeDescXml.trim().length() == 0) {
            customTypeDescXmlLoc = new File(customTypeDescXmlDir, cpeDescripterFileName + "_Types.xml");
            customTypeDescXml = customTypeDescXmlLoc.getAbsolutePath();
        } else {
            customTypeDescXmlLoc = new File(customTypeDescXml);
            customTypeDescXmlDir = customTypeDescXmlLoc.getParentFile();
        }
        if (!customTypeDescXmlDir.exists()) {
            try {
                FileUtils.forceMkdir(customTypeDescXmlDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        TreeSet<String> importedTypes = getImportedTypeNames();
        TreeSet<String> redundant = new TreeSet<>();
        for (String typeFullName : conceptTypeDefinitions.keySet()) {
            if (importedTypes.contains(typeFullName))
                redundant.add(typeFullName);
        }
        for (String removeTypeName : redundant)
            conceptTypeDefinitions.remove(removeTypeName);
        if (conceptTypeDefinitions.size() > 0) {
            dynamicTypeGenerator.addConceptTypes(conceptTypeDefinitions.values());
            dynamicTypeGenerator.reInitTypeSystem(customTypeDescXml, srcPath);
        }
    }

    public void reInitTypeSystem(String customTypeDescXml) {
        reInitTypeSystem(customTypeDescXml, this.srcClassRootPath);
    }


    public TreeSet<String> getImportedTypeNames() {
        return dynamicTypeGenerator.getImportedTypeNames();
    }


    public void run() {
        try {
            if (mCPE == null || refreshed) {
                mCPE = UIMAFramework.produceCollectionProcessingEngine(currentCpeDesc);
                refreshed = false;
            }
            // Create and register a Status Callback Listener
            SimpleStatusCallbackListenerImpl statCbL = new SimpleStatusCallbackListenerImpl(logger);
            mCPE.addStatusCallbackListener(statCbL);
            statCbL.setCollectionProcessingEngine(mCPE);

            // Start Processing
            System.out.println("Running CPE");
            mCPE.process();

            // Allow user to abort by pressing Enter
            if (logger != null)
                logger.logString("To abort processing, type \"abort\" and press enter.");


        } catch (UIMAException e) {
            e.printStackTrace();
        }

    }


    public TypeSystemDescription getTypeSystemDescription() {
        return dynamicTypeGenerator.getTypeSystemDescription();
    }

    public JCas initJCas() {
        JCas jCas = null;
        try {
            jCas = CasCreationUtils.createCas(dynamicTypeGenerator.getTypeSystemDescription(), null, null).getJCas();
        } catch (CASException e) {
            e.printStackTrace();
        } catch (ResourceInitializationException e) {
            e.printStackTrace();
        }
        return jCas;
    }


    public void removeFailedDescriptors(File aFile) {
        int numFailed = 0;
        try {
            CpeCasProcessor[] casProcs = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
            for (int i = 0; i < casProcs.length; i++) {
                boolean success = true;
                try {
                    URL specifierUrl = casProcs[i].getCpeComponentDescriptor().findAbsoluteUrl(defaultResourceManager);
                    ResourceSpecifier specifier = UIMAFramework.getXMLParser().parseResourceSpecifier(
                            new XMLInputSource(specifierUrl));
                    if (isCasConsumerSpecifier(specifier)) {
                        success = addConsumer(casProcs[i]);
                    } else {
                        success = addAE(casProcs[i]);
                    }
                } catch (Exception e) {
                    displayError("Error loading CPE Descriptor " + aFile.getPath());
                    e.printStackTrace();
                    success = false;
                }
                if (!success) {
                    currentCpeDesc.getCpeCasProcessors().removeCpeCasProcessor(i - numFailed);
                    numFailed++;
                }
            }
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    private boolean isCasConsumerSpecifier(ResourceSpecifier specifier) {
        if (specifier instanceof CasConsumerDescription) {
            return true;
        } else if (specifier instanceof URISpecifier) {
            URISpecifier uriSpec = (URISpecifier) specifier;
            return URISpecifier.RESOURCE_TYPE_CAS_CONSUMER.equals(uriSpec.getResourceType());
        } else
            return false;
    }

    private boolean addConsumer(CpeCasProcessor cpeCasProc) throws CpeDescriptorException,
            InvalidXMLException, IOException, ResourceConfigurationException {
        URL consumerSpecifierUrl = cpeCasProc.getCpeComponentDescriptor().findAbsoluteUrl(
                defaultResourceManager);
        //CPE GUI only supports file URLs
        if (!"file".equals(consumerSpecifierUrl.getProtocol())) {
            displayError("Could not load descriptor from URL " + consumerSpecifierUrl.toString() +
                    ".  CPE Configurator only supports file: URLs");
            return false;
        }
        File f;
        try {
            f = urlToFile(consumerSpecifierUrl);
        } catch (URISyntaxException e) {
            displayError(e.toString());
            return false;
        }

        long fileModStamp = f.lastModified(); // get mod stamp before parsing, to prevent race condition
        XMLInputSource consumerInputSource = new XMLInputSource(consumerSpecifierUrl);
        ResourceSpecifier casConsumerSpecifier = UIMAFramework.getXMLParser().parseResourceSpecifier(
                consumerInputSource);
        String tabName;
        if (casConsumerSpecifier instanceof CasConsumerDescription) {
            ResourceMetaData md = ((CasConsumerDescription) casConsumerSpecifier)
                    .getCasConsumerMetaData();
            tabName = md.getName();
        } else {
            tabName = f.getName();
        }
        cpeCasProc.setName(tabName);
        return true;
    }


    private boolean addAE(CpeCasProcessor cpeCasProc) throws CpeDescriptorException,
            InvalidXMLException, IOException, ResourceConfigurationException {
        URL aeSpecifierUrl = cpeCasProc.getCpeComponentDescriptor().findAbsoluteUrl(defaultResourceManager);
        String name = cpeCasProc.getName();
        //CPE GUI only supports file URLs
        if (!"file".equals(aeSpecifierUrl.getProtocol())) {
            displayError("Could not load descriptor from URL " + aeSpecifierUrl.toString() +
                    ".  CPE Configurator only supports file: URLs");
            return false;
        }
        File f;
        try {
            f = urlToFile(aeSpecifierUrl);
        } catch (URISyntaxException e) {
            displayError(e.toString());
            return false;
        }
        long fileModStamp = f.lastModified(); // get mod stamp before parsing, to prevent race condition
        XMLInputSource aeInputSource = new XMLInputSource(aeSpecifierUrl);
        ResourceSpecifier aeSpecifier = UIMAFramework.getXMLParser().parseResourceSpecifier(
                aeInputSource);


        cpeCasProc.setName(name);

        return true;
    }

    private File urlToFile(URL url) throws URISyntaxException {
        return new File(UriUtils.quote(url));
    }

    protected void displayError(String msg) {
        System.err.println(msg);
    }


}
