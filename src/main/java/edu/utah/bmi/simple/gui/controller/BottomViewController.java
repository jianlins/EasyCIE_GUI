package edu.utah.bmi.simple.gui.controller;


import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.*;


/**
 * Created by Jianlin on 5/19/15.
 */
public class BottomViewController {
    @FXML
    private Button resetButton;
    @FXML
    private Button saveButton;
    @FXML
    public Button cancelButton;

    @FXML
    public ProgressBar progressBar;
    @FXML
    public Label msg;

    private TasksFX tasks;
    private Main mainApp;

    public BottomViewController() {
    }

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    @FXML
    private void initialize() {
        // Handle Button event.
        resetButton.setOnAction((event) -> {
            mainApp.refreshSettings();
        });
        saveButton.setOnAction(event -> {
            mainApp.saveSetting();
        });


    }

    /**
     * Is called by the main application to give a reference back to itself.
     *
     * @param mainApp
     */
    public void setMainApp(Main mainApp) {
        this.mainApp = mainApp;
        this.tasks = mainApp.getTasks();


        msg.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> ov, String t, String t1) {
//                System.out.println("Label Text Changed");
                if (t1.indexOf("|") != -1) {
                    msg.textProperty().unbind();
                    String[] infor = t1.split("\\|");
                    String title = infor[0], header = infor[1], content = infor[2];
                    if (infor.length > 3) {
                        msg.setText(infor[3]);
                    } else
                        msg.setText(content);
                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle(title);
                    dialog.setHeaderText(header);
                    TextArea textField = new TextArea();
                    dialog.setHeight(400);
                    dialog.setResizable(true);
                    dialog.getDialogPane().setContent(textField);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                    textField.setEditable(false);
                    textField.setText(content);
                    dialog.showAndWait();

                }
            }
        });
    }

    public void setMsg(String msg) {
        this.msg.textProperty().unbind();
        this.msg.setText(msg);
    }


}
