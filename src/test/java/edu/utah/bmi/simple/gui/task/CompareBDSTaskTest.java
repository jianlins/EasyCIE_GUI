package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.core.EvalCounter;
import edu.utah.bmi.simple.gui.core.SettingOper;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
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

    @Before
    public void testDB() throws IOException {
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
        insertSampleData(edao);
//        String content = FileUtils.readFileToString(tmpprojectConfigFile, StandardCharsets.UTF_8);
//        content = content.replace("src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig.tsv", "src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig0.tsv");
//        FileUtils.writeStringToFile(tmpprojectConfigFile, content, StandardCharsets.UTF_8);
        edao.close();
        CompareBDSTask compareBDSTask = new CompareBDSTask(new SettingOper(tmpprojectConfigFile.getAbsolutePath()).readSettings());
//        compareBDSTask.print = true;
        compareBDSTask.call();
        EvalCounter eval = compareBDSTask.comparior.evalCounters.get("ConceptC_F3:V3;F4:V4");
        assert (eval.tp==2);
        assert (eval.fp==0);
        assert (eval.fn==0);
        eval = compareBDSTask.comparior.evalCounters.get("ConceptA_F1:V1;F2:V2");
        assert (eval.tp==1);
        assert (eval.fp==1);
        assert (eval.fn==0);
    }

    @Test
    public void testDBCompare0() throws Exception {
        insertSampleData0(edao);
        String content = FileUtils.readFileToString(tmpprojectConfigFile, StandardCharsets.UTF_8);
        content = content.replace("src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig.tsv", "src/test/resources/edu.utah.bmi.simple.gui.task/CompareConfig0.tsv");
        FileUtils.writeStringToFile(tmpprojectConfigFile, content, StandardCharsets.UTF_8);
        edao.close();
        CompareBDSTask compareBDSTask = new CompareBDSTask(new SettingOper(tmpprojectConfigFile.getAbsolutePath()).readSettings());
//        compareBDSTask.print = true;
        compareBDSTask.call();
        EvalCounter eval = compareBDSTask.comparior.evalCounters.get("TypeA_");
        assert (eval.tp==1);
        assert (eval.fp==0);
        assert (eval.fn==0);
        eval = compareBDSTask.comparior.evalCounters.get("TypeB_");
        assert (eval.tp==1);
        assert (eval.fp==1);
        assert (eval.fn==1);
    }



    private static void insertSampleData0(EDAO dao) {
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "TypeA", 0, 9, 100, "matchedTA", "", "matchedTA in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "TypeB", 0, 9, 200, "matchedTB", "", "matchedTB in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC2", "v3", 1, "TypeB", 0, 9, 200, "matchedTB", "", "matchedTB in a snippet.", ""));

        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "TypeA", 1, 9, 100, "matchedTA", "", "matchedTA in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "TypeB", 0, 9, 150, "matchedTB", "", "matchedTB in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC2", "gold", 1, "TypeB", 3, 4, 200, "matchedTB", "", "matchedTB in a snippet.", ""));
    }

    private static void insertSampleData(EDAO dao) {
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "ConceptB", 0, 9, 100, "matchedTA", "F3: V3\nF4: V4", "matchedTA in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "ConceptB", 0, 9, 150, "matchedTA", "F3: V3\nF4: V4", "matchedTA in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC1", "v3", 1, "ConceptD", 0, 9, 150, "matchedTB", "F5: V5\nF6: V6", "matchedTB in a snippet.", ""));
        dao.insertRecord("RESULT_SNIPPET", new RecordRow("DOC2", "v3", 1, "ConceptF", 0, 9, 200, "matchedTB", "F5: V5\nF6: V6", "matchedTB in a snippet.", ""));

        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "ConceptA", 1, 9, 100, "matchedTA", "F1: V1\nF2: V2", "matchedTA in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC1", "gold", 1, "ConceptC", 0, 9, 150, "matchedTB", "F3: V3\nF4: V4", "matchedTB in a snippet.", ""));
        dao.insertRecord("REFERENCE", new RecordRow("DOC2", "gold", 1, "ConceptC", 3, 4, 200, "matchedTB", "F3: V3\nF4: V4", "matchedTB in a snippet.", ""));
    }
}