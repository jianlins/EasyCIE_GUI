package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import org.apache.uima.tools.cpm.MyCpmFrame;

public class TestCPEMain {
    public static void main(String[]args){
//        MyCpmFrame.main(new String[]{"desc/cpe/Preannotator.xml"});
//        org.apache.uima.tools.cpm.CpmFrame.main(new String[]{});
        GenericAdaptableCPERunner runner=new GenericAdaptableCPERunner("desc/cpe/Preannotator.xml");
        runner.setLogger(new ConsoleLogger());
        runner.run();
    }
}
