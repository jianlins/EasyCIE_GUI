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

public class ExcelExporter2 {
    private EDAO dao;
    private String sql;
    private static HashMap<String, Font> typeFonts = new HashMap<>();
    private static HashMap<String, Integer> coreColumnPositions = new HashMap<>();
    private LinkedHashSet<String> docLevelColumns = new LinkedHashSet<>();
    private HashSet<String> exportedDocs = new HashSet<>();
    private GUITask task;
    private int total = -1;
    private boolean exportText = false;
    private File rptDir;

    public ExcelExporter2(EDAO dao, String sql) {
        init(dao, sql, -1, null, false);
    }

    public ExcelExporter2(EDAO dao, String sql, int total, GUITask task) {
        init(dao, sql, total, task, true);
    }

    public ExcelExporter2(EDAO dao, String sql, int total, GUITask task, boolean exportText) {
        init(dao, sql, total, task, exportText);
    }


    private void init(EDAO dao, String sql, int total, GUITask task, boolean exportText) {
        this.dao = dao;
        this.sql = sql;
        this.task = task;
        this.total = total;
        this.exportText = exportText;
        docLevelColumns.add("Comments");
        docLevelColumns.add("Agree/Disagree");
        docLevelColumns.add("DOC_CONCLUSION");
        docLevelColumns.add("PAT_ID");
        docLevelColumns.add("DOC_NAME");
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
        for (String docLevelColumn : docLevelColumns) {
            coreColumnPositions.put(docLevelColumn, cellNum);
            cell = titleRow.createCell(cellNum++);
            cell.setCellValue(docLevelColumn);

        }

        for (String columnName : columnInfo.keySet()) {
            if (!coreColumnPositions.containsKey(columnName))
                coreColumnPositions.put(columnName, coreColumnPositions.size());
            String upperCaseColumnName = columnName.toUpperCase();
            switch (upperCaseColumnName) {
                case "BEGIN":
                case "END":
                    break;
                case "SNIPPET":
                    sheet.setColumnWidth(coreColumnPositions.get(columnName), 60 * 256);
                default:

                    if (!docLevelColumns.contains(columnName)) {
                        cell = titleRow.createCell(coreColumnPositions.get(columnName));
                        cell.setCellValue(upperCaseColumnName);
                    }
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
        HashMap<String, ArrayList<RecordRow>> snippets = new HashMap<>();
        LinkedHashMap<String, ArrayList<RecordRow>> docConclusions = new LinkedHashMap<>();
        while (recordIter.hasNext()) {
            RecordRow recordRow = recordIter.next();
            String type = recordRow.getStrByColumnName("TYPE");
            String docName = recordRow.getStrByColumnName("DOC_NAME");

            if (!docConclusions.containsKey(docName)) {
                docConclusions.put(docName, new ArrayList<>());
                snippets.put(docName, new ArrayList<>());
                if (total > -1) {
                    task.updateGUIProgress(progress++, total);
                    if (progress > total)
                        break;
                }
            }

            if (type.toUpperCase().endsWith("_DOC")) {
                recordRow.addCell("DOC_CONCLUSION", type);
                recordRow.addCell("Agree/Disagree", 'y');
                docConclusions.get(docName).add(recordRow);
            } else
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

        for (Map.Entry<String, ArrayList<RecordRow>> entry : docConclusions.entrySet()) {
//        write doc conclusion row

            String docName = entry.getKey();
            if (exportText) {
                link = (XSSFHyperlink) createHelper
                        .createHyperlink(HyperlinkType.URL);
                link.setAddress("./docs/" + docName + ".txt");

            }
            for (RecordRow docRow : entry.getValue()) {
                createEmptyRow(dataRow);

                for (String docColumn : docLevelColumns) {
                    if (docRow.getStrByColumnName(docColumn) != null) {
                        int pos = coreColumnPositions.get(docColumn);
//                        System.out.println(pos);
                        XSSFCell cell = dataRow.getCell(pos);
                        cell.setCellValue(docRow.getStrByColumnName(docColumn));
                        cell.setCellStyle(style);
                        if (exportText && docColumn.equals("DOC_NAME")) {
                            cell.setHyperlink(link);
                            exportRpt(docName, docRow.getStrByColumnName("TEXT"));
                        }
                    }
                }
            }


            if (snippets.get(docName).size() == 0) {
                dataRow = sheet.createRow(rowNum++);
                createEmptyRow(dataRow);
                continue;
            }


            for (RecordRow snippetRecord : snippets.get(docName)) {
                String type = "";
                for (String columnName : columnInfo.keySet()) {
                    switch (columnName) {
                        case "BEGIN":
                        case "END":
                            break;
                        case "TEXT":
                            break;
                        case "SNIPPET":
                            int begin = Integer.parseInt(snippetRecord.getStrByColumnName("BEGIN"));
                            int end = Integer.parseInt(snippetRecord.getStrByColumnName("END"));
                            Font colorFont = getFont(workbook, type);
                            String snippet = snippetRecord.getStrByColumnName("SNIPPET");
                            if (snippet==null || snippet.equals("null"))
                                break;
                            snippet = snippet.replaceAll("[^\\.,\\-;\\(\\)'/\\w]", " ");
                            XSSFRichTextString string = new XSSFRichTextString(snippet);
                            string.applyFont(begin, end, colorFont);
                            XSSFCell cell = dataRow.getCell(coreColumnPositions.get(columnName));
                            cell.setCellValue(string);
                            break;
                        case "TYPE":
                            type = snippetRecord.getStrByColumnName(columnName);
                        default:
                            if (!docLevelColumns.contains(columnName)) {
                                String value = snippetRecord.getStrByColumnName(columnName);
                                cell = dataRow.getCell(coreColumnPositions.get(columnName));
                                if (cell != null)
                                    cell.setCellValue(value.trim());
                                else
                                    System.out.println(columnName);

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
        for (int i = 0; i < coreColumnPositions.size(); i++)
            dataRow.createCell(i + 1);
    }

    private void exportRpt(String rptId, String text) {
        try {
            FileUtils.writeStringToFile(new File(rptDir, rptId + ".txt"), text, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
