package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.easycie.reader.BratReader;
import edu.utah.bmi.nlp.easycie.reader.EhostReader;
import edu.utah.bmi.simple.gui.core.CommonFunc;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

import org.apache.commons.io.comparator.NameFileComparator;
import org.joda.time.DateTime;


/**
 * Created by Jianlin Shi on 9/23/16.
 */
public class Import extends GUITask {
    public static final char ehost = 'e', xmi = 'x', brat = 'b', txt = 't', unknown = 'u';
    protected DAO dao;
    protected boolean print = true;

    protected String inputPath, dbConfigFile, importTable, overWriteAnnotatorName, datasetId, rushRule;
    protected File inputDir;
    protected boolean overwrite = false, initSuccess = false;
    protected String includeTypes;
    protected char corpusType;
    protected RunEasyCIE runner;

    public Import(TasksFX tasks, String importType) {
        initiate(tasks, importType);
    }

    private void initiate(TasksFX tasks, String importType) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("import");
        TaskFX settingConfig = tasks.getTask("settings");
        String documentDir = config.getValue(ConfigKeys.importDir);
        String includeFileTypes = config.getValue(ConfigKeys.includeFileTypes);


        String annotationDir = config.getValue(ConfigKeys.annotationDir);
        String includeAnnotationTypes = config.getValue(ConfigKeys.includeAnnotationTypes);
        overWriteAnnotatorName = config.getValue(ConfigKeys.overWriteAnnotatorName);
        String enableSentenceSegValue = config.getValue(ConfigKeys.enableSentenceSnippet);
        boolean enableSentenceSeg = enableSentenceSegValue.length() > 0 ? (config.getValue(ConfigKeys.enableSentenceSnippet).charAt(0) == 't'
                || config.getValue(ConfigKeys.enableSentenceSnippet).charAt(0) == 'T'
                || config.getValue(ConfigKeys.enableSentenceSnippet).charAt(0) == '1') : false;


        if (importType.equals(ConfigKeys.paraTxtType)) {
            corpusType = txt;
            inputPath = documentDir;
            importTable = settingConfig.getValue(ConfigKeys.inputTableName);
            inputDir = new File(documentDir);
            if (!checkDirExist(inputDir, documentDir, ConfigKeys.importDir))
                return;
            includeTypes = includeFileTypes.replaceAll("\\s+", "");
        } else {
            inputDir = new File(annotationDir);
            inputPath = annotationDir;
            if (!checkDirExist(inputDir, annotationDir, ConfigKeys.annotationDir))
                return;
            importTable = settingConfig.getValue(ConfigKeys.referenceTable);
            corpusType = checkCorpusType(inputDir, includeFileTypes);
            includeTypes = includeAnnotationTypes.replaceAll("\\s+", "");
        }
        if (CommonFunc.checkDirExist(inputDir, "Note:|Sorry:| ")) {
            initSuccess = false;
            return;
        }

        datasetId = settingConfig.getValue(ConfigKeys.datasetId);
        dbConfigFile = settingConfig.getValue(ConfigKeys.readDBConfigFile);
        overwrite = settingConfig.getValue(ConfigKeys.overwrite).charAt(0) == 't'
                || settingConfig.getValue(ConfigKeys.overwrite).charAt(0) == 'T'
                || settingConfig.getValue(ConfigKeys.overwrite).charAt(0) == '1';
        rushRule = (enableSentenceSeg) ? settingConfig.getValue(ConfigKeys.rushRule) : "";
        File dbconfig = new File(dbConfigFile);
        if (CommonFunc.checkFileExist(dbconfig, "dbconfig")) {
            initSuccess = false;
            return;
        }
        dao = new DAO(dbconfig, true, false);
        switch (corpusType) {
            case brat:
                if (overWriteAnnotatorName.length() == 0)
                    overWriteAnnotatorName = "brat_import";
                importBrat(inputDir, datasetId, overWriteAnnotatorName, importTable, rushRule, includeTypes, overwrite);
                break;
            case ehost:
                if (overWriteAnnotatorName.length() == 0)
                    overWriteAnnotatorName = "ehost_import";
                importEhost(inputDir, datasetId, overWriteAnnotatorName, importTable, rushRule, includeTypes, overwrite);
                break;
            case xmi:
                popDialog("Note", "Sorry, currently import xmi corpus is not supported.", "");
                return;
            case unknown:
                popDialog("Note", "Sorry, which type of documents are you going to import?|",
                        "Currently, there is not any txt or text file in the import directory.\n" +
                                "Use parameter includeFileTypes to specify the document types, separated by commas.");
                return;
        }
        initSuccess = true;
    }

    @Override
    protected Object call() throws Exception {
        if (initSuccess) {
            updateGUIMessage("Start import....");
            switch (corpusType) {
                case txt:
                    importText(inputDir, datasetId, importTable, overwrite,
                            includeTypes);
                    break;
                case brat:
                case ehost:
                    runner.run();
                    break;
            }
            updateGUIMessage("Import complete");
        }
        dao.endBatchInsert();
        dao.close();
        updateGUIProgress(1, 1);
        return null;
    }


    protected void run(String inputDir, String outputTable, String SQLFile, boolean overWrite, String[] filters) throws IOException {
        updateGUIMessage("Start import....");
        DAO dao = new DAO(new File(SQLFile));
        dao.initiateTable(outputTable, overWrite);
        if (filters.length == 0 || filters[0].trim().length() == 0)
            filters = null;
        Collection<File> files = FileUtils.listFiles(new File(inputDir), filters, true);
        int total = files.size();
        int counter = 1;
        for (File file : files) {
            updateGUIProgress(counter, total);
            counter++;
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            ArrayList<RecordRow> records = new ArrayList<>();
            records.add(new RecordRow("txt", "", content, 0, content.length(), 0, "", "", file.getName(), ""));
            dao.insertRecords(outputTable, records);
        }
        dao.endBatchInsert();
        dao.close();
        updateGUIMessage("Import complete");
        updateGUIProgress(1, 1);
    }


    protected void importText(File inputDir, String datasetId, String tableName, boolean overWrite, String includeTypes) throws IOException {
        dao.initiateTableFromTemplate("DOCUMENTS_TABLE", tableName, overWrite);
        String[] filters;
        if (includeTypes.length() == 0)
            filters = null;
        else
            filters = includeTypes.split(",");

        Collection<File> files = FileUtils.listFiles(inputDir, filters, true);
        File[] fileArray = new File[files.size()];
        files.toArray(fileArray);
        Arrays.sort(fileArray, NameFileComparator.NAME_COMPARATOR);
        if (print)
            System.out.println("Reading files from: " + inputDir.getAbsolutePath() +
                    "\nImporting into table: " + tableName);
        int total = files.size();
        int counter = 0;
        Boolean n2c2Data = false;
        for (File file : fileArray) {
            if (print)
                System.out.print("Import: " + file.getName() + "\t\t");
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            ArrayList<RecordRow> records = new ArrayList<>();
            RecordRow recordRow = new RecordRow().addCell("DATASET_ID", datasetId)
                    .addCell("DOC_NAME", file.getName())
                    .addCell("TEXT", content);

//          specifically deal with n2c2 data
            if (content.startsWith("Record date:")) {
                n2c2Data = true;
                String date = content.substring(12, content.indexOf("\n")).trim();
                recordRow.addCell("DATE", date + " 00:00:00");
                recordRow.addCell("BUNCH_ID", file.getName().substring(0, file.getName().indexOf("_")));
            }
            records.add(recordRow);
            dao.insertRecords(tableName, records);
            if (print)
                System.out.println("success");
            updateGUIProgress(counter, total);
            counter++;
        }
        if (n2c2Data) {
            try {
                dao.stmt.execute("UPDATE SAMPLES SET REF_DATE = (SELECT MAX(\"DATE\") FROM SAMPLES T2 WHERE T2.BUNCH_ID = SAMPLES.BUNCH_ID);");
                dao.con.commit();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Totally " + counter + (counter > 1 ? " documents have" : " document has") + " been imported successfully.");
    }


    protected void importEhost(File inputDir, String datasetId, String annotator, String tableName,
                               String rush, String includeTypes, boolean overWrite) {
        if (overWrite)
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", tableName, overWrite);

        runner = new RunEasyCIE();

        if (annotator.length() == 0)
            annotator = "ehost_import";
        runner.init(this, overWriteAnnotatorName, rushRule, "", "", "", "", "",
                false, false, dbConfigFile, importTable, datasetId,
                dbConfigFile, importTable, "", "", "", includeTypes, "db");

        runner.initTypes(EhostReader.getTypeDefinitions(inputDir.getAbsolutePath()));
        runner.setReader(EhostReader.class, new Object[]{EhostReader.PARAM_INPUTDIR, inputDir.getAbsolutePath(),
                EhostReader.PARAM_OVERWRITE_ANNOTATOR_NAME, annotator,
                EhostReader.PARAM_READ_TYPES, includeTypes,
                EhostReader.PARAM_PRINT, print});

    }

    protected void importBrat(File inputDir, String datasetId, String annotator, String tableName, String rush, String includeTypes,
                              boolean overWrite) {
        if (overWrite)
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", tableName, overWrite);

        runner = new RunEasyCIE();
        if (annotator.length() == 0)
            annotator = "ehost_import";
        runner.init(this, overWriteAnnotatorName, rushRule, "", "", "", "", "",
                false, false, dbConfigFile, importTable, datasetId,
                dbConfigFile, importTable, "", "", "", includeTypes, "db");

        runner.initTypes(BratReader.getTypeDefinitions(inputDir.getAbsolutePath()));
        runner.setReader(BratReader.class, new Object[]{BratReader.PARAM_INPUTDIR, inputDir.getAbsolutePath(),
                BratReader.PARAM_OVERWRITE_ANNOTATOR_NAME, annotator,
                BratReader.PARAM_READ_TYPES, includeTypes,
                BratReader.PARAM_PRINT, print});


    }

    protected boolean checkDirExist(File inputDir, String relativePath, String paraName) {
        if (!inputDir.exists()) {
            initSuccess = false;
            popDialog("Note", "The directory \"" + relativePath + "\" does not exist",
                    "Please double check your settings of \"" + paraName + "\"");
            initSuccess = false;
            return false;
        }
        return true;
    }

    public static char checkCorpusType(File dir, String includeFileTypes) {
        Iterator<File> fileIterator = FileUtils.iterateFiles(dir, null, true);
        boolean containText = false;
        while (fileIterator.hasNext()) {
            File file = fileIterator.next();
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".xmi"))
                    return xmi;
                if (fileName.endsWith(".ann"))
                    return brat;
                if (fileName.endsWith(".knowtator.xml"))
                    return ehost;
                if (!containText && (fileName.endsWith(".txt") || fileName.endsWith(".text")))
                    containText = true;
            }
        }
//        if the documents is not text or txt files, but specified by users, then consider the files as txt files to read.
        if (containText || includeFileTypes.length() != 0)
            return txt;
        else
            return unknown;
    }


}
