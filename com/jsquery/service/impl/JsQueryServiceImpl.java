package com.jsquery.service.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.jsquery.dao.JsQueryDao;
import com.jsquery.jsqueryengine.JsQueryUtil;
import com.jsquery.jsqueryengine.VColumInfo;
import com.jsquery.jsqueryengine.VData;
import com.jsquery.jsqueryengine.VDataSet;
import com.jsquery.jsqueryengine.VDataSetList;
import com.jsquery.service.JsQueryService;

/**
 * Service 구현에 필요한 공통기능을 제공한다.<BR>
 * 기본적으로 제공하는 함수를 통하여 필요한 업무별 개별프로세스를 구현한다.<BR>
 * 클라이언트에서 지정한 Method 명이 존재하지 않으면 기본 service를 수행하며 <BR>
 * 메소드는 request, response, inVd, inVdl, outVd, outVdl 의 형태로 정의되어져야한다.
 * 
 * @author 최현수
 * @since 2012.08.08
 */
@Service("JsQueryService")
public class JsQueryServiceImpl implements JsQueryService
{
    @Inject
    @Named("JsQueryDao")
    private JsQueryDao jsquerydao;    
    private static final Logger logger = Logger.getLogger(JsQueryServiceImpl.class);

    // 데이터셋의 INSERT / UPDATE / DELETE 상태
    protected int INSERT = VDataSet.INSERT;
    protected int UPDATE = VDataSet.UPDATE;
    protected int DELETE = VDataSet.DELETE;
    protected int LOG_DEBUG = 0;
    protected int LOG_INFO = 1;
    protected int LOG_WARNNING = 2;
    protected int LOG_ERROR = 3;

    public JsQueryServiceImpl()
    {
    }

    /**
     * 서비스의 기본적인 invoke Method를 처리한다. 모든서비스를 XPServiceImpl 를 상속받아 해당 implements 에 정의한 METHOD를 찾아서 해당 METHOD를 실행한다.
     * 
     * @return N/A
     * @author 최현수
     * @date 2012.04.09
     **/
    public void invokeMethod(HttpServletRequest request, HttpServletResponse response, VData inVd, VDataSetList inVdl, VData outVd, VDataSetList outVdl) throws Exception
    {
        String methodName = inVd.getString("METHODNAME");
        String serviceName = inVd.getString("SERVICENAME");

        if(JsQueryUtil.isNull(methodName))
        {
            if(serviceName.indexOf(".") != -1)
            {
                String[] buff = serviceName.split("[.]");
                methodName = buff[1];
            }
            else
            {
                methodName = "service";
            }
        }
        
        Object[] params = new Object[] { request, response, inVd, inVdl, outVd, outVdl };
        Class[] typeParam = { HttpServletRequest.class, HttpServletResponse.class, VData.class, VDataSetList.class, VData.class, VDataSetList.class };
        Method method = this.getClass().getMethod(methodName, typeParam);
        method.invoke(this, params);
    }

    /**
     * 클라이언트로 전송할 데이터셋목록에 데이터셋을 추가한다.
     * 
     * @param VDataSetList outVdl 클라이언트 전송데이터셋목록
     * @param String datasetName 클라이언트에서 사용될 데이터셋의 명
     * @param VDataSet outDs 클라이언트에 전송될 데이터셋
     * @return N/A
     * @author 최현수
     * @date 2012.04.09
     **/
    public void addDataSet(VDataSetList outVdl, String datasetName, VDataSet outDs) throws Exception
    {
        outDs.setName(datasetName);
        outVdl.add(outDs);
    }

    /**
     * 검색조건으로 조회를 한결과에서 첫번째 레코드를 VData 로 바로 받아온다.
     * 
     * @param VDataSetList outVdl 클라이언트 전송데이터셋목록
     * @param String datasetName 클라이언트에서 사용될 데이터셋의 명
     * @param VDataSet outDs 클라이언트에 전송될 데이터셋
     * @return VData
     * @author 최현수
     * @date 2012.04.09
     **/
    public VData selectVariable(String queryId, VData inVd) throws Exception
    {
        VData outVd = new VData();

        VDataSet ds = jsquerydao.executeQueryForList(queryId, inVd);
        if(ds == null) { return outVd; }
        
        return ds.getVData(0);
    }

    public VDataSet executeQueryForList(String sQueryID, VData inVd) throws Exception
    {
        return jsquerydao.executeQueryForList(sQueryID, inVd);
    }
    
    public VDataSet executeQueryForList(String sQueryID, VDataSet inDs) throws Exception
    {
        return jsquerydao.executeQueryForList(sQueryID, inDs.getVData(0));
    }

    public int executeUpdate(String sConnectionName, String sQueryStr, VData inVd) throws Exception
    {
        return jsquerydao.executeUpdate(sConnectionName, sQueryStr, inVd);
    }

    public int executeUpdate(String sQueryID, VData inVd) throws Exception
    {
        return jsquerydao.executeUpdate(sQueryID, inVd);
    }

    public int executeUpdate(String sQueryID, VDataSet inDs) throws Exception
    {
        return jsquerydao.executeUpdate(sQueryID, inDs);
    }

    public int save(String queryid, VDataSet inputDs) throws Exception
    {
        return jsquerydao.save(queryid, inputDs);
    }

    public VDataSet select(String queryid, VDataSet inDs) throws Exception
    {
        if( queryid.indexOf("*") != -1 )
        {
            queryid = queryid.replaceAll("[*]", "");
            return VDataToVDataSet(callProcedure(queryid, inDs.getVData(0)));
        }
        else
            return jsquerydao.executeQueryForList(queryid, inDs.getVData(0));
    }

    public VDataSet select(String queryid, VData inVd) throws Exception
    {
        if( queryid.indexOf("*") != -1 )
        {
            queryid = queryid.replaceAll("[*]", "");
            return VDataToVDataSet(callProcedure(queryid, inVd));
        }
        else
            return jsquerydao.executeQueryForList(queryid, inVd);
    }

    public VData selectRow(String queryid, VData inVd) throws Exception
    {
        VDataSet outds = jsquerydao.executeQueryForList(queryid, inVd);
        if( outds.getRowCount() == 0 )
            return null;

        return outds.getVData(0);
    }

    public VData selectRow(String queryid, VDataSet inDs) throws Exception
    {
        if( queryid.indexOf("*") != -1 )
        {
            queryid = queryid.replaceAll("[*]", "");
            return callProcedure(queryid, inDs.getVData(0));
        }
        else
        {
            VDataSet outds = jsquerydao.executeQueryForList(queryid, inDs.getVData(0));
            if( outds.getRowCount() == 0 )
                return null;
    
            return outds.getVData(0);
        }
    }

    public int update(String queryId, VData inVd) throws Exception
    {
        return jsquerydao.executeUpdate(queryId, inVd);
    }

    public int update(String queryId, VDataSet inDs) throws Exception
    {
        return jsquerydao.executeUpdate(queryId, inDs);
    }

    public void afterPropertiesSet() throws Exception
    {
    }

    public void makeLog(int logLevel, Object logvalue) throws Exception
    {
        String sSqlLogMode = JsQueryUtil.getProperty("GS_QUERY_LOG");
        String sSqlLogLevel = JsQueryUtil.getProperty("GS_QUERY_LOGLEVEL");
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



    public VData executeQueryForRowData(String sQueryID, VData inVd) throws Exception
    {
        return selectRow(sQueryID, inVd);
    }

    public boolean isNull(String value) throws Exception
    {
        if(value == null)
            return true;

        if("".equals(value))
            return true;
        else
            return false;
    }

    public void addVData(VData outVd, String key, Object value) throws Exception
    {
        outVd.add(key, value);
    }

    public void commit() throws Exception
    {
        jsquerydao.commit();
    }

    public void rollback() throws Exception
    {
        jsquerydao.rollback();
    }

    public VDataSet select(String queryid) throws Exception
    {
        if( queryid.indexOf("*") != -1 )
        {
            queryid = queryid.replaceAll("[*]", "");
            return VDataToVDataSet(callProcedure(queryid, new VData()));
        }
        else
        {
            return select(queryid, new VData());
        }
    }

    public VData selectRow(String queryid) throws Exception
    {
        if( queryid.indexOf("*") != -1 )
        {
            queryid = queryid.replaceAll("[*]", "");
            return callProcedure(queryid, new VData());
        }
        else
        {
            return selectRow(queryid, new VData());
        }
    }

    public int update(String queryId) throws Exception
    {
        return update(queryId, new VData());
    }

    public VDataSet callProcedure(String sQueryID, VDataSet inputDs) throws Exception
    {
        VData outVd = null;

        for(int i = 0; i < inputDs.getRowCount(); i++)
        {
            VData inVd = inputDs.getVData(i);
            int rowType = inputDs.getRowType(i);

            if(rowType == INSERT)
            {
                outVd = jsquerydao.callProcedure(sQueryID+".insert", inVd);
            }
            else if(rowType == UPDATE)
            {
                outVd = jsquerydao.callProcedure(sQueryID+".update", inVd);
            }
            else if(rowType == DELETE)
            {
                outVd = jsquerydao.callProcedure(sQueryID+".delete", inVd);
            }
            
            for(Object key:outVd.keySet())
            {
                String strKey = (String)key;
                if( inputDs.getColumInfo(strKey) == null )
                {
                    inputDs.addColumn(strKey, VDataSet.STRING);
                }
                inputDs.set(i, strKey, outVd.getObject(strKey));
            }
        }
        
        return inputDs;
    }

    public VData callProcedure(String sQueryID, VData inVd) throws Exception
    {
        return jsquerydao.callProcedure(sQueryID, inVd);
    }

    public VDataSet VDataToVDataSet(VData inVd) throws Exception
    {
        VDataSet outDs = new VDataSet();
        for(Object key:inVd.keySet())
        {
            String strKey = (String)key;
            if( outDs.getColumInfo(strKey) == null )
            {
                outDs.addColumn(strKey, VDataSet.STRING);
            }
        }

        outDs.newRow();
        
        for(Object key:inVd.keySet())
        {
            String strKey = (String)key;
            outDs.set(0, strKey, inVd.getObject(strKey));
        }

        return outDs;
    }

    public String readFileContents(String path)
    {
        String targetFileName  = JsQueryUtil.getProperty("GS_HTML_FILEPATH")+"/"+path;
        String filecontents    = "";

        try
        {
            File targetFile = new File(targetFileName);
            byte filebyte[] = new byte[(int)targetFile.length()];
            
            BufferedInputStream bufferinput = new BufferedInputStream(new FileInputStream(targetFile));
            bufferinput.read(filebyte);
            bufferinput.close();
            
            filecontents = new String(filebyte, "UTF-8");
        }
        catch(Exception e)
        {
            System.out.println(targetFileName + " not found error.");
        }  
        
        return filecontents;
    }
    
    /**
     * Server Side HashMap 의 데이터를 이용하여 해당 HashMap의 데이터를 Json Object 로 처리해서 
     * HTML 템플릿을 Server Side Script run 하여 그 결과 값을 String 으로 리턴한다. 
     * 
     * @param String path   HTML 템플릿 파일경로 
     * @param HashMap input 입력파라미터로 해당내용이 Json Object 로 변경된다. 
     * @return String script 처리된 HTML 
     * @author 최현수
     * @date 2012.04.09
     **/
    public String parseHtmlTemplate(String path, HashMap input) throws Exception
    {        
        String htmltemplate  = readFileContents(path);

        if( isNull(htmltemplate) ) return "";

        // 입력파라미터를 JSON Object로 형변환을 한다. 
        JSONObject jsondata = new JSONObject();
        for(Object key:input.keySet())
        {
            // 데이터셋일경우 
            if( input.get(key) instanceof VDataSet )
            {                
                VDataSet vds = (VDataSet)input.get(key);

                // 컬럼정보
                ArrayList columList = new ArrayList();
                for(int j=0;j<vds.getColumnCount();j++)
                {
                    VColumInfo columinfo = vds.getColumInfo(j);
                    columList.add(columinfo.getName());
                }

                JSONArray dataset = new JSONArray();
                for(int j=0;j<vds.getRowCount();j++)
                {
                    JSONObject rowdata = new JSONObject();
                    for(int k=0;k<columList.size();k++)
                    {
                        rowdata.put((String)columList.get(k), vds.getString(j,k));
                    }                    
                    dataset.put(rowdata);
                }                
                
                jsondata.put((String)key, dataset);
            }
            // 파라미터일경우 
            else
            {
                jsondata.put((String)key, input.get(key));
            }
        }
        
        // script 의 구문을 파싱하여 include 와 script 실행대상을 발라낸다.  
        String[] buff = htmltemplate.split("</script>");
        ArrayList includescript = new ArrayList();
        ArrayList evalscript    = new ArrayList();
        for(int i=0;i<buff.length-1;i++)
        {
            String[] scriptbuff = buff[i].split("<script");
            String scriptsrc = scriptbuff[1].trim();
            
            // src incude 처리 
            if( "src".equalsIgnoreCase(scriptsrc.substring(0,3)) )
            {
                includescript.add("<script"+scriptbuff[1]+"</script>");
            }
            else
            {            
                evalscript.add("<script"+scriptbuff[1]+"</script>");
            }
        }
        
        String globalscript = "";
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("javascript");
        
        // include script 를 empty 처리한다. 
        for(int i=0;i<includescript.size();i++)
        {
            htmltemplate = htmltemplate.replace((String)includescript.get(i), "");
            String includefilename = ((String)includescript.get(i)).split(">")[0].split("=")[1];
            
            includefilename = includefilename.replaceAll("\"", "");
            includefilename = includefilename.replaceAll("'", "");
            
            globalscript += readFileContents(includefilename);
        }
        
        // 글로벌로 처리하는거.. 
        globalscript += "\nvar JSON_DATA = "+jsondata.toString()+";";
        
        // DataSet은 바로 사용가능하도록 처리한다.  
        for(Object key:input.keySet())
        {
            // 데이터셋일경우 
            if( input.get(key) instanceof VDataSet )
            {                
                VDataSet vds = (VDataSet)input.get(key);

                // 컬럼정보
                ArrayList columList = new ArrayList();
                for(int j=0;j<vds.getColumnCount();j++)
                {
                    VColumInfo columinfo = vds.getColumInfo(j);
                    columList.add(columinfo.getName());
                }

                JSONArray dataset = new JSONArray();
                for(int j=0;j<vds.getRowCount();j++)
                {
                    JSONObject rowdata = new JSONObject();
                    for(int k=0;k<columList.size();k++)
                    {
                        rowdata.put((String)columList.get(k), vds.getString(j,k));
                    }                    
                    dataset.put(rowdata);
                }                
                globalscript += "\nvar "+key+" = "+dataset.toString()+";";
            }
        }

        // 처리대상 script 를 실행해서 결과값을 설정한다.  
        for(int i=0;i<evalscript.size();i++)
        {
            String runscript = (String)evalscript.get(i);            
            String realscript = runscript.split("</script>")[0].split("<script>")[1];
            String fullscript = globalscript+"\n \n \n/**************** JAVASCRIPT FUNCTION *****************/\n  \n  \n"+realscript;
            
            try
            {
                Object evalobj = (Object)engine.eval(fullscript);
                htmltemplate = htmltemplate.replace(runscript, evalobj+"");
            }
            catch(Exception e)
            {                
                try
                {
                    String   errormsg  = e.getMessage();
                    String[] allscript = fullscript.split("\n");
                    String[] errorlist = errormsg.split("at line number ");
                    
                    int lineno = Integer.parseInt(errorlist[1].trim());
                    for(int m=0;m<allscript.length;m++)
                    {
                        String scriptlog = (m+1)+": "+allscript[m];
                        System.out.println(scriptlog.trim());
                    }
                    
                    System.out.println("\n\n"+errorlist[0]+"at line number "+lineno+": \n"+allscript[lineno-1]);
                    throw new Exception(e);                        
                }
                catch(Exception e2)
                {
                    throw new Exception(e);
                }
            }
        }        
        
        return htmltemplate;
    }

    public VData getVData(VDataSet inDs, int nrow)
    {
        try
        {
            return inDs.getVData(nrow);
        }
        catch(Exception e)
        {
            return null;
        }
    }    
}
