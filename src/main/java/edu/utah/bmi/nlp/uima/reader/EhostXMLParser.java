package edu.utah.bmi.nlp.uima.reader;

import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.type.system.Concept;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.uima.common.UIMATypeFunctions;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by Jianlin Shi on 9/29/16.
 */
public class EhostXMLParser {
    public static void getTypes(HashMap<String, HashSet<String>> types, String xmlContent) {
        InputStream inputStream = new ByteArrayInputStream(xmlContent.getBytes());
        HashSet<String> attributes = new HashSet();
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
                            attributes.add("Annotator");
                            break;

                        case "spannedText":
                            xmlEvent = xmlEventReader.nextEvent();
                            attributes.add("Text");
                            break;
                        case "mentionSlot":
                            Attribute idAttr = startElement.getAttributeByName(new QName("id"));
                            if (idAttr != null) {
                                attributes.add(idAttr.getValue());
                            }
                            break;
                        case "mentionClass":
                            idAttr = startElement.getAttributeByName(new QName("id"));
                            if (idAttr != null) {
                                typeName = idAttr.getValue();
                                if (!types.containsKey(typeName)) {
                                    types.put(typeName, (HashSet<String>) attributes.clone());
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

    public static void getTypesFromSchema(LinkedHashMap<String, TypeDefinition> typeDefinitions, String xmlContent) {
        LinkedHashMap<String, HashSet<String>> types = new LinkedHashMap<>();
        getTypesFromSchema(types, xmlContent);
        for (Map.Entry<String, HashSet<String>> entry : types.entrySet()) {
            if (entry.getKey().toLowerCase().endsWith("_doc")) {
                typeDefinitions.put(entry.getKey(), new TypeDefinition(entry.getKey(), Doc_Base.class.getCanonicalName(), entry.getValue()));
            } else
                typeDefinitions.put(entry.getKey(), new TypeDefinition(entry.getKey(), Concept.class.getCanonicalName(), entry.getValue()));
        }
    }

    public static void getTypesFromSchema(HashMap<String, HashSet<String>> types, String xmlContent) {
        InputStream inputStream = new ByteArrayInputStream(xmlContent.getBytes());
        HashSet<String> attributes = new HashSet();
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        LinkedHashSet<Method> methods = new LinkedHashSet<>();
        UIMATypeFunctions.getMethods(Concept.class, methods);
        HashSet<String> commonAttribs = new HashSet<>();
        for (Method method : methods) {
            commonAttribs.add(method.getName().substring(3));
        }
        try {
            XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(inputStream);
            String featureName = "", typeName = "";
            boolean isClassName = false;
            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.nextEvent();
                if (xmlEvent.isStartElement()) {
                    StartElement startElement = xmlEvent.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
                        case "classDef":
                            if (typeName.length() > 0) {
                                if (!types.containsKey(typeName)) {
                                    types.put(typeName, (HashSet<String>) attributes.clone());
                                    attributes.clear();
                                }
                            }
                            isClassName = true;
                            break;

                        case "Name":
                            xmlEvent = xmlEventReader.nextEvent();
                            String name = "";
                            if (xmlEvent.isCharacters()) {
                                name = ((Characters) xmlEvent).getData();
                            }
                            if (isClassName) {
                                typeName = name;
                            } else if (!commonAttribs.contains(name)) {
//                              ignore common methods/features
                                attributes.add(name);
                            }
                            break;
                        case "attributeDef":
                            isClassName = false;
                            break;
                    }

                }
            }
            types.put(typeName, (HashSet<String>) attributes.clone());
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }


}
