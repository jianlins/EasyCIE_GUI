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

import edu.utah.bmi.nlp.compiler.MemoryClassLoader;
import edu.utah.bmi.nlp.compiler.MemoryJavaCompiler;
import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.uima.common.AnnotationOper;
import edu.utah.bmi.nlp.uima.jcas.JcasGen;
import org.apache.commons.io.FileUtils;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.metadata.FeatureDescription;
import org.apache.uima.resource.metadata.TypeDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.resource.metadata.impl.FeatureDescription_impl;
import org.apache.uima.util.CasCreationUtils;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.utah.bmi.nlp.core.DeterminantValueSet.checkNameSpace;
import static edu.utah.bmi.nlp.core.DeterminantValueSet.defaultSuperTypeName;

/**
 * @author Jianlin Shi on 9/28/16.
 */
public class DynamicTypeGenerator {
    private static Logger logger = IOUtil.getLogger(DynamicTypeGenerator.class);
    protected TypeSystemDescription typeSystemDescription;
    protected HashSet<String> compiledTypes = new HashSet<>();
    protected HashSet<String> toBeCompiledTypes;
    public File compiledRootPath = new File("classes");
    private MemoryJavaCompiler compiler = new MemoryJavaCompiler(compiledRootPath);
    private HashMap<String, HashSet<String>> superTypefeatureNamesCache = new HashMap<>();
    @Deprecated
    public boolean debug = false;


    public DynamicTypeGenerator(String... descriptorNames) {
        initTypeSystem(descriptorNames);
    }


    public void setCompiledRootPath(File compiledRootPath) {
        this.compiledRootPath = compiledRootPath;
        if (!compiledRootPath.exists()) {
            try {
                FileUtils.forceMkdir(compiledRootPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        compiler = new MemoryJavaCompiler(compiledRootPath);
    }

    public void setCompiledRootPath(String path) {
        setCompiledRootPath(new File(path));
    }

    public DynamicTypeGenerator(TypeSystemDescription typeSystemDescription) {
        try {
            JCas jCas = CasCreationUtils.createCas(typeSystemDescription, null, null).getJCas();
            this.typeSystemDescription = typeSystemDescription;
            toBeCompiledTypes = new HashSet<>();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public DynamicTypeGenerator(ArrayList<TypeSystemDescription> typeSystemDescriptions) {
        try {
            TypeSystemDescription mergedTypeSystem = CasCreationUtils.mergeTypeSystems(typeSystemDescriptions);
            this.typeSystemDescription = mergedTypeSystem;
            JCas jCas = CasCreationUtils.createCas(mergedTypeSystem, null, null).getJCas();
            toBeCompiledTypes = new HashSet<>();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void initTypeSystem(String... descriptorNames) {
        try {
            compiler.addClassPath();
            boolean nameIsPath = false;
            for (int i = 0; i < descriptorNames.length; i++) {
                if (descriptorNames[i].endsWith(".xml")) {
                    nameIsPath = true;
                    descriptorNames[i] = new File(descriptorNames[i]).getAbsolutePath();
                }
            }
            if (nameIsPath) {
                typeSystemDescription = TypeSystemDescriptionFactory.createTypeSystemDescriptionFromPath(descriptorNames);
            } else {
                typeSystemDescription = TypeSystemDescriptionFactory.createTypeSystemDescription(descriptorNames);
            }
            JCas jCas = CasCreationUtils.createCas(typeSystemDescription, null, null).getJCas();
            toBeCompiledTypes = new HashSet<>();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public TypeSystemDescription getTypeSystemDescription() {
        return typeSystemDescription;
    }


    public TreeSet<String> getImportedTypeNames() {
        TreeSet<String> typeNames = new TreeSet<>();
        for (TypeDescription typeDescription : typeSystemDescription.getTypes()) {
            typeNames.add(typeDescription.getName());
        }
        return typeNames;
    }


    public void addConceptTypes(Collection<TypeDefinition> typeDefinitions) {
        for (TypeDefinition typeDefinition : typeDefinitions) {
            addConceptType(typeDefinition);
        }
    }

    public void addConceptType(TypeDefinition typeDefinition) {
        addConceptType(typeDefinition.getFullTypeName(), typeDefinition.getNewFeatureNames(), typeDefinition.getFullSuperTypeName(), typeDefinition.featureTypes);
    }

    /**
     * If a new concept name is used in rule file, but not described in xml type descriptor, this method should be called to
     * generate the new concept class by extending the general Concept class
     *
     * @param conceptName   new concept type name
     * @param superTypeName the parent type name where the new concept type derives from
     */
    public void addConceptType(String conceptName, String superTypeName) {
        conceptName = checkNameSpace(conceptName);
        addConceptType(conceptName, new ArrayList<>(), superTypeName, new HashMap<>());

    }

    public void addConceptType(String conceptName) {
        addConceptType(conceptName, defaultSuperTypeName);
    }

    public void addConceptType(String conceptName, Collection<String> featureNames) {
        addConceptType(conceptName, featureNames, defaultSuperTypeName, new HashMap<>());
    }

    public void addConceptType(String conceptName, Collection<String> featureNames, String superTypeName) {
        addConceptType(conceptName, featureNames, superTypeName, new HashMap<>());
    }

    public void addConceptType(String conceptName, Collection<String> featureNames, String superTypeName, HashMap<String, String> featureTypes) {
        conceptName = checkNameSpace(conceptName);
        superTypeName = checkNameSpace(superTypeName);
        if (compiledTypes.contains(conceptName))
            return;
        boolean loaded = classLoaded(conceptName);

        if (!toBeCompiledTypes.contains(conceptName)) {
            typeSystemDescription.addType(conceptName, "an automatic generated concept type", superTypeName);
            if (featureNames != null && featureNames.size() > 0) {
                featureNames = filterFeatureNames(superTypeName, featureNames);
                TypeDescription type = typeSystemDescription.getType(conceptName);
                FeatureDescription[] aFeatures = new FeatureDescription[featureNames.size()];
                int i = 0;
                for (String featureName : featureNames) {
//                type.addFeature(featureName, "Automatic generated feature", "uima.cas.String");
                    if (featureTypes.containsKey(featureName) && featureTypes.get(featureName) != null && featureTypes.get(featureName).trim().length() > 0) {
                        String featureType = featureTypes.get(featureName);
                        if (featureType.indexOf(":") == -1)
                            aFeatures[i] = new FeatureDescription_impl(featureName, "Automatic generated Type", featureType);
                        else {
                            String[] typeElementType = featureType.split(":");
                            featureType = typeElementType[0].trim();
                            String elementType = typeElementType[1].trim();
                            aFeatures[i] = new FeatureDescription_impl(featureName, "Automatic generated Type", featureType, elementType, true);
                        }
                    } else {
                        aFeatures[i] = new FeatureDescription_impl(featureName, "Automatic generated Type", "uima.cas.String");
                    }
                    i++;
                }
                type.setFeatures(aFeatures);
//                System.out.println(type);
            }
//           although loaded types do not need to recompile, but still need to be added to type xml
            if (loaded) {
                compiledTypes.add(conceptName);
            } else {
                toBeCompiledTypes.add(conceptName);
            }
        }
    }

    /**
     * Filter out redundant feature definitions (has been defined in its super type)
     *
     * @param superTypeName super type canonical name
     * @param featureNames  a list of feature names
     * @return filtered feature names
     */
    private Collection<String> filterFeatureNames(String superTypeName, Collection<String> featureNames) {
        ArrayList<String> filteredFeatureNames = new ArrayList<>();
        if (!superTypefeatureNamesCache.containsKey(superTypeName)) {
            superTypefeatureNamesCache.put(superTypeName, new HashSet<>());
            Class<? extends Annotation> evidenceTypeClass = AnnotationOper.getTypeClass(superTypeName);
            if (evidenceTypeClass != null) {
                for (Method method : evidenceTypeClass.getMethods()) {
                    if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                        String featureName = method.getName().substring(3);
                        superTypefeatureNamesCache.get(superTypeName).add(featureName);
                    }
                }
            } else {
                logger.warning(superTypeName + " has not been initiated or loaded into memory.");
            }
        }
        for (String featureName : featureNames) {
            if (!superTypefeatureNamesCache.get(superTypeName).contains(featureName))
                filteredFeatureNames.add(featureName);
        }
        return filteredFeatureNames;
    }


    public void reInitTypeSystem(String customTypeDescXml, String srcPath) {
        superTypefeatureNamesCache.clear();
        if (srcPath != null) {
            File srcDir = new File(srcPath);
            if (!srcDir.exists()) {
                try {
                    FileUtils.forceMkdir(srcDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        setCompiledRootPath(compiledRootPath);


        if (toBeCompiledTypes.size() > 0) {
            HashMap<String, String> classSrcs = genTypeDescriptorNClasses(customTypeDescXml, srcPath, false);
            for (Map.Entry<String, String> entry : classSrcs.entrySet()) {
//                if (!classLoaded(checkNameSpace(entry.getKey())))
                if (!compiledTypes.contains(DeterminantValueSet.checkNameSpace(entry.getKey()))) {
                    compiler.addClassSrc(entry.getKey(), entry.getValue());
                    compiledTypes.add(DeterminantValueSet.checkNameSpace(entry.getKey()));
                }
            }
            try {
                Map<String, Class> classes = compiler.compileBatchToSystem();
                if (logger.isLoggable(Level.FINE))
                    for (String type : classes.keySet()) {
                        logger.fine(type + " loaded " + classLoaded(type));
                    }
            } catch (Exception e) {
                logger.warning("Fail to compile new types.");
            }
            toBeCompiledTypes.clear();
////        new org.apache.uima.tools.jcasgen.Jg().main(new String[]{"-jcasgeninput", "desc/type/customized.xml", "-jcasgenoutput", "src/test/java/"});
//            for (String conceptName : toBeCompiledTypes) {
//                if (!classLoaded(conceptName)) {
//                    ArrayList<JavaSourceFromString> compilationUnits = new ArrayList<>();
////                    loadTypeClass2(conceptName, srcPath);
////                    System.out.println(conceptName + " generated and loaded: " + classLoaded(conceptName));
////                    loadTypeClass2(conceptName + "_Type", srcPath);
//                    addCompileClassToSchedule(compilationUnits, conceptName, readJavaContent(conceptName, srcPath));
//                    addCompileClassToSchedule(compilationUnits, conceptName + "_Type", readJavaContent(conceptName + "_Type", srcPath));
//                    writeCompiledClass(compilationUnits, compiledRootPath);
//                }
//            }
        } else {
            writeTypeDescriptorXML(customTypeDescXml);
        }
    }

    public void writeTypeDescriptorXML(String customTypeDescXml) {
        try {
            typeSystemDescription.toXML(new FileWriter(customTypeDescXml));
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, String> genTypeDescriptorNClasses(String customTypeDescXml, String srcPath, boolean classOnly) {
        if (!customTypeDescXml.toLowerCase().endsWith(".xml"))
            customTypeDescXml += ".xml";
        if (!classOnly)
            writeTypeDescriptorXML(customTypeDescXml);
        HashMap<String, String> classes;
        if (srcPath == null)
            classes = new JcasGen().main(customTypeDescXml, toBeCompiledTypes);
        else
            classes = new JcasGen().main(customTypeDescXml, toBeCompiledTypes, srcPath);
        return classes;
    }


    protected boolean classLoaded(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}