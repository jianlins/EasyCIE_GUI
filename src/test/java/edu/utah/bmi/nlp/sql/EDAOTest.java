package edu.utah.bmi.nlp.sql;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class EDAOTest {
    @Test
    public void test(){
        EDAO edao=new EDAO(new File("conf/sqliteconfig.xml"));
        edao.initiateTableFromTemplate("DOCUMENTS_TABLE","test1",false);
        EDAO edao2=new EDAO(new File("conf/sqliteconfig.xml"));
        edao2.queryRecordsFromPstmt("test1",0);

    }

}