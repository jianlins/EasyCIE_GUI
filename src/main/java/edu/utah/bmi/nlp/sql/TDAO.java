package edu.utah.bmi.nlp.sql;

import java.io.File;

public class TDAO extends EDAO {

    private boolean closed = true;
    private static TDAO tdao = null;

    public TDAO(File configFile) {
        closed = true;
    }

    public TDAO() {
        closed = false;
    }


    public static TDAO getInstance(File configFile) {
        if (tdao == null)
            return new TDAO(configFile);
        else
            return tdao;
    }

    public void initiateTableFromTemplate(String templateName, String tableName, boolean overwrite) {
        System.out.println(String.format("Create table %s from template %s.", templateName, tableName));
    }

    public Object insertRecord(String tableName, RecordRow recordRow) {
        System.out.println(String.format("Insert record\n\t %s \n\tto table %s.", recordRow, tableName));
        return 1;
    }

    public boolean isClosed() {
        return closed;
    }
}
