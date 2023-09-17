package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.uima.loggers.GUILogger;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.fit.cpe.CpeBuilder;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

@Deprecated
public class AdaptableUIMACPETaskJCasRunner extends AdaptableUIMACPERunner {

    protected GUITask task;

    protected AnalysisEngine aggregatedAE;



    public AdaptableUIMACPETaskJCasRunner(String customTypeDescriptor) {
        super(customTypeDescriptor);
    }

    public AdaptableUIMACPETaskJCasRunner(String customTypeDescriptor, String compileRootPath) {
        super(customTypeDescriptor, compileRootPath);
    }

    public AdaptableUIMACPETaskJCasRunner() {
    }

    public ArrayList<AnalysisEngineDescription> getAEDesriptors() {
        return this.analysisEngineDescriptors;
    }

    public AnalysisEngine genAEs() {
        AggregateBuilder builder = new AggregateBuilder();
        for (AnalysisEngineDescription aes : this.analysisEngineDescriptors) {
            builder.add(aes);
        }
        try {
            aggregatedAE = builder.createAggregate();
        } catch (ResourceInitializationException e) {
            e.printStackTrace();
        }
        return aggregatedAE;
    }

    public void process(JCas jCas) {
        try {
            aggregatedAE.process(jCas);
        } catch (AnalysisEngineProcessException e) {
            e.printStackTrace();
        }
    }

    public AnalysisEngineDescription addAnalysisEngineFromDescriptor(String descriptorFile, Object[] configurations) {
        AnalysisEngineDescription aed = null;
        try {
            aed = AnalysisEngineFactory.createEngineDescriptionFromPath(descriptorFile, configurations);
            analysisEngineDescriptors.add(aed);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidXMLException e) {
            e.printStackTrace();
        }
        return aed;
    }

    public void run() {

        try {
            AnalysisEngineDescription aaeDesc = createEngineDescription(analysisEngineDescriptors);
            CpeBuilder builder = new CpeBuilder();

            builder.setReader(reader);
            builder.setAnalysisEngine(aaeDesc);
            builder.setMaxProcessingUnitThreadCount(Runtime.getRuntime().availableProcessors() - 1);
            if (logger instanceof GUILogger) {
                ((GUILogger) logger).setTask(task);
            }
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

    public void setTask(GUITask task) {
        this.task = task;
    }


}
