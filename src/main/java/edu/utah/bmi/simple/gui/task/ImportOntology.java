package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.easycie.OntologyOperator;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.controller.Main;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

import java.io.File;

/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class ImportOntology extends GUITask {
    protected String owlFilePath, owlExportDirPath;
    protected TasksFX tasks;

    public ImportOntology(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask("import");
        owlFilePath = config.getValue(ConfigKeys.owlFile);
        owlExportDirPath = config.getValue(ConfigKeys.owlExportDir);
        this.tasks = tasks;
    }


    @Override
    protected Object call() throws Exception {
        updateMessage("Start import ontology...");
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                File owlExportDir = new File(owlExportDirPath);
                if (!owlExportDir.exists()) {
                    updateMessage("Export Directory: '" + owlExportDirPath + "' not exist");
                    return;
                }
                File owlFile = new File(owlFilePath);
                if (!owlFile.exists()) {
                    updateMessage("Ontology file '" + owlFilePath + "' not exist");
                    return;
                }
                OntologyOperator ontologyOperator = new OntologyOperator(owlFile.getAbsolutePath());
                String prefix = owlFile.getName();
                prefix = prefix.substring(0, prefix.length() - 4) + "_";
                ontologyOperator.setMappingTypes("conf/contextMapping.tsv");
                File tRuleFile = new File(owlExportDir, prefix + "tRule.xlsx");
                File cRuleFile = new File(owlExportDir, prefix + "cRule.xlsx");
                File contextRuleFile = new File(owlExportDir, prefix + "context.xlsx");

                ontologyOperator.exportModifiers(tRuleFile, cRuleFile, contextRuleFile);
                popDialog("Note", "Ontology imported successfully!",
                        "Token Rule File:\t" + tRuleFile.getAbsolutePath() + "\n" +
                                (cRuleFile.exists() ? "Character Rule File:\t" + new File(owlExportDir, prefix + "cRule.xlsx").getAbsolutePath() + "\n" : "") +
                                "Context Rule File:\t" + contextRuleFile.getAbsolutePath() + "\n\n" +
                                "EasyCIE's pipeline will be configured to use these rule files.");
                updateProgress(1, 1);
                TaskFX mainTask = tasks.getTask(ConfigKeys.maintask);
                mainTask.setValue(ConfigKeys.tRuleFile, ConfigKeys.getRelativePath(tRuleFile.getAbsolutePath()));
                if (cRuleFile.exists())
                    mainTask.setValue(ConfigKeys.cRuleFile, ConfigKeys.getRelativePath(cRuleFile.getAbsolutePath()));
                mainTask.setValue(ConfigKeys.contextRule, ConfigKeys.getRelativePath(contextRuleFile.getAbsolutePath()));
                TasksOverviewController.currentTasksOverviewController.showTask(mainTask);
                updateMessage("Ontology imported successfully.");
            }
        });
        return null;
    }

}
