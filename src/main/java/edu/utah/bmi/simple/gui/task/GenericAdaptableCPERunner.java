package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorRunner;
import edu.utah.bmi.nlp.uima.DynamicTypeGenerator;
import edu.utah.bmi.nlp.uima.SimpleStatusCallbackListenerImpl;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.impl.metadata.cpe.CpeDescriptorFactory;
import org.apache.uima.collection.metadata.CasProcessorConfigurationParameterSettings;
import org.apache.uima.collection.metadata.CpeCasProcessor;
import org.apache.uima.collection.metadata.CpeCollectionReader;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.resource.metadata.ConfigurationParameterSettings;
import org.apache.uima.resource.metadata.NameValuePair;
import org.apache.uima.resource.metadata.ProcessingResourceMetaData;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Jianlin Shi
 * Created on 2/26/17.
 */
@Deprecated
public class GenericAdaptableCPERunner extends AdaptableCPEDescriptorRunner {
    public boolean debug = false;

    public GenericAdaptableCPERunner(String cpeDescriptor) {
        super(cpeDescriptor, "g1", new ConsoleLogger());
    }

    public GenericAdaptableCPERunner(TasksFX tasks) {
        initiate(tasks, "db");
    }

    public GenericAdaptableCPERunner(TasksFX tasks, String paras) {
        if (paras == null || paras.length() == 0)
            initiate(tasks, "db");
        initiate(tasks, paras);
    }

    public void setLogger(UIMALogger logger) {
        this.logger = logger;
    }

    protected void initiate(TasksFX tasks, String pipelineName) {
        TaskFX otherPiplinesConfig = tasks.getTask("otherPiplines");
        LinkedHashMap<String, SettingAb> customizedSettings = otherPiplinesConfig.getChildSettings(pipelineName);
        String cpeDescriptor = otherPiplinesConfig.getValue(pipelineName + "/" + ConfigKeys.cpeDescriptor);
        String annotator = tasks.getTask(ConfigKeys.maintask).getValue(ConfigKeys.annotator);
        this.externalConfigMap = new LinkedHashMap<>();
        this.annotator = annotator;
        setUIMALogger(logger);
        initCpe(cpeDescriptor, annotator, "");

    }


    private void updateParameters(String casProcName, CasProcessorConfigurationParameterSettings aSettings,
                                  ProcessingResourceMetaData processingResourceMetaData, LinkedHashMap<String, SettingAb> customizedSettings) {
        int casProcNameLength = casProcName.length() + 1;
        HashMap<String, String> modifiedPara = new HashMap<>();
        for (Map.Entry<String, SettingAb> entry : customizedSettings.entrySet()) {
            String paraName = entry.getKey();
            if (!paraName.startsWith(casProcName))
                continue;
            paraName = paraName.substring(casProcNameLength);
            String paraValue = entry.getValue().getSettingValue();
            if (paraValue != null && paraValue.trim().length() > 0) {
                modifiedPara.put(paraName, paraValue);
            }
        }
        ConfigurationParameterSettings descriptorSettings = processingResourceMetaData.getConfigurationParameterSettings();
        for (NameValuePair para : descriptorSettings.getParameterSettings()) {
            String name = para.getName();
            Object value = para.getValue();
            if (modifiedPara.containsKey(name)) {
                value = modifiedPara.get(name);
            }
            aSettings.setParameterValue(name, value);
        }
    }


    protected void debugging(String msg) {
        if (debug)
            System.out.println(msg);
    }

    protected boolean reportable(LinkedHashMap<String, SettingAb> customizedSettings) {
        if (customizedSettings.containsKey(ConfigKeys.report)) {
            String value = customizedSettings.get(ConfigKeys.report).getSettingValue().trim();
            return value.length() > 0 && !value.toLowerCase().startsWith("f") && !value.toLowerCase().startsWith("0");
        }
        return false;
    }


}
