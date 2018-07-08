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

package edu.utah.bmi.nlp.uima.writer;

import javafx.scene.paint.Color;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Write out eHOST configuration xml file
 * Created by Jianlin Shi on 9/26/16.
 */
public class EhostConfigurator {
    private static int seed=1234;
    private static FileOutputStream outputStream;
    public static void setUp(File outputFile, HashMap<String, LinkedHashSet<String>> typeMethods) {
        XMLStreamWriter xtw = initXml(outputFile);
        try {
            for (Map.Entry<String, LinkedHashSet<String>> type : typeMethods.entrySet()) {
                writeType(xtw, type);
            }
            xtw.writeEndElement();
            xtw.writeEndElement();
            xtw.flush();
            outputStream.close();
            xtw.close();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public static XMLStreamWriter initXml(File outputFile) {
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        XMLStreamWriter xtw = null;
        try {
            outputStream=new FileOutputStream(outputFile);
            xtw = xof.createXMLStreamWriter(outputStream,"UTF-8");
            //		System.out.println(outputPath			+ sourcefileName + ".knowtator.xml");
            xtw.writeStartDocument("UTF-8", "1.0");
            xtw.writeStartElement("eHOST_Project_Configure");
            writeEle(xtw, "Handling_Text_Database", "false");
            writeEle(xtw, "OracleFunction_Enabled", "false");
            writeEle(xtw, "AttributeEditor_PopUp_Enabled", "false");
            writeEle(xtw, "OracleFunction", "false");
            writeEle(xtw, "AnnotationBuilder_Using_ExactSpan", "false");
            writeEle(xtw, "OracleFunction_Using_WholeWord", "true");
            writeEle(xtw, "GraphicAnnotationPath_Enabled", "true");
            writeEle(xtw, "Diff_Indicator_Enabled", "true");
            writeEle(xtw, "Diff_Indicator_Check_CrossSpan", "true");
            writeEle(xtw, "Diff_Indicator_Check_Overlaps", "false");
            writeEle(xtw, "StopWords_Enabled", "false");
            writeEle(xtw, "Output_VerifySuggestions", "false");
            writeEle(xtw, "Pre_Defined_Dictionary_DifferentWeight", "false");
            xtw.writeStartElement("PreAnnotated_Dictionaries");
            xtw.writeAttribute("Owner", "NLP_Assistant");
            xtw.writeEndElement();
            writeEle(xtw, "attributeDefs", "");
            writeEle(xtw, "Relationship_Rules", "");
            xtw.writeStartElement("classDefs");
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return xtw;
    }

    public static void writeEle(XMLStreamWriter xtw, String elementName, String elementValue) throws XMLStreamException {
        xtw.writeStartElement(elementName);
        xtw.writeCharacters(elementValue);
        xtw.writeEndElement();
    }

    public static void writeType(XMLStreamWriter xtw, Map.Entry<String, LinkedHashSet<String>>type) throws XMLStreamException {
        xtw.writeStartElement("classDef");
        writeEle(xtw, "Name", type.getKey());
        int[] colors = getRandomBeautifulColors();
        writeEle(xtw, "RGB_R", colors[0] + "");
        writeEle(xtw, "RGB_G", colors[1] + "");
        writeEle(xtw, "RGB_B", colors[2] + "");
        writeEle(xtw, "InHerit_Public_Attributes", "true");
        for (String attr : type.getValue()) {
            xtw.writeStartElement("attributeDef");
            writeEle(xtw, "Name", attr);
            writeEle(xtw, "isCode", "false");
            xtw.writeEndElement();
        }
        writeEle(xtw, "Source", "eHOST");
        xtw.writeEndElement();
    }


    public static int[] getRandomBeautifulColors() {
        String colorString=Integer.toHexString((int)Math.floor(Math.random()*16777215));
        if(colorString.length()==5) {
            colorString = "0" + colorString;
//            System.out.println(colorString);
        }

        Color color =  Color.valueOf(colorString);
        if(color.getSaturation()>0.78){
            color=Color.hsb(color.getHue(),0.78,color.getBrightness());
        }
        if(color.getBrightness()<0.43){
            color=Color.hsb(color.getHue(),color.getSaturation(),0.43);
        }
//        System.out.println(color.getRed());
//        System.out.println(color.getGreen());
//        System.out.println(color.getBlue());

        int red = (int) (color.getRed() * 255);
        int green = (int) (color.getGreen() * 255);
        int blue = (int) (color.getBlue() * 255);
        return new int[]{red, green, blue};
    }


}

