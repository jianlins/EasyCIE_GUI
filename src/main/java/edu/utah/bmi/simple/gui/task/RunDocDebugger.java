package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

import java.io.File;

/**
 * This task support two type of input settings,
 * <p>
 * log/docId  will take the configuration value as DOC_ID and try to find a matched document for processing
 * log/docName will take the configuration value as DOC_NAME and try to find a matched document for processing
 *
 * @author Jianlin Shi on 9/19/16.
 */
public class RunDocDebugger extends GUITask {

    //    protected String rushType, cNERType, tNERType, contextType, featureInfType, docInfType, inputStr;
//    private TasksFX tasks;
    protected String inputStr, annotator, inputTableName;
    //    protected boolean fastNerCaseSensitive;
    protected RunDBDebugPipe debugRunner;
    protected DAO dao;
    protected TaskFX thisTask;


    public RunDocDebugger(TasksFX tasks) {
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIMessage("Initiate configurations..");

        debugRunner = new RunDBDebugPipe(tasks, this);
        TaskFX settings = tasks.getTask("settings");
        String dbconfig = settings.getValue(ConfigKeys.readDBConfigFile);
        inputTableName = settings.getValue(ConfigKeys.inputTableName);
        dao = new DAO(new File(dbconfig));
        thisTask = tasks.getTask("dbdebug");

    }

    @Override
    protected Object call() throws Exception {
        if (guiEnabled)
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    String input = thisTask.getValue("log/docId");
                    String type = "DOC_ID";
                    if (input.length() == 0) {
                        input = thisTask.getValue("log/docName");
                        type = "DOC_NAME";
                    }
                    RecordRow record;
                    if (type.equals("DOC_ID")) {
                        record = dao.queryRecord("SELECT TEXT  FROM " + inputTableName + " WHERE " + type + "=" + input + ";");
                    } else {
                        record = dao.queryRecord("SELECT TEXT  FROM " + inputTableName + " WHERE " + type + "='" + input + "';");
                    }
                    if (record == null) {
                        System.out.println("No record found in table " + inputTableName + ", where " + type + "=" + input);
                        return;
                    }

                    inputStr = record.getStrByColumnName("TEXT");
                    if (inputStr.trim().length() > 0) {
                        debugRunner.addReader(inputStr);
//                        initiate(tasks, "xmi");
                        updateGUIMessage("Execute pipeline...");
                        debugRunner.run();
                    } else {
                        updateGUIMessage("No string entered.");
                        updateGUIProgress(1, 1);
                    }

                }
            });
        return null;
    }


}
