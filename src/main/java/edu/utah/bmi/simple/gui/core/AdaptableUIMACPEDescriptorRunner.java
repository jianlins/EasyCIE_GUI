
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

import edu.utah.bmi.nlp.uima.DynamicTypeGenerator;
import edu.utah.bmi.nlp.uima.SimpleStatusCallbackListenerImpl;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
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
import org.apache.uima.resource.metadata.ResourceMetaData;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.UriUtils;
import org.apache.uima.util.XMLInputSource;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

/**
 * @author Jianlin Shi
 *         Created on 7/9/16.
 */
public class AdaptableUIMACPEDescriptorRunner {
    protected CollectionReaderDescription reader;
    protected CpeDescription currentCpeDesc;
    protected File rootFolder;
    protected ArrayList<AnalysisEngineDescription> analysisEngines = new ArrayList<>();
    protected DynamicTypeGenerator dynamicTypeGenerator;
    protected String srcClassRootPath = null;
    protected UIMALogger logger;
    protected final ResourceManager defaultResourceManager = UIMAFramework.newDefaultResourceManager();
    protected CollectionProcessingEngine mCPE;
    protected long mInitCompleteTime;
    protected int totaldocs = -1;
    public int maxCommentLength = 500;

    protected AdaptableUIMACPEDescriptorRunner() {

    }


    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor) {
        init(cpeDescriptor);
    }


    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath) {
        init(cpeDescriptor, compileRootPath);
    }

    public AdaptableUIMACPEDescriptorRunner(String cpeDescriptor, String compileRootPath, String srcClassRootPath) {
        init(cpeDescriptor, compileRootPath, srcClassRootPath);
    }

    public void init(String... settings) {
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
        try {
            currentCpeDesc = UIMAFramework.getXMLParser().parseCpeDescription(new XMLInputSource(cpeDescriptor));
            removeFailedDescriptors(new File(cpeDescriptor));
            File rootFolder = new File(currentCpeDesc.getSourceUrl().getFile()).getParentFile();
            CpeCollectionReader[] collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();
            for (CpeCollectionReader collReader : collRdrs) {
                File descFile = new File(rootFolder + System.getProperty("file.separator") + collReader.getDescriptor().getImport().getLocation());
                CollectionReaderDescription crd = UIMAFramework.getXMLParser().parseCollectionReaderDescription(new XMLInputSource(descFile));
                TypeSystemDescription typeSystem = crd.getCollectionReaderMetaData().getTypeSystem();
                typeSystem.resolveImports();
                typeSystems.add(typeSystem);
            }
            CpeCasProcessor[] cpeCasProcessors = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
            for (CpeCasProcessor casProcessor : cpeCasProcessors) {

                File descFile = new File(rootFolder + System.getProperty("file.separator") + casProcessor.getCpeComponentDescriptor().getImport().getLocation());
                AnalysisEngineDescription aed = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(new XMLInputSource(descFile));
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

    }

    public void setLogger(UIMALogger logger) {
        this.logger = logger;
    }


    public void addConceptType(String conceptName) {
        dynamicTypeGenerator.addConceptType(conceptName);
    }

    public void addConceptType(String conceptName, Collection<String> featureNames) {
        dynamicTypeGenerator.addConceptType(conceptName, featureNames);
    }

    public void addConceptType(String conceptName, String superTypeName) {
        dynamicTypeGenerator.addConceptType(conceptName,superTypeName);
    }

    public void addConceptType(String conceptName, Collection<String> featureNames, String superTypeName) {
        dynamicTypeGenerator.addConceptType(conceptName, featureNames,superTypeName);
    }

    public void reInitTypeSystem(String customTypeDescXml, String srcPath) {
        dynamicTypeGenerator.reInitTypeSystem(customTypeDescXml, srcPath);
    }

    public void reInitTypeSystem(String customTypeDescXml) {
        dynamicTypeGenerator.reInitTypeSystem(customTypeDescXml, this.srcClassRootPath);
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

        String tabName;
        if (aeSpecifier instanceof AnalysisEngineDescription) {
            AnalysisEngineDescription aeDescription = (AnalysisEngineDescription) aeSpecifier;
            ResourceMetaData md = aeDescription.getMetaData();
            tabName = md.getName();
        } else {
            tabName = f.getName();
        }


        cpeCasProc.setName(tabName);

        return true;
    }

    private File urlToFile(URL url) throws URISyntaxException {
        return new File(UriUtils.quote(url));
    }

    protected void displayError(String msg) {
        System.err.println(msg);
    }

}
