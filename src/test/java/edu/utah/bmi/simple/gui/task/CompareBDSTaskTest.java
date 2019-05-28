package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.core.SettingOper;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class CompareBDSTaskTest {
    public static EDAO edao;
    public static File tmpDBConfigFile;
    public static File tmpprojectConfigFile;

    @BeforeClass
    public static void testDB() throws IOException {
        tmpDBConfigFile = new File("target/generated-test-sources/sqlconfig.xml");

        if (!tmpDBConfigFile.getParentFile().exists())
            tmpDBConfigFile.getParentFile().mkdirs();
        File config = new File("conf/sqliteconfig.xml");
        String content = FileUtils.readFileToString(config, StandardCharsets.UTF_8);
        content = content.replaceAll("<server>[^<]+", "<server>jdbc:sqlite:target/generated-test-sources/test.sqlite?journal_mode=WAL&amp;synchronous=OFF");
        FileUtils.writeStringToFile(tmpDBConfigFile, content, StandardCharsets.UTF_8);


        tmpprojectConfigFile = new File("target/generated-test-sources/test.xml");
        content = FileUtils.readFileToString(new File("conf/demo.xml"), StandardCharsets.UTF_8);
        content = content.replaceAll("conf/sqliteconfig\\.xml", "target/generated-test-sources/sqlconfig.xml");
        FileUtils.writeStringToFile(tmpprojectConfigFile, content, StandardCharsets.UTF_8);
        edao = EDAO.getInstance(tmpDBConfigFile, true, true);
        edao.initiateTableFromTemplate("ANNOTATION_TABLE", "RESULT_SNIPPET", true);
        edao.initiateTableFromTemplate("ANNOTATION_TABLE", "RESULT_DOC", true);
        edao.initiateTableFromTemplate("ANNOTATION_TABLE", "REFERENCE", true);
    }

    @Test
    public void readCompareFeatures() {
        CompareBDSTask compareBDSTask = new CompareBDSTask(null);
        ArrayList<ArrayList<Object>> configs = compareBDSTask.readCompareFeatures("src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig.tsv", new LinkedHashMap<>());
        System.out.println(configs);
    }

    @Test
    public void readCompareFeaturesTypeFilter() {
        CompareBDSTask compareBDSTask = new CompareBDSTask(new SettingOper("conf/demo.xml").readSettings());
        System.out.println(compareBDSTask.comparingTypeFeatures);
        System.out.println(compareBDSTask.configIndices);
    }


    @Test
    public void testDBCompare() throws Exception {
        insertSampleData0(edao);
        String content = FileUtils.readFileToString(tmpprojectConfigFile, StandardCharsets.UTF_8);
        content = content.replace("src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig.tsv", "src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig0.tsv");
        FileUtils.writeStringToFile(tmpprojectConfigFile, content, StandardCharsets.UTF_8);
        edao.close();
        CompareBDSTask compareBDSTask = new CompareBDSTask(new SettingOper(tmpprojectConfigFile.getAbsolutePath()).readSettings());
        compareBDSTask.print = true;
        compareBDSTask.call();
    }

    @Test
    public void testDBCompare2() throws Exception {
        String content = FileUtils.readFileToString(tmpprojectConfigFile, StandardCharsets.UTF_8);
        content = content.replace("src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig.tsv", "src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig0.tsv");
        FileUtils.writeStringToFile(tmpprojectConfigFile, content, StandardCharsets.UTF_8);
        edao.close();
        CompareBDSTask compareBDSTask = new CompareBDSTask(new SettingOper(tmpprojectConfigFile.getAbsolutePath()).readSettings());
        compareBDSTask.print = true;
        compareBDSTask.call();
    }

    private static void insertSampleData(EDAO dao) {
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "TypeA", 0, 9, 100, "matchedTA", "", "matchedTA in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "TypeB", 0, 9, 200, "matchedTB", "", "matchedTB in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC2", "v3", 1, "TypeB", 0, 9, 200, "matchedTB", "", "matchedTB in a snippet.", ""));

        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "TypeA", 1, 9, 100, "matchedTA", "", "matchedTA in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "TypeB", 0, 9, 150, "matchedTB", "", "matchedTB in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC2", "gold", 1, "TypeB", 3, 4, 200, "matchedTB", "", "matchedTB in a snippet.", ""));
    }

    private static void insertSampleData0(EDAO dao) {
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "TypeA", 0, 9, 100, "matchedTA", "", "matchedTA in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "TypeB", 0, 9, 200, "matchedTB", "", "matchedTB in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC2", "v3", 1, "TypeB", 0, 9, 200, "matchedTB", "", "matchedTB in a snippet.", ""));

        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "TypeA", 1, 9, 100, "matchedTA", "", "matchedTA in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "TypeB", 0, 9, 150, "matchedTB", "", "matchedTB in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC2", "gold", 1, "TypeB", 3, 4, 200, "matchedTB", "", "matchedTB in a snippet.", ""));
    }
}