package edu.utah.bmi.simple.gui.controller;

import com.sun.deploy.uitoolkit.impl.fx.HostServicesFactory;
import com.sun.javafx.application.HostServicesDelegate;
import javafx.application.Platform;
import javafx.fxml.FXML;


/**
 * @author Jianlin Shi
 * Created on 3/30/16.
 */
public class RootLayoutController {
    private Main mainApp;

    public void setMainApp(Main mainApp) {
        this.mainApp = mainApp;
    }

    @FXML
    private void exit() {
        Platform.exit();
    }

    @FXML
    private void open() {
        mainApp.openConfigFile();
    }
    @FXML
    private void save() {
        mainApp.saveSetting();
    }

    @FXML
    private void openHelpURL() {
        HostServicesDelegate hostServices = HostServicesFactory.getInstance(mainApp);
        String url = mainApp.tasks.getTask("settings").getValue("help");
        if (url.length() == 0) {
            System.out.println("help document url has not been set up. Please add a \"help\" element with an URL as the value, " +
                    "under \"settings\" inside your configuration file.");
            mainApp.bottomViewController.msg.setText("Help document url has not been set up.");
        } else {
            hostServices.showDocument(url);
            mainApp.bottomViewController.msg.setText("Navigate to EasyCIE wiki.");
        }
//        hostServices.showDocument("https://sourceforge.net/p/simcda/wiki/usermanual-home/");
    }
}
