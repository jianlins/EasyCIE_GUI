package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.core.CommonFunc;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ImportExcelData extends Import {
    public static Logger logger = IOUtil.getLogger(ImportExcelData.class);
    private static char excel = 'x';
    private String sheetName, annotator;
    private int docNameColumnPos, txtColumnPos, dateColumnPos, startRowNum, conclusionColumnPos;
    private File inputFile;

    public ImportExcelData(TasksFX tasks) {
        initiate(tasks);
    }

    protected void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("import");
        TaskFX settingConfig = tasks.getTask("settings");
        String documentPath = config.getValue(ConfigKeys.importExcel);
        sheetName = config.getValue(ConfigKeys.sheetName);
        if (sheetName.length() == 0)
            sheetName = "Sheet1";
        docNameColumnPos = Integer.parseInt(config.getValue(ConfigKeys.docNameColumn, "1"));
        txtColumnPos = Integer.parseInt(config.getValue(ConfigKeys.docTextColumn, "2"));
        dateColumnPos = Integer.parseInt(config.getValue(ConfigKeys.docDateColumn, "-1"));
        conclusionColumnPos = Integer.parseInt(config.getValue(ConfigKeys.conclusionColumn, "-1"));
        startRowNum = Integer.parseInt(config.getValue(ConfigKeys.startRowNum, "-1"));


        corpusType = excel;
        inputPath = documentPath;
        importDocTable = settingConfig.getValue(ConfigKeys.inputTableName);
        referenceTable = settingConfig.getValue(ConfigKeys.referenceTable);
        inputFile = new File(documentPath);
        annotator = inputFile.getName();
        annotator = annotator.substring(0, annotator.lastIndexOf("."));
        if (!checkFileExist(inputFile, ConfigKeys.importDir))
            return;


        datasetId = settingConfig.getValue(ConfigKeys.datasetId);
        dbConfigFile = settingConfig.getValue(ConfigKeys.readDBConfigFileName);
        overwrite = settingConfig.getValue(ConfigKeys.overwrite).charAt(0) == 't'
                || settingConfig.getValue(ConfigKeys.overwrite).charAt(0) == 'T'
                || settingConfig.getValue(ConfigKeys.overwrite).charAt(0) == '1';
        File dbconfig = new File(dbConfigFile);
        if (CommonFunc.checkFileExist(dbconfig, "dbconfig")) {
            initSuccess = false;
            return;
        }
        dao = EDAO.getInstance(dbconfig, true, false);
        initSuccess = true;
    }

    @Override
    protected Object call() throws Exception {
        if (initSuccess) {
            updateGUIMessage("Start import....");
            importExcel(inputFile, datasetId, overwrite, sheetName,
                    docNameColumnPos, txtColumnPos, dateColumnPos, conclusionColumnPos, startRowNum);
            updateGUIMessage("Import complete");
        }
        dao.endBatchInsert();
        dao.close();
        updateGUIProgress(1, 1);
        return null;
    }


    protected boolean checkFileExist(File inputFile, String paraName) {
        if (!inputFile.exists()) {
            initSuccess = false;
            popDialog("Note", "The input Excel File \"" + inputFile.getAbsolutePath() + "\" does not exist",
                    "Please double check your settings of \"" + paraName + "\"");
            initSuccess = false;
            return false;
        }
        return true;
    }

    public static Date parseDateString(String dateString) {
        Date utilDate = new Date(System.currentTimeMillis());
        try {
            utilDate = new org.pojava.datetime.DateTime(dateString).toDate();
        } catch (Exception e) {
            logger.fine("Illegal date string: " + dateString);
            return null;
        }
        return utilDate;
    }


    protected void importExcel(File inputFile, String datasetId, boolean overWrite,
                               String sheetName, int docNameColumnPos, int txtColumnPos,
                               int dateColumnPos, int conclusionColumnPos, int startRowNum) throws IOException {
        dao.initiateTableFromTemplate("DOCUMENTS_TABLE", importDocTable, overWrite);
        if (conclusionColumnPos != -1) {
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", referenceTable, overWrite);
        }
        HashMap<String, Integer> duplicateNames = new HashMap<>();

        int rowCounter = 0, counter = 1;
        FileInputStream  fileIn = new FileInputStream(inputFile);
        Workbook workbook =new XSSFWorkbook(fileIn);
        Sheet sheet = workbook.getSheet(sheetName);
        int total = sheet.getLastRowNum();

        for (Row row : sheet) {
            rowCounter++;
            if (rowCounter >= startRowNum) {
                row.getCell(docNameColumnPos - 1).setCellType(CellType.STRING);
                String docName = row.getCell(docNameColumnPos - 1).getStringCellValue();
                if (duplicateNames.containsKey(docName)) {
                    duplicateNames.put(docName, duplicateNames.get(docName) + 1);
                    docName += "_" + duplicateNames.get(docName);
                } else {
                    duplicateNames.put(docName, 0);
                }
                RecordRow recordRow = new RecordRow().addCell("DATASET_ID", datasetId)
                        .addCell("DOC_NAME", docName)
                        .addCell("TEXT", row.getCell(txtColumnPos - 1));
                Cell cell;
                if (dateColumnPos != -1) {
                    cell = row.getCell(dateColumnPos - 1);
                    if (cell.getCellTypeEnum() == CellType.STRING) {
                        recordRow.addCell("DATE", parseDateString(cell.getStringCellValue()));
                    } else if (cell.getCellTypeEnum() == CellType.NUMERIC) {
                        recordRow.addCell("DATE", cell.getDateCellValue());
                    }
                }
                dao.insertRecord(importDocTable, recordRow);
                if (conclusionColumnPos != -1) {
                    dao.insertRecord(referenceTable, new RecordRow()
                            .addCell("DOC_NAME", docName)
                            .addCell("TYPE", row.getCell(conclusionColumnPos - 1))
                    );
                }
                counter++;
                updateGUIProgress(rowCounter, total);
                rowCounter++;
            }
        }

        logger.info("Totally " + counter + (counter > 1 ? " documents have" : " document has") + " been imported successfully.");
    }
}
