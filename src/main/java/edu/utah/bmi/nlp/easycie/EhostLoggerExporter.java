package edu.utah.bmi.nlp.easycie;

import edu.utah.bmi.nlp.sql.RecordRow;
import org.apache.commons.io.FileUtils;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

public class EhostLoggerExporter extends EhostExporter {
    public static Logger logger = Logger.getLogger(EhostLoggerExporter.class.getCanonicalName());

    public EhostLoggerExporter(String outputDirectory, String colorPool, int randomColor) {
        initialize(new File(outputDirectory), colorPool, randomColor);

    }

    public void initialize(File outputDirectory, String colorPool, int randomColor) {
        this.colorPool = colorPool;
        this.randomColor = randomColor;
        if (this.colorPool.trim().length() == 0)
            this.randomColor = 2;

        mDocNum = 0;
        logger.info("Ehost annotations will be exported to: " + outputDirectory);

        outputDirectory = new File(outputDirectory, annotator);
        if (outputDirectory.exists()){
            try {
                FileUtils.deleteDirectory(outputDirectory);
                FileUtils.forceMkdir(outputDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        xmlOutputDir = new File(outputDirectory, "saved");
        txtOutputDir = new File(outputDirectory, "corpus");

        try {
            if (!xmlOutputDir.exists())
                Files.createDirectories(Paths.get(xmlOutputDir.getAbsolutePath()));
            if (!txtOutputDir.exists())
                Files.createDirectories(Paths.get(txtOutputDir.getAbsolutePath()));
            configDir = new File(outputDirectory, "config");
            if (!configDir.exists())
                Files.createDirectories(Paths.get(configDir.getAbsolutePath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void export(ArrayList<RecordRow> outputs, String content) {
        LinkedHashMap<String, ArrayList<RecordRow>> annos = new LinkedHashMap<>();
        String docName = "";
        int id=100;
        for (RecordRow recordRow : outputs) {
            if (recordRow.getStrByColumnName("TYPE").equals("")) {
                docName =id+"_"+ recordRow.getStrByColumnName("ID");
                annos.put(docName, new ArrayList<>());
                id++;
            } else {
                annos.get(docName).add(recordRow);
            }
        }
        for (String name : annos.keySet()) {
            ArrayList<RecordRow> anno = annos.get(name);
            File[] files = initialOutputXml(name);
            File outputXml = files[1];
            File sourceFile = files[0];
            try {
                writeEhostXML(content, anno, sourceFile, outputXml);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
        }
        setUpSchema();

    }


}
