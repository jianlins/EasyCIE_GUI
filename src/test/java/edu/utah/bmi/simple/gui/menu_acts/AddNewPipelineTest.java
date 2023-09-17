package edu.utah.bmi.simple.gui.menu_acts;

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class AddNewPipelineTest {

    @Test
    public void getResourceInputStream() {
        InputStream ins = new AddNewPipeline(new String[]{}).getResourceInputStream("/demo_configurations/00_Section_Detector.tsv");
        System.out.println(ins==null);
    }

    @Test
    public void getResourceText() {
        String text = new AddNewPipeline(new String[]{}).getResourceText("/demo_configurations/00_Section_Detector.tsv");
        System.out.println(text);
    }
}