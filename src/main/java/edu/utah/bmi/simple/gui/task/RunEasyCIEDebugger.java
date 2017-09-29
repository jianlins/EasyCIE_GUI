package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;

import java.util.Optional;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunEasyCIEDebugger extends GUITask {

    protected String rushType, cNERType, tNERType, contextType, featureInfType, docInfType, inputStr;
    private TasksFX tasks;
    protected String rushRule = "", fastNERRule = "", fastCNERRule = "", contextRule = "",
            featureInfRule = "", docInfRule = "", annotator, exporttypes;
    protected boolean fastNerCaseSensitive;
    protected RunDebugPipe debugRunner;


    public RunEasyCIEDebugger(TasksFX tasks) {
        this.tasks = tasks;
    }


    protected void initiate(TasksFX tasks, String option) {
        updateMessage("Initiate configurations..");
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);
        fastNERRule = config.getValue(ConfigKeys.tRuleFile);
        fastCNERRule = config.getValue(ConfigKeys.cRuleFile);
        contextRule = config.getValue(ConfigKeys.contextRule);
        featureInfRule = config.getValue(ConfigKeys.featureInfRule);
        docInfRule = config.getValue(ConfigKeys.docInfRule);

        String reportString = config.getValue(ConfigKeys.fastNerCaseSensitive);
        fastNerCaseSensitive = reportString.length() > 0 && (reportString.charAt(0) == 't' || reportString.charAt(0) == 'T' || reportString.charAt(0) == '1');


        config = tasks.getTask("settings");
        rushRule = config.getValue(ConfigKeys.rushRule);

        TaskFX debugConfig = tasks.getTask("debug");
        rushType = debugConfig.getValue(ConfigKeys.rushType).trim();
        cNERType = debugConfig.getValue(ConfigKeys.cNERType).trim();
        tNERType = debugConfig.getValue(ConfigKeys.tNERType).trim();
        contextType = debugConfig.getValue(ConfigKeys.contextType).trim();
        featureInfType = debugConfig.getValue(ConfigKeys.featureInfType).trim();
        docInfType = debugConfig.getValue(ConfigKeys.docInfType).trim();

        exporttypes = rushType + (cNERType.length() > 0 ? "," + cNERType : "")
                + (tNERType.length() > 0 ? "," + tNERType : "")
                + (contextType.length() > 0 ? "," + contextType : "")
                + (featureInfType.length() > 0 ? "," + featureInfType : "")
                + (docInfType.length() > 0 ? "," + docInfType : "");

        debugRunner = new RunDebugPipe(this, inputStr, annotator, fastNerCaseSensitive,
                rushRule, fastNERRule, fastCNERRule, contextRule, featureInfRule,
                docInfRule, "target/generated-test-sources", exporttypes, rushType, cNERType, tNERType, contextType,
                featureInfType, docInfType);

    }

    @Override
    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                AnnotationLogger.reset();
                Dialog<String> dialog = new Dialog<>();
                dialog.setTitle("Pipeline Debugger");
                dialog.setHeaderText("Enter your snippet string here:");
                TextArea textField = new TextArea();
                dialog.setHeight(400);
                dialog.setResizable(true);
                dialog.getDialogPane().setContent(textField);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
                textField.setEditable(true);
                textField.setWrapText(true);

                Optional<String> result = dialog.showAndWait();
                String entered = "";

                if (result.isPresent()) {
                    entered = textField.getText();
                }
                inputStr = entered;
                if (entered.trim().length() > 0) {
                    initiate(tasks, "xmi");
                    updateMessage("Execute pipeline...");
                    debugRunner.run();
                } else {
                    updateMessage("No string entered.");
                    updateProgress(1, 1);
                }
            }
        });
        return null;
    }


}
