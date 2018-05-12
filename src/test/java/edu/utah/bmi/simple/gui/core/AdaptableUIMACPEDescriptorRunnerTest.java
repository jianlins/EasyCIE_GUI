package edu.utah.bmi.simple.gui.core;

import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.metadata.CpeCasProcessor;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.junit.Test;

import java.util.LinkedHashMap;

public class AdaptableUIMACPEDescriptorRunnerTest {
    @Test
    public void test() throws CpeDescriptorException {
        AdaptableUIMACPEDescriptorRunner runner = new AdaptableUIMACPEDescriptorRunner("desc/cpe/Preannotator.xml");
        CollectionProcessingEngine mCPE = runner.mCPE;
        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
            System.out.println(cpeCasProcessor.getName());

        }

    }

    @Test
    public void test2() throws CpeDescriptorException {
        LinkedHashMap<String, String> configs = new LinkedHashMap<>();
        configs.put("FastNER", "@fastner\n" +
                "@splitter:|\n" +
                "very concept|ConceptA\n" +
                "tee|ConceptB");
        AdaptableUIMACPEDescriptorRunner runner = new AdaptableUIMACPEDescriptorRunner("desc/cpe/Preannotator.xml", configs);
        CollectionProcessingEngine mCPE = runner.mCPE;
        CpeCasProcessor[] cpeCasProcessors = runner.currentCpeDesc.getCpeCasProcessors().getAllCpeCasProcessors();
        for (CpeCasProcessor cpeCasProcessor : cpeCasProcessors) {
            System.out.println(cpeCasProcessor.getName());
        }
    }


    public static void main(String[] args) throws CpeDescriptorException {
        org.apache.uima.tools.cpm.CpmFrame.main(new String[]{});
    }
}