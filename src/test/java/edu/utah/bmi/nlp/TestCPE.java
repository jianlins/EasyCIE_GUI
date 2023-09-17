package edu.utah.bmi.nlp;

import org.apache.uima.examples.cpe.SimpleRunCPE;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Jianlin Shi
 *         Created on 2/24/17.
 */
public class TestCPE {

    public static void main(String[]args) {

//        org.apache.uima.tools.cpm.CpmFrame.main(args);
        try {
            SimpleRunCPE.main(new String[]{"desc/cpe/Preannotator.xml"});
        } catch (Exception e) {
            e.printStackTrace();
        }
//        org.apache.uima.tools.AnnotationViewerMain.main(args);
//        Class c=Class.forName("org.apache.uima.tools.cpm.CpmFrame");
//        Method m=c.getMethod("main",String[].class);
//        m.invoke(null,(Object)new String[]{});
//        AdaptableCPEDescriptorRunner runner=new AdaptableCPEDescriptorRunner("desc/cpe/PreannotatorCPE.xml");
//        runner.setUIMALogger(new ConsoleLogger());
//        runner.run();
    }

}
