
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
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.uima.*;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.impl.PrimitiveAnalysisEngine_impl;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CasConsumerDescription;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.base_cpm.CasProcessor;
import org.apache.uima.collection.impl.CollectionProcessingEngine_impl;
import org.apache.uima.collection.impl.cpm.engine.CPMEngine;
import org.apache.uima.collection.impl.metadata.CpeDefaultValues;
import org.apache.uima.collection.impl.metadata.cpe.CpeDescriptorFactory;
import org.apache.uima.collection.metadata.*;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.impl.ChildUimaContext_impl;
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
import org.xml.sax.SAXException;

import javax.xml.stream.*;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.utah.bmi.nlp.core.DeterminantValueSet.defaultSuperTypeName;
import static org.apache.uima.collection.impl.metadata.cpe.CpeDescriptorFactory.produceCollectionReader;

/**
 * @author Jianlin Shi
 * Created on 7/9/17.
 */
public class AdaptableCPEDescriptorRunner implements StatusSetable {
    public static Logger classLogger = IOUtil.getLogger(AdaptableCPEDescriptorRunner.class);
    public static AdaptableCPEDescriptorRunner lastRunner = null;
    protected String annotator, runnerName;
    protected static ModifiedChecker modifiedChecker = new ModifiedChecker();
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
    protected LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap;
    protected LinkedHashMap<String, LinkedHashMap<String, String>> aeConfigTypeMap;
    protected LinkedHashMap<String, Integer> writerIds = new LinkedHashMap<>();
    protected LinkedHashMap<String, Integer> cpeProcessorIds = new LinkedHashMap<>();
    //  store only rule configurations of each rule-based analyses engine
    protected LinkedHashMap<String, Class<? extends JCasAnnotator_ImplBase>> ruleBaseAeClassMap;
    protected ArrayList<Integer> versionedCpProcessorId = new ArrayList<>();

    protected File customTypeDescXmlDir = new File("target/generated-test-sources/uima-descripters");
    protected String runId = "-1", previousRunId = "-1";
    protected File customTypeDescXmlLoc = null;

    protected String cpeDescripterFileName = "", cpeDescriptorPath = "";
    protected boolean refreshed = false;
    protected int status = 0;
    private static final String ACTION_ON_MAX_ERROR = "terminate";

    protected AdaptableCPEDescriptorRunner() {

    }


    public static AdaptableCPEDescriptorRunner getInstance(TasksFX tasks) {
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        String cpeDescriptor = config.getValue("pipeLineSetting/CpeDescriptor");
        LinkedHashMap<String, String> componentsSettings = readPipelineConfigurations(config.getChildSettings("pipeLineSetting"));
        String annotator = config.getValue(ConfigKeys.annotator);
        String pipelineName = new File(cpeDescriptor).getName();
        pipelineName = pipelineName.substring(0, pipelineName.length() - 4);
        return getInstance(cpeDescriptor, annotator, componentsSettings, "classes", "desc/type/" + pipelineName + "_" + annotator + "_Type.xml");
    }

    /**
     * @param cpeDescriptor location of cpe descripter xml file
     * @param options       0~3 parameters:
     *                      1. The location of compiled classes for auto-gen type systems
     *                      2. The location of auto-gen type descriptor
     *                      3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */

    public static AdaptableCPEDescriptorRunner getInstance(String cpeDescriptor, String annotator, String... options) {
        return getInstance(cpeDescriptor, annotator, null, new LinkedHashMap<>(), options);
    }

    /**
     * @param cpeDescriptor location of cpe descripter xml file
     * @param logger        logger to track the pipeline running log (can be null)
     * @param options       0~3 parameters:
     *                      1. The location of compiled classes for auto-gen type systems
     *                      2. The location of auto-gen type descriptor
     *                      3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */

    public static AdaptableCPEDescriptorRunner getInstance(String cpeDescriptor, String annotator, UIMALogger logger, String... options) {
        return getInstance(cpeDescriptor, annotator, logger, new LinkedHashMap<>(), options);
    }

    /**
     * db logger will automatically added if db writer is configured
     *
     * @param cpeDescriptor         location of cpe descripter xml file
     * @param externalRuleConfigMap external configuration values
     * @param options               0~3 parameters:
     *                              1. The location of compiled classes for auto-gen type systems
     *                              2. The location of auto-gen type descriptor
     *                              3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */
    public static AdaptableCPEDescriptorRunner getInstance(String cpeDescriptor, String annotator, LinkedHashMap<String, String> externalRuleConfigMap, String... options) {
        return getInstance(cpeDescriptor, annotator, null, externalRuleConfigMap, options);
    }

    public static AdaptableCPEDescriptorRunner getInstance(String cpeDescriptor, String annotator,
                                                           UIMALogger logger,
                                                           LinkedHashMap<String, String> externalSettingMap,
                                                           String... options) {
        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap = parseExternalConfigMap(externalSettingMap);
        ArrayList<String> modifiedAes = modifiedChecker.checkModifiedAEs(cpeDescriptor, externalConfigMap);
        return getInstance(cpeDescriptor, annotator, logger, modifiedAes, externalConfigMap, options);
    }

    /**
     * @param cpeDescriptor     location of cpe descripter xml file
     * @param logger            logger to track the pipeline running log (can be null)
     * @param externalConfigMap external configurations
     * @param options           0~3 parameters:
     *                          1. The location of compiled classes for auto-gen type systems
     *                          2. The location of auto-gen type descriptor
     *                          3. The location of class source files for auto-gen type systems
     * @return an instance of AdaptableCPEDescriptorRunner
     */
    public static AdaptableCPEDescriptorRunner getInstance(String cpeDescriptor, String annotator,
                                                           UIMALogger logger, ArrayList<String> modifiedAes,
                                                           LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap,
                                                           String... options) {
        String cpeName = FilenameUtils.getBaseName(cpeDescriptor) + "_" + annotator;
        if (lastRunner != null && lastRunner.runnerName.equals(cpeName) && modifiedAes != null) {
            if (modifiedAes.size() > 0) {
                for (String aeName : modifiedAes) {
                    classLogger.finest("The configuration of the AE: " + aeName + " has been modified. Re-initiate this AE.");
                    lastRunner.updateProcessorConfigurations(aeName, externalConfigMap.get(aeName));
                }
            }
        } else {
            if (classLogger.isLoggable(Level.FINEST)) {
                if (modifiedAes == null)
                    classLogger.finest("Cpe descriptor modification detected.");
                else
                    classLogger.finest("Configuration modification detected: " + modifiedAes);
            }
            lastRunner = new AdaptableCPEDescriptorRunner(cpeDescriptor, annotator, logger, externalConfigMap, options);
            lastRunner.runnerName = cpeName;
        }
        lastRunner.setUIMALogger(logger);
        return lastRunner;
    }

    /**
     * @param cpeDescriptor location of cpe descripter xml file
     * @param logger        logger to track the pipeline running log (can be null)
     * @param options       0~3 parameters:
     *                      1. The location of compiled classes for auto-gen type systems
     *                      2. The location of auto-gen type descriptor
     *                      3. The location of class source files for auto-gen type systems
     */
    public AdaptableCPEDescriptorRunner(String cpeDescriptor, String annotator, UIMALogger logger, String... options) {
        this.externalConfigMap = new LinkedHashMap<>();
        this.annotator = annotator;
        setUIMALogger(logger);
        initCpe(cpeDescriptor, annotator, options);
    }

    /**
     * @param cpeDescriptor     location of cpe descripter xml file
     * @param logger            logger to track the pipeline running log (can be null)
     * @param externalConfigMap external configurations (AE name->( configuration name-> value))
     * @param options           0~3 parameters:
     *                          1. The location of compiled classes for auto-gen type systems
     *                          2. The location of auto-gen type descriptor
     *                          3. The location of class source files for auto-gen type systems
     */
    public AdaptableCPEDescriptorRunner(String cpeDescriptor, String annotator, UIMALogger logger,
                                        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap,
                                        String... options) {
        this.annotator = annotator;
        this.externalConfigMap = externalConfigMap;
        setUIMALogger(logger);
        initCpe(cpeDescriptor, annotator, options);
    }


    public CollectionProcessingEngine getmCPE() {
        return mCPE;
    }

    /**
     * convert AEName/ParaName:value pair to nested maps (AE names as the 1st layer, parameter names in the 2nd layer)
     *
     * @param externalSettingMap external configurations (AEName/ParaName:value format)
     */
    public static LinkedHashMap<String, LinkedHashMap<String, String>> parseExternalConfigMap(LinkedHashMap<String, String> externalSettingMap) {
        LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap = new LinkedHashMap<>();
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
        return externalConfigMap;
    }

    /**
     * directly set external configuration map in the nested map format
     *
     * @param externalConfigMap external configurations (nested map format)
     */
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
    public void initCpe(String cpeDescriptor, String annotator, String... options) {
        aeConfigTypeMap = new LinkedHashMap<>();
        conceptTypeDefinitions = new LinkedHashMap<>();
        ruleBaseAeClassMap = new LinkedHashMap<>();
        status = 0;
        if (options == null || options.length == 0) {
            initCpeDescriptor(cpeDescriptor, annotator);
        } else {
            switch (options.length) {
                case 3:
                    this.srcClassRootPath = options[2];
                case 2:
                    this.genDescriptorPath = options[1];
                case 1:
                    this.compiledClassPath = options[0];
                    initCpeDescriptor(cpeDescriptor, annotator);
                    break;
                default:
                    this.compiledClassPath = options[0];
                    this.genDescriptorPath = options[1];
                    this.srcClassRootPath = options[2];
                    initCpeDescriptor(cpeDescriptor, annotator);
                    break;
            }
        }
    }

    public void initCpeDescriptor(String cpeDescriptor, String annotator) {
        refreshed = true;
        ArrayList<TypeSystemDescription> typeSystems = new ArrayList<>();
        ruleBaseAeClassMap = new LinkedHashMap<>();
        cpeDescripterFileName = FilenameUtils.getBaseName(cpeDescriptor) + "_" + annotator;
        customTypeDescXmlDir = new File(customTypeDescXmlDir, cpeDescripterFileName + "_" + Math.abs(new Random().nextInt()));
        try {
            currentCpeDesc = UIMAFramework.getXMLParser().parseCpeDescription(new XMLInputSource(cpeDescriptor));
            removeFailedDescriptors(currentCpeDesc, new File(cpeDescriptor));
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
            CpeCasProcessors cpeCasProcessors = currentCpeDesc.getCpeCasProcessors();
            updateProcessorsDescriptorConfigurations(cpeCasProcessors, annotator, typeSystems, externalConfigMap);

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
        if (conceptTypeDefinitions.size() > 0 || (lastRunner != null && !annotator.equals(lastRunner.annotator))) {
            reInitTypeSystem(genDescriptorPath);
//       attach the new type descriptor to new cpe descriptor
            attachTypeDescriptorToReader();
        }
    }

    public void setCollectionReaderDescriptor(String readerDescriptor, Object... configs) {
        try {
            CpeCollectionReader reader = CpeDescriptorFactory.produceCollectionReader(readerDescriptor);
            currentCpeDesc.setAllCollectionCollectionReaders(new CpeCollectionReader[]{reader});
            updateAllReaderDescriptorConfigs(configs);
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }

    }

    public void setCollectionReaderDescriptor(String readerDescriptor, LinkedHashMap<String, String> configs) {
        try {
            CpeCollectionReader reader = CpeDescriptorFactory.produceCollectionReader(readerDescriptor);
            currentCpeDesc.setAllCollectionCollectionReaders(new CpeCollectionReader[]{reader});
            updateAllReaderDescriptorConfigs(configs);
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }

    }

    public void updateAllReaderDescriptorConfigs(LinkedHashMap<String, String> configs) {
        try {
            CpeCollectionReader[] collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();

            for (CpeCollectionReader collReader : collRdrs) {
                for (String paraName : configs.keySet())
                    collReader.getConfigurationParameterSettings().setParameterValue(paraName, configs.get(paraName));

            }
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    public void updateAllReaderDescriptorConfigs(String paraName, Object value) {
        try {
            CpeCollectionReader[] collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();
            for (CpeCollectionReader collReader : collRdrs) {
                collReader.getConfigurationParameterSettings().setParameterValue(paraName, value);
            }
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    public void updateAllReaderDescriptorConfigs(Object... configs) {
        try {
            CpeCollectionReader[] collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();
            for (CpeCollectionReader collReader : collRdrs) {
                for (int i = 0; i < configs.length; i += 2) {
                    if (collReader.getConfigurationParameterSettings() == null) {
                        CasProcessorConfigurationParameterSettings aSe = CpeDescriptorFactory.produceCasProcessorConfigurationParameterSettings();
                        collReader.setConfigurationParameterSettings(aSe);
                    }
//                    collReader.getConfigurationParameterSettings().setParameterValue((String) configs[i], configs[i + 1]);
                    collReader.getCollectionIterator().getConfigurationParameterSettings().setParameterValue((String) configs[i], configs[i + 1]);
//                    System.out.println(collReader.getConfigurationParameterSettings().getParameterSettings()[0].getClass().getCanonicalName());
                }
            }
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    public void attachTypeDescriptorToReader() {
        try {
            CpeCollectionReader reader = currentCpeDesc.getAllCollectionCollectionReaders()[0];
            if (reader.getDescriptor().getImport() != null) {
                String readerxml = reader.getDescriptor().getImport().getLocation();
                if (!new File(readerxml).exists())
                    readerxml = new File(new File(currentCpeDesc.getSourceUrl().getPath()).getParentFile(), readerxml).getAbsolutePath();
                String newReaderXml = updateTypeDescriptor(readerxml, customTypeDescXmlDir, customTypeDescXmlLoc);
                reader.getDescriptor().getImport().setLocation(new File(newReaderXml).toURI().toString());
            } else {
                String readerxml = reader.getDescriptor().getInclude().get();
                if (!new File(readerxml).exists())
                    readerxml = new File(new File(currentCpeDesc.getSourceUrl().getPath()).getParentFile(), readerxml).getAbsolutePath();
                String newReaderXml = updateTypeDescriptor(readerxml, customTypeDescXmlDir, customTypeDescXmlLoc);
                reader.getDescriptor().getInclude().set(new File(newReaderXml).toURI().toString());
            }
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    protected boolean checkClassLoaded(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    protected void configureReader(CpeCollectionReader collReader, CollectionReaderDescription crd,
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

    /**
     * Because new types are dynamically generated, need to bound the new type descriptor to cpe descriptor (hook to the reader descriptor)
     *
     * @param readerxml            cpe reader's xml location
     * @param customTypeDescXmlDir generated type descriptor directory
     * @param customTypeDescXmlLoc generated type descriptor location
     * @return new generated reader descriptor
     */
    protected String updateTypeDescriptor(String readerxml, File customTypeDescXmlDir, File customTypeDescXmlLoc) {
        File inputFile = new File(readerxml);
        String fileName = cpeDescripterFileName + "_" + FilenameUtils.getBaseName(readerxml) + ".xml";
        File outputXmlFile = new File(customTypeDescXmlDir, fileName);
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
                    writer.add(eventFactory.createAttribute("location", customTypeDescXmlLoc.getName()));
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

    /**
     * @param cpeCasProcessors  cpeDescriptor's cpeCasProcessor
     * @param typeSystems       TypeSystem for each AE
     * @param externalConfigMap external configurations for parameters
     * @return
     * @throws IOException
     * @throws InvalidXMLException
     * @throws CpeDescriptorException
     */
    protected void updateProcessorsDescriptorConfigurations(
            CpeCasProcessors cpeCasProcessors, String annotator,
            ArrayList<TypeSystemDescription> typeSystems,
            LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap)
            throws IOException, InvalidXMLException, CpeDescriptorException {
        CpeCasProcessor[] cpeCasProcessorsArray = cpeCasProcessors.getAllCpeCasProcessors();
        int numRemoved = 0;
        for (int i = 0; i < cpeCasProcessorsArray.length; i++) {
            CpeCasProcessor cp = cpeCasProcessorsArray[i];


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
            boolean ruleBasedAE = false;
            Object cpeVersionValue = null;
            if (cp.getConfigurationParameterSettings() != null) {
                cpeVersionValue = cp.getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_VERSION);
            } else {
                CasProcessorConfigurationParameterSettings aSe = CpeDescriptorFactory.produceCasProcessorConfigurationParameterSettings();
                cp.setConfigurationParameterSettings(aSe);

            }
            Object aeVersionValue = aed.getMetaData().getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_VERSION);

            if (RuleBasedAEInf.class.isAssignableFrom(aeClass)) {
                ruleBasedAE = true;
                if (externalConfigMap.containsKey(processorName)
                        && externalConfigMap.get(processorName).containsKey(DeterminantValueSet.PARAM_RULE_STR)
                        && externalConfigMap.get(processorName).get(DeterminantValueSet.PARAM_RULE_STR).trim().length() == 0) {
                    cpeCasProcessors.removeCpeCasProcessor(i - numRemoved);
                    classLogger.fine("The rule for the Rule-based AE: '" + processorName + "' has been configured to blank, skip this AE.");
                    numRemoved++;
                    continue;
                } else {
                    Object aeRuleValue = aed.getMetaData().getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR);
                    Object cpeRuleValue = cp.getConfigurationParameterSettings() == null ? null : cp.getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR);
                    String externalValue = null;
                    if (externalConfigMap.containsKey(processorName)
                            && externalConfigMap.get(processorName).containsKey(DeterminantValueSet.PARAM_RULE_STR)) {
                        externalValue = externalConfigMap.get(processorName).get(DeterminantValueSet.PARAM_RULE_STR);
                    }
                    if ((cpeRuleValue == null || cpeRuleValue.equals(""))
                            && (aeRuleValue == null || aeRuleValue.equals("")
                            || (aeRuleValue.toString().indexOf("\t") == -1
                            && !new File(aeRuleValue.toString()).exists()))
                            && (externalValue == null || externalValue.trim().length() == 0)) {
                        cpeCasProcessors.removeCpeCasProcessor(i - numRemoved);
                        classLogger.fine("The rule for the Rule-based AE: '" + processorName + "' has not been configured, skip this AE.");
                        numRemoved++;
                        continue;
                    }
                    ruleBaseAeClassMap.put(processorName, aeClass);
                }
            }
            if (processorName.toLowerCase().indexOf("writer") != -1) {
                writerIds.put(processorName, i - numRemoved);
            }
            updateProcessorsDescriptorConfigurations(processorName, cp, aed, externalConfigMap.get(processorName));
//                  track versioned rule-based AE ids
            if (aeVersionValue != null) {
//                        only update to current run_id when cpe didn't configure run_id to a real value.
                if (cpeVersionValue == null || cpeVersionValue.equals("")) {
                    versionedCpProcessorId.add(i - numRemoved);
//                    if (runId.equals("-1")) {
//                        Object dbConfigFile = cp.getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_DB_CONFIG_FILE);
//                        if (annotator.length() == 0)
//                            annotator = cp.getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_ANNOTATOR).toString();
//                        if (this.logger==null && dbConfigFile != null && dbConfigFile instanceof String && dbConfigFile.toString().trim().length() > 0) {
//                            setUIMALogger(new NLPDBLogger(dbConfigFile.toString(), annotator));
//                        }
//                    }

                }
                cp.getConfigurationParameterSettings().setParameterValue(DeterminantValueSet.PARAM_ANNOTATOR, annotator);
            }
            addAeTypes(aed, typeSystems);
            if (ruleBasedAE) {

                CasProcessorConfigurationParameterSettings settings = cp.getConfigurationParameterSettings();
                if (settings.getParameterValue(DeterminantValueSet.PARAM_RULE_STR) == null)
                    for (NameValuePair namevalue : settings.getParameterSettings()) {
                        if (namevalue.getName().equals(DeterminantValueSet.PARAM_RULE_STR))
                            addAutoGenTypesForAE((Class<? extends RuleBasedAEInf>) aeClass, namevalue.getValue().toString());
                    }
                else
                    addAutoGenTypesForAE((Class<? extends RuleBasedAEInf>) aeClass, cp.getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR).toString());
            }
        }
    }


    protected void updateProcessorDescriptorConfigurations(
            CpeCasProcessor cpeCasProcessor,
            ArrayList<TypeSystemDescription> typeSystems,
            LinkedHashMap<String, LinkedHashMap<String, String>> externalConfigMap)
            throws IOException, InvalidXMLException, CpeDescriptorException {

    }

    /**
     * @param id
     * @param paraName
     * @param value
     */
    public void updateDescriptorConfiguration(int id, String paraName, Object value) {
        try {
            CpeCasProcessor cpeProcessor = currentCpeDesc.getCpeCasProcessors().getCpeCasProcessor(id);
            if (cpeProcessor.getConfigurationParameterSettings() == null) {
                CasProcessorConfigurationParameterSettings aSe = CpeDescriptorFactory.produceCasProcessorConfigurationParameterSettings();
                cpeProcessor.setConfigurationParameterSettings(aSe);
            }
            cpeProcessor.getConfigurationParameterSettings().setParameterValue(paraName, value);
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    public void updateReadDescriptorsConfiguration(String paraName, Object value) {
        CpeCollectionReader[] collRdrs = new CpeCollectionReader[0];
        try {
            collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();
            for (CpeCollectionReader collReader : collRdrs) {
                collReader.getConfigurationParameterSettings().setParameterValue(paraName, value);

            }
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

//    public void setReaderDescriptor(String descriptorPath, Object... configurationData) {
//        try {
//            CpeCollectionReader[] collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();
//            URL url = new File(descriptorPath).toURI().toURL();
//            collRdrs[0].setSourceUrl(url);
//            collRdrs[0].getDescriptor().getImport().setLocation(url.getPath());
//            collRdrs[0].getDescriptor();
//            for (int i = 0; i < configurationData.length; i += 2)
//                collRdrs[0].getConfigurationParameterSettings().setParameterValue(configurationData[i] + "", configurationData[i + 1]);
//            currentCpeDesc.setAllCollectionCollectionReaders(collRdrs);
//        } catch (CpeDescriptorException e) {
//            e.printStackTrace();
//        } catch (MalformedURLException e) {
//            e.printStackTrace();
//        }
//
//    }


    protected void updateProcessorsDescriptorConfigurations(CpeCasProcessor cp,
                                                            AnalysisEngineDescription aed, Object... configs) {
        for (int i = 0; i < configs.length - 1; i += 2) {
            String paraName = (String) configs[i];
            Object value = configs[i + 1];
            if (cp.getConfigurationParameterSettings() == null) {
                CasProcessorConfigurationParameterSettings aSe = CpeDescriptorFactory.produceCasProcessorConfigurationParameterSettings();
                try {
                    cp.setConfigurationParameterSettings(aSe);
                } catch (CpeDescriptorException e) {
                    e.printStackTrace();
                }
            }
            cp.getConfigurationParameterSettings().setParameterValue(paraName, value);
        }

    }


    /**
     * Filter out rule-based AEs with null or blank value in parameter  "RuleFileOrStr"
     *
     * @param processorName
     * @param cp
     * @param aed
     * @param externalConfigMap
     */
    protected void updateProcessorsDescriptorConfigurations(String processorName, CpeCasProcessor cp,
                                                            AnalysisEngineDescription aed,
                                                            LinkedHashMap<String, String> externalConfigMap) {

        LinkedHashMap<String, String> aeparas = aeConfigTypeMap.get(processorName);
        /* configure cp process with external configuration, if applicable */
        if (externalConfigMap != null) {
            for (Map.Entry<String, String> externalConfigs : externalConfigMap.entrySet()) {
                String configName = externalConfigs.getKey();
                String valueStr = externalConfigs.getValue();
                String valueType = aeparas.get(configName);
                try {
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
                                char ch = valueStr.toLowerCase().charAt(0);
                                boolean value = ch == 't' || ch == '1';
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
                } catch (Exception e) {
                    classLogger.warning("Incorrect format of configuration value for AE: " + processorName
                            + " parameter: " + configName + " value: " + valueStr + ". Cannot be parsed to type: "
                            + valueType);

                }
            }
        }
    }

    private void addAeTypes(AnalysisEngineDescription aed, ArrayList<TypeSystemDescription> typeSystems) {
        //      add build-in type system for initiating dynamic type generator
        TypeSystemDescription typeSystem = aed.getAnalysisEngineMetaData().getTypeSystem();
        try {
            typeSystem.resolveImports();
        } catch (InvalidXMLException e) {
            e.printStackTrace();
        }
        typeSystems.add(typeSystem);
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

    public void setUIMALogger(UIMALogger logger) {
        this.logger = logger;
        if (logger != null)
            runId = logger.getRunid() + "";
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
//            classLogger.finest("add type:"+typeDefinition.fullTypeName);
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
            if (customTypeDescXmlLoc == null) {
                customTypeDescXmlLoc = new File(customTypeDescXmlDir, cpeDescripterFileName + "_Types.xml");
                customTypeDescXml = customTypeDescXmlLoc.getAbsolutePath();
            }
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

    public void reInitTypeSystem() {
        reInitTypeSystem(null, srcClassRootPath);
    }

    public void reInitTypeSystem(String customTypeDescXml) {
        reInitTypeSystem(customTypeDescXml, this.srcClassRootPath);
    }


    public TreeSet<String> getImportedTypeNames() {
        return dynamicTypeGenerator.getImportedTypeNames();
    }


    /**
     * Try to update the configuration after mCPE is compiled--handy for debugging--reduce recompile time
     *
     * @param cpeName        processor name
     * @param configurations new configuraitons
     */
    public void updateProcessorConfigurations(String cpeName, LinkedHashMap configurations) {
        if (cpeProcessorIds.containsKey(cpeName)) {
            int id = cpeProcessorIds.get(cpeName);
            updateProcessorConfigurations(id, configurations);
        } else {
            classLogger.warning(cpeName + " doesn't exist in this CPE descriptor.");
        }
    }

    /**
     * Try to update the configuration after mCPE is compiled--handy for debugging--reduce recompile time
     *
     * @param cpeId          processor name
     * @param configurations new configuraitons
     */
    public void updateProcessorConfigurations(int cpeId, LinkedHashMap configurations) {
        if (mCPE != null && cpeId < mCPE.getCasProcessors().length) {
            CasProcessor[] processors = mCPE.getCasProcessors();
            CasProcessor processor = processors[cpeId];
            try {
                CpeCasProcessor cpeProcessor = currentCpeDesc.getCpeCasProcessors().getCpeCasProcessor(cpeId);
                String processorName = cpeProcessor.getName();
                if (processor instanceof PrimitiveAnalysisEngine_impl) {
                    PrimitiveAnalysisEngine_impl ae = (PrimitiveAnalysisEngine_impl) processor;
                    UimaContext uimaContext = ae.getUimaContext();
                    if (uimaContext instanceof ChildUimaContext_impl) {
                        ChildUimaContext_impl uimaContext_impl = (ChildUimaContext_impl) uimaContext;
                        for (Object configName : configurations.keySet()) {
                            Object value = configurations.get(configName);
                            uimaContext_impl.setSharedParam("/" + processorName + "/" + configName, value);
                            AnalysisComponent aeEngine = ae.getAnalysisComponent();
                            if (configName.equals(DeterminantValueSet.PARAM_RULE_STR) && RuleBasedAEInf.class.isAssignableFrom(aeEngine.getClass())) {
                                RuleBasedAEInf ruleAeEngine = (RuleBasedAEInf) aeEngine;
                                dynamicTypeGenerator.addConceptTypes(ruleAeEngine.getTypeDefs(value.toString()).values());
                                dynamicTypeGenerator.reInitTypeSystem(customTypeDescXmlLoc.getAbsolutePath(), srcClassRootPath);
                                aeEngine.initialize(uimaContext);
                            }
                        }
                    }
                    ae.setmInitialized(false);
                }
            } catch (ResourceInitializationException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            classLogger.warning(cpeId + " doesn't exist in this CPE descriptor.");
        }
    }

    /**
     * Try to update the configuration after mCPE is compiled--handy for debugging--reduce recompile time
     *
     * @param cpeName   processor name
     * @param parameter parameter name
     * @param value     configuration value
     */
    public void updateCpeProcessorConfiguration(String cpeName, String parameter, Object value) {
        if (cpeProcessorIds.containsKey(cpeName)) {
            int id = cpeProcessorIds.get(cpeName);
            updateCpeProcessorConfiguration(id, parameter, value);
        } else {
            classLogger.warning(cpeName + " doesn't exist in this CPE descriptor.");
        }
    }


    public void updateCpeProcessorConfiguration(int cpeId, Object... configs) {
        if (mCPE != null && cpeId < mCPE.getCasProcessors().length) {
            CasProcessor processor = mCPE.getCasProcessors()[cpeId];
            try {
                CPMEngine cpeEngine = ((CollectionProcessingEngine_impl) mCPE).getCPM().getCpEngine();
                CpeCasProcessor cpeProcessor = currentCpeDesc.getCpeCasProcessors().getCpeCasProcessor(cpeId);
                String processorName = cpeProcessor.getName();
                if (processor instanceof PrimitiveAnalysisEngine_impl) {
                    PrimitiveAnalysisEngine_impl ae = (PrimitiveAnalysisEngine_impl) processor;
                    UimaContext uimaContext = ae.getUimaContext();
                    AnalysisComponent aeEngine = ae.getAnalysisComponent();
                    ae.setmInitialized(false);
                    for (int i = 0; i < configs.length - 1; i += 2) {
                        Object obj = configs[i];
                        if (!(obj instanceof String))
                            classLogger.warning("parameter \"" + obj + "\" to set for cpeprocess " + cpeId + " is not a string");
                        String parameter = (String) configs[i];
                        Object value = configs[i + 1];
                        if (uimaContext instanceof ChildUimaContext_impl) {
                            ChildUimaContext_impl uimaContext_impl = (ChildUimaContext_impl) uimaContext;
                            uimaContext_impl.setSharedParam("/" + processorName + "/" + parameter, value);
                        }
                        if (parameter.equals(DeterminantValueSet.PARAM_RULE_STR) && RuleBasedAEInf.class.isAssignableFrom(aeEngine.getClass())) {
                            RuleBasedAEInf ruleAeEngine = (RuleBasedAEInf) aeEngine;
                            dynamicTypeGenerator.addConceptTypes(ruleAeEngine.getTypeDefs(value.toString()).values());
                            dynamicTypeGenerator.reInitTypeSystem(customTypeDescXmlLoc.getAbsolutePath(), srcClassRootPath);
                        }
                    }
                    aeEngine.initialize(uimaContext);
                }
//                cpeEngine.getCpeFactory().addCasProcessor(processor);
//            } catch (ResourceConfigurationException e) {
//                e.printStackTrace();
            } catch (ResourceInitializationException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            classLogger.warning(cpeId + " doesn't exist in this CPE descriptor.");
        }
    }


    public void addCpeProcessorDescriptor(Class aeClass, String name, Object... configs) {
        try {
            addCpeProcessorDescriptor(aeClass, name, currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors().length, configs);
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    public void addCpeProcessorDescriptor(Class aeClass, String name, int pos, Object... configs) {
        try {
            AnalysisEngineDescription aed = AnalysisEngineFactory.createEngineDescription(aeClass, configs);
            CpeIntegratedCasProcessor cp = createProcessor(name, aed);
            currentCpeDesc.getCpeCasProcessors().addCpeCasProcessor(cp, pos);
            updateProcessorsDescriptorConfigurations(cp, aed, configs);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ResourceInitializationException e) {
            e.printStackTrace();
        }
    }

    public void addCpeProcessorDescriptor(String descriptorPath, int pos, Object... configs) {
        File descFile = new File(descriptorPath);
        try {
            AnalysisEngineDescription aed = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(new XMLInputSource(descFile));
            String name = aed.getAnalysisEngineMetaData().getName();
            CpeIntegratedCasProcessor cp = createProcessor(name, aed);
            currentCpeDesc.getCpeCasProcessors().addCpeCasProcessor(cp, pos);
            cpeProcessorIds.put(name, pos);
            updateCpeProcessorConfiguration(pos, configs);
        } catch (InvalidXMLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public void addCpeProcessorDescriptor(String descriptorPath, String name, Object... configs) {
        try {
            addCpeProcessorDescriptor(descriptorPath, name, currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors().length, configs);
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }


    public void addCpeProcessorDescriptor(String descriptorPath, String name, int pos, Object... configs) {
        File descFile = new File(descriptorPath);
        try {
            AnalysisEngineDescription aed = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(new XMLInputSource(descFile));
            CpeIntegratedCasProcessor cp = createProcessor(name, aed);
            currentCpeDesc.getCpeCasProcessors().addCpeCasProcessor(cp, pos);
            cpeProcessorIds.put(name, pos);
            updateProcessorsDescriptorConfigurations(cp, aed, configs);
        } catch (InvalidXMLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public void addCpeProcessorDescriptor(String name, AnalysisEngineDescription aDesc, int pos, Object... configs) {
        try {
            CpeCasProcessors cpeCasProcessors = currentCpeDesc.getCpeCasProcessors();
            CpeIntegratedCasProcessor processor = createProcessor(name, aDesc);
            cpeCasProcessors.addCpeCasProcessor(processor, pos);
            cpeProcessorIds.put(name, pos);
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeCpeProcessDescriptor(String cpeName) {
        removeCpeProcessDescriptor(this.cpeProcessorIds.get(cpeName));
    }

    public void removeCpeProcessDescriptor(int id) {
        try {
            currentCpeDesc.getCpeCasProcessors().removeCpeCasProcessor(id);
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    public Set<String> getCpeProcessorNames() {
        return cpeProcessorIds.keySet();
    }

    public void compileCPE() {
        try {
            mCPE = UIMAFramework.produceCollectionProcessingEngine(currentCpeDesc);
            Class<CompositeResourceFactory> e = CompositeResourceFactory.class;

            if (runId.equals(previousRunId))
                runId = logger.getRunid() + "";
            for (int writerId : writerIds.values()) {
                updateCpeProcessorConfiguration(writerId, DeterminantValueSet.PARAM_VERSION, runId);
            }
        } catch (ResourceInitializationException e) {
            e.printStackTrace();
        }
        refreshed = false;
        int i = 0;
        for (CasProcessor casProcessor : mCPE.getCasProcessors()) {
            String name = casProcessor.getProcessingResourceMetaData().getName();
            cpeProcessorIds.put(name, i);
            i++;
        }

    }

    public void run() {
        try {
            if (status == 1) {
                classLogger.info("Pipeline is running. Please wait till finish.");
                return;
            }

            status = 1;
            compileCPE();
            // Create and register a Status Callback Listener
            SimpleStatusCallbackListenerImpl statCbL = new SimpleStatusCallbackListenerImpl(logger);
            mCPE.addStatusCallbackListener(statCbL);
            statCbL.setRunner(this);
            statCbL.setCollectionProcessingEngine(mCPE);


            // Start Processing
//            System.out.println("Running CPE");
            mCPE.process();
            previousRunId = runId;

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


    public void removeFailedDescriptors(CpeDescription currentCpeDesc, File aFile) {
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
                        success = tryLoadConsumer(casProcs[i]);
                    } else {
                        success = tryLoadAE(casProcs[i]);
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

    protected boolean isCasConsumerSpecifier(ResourceSpecifier specifier) {
        if (specifier instanceof CasConsumerDescription) {
            return true;
        } else if (specifier instanceof URISpecifier) {
            URISpecifier uriSpec = (URISpecifier) specifier;
            return URISpecifier.RESOURCE_TYPE_CAS_CONSUMER.equals(uriSpec.getResourceType());
        } else
            return false;
    }

    protected boolean tryLoadConsumer(CpeCasProcessor cpeCasProc) throws CpeDescriptorException,
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


    protected boolean tryLoadAE(CpeCasProcessor cpeCasProc) throws CpeDescriptorException,
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

    protected File urlToFile(URL url) throws URISyntaxException {
        return new File(UriUtils.quote(url));
    }

    protected void displayError(String msg) {
        System.err.println(msg);
    }


    public LinkedHashMap<String, Integer> getWriterIds() {
        return writerIds;
    }

    public UIMALogger getLogger() {
        return logger;
    }


    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public int getStatus() {
        return status;
    }


    protected static LinkedHashMap<String, String> readPipelineConfigurations(LinkedHashMap<String, SettingAb> pipelineSettings) {
        LinkedHashMap<String, String> componentsSettings = new LinkedHashMap<>();
        for (SettingAb setting : pipelineSettings.values()) {
            String[] componentConfigure = setting.getSettingName().split("/");
            if (componentConfigure.length < 3)
                continue;
            String key = componentConfigure[1] + "/" + componentConfigure[2];
            String value = setting.getSettingValue();
            componentsSettings.put(key, value);
        }
        return componentsSettings;
    }

    public LinkedHashMap<String, Integer> getCpeProcessorIds() {
        return cpeProcessorIds;
    }


    private static CpeIntegratedCasProcessor createProcessor(String key,
                                                             AnalysisEngineDescription aDesc) throws IOException, SAXException,
            CpeDescriptorException {
        URL descUrl = materializeDescriptor(aDesc).toURI().toURL();

        CpeInclude cpeInclude = UIMAFramework.getResourceSpecifierFactory()
                .createInclude();
        cpeInclude.set(descUrl.toString());

        CpeComponentDescriptor ccd = UIMAFramework
                .getResourceSpecifierFactory().createDescriptor();
        ccd.setInclude(cpeInclude);

        CpeIntegratedCasProcessor proc = CpeDescriptorFactory
                .produceCasProcessor(key);
        proc.setCpeComponentDescriptor(ccd);
        proc.setAttributeValue(CpeDefaultValues.PROCESSING_UNIT_THREAD_COUNT, 1);
        proc.setActionOnMaxError(ACTION_ON_MAX_ERROR);
        proc.setMaxErrorCount(0);

        return proc;
    }


    private static File materializeDescriptor(ResourceSpecifier resource)
            throws IOException, SAXException {
        File tempDesc = File.createTempFile("desc", ".xml");
        // tempDesc.deleteOnExit();

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempDesc), "UTF-8"));
        resource.toXML(out);
        out.close();

        return tempDesc;
    }
}
