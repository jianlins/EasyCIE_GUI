package edu.utah.bmi.nlp.sql;

import org.junit.Test;

import java.io.File;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

public class EDAOTest {
    @Test
    public void test(){
        EDAO edao=new EDAO(new File("conf/sqliteconfig.xml"));
        edao.initiateTableFromTemplate("DOCUMENTS_TABLE","test1",false);
        EDAO edao2=new EDAO(new File("conf/sqliteconfig.xml"));
        edao2.queryRecordsFromPstmt("test1",0);

    }

    @Test
    public void testMapKeys(){
        LinkedHashMap<String, String>map=new LinkedHashMap<>();
        map.put("red","red");
        map.put("blue","blue");
        map.put("green","green");
        System.out.println(map.keySet());
    }


}