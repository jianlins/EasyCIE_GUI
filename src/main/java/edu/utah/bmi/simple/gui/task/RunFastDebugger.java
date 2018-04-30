package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
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
public class RunFastDebugger extends GUITask {

    private TasksFX tasks;
    protected boolean fastNerCaseSensitive;
    protected FastDebugPipe fastDebugPipe;
    protected GUITask guiTask;
    private String inputStr, metaStr;


    public RunFastDebugger(TasksFX tasks) {
        initiate(tasks);
    }


    protected void initiate(TasksFX tasks) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        if (TasksOverviewController.currentTasksOverviewController.currentGUITask == null) {
            TasksOverviewController.currentTasksOverviewController.currentGUITask = this;
        }
        updateGUIMessage("Initiate configurations..");
        this.tasks = tasks;
        guiTask = this;
        TaskFX settings = tasks.getTask("debug");
        metaStr = settings.getValue("log/metaStr");
//      use singleton to speed up initialization
        fastDebugPipe = FastDebugPipe.getInstance(tasks);
//      if need update configurations, use manual refresh instead.

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
                    ;
//                        initiate(tasks, "xmi");
                    updateGUIMessage("Execute pipeline...");
                    new Thread(() -> {
                        fastDebugPipe.process(inputStr, metaStr);
                        fastDebugPipe.showResults();

                    }).start();


                } else {
                    updateGUIMessage("No string entered.");
                    updateGUIProgress(1, 1);
                }
            });
        return null;
    }


}
