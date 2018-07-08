package edu.utah.bmi.simple.gui.task;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by u0876964 on 11/6/16.
 */
public class ConfigKeys {

    public static String mainBasePath = null;
    public static final String importDir = "documents/corpusDir";
    //    import excel configs
    public static final String importExcel = "excelData/filePath";
    public static final String sheetName = "excelData/sheetName";
    public static final String docNameColumn = "excelData/docNameColumnPos";
    public static final String docTextColumn = "excelData/docTxtColumnPos";
    public static final String docDateColumn = "excelData/docDateColumnPos";
    public static final String conclusionColumn ="excelData/conclusionColumnPos";
    public static final String startRowNum = "excelData/startRowNum";


    public static final String includeFileTypes = "documents/includeFileTypes";

    public static final String annotationDir = "annotations/projectDir";
    public static final String includeAnnotationTypes = "annotations/includeAnnotationTypes";
    public static final String overWriteAnnotatorName = "annotations/overWriteAnnotatorName";
    public static final String enableSentenceSnippet = "annotations/enableSentenceSnippet";

    public static final String owlFile = "ontology/owlFile";
    public static final String owlExportDir = "ontology/exportDir";

    public static final String sectionRule = "pipeLineSetting/sectionRule";
    public static final String tRuleFile = "pipeLineSetting/tRule";
    public static final String cRuleFile = "pipeLineSetting/cRule";
    public static final String includesections = "pipeLineSetting/includesections";
    public static final String excludesections = "pipeLineSetting/excludesections";

    public static final String contextRule = "pipeLineSetting/contextRule";
    public static final String reportAfterProcessing = "pipeLineSetting/report";
    public static final String fastNerCaseSensitive = "pipeLineSetting/fastNERCaseSensitive";
    public static final String forceAssignSection = "pipeLineSetting/forceAssignSection";

    public static final String dateRule = "pipeLineSetting/dateRule";
    public static final String dayInterval = "pipeLineSetting/dayInterval";
    public static final String inferAllTemporal = "pipeLineSetting/inferAllTemporal";
    public static final String saveDateAnnotation = "pipeLineSetting/saveDateAnnotation";

    public static final String featureInfRule = "pipeLineSetting/featureInfRule";
    public static final String docInfRule = "pipeLineSetting/docInfRule";
    public static final String featureMergerRule = "pipeLineSetting/featureMergerRule";
    public static final String bunchInfRule = "pipeLineSetting/bunchInfRule";

    public static final String outputEhostDir = "format/ehost";
    public static final String outputBratDir = "format/brat";
    public static final String outputXMIDir = "format/uima";
    public static final String exportTypes = "format/exportTypes";
    public static final String excelDir = "excel/directory";
    public static final String exportTxtDocs = "excel/exportTxtDocs";
    public static final String sampleSize = "excel/sampleSize";
    public static final String mentionTypes = "excel/includeMentionTypes";
    public static final String sampleOnColumn = "excel/sampleOnColumn";


    public static final String maintask = "easycie";
    public static final String annotator = "annotators/current";

    public static final String viewQueryName = "view/queryName";

    public static final String comparetask = "compare";
    public static final String targetAnnotator = "compare/targetAnnotator";
    public static final String referenceAnnotator = "compare/referenceAnnotator";
    public static final String compareReferenceTable = "compare/referenceTable";
    public static final String compareTable = "output/compareTable";
    public static final String targetRunId = "compare/targetRunId";
    public static final String referenceRunId = "compare/referenceRunId";
    public static final String strictCompare = "compare/strictCompare";
    public static final String typeFilter = "compare/typeFilter";
    public static final String categoryFilter = "compare/categoryFilter";


    public static final String customizedTypes = "customizedTypes";
    public static final String cpeDescriptor = "cpeDescriptor";

    public static final String report = "report";


    public static final String readDBConfigFileName = "import/dbFile";
    public static final String inputTableName = "import/table";
    public static final String overwrite = "import/overwrite";
    public static final String datasetId = "import/datasetId";

    public static final String paraTxtType = "txt";

    public static final String referenceTable = "reference/table";

    public static final String writeDBConfigFileName = "output/dbFile";
    public static final String snippetResultTableName = "output/snippetResultTable";
    public static final String docResultTableName = "output/docResultTable";
    public static final String bunchResultTableName = "output/bunchResultTable";


    public static final String rushRule = "nlpComponents/rush";


    public static final String sectionType = "log/sectionType";
    public static final String rushType = "log/rushType";
    public static final String cNERType = "log/cNERType";
    public static final String tNERType = "log/tNERType";
    public static final String contextType = "log/contextType";
    public static final String dateType = "log/dateType";
    public static final String featureInfType = "log/featureInfType";
    public static final String docInfType = "log/docInfType";
    public static final String metaStr = "log/metaStr";



    public static String getRelativePath(String basePath, String file) {
        if (mainBasePath == null)
            mainBasePath = basePath;
        Path pathAbsolute = Paths.get(file);
        Path pathBase = Paths.get(basePath);
        Path pathRelative = pathBase.relativize(pathAbsolute);
        return pathRelative.toString();
    }

    public static String getRelativePath(String file) {
        return getRelativePath(mainBasePath, file);
    }
}

