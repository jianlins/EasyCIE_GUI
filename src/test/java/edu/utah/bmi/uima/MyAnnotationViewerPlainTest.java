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

package edu.utah.bmi.uima;

import edu.utah.bmi.nlp.uima.MyAnnotationViewerMain;
import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import org.junit.Test;

import javax.swing.*;

/**
 * Created by u0876964 on 11/7/16.
 */
public class MyAnnotationViewerPlainTest {

    @Test
    public void test() {
        JFrame frame = new MyAnnotationViewerPlain(new String[]{"Test", "data/output/xmi",
                "desc/type/pipeline_v1.xml"});
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
//        MyAnnotationViewerMain.main(new String[]{".","desc/type/All_Types.xml"});
    }
    @Test
    public void test2(){
        MyAnnotationViewerPlain.main(new String[]{"Test", "../EasyCIE_GUI/data/output/xmi",
                "../EasyCIE_GUI/desc/type/pipeline_v5.xml"});
    }


    public static void main(String[]args){
        JFrame frame = new MyAnnotationViewerPlain(new String[]{"Test", "data/output/xmi",
                "desc/type/pipeline_v1.xml"});
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}