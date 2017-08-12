package edu.utah.bmi.simple.gui.task;

/**
 * Created by u0876964 on 11/6/16.
 */
public class ConfigKeys {
    public static final String importDir = "documents/corpusDir";

    public static final String includeFileTypes = "documents/includeFileTypes";

    public static final String annotationDir = "annotations/projectDir";
    public static final String includeAnnotationTypes = "annotations/includeAnnotationTypes";
    public static final String overWriteAnnotatorName = "annotations/overWriteAnnotatorName";
    public static final String enableSentenceSnippet = "annotations/enableSentenceSnippet";


    public static final String ruleFile = "pipeLineSetting/tRule";
    public static final String cRuleFile = "pipeLineSetting/cRule";
    public static final String contextRule = "pipeLineSetting/contextRule";
    public static final String reportPreannotating = "pipeLineSetting/report";
    public static final String fastNerCaseSensitive = "pipeLineSetting/fastNERCaseSensitive";
    public static final String featureInfRule = "pipeLineSetting/featureInfRule";
    public static final String docInfRule = "pipeLineSetting/docInfRule";

    public static final String outputEhostDir = "format/ehost";
    public static final String outputBratDir = "format/brat";
    public static final String outputXMIDir = "format/uima";
    public static final String exportTypes = "format/exportTypes";


    public static final String maintask = "easycie";
    public static final String annotator = "annotators/current";
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


    public static final String readDBConfigFile = "import/dbFile";
    public static final String inputTableName = "import/table";
    public static final String overwrite = "import/overwrite";
    public static final String datasetId = "import/datasetId";

    public static final String paraTxtType = "txt";

    public static final String referenceTable = "reference/table";

    public static final String writeConfigFileName = "output/dbFile";
    public static final String outputTableName = "output/table";

    public static final String rushRule = "nlpComponents/rush";


}
