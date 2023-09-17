package edu.utah.bmi.simple.gui.menu_acts;

import edu.utah.bmi.simple.gui.task.ConfigKeys;
import org.apache.commons.io.FilenameUtils;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.*;
import org.apache.uima.resource.metadata.ConfigurationParameter;
import org.apache.uima.resource.metadata.ConfigurationParameterDeclarations;
import org.apache.uima.resource.metadata.ConfigurationParameterSettings;
import org.apache.uima.util.FileUtils;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

/**
 * Read from cpe descriptor, generate properties strings that contains all the parameters and corresponding
 * values for each reader and aes.
 */
public class GenDefaultXmlConfigs {
    private final File cpeDesc;
    private File rootFolder;
    private LinkedHashMap<String, String> configurations = new LinkedHashMap<>();
    private LinkedHashMap<String, String> descriptions = new LinkedHashMap<>();
    private char style = 'f';
    private String cpeDescBaseName;

    public static void main(String[] args) {
        switch (args.length) {
            case 0:
                System.out.println("This is the program to generate default configurations from CPE descriptor.\n" +
                        "It takes 1~2 paramters: \n" +
                        "   1. The location of the cpe descriptor file.\n" +
                        "   2. The style of output ('full','f' or not specified--all paramters; " +
                        "'concise' or 'c'--only parameters without default values;'verbose' or 'v' " +
                        "all parameters and corresponding descriptions).");
                break;
            case 1:
                System.out.println(new GenDefaultXmlConfigs(args[0], "f").getConfigurations());
                break;
            default:
                System.out.println(new GenDefaultXmlConfigs(args[0], args[1]).getConfigurations());
                break;

        }
    }

    public GenDefaultXmlConfigs(String... args) {
        cpeDesc = new File(args[0]);
        if (!cpeDesc.exists())
            return;
        configurations.clear();
        style = args[1].toLowerCase().charAt(0);
        parse(args[0]);
    }

    private void parse(String cpeDescriptorPath) {
        try {
            CpeDescription currentCpeDesc = UIMAFramework.getXMLParser().parseCpeDescription(new XMLInputSource(cpeDescriptorPath));
            rootFolder = new File(currentCpeDesc.getSourceUrl().getFile()).getParentFile();
            parseReaders(currentCpeDesc);
            parseAEs(currentCpeDesc);
        } catch (InvalidXMLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CpeDescriptorException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return full or concise (only include the ones do not have any default values) configurations
     *
     * @return configuration string in properties format
     */
    public String getConfigurations() {
        Document document = DocumentHelper.createDocument();
        cpeDescBaseName = FilenameUtils.getBaseName(cpeDesc.getName());
        Element pipe = document.addElement(cpeDescBaseName);
        Element pipeLineSetting = pipe.addElement("pipeLineSetting");
        Element cpeDescriptor = pipeLineSetting.addElement("CpeDescriptor");
        cpeDescriptor.addAttribute("memo", "location of CPE descriptor");
        cpeDescriptor.addAttribute("doubleClick", "edu.utah.bmi.simple.gui.doubleclick.ConfigFileChooser");
        cpeDescriptor.addAttribute("openClick", "edu.utah.bmi.simple.gui.doubleclick.RunCpmFrame");
        cpeDescriptor.setText(ConfigKeys.getRelativePath(new File("").getAbsolutePath(), cpeDesc.getAbsolutePath()));
        String lastAeName = "";
        Element currentElement = null;
        for (String configName : configurations.keySet()) {
            String value = configurations.get(configName);
            if (configName.startsWith("SQL_Text_Reader"))
                continue;
            String[] configNames = configName.split("/");
            String aeName = configNames[0];
            String paraName = configNames[1];
            if (!lastAeName.equalsIgnoreCase(aeName)) {
                lastAeName=aeName;
                currentElement = pipeLineSetting.addElement(aeName);
            }
            if (style == 'f' || style == 'v' || value.trim().length() == 0) {
                String memo = "";
                if (style == 'v' && descriptions.containsKey(configName)) {
                    String description = descriptions.get(configName);
                    if (description != null && description.trim().length() > 0)
                        memo = descriptions.get(configName);
                }
                Element para = currentElement.addElement(paraName);
                para.setText(value);
                para.addAttribute("memo", memo);
                if (paraName.equals("RuleFileOrStr")) {
                    para.addAttribute("doubleClick", "edu.utah.bmi.simple.gui.doubleclick.ConfigFileChooser");
                    para.addAttribute("openClick", "edu.utah.bmi.simple.gui.doubleclick.RunCpmFrame");
                }
            }
        }
        Element annotators = pipe.addElement("annotators");
        annotators.setText("v0");
        annotators.addAttribute("memo", "Mark current annotations with annotator: ");
        Element executes = pipe.addElement("executes");
//         <RunEasyCIE memo="Run Preannotator using current rules">edu.utah.bmi.simple.gui.task.RunCPEDescriptorTask</RunEasyCIE>
//      <ViewOutputInDB memo="View the output in database">edu.utah.bmi.simple.gui.task.ViewOutputDB</ViewOutputInDB>
        Element runEasyCIE = executes.addElement("Run" + Character.toUpperCase(cpeDescBaseName.charAt(0)) + cpeDescBaseName.substring(1));
        runEasyCIE.addAttribute("memo", "run Pipeline "+cpeDescBaseName);
        runEasyCIE.setText("edu.utah.bmi.simple.gui.task.RunCPEDescriptorTask");
        Element viewOutputInDB=executes.addElement("ViewOutputInDB");
        viewOutputInDB.addAttribute("memo","View the output in database");
        viewOutputInDB.setText("edu.utah.bmi.simple.gui.task.ViewOutputDB");
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setExpandEmptyElements(true);
        format.setSuppressDeclaration(true);
        StringWriter stringWriter = new StringWriter();
        XMLWriter writer = new XMLWriter(stringWriter, format);
        try {
            writer.write(document);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringWriter.toString();
    }


    private void parseAEs(CpeDescription currentCpeDesc) throws
            CpeDescriptorException, IOException, InvalidXMLException {
        CpeCasProcessor[] cpeCasProcessorsArray = currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (int i = 0; i < cpeCasProcessorsArray.length; i++) {
            CpeCasProcessor cp = cpeCasProcessorsArray[i];
            String processorName = cp.getName();
            String descriptorPath = cp.getCpeComponentDescriptor().getImport().getLocation();
            File descFile = new File(descriptorPath);
            if (!descFile.exists())
                descFile = new File(rootFolder, descriptorPath);
            AnalysisEngineDescription aed = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(new XMLInputSource(descFile));
            ConfigurationParameterSettings aeparas = aed.getMetaData().getConfigurationParameterSettings();
            for (org.apache.uima.resource.metadata.NameValuePair para : aeparas.getParameterSettings()) {
                configurations.put(processorName + "/" + para.getName(), para.getValue() + "");
            }
            ConfigurationParameterDeclarations declares = aed.getMetaData().getConfigurationParameterDeclarations();
            for (ConfigurationParameter para : declares.getConfigurationParameters()) {
                descriptions.put(processorName + "/" + para.getName(), para.getDescription());
            }

            if (cp.getConfigurationParameterSettings() != null)
                for (NameValuePair para : cp.getConfigurationParameterSettings().getParameterSettings()) {
                    configurations.put(processorName + "/" + para.getName(), para.getValue() + "");
                }
        }
    }

    private void parseReaders(CpeDescription currentCpeDesc) throws
            CpeDescriptorException, IOException, InvalidXMLException {
        CpeCollectionReader[] collRdrs = currentCpeDesc.getAllCollectionCollectionReaders();
        for (CpeCollectionReader collReader : collRdrs) {
            CpeComponentDescriptor readerName = collReader.getDescriptor();
            String descriptorPath = collReader.getDescriptor().getImport().getLocation();
            File descFile = new File(descriptorPath);
            if (!descFile.exists())
                descFile = new File(rootFolder, descriptorPath);
            CollectionReaderDescription crd = UIMAFramework.getXMLParser().parseCollectionReaderDescription(new XMLInputSource(descFile));
            String name = crd.getCollectionReaderMetaData().getName();

            ConfigurationParameterDeclarations declares = crd.getMetaData().getConfigurationParameterDeclarations();
            for (ConfigurationParameter para : declares.getConfigurationParameters()) {
                descriptions.put(name + "/" + para.getName(), para.getDescription());
            }
            ConfigurationParameterSettings readerParas = crd.getMetaData().getConfigurationParameterSettings();
//          read all default values from reader descriptor
            for (org.apache.uima.resource.metadata.NameValuePair para : readerParas.getParameterSettings()) {
                configurations.put(name + "/" + para.getName(), para.getValue() + "");
            }
//           read all default values from cpe descriptor, will overwrite the readers' descriptors' configurations
            for (NameValuePair para : collReader.getConfigurationParameterSettings().getParameterSettings()) {
                configurations.put(name + "/" + para.getName(), para.getValue() + "");
            }
        }
    }
}
