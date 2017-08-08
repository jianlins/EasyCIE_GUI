package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.runner.CommonFunc;
import edu.utah.bmi.nlp.runner.RunPipe;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.BratReader;
import edu.utah.bmi.nlp.uima.EhostReader;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static edu.utah.bmi.nlp.runner.CommonFunc.getCmdValue;

/**
 * Created by Jianlin Shi on 9/23/16.
 */
public class Import extends javafx.concurrent.Task {
    public static final char ehost = 'e', xmi = 'x', brat = 'b', txt = 't', unknown = 'u';
    protected DAO dao;
    protected boolean print;

    protected String inputPath, SQLFile, corpusTable;
    protected File inputDir
    protected boolean overwrite = false;
    protected String[] filters;

    public Import(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("import");
        inputPath = config.getValue(ConfigKeys.importDir);
        String filterString = config.getValue(ConfigKeys.importFilters);
        filters = filterString.split("\\s+");

        char corpusType = checkCorpusType(inputDir, includeTypes);

        config = tasks.getTask("settings");
        SQLFile = config.getValue(ConfigKeys.readDBConfigFile);
        corpusTable = config.getValue(ConfigKeys.inputTableName);
        overwrite = config.getValue(ConfigKeys.overwrite).charAt(0) == 't' || config.getValue(ConfigKeys.overwrite).charAt(0) == 'T';

    }

    @Override
    protected Object call() throws Exception {
        run(inputDir, corpusTable, SQLFile, overwrite, filters);
        return null;
    }

    protected void run(CommandLine cmd) {
        String inputPath = getCmdValue(cmd, "inputdir", "data/input");
        String dbconfigFile = getCmdValue(cmd, "wconfig", "conf/dbconfig.xml");
        String outputTableName = getCmdValue(cmd, "writetable", "");
        String datasetId = getCmdValue(cmd, "datasetid", "0");
        String annotator = getCmdValue(cmd, "annotator", null);
        Boolean overwrite = cmd.hasOption("overwrite");
        String rushRule = (cmd.hasOption("rush")) ? getCmdValue(cmd, "rush", "conf/rush_rule.tsv") : "";
        String featureinf = (cmd.hasOption("featureinf")) ? getCmdValue(cmd, "featureinf", "") : "";
        String docinf = (cmd.hasOption("docinf")) ? getCmdValue(cmd, "docinf", "") : "";
        String includeTypes = getCmdValue(cmd, "includetypes", null);
        print = cmd.hasOption("print");
        File inputDir = new File(inputPath);
        if (CommonFunc.checkDirExist(inputDir, "inputdir")) {
            System.exit(1);
        }
        File dbconfig = new File(dbconfigFile);
        if (CommonFunc.checkFileExist(dbconfig, "wconfig")) {
            System.exit(1);
        }
        dao = new DAO(dbconfig,true,false);
        char corpusType = checkCorpusType(inputDir, includeTypes);
        if (outputTableName.length() == 0) {
            if (corpusType == txt)
                outputTableName = "DOCUMENTS";
            else
                outputTableName = "REFERENCE";
        }
        try {
            switch (corpusType) {
                case txt:
                    importText(inputDir, datasetId, outputTableName, overwrite,
                            includeTypes == null ? null : includeTypes.split(","));
                    break;
                case brat:
                    importBrat(dbconfigFile, inputDir, datasetId, annotator, outputTableName, rushRule, featureinf, docinf, includeTypes, overwrite);
                    break;
                case ehost:
                    importEhost(dbconfigFile, inputDir, datasetId, annotator, outputTableName, rushRule, featureinf, docinf, includeTypes, overwrite);
                    break;
                case xmi:
                    System.err.println("Sorry, currently import xmi corpus is not supported.");
                    break;
                case unknown:
                    System.err.println("Sorry, which type of documents are you going to import?\n" +
                            "Currently, there is not any txt or text file in the import directory.\n" +
                            "Use parameter \"-t <file extension name>\" to specify the document types, separated by commas.");
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        dao.endBatchInsert();
        dao.close();
    }


    protected void run(String inputDir, String outputTable, String SQLFile, boolean overWrite, String[] filters) throws IOException {
        updateMessage("Start import....");
        DAO dao = new DAO(new File(SQLFile));
        dao.initiateTable(outputTable, overWrite);
        if (filters.length == 0 || filters[0].trim().length() == 0)
            filters = null;
        Collection<File> files = FileUtils.listFiles(new File(inputDir), filters, true);
        int total = files.size();
        int counter = 1;
        for (File file : files) {
            updateProgress(counter, total);
            counter++;
            String content = FileUtils.readFileToString(file);
            ArrayList<RecordRow> records = new ArrayList<>();
            records.add(new RecordRow("txt", "", content, 0, content.length(), 0, "", "", file.getName(), ""));
            dao.insertRecords(outputTable, records);
        }
        dao.endBatchInsert();
        dao.close();
        updateMessage("Import complete");
        updateProgress(1, 1);
    }


    protected void importText(File inputDir, String datasetId, String tableName, boolean overWrite, String[] filters) throws IOException {
        dao.initiateTableFromTemplate("DOCUMENTS", tableName, overWrite);
        if (filters == null || filters.length == 0)
            filters = null;
        Iterator iterator = FileUtils.iterateFiles(inputDir, filters, true);
        if (print)
            System.out.println("Reading files from: " + inputDir.getAbsolutePath() +
                    "\nImporting into table: " + tableName);
        int counter = 0;
        while (iterator.hasNext()) {
            File file = (File) iterator.next();
            if (print)
                System.out.print("Import: " + file.getName() + "\t\t");
            String content = FileUtils.readFileToString(file);
            ArrayList<RecordRow> records = new ArrayList<>();
            records.add(new RecordRow().addCell("DATASET_ID", datasetId)
                    .addCell("DOC_NAME", file.getName())
                    .addCell("TEXT", content));
            dao.insertRecords(tableName, records);
            if (print)
                System.out.println("success");
            counter++;
        }
        System.out.println("Totally " + counter + (counter > 1 ? " documents have" : " document has") + " been imported successfully.");
    }

    protected void importEhost(String dbconfigFile, File inputDir, String datasetId, String annotator, String tableName, String rush, String featureInf, String docInf, String includeTypes,
                               boolean overWrite) {
        if (overWrite)
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", tableName, overWrite);

        RunPipe runner = new RunPipe();
        if (rush == null)
            rush = "";
        if (annotator == null)
            annotator = "ehost_import";
        if (includeTypes != null)
            runner.initPipe(new String[]{"-r", dbconfigFile, "-wt", tableName, "-id", datasetId, "-a", annotator, "-ru",
                    rush, "-n", "", "-cn", "", "-x", "", "-f", featureInf, "-d", docInf, "-t", includeTypes});
        else
            runner.initPipe(new String[]{"-r", dbconfigFile, "-wt", tableName, "-id", datasetId, "-a", annotator, "-ru",
                    rush, "-n", "", "-cn", "", "-x", "", "-f", featureInf, "-d", docInf});
        runner.initTypes(EhostReader.getTypeDefinitions(inputDir.getAbsolutePath()));
        runner.setReader(EhostReader.class, new Object[]{EhostReader.PARAM_INPUTDIR, inputDir.getAbsolutePath(),
                EhostReader.PARAM_OVERWRITE_ANNOTATOR_NAME, annotator, EhostReader.PARAM_PRINT, print});
        runner.run();
    }

    protected void importBrat(String dbconfigFile, File inputDir, String datasetId, String annotator, String tableName, String rush, String featureInf, String docInf, String includeTypes,
                              boolean overWrite) {
        if (overWrite)
            dao.initiateTableFromTemplate("ANNOTATION_TABLE", tableName, overWrite);

        RunPipe runner = new RunPipe();
        if (rush == null)
            rush = "";
        if (annotator == null)
            annotator = "ehost_import";
        if (includeTypes != null)
            runner.initPipe(new String[]{"-r", dbconfigFile, "-wt", tableName, "-id", datasetId, "-a", annotator, "-ru",
                    rush, "-n", "", "-cn", "", "-x", "", "-f", featureInf, "-d", docInf, "-t", includeTypes});
        else
            runner.initPipe(new String[]{"-r", dbconfigFile, "-wt", tableName, "-id", datasetId, "-a", annotator, "-ru",
                    rush, "-n", "", "-cn", "", "-x", "", "-f", featureInf, "-d", docInf});
        runner.initTypes(BratReader.getTypeDefinitions(inputDir.getAbsolutePath()));
        runner.setReader(BratReader.class, new Object[]{EhostReader.PARAM_INPUTDIR, inputDir.getAbsolutePath(),
                EhostReader.PARAM_OVERWRITE_ANNOTATOR_NAME, annotator, EhostReader.PARAM_PRINT, print});
        runner.run();
    }

    public static char checkCorpusType(File dir, String includeTypes) {
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
        if (containText || includeTypes != null)
            return txt;
        else
            return unknown;
    }


}
