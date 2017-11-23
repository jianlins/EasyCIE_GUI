package edu.utah.bmi.simple.gui.controller;


import edu.utah.bmi.simple.gui.core.SettingOper;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javafx.scene.image.Image;


/**
 * @author Jianlin Shi
 */
public class Main extends Application {

    private Stage primaryStage;
    private BorderPane rootLayout;
    private ObservableList<TaskFX> taskParameters = FXCollections.observableArrayList();
    public TasksFX tasks;
    public static HashMap<String, String> valueChanges = new HashMap<>(), memochanges = new HashMap<>();
    private SettingOper settingOper;
    public BottomViewController bottomViewController;
    private File currentConfigFile;
    private static String basePath;
    private final File logFile = new File("conf/.log");
    private String currentTaskName = "";

    @FXML
    private MenuBar menuBar;

    public static void main(String[] args) {
        launch(args);
    }

    public Main() {

    }


    public void start(Stage primaryStage) throws Exception {
        basePath = System.getProperty("user.dir");
        this.primaryStage = primaryStage;

//        System.out.println(Paths.get(Thread.currentThread().getContextClassLoader().getResource("edu/utah/bmi/simple/gui/view/big.png").toURI()).toString());
//        Image anotherIcon = new Image(Paths.get(getClass().getClassLoader().getResource("edu/utah/bmi/simple/gui/view/big.png").toURI()).toString());
        Image anotherIcon = new Image("edu/utah/bmi/simple/gui/view/transbig.png");
        primaryStage.getIcons().add(anotherIcon);
        initRootLayout();
        String configFile = getLastConfigFile();
        currentConfigFile = new File(configFile);
        this.primaryStage.setTitle("EasyCIE(__"+currentConfigFile.getName()+"__)");
        refreshSettings();
    }

    public void refreshSettings() {
        settingOper = new SettingOper(currentConfigFile.getAbsolutePath());
        initiateSetting();
        showBottomView();
        showTaskOverview();
        System.out.println("Refresh loading from " + getRelativePath(currentConfigFile.getAbsolutePath()));
        setMsg("Load: " + getRelativePath(currentConfigFile.getAbsolutePath()));
        TaskFX currentTask = tasks.getTask(currentTaskName);
    }

    public void openConfigFile() {
        Platform.runLater(new Runnable() {
            public void run() {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Choose configuration file: ");
                File oldParentDir;
                if (!currentConfigFile.exists())
                    oldParentDir = new File("./");
                else {
                    oldParentDir = currentConfigFile.getParentFile();
                }
                if (oldParentDir.exists())
                    fileChooser.setInitialDirectory(oldParentDir);
                if (currentConfigFile.exists())
                    fileChooser.setInitialFileName(currentConfigFile.getName());
                File file = fileChooser.showOpenDialog(null);
                if (file != null) {
                    currentConfigFile = file;
                    primaryStage.setTitle("EasyCIE(__"+file.getName()+"__)");

                    refreshSettings();
                    saveOpenLog(getRelativePath(currentConfigFile.getAbsolutePath()) + "\n" + currentTaskName);
                }
            }
        });
    }


    private void saveOpenLog(String filePath) {
        List<String> lines = new ArrayList<>();
        try {
            lines.add(filePath);
            lines.add(currentTaskName);
            FileUtils.writeLines(logFile, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getLastConfigFile() {
        String conf = "conf/config.xml";
        if (logFile.exists()) {
            try {
                List<String> lines = FileUtils.readLines(logFile);
                if (lines.size() > 0)
                    conf = lines.get(0);
                currentTaskName = lines.size() > 1 ? lines.get(1) : "import";
                if (!new File(conf).exists()) {
                    System.out.println("The last used configuration file: " +
                            new File(conf).getAbsolutePath() +
                            " does not exists, please choose another one.");
                    setMsg(getRelativePath(new File(conf).getAbsolutePath()) + " does not exists");
                    openConfigFile();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(logFile.getAbsolutePath() + " does not exists, please choose another one.");
            setMsg(getRelativePath(logFile.getAbsolutePath()) + " does not exists");
            openConfigFile();
        }
        return conf;
    }

    public void initiateSetting() {
        tasks = settingOper.readSettings();
    }

    public TasksFX getTasks() {
        return tasks;
    }

    public void saveSetting() {
//        settingOper.writeTasks(tasks);
        settingOper.ChangeMemos(memochanges);
        settingOper.ChangeValues(valueChanges);
        int changeCount = valueChanges.size() + memochanges.size();
        if (changeCount > 0)
            bottomViewController.setMsg(changeCount +
                    (changeCount > 1 ? " changes" : " change") + " saved");
        else
            bottomViewController.setMsg("No change is made.");
        settingOper.saveConfigs();
    }


    public void saveAsSetting() {
        settingOper.ChangeMemos(memochanges);
        settingOper.ChangeValues(valueChanges);
        int changeCount = valueChanges.size() + memochanges.size();
        if (changeCount > 0)
            bottomViewController.setMsg(changeCount +
                    (changeCount > 1 ? " changes" : " change") + " saved");
        else
            bottomViewController.setMsg("No change is made.");
        Platform.runLater(new Runnable() {
            public void run() {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Choose configuration file: ");
                File oldParentDir;
                if (!currentConfigFile.exists())
                    oldParentDir = new File("./");
                else {
                    oldParentDir = currentConfigFile.getParentFile();
                }
                if (oldParentDir.exists())
                    fileChooser.setInitialDirectory(oldParentDir);
                if (currentConfigFile.exists())
                    fileChooser.setInitialFileName(currentConfigFile.getName());
                File file = fileChooser.showOpenDialog(null);
                if (file != null) {
                    settingOper.saveConfigs(file);
                }
            }
        });


    }


    public HashMap<String, String> getValueChanges() {
        return valueChanges;
    }

    public HashMap<String, String> getMemochanges() {
        return memochanges;
    }


    /**
     * Initializes the root layout.
     */
    public void initRootLayout() {
        try {
            // Load root layout from fxml file.
            FXMLLoader loader = new FXMLLoader();
            System.out.println("Working Directory = " +
                    System.getProperty("user.dir"));
            URL layoutURL = getClass().getClassLoader().getResource("edu/utah/bmi/simple/gui/view/RootLayout.fxml");
            loader.setLocation(getClass().getClassLoader().getResource("edu/utah/bmi/simple/gui/view/RootLayout.fxml"));
//            System.out.println("loader location:" + loader.getLocation());
            rootLayout = loader.load();

            RootLayoutController rootLayoutController = loader.getController();
            rootLayoutController.setMainApp(this);
            // Show the scene containing the root layout.
            Scene scene = new Scene(rootLayout);
            scene.getStylesheets().add("edu/utah/bmi/simple/gui/view/panel.css");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows the tasks overview inside the root layout.
     */
    public void showTaskOverview() {
        try {
            // Load person overview.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(this.getClass().getClassLoader().getResource("edu/utah/bmi/simple/gui/view/TaskOverview.fxml"));
            AnchorPane taskOverview = loader.load();
            // Set person overview into the center of root layout.
            rootLayout.setCenter(taskOverview);
            TasksOverviewController tasksOverviewController = loader.getController();
            tasksOverviewController.setMainApp(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Shows the bottom overview inside the root layout.
     */
    public void showBottomView() {
        try {
            // Load person overview.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(this.getClass().getClassLoader().getResource("edu/utah/bmi/simple/gui/view/BottomLayout.fxml"));
            HBox personOverview = loader.load();
            // Set person overview into the center of root layout.
            rootLayout.setBottom(personOverview);
            bottomViewController = loader.getController();
            bottomViewController.setMainApp(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the main stage.
     *
     * @return
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }


    public ObservableList<TaskFX> getTaskParameters() {
        return this.taskParameters;
    }


    public void stop() {
        saveOpenLog(getRelativePath(currentConfigFile.getAbsolutePath()));
        System.out.println(currentTaskName);
        System.exit(0);
    }

    public void setMsg(String msg) {
        if (bottomViewController != null) {
//            bottomViewController.progressBar.setPrefWidth(1);
//            bottomViewController.msg.setPrefWidth(580);
            bottomViewController.setMsg(msg);
        }
    }


    public static String getRelativePath(String file) {
        return ConfigKeys.getRelativePath(basePath, file);
    }

    public void setCurrentTaskName(String currentTaskName) {
        this.currentTaskName = currentTaskName;
    }

    public String getCurrentTaskName() {
        return currentTaskName;
    }

    public TaskFX getCurrentTask() {
        return tasks.getTask(currentTaskName);
    }

    public int getCurrentTaskId() {
        return tasks.getTaskId(currentTaskName);
    }
}

