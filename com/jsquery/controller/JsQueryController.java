package com.jsquery.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import sun.misc.BASE64Decoder;

import com.gauce.GauceContext;
import com.gauce.GauceDataColumn;
import com.gauce.GauceDataRow;
import com.gauce.GauceDataSet;
import com.gauce.GauceService;
import com.gauce.ServiceLoader;
import com.gauce.io.GauceRequest;
import com.gauce.io.GauceResponse;
import com.jsquery.BizException;
import com.jsquery.jsqueryengine.JsQueryEngine;
import com.jsquery.jsqueryengine.JsQueryUtil;
import com.jsquery.jsqueryengine.VColumInfo;
import com.jsquery.jsqueryengine.VData;
import com.jsquery.jsqueryengine.VDataSet;
import com.jsquery.jsqueryengine.VDataSetList;
import com.jsquery.service.JsQueryService;
import com.tobesoft.xplatform.data.ColumnHeader;
import com.tobesoft.xplatform.data.DataSet;
import com.tobesoft.xplatform.data.DataSetList;
import com.tobesoft.xplatform.data.DataTypes;
import com.tobesoft.xplatform.data.PlatformData;
import com.tobesoft.xplatform.data.VariableList;
import com.tobesoft.xplatform.tx.HttpPlatformRequest;
import com.tobesoft.xplatform.tx.HttpPlatformResponse;
import com.tobesoft.xplatform.tx.PlatformException;
import com.tobesoft.xplatform.tx.PlatformType;

/**
 * CommonController 클라이언트의 전송값을 이용해서 공통으로 서비스를 처리하고 그결과를 리턴한다. 클라이언트의
 * SERVICENAME 의 값이 없으면 공통으로 처리를 하고 SERVICENAME이 존재하면 해당 Service의 Method를 Call
 * 해서 그결과를 리턴해준다.
 * 
 * @author 최현수
 * @since 2012.04.09
 * @version 1.0
 */
@SuppressWarnings("rawtypes")
public class JsQueryController extends AbstractController
{
    @Autowired
    @Qualifier("JsQueryService")
    private JsQueryService jsqueryservice;
    
    final static String GS_METHODNAME  = JsQueryUtil.getProperty("GS_METHODNAME");
    final static String GS_SERVICENAME = JsQueryUtil.getProperty("GS_SERVICENAME");
    

    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        return null;
    }

    public HashMap registerGlobalVariable(HttpServletRequest request, HttpServletResponse response, VData inVd, VDataSetList inVdl) throws Exception
    {
        return new HashMap();
    }


    /**
     * CommonCotroller 의 개별서비스처리 및 공통서비스처리를 판단하여 처리한다.
     * 
     * @author 최현수
     * @since 2012.04.09
     * @version 1.0
     */
    public void executeCommonController(HttpServletRequest request, HttpServletResponse response, VData inVd, VDataSetList inVdl, VData outVd, VDataSetList outVdl) throws Exception
    {
        String servicename = inVd.getString("SERVICENAME");

        try
        {
            /*-----------------------------------------------------------------------------------------
             *  서비스 호출처리 
            -----------------------------------------------------------------------------------------*/
            if(!JsQueryUtil.isNull(servicename))
            {
                if(servicename.indexOf(".") != -1)
                {
                    String[] serviceinfo = servicename.split("[.]");
                    servicename = serviceinfo[0];
                    inVd.add("METHODNAME", serviceinfo[1]);
                }

                Object bean = null;
                
                try
                {
                    bean = getWebApplicationContext().getBean(servicename); 
                    Object[] params = new Object[] { request, response, inVd, inVdl, outVd, outVdl };
                    Class[] typeParam = { HttpServletRequest.class, HttpServletResponse.class, VData.class, VDataSetList.class, VData.class, VDataSetList.class };
                    Method method = bean.getClass().getMethod("invokeMethod", typeParam);
                    method.invoke(bean, params);        
                }
                catch(NoSuchBeanDefinitionException e)
                {
                    excuteServiceMapping(inVd, inVdl, outVd, outVdl);
                }
            }
            /*-----------------------------------------------------------------------------------------
             *  공통서비스 호출처리 
            -----------------------------------------------------------------------------------------*/
            else
            {
                excuteCommonService(inVd, inVdl, outVd, outVdl);
            }

            outVd.add("ErrorCode", "0");
            outVd.add("ErrorMsg", "SUCCESS");

            /*-----------------------------------------------------------------------------------------
             *  TRANSACTION COMMIT 처리  
            -----------------------------------------------------------------------------------------*/
            jsqueryservice.commit();
        }
        catch(InvocationTargetException e)
        {
            e.printStackTrace();

            // Method Call InvocationTargetException 에서는 에러를 발생해도 Exception
            // Class를 받아내지 못한다.
            // 해서 ThreadID_EXCEPTION 의 전역변수 HashMap에 담겨진 Object 를 꺼내서 사용을 한다.
            JsQueryEngine engine = JsQueryEngine.getInstance();
            Object ex = (Object) engine.getException();
            

            // -1 사용자 정의 오류
            if(ex instanceof BizException)
            {
                outVd.add("ErrorCode", ((BizException) ex).getMessageType());
                outVd.add("ErrorMsg", ((BizException) ex).getClientMessage(inVd.getString("GS_QUERY_LANG")));
            }
            // -2 SQL 오류
            else if(ex instanceof SQLException)
            {
                outVd.add("ErrorCode", -40);
                outVd.add("ErrorMsg", ((SQLException) ex).getMessage());
            }
            // -3 프로그램오류
            else if(ex instanceof Exception)
            {
                outVd.add("ErrorCode", -50);
                outVd.add("ErrorMsg", ((Exception) ex).getMessage());
            }
            else
            {
                outVd.add("ErrorCode", -60);
                outVd.add("ErrorMsg", ((Exception) ex).getMessage());
            }

            /*-----------------------------------------------------------------------------------------
             *  TRANSACTION ROLLBACK 처리  
            -----------------------------------------------------------------------------------------*/
            jsqueryservice.rollback();

            throw new Exception(e);
        }
        catch(Exception e)
        {
            e.printStackTrace();

            outVd.add("ErrorCode", -1);
            outVd.add("ErrorMsg", e.getMessage());

            /*-----------------------------------------------------------------------------------------
             *  TRANSACTION ROLLBACK 처리  
            -----------------------------------------------------------------------------------------*/
            jsqueryservice.rollback();

            throw new Exception(e);
        }
    }
    

    /**
     * Gauce / Ajax 의 경우는 Dataset=QueryID 로 직접적인 매핑을 보안상 할수가 없기때문에 이를 ServiceMapping.xml 을 이용해서 
     * Dataset=QueryID 로 처리를 해준다.
     * 
     * @author 최현수
     * @since 2012.12.13
     * @version 1.0
     * @throws Exception 
     */
    public void excuteServiceMapping(VData inVd, VDataSetList inVdl, VData outVd, VDataSetList outVdl) throws Exception
    {
        String servicename = inVd.getString(GS_SERVICENAME);
        
        JsQueryEngine engine = JsQueryEngine.getInstance();
        HashMap mapping = engine.getServiceMapping(servicename);

        String outmap = (String)mapping.get("out");
        String inmap = (String)mapping.get("in");
        Element eValidation= (Element)mapping.get("validation"); 

        // 입력값의 유무를 첵크한다. 웹상의 호출은 클라이언트에서의 데이터 조작이 발생하는것이 있기 때문에 이를 방지하기위함.
        if( eValidation != null )
        {
            List validationList = eValidation.getChildren();
            if( validationList.size() != 0 )
            {
                for(int i=0;i<validationList.size();i++)
                {
                    Element ePrams = (Element)validationList.get(i);
                    String validationType = ePrams.getText();
                    String validationKey  = ePrams.getName();
                    
                    if( "notnull".equalsIgnoreCase(validationType) )
                    {
                        String keyvalue = inVd.getString(validationKey)+"";
                        if( keyvalue.length() == 0 )
                        {
                            throw new Exception("Validation Error : "+validationKey+" is null."); 
                        }
                    }
                }
            }
        }
        
        if( inmap != null )
        {
            ArrayList inList = new ArrayList();
            String[] inmaplist = inmap.trim().split(" ");
            for(int i=0;i<inmaplist.length;i++)
            {
                String querylist = inmaplist[i].trim();
                if( querylist != null )
                {
                    if( querylist.length() > 0 )
                        inList.add(querylist.trim());
                }
            }
            
            String inquery = "";
            for(int i=0;i<inList.size();i++)
            {
                if( i == 0 )
                    inquery = (String)inList.get(i);
                else
                    inquery += " "+(String)inList.get(i);
            }

            inVd.add("TRANSACTIONLIST", inquery);
        }
        
        if( outmap != null )
        {
            ArrayList outList = new ArrayList();
            String[] outmaplist = outmap.trim().split(" ");
            for(int i=0;i<outmaplist.length;i++)
            {
                String querylist = outmaplist[i].trim();
                if( querylist != null )
                {                    
                    if( querylist.length() > 0 )
                        outList.add(querylist);
                }
            }
            
            String outquery = "";
            for(int i=0;i<outList.size();i++)
            {
                if( i == 0 )
                    outquery = (String)outList.get(i);
                else
                    outquery += " "+(String)outList.get(i);
            }
            
            inVd.add("SELECTQUERYLIST", outquery);
        }
        
        excuteCommonService(inVd, inVdl, outVd, outVdl);
    }
    

    /**
     * VData 에 필요한 데이터를 설정한다.
     * 
     * @author 최현수
     * @since 2012.04.09
     * @version 1.0
     */
    public void setGlobalVariableToInVd(HashMap<String, String> sessionInfo, VData inVd)
    {
        Iterator entries = sessionInfo.entrySet().iterator();
        while(entries.hasNext())
        {
            Map.Entry entry = (Map.Entry) entries.next();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            inVd.add(key, value);
        }
    }

    /**
     * DataSet 에 필요한 데이터를 설정한다.
     * 
     * @author 최현수
     * @since 2012.08.02
     * @version 1.0
     */
    public void setGlobalVariableToInDl(HashMap<String, String> globalDataMap, VDataSetList inVdl)
    {
        for(int j = 0;j < inVdl.size();j++)
        {
            try
            {
                VDataSet inDs = inVdl.get(j);

                // 컬럼추가
                Iterator entries = globalDataMap.entrySet().iterator();
                while(entries.hasNext())
                {
                    Map.Entry entry = (Map.Entry) entries.next();
                    String key = (String) entry.getKey();
                    inDs.addColumn(key, VDataSet.STRING);
                }

                // 데이터설정
                for(int k = 0;k < inDs.getRowCount();k++)
                {
                    entries = globalDataMap.entrySet().iterator();
                    while(entries.hasNext())
                    {
                        Map.Entry entry = (Map.Entry) entries.next();
                        String key = (String) entry.getKey();
                        String value = (String) entry.getValue();
                        inDs.set(k, key, value);
                    }
                }
            }
            catch(Exception e)
            {
                ;
            }
        }
    }

    /**
     * 공통프로세스를 처리한다. search 및 save 에 대한 클라이언트의 조회처리
     * ds_searchData=com.selectSample 저장처리 ds_saveData=com.saveSample 과 같이
     * DataSet=xmlQueryID 로 처리되는 공통처리 프로세스
     * 
     * @author 최현수
     * @since 2012.08.02
     * @version 1.0
     */
    public void excuteCommonService(VData inVd, VDataSetList inVdl, VData outVd, VDataSetList outVdl) throws Exception
    {
        String key, value, datasetName, queryID;
        String[] queryList = null;
        String[] transactionList = null;
        String[] queryInfo = null;

        /*-----------------------------------------------------------------------------------------
         *  파라미터입력값에서 TRSACTION 처리와 조회를 찾는다.  
        -----------------------------------------------------------------------------------------*/
        for(Object keyName:inVd.keySet())
        {
            key = (String) keyName;
            value = inVd.getString(key);

            // 조회처리
            if("SELECTQUERYLIST".equalsIgnoreCase(key))
            {
                queryList = value.split(" ");
            }
            // TRANSACTION 처리
            else if("TRANSACTIONLIST".equalsIgnoreCase(key))
            {
                transactionList = value.split(" ");
            }
        }

        /*-----------------------------------------------------------------------------------------
         *  먼저 TRANSACTION을 처리한다.  
        -----------------------------------------------------------------------------------------*/
        if(transactionList != null)
        {
            for(int i = 0;i < transactionList.length;i++)
            {
                queryInfo = transactionList[i].split("=");
                datasetName = queryInfo[0];
                queryID = queryInfo[1];

                // DATASET MODE :U :A 로 넘어온것들의 :U/A 등은 필요없어서 잘라낸다.
                if(queryID.indexOf(":") != -1)
                {
                    String[] queryBuff = queryID.split(":");
                    queryID = queryBuff[0];
                }

                // 프로시져를 콜해서 저장하는 경우는 입력데이터셋을 그대로 클라이언트에 리턴해준다.
                if(queryID.indexOf("*") != -1)
                {
                    queryID = queryID.replaceAll("[*]", "");
                    VDataSet outDs = jsqueryservice.callProcedure(queryID, inVdl.get(datasetName));
                    outDs.setName(datasetName);
                    outVdl.add(outDs);
                }
                else
                {
                    jsqueryservice.save(queryID, inVdl.get(datasetName));
                }
            }
        }

        /*-----------------------------------------------------------------------------------------
         *  조회쪽을 처리한다. 
        -----------------------------------------------------------------------------------------*/
        if(queryList != null)
        {
            for(int i = 0;i < queryList.length;i++)
            {
                if(queryList[i].indexOf("=") == -1)
                {
                    throw new Exception("XML QUERY is not defined.");
                }
                else
                {
                    queryInfo = queryList[i].split("=");
                    datasetName = queryInfo[0];
                    queryID = queryInfo[1].trim();

                    // 검색조건으로 Dataset으로 넘어올경우
                    if(queryID.indexOf("(") != -1)
                    {
                        String[] buff = queryID.split("[(]");
                        queryID = buff[0];
                        String[] buff2 = buff[1].split("[)]");
                        VDataSet inParamDs = inVdl.get(buff2[0]);

                        // 검색조건 Dataset 이 넘어올경우
                        if(inParamDs != null)
                        {
                            // 검색조건 "%", "_" 필터링처리
                            checkSpecialChar(inParamDs.getVData(0));
                            
                            VDataSet outDs = jsqueryservice.select(queryID, inParamDs);
                            outDs.setName(datasetName);
                            outVdl.add(outDs);
                        }
                        // 빈데이터셋일경우
                        else
                        {
                            // 검색조건 "%", "_" 필터링처리
                            checkSpecialChar(inVd);
                            
                            VDataSet outDs = jsqueryservice.select(queryID, inVd);
                            outDs.setName(datasetName);
                            outVdl.add(outDs);
                        }
                    }
                    // 검색조건을 VData 로 처리한다.
                    else
                    {
                        // 검색조건 "%", "_" 필터링처리
                        checkSpecialChar(inVd);
                        
                        VDataSet outDs = jsqueryservice.select(queryID, inVd);
                        outDs.setName(datasetName);
                        outVdl.add(outDs);
                    }
                }
            }
        }
    }
    
    /*
     * 검색조건으로 들어온 파라미터의 값이 "%", "_" 로만 구성되면 튕겨낸다. 
     */
    public boolean checkSpecialChar(VData inputdata) throws Exception
    {
        Iterator<?> iterator = inputdata.keySet().iterator();
        while(iterator.hasNext())
        {
            String key = (String) iterator.next();
            String value = inputdata.getString(key);
            
            // 널값이 아닌값에 대해서는 "%", "_" 로 구성된것을 첵크한다.  
            if( !isNull(value) )
            {
                int strlength = value.length();
                int persentcheck = 0;
                int underbarcheck = 0;
                
                for(int i=0;i<strlength;i++)
                {
                    if( value.charAt(i) == '%' )
                        ++persentcheck;
                    else if( value.charAt(i) == '_' )
                        ++underbarcheck;
                }
                
                // 허용되지 않는 특수문자일경우는 에러처리한다. 
                if( strlength == (persentcheck+underbarcheck) )
                {
                    throw new Exception("KEYWORDERROR");
                }
            }
        }
        
        return true;
    }
    
    public boolean isNull(String value)
    {
        if( value == null ) return true;
        
        value = value+"";
        
        if( "".equals(value) || value.length() == 0 || "null".equalsIgnoreCase(value) || "undefined".equalsIgnoreCase(value) )
            return true;
        else
            return false;
    }

    /**********************************************************************************************************************
     * 
     *********************************************************************************************************************/
    @SuppressWarnings("deprecation")
    public void handleXPlatformRequest(HttpServletRequest request, HttpServletResponse response) throws PlatformException
    {
        String XQUERY_XP_CONTENTTYPE = JsQueryUtil.getProperty("GS_XP_CONTENTTYPE");
        String XQUERY_XP_ENCTYPE = JsQueryUtil.getProperty("GS_XP_ENCTYPE");

        HttpPlatformRequest xpRequest = null;
        HttpPlatformResponse xpResponse = null;

        if("PlatformZlib".equalsIgnoreCase(XQUERY_XP_CONTENTTYPE))
        {
            xpRequest = new HttpPlatformRequest(request, PlatformType.CONTENT_TYPE_BINARY);
        }
        else
        {
            xpRequest = new HttpPlatformRequest(request, XQUERY_XP_CONTENTTYPE);
        }

        xpRequest.receiveData();
        PlatformData xpInData = xpRequest.getData();
        VariableList xpInVl = xpInData.getVariableList();
        DataSetList xpInDl = xpInData.getDataSetList();
        PlatformData xpOutData = new PlatformData();
        VariableList xpOutVl = new VariableList();
        DataSetList xpOutDl = new DataSetList();

        if("PlatformZlib".equalsIgnoreCase(XQUERY_XP_CONTENTTYPE))
        {
            xpResponse = new HttpPlatformResponse(response, PlatformType.CONTENT_TYPE_BINARY, XQUERY_XP_ENCTYPE);
            xpResponse.addProtocolType(PlatformType.PROTOCOL_TYPE_ZLIB);
        }
        else
        {
            xpResponse = new HttpPlatformResponse(response, XQUERY_XP_CONTENTTYPE, XQUERY_XP_ENCTYPE);
        }

        VData inVd = new VData();
        VDataSetList inVdl = new VDataSetList();
        VData outVd = new VData();
        VDataSetList outVdl = new VDataSetList();

        /*-----------------------------------------------------------------------------------------
         *  입력 데이터의 컨버젼
        -----------------------------------------------------------------------------------------*/
        List<?> keyList = xpInVl.keyList();
        for(int i = 0;i < keyList.size();i++)
        {
            inVd.add((String) keyList.get(i), xpInVl.getString((String) keyList.get(i)));
        }

        for(int i = 0;i < xpInDl.size();i++)
        {
            DataSet xpds = xpInDl.get(i);
            VDataSet vds = new VDataSet(xpds.getName());

            // 컬럼해더생성
            for(int j = 0;j < xpds.getColumnCount();j++)
            {
                ColumnHeader xpcolum = xpds.getColumn(j);
                int dataType = xpcolum.getDataType();

                if(dataType == DataTypes.STRING)
                    vds.addColumn(xpcolum.getName(), VDataSet.STRING, xpcolum.getDataSize());
                else if(dataType == DataTypes.FLOAT)
                    vds.addColumn(xpcolum.getName(), VDataSet.FLOAT);
                else if(dataType == DataTypes.BLOB)
                    vds.addColumn(xpcolum.getName(), VDataSet.BLOB);
                else if(dataType == DataTypes.DECIMAL)
                    vds.addColumn(xpcolum.getName(), VDataSet.DECIMAL);
                else if(dataType == DataTypes.BIG_DECIMAL)
                    vds.addColumn(xpcolum.getName(), VDataSet.BIG_DECIMAL);
                else if(dataType == DataTypes.DATE)
                    vds.addColumn(xpcolum.getName(), VDataSet.DATE);
                else if(dataType == DataTypes.DATE_TIME)
                    vds.addColumn(xpcolum.getName(), VDataSet.DATE_TIME);
                else if(dataType == DataTypes.BOOLEAN)
                    vds.addColumn(xpcolum.getName(), VDataSet.BOOLEAN);
                else if(dataType == DataTypes.DOUBLE)
                    vds.addColumn(xpcolum.getName(), VDataSet.DOUBLE);
                else if(dataType == DataTypes.INT)
                    vds.addColumn(xpcolum.getName(), VDataSet.INT);
                else
                    vds.addColumn(xpcolum.getName(), VDataSet.STRING);
            }
            
            int columcount = xpds.getColumnCount();
            for(int j=0;j<xpds.getRowCount();j++)
            {
                vds.newRow();
                for(int k=0;k<columcount;k++)
                {
                    vds.set(j, k, xpds.getObject(j, k));
                }
                
                int rowtype = xpds.getRowType(j);
                if( rowtype == DataSet.ROW_TYPE_INSERTED )
                    vds.setRowType(j, VDataSet.INSERT);
                else if( rowtype == DataSet.ROW_TYPE_UPDATED )
                    vds.setRowType(j, VDataSet.UPDATE);
                else 
                    vds.setRowType(j, VDataSet.NORMAL);
            }
            
            int removedCount = xpds.getRemovedRowCount();
            for(int j = 0; j < removedCount; j++)
            {
                int row = vds.newRow();
                for(int k=0;k<columcount;k++)
                {
                    vds.set(row, k, xpds.getRemovedData(j, k));
                }
                vds.setRowType(row, VDataSet.DELETE);
            }  
            
            inVdl.add(vds);
        }

        try
        {
            /* Global 변수처리할 내용을 설정한다. */
            HashMap globalVariableMap = registerGlobalVariable(request, response, inVd, inVdl);
            setGlobalVariableToInVd(globalVariableMap, inVd);
            setGlobalVariableToInDl(globalVariableMap, inVdl);

            executeCommonController(request, response, inVd, inVdl, outVd, outVdl);
        }
        catch(Exception e)
        {
            xpOutVl.add("ErrorCode", "-90");
            
            String errorMsg = e.getMessage();
            
            if( errorMsg.indexOf("ORA-00001") != -1 )
            {
                errorMsg = "SYS00001";
            }
            else if( errorMsg.indexOf("ORA-00904") != -1 )
            {
                errorMsg = "SYS00904";
            }
            else if( errorMsg.indexOf("ORA-01400") != -1 )
            {
                errorMsg = "SYS01400";
            }
            else if( errorMsg.indexOf("NONEAUTH") != -1 )
            {
                errorMsg = "NONEAUTH";
            }
            else if( errorMsg.indexOf("SESSIONCLOSED") != -1 )
            {
                errorMsg = "SESSIONCLOSED";
            }
            else if( errorMsg.indexOf("MAXRECORD") != -1 )
            {
                errorMsg = "MAXRECORD";
            }
            else if( errorMsg.indexOf("KEYWORDERROR") != -1 )
            {
                errorMsg = "KEYWORDERROR";
            }
            else
            {
                errorMsg = "SYSERROR";
            }
            
            outVd.add("ErrorCode", "-999");                
            outVd.add("ErrorMsg", errorMsg);
            
            e.printStackTrace();
        }
        
        // 출력 Variable 을 설정한다.
        for(Object keyName:outVd.keySet())
        {            
            String key = (String) keyName;
            if( key.equalsIgnoreCase("ErrorCode") )
            {
                xpOutVl.add("ErrorCode", outVd.get(key));                
            }
            else if( key.equalsIgnoreCase("ErrorMsg") ) 
            {
                xpOutVl.add("ErrorMsg", outVd.get(key));                
            }
            else
                xpOutVl.add(key, outVd.get(key));
        }        
        
        try
        {
            /*-----------------------------------------------------------------------------------------
             *  출력 데이터의 컨버젼
            -----------------------------------------------------------------------------------------*/
            for(Object key:outVd.keySet())
            {
                String strKey = (String)key;
                xpOutVl.add(strKey, outVd.get(key));
            }

            for(int i = 0;i < outVdl.size();i++)
            {
                VDataSet vds = outVdl.get(i);
                DataSet xpds = new DataSet(vds.getName());

                // 컬럼설정
                for(int j=0;j<vds.getColumnCount();j++)
                {
                    VColumInfo columinfo = vds.getColumInfo(j);
                    int datatype = columinfo.getDataType();
                    
                    if( datatype == VDataSet.STRING )
                    {
                        xpds.addColumn(columinfo.getName(), DataTypes.STRING, columinfo.getDataSize());
                    }
                    else if (datatype == VDataSet.FLOAT) 
                        xpds.addColumn(columinfo.getName(), DataTypes.FLOAT);
                    else if (datatype == VDataSet.DOUBLE) 
                        xpds.addColumn(columinfo.getName(), DataTypes.DOUBLE);
                    else if (datatype == VDataSet.INT) 
                        xpds.addColumn(columinfo.getName(), DataTypes.INT);
                    else if (datatype == VDataSet.FLOAT) 
                        xpds.addColumn(columinfo.getName(), DataTypes.FLOAT);
                    else if (datatype == VDataSet.DATE) 
                        xpds.addColumn(columinfo.getName(), DataTypes.DATE);
                    else if (datatype == VDataSet.DATE_TIME) 
                        xpds.addColumn(columinfo.getName(), DataTypes.DATE_TIME);
                    else if (datatype == VDataSet.BLOB) 
                        xpds.addColumn(columinfo.getName(), DataTypes.BLOB);
                    else if (datatype == VDataSet.BOOLEAN) 
                        xpds.addColumn(columinfo.getName(), DataTypes.BOOLEAN);
                    else if (datatype == VDataSet.DECIMAL) 
                        xpds.addColumn(columinfo.getName(), DataTypes.DECIMAL);
                    else if (datatype == VDataSet.BIG_DECIMAL) 
                        xpds.addColumn(columinfo.getName(), DataTypes.BIG_DECIMAL);
                    else
                        xpds.addColumn(columinfo.getName(), DataTypes.STRING);                    
                }
                
                for(int j=0;j<vds.getRowCount();j++)
                {
                    xpds.newRow();                
                    for(int k=0;k<vds.getColumnCount();k++)
                    {
                        xpds.set(j, k, vds.getObject(j, k));
                    }
                }
                xpOutDl.add(xpds);
                vds = null;
            }        

            xpOutData.setVariableList(xpOutVl);
            xpOutData.setDataSetList(xpOutDl);
            xpResponse.setData(xpOutData);
            xpResponse.sendData();
            xpOutData = null;
            xpOutVl = null;
            xpOutDl = null;
            return;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return;            
        }
    }
    

    /**********************************************************************************************************************
     * GAUCE 3.5 처리프로세스 
     *********************************************************************************************************************/
    public void handleGauceRequest(HttpServletRequest request, HttpServletResponse response) throws PlatformException
    {
        String XQUERY_GAUCE_DOMAIN = JsQueryUtil.getProperty("GS_GAUCE_DOMAIN");
        
        ServiceLoader serviceloader = null;
        GauceService gauceservice = null;
        
        VData inVd = new VData();
        VData outVd = new VData();
        VDataSetList inVdl = new VDataSetList();
        VDataSetList outVdl = new VDataSetList();
        
        try
        {
            serviceloader = new ServiceLoader(request, response);
            gauceservice = serviceloader.newService(XQUERY_GAUCE_DOMAIN);
            @SuppressWarnings("unused")
            GauceContext context = gauceservice.getContext();
            GauceRequest gauceRequest = gauceservice.getGauceRequest();
            GauceResponse gauceResponse = gauceservice.getGauceResponse();

            // 입력파라미터값 처리 
            Enumeration paramenum = gauceRequest.getParameterNames();
            for(Object paramdata : Collections.list(paramenum))
            {
                String paramname = (String)paramdata;
                inVd.add(paramname, gauceRequest.getParameter(paramname));
            }
            
            // 입력 데이터셋처리 
            GauceDataSet[] gauceDatasetList = gauceRequest.getGauceDataSets();
            if( gauceDatasetList != null )
            {
                for(int i=0;i<gauceDatasetList.length;i++)
                {
                    GauceDataSet gauceDs = gauceDatasetList[i];
                    VDataSet vds = new VDataSet(gauceDs.getName());
                    
                    // 컬럼정의
                    int columCount = gauceDs.getDataColCnt();
                    for(int j=0;j<columCount;j++)
                    {
                        GauceDataColumn gaucedatacolum = gauceDs.getDataColumn(j);
                        
                        String columname = gaucedatacolum.getColName();
                        int datatype = gaucedatacolum.getColType();
                        
                        if( datatype == GauceDataColumn.TB_DECIMAL )
                            vds.addColumn(columname, VDataSet.BIG_DECIMAL);
                        else if( datatype == GauceDataColumn.TB_BLOB )
                            vds.addColumn(columname, VDataSet.BLOB);
                        else if( datatype == GauceDataColumn.TB_DECIMAL )
                            vds.addColumn(columname, VDataSet.BIG_DECIMAL);
                        else if( datatype == GauceDataColumn.TB_INT )
                            vds.addColumn(columname, VDataSet.INT);
                        else if( datatype == GauceDataColumn.TB_NUMBER )
                            vds.addColumn(columname, VDataSet.DECIMAL);
                        else if( datatype == GauceDataColumn.TB_STRING )
                            vds.addColumn(columname, VDataSet.STRING);
                        else if( datatype == GauceDataColumn.TB_URL )
                            vds.addColumn(columname, VDataSet.STRING);
                        else 
                            vds.addColumn(columname, VDataSet.STRING);
                    }
                    
                    // 데이터설정 
                    GauceDataRow[] rows = gauceDs.getDataRows();
                    for(int j=0;j<rows.length;j++)
                    {
                        vds.newRow();

                        // 데이터 입력수정삭제 플래그처리 
                        if( rows[j].getJobType() == GauceDataRow.TB_JOB_INSERT )
                            vds.setRowType(j, VDataSet.INSERT);
                        else if( rows[j].getJobType() == GauceDataRow.TB_JOB_UPDATE )
                            vds.setRowType(j, VDataSet.UPDATE);
                        else if( rows[j].getJobType() == GauceDataRow.TB_JOB_DELETE )
                            vds.setRowType(j, VDataSet.DELETE);
                        
                        for(int k=0;k<columCount;k++)
                        {
                           vds.set(j, k, rows[j].getColumnValue(k));
                        }
                    }

                    inVdl.add(vds);
                }
            }
            
            try
            {
                /* Global 변수처리할 내용을 설정한다. */
                HashMap globalVariableMap = registerGlobalVariable(request, response, inVd, inVdl);
                setGlobalVariableToInVd(globalVariableMap, inVd);
                setGlobalVariableToInDl(globalVariableMap, inVdl);

                executeCommonController(request, response, inVd, inVdl, outVd, outVdl);               
                
                for(int i = 0;i < outVdl.size();i++)
                {
                    VDataSet vds = outVdl.get(i);
                    GauceDataSet gauceDs = new GauceDataSet(vds.getName());
                    gauceResponse.enableFirstRow(gauceDs);

                    // 컬럼설정
                    for(int j=0;j<vds.getColumnCount();j++)
                    {
                        VColumInfo columinfo = vds.getColumInfo(j);
                        int datatype = columinfo.getDataType();
                        String columname = columinfo.getName();
                        
                        if( datatype == VDataSet.STRING )
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_STRING));
                        else if (datatype == VDataSet.FLOAT) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_DECIMAL));
                        else if (datatype == VDataSet.DOUBLE) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_DECIMAL));
                        else if (datatype == VDataSet.INT) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_INT));
                        else if (datatype == VDataSet.FLOAT) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_NUMBER));
                        else if (datatype == VDataSet.DATE) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_STRING));
                        else if (datatype == VDataSet.DATE_TIME) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_STRING));
                        else if (datatype == VDataSet.BLOB) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_BLOB));
                        else if (datatype == VDataSet.BOOLEAN) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_INT));
                        else if (datatype == VDataSet.DECIMAL) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_DECIMAL));
                        else if (datatype == VDataSet.BIG_DECIMAL) 
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_DECIMAL));
                        else
                            gauceDs.addDataColumn(new GauceDataColumn(columname, GauceDataColumn.TB_STRING));
                    }
                    
                    // 데이터셋설정
                    for(int j=0;j<vds.getRowCount();j++)
                    {
                        GauceDataRow row = gauceDs.newDataRow();  
                        for(int k=0;k<vds.getColumnCount();k++)
                        {
                            row.addColumnValue(vds.getObject(j, k));
                        }
                        gauceDs.addDataRow(row);
                    }
                    
                    gauceDs.flush();
                }        

                gauceResponse.flush();
                gauceResponse.commit("SUCCESS");
                gauceResponse.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();

                gauceResponse.flush();
                gauceResponse.commit("FAIL");
                gauceResponse.close();
            }                        
        }
        catch(Exception e)
        {
            return;            
        }
        finally
        {
            serviceloader.restoreService(gauceservice);
        }
    }
    



    /**
     * @throws IOException 
     * @throws UnsupportedEncodingException ********************************************************************************************************************
     * 
     *********************************************************************************************************************/
    public void handleFlexRequest(HttpServletRequest request, HttpServletResponse response) throws PlatformException, UnsupportedEncodingException, IOException
    {
        String XQUERY_AJAX_ENCTYPE = JsQueryUtil.getProperty("GS_AJAX_ENCTYPE");
        String XQUERY_AJAX_FORMAT  = JsQueryUtil.getProperty("GS_AJAX_FORMAT");
        

        VData inVd = new VData();
        VData outVd = new VData();
        VDataSetList inVdl = new VDataSetList();
        VDataSetList outVdl = new VDataSetList();

        Element eOutDocument = new Element("result");

        /*-----------------------------------------------------------------------------------------
         *  입력 데이터의 컨버젼
        -----------------------------------------------------------------------------------------*/
        Map inRequest = request.getParameterMap();
        for(Object keyName:inRequest.keySet())
        {
            String key = (String) keyName;
            inVd.add(key, request.getParameter(key));
        }

        try
        {
            /* Global 변수처리할 내용을 설정한다. */
            HashMap globalVariableMap = registerGlobalVariable(request, response, inVd, inVdl);
            setGlobalVariableToInVd(globalVariableMap, inVd);
            setGlobalVariableToInDl(globalVariableMap, inVdl);

            executeCommonController(request, response, inVd, inVdl, outVd, outVdl);
            
            /*-----------------------------------------------------------------------------------------
             *  출력 데이터의 컨버젼
            -----------------------------------------------------------------------------------------*/
            for(Object key:outVd.keySet())
            {
                String strKey = (String)key;

                Element eOutVd = new Element(strKey);
                eOutVd.setText((String) outVd.get(key));
                eOutDocument.addContent(eOutVd);
            }

            
            for(int i = 0;i < outVdl.size();i++)
            {
                VDataSet vds = outVdl.get(i);
                Element eOutVds = new Element(vds.getName());
                
                eOutVds.setAttribute("coumncount", vds.getColumnCount()+"");
                eOutVds.setAttribute("rowcount", vds.getRowCount()+"");
                
                // 컬럼설정
                ArrayList columList = new ArrayList();
                for(int j=0;j<vds.getColumnCount();j++)
                {
                    VColumInfo columinfo = vds.getColumInfo(j);
                    columList.add(columinfo.getName());
                }
                
                for(int j=0;j<vds.getRowCount();j++)
                {
                    Element eRowData = new Element("record");
                    eRowData.setAttribute("index", j+"");

                    for(int k=0;k<columList.size();k++)
                    {
                        Element eColum = new Element((String)columList.get(k));
                        eColum.setText(vds.getString(j,k));                        
                        eRowData.addContent(eColum);
                    }                    
                    eOutVds.addContent(eRowData);
                }
                
                eOutDocument.addContent(eOutVds);                
            }   
            
            Element eErrorCode = new Element("errorCode");
            Element eErrorMsg  = new Element("errorMsg");
            eErrorCode.setText("0");
            eErrorMsg.setText("SUCCESS");
            
            eOutDocument.addContent(eErrorCode);   
            eOutDocument.addContent(eErrorMsg);   
            
            XMLOutputter outputter = null;
            
            if( "compact".equalsIgnoreCase(XQUERY_AJAX_FORMAT) )
                outputter = new XMLOutputter(Format.getCompactFormat());
            else if( "raw".equalsIgnoreCase(XQUERY_AJAX_FORMAT) )
                outputter = new XMLOutputter(Format.getRawFormat());
            else
                outputter = new XMLOutputter(Format.getPrettyFormat());
            
            String xmlout = "<?xml version='1.0' encoding='"+XQUERY_AJAX_ENCTYPE+"'?>\r\n"+outputter.outputString(eOutDocument);            
            response.setContentType("text/html; charset="+XQUERY_AJAX_ENCTYPE);            
            ServletOutputStream sos = response.getOutputStream();
            sos.write(xmlout.getBytes(XQUERY_AJAX_ENCTYPE));
            sos.close();
        }
        catch(Exception e)
        {
            Element eErrorCode = new Element("errorCode");
            Element eErrorMsg  = new Element("errorMsg");
            eErrorCode.setText("0");
            eErrorMsg.setText(e.getMessage());
            eOutDocument.addContent(eErrorCode);   
            eOutDocument.addContent(eErrorMsg);   

            XMLOutputter outputter = null;
            
            if( "compact".equalsIgnoreCase(XQUERY_AJAX_FORMAT) )
                outputter = new XMLOutputter(Format.getCompactFormat());
            else if( "raw".equalsIgnoreCase(XQUERY_AJAX_FORMAT) )
                outputter = new XMLOutputter(Format.getRawFormat());
            else
                outputter = new XMLOutputter(Format.getPrettyFormat());

            String xmlout = "<?xml version='1.0' encoding='"+XQUERY_AJAX_ENCTYPE+"'?>\r\n"+outputter.outputString(eOutDocument);
            response.setContentType("text/html; charset="+XQUERY_AJAX_ENCTYPE);            
            ServletOutputStream sos = response.getOutputStream();
            sos.write(xmlout.getBytes(XQUERY_AJAX_ENCTYPE));
            sos.close();
        }
    }
    


    /**
     * @throws IOException 
     * @throws UnsupportedEncodingException 
     * @throws ParseException 
     * @throws JSONException ********************************************************************************************************************
     * 
     *********************************************************************************************************************/
    public void handlejQueryRequest(HttpServletRequest request, HttpServletResponse response) throws PlatformException, UnsupportedEncodingException, IOException, ParseException, JSONException 
    {
        String XQUERY_AJAX_ENCTYPE = JsQueryUtil.getProperty("GS_AJAX_ENCTYPE");

        VData inVd = new VData();
        VData outVd = new VData();
        VDataSetList inVdl = new VDataSetList();
        VDataSetList outVdl = new VDataSetList();

        /*-----------------------------------------------------------------------------------------
         *  입력 데이터의 컨버젼
        -----------------------------------------------------------------------------------------*/
        StringBuilder json = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(request.getInputStream()));
        if(br != null)
        {             
            json.append( br.readLine());         
        }

        JSONObject inputJsonData = new JSONObject(URLDecoder.decode(json.toString(), XQUERY_AJAX_ENCTYPE));
        Iterator jkeys = inputJsonData.keys();
        while(jkeys.hasNext()) 
        {
            String key = jkeys.next().toString();
            
            // 단순파라미터 
            if( inputJsonData.get(key) instanceof String )
            {
                inVd.add(key, inputJsonData.get(key));

                // 콘솔로그만 찍고 빠져나간다.
                if( "consolelog".equals(key) )
                {
                    BASE64Decoder base64 = new BASE64Decoder();
                    byte[] consolelogbyte = base64.decodeBuffer((String)inputJsonData.get(key));
                    String consolelog = new String(consolelogbyte, XQUERY_AJAX_ENCTYPE);                    
                    
                    System.out.println(consolelog);
                    JSONObject outdata = new JSONObject();
                    outdata.put("errorCode", "0");
                    outdata.put("errorMsg",  "SUCCESS");
                    
                    response.setCharacterEncoding(XQUERY_AJAX_ENCTYPE);
                    response.setContentType("application/json");     
                    response.setHeader("Cache-Controll","no-cache");            
                    PrintWriter out = response.getWriter();
                    out.print(outdata);
                    out.flush();            
                    return;
                }
            }
            // JSONObject
            else if( inputJsonData.get(key) instanceof JSONObject )
            {
                VDataSet vds = new VDataSet(key);                
            	JSONObject jsonData = (JSONObject)inputJsonData.get(key);
            	
            	// 컬럼해더생성
                Iterator pkeys = jsonData.keys();
                while(pkeys.hasNext()) 
                {
                    String pkey = pkeys.next().toString();
                    vds.addColumn(pkey, VDataSet.STRING);                            
                }

                vds.newRow();
                Iterator pkeys2 = jsonData.keys();
                while(pkeys2.hasNext()) 
                {
                	String columname = pkeys2.next().toString();
                    vds.set(0, columname, jsonData.get(columname));     
                }

                // 입력데이터셋에 추가
                inVdl.add(vds);
            }
            // JSONArray
            else if( inputJsonData.get(key) instanceof JSONArray )
            {
                VDataSet vds = new VDataSet(key);                
                JSONArray jsonArray = inputJsonData.getJSONArray(key);
                
                // 컬럼정보 설정
                JSONObject columdata = (JSONObject)jsonArray.getJSONObject(0);
                Iterator columkeys = columdata.keys();
                while(columkeys.hasNext())
                {
                    String columkey = columkeys.next().toString();
                    vds.addColumn(columkey, VDataSet.STRING);                            
                }
                // ROWSTATUS 를 추가한다. 
                vds.addColumn("_ROWSTATUS", VDataSet.STRING);
                
                // ROWDATA 처리 
                for(int i=0;i<jsonArray.length();i++)
                {
                    vds.newRow();
                    JSONObject rowdata = (JSONObject)jsonArray.getJSONObject(i);
                    Iterator rowkeys = rowdata.keys();
                    while(rowkeys.hasNext())
                    {
                        String rowkey = rowkeys.next().toString();
                        vds.set(i, rowkey, rowdata.get(rowkey));     
                    }
                    
                    // ROWSATUS 처리 
                    if( rowdata.has("_ROWSTATUS") )
                    {
                        if( "C".equals( rowdata.getString("_ROWSTATUS")) )
                            vds.setRowType(i, VDataSet.INSERT);
                        else if( "U".equals( rowdata.getString("_ROWSTATUS")) )
                            vds.setRowType(i, VDataSet.UPDATE);
                        else if( "D".equals( rowdata.getString("_ROWSTATUS")) )
                            vds.setRowType(i, VDataSet.DELETE);
                    }
                    else
                    {
                        vds.setRowType(i, VDataSet.NORMAL);
                    }
                }
                // 입력데이터셋에 추가
                inVdl.add(vds);
            }
        }

        try
        {
            /* Global 변수처리할 내용을 설정한다. */
            HashMap globalVariableMap = registerGlobalVariable(request, response, inVd, inVdl);
            setGlobalVariableToInVd(globalVariableMap, inVd);
            setGlobalVariableToInDl(globalVariableMap, inVdl);

            // ServiceMapping 을 이용할경우 
            if( "".equals(inVd.getString(GS_SERVICENAME)) ) 
                excuteServiceMapping(inVd, inVdl, outVd, outVdl);
            else            
                executeCommonController(request, response, inVd, inVdl, outVd, outVdl);
            
            /*-----------------------------------------------------------------------------------------
             *  출력 데이터의 컨버젼
            -----------------------------------------------------------------------------------------*/
            JSONObject outdata = new JSONObject();
            for(Object key:outVd.keySet())
            {
                outdata.put((String)key, outVd.get(key));
            }
            
            for(int i = 0;i < outVdl.size();i++)
            {
                VDataSet vds = outVdl.get(i);
                
                // 컬럼정보
                ArrayList columList = new ArrayList();
                for(int j=0;j<vds.getColumnCount();j++)
                {
                    VColumInfo columinfo = vds.getColumInfo(j);
                    columList.add(columinfo.getName());
                }
                
                // 데이터셋목록처리 
            	JSONArray outjsondataset = new JSONArray();
                for(int j=0;j<vds.getRowCount();j++)
                {
                    JSONObject rowdata = new JSONObject();
                    for(int k=0;k<columList.size();k++)
                    {
                        rowdata.put((String)columList.get(k), vds.getString(j,k));
                    }                    
                	outjsondataset.put(rowdata);
                }                
                
                // 데이터셋추가
                outdata.put(vds.getName(), outjsondataset);                
            }

            outdata.put("errorCode", "0");
            outdata.put("errorMsg",  "SUCCESS");
            
            response.setCharacterEncoding(XQUERY_AJAX_ENCTYPE);
            response.setContentType("application/json");     
            response.setHeader("Cache-Controll","no-cache");            
            PrintWriter out = response.getWriter();
            out.print(outdata);
            out.flush();            
        }
        catch(Exception e)
        {
            JSONObject outdata = new JSONObject();
            outdata.put("errorCode", "1");
            outdata.put("errorMsg",  e.getMessage());
            
            response.setCharacterEncoding(XQUERY_AJAX_ENCTYPE);
            response.setContentType("application/json");     
            response.setHeader("Cache-Controll","no-cache");            
            PrintWriter out = response.getWriter();
            out.print(outdata);
            out.flush();            
        }
    }    
}
