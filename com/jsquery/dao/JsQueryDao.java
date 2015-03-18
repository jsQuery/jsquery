package com.jsquery.dao;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletOutputStream;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Element;
import org.springframework.stereotype.Service;

import com.jsquery.jsqueryengine.JsQueryEngine;
import com.jsquery.jsqueryengine.JsQueryUtil;
import com.jsquery.jsqueryengine.VData;
import com.jsquery.jsqueryengine.VDataSet;

/**
 * VDataSet 및 VData 를 이용한 Dao 서비스를 제공한다.
 * 
 * @author 최현수
 * @since 2012.08.08
 */
@SuppressWarnings("deprecation")
@Service("JsQueryDao")
public class JsQueryDao
{
    private static final Logger logger = Logger.getLogger(JsQueryDao.class);

    protected static HashMap<String, String> RUNTIMEQUERYMAP2 = new HashMap<String, String>();
    protected static final String MAXRECORD = "MAXRECORD";
    protected static final int INSERT = VDataSet.INSERT;
    protected static final int UPDATE = VDataSet.UPDATE;
    protected static final int DELETE = VDataSet.DELETE;
    protected static final int LOG_DEBUG = 0;
    protected static final int LOG_INFO = 1;
    protected static final int LOG_WARNNING = 2;
    protected static final int LOG_ERROR = 3;
    protected static final String LOGLINE = "--------------------------------------------------------------------------------------------------------------------------------------------------";
    protected static final String NEWLINE = "\r\n";

    private String GS_QUERY_MAXRECORDS = JsQueryUtil.getProperty("GS_QUERY_MAXRECORDS");
    private String GS_QUERY_LOG = JsQueryUtil.getProperty("GS_QUERY_LOG");
    private String GS_QUERY_LOGLEVEL = JsQueryUtil.getProperty("GS_QUERY_LOGLEVEL");
    private String GS_QUERY_LOGFILTER = JsQueryUtil.getProperty("GS_QUERY_LOGFILTER");
    

    public JsQueryDao()
    {
    }

    /**
     * SQL문장의 #동적처리할 문장을 Parsing처리하여 조건문의 참/거짓을 판단하여 그에 대응하는 SQL문장으로
     * PreCompile처리한다.
     * 
     * @param String sXmlQueryPath XML QUERY의 경로
     * @param VData inVl 화면에서 입력받은 입력 파라미터
     * @return Dataset XMLQUERY 의 실행결과 Dataset
     * @author 최현수
     * @date 2011.02.15
     */
    public VDataSet executeQueryForList(String queryID, VData inVl) throws Exception
    {
        Connection xpConnection = null;
        PreparedStatement xpPstmt = null;
        ResultSet xpResultset = null;
        VDataSet outDataset = new VDataSet();

        String query;
        String querylog = "";
        String currThreadName = getThreadID();
        int maxRecordCount = Integer.parseInt(GS_QUERY_MAXRECORDS);

        try
        {
            long startTime = System.currentTimeMillis();

            /*-----------------------------------------------------------------------------------------
             *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
            -----------------------------------------------------------------------------------------*/
            VData upperVl = getUpperCaseVData(inVl);

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
            -----------------------------------------------------------------------------------------*/

            HashMap queryInfo = getQueryInfoByQueryID(queryID, upperVl);

            query = getOracleTraceLogFormat(queryID) + (String) queryInfo.get("QUERY");
            ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");
            xpConnection = getConnection((String) queryInfo.get("CONNECTION"));

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL의 실제 Runtime SQL log변환처리
            -----------------------------------------------------------------------------------------*/
            ArrayList logParamList = new ArrayList();
            for(int j = 0;j < bindParamList.size();j++)
            {
                logParamList.add(bindParamList.get(j));
            }

            Collections.sort(logParamList);
            querylog = query;
            String paramValue = null;

            for(int j = logParamList.size() - 1;j > -1;j--)
            {
                paramValue = upperVl.getString((String) logParamList.get(j));
                if(isNull(paramValue))
                {
                    querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "NULL");
                }
                else
                {
                    try
                    {
                        // 특수문자가 들어간경우는 String.replaceAll 로 처리가 불가능하다.
                        if(paramValue.indexOf("$") != -1)
                        {
                            querylog = replaceAll(querylog, ":" + logParamList.get(j).toString().toLowerCase(), "'" + paramValue + "'");
                            querylog = replaceAll(querylog, ":" + logParamList.get(j).toString().toUpperCase(), "'" + paramValue + "'");
                        }
                        else
                        {
                            querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'" + paramValue + "'");
                        }
                    }
                    catch(Exception e)
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'java.lang.IllegalArgumentException'");
                    }
                }
            }

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL 파싱처리된 SQL의 Prestatement 생성
            -----------------------------------------------------------------------------------------*/
            xpPstmt = xpConnection.prepareStatement(query);
            xpPstmt.clearParameters();
            int paramCount = logParamList.size();
            // int metaParamCount =
            // xpPstmt.getParameterMetaData().getParameterCount();

            // prepareStatement 로 처리할때 파라미터의 갯수를 잘못 해석하는 JDBC드라이브의 버그가 존재함 이럴경우에
            // SQL문장의 파라미터갯수는 예를 들어 4개 인데.. JDBC는 14개 이런식으로 잘못해석 하고
            // 이때 프로그램에서는 4개만 값을 셋팅하고 나머지 10개에 대해서 값을 설정하지 않으면 IN/OUT 갯수가 맞지 않다란
            // 오류를 밷어낸다.
            // 그래서 이를 Metadata의 Parameter갯수만큼 초기화를 시킨다.
            // by 최현수 2011.12.27
            // System.out.println("paramCount : " + paramCount);
            try
            {
                for(int j = 0;j < paramCount;j++)
                {
                    xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                }
            }
            catch(Exception e)
            {
            }

            /*-----------------------------------------------------------------------------------------
             *   Prestatement Bind Paramter 설정
            -----------------------------------------------------------------------------------------*/
            String sqlBindPram = null;
            for(int j = 0;j < paramCount;j++)
            {
                try
                {
                    sqlBindPram = upperVl.getString((String) bindParamList.get(j));
                    if(isNull(sqlBindPram))
                    {
                        xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                    }
                    else
                    {
                        xpPstmt.setObject(j + 1, sqlBindPram);
                    }
                }
                // 입력변수값이 없으면 해당변수에 대해서 NULL로 처리한다.
                catch(Exception e)
                {
                    xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                }
            }

            // 콘솔용로그데이터 변수
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ") Run..."+NEWLINE;

            // 시스템콘솔로그용
            if( isQueryLogFilter(queryID) == false )
            {
                makeLog(LOG_INFO, queryHeader);
                makeLog(LOG_DEBUG, LOGLINE + "\r\n" + stripEmptyLine(querylog) + "\r\n" + LOGLINE+NEWLINE);
            }

            /*-----------------------------------------------------------------------------------------
             *   Prestatement의 실행 및 결과 Recordset 획득
            -----------------------------------------------------------------------------------------*/
            xpPstmt.execute();
            
            /*-----------------------------------------------------------------------------------------
             *   ORACLE SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long OracleTime = System.currentTimeMillis();
            double OracleRumTime = (double) (OracleTime - startTime) / (double) 1000;

            xpResultset = xpPstmt.getResultSet();
            ResultSetMetaData rsmd = xpResultset.getMetaData();
            int col_cnt = rsmd.getColumnCount();
            String[] coumidList = new String[col_cnt + 1];
            int[] columtypeList = new int[col_cnt + 1];

            /*-----------------------------------------------------------------------------------------
             *   RecordSet을 이용한 XP 출력 Dataset생성처리
            -----------------------------------------------------------------------------------------*/
            String datasetColumName = null;
            for(int j = 1;j <= col_cnt;j++)
            {
                datasetColumName = rsmd.getColumnName(j).toUpperCase();
                coumidList[j] = rsmd.getColumnName(j).toUpperCase();
                
                if(rsmd.getColumnTypeName(j).equalsIgnoreCase("LONG") || rsmd.getColumnTypeName(j).equalsIgnoreCase("CLOB"))
                {
                    outDataset.addColumn(datasetColumName, VDataSet.STRING);
                    columtypeList[j] = 1;
                }
                else if(rsmd.getColumnTypeName(j).equalsIgnoreCase("BLOB") )
                {
                    outDataset.addColumn(datasetColumName, VDataSet.BLOB);
                    columtypeList[j] = 2;
                }
                else if( rsmd.getColumnTypeName(j).equalsIgnoreCase("LONG RAW") )
                {
                    outDataset.addColumn(datasetColumName, VDataSet.BLOB);
                    columtypeList[j] = 4;
                }
                else if(rsmd.getColumnTypeName(j).equalsIgnoreCase("NUMBER"))
                {
                    outDataset.addColumn(datasetColumName, VDataSet.BIG_DECIMAL);
                    columtypeList[j] = 3;
                }
                else
                {
                    outDataset.addColumn(datasetColumName, VDataSet.STRING, (int) rsmd.getColumnDisplaySize(j));
                    columtypeList[j] = 3;
                }
            }

            int nColoum;
            int rowCount = 0;
            int longtypeChar;
            StringBuffer blobBuffer;
            Reader blobReader;
            String blobString = "";
            while(xpResultset.next())
            {
                outDataset.newRow();

                for(nColoum = 1;nColoum <= col_cnt;nColoum++)
                {
                    switch(columtypeList[nColoum])
                    {
                    case 1:

                        blobString = "";
                        blobBuffer = new StringBuffer();
                        blobReader = xpResultset.getCharacterStream(coumidList[nColoum]);
                        if(blobReader != null)
                        {
                            longtypeChar = 0;
                            while((longtypeChar = blobReader.read()) != -1)
                            {
                                blobBuffer.append((char) longtypeChar);
                            }
                            blobString = blobBuffer.toString();
                            if(blobReader != null)
                            {
                                blobReader.close();
                            }

                            outDataset.set(rowCount, coumidList[nColoum], blobString);
                        }

                        break;

                    case 2:

                        Blob myblob = (Blob) xpResultset.getBlob(coumidList[nColoum]);
                        if(myblob != null)
                        {
                            ByteArrayOutputStream ous = new ByteArrayOutputStream();
                            InputStream ios = myblob.getBinaryStream();
                            byte[] buffer = new byte[4096];
                            int read = 0;
                            while((read = ios.read(buffer)) != -1)
                            {
                                ous.write(buffer, 0, read);
                            }                            
                            outDataset.set(rowCount, coumidList[nColoum], ous.toByteArray());
                        }
                        break;

                    case 3:

                        outDataset.set(rowCount, coumidList[nColoum], xpResultset.getString(nColoum));
                        break;
                        
                    case 4:

                        byte[] buffer = xpResultset.getBytes(coumidList[nColoum]);
                        outDataset.set(rowCount, coumidList[nColoum], buffer);
                        break;
                        
                    }
                }

                if(++rowCount == maxRecordCount)
                {
                    throw new Exception(MAXRECORD);
                }
            }

            /*-----------------------------------------------------------------------------------------
             *   SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long dataProcessTime = System.currentTimeMillis();
            double dataProcessRunTime = (double) (dataProcessTime - OracleTime) / (double) 1000;
            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            String queryResult = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")" + " End...\n" + rowCount + " Records (Oracle:" + OracleRumTime + "Sec Fetch:" + dataProcessRunTime + "Sec Total:" + runTime + "Sec)"+NEWLINE+NEWLINE;
            if( isQueryLogFilter(queryID) == false )
            {
                makeLog(LOG_INFO, queryResult);
            }
            return outDataset;
        }
        catch(SQLException e)
        {
            if(xpResultset != null)
            {
                xpResultset.close();
                xpResultset = null;
            }

            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }

            // debug 가 아닐경우는 에러시 SQL 을 로그를 찍어준다. 
            if( !"debug".equalsIgnoreCase(GS_QUERY_LOGLEVEL) )
            {
                makeLog(LOG_ERROR, stripEmptyLine(querylog));
            }
            
            String queryResult = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")" + " ERROR"+NEWLINE;
            makeLog(LOG_ERROR, queryResult);
            
            throw new SQLException(e);
        }
        finally
        {
            if(xpResultset != null)
            {
                xpResultset.close();
                xpResultset = null;
            }

            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }
        }
    }

    /**
     * SQL문장을 먼저 RowRecord갯수를 먼저 카운팅하고 그결과를 리턴한다.
     * 
     * @param String sXmlQueryPath XML QUERY의 경로
     * @param VData inVl 화면에서 입력받은 입력 파라미터
     * @return Dataset XMLQUERY 의 실행결과 Dataset
     * @author 최현수
     * @date 2011.02.15
     */
    public int executeQueryForRowCount(String queryID, VData inVl) throws Exception
    {
        Connection xpConnection = null;
        PreparedStatement xpPstmt = null;
        ResultSet xpResultset = null;
        String query;
        String querylog;
        String currThreadName = getThreadID();

        try
        {
            long startTime = System.currentTimeMillis();

            /*-----------------------------------------------------------------------------------------
             *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
            -----------------------------------------------------------------------------------------*/
            VData upperVl = getUpperCaseVData(inVl);

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
            -----------------------------------------------------------------------------------------*/
            HashMap<String, Serializable> queryInfo = getQueryInfoByQueryID(queryID, upperVl);
            query = (String) queryInfo.get("QUERY");
            ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");
            xpConnection = getConnection((String) queryInfo.get("CONNECTION"));

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL의 실제 Runtime SQL log변환처리
            -----------------------------------------------------------------------------------------*/
            ArrayList logParamList = new ArrayList();
            for(int j = 0;j < bindParamList.size();j++)
            {
                logParamList.add(bindParamList.get(j));
            }

            Collections.sort(logParamList);
            querylog = query;
            String paramValue = null;
            for(int j = logParamList.size() - 1;j > -1;j--)
            {
                paramValue = upperVl.getString((String) logParamList.get(j));
                if(isNull(paramValue))
                {
                    querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "NULL");
                }
                else
                {
                    try
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'" + paramValue + "'");
                    }
                    catch(Exception e)
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'java.lang.IllegalArgumentException'");
                    }
                }
            }

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL 파싱처리된 SQL의 Prestatement 생성
            -----------------------------------------------------------------------------------------*/
            query = "SELECT COUNT(*) FROM (" + query + ")";
            xpPstmt = xpConnection.prepareStatement(query);
            xpPstmt.clearParameters();

            int paramCount = logParamList.size();

            /*-----------------------------------------------------------------------------------------
             *   Prestatement Bind Paramter 설정
            -----------------------------------------------------------------------------------------*/
            String sqlBindPram = null;
            for(int j = 0;j < paramCount;j++)
            {
                try
                {
                    sqlBindPram = upperVl.getString((String) bindParamList.get(j));
                    if(isNull(sqlBindPram))
                    {
                        xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                    }
                    else
                    {
                        xpPstmt.setObject(j + 1, sqlBindPram);
                    }
                }
                // 입력변수값이 없으면 해당변수에 대해서 NULL로 처리한다.
                catch(Exception e)
                {
                    xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                }
            }

            // 콘솔용로그데이터 변수
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ".getRowCount())"+NEWLINE;

            // 시스템콘솔로그용
            makeLog(LOG_INFO, queryHeader);
            makeLog(LOG_DEBUG, LOGLINE + "\r\n" + querylog + "\r\n"+LOGLINE+NEWLINE);

            /*-----------------------------------------------------------------------------------------
             *   Prestatement의 실행 및 결과 Recordset 획득
            -----------------------------------------------------------------------------------------*/
            xpPstmt.execute();
            xpResultset = xpPstmt.getResultSet();
            xpResultset.next();
            int rowCount = xpResultset.getInt(1);

            /*-----------------------------------------------------------------------------------------
             *   ORACLE SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long OracleTime = System.currentTimeMillis();
            double OracleRumTime = (double) (OracleTime - startTime) / (double) 1000;

            /*-----------------------------------------------------------------------------------------
             *   SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long dataProcessTime = System.currentTimeMillis();
            double dataProcessRunTime = (double) (dataProcessTime - OracleTime) / (double) 1000;
            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            String queryResult = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + rowCount + " Records (Oracle:" + OracleRumTime + "Sec Fetch:" + dataProcessRunTime + "Sec Total:" + runTime + "Sec)"+NEWLINE;
            makeLog(LOG_INFO, queryResult);

            // 사용한리소스 메모리해제처리
            logParamList = null;

            return rowCount;
        }
        catch(SQLException e)
        {
            if(xpResultset != null)
            {
                xpResultset.close();
                xpResultset = null;
            }

            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }

            // 시스템콘솔로그용
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")"+NEWLINE;
            makeLog(LOG_ERROR, queryHeader + "\n" + e.getMessage() + NEWLINE);

            throw new SQLException(e);
        }
        finally
        {
            if(xpResultset != null)
            {
                xpResultset.close();
                xpResultset = null;
            }

            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }
        }
    }

    /**
     * Dataset의 Row Data를 VData로 값을 꺼내온다.
     * 
     * @param VDataSet XP Dataset
     * @return VData RowData
     * @author 최현수
     * @throws Exception
     * @date 2011.07.26
     */
    public VData executeQueryForRowData(String sXmlQueryPath, VData paramVl) throws Exception
    {
        VData rtnVl = null;

        VDataSet resultDataset = executeQueryForList(sXmlQueryPath, paramVl);
        if(resultDataset.getRowCount() != 0)
        {
            return resultDataset.getVData(0);
        }
        else
        {
            return rtnVl;
        }
    }

    /**
     * SQL문장의 ExecuteUpdate처리한다. XMLQUERY가 아닌 직접Query를 이용한다. (단일건처리)
     * 
     * @param Stirng ConnectinName DB Connection 대상
     * @param Stirng Query 입력/수정/삭제처리를 위한 SQL문장
     * @param VData 처리할 파라미터
     * @return int updateCount 입력/수정/삭제 처리건수
     * @author 최현수
     * @throws Exception
     * @throws
     * @date 2011.09.15
     */
    public int executeUpdate(String connectionName, String queryString, VData inVl) throws Exception
    {
        Connection xpConnection = null;
        PreparedStatement xpPstmt = null;
        int updateCount = 0;

        String parsedQuery = "";
        String query;
        String querylog = null;
        String currThreadName = getThreadID();

        try
        {
            long startTime = System.currentTimeMillis();

            /*-----------------------------------------------------------------------------------------
             *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
            -----------------------------------------------------------------------------------------*/
            VData upperVl = getUpperCaseVData(inVl);

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
            -----------------------------------------------------------------------------------------*/
            HashMap<String, Serializable> queryInfo = getQueryInfoByQuery(queryString, upperVl);
            query = getOracleTraceLogFormat(null) + (String) queryInfo.get("QUERY");
            parsedQuery = query;
            ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");
            xpConnection = getConnection(connectionName);

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL의 실제 Runtime SQL log변환처리
            -----------------------------------------------------------------------------------------*/
            ArrayList sqllogBindParamList = new ArrayList();
            for(int j = 0;j < bindParamList.size();j++)
            {
                sqllogBindParamList.add(bindParamList.get(j));
            }

            Collections.sort(sqllogBindParamList);
            querylog = query;
            String paramValue = "";
            for(int j = sqllogBindParamList.size() - 1;j > -1;j--)
            {
                paramValue = upperVl.getString((String) sqllogBindParamList.get(j));
                if(isNull(paramValue))
                {
                    querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                }
                else
                {
                    try
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "'" + upperVl.getString((String) sqllogBindParamList.get(j)) + "'");
                    }
                    catch(Exception e)
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "'java.lang.IllegalArgumentException'");
                    }
                }
            }

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL 파싱처리된 SQL의 Prestatement 생성
            -----------------------------------------------------------------------------------------*/
            xpPstmt = xpConnection.prepareStatement(getQuestionMarkSQL(parsedQuery, sqllogBindParamList));
            int paramCount = sqllogBindParamList.size();

            /*-----------------------------------------------------------------------------------------
             *   Prestatement Bind Paramter 설정
            -----------------------------------------------------------------------------------------*/
            Object bindobj;
            for(int j = 0;j < paramCount;j++)
            {
                try
                {
                    bindobj = upperVl.get(bindParamList.get(j));
                    if(bindobj == null)
                    {
                        xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                    }
                    else
                    {
                        xpPstmt.setObject(j + 1, bindobj);
                    }
                }
                // 입력변수값이 없으면 해당변수에 대해서 NULL로 처리한다.
                catch(Exception e)
                {
                    xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                }
            }

            if(paramCount == 0)
                xpPstmt.clearParameters();

            /*-----------------------------------------------------------------------------------------
             *   SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            // 콘솔용로그데이터 변수
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName()+NEWLINE;

            // 시스템콘솔로그용
            makeLog(LOG_INFO, queryHeader);
            makeLog(LOG_DEBUG, LOGLINE + "\r\n" + querylog + "\r\n" + LOGLINE + NEWLINE);

            /*-----------------------------------------------------------------------------------------
             *   Prestatement의 실행 및 결과 Recordset 획득
            -----------------------------------------------------------------------------------------*/
            updateCount = xpPstmt.executeUpdate();

            String queryResult = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + updateCount + " Records Updated " + runTime + "Sec"+NEWLINE+NEWLINE;
            makeLog(LOG_INFO, queryResult);

            // 사용한리소스 메모리해제처리
            sqllogBindParamList = null;
        }
        catch(Exception e1)
        {
            // debug 가 아닐경우는 에러시 SQL 을 로그를 찍어준다. 
            if( !"debug".equalsIgnoreCase(GS_QUERY_LOGLEVEL) )
            {
                makeLog(LOG_ERROR, stripEmptyLine(querylog));
            }
            
            // 콘솔로그처리
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + connectionName + ")"+NEWLINE;
            makeLog(LOG_ERROR, queryHeader + "\n" + e1.getMessage() + NEWLINE+NEWLINE);
            throw new Exception(e1);
        }
        finally
        {
            JsQueryEngine.getInstance().setTransactionFlag();            
            
            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }
        }
        return updateCount;
    }

    /**
     * SQL문장의 ExecuteUpdate처리한다. (단일건처리)
     * 
     * @param Stirng sXmlQueryPath 입력/수정/삭제처리를 위한 SQL Statement
     * @param VData 처리할 파라미터
     * @return int updateCount 입력/수정/삭제 처리건수
     * @author 최현수
     * @throws Exception
     * @throws
     * @date 2011.07.25
     */
    public int executeUpdate(String queryID, VData inVl) throws Exception
    {
        Connection xpConnection = null;
        PreparedStatement xpPstmt = null;
        int updateCount = 0;

        String parsedQuery = "";
        String query;
        String querylog = "";
        String currThreadName = getThreadID();

        try
        {
            long startTime = System.currentTimeMillis();

            /*-----------------------------------------------------------------------------------------
             *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
            -----------------------------------------------------------------------------------------*/
            VData upperVl = getUpperCaseVData(inVl);

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
            -----------------------------------------------------------------------------------------*/
            HashMap<String, Serializable> queryInfo = getQueryInfoByQueryID(queryID, upperVl);
            query = getOracleTraceLogFormat(queryID) + (String) queryInfo.get("QUERY");
            parsedQuery = query;
            ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");
            xpConnection = getConnection((String) queryInfo.get("CONNECTION"));

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL의 실제 Runtime SQL log변환처리
            -----------------------------------------------------------------------------------------*/
            ArrayList sqllogBindParamList = new ArrayList();
            for(int j = 0;j < bindParamList.size();j++)
            {
                sqllogBindParamList.add(bindParamList.get(j));
            }

            Collections.sort(sqllogBindParamList);
            querylog = query;
            String paramValue = "";
            for(int j = sqllogBindParamList.size() - 1;j > -1;j--)
            {
                paramValue = upperVl.getString((String) sqllogBindParamList.get(j));
                if(isNull(paramValue))
                {
                    querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                }
                else
                {
                    try
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "'" + upperVl.getString((String) sqllogBindParamList.get(j)) + "'");
                    }
                    catch(Exception e)
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "'java.lang.IllegalArgumentException'");
                    }
                }
            }

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL 파싱처리된 SQL의 Prestatement 생성
            -----------------------------------------------------------------------------------------*/
            xpPstmt = xpConnection.prepareStatement(getQuestionMarkSQL(parsedQuery, sqllogBindParamList));
            int paramCount = sqllogBindParamList.size();

            /*-----------------------------------------------------------------------------------------
             *   Prestatement Bind Paramter 설정
            -----------------------------------------------------------------------------------------*/
            Object bindobj;
            for(int j = 0;j < paramCount;j++)
            {
                try
                {
                    bindobj = upperVl.get(bindParamList.get(j));
                    if(bindobj == null)
                    {
                        xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                    }
                    else
                    {
                        xpPstmt.setObject(j + 1, bindobj);
                    }
                }
                // 입력변수값이 없으면 해당변수에 대해서 NULL로 처리한다.
                catch(Exception e)
                {
                    xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                }
            }

            if(paramCount == 0)
                xpPstmt.clearParameters();

            /*-----------------------------------------------------------------------------------------
             *   SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            // 콘솔용로그데이터 변수
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")"+NEWLINE;

            // 시스템콘솔로그용
            if(isQueryLogFilter(queryID) == false)
            {
                makeLog(LOG_INFO, queryHeader);
                makeLog(LOG_DEBUG, LOGLINE + "\r\n" + querylog + "\r\n" + LOGLINE + NEWLINE);
            }

            /*-----------------------------------------------------------------------------------------
             *   Prestatement의 실행 및 결과 Recordset 획득
            -----------------------------------------------------------------------------------------*/
            updateCount = xpPstmt.executeUpdate();

            String queryResult = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + updateCount + " Records Updated " + runTime + "Sec"+NEWLINE+NEWLINE;
            if(isQueryLogFilter(queryID) == false)
            {
                makeLog(LOG_INFO, queryResult);
            }
        }
        catch(Exception e1)
        {
            // debug 가 아닐경우는 에러시 SQL 을 로그를 찍어준다. 
            if( !"debug".equalsIgnoreCase(GS_QUERY_LOGLEVEL) )
            {
                makeLog(LOG_ERROR, stripEmptyLine(querylog));
            }
            
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")"+NEWLINE;
            makeLog(LOG_ERROR, queryHeader + "\n" + querylog + "\n" + e1.getMessage() + NEWLINE);
            throw new Exception(e1);
        }
        finally
        {
            JsQueryEngine.getInstance().setTransactionFlag();            
            
            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }
        }
        return updateCount;
    }

    /**
     * SQL문장을 Dataset으로 ROWSTATUS를 이용하여 입력/수정/삭제처리를 한다. (멀티건처리)
     * 
     * @param Stirng QueryID
     * @param VDataSet 처리할 대상 Dataset
     * @return int
     * @author 최현수
     * @throws Exception
     * @date 2014.04.29
     */
    public int executeUpdate(String queryID, VDataSet inputDataset) throws Exception
    {
        Connection xpConnection = null;
        PreparedStatement xpPstmt = null;

        int i = 0;
        String parsedQuery = "";
        String querylog = "";
        StringBuffer logBuffer = new StringBuffer();
        String currThreadName = getThreadID();

        try
        {
            long startTime = System.currentTimeMillis();
            int datasetRowCount = inputDataset.getRowCount();
            if(datasetRowCount == 0)
            {
                return 0;
            }

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
            -----------------------------------------------------------------------------------------*/
            HashMap<String, Serializable> queryInfo = getQueryInfoByQueryID(queryID, inputDataset.getVData(0));
            parsedQuery = getOracleTraceLogFormat(queryID) + (String) queryInfo.get("QUERY");
            ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");
            xpConnection = getConnection((String) queryInfo.get("CONNECTION"));

            /*-----------------------------------------------------------------------------------------
             *    첫번째 파라미터를 기준으로 prepareStatement 를 준비한다.  
            -----------------------------------------------------------------------------------------*/
            if(datasetRowCount > 0)
            {
                VData saveRowVl = inputDataset.getVData(0);

                /*-----------------------------------------------------------------------------------------
                 *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
                -----------------------------------------------------------------------------------------*/
                VData upperVl = getUpperCaseVData(saveRowVl);

                /*-----------------------------------------------------------------------------------------
                 *   Dynamic SQL의 실제 Runtime SQL log변환처리
                -----------------------------------------------------------------------------------------*/
                ArrayList sqllogBindParamList = new ArrayList();
                for(int j = 0;j < bindParamList.size();j++)
                {
                    sqllogBindParamList.add(bindParamList.get(j));
                }

                Collections.sort(sqllogBindParamList);
                querylog = parsedQuery;
                String sqlLogParam = "";
                for(int j = sqllogBindParamList.size() - 1;j > -1;j--)
                {
                    sqlLogParam = upperVl.getString((String) sqllogBindParamList.get(j));
                    if(isNull(sqlLogParam))
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                    }
                    else
                    {
                        try
                        {
                            querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "'" + getSQLLogString(upperVl.getString((String) sqllogBindParamList.get(j))) + "'");
                        }
                        catch(Exception e)
                        {
                            querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                        }
                    }
                }

                // SELECT가 아닌 CUD의 경우는 입력값에 :로 들어오는값에 대한 로그를 정확하게 찍기위해서
                querylog = querylog.replaceAll("#getSQLLogStringTempString#", ":");
                logBuffer.append("\n" + querylog);

                xpPstmt = xpConnection.prepareStatement(getQuestionMarkSQL(parsedQuery, sqllogBindParamList));
            }

            for(i = 0;i < datasetRowCount;i++)
            {
                VData saveRowVl = inputDataset.getVData(i);

                /*-----------------------------------------------------------------------------------------
                 *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
                -----------------------------------------------------------------------------------------*/
                VData upperVl = getUpperCaseVData(saveRowVl);

                /*-----------------------------------------------------------------------------------------
                 *   Dynamic SQL의 실제 Runtime SQL log변환처리
                -----------------------------------------------------------------------------------------*/
                ArrayList sqllogBindParamList = new ArrayList();
                for(int j = 0;j < bindParamList.size();j++)
                {
                    sqllogBindParamList.add(bindParamList.get(j));
                }

                Collections.sort(sqllogBindParamList);

                // 로그가 너무 많으면 메모리를 많이 쓰기 때문에 10개로 자른다.
                if(i < 11)
                {
                    querylog = parsedQuery;
                    String sqlLogParam = "";
                    for(int j = sqllogBindParamList.size() - 1;j > -1;j--)
                    {
                        sqlLogParam = upperVl.getString((String) sqllogBindParamList.get(j));
                        if(isNull(sqlLogParam))
                        {
                            querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                        }
                        else
                        {
                            try
                            {
                                querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "'" + getSQLLogString(upperVl.getString((String) sqllogBindParamList.get(j))) + "'");
                            }
                            catch(Exception e)
                            {
                                querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                            }
                        }
                    }

                    // SELECT가 아닌 CUD의 경우는 입력값에 :로 들어오는값에 대한 로그를 정확하게 찍기위해서
                    querylog = querylog.replaceAll("#getSQLLogStringTempString#", ":");
                    logBuffer.append("\n" + querylog);
                }

                /*-----------------------------------------------------------------------------------------
                 *   Prestatement Bind Paramter 설정
                -----------------------------------------------------------------------------------------*/
                int paramCount = sqllogBindParamList.size();
                Object bindObj;
                for(int j = 0;j < paramCount;j++)
                {
                    try
                    {
                        bindObj = upperVl.get(bindParamList.get(j));
                        if(bindObj == null)
                        {
                            xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                        }
                        else
                        {
                            xpPstmt.setObject(j + 1, bindObj);
                        }
                    }
                    // 입력변수값이 없으면 해당변수에 대해서 NULL로 처리한다.
                    catch(Exception e)
                    {
                        xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                    }
                }

                if(paramCount == 0)
                    xpPstmt.clearParameters();

                xpPstmt.addBatch();

                // 사용한리소스 메모리해제처리
                sqllogBindParamList = null;
            }

            /*-----------------------------------------------------------------------------------------
             *   클라이언트 SQL Log 처리용 Data처리
            -----------------------------------------------------------------------------------------*/
            if(!"system/common.saveCommonLogging".equalsIgnoreCase(queryID))
            {
                // 콘솔용로그데이터 변수
                String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")"+NEWLINE;

                // 시스템콘솔로그용
                makeLog(LOG_INFO, queryHeader);
                makeLog(LOG_INFO, LOGLINE + NEWLINE);
                makeLog(LOG_INFO, logBuffer + NEWLINE);
                makeLog(LOG_INFO, LOGLINE + NEWLINE);
            }

            xpPstmt.executeBatch();

            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            /*-----------------------------------------------------------------------------------------
             *   클라이언트 SQL Log 처리용 Data처리
            -----------------------------------------------------------------------------------------*/
            if(!"system/common.saveCommonLogging".equalsIgnoreCase(queryID))
            {
                // 콘솔용로그데이터 변수
                String queryResult = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + inputDataset.getRowCount() + "Processed " + runTime + "Sec"+NEWLINE+NEWLINE;

                // 시스템콘솔로그용
                makeLog(LOG_INFO, queryResult);
            }
            // 사용한리소스 메모리해제처리
            logBuffer = null;
        }
        catch(Exception e)
        {
            // debug 가 아닐경우는 에러시 SQL 을 로그를 찍어준다. 
            if( !"debug".equalsIgnoreCase(GS_QUERY_LOGLEVEL) )
            {
                makeLog(LOG_ERROR, stripEmptyLine(querylog));
            }
            
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")"+NEWLINE;
            makeLog(LOG_ERROR, queryHeader + "\n" + querylog + "\n" + e.getMessage() + NEWLINE+NEWLINE);

            e.printStackTrace();
            throw new Exception(e);
        }
        finally
        {
            JsQueryEngine.getInstance().setTransactionFlag();            
            
            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }
        }

        return inputDataset.getRowCount();
    }

    /**
     * SQL문장을 Dataset으로 ROWSTATUS를 이용하여 입력/수정/삭제처리를 한다. (멀티건처리)
     * 
     * @param Stirng sXmlQueryPath 입력/수정/삭제처리를 위한 SQL Statement
     * @param VDataSet 처리할 대상 Dataset
     * @return int updateCount 입력/수정/삭제 처리건수
     * @author 최현수
     * @throws Exception
     * @date 2011.07.25
     */
    public int save(String queryID, VDataSet inputDataset) throws Exception
    {
        Connection xpConnection = null;
        PreparedStatement xpPstmt = null;
        PreparedStatement insPstmt = null;
        PreparedStatement updPstmt = null;
        PreparedStatement delPstmt = null;

        int i = 0;
        int updateCount = 0;
        int insertCount = 0;
        int deleteCount = 0;
        int insertlog = 0;
        int updatelog = 0;
        int deletelog = 0;

        String parsedQuery = "";
        String querylog = "";
        StringBuffer logBuffer = new StringBuffer();
        String currThreadName = getThreadID();
        HashMap<String, Connection> connectionMap = new HashMap<String, Connection>();

        try
        {
            long startTime = System.currentTimeMillis();

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 INSERT/UPDATE/DELETE SQL 을 읽어온다. 
            -----------------------------------------------------------------------------------------*/
            HashMap<String, String> sqlMap;
            if(inputDataset.getRowCount() > 0)
                sqlMap = getSaveQuery(queryID, inputDataset.getVData(0));
            else
                sqlMap = getSaveQuery(queryID, new VData());

            /*-----------------------------------------------------------------------------------------
             *    입력/수정/삭제건수가 0 이면 그냥 리턴한다.
            -----------------------------------------------------------------------------------------*/
            int datasetRowCount = inputDataset.getRowCount();
            if(datasetRowCount == 0)
            {
                return 0;
            }

            int rowType;
            String crudQuery = "";
            String connectionName = "";
            VData saveRowVl;
            boolean logflag = true;

            for(i = 0;i < datasetRowCount;i++)
            {
                saveRowVl = inputDataset.getVData(i);
                rowType = inputDataset.getRowType(i);

                if(rowType == INSERT)
                {
                    crudQuery = sqlMap.get("INSERTQUERY");
                    connectionName = sqlMap.get("INSERTQUERY_CONNECTION");

                    if(connectionMap.get("INSERT") == null)
                    {
                        connectionMap.put("INSERT", getConnection(connectionName));
                    }

                    xpConnection = connectionMap.get("INSERT");

                    if(++insertlog > 5)
                        logflag = false;
                }
                else if(rowType == UPDATE)
                {
                    crudQuery = sqlMap.get("UPDATEQUERY");
                    connectionName = sqlMap.get("UPDATEQUERY_CONNECTION");

                    if(connectionMap.get("UPDATE") == null)
                    {
                        connectionMap.put("UPDATE", getConnection(connectionName));
                    }

                    xpConnection = connectionMap.get("UPDATE");

                    if(++updatelog > 5)
                        logflag = false;
                }
                else if(rowType == DELETE)
                {
                    crudQuery = sqlMap.get("DELETEQUERY");
                    connectionName = sqlMap.get("DELETEQUERY_CONNECTION");

                    if(connectionMap.get("DELETE") == null)
                    {
                        connectionMap.put("DELETE", getConnection(connectionName));
                    }

                    xpConnection = connectionMap.get("DELETE");

                    if(++deletelog > 5)
                        logflag = false;
                }
                else
                {
                    continue;
                }

                /*-----------------------------------------------------------------------------------------
                 *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
                -----------------------------------------------------------------------------------------*/
                VData upperVl = getUpperCaseVData(saveRowVl);

                /*-----------------------------------------------------------------------------------------
                 *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
                -----------------------------------------------------------------------------------------*/
                HashMap<String, Serializable> queryInfo = getQueryInfoByQuery(crudQuery, upperVl);
                crudQuery = getOracleTraceLogFormat(queryID) + (String) queryInfo.get("QUERY");
                parsedQuery = crudQuery;
                ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");

                /*-----------------------------------------------------------------------------------------
                 *   Dynamic SQL의 실제 Runtime SQL log변환처리
                -----------------------------------------------------------------------------------------*/
                ArrayList sqllogBindParamList = new ArrayList();
                for(int j = 0;j < bindParamList.size();j++)
                {
                    sqllogBindParamList.add(bindParamList.get(j));
                }
                Collections.sort(sqllogBindParamList);

                /*-----------------------------------------------------------------------------------------
                 *   로그가 너무 많이 쌓이는것을 방지하기 위해서 5건만 로그를 남기에 한다.
                -----------------------------------------------------------------------------------------*/
                if(logflag == true)
                {
                    querylog = crudQuery;
                    String sqlLogParam = "";
                    for(int j = sqllogBindParamList.size() - 1;j > -1;j--)
                    {
                        sqlLogParam = upperVl.getString(sqllogBindParamList.get(j));
                        if(isNull(sqlLogParam))
                        {
                            querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                        }
                        else
                        {
                            try
                            {
                                querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "'" + getSQLLogString(upperVl.getString((String) sqllogBindParamList.get(j))) + "'");
                            }
                            catch(Exception e)
                            {
                                querylog = querylog.replaceAll("(?i)\\:" + sqllogBindParamList.get(j).toString(), "NULL");
                            }
                        }
                    }

                    // SELECT가 아닌 CUD의 경우는 입력값에 :로 들어오는값에 대한 로그를 정확하게 찍기위해서
                    querylog = querylog.replaceAll("#getSQLLogStringTempString#", ":");
                    logBuffer.append("\n" + querylog);
                }

                /*-----------------------------------------------------------------------------------------
                 *   Dynamic SQL 파싱처리된 SQL의 Prestatement 생성
                -----------------------------------------------------------------------------------------*/
                if(rowType == INSERT)
                {
                    if(insPstmt == null)
                    {
                        insPstmt = xpConnection.prepareStatement(getQuestionMarkSQL(parsedQuery, sqllogBindParamList));
                    }

                    xpPstmt = insPstmt;
                    ++insertCount;
                }
                else if(rowType == UPDATE)
                {
                    if(updPstmt == null)
                    {
                        updPstmt = xpConnection.prepareStatement(getQuestionMarkSQL(parsedQuery, sqllogBindParamList));
                    }
                    xpPstmt = updPstmt;
                    ++updateCount;
                }
                else if(rowType == DELETE)
                {
                    if(delPstmt == null)
                    {
                        delPstmt = xpConnection.prepareStatement(getQuestionMarkSQL(parsedQuery, sqllogBindParamList));
                    }
                    xpPstmt = delPstmt;
                    ++deleteCount;
                }

                int paramCount = sqllogBindParamList.size();

                /*-----------------------------------------------------------------------------------------
                 *   Prestatement Bind Paramter 설정
                -----------------------------------------------------------------------------------------*/
                Object bindObj;
                for(int j = 0;j < paramCount;j++)
                {
                    try
                    {
                        bindObj = upperVl.get(bindParamList.get(j));
                        if(bindObj == null)
                        {
                            xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                        }
                        else
                        {
                            xpPstmt.setObject(j + 1, bindObj);
                        }
                    }
                    // 입력변수값이 없으면 해당변수에 대해서 NULL로 처리한다.
                    catch(Exception e)
                    {
                        xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                    }
                }

                if(paramCount == 0)
                    xpPstmt.clearParameters();

                xpPstmt.addBatch();

                // 사용한리소스 메모리해제처리
                sqllogBindParamList = null;
            }

            /*-----------------------------------------------------------------------------------------
             *   클라이언트 SQL Log 처리용 Data처리
            -----------------------------------------------------------------------------------------*/
            if(isQueryLogFilter(queryID) == false)
            {
                // 콘솔용로그데이터 변수
                String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")"+NEWLINE;
                makeLog(LOG_INFO, queryHeader);
                makeLog(LOG_DEBUG, LOGLINE + NEWLINE);
                makeLog(LOG_DEBUG, logBuffer + NEWLINE);
                makeLog(LOG_DEBUG, LOGLINE + NEWLINE);

                // 클라이언트 리턴용 SQL
                logBuffer.append(queryHeader);
            }

            // batch 로 Update처리
            if(deleteCount > 0)
            {
                delPstmt.executeBatch();
            }

            if(insertCount > 0)
            {
                insPstmt.executeBatch();
            }

            if(updateCount > 0)
            {
                updPstmt.executeBatch();
            }

            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            /*-----------------------------------------------------------------------------------------
             *   클라이언트 SQL Log 처리용 Data처리
            -----------------------------------------------------------------------------------------*/
            if(isQueryLogFilter(queryID) == false)
            {
                // 콘솔용로그데이터 변수
                String queryResult = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: Insert[" + insertCount + "] Update[" + updateCount + "] Delete[" + deleteCount + "] Processed " + runTime + "Sec"+NEWLINE+NEWLINE;
                makeLog(LOG_INFO, queryResult);

                // 클라이언트 리턴용 SQL
                logBuffer.append(queryResult);
            }

            //RUNTIMEQUERYMAP.put(currThreadName, RUNTIMEQUERYMAP.get(currThreadName) + logBuffer.toString());

            // 사용한리소스 메모리해제처리
            logBuffer = null;
        }
        catch(Exception e)
        {
            // debug 가 아닐경우는 에러시 SQL 을 로그를 찍어준다. 
            if( !"debug".equalsIgnoreCase(GS_QUERY_LOGLEVEL) )
            {
                makeLog(LOG_ERROR, stripEmptyLine(querylog));
            }
            
            String queryHeader = "[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ") ERROR:"+NEWLINE;
            makeLog(LOG_ERROR, queryHeader);
            makeLog(LOG_ERROR, LOGLINE + NEWLINE);
            makeLog(LOG_ERROR, e.getMessage() + NEWLINE+NEWLINE);

            //RUNTIMEQUERYMAP.put(currThreadName, RUNTIMEQUERYMAP.get(currThreadName) + querylog + "\n" + e.getMessage());

            e.printStackTrace();
            throw new Exception(e);
        }
        finally
        {
            JsQueryEngine.getInstance().setTransactionFlag();            
            
            if(insPstmt != null)
            {
                insPstmt.close();
                insPstmt = null;
            }
            if(updPstmt != null)
            {
                updPstmt.close();
                updPstmt = null;
            }
            if(delPstmt != null)
            {
                delPstmt.close();
                delPstmt = null;
            }
        }

        return updateCount;
    }

    /**
     * SQL문장의 #동적처리할 문장을 Parsing처리하여 조건문의 참/거짓을 판단하여 그에 대응하는 SQL문장으로
     * PreCompile처리한다.
     * 
     * @param Stirng SQL문장
     * @param VData 마이플랫폼 입력파라미터
     * @return String preCompile처리된 SQL문장
     * @author 최현수
     * @date 2011.02.15
     */
    public String parseDynamicSQL(String query, VData inVl) throws Exception
    {
        try
        {
            String returnQuery = query;
            int dymamicSqlCount = 0;
            String newsql = returnQuery;
            String[] buff = split(returnQuery, "#end", false);

            for(int i = 0;i < buff.length - 1;i++)
            {
                boolean notequalsflag = false;
                String[] temp1 = split(buff[i], "#if", false);
                String orgdynamicsql = temp1[1];

                String[] temp2 = split(orgdynamicsql, "#then", false);
                String ifsentens = temp2[0];
                String thensentens = "";
                String elsesentens = "";

                String[] temp3 = split(temp2[1], "#else", false);
                thensentens = temp3[0];
                if(temp3.length == 2)
                {
                    elsesentens = temp3[1];
                }

                boolean truefalse = true;
                String paramName = "";
                String paramValue = "";
                if(ifsentens.toLowerCase().indexOf("isnotnull") != -1)
                {
                    String[] temp4 = split(ifsentens, ".isnotnull", false);
                    paramName = temp4[0].trim().toUpperCase();

                    paramValue = inVl.getString(paramName);
                    if(isNull(paramValue))
                        truefalse = false;
                    else
                        truefalse = true;
                }
                else if(ifsentens.toLowerCase().indexOf("isnull") != -1)
                {
                    String[] temp4 = split(ifsentens, ".isnull", false);
                    paramName = temp4[0].trim().toUpperCase();

                    paramValue = inVl.getString(paramName);
                    if(isNull(paramValue))
                        truefalse = true;
                    else
                        truefalse = false;
                }
                else if(ifsentens.toLowerCase().indexOf("notequals") != -1)
                {
                    notequalsflag = true;

                    String[] temp4 = split(ifsentens, ".notequals", false);
                    paramName = temp4[0].trim().toUpperCase();
                    paramValue = inVl.getString(paramName);

                    String[] temp5 = temp4[1].split("[\"]");

                    if(temp5[1].equalsIgnoreCase(paramValue))
                        truefalse = false;
                    else
                        truefalse = true;
                }
                else if(ifsentens.toLowerCase().indexOf("equals") != -1)
                {
                    if(notequalsflag == false)
                    {
                        String[] temp4 = split(ifsentens, ".equals", false);
                        paramName = temp4[0].trim().toUpperCase();
                        paramValue = inVl.getString(paramName);

                        String[] temp5 = temp4[1].split("[\"]");

                        if(temp5[1].equalsIgnoreCase(paramValue))
                            truefalse = true;
                        else
                            truefalse = false;
                    }
                }
                else if(ifsentens.indexOf(">=") != -1)
                {
                    String[] temp4 = ifsentens.split(">=");
                    paramName = temp4[0].trim().toUpperCase();
                    paramValue = inVl.getString(paramName);

                    String compareValue = "";
                    String[] temp5 = temp4[1].split(" ");
                    for(int j = 0;j < temp5.length;j++)
                    {
                        String tempValue = temp5[j];
                        if(!"".equals(tempValue))
                        {
                            compareValue = temp5[j];
                            break;
                        }
                    }

                    if(Double.parseDouble(compareValue) >= inVl.getDouble(paramName))
                        truefalse = true;
                    else
                        truefalse = false;
                }
                else if(ifsentens.indexOf("<=") != -1)
                {
                    String[] temp4 = ifsentens.split("<=");
                    paramName = temp4[0].trim().toUpperCase();
                    paramValue = inVl.getString(paramName);

                    String compareValue = "";
                    String[] temp5 = temp4[1].split(" ");
                    for(int j = 0;j < temp5.length;j++)
                    {
                        String tempValue = temp5[j];
                        if(!"".equals(tempValue))
                        {
                            compareValue = temp5[j];
                            break;
                        }
                    }

                    if(Double.parseDouble(compareValue) <= inVl.getDouble(paramName))
                        truefalse = true;
                    else
                        truefalse = false;
                }
                else if(ifsentens.indexOf(">") != -1)
                {
                    String[] temp4 = ifsentens.split(">");
                    paramName = temp4[0].trim().toUpperCase();
                    paramValue = inVl.getString(paramName);

                    String compareValue = "";
                    String[] temp5 = temp4[1].split(" ");
                    for(int j = 0;j < temp5.length;j++)
                    {
                        String tempValue = temp5[j];
                        if(!"".equals(tempValue))
                        {
                            compareValue = temp5[j];
                            break;
                        }
                    }

                    if(Double.parseDouble(compareValue) > inVl.getDouble(paramName))
                        truefalse = true;
                    else
                        truefalse = false;
                }
                else if(ifsentens.indexOf("<") != -1)
                {
                    String[] temp4 = ifsentens.split("<");
                    paramName = temp4[0].trim().toUpperCase();
                    paramValue = inVl.getString(paramName);

                    String compareValue = "";
                    String[] temp5 = temp4[1].split(" ");
                    for(int j = 0;j < temp5.length;j++)
                    {
                        String tempValue = temp5[j];
                        if(!"".equals(tempValue))
                        {
                            compareValue = temp5[j];
                            break;
                        }
                    }

                    if(Double.parseDouble(compareValue) < inVl.getDouble(paramName))
                        truefalse = true;
                    else
                        truefalse = false;
                }

                String newsentence = "";
                if(truefalse)
                    newsentence = thensentens;
                else
                    newsentence = elsesentens;

                // 처리될 SQL에 .forSQL 이 있으면 해당변수의 내용을 그대로 SQL문장으로 적용처리한다.
                if(newsentence.toUpperCase().indexOf(".FORSQL") != -1)
                {
                    String[] queryforsqlbuff = split(newsentence, ".forsql", false);
                    if(queryforsqlbuff.length != 1)
                    {
                        String[] forsqlbuff = queryforsqlbuff[0].split(":");
                        String forsqlParamName = forsqlbuff[1].trim().toUpperCase();
                        String forsqlString = inVl.getString(forsqlParamName);
                        String targetsql = newsentence.substring(newsentence.indexOf(":"), newsentence.indexOf(":") + forsqlParamName.length() + 8);

                        newsentence = replaceAll(newsentence, targetsql, forsqlString);
                        newsql = replaceAll(newsql, "#if" + orgdynamicsql + "#end", ltrim(newsentence));
                    }
                }

                // 처리될 SQL 에 .list가 있으면 해당SQL을 IN ('','') 로 처리하도록 변경한다.
                String[] queryincheckbuff = split(newsentence, ".list", false);
                if(queryincheckbuff.length != 1)
                {
                    String inquery = "";
                    String inquerysentence = "";
                    String[] inbuff2 = queryincheckbuff[0].split(":");

                    inquery += inbuff2[0];
                    String inParamName = inbuff2[1].trim().toUpperCase();
                    String inParamValue = inVl.getString(inParamName);

                    // LIST의 값이 없을경우
                    if(inParamValue.length() == 0 || "null".equalsIgnoreCase(inParamValue))
                    {
                        inquery += "(NULL)";
                        inquery += queryincheckbuff[1];
                        newsentence = inquery;
                    }
                    else
                    {
                        // ('','') 를 만들어준다.
                        String[] inParamValueList = inParamValue.split(",");
                        for(int j = 0;j < inParamValueList.length;j++)
                        {
                            String listValue = inParamValueList[j] + "";
                            if(listValue.length() == 0 || "null".equalsIgnoreCase(listValue))
                            {
                                continue;
                            }

                            if(inquerysentence.length() == 0)
                                inquerysentence = "'" + getQueryString(inParamValueList[j]) + "'";
                            else
                                inquerysentence += ",'" + getQueryString(inParamValueList[j]) + "'";
                        }
                        inquery += inquerysentence;
                        inquery += queryincheckbuff[1];
                        newsentence = inquery;
                    }
                }

                // 처리될 SQL 에 .likelist가 있으면 해당SQL을 로 처리하도록 변경한다.
                // 추가 2011.11.22 by 최현수
                String[] querylikecheckbuff = split(newsentence, ".likelist", false);
                if(querylikecheckbuff.length != 1)
                {
                    String inquery = "";
                    String inquerysentence = "";
                    String[] inbuff2 = querylikecheckbuff[0].split(":");

                    // :productspec.likelist(AND, A.PRODUCTSPECNAME, 1) 를 해석해서
                    // LIKE검색처리옵션을 뽑아온다.
                    String[] option1 = querylikecheckbuff[1].split("[(]");
                    String[] option2 = option1[1].split("[)]");
                    String[] option3 = option2[0].split(",");
                    String andType = option3[0].trim();
                    String targetColum = option3[1].trim();
                    String likeType = option3[2].trim();

                    inquery += inbuff2[0];
                    String inParamName = inbuff2[1].trim().toUpperCase();
                    String inParamValue = inVl.getString(inParamName);

                    // LIST의 값이 없을경우
                    if(inParamValue.length() == 0 || "null".equalsIgnoreCase(inParamValue))
                    {
                        inquery += "(NULL)";
                        inquery += querylikecheckbuff[1];
                        newsentence = inquery;
                    }
                    else
                    {
                        // ('','') 를 만들어준다.
                        String[] inParamValueList = inParamValue.split(",");
                        for(int j = 0;j < inParamValueList.length;j++)
                        {
                            String listValue = inParamValueList[j] + "";
                            if(listValue.length() == 0 || "null".equalsIgnoreCase(listValue))
                            {
                                continue;
                            }

                            if(inquerysentence.length() == 0)
                            {
                                // 전자검색
                                if("1".equals(likeType))
                                    inquerysentence = targetColum + " LIKE '" + getQueryString(inParamValueList[j]) + "%' ";
                                else if("2".equals(likeType))
                                    inquerysentence = targetColum + " LIKE '%" + getQueryString(inParamValueList[j]) + "%' ";
                                else
                                    inquerysentence = targetColum + " LIKE '%" + getQueryString(inParamValueList[j]) + "' ";
                            }
                            else
                            {

                                String likebuff = "";

                                // 전자검색
                                if("1".equals(likeType))
                                    likebuff = targetColum + " LIKE '" + getQueryString(inParamValueList[j]) + "%' ";
                                else if("2".equals(likeType))
                                    likebuff = targetColum + " LIKE '%" + getQueryString(inParamValueList[j]) + "%' ";
                                else
                                    likebuff = targetColum + " LIKE '%" + getQueryString(inParamValueList[j]) + "' ";

                                inquerysentence += andType + " " + likebuff;
                            }
                        }

                        inquery += inquerysentence;
                        inquery += querylikecheckbuff[1];
                        newsentence = inquery;

                        newsentence = replaceAll(newsentence, querylikecheckbuff[1], ")");
                    }
                }

                newsql = replaceAll(newsql, "#if" + orgdynamicsql + "#end", ltrim(newsentence));
                ++dymamicSqlCount;
            }

            if(dymamicSqlCount != 0)
            {
                returnQuery = newsql;
            }

            // #loopstart 1 to PARAMNAME #begin #loopend 문장처리
            String[] loopbuff = split(returnQuery, "#loopend", false);
            for(int i = 0;i < loopbuff.length - 1;i++)
            {
                String[] temp1 = split(loopbuff[i], "#loopstart", false);
                String orgdynamicsql = temp1[1];

                String[] temp2 = split(orgdynamicsql, "#begin", false);
                String loopcondition = temp2[0];
                String loopcontents = temp2[1];

                // makeLog("loopcondition "+loopcondition);
                // makeLog("loopcontents "+loopcontents);

                String[] loopCountBuff = split(loopcondition, "to", false);
                String startValue = loopCountBuff[0];
                String endValue = loopCountBuff[1];

                // 루프의갯수가 없으면 기본 0으로 처리하도록 한다.
                try
                {
                    endValue = inVl.getString(loopCountBuff[1].trim());
                    if(isNull(endValue))
                    {
                        endValue = loopCountBuff[1].trim();
                    }
                }
                catch(Exception e)
                {
                    endValue = loopCountBuff[1].trim();
                }

                // 변수 LOOP 의 대소문자에 상관없이 처리를 위해서 :loop 를 모두 소문자처리한다.
                String newLoopContents = "";
                String[] loopChangeBuff = split(loopcontents, "#loop", false);
                for(int j = 0;j < loopChangeBuff.length - 1;j++)
                {
                    newLoopContents += loopChangeBuff[j] + "#loop";
                }
                newLoopContents += loopChangeBuff[loopChangeBuff.length - 1];

                // makeLog("startValue ["+startValue+"]");
                // makeLog(LOG_DEBUG, "endValue ["+endValue+"]");

                String newLoopSQL = "";
                int loopstart = Integer.parseInt(startValue.trim());
                int loopend = Integer.parseInt(endValue.trim());
                for(int j = loopstart;j < loopend + 1;j++)
                {
                    String replaceValue = j + "";
                    newLoopSQL += replaceAll(newLoopContents, "#loop", replaceValue);
                }
                returnQuery = replaceAll(returnQuery, "#loopstart" + orgdynamicsql + "#loopend", ltrim(newLoopSQL));
            }

            return returnQuery;
        }
        catch(Exception e)
        {
            System.out.println(LOGLINE);
            System.out.println("SQL SYNTEXT  ERROR : \n" + query);
            e.printStackTrace();
            throw new Exception(e);
        }

    }

    /**
     * SQL 문장에 들어있는 BIND변수를 찾아서 BIND변수를 ArrayList로 리턴한다.
     * 
     * @param Stirng SQL문장
     * @return ArrayList Bind변수처리할 변수의 목록
     * @author 최현수
     * @date 2011.02.09
     */
    public ArrayList<String> getBindParamList(String query)
    {
        StringBuffer paramsQuery = new StringBuffer();
        ArrayList<String> bindParamList = new ArrayList<String>();

        // 오라클 주석 /* */ 및 -- 라인주석 내용삭제 및 Oracle String ' ' 에 담긴 문자를
        // 모두 날려야 제대로된 BIND변수만 뽑아온다.

        /*
         * Oracle String ' ' 로 묶인내용을 모두 날려버린다. 그속 String 속에 :로된건 변수가 아니기때문에. 아래의
         * SQL문장에서 --- :USERID (사용자아이디) SELECT TO_CHAR(SYSDATE,'YYYY:MM:DD'),
         * USERID||':'||USERNAME, YYYYMMDD||':00:00' FROM TABLEA WHERE USERID =
         * :USERID || ':USERNAME' ------- :INPUTDATE 는 생년월일 --------- AND
         * BIRTHDAY = TO_DATE(:INPUTDATE,'YYYY-MM-DD HH24:MI:SS') => 변경되면..
         * SELECT TO_CHAR(SYSDATE,), USERID||||USERNAME, YYYYMMDD|| FROM TABLEA
         * WHERE USERID = :USERID || AND BIRTHDAY = TO_DATE(:INPUTDATE,) => 결국
         * 변수명 :USERID 하나의 변수만 뽑아온다.
         */
        query = query.replaceAll("--.*\n", "\n");
        query = query.replaceAll("(/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/)|(//.*)", "");
        query = query.replaceAll("''", "");
        String[] tempQuery = query.split("'");
        for(int i = 0;i < tempQuery.length;i++)
        {
            paramsQuery.append(tempQuery[i]);
            ++i;
        }

        String[] buff = paramsQuery.toString().split("[:]");
        for(int i = 1;i < buff.length;i++)
        {
            String[] bindParam = buff[i].split("[ ,)|+-/*%\r\n\t';]");
            String paramsName = bindParam[0].trim() + "";

            // System.out.println("BindParamList["+i+"]["+paramsName+"]");
            if(paramsName.length() != 0)
            {
                // 변수명이 := 로 시작되어서는 안된다.
                if(!"=".equals(paramsName.indexOf(0)))
                {
                    bindParamList.add(bindParam[0].toUpperCase());
                }
            }
        }

        // 사용한리소스 메모리해제처리
        paramsQuery = null;

        return bindParamList;
    }

    /**
     * Split 처리를 대소문자구분Flag로 split하게 처리
     * 
     * @param Stirng source
     * @param String seperator
     * @param Boolean matchCase
     * @return String Split처리결과
     * @author http://blog.naver.com/json2811?Redirect=Log&logNo=90094741526
     * @date 2011.02.15
     */
    public static String[] split(String source, String checkSeperator, boolean matchCase)
    {
        String seperator = checkSeperator;
        int current_index = 0;
        int delimiter_index = 0;
        String element = null;

        String source_lookup_reference = null;
        ArrayList<String> substrings = new ArrayList<String>();

        if(null == source)
        {
            return new String[0];
        }

        if(null == seperator)
        {
            return new String[0];
        }

        // 대소문자를 구별에 관한 판단
        if(!matchCase)
        {
            source_lookup_reference = source.toLowerCase();
            seperator = seperator.toLowerCase();
        }
        else
        {
            source_lookup_reference = source;
        }

        // 문자열 길이보다 작은한 반복해서 split를 시도한다.
        while(current_index <= source_lookup_reference.length())
        {
            // 식별자(seperator)가 존재하는지 검사.
            delimiter_index = source_lookup_reference.indexOf(seperator, current_index);

            // 존재하지 않는 경우
            if(-1 == delimiter_index)
            {
                element = new String(source.substring(current_index, source.length()));
                substrings.add(element);
                current_index = source.length() + 1;
            }
            else
            // 존재하는 경우
            {
                element = new String(source.substring(current_index, delimiter_index));
                substrings.add(element);
                current_index = delimiter_index + seperator.length();
            }
        }

        String[] rtnValue = new String[substrings.size()];
        for(int i = 0;i < substrings.size();i++)
        {
            rtnValue[i] = substrings.get(i);
        }

        // 사용한리소스 메모리해제처리
        substrings = null;

        return rtnValue;
    }

    /**
     * In 조건절 처리시 데이터가 완벽한 String 으로 처리되기 때문에 SQL Injection 처리를 위해서 ' 를 '' 으로
     * 변경처리를 하여야 하고 이를 위한 String 에서의 ' 를 '' 으로 변경처리한다.
     * 
     * @param Stirng source
     * @return String SQL Injection 처리결과 String
     * @author 최현수
     * @date 2011.02.15
     */
    public String getQueryString(String strValue)
    {
        return strValue.replaceAll("'", "''");
    }

    /**
     * 값이 null 인지 아닌지를 판단한다.
     * 
     * @param String
     * @return boolean
     * @author 최현수
     * @date 2011.07.25
     */
    public boolean isNull(String str)
    {
        if(str == null)
        {
            return true;
        }

        if(str.length() == 0 || "null".equalsIgnoreCase(str) || "undefined".equalsIgnoreCase(str))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * 해당함수는 SMD DB Connection 을 연결하고 해당 DB연결 Connection 을 HashMap connList 에
     * 넣는다. 기존에 연결된 Connection이 있으면 해당 연결 Connection 을 리턴하고 그렇치 않으면 신규로 생성하고
     * connList 에 그 내용을 넣는다. 개별 java 프로그램에서 어떤연결을 사용할지 모르고 이를 connection 을 연결처리를
     * 하고 프로그램이 종료될때 공통에서 일괄적으로 open된 connection 을 Close처리한다.
     * 
     * @param String
     * @return Connection
     * @author 최현수
     * @throws Exception
     * @date 2011.07.25
     */
    private Connection getConnection(String connectionName) throws Exception
    {
        JsQueryEngine queryMgr = JsQueryEngine.getInstance();
        return queryMgr.getConnection(connectionName);
    }

    /**
     * PROGRAMQUERY 에 등록된 QUERY 를 조회하고 해당 QUERY에 들어가는 VData 를 이용해서 동적Query의 최종본의
     * SQL 을 얻어오고 해당 SQL 에 사용되는 BIND변수의 목록을 찾아서 리턴을 한다. 이때 찾아오는 SQL의 기준은 QUERYID
     * 와 해당 QUERYID의 최신버전을 기준으로 한다.
     * 
     * @param String queryID 실행할 QUERY의 ID
     * @param String queryType QUERY의 TYPE(조회/입력/수정/삭제) 조건이 없으면 해당 QUERYID의 최신버전
     * @param VData inVl Query실행에 들어갈 데이터목록
     * @return HashMap SQL파싱및 바인딩변수 및 해당 QUERY의 실행 CONNECTION명을 HashMap으로 리턴한다.
     * @author 최현수
     * @throws Exception
     * @date 2011.07.25
     */
    public HashMap getQueryInfoByQueryID(String queryID, VData inVl) throws Exception
    {
        HashMap rtnVal = new HashMap();

        // XMLQUERYMGR에서 XMLQUERY를 얻어온다.
        JsQueryEngine queryMgr = JsQueryEngine.getInstance();

        HashMap queryinfo = queryMgr.getQuery(queryID, inVl);

        String query = (String) queryinfo.get("sql");
        String conenctionName = (String) queryinfo.get("connection");

        // ORACLE := 바인드는 변수로 처리되지 않도록 한다.
        query = query.replaceAll(":=", "#ORACLE_BINDVALUE_MAPPING#");

        // SQL문자을 파상처리한다.
        query = query.replaceAll("(?i)#if", "#if");
        query = query.replaceAll("(?i)#end", "#end");
        query = parseDynamicSQL(query, inVl);

        // SQL BIND 파라미터 리턴
        ArrayList<String> bindParamList = getBindParamList(query);

        // Oracle 바인드변수 원복처리
        query = query.replaceAll("#ORACLE_BINDVALUE_MAPPING#", ":=");

        rtnVal.put("PARAMETERLIST", bindParamList);
        rtnVal.put("CONNECTION", conenctionName);
        rtnVal.put("PARAMETERS", queryinfo.get("parameters"));

        // SQL
        rtnVal.put("QUERY", query);

        return rtnVal;
    }

    /**
     * 입력 QUERY와 파라미터를 이용해서 동적Query의 최종본의 SQL 을 얻어오고 해당 SQL 에 사용되는 BIND변수의 목록을
     * 찾아서 리턴을 한다.
     * 
     * @param String query 실행할 SQL문장
     * @param VData inVl Query실행에 들어갈 데이터목록
     * @return HashMap SQL파싱및 바인딩변수를 HashMap으로 리턴한다.
     * @author 최현수
     * @throws Exception
     * @date 2011.07.25
     */
    public HashMap getQueryInfoByQuery(String query, VData inVl) throws Exception
    {
        String rtnquery = query;
        HashMap rtnVal = new HashMap();

        // SQL문자을 파상처리한다.
        rtnquery = rtnquery.replaceAll(":=", "#ORACLE_BINDVALUE_MAPPING#");

        rtnquery = rtnquery.replaceAll("(?i)#if", "#if");
        rtnquery = rtnquery.replaceAll("(?i)#end", "#end");
        rtnquery = parseDynamicSQL(rtnquery, inVl);

        // SQL BIND 파라미터 리턴
        ArrayList<String> bindParamList = getBindParamList(rtnquery);
        rtnVal.put("PARAMETERLIST", bindParamList);

        // SQL
        rtnVal.put("QUERY", rtnquery.replaceAll("#ORACLE_BINDVALUE_MAPPING#", ":="));

        return rtnVal;
    }

    /**
     * PreparedSQL 문장을 처리할때 BEGIN END 로 SQL문장을 처리할때 BEGIN
     * 
     * SELECT * FROM TAB WHERE TNAME = :TNAME AND TNAME LILE :TNAME||'%' END;
     * 
     * 이런 문장일때 이럴때는 변수를 1개 :TNAME 하나로 인식하는 현상이 발생되고 이런경우 때문에
     * INSERT/UPDATE/DELETE 의 경우에는 이를 ? 로 바인드변수로 취해서 처리한다. (저장처리로직에서만)
     * 
     * @param String query 실행할 SQL문장
     * @param VData inVl Query실행에 들어갈 데이터목록
     * @return HashMap SQL파싱및 바인딩변수를 HashMap으로 리턴한다.
     * @author 최현수
     * @throws Exception
     * @date 2011.07.25
     */
    public String getQuestionMarkSQL(String query, ArrayList params)
    {
        String rtnQuery = query;
        String paramName = "";

        Collections.sort(params);
        for(int j = params.size() - 1;j > -1;j--)
        {
            paramName = (String) params.get(j);
            rtnQuery = rtnQuery.replaceAll("(?i)\\:" + paramName, "?");
        }

        return rtnQuery;
    }

    /**
     * Dataset으로 데이터를 저장할때 insert/update/delete 의 SQL 문장을 읽어온다.
     * 
     * @param String
     * @return boolean
     * @author 최현수
     * @throws Exception
     * @date 2011.07.25
     */
    public HashMap<String, String> getSaveQuery(String queryID, VData inputdata) throws Exception
    {
        HashMap<String, String> rtnVal = new HashMap<String, String>();
        HashMap queryinfo;

        // XMLQUERYMGR에서 XMLQUERY를 얻어온다.
        JsQueryEngine queryMgr = JsQueryEngine.getInstance();

        // 입력
        try
        {
            queryinfo = queryMgr.getQuery(queryID + ".insert", inputdata);
            rtnVal.put("INSERTQUERY", (String) queryinfo.get("sql"));
            rtnVal.put("INSERTQUERY_CONNECTION", (String) queryinfo.get("connection"));
        }
        catch(Exception e)
        {
            rtnVal.put("INSERTQUERY", "");
            rtnVal.put("INSERTQUERY_CONNECTION", "");
        }

        // 수정
        try
        {
            queryinfo = queryMgr.getQuery(queryID + ".update", inputdata);
            rtnVal.put("UPDATEQUERY", (String) queryinfo.get("sql"));
            rtnVal.put("UPDATEQUERY_CONNECTION", (String) queryinfo.get("connection"));
        }
        catch(Exception e)
        {
            rtnVal.put("UPDATEQUERY", "");
            rtnVal.put("UPDATEQUERY_CONNECTION", "");
        }

        // 삭제
        try
        {
            queryinfo = queryMgr.getQuery(queryID + ".delete", inputdata);
            rtnVal.put("DELETEQUERY", (String) queryinfo.get("sql"));
            rtnVal.put("DELETEQUERY_CONNECTION", (String) queryinfo.get("connection"));
        }
        catch(Exception e)
        {
            rtnVal.put("DELETEQUERY", "");
            rtnVal.put("DELETEQUERY_CONNECTION", "");
        }

        return rtnVal;
    }

    /**
     * SQL로그를 찍으면서 String 의 값에 ":" 이 들어간것은 해당데이터를변수로보고 엉뚱한 변수값이 찍히는 경우가 발생을 해서
     * 이를 막고 원래 스트링값 그대로 찍히도록 유도하기 위해서 ' :USERID ' 와 같은것을 ' luthiers.choi ' 와 같이
     * 찍히지 않고 있는 그대로로 찍기 위해서 :를 "#getSQLLogStringTempString#" 잠시 바꿔서 다시 이를 : 로
     * 바꾸도록 유도처리함
     * 
     * @param String
     * @return boolean
     * @author 최현수
     * @date 2011.08.01
     */
    public String getSQLLogString(String bindValues)
    {
        String rtnVal = bindValues;
        rtnVal = rtnVal.replaceAll("'", "''");
        return rtnVal.replaceAll(":", "#getSQLLogStringTempString#");
    }

    /**
     * VData 가 대소문자를 구별하기 때문에 개발시 대소문자구별로 인한 데이터를 제대로 못가지고 오는 현상을 막기위해서 VData의
     * key값을 모두 대문자로 치환해서 그값을 리턴한다.
     * 
     * @param VData inVl
     * @return VData
     * @author 최현수
     * @date 2011.08.18
     */
    public VData getUpperCaseVData(VData inVl)
    {
        VData xpUpperInVl = new VData();

        for(Object key:inVl.keySet())
        {
            String strKey = (String) key;
            if(strKey.equals(strKey.toUpperCase()))
            {
                xpUpperInVl.add(strKey.toUpperCase(), inVl.get(key));
            }
            else
            {
                xpUpperInVl.add(strKey, inVl.get(key));
                xpUpperInVl.add(strKey.toUpperCase(), inVl.get(key));
            }
        }

        return xpUpperInVl;
    }

    /**
     * log4j로 로그를 남기지만 실제 너무나 많은 쓰레기성로그로 정작필요한 로그를 통해서 디버깅하기 힘듬. 그래서 log4j를
     * ERROR로 설정하고.. GS_XPSERVICE_LOG 를 통해서 필요한 SQL 로그와 처리결과 만 console 로 출력하는것이
     * 유용함
     * 
     * @param Object logvalue
     * @return N/A
     * @author 최현수
     * @date 2011.08.05
     */
    public void makeLog(int logLevel, Object logvalue)
    {
        String sSqlLogMode = GS_QUERY_LOG;
        String sSqlLogLevel = GS_QUERY_LOGLEVEL;
        int systemLogLevel = 9;

        if("debug".equalsIgnoreCase(sSqlLogLevel))
        {
            systemLogLevel = 0;
        }
        else if("info".equalsIgnoreCase(sSqlLogLevel))
        {
            systemLogLevel = 1;
        }
        else if("warn".equalsIgnoreCase(sSqlLogLevel))
        {
            systemLogLevel = 2;
        }
        else if("error".equalsIgnoreCase(sSqlLogLevel))
        {
            systemLogLevel = 3;
        }

        // 웹로직콘솔을 이용
        if("console".equalsIgnoreCase(sSqlLogMode))
        {
            // 시스템에서 지정한 로그레벨이상일때만 로그를찍는다.
            if(systemLogLevel <= logLevel)
            {
                System.out.print(logvalue);
            }
        }
        // LOG4J를 이용
        else if("log4j".equalsIgnoreCase(sSqlLogMode))
        {
            // 시스템에서 지정한 로그레벨이상일때만 로그를찍는다.
            if(systemLogLevel <= logLevel)
            {
                if(logLevel == LOG_DEBUG)
                    logger.debug(logvalue);
                else if(logLevel == LOG_INFO)
                    logger.info(logvalue);
                else if(logLevel == LOG_WARNNING)
                    logger.warn(logvalue);
                else if(logLevel == LOG_ERROR)
                    logger.error(logvalue);
            }
        }
    }

    /**
     * Thread의 ID 를 문자로 리턴한다.
     * 
     * @return String threadID
     * @author 최현수
     * @throws Exception
     * @date 2011.09.30
     */
    public String getThreadID()
    {
        return Thread.currentThread().getId() + "";
    }

    /**
     * SQL문장을 실행하고 그결과를 CSV 포맷으로 결과를 httpd servlet download 방식의 outputstream으로
     * 출력처리한다.
     * 
     * @param String sXmlQueryPath XML QUERY의 경로
     * @param VData inVl 화면에서 입력받은 입력 파라미터
     * @return Dataset XMLQUERY 의 실행결과 Dataset
     * @author 최현수
     * @date 2011.10.14
     */
    public void executeQueryForCVS(ServletOutputStream souts, String queryID, VData inVl) throws Exception
    {
        Connection xpConnection = null;
        PreparedStatement xpPstmt = null;
        ResultSet xpResultset = null;
        VDataSet outDataset = null;

        String parsedQuery = "";
        String query;
        String querylog;
        String currThreadName = getThreadID();

        try
        {
            long startTime = System.currentTimeMillis();

            /*-----------------------------------------------------------------------------------------
             *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
            -----------------------------------------------------------------------------------------*/
            VData upperVl = getUpperCaseVData(inVl);

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
            -----------------------------------------------------------------------------------------*/
            HashMap<String, Serializable> queryInfo = getQueryInfoByQueryID(queryID, upperVl);
            query = (String) queryInfo.get("QUERY");
            parsedQuery = query;
            ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");
            xpConnection = getConnection((String) queryInfo.get("CONNECTION"));

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL의 실제 Runtime SQL log변환처리
            -----------------------------------------------------------------------------------------*/
            ArrayList logParamList = new ArrayList();
            for(int j = 0;j < bindParamList.size();j++)
            {
                logParamList.add(bindParamList.get(j));
            }

            Collections.sort(logParamList);
            querylog = query;
            String paramValue = null;
            for(int j = logParamList.size() - 1;j > -1;j--)
            {
                paramValue = upperVl.getString((String) logParamList.get(j));
                if(isNull(paramValue))
                {
                    querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "NULL");
                }
                else
                {
                    try
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'" + paramValue + "'");
                    }
                    catch(Exception e)
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'java.lang.IllegalArgumentException'");
                    }
                }
            }

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL 파싱처리된 SQL의 Prestatement 생성
            -----------------------------------------------------------------------------------------*/
            xpPstmt = xpConnection.prepareStatement(query);
            xpPstmt.clearParameters();
            int paramCount = logParamList.size();

            /*-----------------------------------------------------------------------------------------
             *   Prestatement Bind Paramter 설정
            -----------------------------------------------------------------------------------------*/
            String sqlBindPram = null;
            for(int j = 0;j < paramCount;j++)
            {
                try
                {
                    sqlBindPram = upperVl.getString((String) bindParamList.get(j));
                    if(isNull(sqlBindPram))
                    {
                        xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                    }
                    else
                    {
                        xpPstmt.setObject(j + 1, sqlBindPram);
                    }
                }
                // 입력변수값이 없으면 해당변수에 대해서 NULL로 처리한다.
                catch(Exception e)
                {
                    xpPstmt.setNull(j + 1, java.sql.Types.VARCHAR);
                }
            }

            // 콘솔용로그데이터 변수
            String queryHeader = "\r\n[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")";

            // 시스템콘솔로그용
            makeLog(LOG_INFO, queryHeader);
            makeLog(LOG_DEBUG, "\r\n" + LOGLINE + "\r\n" + querylog + "\r\n" + LOGLINE);

            /*-----------------------------------------------------------------------------------------
             *   Prestatement의 실행 및 결과 Recordset 획득
            -----------------------------------------------------------------------------------------*/
            xpPstmt.execute();

            /*-----------------------------------------------------------------------------------------
             *   ORACLE SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long OracleTime = System.currentTimeMillis();
            double OracleRumTime = (double) (OracleTime - startTime) / (double) 1000;

            xpResultset = xpPstmt.getResultSet();
            ResultSetMetaData rsmd = xpResultset.getMetaData();
            xpResultset.setFetchSize(1000);
            int col_cnt = rsmd.getColumnCount();

            /*-----------------------------------------------------------------------------------------
             *   RecordSet을 이용한 XP 출력 Dataset생성처리
            -----------------------------------------------------------------------------------------*/
            int nColoum;
            int rowCount = 0;
            int longtypeChar;
            StringBuffer blobBuffer;
            Reader blobReader;
            String blobString = "";
            String datasetColumName;
            while(xpResultset.next())
            {
                for(nColoum = 1;nColoum <= col_cnt;nColoum++)
                {
                    if(nColoum != 1)
                    {
                        souts.write(",".getBytes());
                    }

                    datasetColumName = rsmd.getColumnName(nColoum).toUpperCase();
                    if(rsmd.getColumnTypeName(nColoum).equalsIgnoreCase("LONG"))
                    {
                        blobString = "";
                        blobBuffer = new StringBuffer();
                        blobReader = xpResultset.getCharacterStream(datasetColumName);

                        longtypeChar = 0;
                        while((longtypeChar = blobReader.read()) != -1)
                        {
                            blobBuffer.append((char) longtypeChar);
                        }
                        blobString = blobBuffer.toString();
                        if(blobReader != null)
                        {
                            blobReader.close();
                        }

                        souts.write(blobString.getBytes());
                    }
                    else
                    {
                        try
                        {
                            souts.write(xpResultset.getString(nColoum).getBytes());
                        }
                        catch(Exception e)
                        {
                            souts.write("".getBytes());
                        }
                    }
                }
                souts.write("\r\n".getBytes());
                ++rowCount;
            }

            /*-----------------------------------------------------------------------------------------
             *   SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long dataProcessTime = System.currentTimeMillis();
            double dataProcessRunTime = (double) (dataProcessTime - OracleTime) / (double) 1000;
            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            String queryResult = "\r\n[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + rowCount + " Records (Oracle:" + OracleRumTime + "Sec Fetch:" + dataProcessRunTime + "Sec Total:" + runTime + "Sec)\r\n";
            makeLog(LOG_INFO, queryResult);


            // 사용한리소스 메모리해제처리
            logParamList = null;
        }
        catch(SQLException e)
        {
            // 데이터셋을 클리어처리한다.
            outDataset.clear();

            if(xpResultset != null)
            {
                xpResultset.close();
                xpResultset = null;
            }

            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }
            // 시스템콘솔로그용
            makeLog(LOG_ERROR, "\r\n" + LOGLINE + "\r\n" + parsedQuery + "\r\n" + LOGLINE);
            throw new SQLException(e);
        }
        finally
        {
            if(xpResultset != null)
            {
                xpResultset.close();
                xpResultset = null;
            }

            if(xpPstmt != null)
            {
                xpPstmt.close();
                xpPstmt = null;
            }
        }
    }

    /**
     * 현재 실행되고 있는 Class의 명을 얻어온다. com.smd. 의 경로를 뺀나머지를 리턴한다.
     * 
     * @return String 실행되고있는 현재 Class 의 com.smd. 의 경로를 뺀 Class명을 리턴한다.
     * @author 최현수
     * @date 2011.10.14
     */
    public String getRunTimeClassName()
    {
        String runtimeClassName = this.getClass().getName();
        runtimeClassName = runtimeClassName.replaceAll("com.smd.", "");

        return runtimeClassName;
    }

    /**
     * SmdString 의 Replace 에 대한 버그가 있어 이를 보안처리함.
     * 
     * @param String strSource
     * @param String strFrom
     * @param String strTo
     * @return replacec처리문장.
     * @author naver 검색..
     * @date 2011.11.09
     */
    public static String replaceAll(String strSource, String strFrom, String strTo)
    {
        int index = strSource.indexOf(strFrom);

        if(index < 0)
        {
            return strSource;
        }

        StringBuffer buf = new StringBuffer();
        buf.append(strSource.substring(0, index));
        buf.append(strTo);
        if(index + strFrom.length() < strSource.length())
        {
            buf.append(replaceAll(strSource.substring(index + strFrom.length(), strSource.length()), strFrom, strTo));
        }

        return buf.toString();
    }

    /**
     * 실제 Oracle에서 실행되는 SQL 문장에 어떤 QUERY가 돌고있다란것을 Oracle DBA가 모니터링해서 찾아낼수있도록 흰트를
     * 강제로 붙여서 모니터링가능하도록 처리한다.
     * 
     * @param String QueryID
     * @return String XMLQUERY FILE 및 QUERYID
     * @author 최현수
     * @date 2011.11.23
     */
    public String getOracleTraceLogFormat(String queryID)
    {
        try
        {
            String[] querybuff = queryID.split("[.]");
            return "/* " + querybuff[0] + " : " + querybuff[1] + " */";
        }
        catch(Exception e)
        {
            return "/* " + getRunTimeClassName() + " */";
        }
    }

    /**
     * SQL로그를 남길때.. 쓸때없이 줄넘기기 많으면 보기힘들어져서 이걸 좀 뉴라인을 없애도록 처리한다.
     * 
     * @param String QueryID
     * @return String XMLQUERY FILE 및 QUERYID
     * @author 최현수
     * @date 2011.12.22
     */
    public String stripEmptyLine(String orgQuery)
    {
        StringBuffer rtnQuery = new StringBuffer();

        String temp = "";
        String[] lineList = orgQuery.split("\n");
        for(int i = 0;i < lineList.length;i++)
        {
            temp = lineList[i].trim();
            if(!"".equals(temp))
            {
                if("UNION ALL".equalsIgnoreCase(temp))
                {
                    rtnQuery.append("\n" + lineList[i] + "\n\n");
                }
                else
                {
                    if(temp.length() > 5)
                    {
                        // 주석은 길고 짧은지를 첵크해서 짧은건 날리고 긴건 남긴다.
                        if("--".equals(temp.substring(0, 2)))
                        {
                            // 긴주석만 남긴다.
                            if("------".equals(temp.substring(0, 6)))
                            {
                                rtnQuery.append(lineList[i] + "\n");
                            }
                        }
                        else
                        {
                            rtnQuery.append(lineList[i] + "\n");
                        }
                    }
                    else
                    {
                        rtnQuery.append(lineList[i] + "\n");
                    }
                }
            }
        }

        return rtnQuery.toString();
    }

    public void commit() throws Exception
    {
        JsQueryEngine.getInstance().commit();
    }

    public void rollback() throws Exception
    {
        JsQueryEngine.getInstance().rollback();
    }

    public VDataSet select(String sQueryID, VData inVl) throws Exception
    {
        return executeQueryForList(sQueryID, inVl);
    }

    public int update(String sConnectionName, String sQueryStr, VData inVl) throws Exception
    {
        return executeUpdate(sConnectionName, sQueryStr, inVl);
    }

    public int update(String sQueryID, VData inVl) throws Exception
    {
        return executeUpdate(sQueryID, inVl);
    }

    public int update(String sQueryID, VDataSet inDs) throws Exception
    {
        return executeUpdate(sQueryID, inDs);
    }

    public VData callProcedure(String queryID, VData inVl) throws Exception
    {
        Connection xpConnection = null;
        CallableStatement xpcstmt = null;

        String query;
        String querylog = "";
        String currThreadName = getThreadID();

        try
        {
            long startTime = System.currentTimeMillis();

            /*-----------------------------------------------------------------------------------------
             *    SQL 에처리할 파라미터를 모두 대문자로 치환해서 사용한다.
            -----------------------------------------------------------------------------------------*/
            VData upperVl = getUpperCaseVData(inVl);

            /*-----------------------------------------------------------------------------------------
             *    PROGRAMQUERY에서 SQL 을 읽어오고 Parsing 및 변수의 목록을 가져온다. 
            -----------------------------------------------------------------------------------------*/
            HashMap queryInfo = getQueryInfoByQueryID(queryID, upperVl);

            query = getOracleTraceLogFormat(queryID) + (String) queryInfo.get("QUERY");
            ArrayList bindParamList = (ArrayList) queryInfo.get("PARAMETERLIST");
            xpConnection = getConnection((String) queryInfo.get("CONNECTION"));
            List paramlist = (List) queryInfo.get("PARAMETERS");

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL의 실제 Runtime SQL log변환처리
            -----------------------------------------------------------------------------------------*/
            ArrayList logParamList = new ArrayList();
            for(int j = 0;j < bindParamList.size();j++)
            {
                logParamList.add(bindParamList.get(j));
            }

            Collections.sort(logParamList);
            querylog = query;
            String paramValue = null;

            for(int j = logParamList.size() - 1;j > -1;j--)
            {
                paramValue = upperVl.getString((String) logParamList.get(j));
                if(isNull(paramValue))
                {
                    querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "NULL");
                }
                else
                {
                    try
                    {
                        // 특수문자가 들어간경우는 String.replaceAll 로 처리가 불가능하다.
                        if(paramValue.indexOf("$") != -1)
                        {
                            querylog = replaceAll(querylog, ":" + logParamList.get(j).toString().toLowerCase(), "'" + paramValue + "'");
                            querylog = replaceAll(querylog, ":" + logParamList.get(j).toString().toUpperCase(), "'" + paramValue + "'");
                        }
                        else
                        {
                            querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'" + paramValue + "'");
                        }
                    }
                    catch(Exception e)
                    {
                        querylog = querylog.replaceAll("(?i)\\:" + logParamList.get(j).toString(), "'java.lang.IllegalArgumentException'");
                    }
                }
            }

            /*-----------------------------------------------------------------------------------------
             *   Dynamic SQL 파싱처리된 SQL의 Prestatement 생성
            -----------------------------------------------------------------------------------------*/
            List outParamtersList = new ArrayList();
            xpcstmt = xpConnection.prepareCall(query);

            for(int i = 0;i < paramlist.size();i++)
            {
                Element param = (Element) paramlist.get(i);
                List attrlist = param.getAttributes();
                for(int j = 0;j < attrlist.size();j++)
                {
                    Attribute attr = (Attribute) attrlist.get(j);
                    if("type".equalsIgnoreCase(attr.getName()))
                    {
                        String paramname = param.getName().toUpperCase();
                        String paramtype = param.getValue();
                        String inouttype = attr.getValue();

                        if("in".equalsIgnoreCase(inouttype))
                        {
                            if("STRING".equalsIgnoreCase(paramtype))
                                xpcstmt.setString(i + 1, inVl.getString(paramname));
                            else if("INT".equalsIgnoreCase(paramtype))
                                xpcstmt.setInt(i + 1, inVl.getInt(paramname));
                            else if("DOUBLE".equalsIgnoreCase(paramtype))
                                xpcstmt.setDouble(i + 1, inVl.getDouble(paramname));
                            else if("FLOAT".equalsIgnoreCase(paramtype))
                                xpcstmt.setFloat(i + 1, inVl.getFloat(paramname));
                            else
                                xpcstmt.setObject(i + 1, inVl.getObject((paramname)));
                        }
                        else
                        {
                            if("STRING".equalsIgnoreCase(paramtype))
                                xpcstmt.registerOutParameter(i + 1, Types.VARCHAR);
                            else if("INT".equalsIgnoreCase(paramtype))
                                xpcstmt.registerOutParameter(i + 1, Types.INTEGER);
                            else if("DOUBLE".equalsIgnoreCase(paramtype))
                                xpcstmt.registerOutParameter(i + 1, Types.DOUBLE);
                            else if("FLOAT".equalsIgnoreCase(paramtype))
                                xpcstmt.registerOutParameter(i + 1, Types.FLOAT);
                            else
                                xpcstmt.registerOutParameter(i + 1, Types.OTHER);

                            HashMap paraminfo = new HashMap();
                            paraminfo.put("INDEX", i + 1);
                            paraminfo.put("NAME", paramname);
                            paraminfo.put("TYPE", paramtype);
                            outParamtersList.add(paraminfo);
                        }
                    }
                }
            }

            // 콘솔용로그데이터 변수
            String queryHeader = "\r\n[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")";

            // 시스템콘솔로그용
            makeLog(LOG_INFO, queryHeader + " Run...");
            makeLog(LOG_DEBUG, "\r\n" + LOGLINE + "\r\n" + stripEmptyLine(querylog) + "\r\n" + LOGLINE);

            /*-----------------------------------------------------------------------------------------
             *   Prestatement의 실행 및 결과 Recordset 획득
            -----------------------------------------------------------------------------------------*/
            xpcstmt.execute();

            long OracleTime = System.currentTimeMillis();
            double OracleRumTime = (double) (OracleTime - startTime) / (double) 1000;

            // 처리결과를 담는다.
            for(int i = 0;i < outParamtersList.size();i++)
            {
                HashMap outinfo = (HashMap) outParamtersList.get(i);
                String paramname = (String) outinfo.get("NAME");
                String paramtype = (String) outinfo.get("TYPE");
                int index = (Integer) outinfo.get("INDEX");

                if("STRING".equalsIgnoreCase(paramtype))
                    inVl.add(paramname, xpcstmt.getString(index));
                else if("INT".equalsIgnoreCase(paramtype))
                    inVl.add(paramname, xpcstmt.getInt(index));
                else if("DOUBLE".equalsIgnoreCase(paramtype))
                    inVl.add(paramname, xpcstmt.getDouble(index));
                else if("FLOAT".equalsIgnoreCase(paramtype))
                    inVl.add(paramname, xpcstmt.getFloat(index));
                else
                    inVl.add(paramname, xpcstmt.getObject(index));
            }

            /*-----------------------------------------------------------------------------------------
             *   ORACLE SQL Runtime Log정보 
            -----------------------------------------------------------------------------------------*/
            long dataProcessTime = System.currentTimeMillis();
            double dataProcessRunTime = (double) (dataProcessTime - OracleTime) / (double) 1000;
            long endTime = System.currentTimeMillis();
            double runTime = (double) (endTime - startTime) / (double) 1000;

            String queryResult = "\r\n[" + (new Date()).toLocaleString() + "] " + currThreadName + " :: " + getRunTimeClassName() + "(" + queryID + ")" + " End...\n Procedure (Oracle:" + OracleRumTime + "Sec Fetch:" + dataProcessRunTime + "Sec Total:" + runTime + "Sec)\r\n";
            makeLog(LOG_INFO, queryResult);

            return inVl;
        }
        catch(SQLException e)
        {
            if(xpcstmt != null)
            {
                xpcstmt.close();
                xpcstmt = null;
            }

            System.out.println(queryID + " ERROR: \n" + e.getMessage());
            throw new SQLException(e);
        }
        finally
        {
            if(xpcstmt != null)
            {
                xpcstmt.close();
                xpcstmt = null;
            }
        }
    }

    public String ltrim(String str)
    {
        if(str == null)
            return "";

        String rtnval;
        for(int i = 0;i < str.length();i++)
        {
            if(str.charAt(i) != '\t' && str.charAt(i) != ' ')
            {
                rtnval = str.substring(i);

                if(rtnval == null)
                    return "";
                else
                    return rtnval;
            }
        }

        return "";
    }

    public boolean isQueryLogFilter(String queryid)
    {
        boolean rtnval = false;

        if(JsQueryUtil.isNull(GS_QUERY_LOGFILTER))
        {
            return false;
        }

        try
        {
            String[] filterlist = GS_QUERY_LOGFILTER.split(" ");
            for(int i = 0;i < filterlist.length;i++)
            {
                if( queryid.indexOf(filterlist[i]) != -1 )
                {
                    return true;
                }
            }
        }
        catch(Exception e)
        {
            return false;
        }

        return rtnval;
    }
}
