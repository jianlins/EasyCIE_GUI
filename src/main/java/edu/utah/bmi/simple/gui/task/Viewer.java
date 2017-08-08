package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.uima.MyAnnotationViewerPlain;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by Jianlin Shi on 9/25/16.
 */
public class Viewer extends javafx.concurrent.Task {
    protected String xmiDir = null;
    protected String[] types = new String[]{};

    public Viewer(TasksFX tasks) {
        initiate(tasks);
    }

    private void initiate(TasksFX tasks) {
        TaskFX config = tasks.getTask("export");
        xmiDir = config.getValue(ConfigKeys.outputXMIDir);
        String typeString = config.getValue(ConfigKeys.exportTypes).trim();
        if (typeString.length() > 0) {
            types = typeString.split(",");
        }

    }

    @Override
    protected Object call() throws Exception {
        File xmiDir;
        if (this.xmiDir == null)
            xmiDir = new File("data/output/xmi");
        else
            xmiDir = new File(this.xmiDir);
        if (!xmiDir.exists()) {
            try {
                throw new FileNotFoundException(xmiDir.getAbsolutePath() + " not found.");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        File descriptorFile = new File("desc/type/customized.xml");
        if (!descriptorFile.exists()) {
            descriptorFile = new File("desc/type/All_Types.xml");
        }

        String inputPath = xmiDir.getAbsolutePath();
        String descripterPath = descriptorFile.getAbsolutePath();
        final String[] args = new String[types.length + 3];
        args[0] = "Annotation Viewer";
        args[1] = inputPath;
        args[2] = descripterPath;
        if (types.length > 0) {
            for (int i = 0; i < types.length; i++) {
                String type = types[i].trim();
                if (type.indexOf(".") == -1)
                    type = "edu.utah.bmi.type.system." + type;
                args[i + 3] = type;
            }
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new MyAnnotationViewerPlain(args);
                frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);
            }
        });
        updateMessage("Open UIMA annotation viewer");
        updateProgress(1, 1);
        return null;
    }


}
