package edu.utah.bmi.simple.gui.core;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.entry.StaticVariables;
import org.apache.commons.io.FileUtils;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.*;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class ExcelExporter3 {
    private EDAO dao;
    private String sql;
    private static HashMap<String, Font> typeFonts = new HashMap<>();
    private static HashMap<String, Integer> columnPositions = new HashMap<>();
    private HashSet<String> exportedDocs = new HashSet<>();
    private GUITask task;
    private int total = -1;
    private boolean exportText = false;
    private File rptDir;
    private ArrayList<String> extraColumns = new ArrayList<>();
    private String inputTable;

    public ExcelExporter3(EDAO dao, String sql) {
        init(dao, sql, -1, null, false, null);
    }

    public ExcelExporter3(EDAO dao, String sql, int total, GUITask task) {
        init(dao, sql, total, task, false, null);
    }

    public ExcelExporter3(EDAO dao, String sql, int total, GUITask task, boolean exportText, String inputTable) {
        init(dao, sql, total, task, exportText, inputTable);
    }


    private void init(EDAO dao, String sql, int total, GUITask task, boolean exportText, String inputTable) {
        this.dao = dao;
        this.sql = sql;
        this.task = task;
        this.total = total;
        this.exportText = exportText;
        this.inputTable = inputTable;
        extraColumns.add("Comments");
        extraColumns.add("Agree/Disagree");
    }

    public void setExportText(boolean exportText) {
        this.exportText = exportText;
    }

    public void export(File exportFile) {
        if (total > -1)
            task.updateGUIMessage("Start exporting...");
        RecordRowIterator recordIter = dao.queryRecords(sql);
        LinkedHashMap<String, String> columnInfo = recordIter.getColumninfo().getColumnInfo();
        if (total > -1)
            task.updateGUIMessage("Initiating spreadsheet...");
        if (exportText) {
            rptDir = new File(exportFile.getParentFile(), "docs");
            if (!rptDir.exists())
                try {
                    FileUtils.forceMkdir(rptDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = createTitleRow(workbook, columnInfo);

        writeDataRows(workbook, sheet, recordIter, columnInfo);
        if (exportText)
            exportRpts();
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
        XSSFCell cell;
        for (String columnName : extraColumns) {
            if (!columnPositions.containsKey(columnName))
                columnPositions.put(columnName, columnPositions.size());
            cell = titleRow.createCell(columnPositions.get(columnName));
            cell.setCellValue(columnName.toUpperCase());
        }

        for (String columnName : columnInfo.keySet()) {
            if (!columnPositions.containsKey(columnName))
                columnPositions.put(columnName, columnPositions.size());
            String upperCaseColumnName = columnName.toUpperCase();
            switch (upperCaseColumnName) {
                case "BEGIN":
                case "END":
                case "TEXT":
                case "SNIPPET_BEGIN":
                    break;
                case "SNIPPET":
                    sheet.setColumnWidth(columnPositions.get(columnName), 60 * 256);
                default:
                    cell = titleRow.createCell(columnPositions.get(columnName));
                    cell.setCellValue(upperCaseColumnName);
                    break;
            }
        }
        titleRow.cellIterator().forEachRemaining(e -> {
            System.out.println(e);
        });
        return sheet;
    }

    private void writeDataRows(XSSFWorkbook workbook, XSSFSheet sheet,
                               RecordRowIterator recordIter,
                               LinkedHashMap<String, String> columnInfo) {
//        skip title row
        int rowNum = 2;
        int progress = 0;
        LinkedHashMap<String, ArrayList<RecordRow>> snippets = new LinkedHashMap<>();
        while (recordIter.hasNext()) {
            RecordRow recordRow = recordIter.next();
            String docName = recordRow.getStrByColumnName("DOC_NAME");


            if (total > -1) {
                task.updateGUIProgress(progress++, total);
                if (progress > total)
                    break;
            }
            if (!snippets.containsKey(docName))
                snippets.put(docName, new ArrayList<>());
            snippets.get(docName).add(recordRow);

        }

        XSSFRow dataRow = sheet.createRow(rowNum++);
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);

        CreationHelper createHelper = workbook.getCreationHelper();
        XSSFHyperlink link = null;

//        System.out.println(docConclusions.size());

        for (Map.Entry<String, ArrayList<RecordRow>> entry : snippets.entrySet()) {
//        write doc conclusion row

            String docName = entry.getKey();

            createEmptyRow(dataRow);

            for (RecordRow snippetRecord : snippets.get(docName)) {
                String type = "";
                if (exportText) {
                    exportedDocs.add(docName);
                }
                for (String columnName : columnInfo.keySet()) {
                    switch (columnName) {
                        case "BEGIN":
                        case "END":
                        case "SNIPPET_BEGIN":
                        case "TEXT":
                            break;
                        case "SNIPPET":
                            int begin = Integer.parseInt(snippetRecord.getStrByColumnName("BEGIN"));
                            int end = Integer.parseInt(snippetRecord.getStrByColumnName("END"));
                            Font colorFont = getFont(workbook, type);
                            String snippet = snippetRecord.getStrByColumnName("SNIPPET");
                            if (snippet == null || snippet.equals("null"))
                                break;
                            snippet = snippet.replaceAll("[^\\.,\\-;\\(\\)'/\\w]", " ");
                            XSSFRichTextString string = new XSSFRichTextString(snippet);
                            string.applyFont(begin, end, colorFont);
                            XSSFCell cell = dataRow.getCell(columnPositions.get(columnName));
                            cell.setCellValue(string);
                            break;
                        default:
                            String value = snippetRecord.getStrByColumnName(columnName);
                            if (value == null || value.equals("null"))
                                value = "";
                            cell = dataRow.getCell(columnPositions.get(columnName));
                            if (cell != null)
                                cell.setCellValue(value.trim());
                            else
                                System.out.println(columnName);
                            if (exportText) {
                                link = (XSSFHyperlink) createHelper
                                        .createHyperlink(HyperlinkType.URL);
                                link.setAddress("./docs/" + docName + ".txt");
                                if (columnName.equals("DOC_NAME") || columnName.equals("DOC_ID")) {
                                    cell.setHyperlink(link);
                                }
                            }

                            break;
                    }
                }
                dataRow = sheet.createRow(rowNum++);
                createEmptyRow(dataRow);
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
//        System.out.println(color);
        font.setColor(new XSSFColor(Color.decode(color)));
        font.setBold(true);
        typeFonts.put(type, font);
        return font;
    }

    private void createEmptyRow(XSSFRow dataRow) {
        for (int i = 0; i < columnPositions.size(); i++)
            dataRow.createCell(i + 1);
    }

    private void exportRpt(String rptId, String text) {
        try {
            FileUtils.writeStringToFile(new File(rptDir, rptId + ".txt"), text, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void exportRpts() {
        dao.initiateTableFromTemplate("DOCUMENTS_TABLE", inputTable, false);
        for (String docName : exportedDocs) {
            RecordRowIterator iter = dao.queryRecordsFromPstmt(inputTable, docName);
            if (iter.hasNext()) {
                RecordRow recordRow = iter.next();
                exportRpt(docName, recordRow.getStrByColumnName("TEXT"));
            }
        }
    }

}
