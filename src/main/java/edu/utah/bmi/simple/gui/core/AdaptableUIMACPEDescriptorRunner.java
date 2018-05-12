
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

package edu.utah.bmi.simple.gui.core;

import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.uima.DynamicTypeGenerator;
import edu.utah.bmi.nlp.uima.SimpleStatusCallbackListenerImpl;
import edu.utah.bmi.nlp.uima.ae.RuleBasedAEInf;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CasConsumerDescription;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.*;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.*;
import org.apache.uima.resource.metadata.ResourceMetaData;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.UriUtils;
import org.apache.uima.util.XMLInputSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import static edu.utah.bmi.nlp.core.DeterminantValueSet.defaultSuperTypeName;

/**
 * @author Jianlin Shi
 * Created on 7/9/16.
 */
public class AdaptableUIMACPEDescriptorRunner {
    protected CollectionReaderDescription reader;
    protected CpeDescription currentCpeDesc;
    protected File rootFolder;
    protected ArrayList<AnalysisEngineDescription> analysisEngines = new ArrayList<>();
    protected LinkedHashMap<String, TypeDefinition> conceptTypeDefinitions = new LinkedHashMap<>();
    protected DynamicTypeGenerator dynamicTypeGenerator;
    protected String srcClassRootPath = null;
    protected UIMALogger logger;
    protected final ResourceManager defaultResourceManager = UIMAFramework.newDefaultResourceManager();
    protected CollectionProcessingEngine mCPE;
    protected long mInitCompleteTime;
    protected int totaldocs = -1;
    public int maxCommentLength = 500;
    private LinkedHashMap<String, String> externalRuleConfigMap;
    protected LinkedHashMap<String, Class<? extends JCasAnnotator_ImplBase>> ruleBaseAeClassMap;
    protected String genDescriptorPath;

    protected AdaptableUIMACPEDescriptorRunner() {

    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor) {
        this.externalRuleConfigMap = new LinkedHashMap<>();
        initTypeGenerator(cpeDescriptor);
    }


    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath) {
        this.externalRuleConfigMap = new LinkedHashMap<>();
        initTypeGenerator(cpeDescriptor, compileRootPath);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath, String srcClassRootPath) {
        this.externalRuleConfigMap = new LinkedHashMap<>();
        initTypeGenerator(cpeDescriptor, compileRootPath, srcClassRootPath);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, LinkedHashMap<String, String> externalRuleConfigMap, String... optionals) {
        this.externalRuleConfigMap = externalRuleConfigMap;
        if (optionals != null && optionals.length > 0)
            genDescriptorPath = optionals[1];
        initTypeGenerator(cpeDescriptor);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath, LinkedHashMap<String, String> externalRuleConfigMap, String... optionals) {
        this.externalRuleConfigMap = externalRuleConfigMap;
        if (optionals != null && optionals.length > 0)
            genDescriptorPath = optionals[1];
        initTypeGenerator(cpeDescriptor, compileRootPath);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath, String srcClassRootPath, LinkedHashMap<String, String> externalRuleConfigMap, String... optionals) {
        this.externalRuleConfigMap = externalRuleConfigMap;
        if (optionals != null && optionals.length > 0)
            genDescriptorPath = optionals[1];
        initTypeGenerator(cpeDescriptor, compileRootPath, srcClassRootPath);
    }


    public void initTypeGenerator(String... settings) {
        switch (settings.length) {
            case 0:
                new Throwable("Not CPE descriptor is specified");
                break;
            case 1:
                initTypeDescriptor(settings[0]);
                break;
            case 2:
                dynamicTypeGenerator.setCompiledRootPath(new File(settings[1]));
                initTypeDescriptor(settings[0]);
                break;
            default:
                dynamicTypeGenerator.setCompiledRootPath(new File(settings[1]));
                this.srcClassRootPath = settings[2];
                initTypeDescriptor(settings[0]);
                break;
        }
    }

    public void initTypeDescriptor(String cpeDescriptor) {
        ArrayList<TypeSystemDescription> typeSystems = new ArrayList<>();
        ruleBaseAeClassMap = new LinkedHashMap<>();
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
            }


            CpeCasProcessor[] cpeCasProcessors = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
            LinkedHashMap<String, AnalysisEngineDescription> aeDescMap = initCpeProcessorClassMap(cpeCasProcessors, ruleBaseAeClassMap);
            if (externalRuleConfigMap.size() > 0) {
                customizeCpe(currentCpeDesc, ruleBaseAeClassMap, externalRuleConfigMap);//
                cpeCasProcessors = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
            }
//            currentCpeDesc.setCpeCasProcessors(customizedCpeCasProcessors.toArray(new CpeCasProcessor[customizedCpeCasProcessors.size()]));
            for (int i = 0; i < cpeCasProcessors.length; i++) {
                CpeCasProcessor casProcessor = cpeCasProcessors[i];
                String processorName = casProcessor.getName();

                File descFile = new File(rootFolder + File.separator + casProcessor.getCpeComponentDescriptor().getImport().getLocation());
                AnalysisEngineDescription aed = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(new XMLInputSource(descFile));

                String config = null;
//               if no external configuration is set, use the cpe descriptor's default rule configurations to generate type system.
                if (externalRuleConfigMap.size() == 0) {
                    config = (String) casProcessor.getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR);
                    if (config == null)
                        config = (String) aed.getMetaData().getConfigurationParameterSettings().getParameterValue(DeterminantValueSet.PARAM_RULE_STR);
                    if (config != null && config.trim().length() > 0)
                        externalRuleConfigMap.put(processorName, config);
                }
                TypeSystemDescription typeSystem = aed.getAnalysisEngineMetaData().getTypeSystem();
                typeSystem.resolveImports();
                typeSystems.add(typeSystem);
            }


        } catch (InvalidXMLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
        dynamicTypeGenerator = new DynamicTypeGenerator(typeSystems);
        addAutoGenTypes(ruleBaseAeClassMap, externalRuleConfigMap);
        if (externalRuleConfigMap.size() > 0)
            reInitTypeSystem(genDescriptorPath);
    }

    private LinkedHashMap<String, AnalysisEngineDescription> initCpeProcessorClassMap(
            CpeCasProcessor[] cpeCasProcessors,
            LinkedHashMap<String, Class<? extends JCasAnnotator_ImplBase>> ruleBaseAeClassMap)
            throws IOException, InvalidXMLException {
        LinkedHashMap<String, AnalysisEngineDescription> aeDescMap = new LinkedHashMap<>();
        for (CpeCasProcessor cp : cpeCasProcessors) {
            String processorName = cp.getName();
            File descFile = new File(rootFolder + File.separator + cp.getCpeComponentDescriptor().getImport().getLocation());
            AnalysisEngineDescription aed = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(new XMLInputSource(descFile));
            aeDescMap.put(processorName, aed);
            String className = aed.getImplementationName();
            ruleBaseAeClassMap.put(processorName, getJCasAnnotatorClassByName(className));
        }
        return aeDescMap;
    }

    private void customizeCpe(CpeDescription currentCpeDesc,
                              LinkedHashMap<String, Class<? extends JCasAnnotator_ImplBase>> ruleBaseAeClassMap,
                              LinkedHashMap<String, String> externalRuleConfigMap) throws CpeDescriptorException {
        CpeCasProcessor[] cpeCasProcessors = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        currentCpeDesc.getCpeCasProcessors().removeAllCpeCasProcessors();
        for (CpeCasProcessor cp : cpeCasProcessors) {
            String cpName = cp.getName();
            if (!RuleBasedAEInf.class.isAssignableFrom(ruleBaseAeClassMap.get(cpName))) {
//              if this is not a RuleBasedAEInf processor, add it anyway no matter if it's configured in external configurations
                currentCpeDesc.getCpeCasProcessors().addCpeCasProcessor(cp);
            } else if (externalRuleConfigMap.containsKey(cp.getName())
                    && externalRuleConfigMap.get(cp.getName()) != null
                    && externalRuleConfigMap.get(cp.getName()).trim().length() > 0) {
                currentCpeDesc.getCpeCasProcessors().addCpeCasProcessor(cp);
            }
        }
    }

    public Class<? extends JCasAnnotator_ImplBase> getJCasAnnotatorClassByName(String className) {
        Class<? extends JCasAnnotator_ImplBase> processorClass = null;
        try {
            processorClass = Class.forName(className).asSubclass(JCasAnnotator_ImplBase.class);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return processorClass;
    }

    public void addAutoGenTypes(LinkedHashMap<String, Class<? extends
            JCasAnnotator_ImplBase>> ruleBaseAeClassMap, LinkedHashMap<String, String> ruleBaseAedefaultConfigRuleMap) {
        for (String name : ruleBaseAeClassMap.keySet()) {
            Class<? extends JCasAnnotator_ImplBase> processClass = ruleBaseAeClassMap.get(name);
            try {
                if (RuleBasedAEInf.class.isAssignableFrom(processClass)) {
                    Constructor<? extends RuleBasedAEInf> constructor = (Constructor<? extends RuleBasedAEInf>) processClass.getConstructor();
                    RuleBasedAEInf instance = constructor.newInstance();
                    LinkedHashMap<String, TypeDefinition> typeDefs = instance.getTypeDefs(ruleBaseAedefaultConfigRuleMap.get(name));
                    addConceptTypes(typeDefs.values());
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public void setLogger(UIMALogger logger) {
        this.logger = logger;
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
            customTypeDescXml = "desc/type/tmp_desc.xml";
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
            mCPE = null;
            mCPE = UIMAFramework.produceCollectionProcessingEngine(currentCpeDesc);
            SimpleStatusCallbackListenerImpl statCbL = new SimpleStatusCallbackListenerImpl(logger);
            mCPE.addStatusCallbackListener(statCbL);
            statCbL.setCollectionProcessingEngine(mCPE);

            // start processing
            mCPE.process();
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
