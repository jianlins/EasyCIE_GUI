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
@Deprecated
public class RunEasyCIEDebugger extends GUITask {

    protected String rushType, cNERType, tNERType, contextType, featureInfType, docInfType, inputStr, metaStr;
    private TasksFX tasks;
    protected String rushRule = "", fastNERRule = "", fastCNERRule = "", contextRule = "",
            featureInfRule = "", docInfRule = "", annotator, exporttypes;
    protected boolean fastNerCaseSensitive;
    protected DebugPipe debugRunner;
    protected GUITask guiTask;


    public RunEasyCIEDebugger(TasksFX tasks) {
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        updateGUIMessage("Initiate configurations..");
        this.tasks = tasks;
        TaskFX settings = tasks.getTask("debug");
        metaStr = settings.getValue("log/metaStr");
        guiTask = this;

    }

    @Override
    protected Object call() throws Exception {
        if (guiEnabled)
            Platform.runLater(() -> {
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
                    debugRunner = new DebugPipe(tasks, guiTask);
                    debugRunner.addReader(inputStr, metaStr);
//                        initiate(tasks, "xmi");
                    updateGUIMessage("Execute pipeline...");
                    new Thread(() -> debugRunner.run()).start();


                } else {
                    updateGUIMessage("No string entered.");
                    updateGUIProgress(1, 1);
                }
            });
        return null;
    }


}
