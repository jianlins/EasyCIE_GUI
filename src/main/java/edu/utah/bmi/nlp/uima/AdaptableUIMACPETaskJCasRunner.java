package edu.utah.bmi.nlp.uima;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.ArrayList;

public class AdaptableUIMACPETaskJCasRunner extends AdaptableUIMACPETaskRunner {

    protected AnalysisEngine aggregatedAE;

    public AdaptableUIMACPETaskJCasRunner(String customTypeDescriptor, String s) {
        super(customTypeDescriptor,s);
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

    public void process(JCas jCas){
        try {
            aggregatedAE.process(jCas);
        } catch (AnalysisEngineProcessException e) {
            e.printStackTrace();
        }
    }
}
