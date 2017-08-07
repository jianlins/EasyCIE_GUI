package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.sql.DAO;
import edu.utah.bmi.sql.DAOFactory;
import edu.utah.bmi.sql.Record;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by Jianlin Shi on 9/23/16.
 */
public class Import extends javafx.concurrent.Task {
    protected String inputDir, SQLFile, corpusTable;
    protected boolean overwrite = false;
    protected String[] filters;

    public Import(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("import");
        inputDir = config.getValue(ConfigKeys.importDir);
        String filterString = config.getValue(ConfigKeys.importFilters);
        filters = filterString.split("\\s+");

        config = tasks.getTask("settings");
        SQLFile = config.getValue(ConfigKeys.corpusDBFile);
        corpusTable = config.getValue(ConfigKeys.corpusDBTable);
        overwrite = config.getValue(ConfigKeys.overwrite).charAt(0) == 't' || config.getValue(ConfigKeys.overwrite).charAt(0) == 'T';

    }

    @Override
    protected Object call() throws Exception {
        run(inputDir, corpusTable, SQLFile, overwrite, filters);
        return null;
    }


    protected void run(String inputDir, String outputTable, String SQLFile, boolean overWrite, String[] filters) throws IOException {
        updateMessage("Start import....");
        DAO dao = DAOFactory.getDAO(new File(SQLFile));
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
            ArrayList<Record> records = new ArrayList<>();
            records.add(new Record("txt", "", content, 0, content.length(), 0, "", "", file.getName(), ""));
            dao.insertRecords(outputTable, records);
        }
        dao.endBatchInsert();
        dao.close();
        updateMessage("Import complete");
        updateProgress(1, 1);
    }


}
