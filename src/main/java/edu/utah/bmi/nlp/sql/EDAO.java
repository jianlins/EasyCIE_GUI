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

package edu.utah.bmi.nlp.sql;

import edu.utah.bmi.nlp.core.IOUtil;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jianlin Shi on 9/20/16.
 */
public class EDAO {
    public static Logger logger = IOUtil.getLogger(EDAO.class);
    public static HashMap<String, EDAO> instances = new HashMap<>();
    private static EDAO lastInstance;
    @Deprecated
    public boolean debug = false;
    private boolean isClosed = true;
    private String server, username, password, driver;
    private boolean concurUpdatable;
    public Connection con = null;
    public Statement stmt = null;
    private LinkedHashMap<String, ArrayList<String>> createTableSQLs;
    private LinkedHashMap<String, ArrayList<String>> createTableTemplates;

    private LinkedHashMap<String, ArrayList<String>> dropTableSQLs;
    public HashMap<String, String> insertReturnEnabledTables;
    private HashMap<String, ColumnInfo> insertTableColumnInfo;
    private HashMap<String, ColumnInfo> updateTableColumnInfo;
    private File configFile;
    public ConfigReader configReader;
//    public static boolean createTables = false;


    public HashMap<String, String> insertTemplates;
    public HashMap<String, String> queryTemplates;
    public HashMap<String, PreparedStatement> insertPreparedStatements = new HashMap<>();

    public HashMap<String, PreparedStatement> updatePreparedStatements = new HashMap<>();
    public HashMap<String, PreparedStatement> queryPreparedStatements = new HashMap<>();
    public HashMap<String, String> queries = new HashMap<>(), inserts = new HashMap<>(), updateTableSQLs = new HashMap<>();

    public int batchsize = 200, batchCounter = 0;
    public String databaseName = "";

    protected EDAO() {

    }

    public static EDAO getInstance(File configFile) {
        return getInstance(configFile, false, false, true);
    }

    public static EDAO getInstance(File configFile, boolean initiateTables, boolean overwriteExistingTables) {
        return getInstance(configFile, initiateTables, overwriteExistingTables, true);
    }

    public static EDAO getInstance(File configFile, boolean initiateTables, boolean overwriteExistingTables, boolean concurUpdatable) {
        String key = configFile.getAbsolutePath();
        try {
            if (!instances.containsKey(key)
                    || instances.get(key).isClosed()
                    || instances.get(key).con.isClosed()) {
                instances.put(key, new EDAO(configFile, initiateTables, overwriteExistingTables, concurUpdatable));
                instances.get(key).isClosed = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        lastInstance = instances.get(key);
        if (initiateTables)
            lastInstance.initiateTables(overwriteExistingTables);
        return lastInstance;
    }

    public static EDAO getLastInstance() {
        if (lastInstance != null) {
            return lastInstance;
        } else {
            logger.warning("No instance has been initiated. ");
        }
        return null;
    }


    public EDAO(File configFile) {
        initConnection(configFile, false, false, true);
    }


    protected EDAO(File configFile, boolean initiateTables, boolean overwriteExistingTables) {
        initConnection(configFile, initiateTables, overwriteExistingTables, true);
    }

    protected EDAO(File configFile, boolean initiateTables, boolean overwriteExistingTables, boolean concurUpdatable) {
        initConnection(configFile, initiateTables, overwriteExistingTables, concurUpdatable);
    }

    protected void initConnection(File configFile, boolean initiateTables, boolean overwriteExistingTables, boolean concurUpdatable) {


        this.configFile = configFile;
        configReader = ConfigReaderFactory.createConfigReader(configFile);

        server = (String) configReader.getValue("server");
        username = (String) configReader.getValue("username");
        password = (String) configReader.getValue("password");
        databaseName = (String) configReader.getValue("databaseName");
        driver = (String) configReader.getValue("driver");
        if (password.startsWith("ENC(") && password.endsWith(")")) {
            password = Decrypt.decrypt(password.substring(4, password.length() - 1));
        }
        if (configReader.getValue("concurUpdatable") != null) {
            concurUpdatable = ((String) configReader.getValue("concurUpdatable")).startsWith("t");
        }
        this.concurUpdatable = concurUpdatable;

        createTableSQLs = new LinkedHashMap<>();
        createTableTemplates = new LinkedHashMap<>();
        dropTableSQLs = new LinkedHashMap<>();
        insertTableColumnInfo = new HashMap<>();
        updateTableColumnInfo = new HashMap<>();
        updateTableSQLs = new HashMap<>();
        insertReturnEnabledTables = new HashMap<>();
        insertTemplates = new HashMap<>();
        queryTemplates = new HashMap<>();

        if (configReader.getValue("createTables") != null) {
            for (Map.Entry<String, Object> entry : ((HashMap<String, Object>) configReader.getValue("createTables")).entrySet()) {
                String tableName = entry.getKey();
                ArrayList<String> sqls = (ArrayList<String>) ((HashMap<String, Object>) entry.getValue()).get("sql");
                for (int i = 0; i < sqls.size(); i++) {
                    String sql = sqls.get(i);
                    sql = fillVariables(sql);
                    sqls.set(i, sql);
                }
                createTableSQLs.put(tableName, sqls);
            }
        }

        if (configReader.getValue("dropTables") != null) {
            for (Map.Entry<String, Object> entry : ((HashMap<String, Object>) configReader.getValue("dropTables")).entrySet()) {
                String tableName = entry.getKey();
                ArrayList<String> sqls = (ArrayList<String>) ((HashMap<String, Object>) entry.getValue()).get("sql");
                for (int i = 0; i < sqls.size(); i++) {
                    String sql = sqls.get(i);
                    sql = fillVariables(sql);
                    sqls.set(i, sql);
                }
                dropTableSQLs.put(tableName, sqls);
            }
        }

        if (configReader.getValue("templates") != null) {
            for (Map.Entry<String, Object> entry : ((LinkedHashMap<String, Object>) configReader.getValue("templates")).entrySet()) {
                String templateName = entry.getKey();
                HashMap<String, Object> values = (LinkedHashMap<String, Object>) entry.getValue();
                ArrayList<String> sqls = (ArrayList<String>) values.get("sql");
                for (int i = 0; i < sqls.size(); i++) {
                    String sql = sqls.get(i);
                    sql = fillVariables(sql);
                    sqls.set(i, sql);
                }
                createTableTemplates.put(templateName, sqls);
//                add insert sql
                if (!values.containsKey("insert")) {
                    System.err.println("Template " + templateName + " hasn't set up insert sql template.");
                    continue;
                }
                String insertTemplate = fillVariables((String) values.get("insert"));
                Object returnKey = null;
                if (values.containsKey("returnKey"))
                    returnKey = values.get("returnKey");
                insertTemplates.put(templateName, insertTemplate);
                if (returnKey != null)
                    insertReturnEnabledTables.put(templateName, (String) returnKey);
//               add query sql
                if (values.containsKey("query")) {
                    String query = fillVariables((String) values.get("query"));
                    queryTemplates.put(templateName, query);
                }
//                add drop sql
                if (values.containsKey("drop")) {
                    if (!dropTableSQLs.containsValue(templateName)) {
                        dropTableSQLs.put(templateName, new ArrayList<>());
                    }
                    ArrayList<String> dropSqls = dropTableSQLs.get(templateName);
                    for (Object dropSql : (ArrayList) values.get("drop")) {
                        dropSqls.add(fillVariables((String) dropSql));
                    }
                }
            }

        }

        if (configReader.getValue("updateStatements") != null) {
            for (Map.Entry<String, Object> entry : ((HashMap<String, Object>) configReader.getValue("updateStatements")).entrySet()) {
                String tableName = entry.getKey();
                String sql = (String) ((HashMap<String, Object>) entry.getValue()).get("sql");
                sql = fillVariables(sql);
                updateTableSQLs.put(tableName, sql);
            }
        }

        con = null;
        stmt = null;
        reConnect();

        initCheckTableStatment("queryStatements", queryPreparedStatements);

        if (initiateTables)
            initiateTables(overwriteExistingTables);
        if (configReader.getValue("queryStatements") != null) {
            initStatements(queries, "queryStatements", queryPreparedStatements);
        }


        if (configReader.getValue("insertStatements") != null) {
            initInsertStatements();
        }

        if (configReader.getValue("updateStatements") != null) {
            initUpdateStatements(inserts, "updateStatements", updatePreparedStatements, updateTableColumnInfo);
        }
//		if (configReader.getValue("dropTables") != null) {
//			initStatements("dropTables", dropPreparedStatements);
//		}

    }


    public void reConnect() {
        try {
            Class.forName(driver);
            con = DriverManager.getConnection(server, username, password);
            if (concurUpdatable)
                stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            else
                stmt = con.createStatement();
            con.setAutoCommit(false);
            this.isClosed = false;
        } catch (SQLException e) {
            logger.finest("server: " + server);
            logger.finest("username: " + username);
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    //  need to initiate this statement before create tables.
    private void initCheckTableStatment(String queryStatements, HashMap<String, PreparedStatement> queryPreparedStatements) {
        if (configReader.getValue("queryStatements") != null) {
            HashMap<String, Object> queryConfigs = (HashMap<String, Object>) configReader.getValue("queryStatements");
            if (queryConfigs.containsKey("checkTableExists")) {
                String sql = (String) ((HashMap<String, Object>) queryConfigs.get("checkTableExists")).get("sql");
                sql = fillVariables(sql);
                if (sql.indexOf("ableName}") == -1) {
                    PreparedStatement pstms;
                    try {
                        pstms = con.prepareStatement(sql);
                        queryPreparedStatements.put("checkTableExists", pstms);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                } else {
                    queries.put("checkTableExists", sql);
                }
            } else {
                System.err.println("'checkTableExists' hasn't been set up yet. You need to add it into 'queryStatements'.");
            }
        }
    }


    protected void initStatements(HashMap<String, String> sqls, String statementSetName, HashMap<String, PreparedStatement> statementHashMap) {
        for (Map.Entry<String, Object> entry : ((HashMap<String, Object>) configReader.getValue(statementSetName)).entrySet()) {
            String name = entry.getKey();
            String sql = (String) ((HashMap<String, Object>) entry.getValue()).get("sql");
            sql = fillVariables(sql);
            if (sql.indexOf("{tableName}") == -1) {
                PreparedStatement pstms;
                try {
                    pstms = con.prepareStatement(sql);
                    statementHashMap.put(name, pstms);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            sqls.put(name, sql);
        }
    }

    protected void initUpdateStatements(HashMap<String, String> sqls, String statementSetName,
                                        HashMap<String, PreparedStatement> statementHashMap,
                                        HashMap<String, ColumnInfo> updateTableColumnInfo) {
        for (Map.Entry<String, Object> entry : ((HashMap<String, Object>) configReader.getValue(statementSetName)).entrySet()) {
            String name = entry.getKey();
            String sql = (String) ((HashMap<String, Object>) entry.getValue()).get("sql");
            sql = fillVariables(sql, name);
            boolean tableExist = checkTableExits(name);
            if (tableExist) {
                parseUpdateSQLColumnInfor(name, sql, updateTableColumnInfo);
            }
            if (sql.indexOf("{tableName}") == -1) {
                PreparedStatement pstms;
                try {
                    pstms = con.prepareStatement(sql);
                    statementHashMap.put(name, pstms);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            sqls.put(name, sql);
        }
    }

    private ColumnInfo parseUpdateSQLColumnInfor(String tableName, String sql, HashMap<String, ColumnInfo> sqlColumnInfo) {
        ColumnInfo tableColumnInfo = getTableColumnInfo(tableName);
        ColumnInfo updateColumnInfo = new ColumnInfo();
        for (String section : sql.split("\\s*=\\s*\\?")) {
            String[] subsect = section.split("(,| )+");
            String columnName = "";
            if (subsect.length > 1) {
                columnName = subsect[subsect.length - 1];
            } else {
                logger.warning("SQL setting error at: " + sql);
            }
            if (!tableColumnInfo.getColumnInfo().containsKey(columnName)) {
                logger.warning("SQL setting error: column " + columnName + " does not exist in table " + tableName);
            } else {
                updateColumnInfo.addColumnInfo(columnName, tableColumnInfo.getColumnType(columnName));
            }
        }
        sqlColumnInfo.put(tableName, updateColumnInfo);
        return updateColumnInfo;
    }

    protected void initInsertStatements() {
        for (Map.Entry<String, Object> entry : ((HashMap<String, Object>) configReader.getValue("insertStatements")).entrySet()) {
            String tableName = entry.getKey();
            HashMap<String, Object> values = (HashMap<String, Object>) entry.getValue();
            String sql = (String) ((HashMap<String, Object>) entry.getValue()).get("sql");
            sql = fillVariables(sql);
            Object returnKey = null;
            if (values.containsKey("returnKey"))
                returnKey = values.get("returnKey");
            boolean tableExist = checkTableExits(tableName);
            if (tableExist) {
                parseSQLColumnInfor(tableName, sql, insertTableColumnInfo);
                addInsertPreparedStatement(tableName, sql, returnKey);
                if (returnKey != null)
                    insertReturnEnabledTables.put(tableName, (String) returnKey);

            }
        }

    }


    /**
     * @param tableName *
     * @return
     */
    private ColumnInfo parseSQLColumnInfor(String tableName, String sql, HashMap<String, ColumnInfo> tableColumnInfo) {
        String columnNames = sql.substring(sql.indexOf("(") + 1);
        String parameters = sql.substring(sql.indexOf(")"));
        parameters = parameters.substring(parameters.indexOf("(") + 1);
        parameters = parameters.substring(0, parameters.indexOf(")"));
        columnNames = columnNames.substring(0, columnNames.indexOf(")"));
        columnNames = columnNames.trim();
        String[] columns = columnNames.split(",\\s*");
        String[] paras = parameters.split(",\\s*");
        ColumnInfo tableColumnInfor = getTableColumnInfo(tableName);
        ColumnInfo insertColumnInfo = new ColumnInfo();
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i].trim();
            String para = paras[i];
            if (!para.equals("?"))
                continue;
            if (column.startsWith("'") && column.endsWith("'"))
                column.substring(1, column.length() - 1);
            else if (column.startsWith("`") && column.endsWith("`"))
                column.substring(1, column.length() - 1);
            else if (column.startsWith("\"") && column.endsWith("\""))
                column.substring(1, column.length() - 1);
            insertColumnInfo.addColumnInfo(column, tableColumnInfor.getColumnType(column));
        }
        tableColumnInfo.put(tableName, insertColumnInfo);
        return insertColumnInfo;
    }

    public void dropTable(String tableName) {
        if (checkTableExits(tableName))
            try {
                if (dropTableSQLs.containsKey(tableName)) {
                    for (String sql : dropTableSQLs.get(tableName)) {
                        logger.fine(sql);
                        if (insertPreparedStatements.containsKey(tableName)) {
                            insertPreparedStatements.get(tableName).clearBatch();
                            insertPreparedStatements.get(tableName).close();
//                            System.out.println(insertPreparedStatements.get(tableName).isClosed());
                        }
                        if (queryPreparedStatements.containsKey(tableName))
                            System.out.println(queryPreparedStatements.get(tableName).isClosed());

                        stmt.execute(sql);
                        if (!con.getAutoCommit())
                            con.commit();
                    }
                } else {
                    System.out.println("The sql to create table: " + tableName + " hasn't been set up.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

//			try {
//				if (dropPreparedStatements.containsKey(tableName)) {
//					System.out.println(tableName + "exists, drop it.");
//					dropPreparedStatements.get(tableName).execute();
//					con.commit();
//				} else {
//					System.out.println("The sql to drop table: " + tableName + " hasn't been set up.");
//				}
//			} catch (SQLException e) {
//				e.printStackTrace();
//			}

    }

    public void initiateTable(String tableName, boolean overwrite) {
        if (overwrite) {
            dropTable(tableName);
            createTableNIndexes(tableName);
        } else if (!checkExists(tableName)) {
            createTableNIndexes(tableName);
        }
    }

    private void createTableNIndexes(String tableName) {
        try {
            if (createTableSQLs.containsKey(tableName)) {
                for (String sql : createTableSQLs.get(tableName)) {
                    logger.fine(sql);
                    stmt.execute(sql);
                    if (!con.getAutoCommit())
                        con.commit();
                }
            } else {
                System.out.println("The sql to create table: " + tableName + " hasn't been set up.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void initiateTableFromTemplate(String templateName, String tableName, boolean overwrite) {
        if (dropTableSQLs.containsKey(templateName)) {
            dropTableSQLs.put(tableName, new ArrayList<>());
            for (String dropSql : dropTableSQLs.get(templateName)) {
                dropSql = dropSql.replaceAll("\\{tableName}", tableName);
                dropTableSQLs.get(tableName).add(dropSql);
            }
        }
        if (overwrite) {
            dropTable(tableName);
            createTableNIndexes(templateName, tableName);
        } else if (!checkTableExits(tableName)) {
            createTableNIndexes(templateName, tableName);
        }
        if (insertTemplates.containsKey(templateName)) {
            String insertSQL = insertTemplates.get(templateName);
            insertSQL = insertSQL.replaceAll("\\{tableName}", tableName);
            String returnKey = insertReturnEnabledTables.getOrDefault(templateName, null);
            if (returnKey != null)
                insertReturnEnabledTables.put(tableName, returnKey);
            parseSQLColumnInfor(tableName, insertSQL, insertTableColumnInfo);
            addInsertPreparedStatement(tableName, insertSQL, returnKey);
        }
        if (queryTemplates.containsKey(templateName)) {
            String sql = queryTemplates.get(templateName);
            sql = sql.replaceAll("\\{tableName}", tableName);
            PreparedStatement pstms;
            try {
                pstms = con.prepareStatement(sql);
                queryPreparedStatements.put(tableName, pstms);
            } catch (SQLException e) {
                e.printStackTrace();
            }

        }

    }

    private void createTableNIndexes(String templateName, String tableName) {
        try {
            if (createTableTemplates.containsKey(templateName)) {
                for (String sql : createTableTemplates.get(templateName)) {
                    sql = sql.replaceAll("\\{tableName}", tableName);
                    logger.fine(sql);
                    stmt.execute(sql);
                    if (!con.getAutoCommit())
                        con.commit();
                }
            } else {
                System.out.println("The sql to create table: " + tableName + " hasn't been set up.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void initiateTables(boolean overwrite) {
        for (String tableName : createTableSQLs.keySet()) {
//            System.out.println("create table: " + tableName);
            initiateTable(tableName, overwrite);
        }
    }


    public ColumnInfo getResultSetMetaData(ResultSet results) {
        ColumnInfo columnInfo = new ColumnInfo();
        try {
            ResultSetMetaData metaData = results.getMetaData();

            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
//                System.out.println(metaData.getColumnName(i)+'\t'+metaData.getColumnLabel(i));
                columnInfo.addColumnInfo(metaData.getColumnName(i), metaData.getColumnTypeName(i).toLowerCase());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return columnInfo;
    }

    public ColumnInfo getTableColumnInfo(String tableName) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Pseudo get column info from table: \n" + tableName);
            return new ColumnInfo();
        }
        ColumnInfo columnInfor = new ColumnInfo();
//        String sql = (String) configReader.getValue("getColumnsInfo");
//        sql = sql.replaceAll("\\{tableName\\}", tableName);
        RecordRowIterator recordRowIterator = queryRecordsFromPstmt("getColumnsInfo", tableName);
        while (recordRowIterator.hasNext()) {
            RecordRow recordRow = recordRowIterator.next();
            int offset = 0;
            if (recordRow.getValueByColumnId(1) instanceof Integer)
                offset = 1;
            String type = recordRow.getValueByColumnId(offset + 2) + "";
            if (type.indexOf("(") != -1) {
                type = type.substring(0, type.indexOf("("));
            }
            columnInfor.addColumnInfo((String) recordRow.getValueByColumnId(offset + 1), type.toLowerCase());
        }
        return columnInfor;
    }


    public Object[] queryRecordsNMeta(String sql) {
        RecordRowIterator recordIterator = null;
        ColumnInfo metaData = null;
        try {
//            System.out.println(sql);
            sql = fillVariables(sql);
            ResultSet rs = stmt.executeQuery(sql);
            metaData = getResultSetMetaData(rs);
            if (!con.getAutoCommit())
                con.commit();
            recordIterator = new RecordRowIterator(rs, metaData);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new Object[]{recordIterator, metaData};
    }


    public RecordRowIterator queryRecords(String sql) {
        return (RecordRowIterator) queryRecordsNMeta(sql)[0];
    }

    public Object[] queryRecordsNMetaFromPstmt(String queryName, Object... values) {
        RecordRowIterator recordIterator = null;
        ColumnInfo metaData = null;
        try {
//            System.out.println(sql);
            if (queryPreparedStatements.containsKey(queryName)) {
                PreparedStatement queryStmt = queryPreparedStatements.get(queryName);
                setPstmtValues(queryStmt, values);

                ResultSet rs = queryStmt.executeQuery();
                metaData = getResultSetMetaData(rs);
                if (!con.getAutoCommit())
                    con.commit();
                recordIterator = new RecordRowIterator(rs, metaData);
            } else if (values.length > 0 && queryPreparedStatements.containsKey(queryName + "_" + values[0])) {
//              if query contains {tableName}
                PreparedStatement queryStmt = queryPreparedStatements.get(queryName + "_" + values[0]);
                setPstmtValues(queryStmt, Arrays.copyOfRange(values, 1, values.length));
                ResultSet rs = queryStmt.executeQuery();
                metaData = getResultSetMetaData(rs);
                if (!con.getAutoCommit())
                    con.commit();
                recordIterator = new RecordRowIterator(rs, metaData);
            } else if (queries.containsKey(queryName) && values.length > 0) {
//                if query contains {tableName}, put tableName in the first values
                String sql = queries.get(queryName).replaceAll("\\{tableName}", values[0].toString());
                PreparedStatement pstms;
                try {
                    pstms = con.prepareStatement(sql);
                    queryPreparedStatements.put(queryName + "_" + values[0], pstms);
                    if (values.length > 1)
                        setPstmtValues(pstms, Arrays.copyOfRange(values, 1, values.length));
                    ResultSet rs = pstms.executeQuery();
                    metaData = getResultSetMetaData(rs);
                    if (!con.getAutoCommit())
                        con.commit();
                    recordIterator = new RecordRowIterator(rs, metaData);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Query SQL: '" + queryName + "' has not been configured.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new Object[]{recordIterator, metaData};
    }

    public RecordRowIterator queryRecordsFromPstmt(String queryName, Object... values) {
        return (RecordRowIterator) queryRecordsNMetaFromPstmt(queryName, values)[0];
    }

    public RecordRow queryRecord(String sql) {
        RecordRowIterator recordIterator = null;
        recordIterator = queryRecords(sql);
        if (recordIterator.hasNext())
            return recordIterator.next();
        return null;
    }


    protected void addInsertPreparedStatement(String tableName, String sql, Object returnKey) {
        PreparedStatement insertPstmt;
        try {
            if (returnKey != null)
                insertPstmt = con.prepareStatement(sql, new String[]{(String) returnKey});
            else
                insertPstmt = con.prepareStatement(sql);
            insertPreparedStatements.put(tableName, insertPstmt);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    protected PreparedStatement getInsertPstmt(String tableName) {
        return getPstmt(tableName, insertPreparedStatements);
    }

    protected PreparedStatement getUpdatePstmt(String tableName) {
        return getPstmt(tableName, updatePreparedStatements);
    }

    protected PreparedStatement getPstmt(String tableName, HashMap<String, PreparedStatement> preparedStatements) {
        if (!preparedStatements.containsKey(tableName)) {
            if (preparedStatements.containsKey(databaseName + "." + tableName)) {
                return preparedStatements.get(databaseName + "." + tableName);
            } else
                System.err.println(preparedStatements.getClass().getSimpleName() + " for table: " + tableName + " hasn't been set up.");
        }
        return preparedStatements.get(tableName);
    }


    private void insertRecord(PreparedStatement insertPstmt, ColumnInfo columnInfo, RecordRow record) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Pseudo insert record: \n" + record.toString("\t"));
            return;
        }
        for (Map.Entry<String, String> columnNameType : columnInfo.getColumnInfoSet()) {
            String columnName = columnNameType.getKey();
            int columnId = columnInfo.getColumnId(columnName);
            Object value = null;
            if (record.getColumnNameValues().size() > 0) {
                value = record.getValueByColumnName(columnName);
            } else if (record.getColumnIdsValues().size() > 0) {
                value = record.getValueByColumnId(columnId);
            }
            setPstmtValue(insertPstmt, columnId, value);
        }
    }

    public Object insertRecord(String tableName, RecordRow record) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(record.toString());
            return null;
        }
        PreparedStatement insertPstmt = getInsertPstmt(tableName);
        ColumnInfo columnInfo = insertTableColumnInfo.get(tableName);
        try {
            insertRecord(insertPstmt, columnInfo, record);
            insertPstmt.executeUpdate();
            if (!con.getAutoCommit())
                con.commit();
            if (insertReturnEnabledTables.containsKey(tableName)) {
                ResultSet rs = insertPstmt.getGeneratedKeys();
                if (rs.next()) {
                    Object last_inserted_id = rs.getObject(1);
                    return last_inserted_id;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateRecord(String tableName, RecordRow recordRow) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Pseudo update record to table: " + tableName + ":\n" + recordRow.toString("\t"));
            return;
        }
        HashMap<Integer, Object> idCells = recordRow.getId_cells();
        PreparedStatement updatePstmt = getUpdatePstmt(tableName);
        ColumnInfo columnInfo = updateTableColumnInfo.get(tableName);
        try {
            for (Map.Entry<String, String> columnNameType : columnInfo.getColumnInfoSet()) {
                String columnName = columnNameType.getKey();
                int columnId = columnInfo.getColumnId(columnName);
                String type = columnNameType.getValue();
                Object value = recordRow.getValueByColumnName(columnName);
//                System.out.println(columnName + "\t" + type+"\t"+(value!=null?value.getClass():null));
                switch (type) {
                    case "number":
                    case "long":
                        if (value != null)
                            updatePstmt.setLong(columnId, NumberUtils.createLong(value + ""));
                        else
                            updatePstmt.setObject(columnId, null);
                        break;
                    case "string":
                    case "varchar2":
                        updatePstmt.setString(columnId, recordRow.getStrByColumnName(columnName));
                        break;
                    case "date":
                        if (value instanceof Date)
                            value = new java.sql.Date(((Date) value).getTime());
                        updatePstmt.setDate(columnId, (java.sql.Date) value);
                        break;
                    default:
                        updatePstmt.setObject(columnId, recordRow.getValueByColumnName(columnName));
                        break;
                }


            }
            updatePstmt.executeUpdate();
            if (!con.getAutoCommit())
                con.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertRecordToBatch(String tableName, RecordRow record) {
        PreparedStatement insertPstmt = getInsertPstmt(tableName);
        ColumnInfo columnInfo = insertTableColumnInfo.get(tableName);
        try {
            insertRecord(insertPstmt, columnInfo, record);
            if (!logger.isLoggable(Level.FINE)) {
                insertPstmt.addBatch();
                batchCounter++;
                if (this.batchCounter % batchsize == 0) {
                    insertPstmt.executeBatch();
                    batchCounter = 0;
                    if (!con.getAutoCommit())
                        con.commit();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void insertRecords(String tableName, Iterable<RecordRow> records) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Pseudo insert records....");
            for (RecordRow record : records)
                System.out.println(record.toString("\t") + "\n");

            return;
        }
        PreparedStatement insertPstmt = getInsertPstmt(tableName);
        ColumnInfo columnInfo = insertTableColumnInfo.get(tableName);
        try {
            for (RecordRow record : records) {
                insertRecord(insertPstmt, columnInfo, record);
                insertPstmt.addBatch();
                batchCounter++;
                if (this.batchCounter % batchsize == 0) {
                    insertPstmt.executeBatch();
                    batchCounter = 0;
                    if (!con.getAutoCommit())
                        con.commit();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void endBatchInsert(String... tableNames) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Pseudo execute endBatchInsert--finish whatever left in the batch.");
            return;
        }
        try {
            if (tableNames.length > 0) {
                for (String tableName : tableNames)
                    insertPreparedStatements.get(tableName).executeBatch();
            } else {
                if (!con.getAutoCommit())
                    for (PreparedStatement sts : insertPreparedStatements.values()) {
                        if (!sts.isClosed() && !sts.isCloseOnCompletion())
                            sts.executeBatch();
                    }
            }
            if (!con.getAutoCommit())
                con.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setPstmtValue(PreparedStatement insertPstmt, Integer key, Object value) {
        try {
            if (value != null) {
//                System.out.println(key+"\t"+value);
                insertPstmt.setObject(key, value);
            } else {
                insertPstmt.setNull(key, Types.NULL);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setPstmtValues(PreparedStatement insertPstmt, Object... values) {
        try {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value != null) {
//                System.out.println(key+"\t"+value);
                    insertPstmt.setObject(i + 1, value);
                } else {
                    insertPstmt.setNull(i + 1, Types.NULL);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public String fillVariables(String query, String... tableName) {
        query = query.replaceAll("\\{databaseName\\}", databaseName);
        if (tableName.length > 0)
            query = query.replaceAll("\\{tableName\\}", tableName[0]);
        return query;
    }


    public int countRecords(String countSqlName, Object... values) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(countSqlName);
            return 1;
        }
        int total = 0;
        RecordRowIterator rsIter = queryRecordsFromPstmt(countSqlName, values);
        if (rsIter.hasNext()) {
            RecordRow recordRow = rsIter.next();
            Object value = recordRow.getValueByColumnId(1);
            if (value instanceof Long)
                total = ((Long) value).intValue();
            else
                total = (int) recordRow.getValueByColumnId(1);
        }
        return total;
    }


    public void close() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("pseudo close ldao...");
            this.isClosed = true;
            return;
        }
        try {
            if (!con.isClosed()) {
                endBatchInsert();
                for (PreparedStatement stat : insertPreparedStatements.values()) {
                    stat.close();
                }
                stmt.close();
                con.close();
                this.isClosed = true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean checkTableExits(String tableName) {
//        bypass sqlite jdbc bug
        try {
//            System.out.println(con.getMetaData().getDriverName());
            if (con.getMetaData().getDriverName().toLowerCase().startsWith("sqlite")) {
                return checkExists(tableName);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return checkExits("checkTableExists", tableName);
    }

    public boolean checkSequenceExists(String sequenceName) {
        return checkExits("checkSequenceExists", sequenceName);
    }

    public boolean checkTriggerExists(String triggerName) {
        return checkExits("checkTriggerExists", triggerName);
    }

    public boolean checkExits(String checkQueryName, String objectName) {

        //          bypass sqlite jdbc bug
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("pseudo " + checkQueryName + " for: " + objectName);
            return true;
        }
        RecordRowIterator recordRowIterator = queryRecordsFromPstmt(checkQueryName, objectName);
        int count = 0;
        if (recordRowIterator.hasNext()) {
            RecordRow recordRow = recordRowIterator.next();
            Object value = recordRow.getValueByColumnId(1);
            if (value instanceof Long)
                count = ((Long) value).intValue();
            else
                count = (int) recordRow.getValueByColumnId(1);
        }
        return count != 0;
    }

    public boolean checkExists(String tableName) {
        try {
            if (databaseName != null && databaseName.length() > 0)
                stmt.execute("SELECT COUNT(*) FROM " + databaseName + "." + tableName);
            else
                stmt.execute("SELECT COUNT(*) FROM " + tableName);
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    private String getSimpleTableName(String tableName) {
        int pointer = tableName.indexOf(".");
        if (pointer != -1) {
            tableName = tableName.substring(pointer + 1, tableName.length());
        }
        return tableName;
    }

    public int getLastId(String tableName) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("pseudo getLastId of table: " + tableName);
            return 1;
        }
        int id = 0;
        RecordRowIterator recordRowIterator = queryRecordsFromPstmt("lastIdof" + tableName);
        if (recordRowIterator.hasNext()) {
            RecordRow recordRow = recordRowIterator.next();
            Object value = recordRow.getValueByColumnId(1);
            if (value instanceof Long)
                id = ((Long) value).intValue();
            else
                id = (int) recordRow.getValueByColumnId(1);
        }
//        String sql = (String) configReader.getValue("getLastId");
//        sql = sql.replaceAll("\\{tableName\\}", tableName);
//        sql = sql.replaceAll("\\{columnName\\}", columnName);
//
//        try {
//            ResultSet results = stmt.executeQuery(sql);
//            if (results.next()) {
//                id = results.getInt(1);
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
        return id;
    }

    public String formatTableName(String tableName) {
        if (tableName.indexOf(".") == -1) {
            tableName = databaseName + "." + tableName;
        }
        return tableName;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public File getConfigFile() {
        return configFile;
    }
}
