
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

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.uima.loggers.GUILogger;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import org.apache.uima.Constants;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.analysis_engine.impl.AnalysisEngineDescription_impl;
import org.apache.uima.analysis_engine.metadata.FixedFlow;
import org.apache.uima.analysis_engine.metadata.impl.FixedFlow_impl;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.fit.cpe.CpeBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.InvalidXMLException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.*;

import static edu.utah.bmi.nlp.core.DeterminantValueSet.defaultSuperTypeName;

/**
 * @author Jianlin Shi
 * Created on 7/9/16.
 */
public class AdaptableUIMACPERunner {
	protected CollectionReaderDescription reader;
	protected ArrayList<AnalysisEngineDescription> analysisEngineDescriptors = new ArrayList<>();
	protected DynamicTypeGenerator dynamicTypeGenerator;
	protected HashMap<String, TypeDefinition> conceptTypeDefinitions = new HashMap<>();
	protected CollectionProcessingEngine engine;
	protected String srcClassRootPath = null;
	protected UIMALogger logger;


	public AdaptableUIMACPERunner() {

	}

	public AdaptableUIMACPERunner(String typeDescriptorURI) {
		dynamicTypeGenerator = new DynamicTypeGenerator(typeDescriptorURI);
	}


	public AdaptableUIMACPERunner(String typeDescriptorURI, String compileRootPath) {
		dynamicTypeGenerator = new DynamicTypeGenerator(typeDescriptorURI);
		dynamicTypeGenerator.setCompiledRootPath(compileRootPath);
	}

	public AdaptableUIMACPERunner(String typeDescriptorURI, String compileRootPath, String srcClassRootPath) {
		dynamicTypeGenerator = new DynamicTypeGenerator(typeDescriptorURI);
		dynamicTypeGenerator.setCompiledRootPath(compileRootPath);
		this.srcClassRootPath = srcClassRootPath;
	}

	public void setTask(GUITask task) {
		if (logger instanceof GUILogger) {
			((GUILogger) logger).setTask(task);
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


	public void setCollectionReader(Class readerClass, Object[] configurations) {
		try {
			reader = CollectionReaderFactory.createReaderDescription(readerClass, dynamicTypeGenerator.getTypeSystemDescription(), configurations);
		} catch (ResourceInitializationException e) {
			e.printStackTrace();
		}
	}

	public void addAnalysisEngine(Class analysisEngineClass, Object[] configurations) {
		try {
			analysisEngineDescriptors.add(AnalysisEngineFactory.createEngineDescription(analysisEngineClass, configurations));
		} catch (ResourceInitializationException e) {
			e.printStackTrace();
		}
	}

	public void removeAnalysisEngine(int index) {
		analysisEngineDescriptors.remove(index);
	}

	public void addAnalysisEngine(AnalysisEngineDescription analysisEngineDescription) {
		analysisEngineDescriptors.add(analysisEngineDescription);
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


	public void run() {

		try {
			AnalysisEngineDescription aaeDesc = createEngineDescription(analysisEngineDescriptors);
			CpeBuilder builder = new CpeBuilder();

			builder.setReader(reader);
			builder.setAnalysisEngine(aaeDesc);
			builder.setMaxProcessingUnitThreadCount(Runtime.getRuntime().availableProcessors() - 1);
			SimpleStatusCallbackListenerImpl status = new SimpleStatusCallbackListenerImpl(logger);
			builder.setMaxProcessingUnitThreadCount(0);
			engine = builder.createCpe(status);

			status.setCollectionProcessingEngine(engine);
			engine.process();
			try {
				synchronized (status) {
					while (status.isProcessing) {
						status.wait();
					}
					System.out.println("Pipeline complete");
				}
			} catch (InterruptedException var9) {
				var9.printStackTrace();
			}

			if (status.exceptions.size() > 0) {
				throw new AnalysisEngineProcessException(status.exceptions.get(0));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (CpeDescriptorException e) {
			e.printStackTrace();
		} catch (AnalysisEngineProcessException e) {
			e.printStackTrace();
		} catch (InvalidXMLException e) {
			e.printStackTrace();
		} catch (ResourceInitializationException e) {
			e.printStackTrace();
		}
	}

	public AnalysisEngineDescription createEngineDescription(
			List<AnalysisEngineDescription> analysisEngineDescriptions)
			throws ResourceInitializationException {

		// create the descriptor and set configuration parameters
		AnalysisEngineDescription desc = new AnalysisEngineDescription_impl();
		desc.setFrameworkImplementation(Constants.JAVA_FRAMEWORK_NAME);
		desc.setPrimitive(false);

		// if any of the aggregated analysis engines does not allow multiple
		// deployment, then the
		// aggregate engine may also not be multiply deployed
		boolean allowMultipleDeploy = true;
		for (AnalysisEngineDescription d : analysisEngineDescriptions) {
			allowMultipleDeploy &= d.getAnalysisEngineMetaData().getOperationalProperties()
					.isMultipleDeploymentAllowed();
		}
		desc.getAnalysisEngineMetaData().getOperationalProperties()
				.setMultipleDeploymentAllowed(allowMultipleDeploy);

		List<String> flowNames = new ArrayList<String>();

		for (int i = 0; i < analysisEngineDescriptions.size(); i++) {
			AnalysisEngineDescription aed = analysisEngineDescriptions.get(i);
			String componentName = aed.getImplementationName() + "-" + i;
			desc.getDelegateAnalysisEngineSpecifiersWithImports().put(componentName, aed);
			flowNames.add(componentName);
		}


		FixedFlow fixedFlow = new FixedFlow_impl();
		fixedFlow.setFixedFlow(flowNames.toArray(new String[flowNames.size()]));
		desc.getAnalysisEngineMetaData().setFlowConstraints(fixedFlow);

		return desc;
	}
}
