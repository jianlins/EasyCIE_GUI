/*
 * Copyright  2017  Department of Biomedical Informatics, University of Utah
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.utah.bmi.nlp.core;

import javafx.application.Platform;
import javafx.concurrents.Task;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Extend Task  to open updateMessage and updateProgress methods.
 *
 * @author Jianlin Shi
 * Created on 2/24/17.
 */
public abstract class GUITask<V> extends Task<V> {


    public boolean guiEnabled = true;
    public boolean print = false;

    public GUITask() {
        this(new TaskCallable<V>());
    }

    public GUITask(TaskCallable<V> callableAdapter) {
        super(callableAdapter);
        callableAdapter.task = this;
    }

    public void updateGUIMessage(String msg) {
        if (guiEnabled)
            Platform.runLater(() -> {
                updateMessage(msg);
            });
        if (print)
            System.out.println(msg);
    }

    public void updateGUIProgress(Double workDone, Double max) {
        if (guiEnabled)
            Platform.runLater(() -> {
                updateProgress(workDone, max);
            });
        if (print)
            System.out.println(workDone + "/" + max);
    }

    public void updateGUIProgress(Long workDone, Long max) {
        if (guiEnabled)
            Platform.runLater(() -> {
                updateProgress(workDone, max);
            });
        if (print)
            System.out.println(workDone + "/" + max);
    }

    public void updateGUIProgress(int workDone, int max) {
        if (guiEnabled)
            Platform.runLater(() -> {
                updateProgress(workDone, max);
            });
        if (print)
            System.out.println(workDone + "/" + max);
    }

    public void popDialog(String title, String header, String content) {
        if (guiEnabled)
            Platform.runLater(() -> {
                Dialog<String> dialog = new Dialog<>();
                dialog.setTitle(title);
                dialog.setHeaderText(header);
                TextArea textField = new TextArea();
                dialog.setHeight(400);
                dialog.setResizable(true);
                dialog.getDialogPane().setContent(textField);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                ButtonType copyButton = new ButtonType(
                        "CopyContent", ButtonBar.ButtonData.OTHER);
                DialogPane panel = dialog.getDialogPane();
                panel.getButtonTypes().add(copyButton);

                textField.setEditable(false);
                textField.setText(content);
                textField.setWrapText(true);
                Object value = dialog.showAndWait();
                Optional<ButtonType> answer = (Optional<ButtonType>) value;
                if (answer.get().getButtonData().equals(ButtonBar.ButtonData.OTHER)) {
                    ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(content);
                    Clipboard.getSystemClipboard().setContent(clipboardContent);
                }

            });
        if (print)
            System.out.println(title + ":\t" + header + ":\t" + content);
    }

    public void guiCall() {
        try {
            call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
