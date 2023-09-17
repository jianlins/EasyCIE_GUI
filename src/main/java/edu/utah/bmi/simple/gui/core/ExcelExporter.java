package edu.utah.bmi.simple.gui.core;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.entry.StaticVariables;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.*;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class ExcelExporter {
    private EDAO dao;
    private String sql;
    private static HashMap<String, Font> typeFonts = new HashMap<>();
    private static HashMap<String, String> coreColumns = new HashMap<>();
    private GUITask task;
    private int total = -1;

    public ExcelExporter(EDAO dao, String sql) {
        this.dao = dao;
        this.sql = sql;
        this.task = null;
        this.total = -1;
    }

    public ExcelExporter(EDAO dao, String sql, int total, GUITask task) {
        this.dao = dao;
        this.sql = sql;
        this.task = task;
        this.total = total;
    }

    public void export(File exportFile) {
        if (total > -1)
            task.updateGUIMessage("Start exporting...");
        RecordRowIterator recordIter = dao.queryRecords(sql);
        LinkedHashMap<String, String> columnInfo = recordIter.getColumninfo().getColumnInfo();
        if (total > -1)
            task.updateGUIMessage("Initiating spreadsheet...");
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = createTitleRow(workbook, columnInfo);

        writeDataRows(workbook, sheet, recordIter, columnInfo);
        if (total > -1)
            task.updateGUIMessage("Write to disk...");
        try {            //Write the workbook in file system
            FileOutputStream out = new FileOutputStream(exportFile);
            workbook.write(out);
            out.close();
            System.out.println(exportFile + " is written successfully on disk.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (total > -1)
            task.updateGUIMessage("Export complete.");

    }

    private XSSFSheet createTitleRow(XSSFWorkbook workbook, LinkedHashMap<String, String> columnInfo) {
        XSSFSheet sheet = workbook.createSheet("Sheet1");
        XSSFRow titleRow = sheet.createRow(1);
        int cellNum = 0;
        XSSFCellStyle cs = workbook.createCellStyle();
        Font f = workbook.createFont();
        f.setBold(true);
        cs.setFont(f);
        titleRow.setRowStyle(cs);


        XSSFCell cell = titleRow.createCell(cellNum++);
        cell.setCellValue("Comments");

        cell = titleRow.createCell(cellNum++);
        cell.setCellValue("Agree/Disagree");

        cell = titleRow.createCell(cellNum++);
        cell.setCellValue("Doc_Conclusion");

        for (String columnName : columnInfo.keySet()) {
            String upperCaseColumnName = columnName.toUpperCase();
            switch (upperCaseColumnName) {
                case "BEGIN":
                case "END":
                    coreColumns.put(upperCaseColumnName, columnName);
                    break;
                case "SNIPPET":
                    sheet.setColumnWidth(cellNum, 60 * 256);
                case "TYPE":
                    coreColumns.put(upperCaseColumnName, columnName);
                default:
                    cell = titleRow.createCell(cellNum++);
                    cell.setCellValue(upperCaseColumnName);
                    break;
            }
        }
        return sheet;
    }

    private void writeDataRows(XSSFWorkbook workbook, XSSFSheet sheet, RecordRowIterator recordIter, LinkedHashMap<String, String> columnInfo) {
//        skip title row
        int rowNum = 2;
        int progress=0;
        HashMap<String, ArrayList<RecordRow>> snippets = new HashMap<>();
        LinkedHashMap<String, ArrayList<RecordRow>> docConclusions = new LinkedHashMap<>();
        while (recordIter.hasNext()) {
            RecordRow recordRow = recordIter.next();
            String type = recordRow.getStrByColumnName(coreColumns.get("TYPE")).toUpperCase();
            String docName = recordRow.getStrByColumnName("DOC_NAME");
            String docText=recordRow.getStrByColumnName("TEXT");

            if (!docConclusions.containsKey(docName)) {
                docConclusions.put(docName, new ArrayList<>());
                snippets.put(docName, new ArrayList<>());
            }



            if (type.endsWith("_DOC"))
                docConclusions.get(docName).add(recordRow);
            else
                snippets.get(docName).add(recordRow);
            if (total > -1)
                task.updateGUIProgress(progress++, total);

        }

        XSSFRow dataRow = sheet.createRow(rowNum++);
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);

        System.out.println(docConclusions.size());

        for (Map.Entry<String, ArrayList<RecordRow>> entry : docConclusions.entrySet()) {
//        write doc conclusion row
            String docName = entry.getKey();
            int cellNum = 1;
            XSSFCell cell = dataRow.createCell(cellNum++);
            cell.setCellValue(1);
            cell.setCellStyle(style);
            cell = dataRow.createCell(cellNum++);
            StringBuilder sb = new StringBuilder();

            for (RecordRow docRow : entry.getValue())
                sb.append(docRow.getStrByColumnName("TYPE") + ",");
            cell.setCellValue(sb.substring(0, sb.length() - 1));
            cell.setCellStyle(style);

            RecordRow docRow = entry.getValue().get(0);
            cell = dataRow.createCell(cellNum++);
            cell.setCellValue(docRow.getStrByColumnName("ID"));
            cell.setCellStyle(style);

            cell = dataRow.createCell(cellNum++);
            cell.setCellValue(docName);
            cell.setCellStyle(style);

            if(snippets.get(docName).size()==0){
                dataRow = sheet.createRow(rowNum++);
            }


            for (RecordRow snippetRecord : snippets.get(docName)) {
                int snippetCellNum = cellNum;
                int begin = Integer.parseInt(snippetRecord.getStrByColumnName(coreColumns.get("BEGIN")));
                int end = Integer.parseInt(snippetRecord.getStrByColumnName(coreColumns.get("END")));
                String type = snippetRecord.getStrByColumnName(coreColumns.get("TYPE"));
                cell = dataRow.createCell(snippetCellNum++);
                cell.setCellValue(type);

                Font colorFont = getFont(workbook, type);
                String snippet = snippetRecord.getStrByColumnName("SNIPPET");
                snippet = snippet.replaceAll("[^\\{punc}\\w]", " ");
                XSSFRichTextString string = new XSSFRichTextString(snippet);
                string.applyFont(begin, end, colorFont);
                cell = dataRow.createCell(snippetCellNum++);
                cell.setCellValue(string);

                String value = snippetRecord.getStrByColumnName("FEATURES");
                cell = dataRow.createCell(snippetCellNum++);
                cell.setCellValue(value);

                dataRow = sheet.createRow(rowNum++);
            }

        }

    }

    private Font getFont(XSSFWorkbook workbook, String type) {
        if (typeFonts.containsKey(type))
            return typeFonts.get(type);
        String color = StaticVariables.pickColor(type);
        XSSFFont font = workbook.createFont();
        if (!color.startsWith("#"))
            color = "#" + color;
        System.out.println(color);
        font.setColor(new XSSFColor(Color.decode(color)));
        font.setBold(true);
        typeFonts.put(type, font);
        return font;
    }
}
