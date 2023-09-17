package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.easycie.reader.SQLTextReader;
import edu.utah.bmi.nlp.easycie.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorRunner;
import edu.utah.bmi.nlp.uima.AdaptableCPEDescriptorStringDebugger;
import edu.utah.bmi.nlp.uima.BunchMixInferenceWriter;
import edu.utah.bmi.nlp.uima.Processable;
import edu.utah.bmi.nlp.uima.loggers.NLPDBLogger;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.SettingAb;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunCPEDescriptorDebugTask extends GUITask {
    public static Logger logger = Logger.getLogger(RunCPEDescriptorDebugTask.class.getCanonicalName());
    public static Button button;
    protected String inputTableName, snippetResultTable, docResultTable, bunchResultTable, annotator, datasetId;
    public boolean report = false;
    public AdaptableCPEDescriptorStringDebugger runner;
    protected LinkedHashMap<String, String> componentsSettings;
    protected LinkedHashMap<String, String> loggerSettings;
    private String cpeDescriptor;
    private TasksFX tasks;
    private String inputStr, metaStr;
    private String pipelineName;

    protected RunCPEDescriptorDebugTask() {

    }


    public RunCPEDescriptorDebugTask(TasksFX tasks, Button button) {
        this.button = button;
        button.setDisable(true);
        this.tasks = tasks;
    }


    protected void initiate(TasksFX tasks) {
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);
        String rawStringValue = config.getValue(ConfigKeys.reportAfterProcessing);
        report = rawStringValue.length() > 0 && (rawStringValue.charAt(0) == 't' || rawStringValue.charAt(0) == 'T' || rawStringValue.charAt(0) == '1');

        config = tasks.getTask("debug");
        metaStr = config.getValue("log/metaStr");
        runner = AdaptableCPEDescriptorStringDebugger.getInstance(tasks);
        runner.setGuiTask(this);
    }


    @Override
    protected Object call() throws Exception {
        Platform.runLater(() -> {
            updateGUIMessage("Initiating pipeline...");
        });
        new Thread(() -> {
            initiate(tasks);
        }).start();
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
                    new Thread(() -> {
                        int timer = 0;
                        while (runner == null || runner.getStatus() == 0) {
                            try {
                                sleep(1000);
                                timer++;
                                if (timer > 40) {
                                    Platform.runLater(() -> {
                                        updateGUIMessage("Processing failed.");
                                        updateGUIProgress(0, 0);
                                    });
                                    return;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        runner.process(inputStr, metaStr);
                    }).start();
                } else {
                    Platform.runLater(() -> {
                        updateGUIMessage("No string entered.");
                        updateGUIProgress(1, 1);
                    });
                }
            });
        return null;
    }

}
