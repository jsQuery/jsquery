package com.jsquery.jsqueryengine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.sql.DataSource;

import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.tobesoft.xplatform.data.VariableList;

/**
 * 
 * @author 최현수
 * @date 2011.09.01
 */

@SuppressWarnings({ "unchecked" })
@Service("JsQueryEngine")
public class JsQueryEngine
{
    private static HashMap threadConMap = null;
    private static JsQueryEngine xmlQueryEngine = null;
    private static Element xmlQueryElement = null;
    private static String xmlQueryRootDir = null;
    private static int xmlFileCount = 0;
    private static int xmlQueryIDCount = 0;
    private static ScriptEngine scriptengine = null;
    private static ScriptEngineManager scriptenginemanager = null;

    private static String GS_QUERY_REFRESH = JsQueryUtil.getProperty("GS_QUERY_REFRESH");
    private static String GS_QUERY_PATH = JsQueryUtil.getProperty("GS_QUERY_PATH");
    private static String GS_QUERY_SCRIPT = JsQueryUtil.getProperty("GS_QUERY_SCRIPT");
    private static String GS_QUERY_CONNECTION = JsQueryUtil.getProperty(JsQueryUtil.getProperty("GS_DEFAULT_CONNECTION"));
    public boolean SELF_TEST_RUN_FLAG = false;
    

    /*
     * JsQueryEngine 생성자 . Class생성시 자동으로 리소스를 메모리에 로드한다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public JsQueryEngine() throws Exception
    {
        if( xmlQueryElement == null )
        {
            loadResource();
        }
    }
    
    public ArrayList getTestQueryInfo()
    {
        ArrayList testQueryList = new ArrayList();
        
        try
        {
            List childlist = xmlQueryElement.getChildren();
            for(int i = 0;i < childlist.size();i++)
            {
                Element xmlfile = (Element) childlist.get(i);

                List querylist = xmlfile.getChildren();
                for(int j = 0;j < querylist.size();j++)
                {
                    Element queryid = (Element) querylist.get(j);
                    String testdata = queryid.getChildText("testdata");
                    if(testdata != null)
                    {
                        if(testdata.length() > 0)
                        {
                            String testqueryid = xmlfile.getName().replaceAll("_._", "/") + "." + queryid.getName();
                            VData  inputdata = new VData();
                            
                            JSONObject jsondata = new JSONObject(testdata);
                            Iterator keys = jsondata.keys();
                            while(keys.hasNext())
                            {
                                String key = (String)keys.next();
                                inputdata.add(key, jsondata.get(key));    
                            }
                            
                            HashMap testData = new HashMap();
                            testData.put("queryid", testqueryid);
                            testData.put("variable", inputdata);
                            testQueryList.add(testData);
                        }
                    }
                }
            }
            
            return testQueryList;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return testQueryList;
        }
    }
    
    /*
     * getInstance를 통하여 이미 로드된 리소스메니저를 획득한다. 
     * JsQueryEngine 를 사용하는 부분에서는 반드시 getInstance를 통하여 접근하여야 한다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public static synchronized JsQueryEngine getInstance() throws Exception
    {
        if(xmlQueryEngine == null)
        {
            threadConMap = new HashMap();
            xmlQueryEngine = new JsQueryEngine();
            scriptenginemanager = new ScriptEngineManager();
            scriptengine = scriptenginemanager.getEngineByName(GS_QUERY_SCRIPT);
        }
        return xmlQueryEngine;
    }

    /*
     * 리소스매니저를 다시 로드시킨다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public JsQueryEngine reload() throws Exception
    {
        // xmlquery refresh 가 none 이면 reload를 제공하지 않는다. Server Restart로만 처리되어진다.
        if("none".equalsIgnoreCase(GS_QUERY_REFRESH))
        {
            return getInstance();
        }
        else
        {
            loadResource();
            return xmlQueryEngine;
        }
    }

    /*
     * 실제적인 리소스매니저의 리소스를 읽어들이는 함수. XMLQUERY 를 읽어온다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public void loadResource() throws Exception
    {
        try
        {
            xmlQueryElement = null;
            xmlQueryElement = new Element("XMLQUERY");
            xmlQueryRootDir = GS_QUERY_PATH;

            xmlFileCount = 0;
            xmlQueryIDCount = 0;

            final long startTime = System.currentTimeMillis();

            loadXmlQueryDir(xmlQueryRootDir);

            final long EndTime = System.currentTimeMillis();
            final double xmlLoadTime = (double) (EndTime - startTime) / (double) 1000;

            System.out.println(GS_QUERY_PATH+" [" + xmlFileCount + "]XML Files [" + xmlQueryIDCount + "]QueryID Load Successfully " + xmlLoadTime + " Sec");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /*
     * XMLQUERY 파일을 JDOM으로 읽어들여 xmlQueryElement 전역변수에 저장을한다. 
     * 이때 디렉토리의 PATH 는 "/" 로 되어있고 이는 XML 에서 사용불가능하므로 이를 "_._" 로 디렉토리의 패스를 변경해서 처리한다. 
     * xmlQueryElement 에 elements 에 각각 XMLQUERY파일의 파일PATH 의 형태로 네이밍되어 저장되고 JDOM 을 이용해서 해당 element를 빼서 사용하도록 한다. 
     * 해당파일에 대한 속성중에 xmlfilename 과 lastModified 속성을 추가하여 lastModified 를 첵크해서 변경사항이 발생하면 자동으로 xmlfilename를 이용하여 다시 메모리에 로드하도록 한다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public void loadXmlFileToGlobal(String sFileName)
    {
        Element eQueryID;
        Element eNewQueryID;
        Element eQueryIDSub;
        Element eNewQueryIDSub;

        File xmlFile = new File(sFileName);
        long lastupdateTime = xmlFile.lastModified();

        try
        {
            String xmlFileNamePath = sFileName.replaceAll(xmlQueryRootDir, "").substring(1);
            xmlFileNamePath = xmlFileNamePath.replaceAll("(?i).xml", "");

            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(xmlFile);
            Element eXmlQueryFile = doc.getRootElement();

            // 경로의 "/"는 XML의 경로로 쓸수없기 때문에 이를 _._로 변경한다.
            String newXmlQueryFileName = xmlFileNamePath.replaceAll("/", "_._").toLowerCase();

            Element newXmlQueryFile = new Element(newXmlQueryFileName);
            newXmlQueryFile.setAttribute("xmlfilename", sFileName);
            newXmlQueryFile.setAttribute("lastModified", lastupdateTime + "");

            // XML 파일의 QUERY목록을 하나하나 읽어서 XML파일에 추가를 한다.
            List queryList = eXmlQueryFile.getChildren();
            for(int j = 0; j < queryList.size(); j++)
            {
                eQueryID = (Element) queryList.get(j);
                eNewQueryID = new Element(eQueryID.getName());
                eNewQueryID.setText(eQueryID.getText());
                ++xmlQueryIDCount;

                // QUERYID의 하위 Element를 모두 담는다.
                List queryIDList = eQueryID.getChildren();
                for(int k = 0; k < queryIDList.size(); k++)
                {
                    eQueryIDSub = (Element) queryIDList.get(k);
                    eNewQueryIDSub = new Element(eQueryIDSub.getName());
                    eNewQueryIDSub = (Element) eQueryIDSub.clone();
                    eNewQueryID.addContent(eNewQueryIDSub);
                }

                // 읽어들인 QueryID의 element를 추가한다.
                newXmlQueryFile.addContent(eNewQueryID);
            }
            
            // XML 의 script 를 global 로 로딩한다.
            String globalScript = "";
            List globalScriptList = eXmlQueryFile.getChildren("script");
            for(int i =0;i<globalScriptList.size();i++)
            {
                Element eGlobalScript = (Element) globalScriptList.get(i);
                List globalScriptAttributes = eGlobalScript.getAttributes();
                
                if( globalScriptAttributes.isEmpty() )
                {
                    globalScript += eGlobalScript.getText();
                }
                else
                {
                    for(int j=0;j<globalScriptAttributes.size();j++)
                    {
                        Attribute scriptAttribute = (Attribute)globalScriptAttributes.get(j);
                        if( "src".equalsIgnoreCase(scriptAttribute.getName()) )
                        {
                            globalScript += loadScriptFile(scriptAttribute.getValue());
                        }
                    }
                }
            }
            
            if( globalScript.length() != 0 )
            {
                Element eGlobalScript = new Element("GLOBAL_SCRIPT");
                eGlobalScript.setText(globalScript);                
                newXmlQueryFile.addContent(eGlobalScript);
            }

            // 파일의 내용에 대한 element를 추가한다.
            xmlQueryElement.addContent(newXmlQueryFile);

            ++xmlFileCount;
        }
        catch (Exception e)
        {
            // XML 파일이 아닌것은 해당사항이 없다.
            if(sFileName.toUpperCase().indexOf(".XML") != -1)
            {
                System.out.println(e.getMessage());
            }
        }
    }

    /*
     * 디렉토리에 존재하는 XML파일을 읽어들이고 이를 메모리에 로드한다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public void loadXmlQueryDir(String dirName)
    {
        try
        {
            File targetDir = new File(dirName);

            // 디렉토리경로가 아니고 파일이면 직접처리한다.
            if(targetDir.isFile())
            {
                loadXmlFileToGlobal(dirName);
                return;
            }

            // 디렉토리의 내용을 로드한다.
            String files[] = targetDir.list();
            for(int i = 0; i < files.length; i++)
            {
                File filedata = new File(dirName, files[i]);
                if(filedata.isDirectory())
                {
                    loadXmlQueryDir(dirName + "/" + files[i]);
                }
                else
                {
                    loadXmlFileToGlobal(dirName + "/" + files[i]);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /*
     * QUERYID에 해당하는 SQL문장 과 Connection 정보를 HashMape으로 리턴한다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public HashMap getQuery(String sQueryID, VData inVl) throws Exception
    {
        HashMap rtnVal = new HashMap();

        int splitPos = sQueryID.indexOf(".");
        if(splitPos == -1) { throw new Exception(sQueryID + " is wrong format!!!!!!!. Format is XmlQueryPath.QueryID"); }

        String orgXmlQueryFileName = sQueryID.substring(0, splitPos);
        String queryID = sQueryID.substring(splitPos + 1);

        // FILEPATH "/" 를 "_._" 로 바꿔처리하기때문에 이를 변경해줘야한다.
        String xmlQueryFileName = orgXmlQueryFileName.replaceAll("/", "_._").toLowerCase();

        // XML 파일을 찾는다.
        List eXmlQueryFileList = xmlQueryElement.getChildren(xmlQueryFileName);
        if(eXmlQueryFileList.size() == 0) { throw new Exception(queryID + " is not found in " + GS_QUERY_PATH + "/" + orgXmlQueryFileName); }

        Element eXmlQueryFile = (Element) eXmlQueryFileList.get(0);

        // XMLQUERY 의 최신버전으로 수정되었는지의 여부를 첵크하여 Update된경우 해당 XMLQUERY만 리로드처리하여 최신버전으로 리턴한다.
        if("dynamic".equalsIgnoreCase(GS_QUERY_REFRESH))
        {
            String targetXMLFile = eXmlQueryFile.getAttributeValue("xmlfilename");
            String updateTime = eXmlQueryFile.getAttributeValue("lastModified");

            File xmlFile = new File(targetXMLFile);
            String lastupdateTime = xmlFile.lastModified() + "";
            if(!updateTime.equals(lastupdateTime))
            {
                xmlQueryElement.removeChildren(xmlQueryFileName);
                loadXmlFileToGlobal(targetXMLFile);

                return getQuery(sQueryID, inVl);
            }
        }

        // QUERYID를 찾는다.
        List eXmlQueryIDList = eXmlQueryFile.getChildren(queryID);
        if(eXmlQueryIDList.size() == 0) { throw new Exception("QueryID " + queryID + " is not found error."); }

        Element eXmlQueryID = (Element) eXmlQueryIDList.get(0);
        rtnVal.put("connection", eXmlQueryID.getChildText("connection"));
        
        // Procedure 처리시 필요한 파라미터 정보 설정
        List eparamsList = eXmlQueryID.getChildren("parameters");
        if( eparamsList.size() != 0 )
        {
            Element eparam = (Element)eparamsList.get(0);
            rtnVal.put("parameters", eparam.getChildren());
        }
        
        // Global Script 처리  
        String globalScript = eXmlQueryFile.getChildText("GLOBAL_SCRIPT");
        if( globalScript == null )
        {
            globalScript = "";
        }

        // QueryID Script 처리 
        String queryIDScript = "";
        List queryIDScriptList = eXmlQueryID.getChildren(GS_QUERY_SCRIPT);
        for(int i =0;i<queryIDScriptList.size();i++)
        {
            Element eGlobalScript = (Element) queryIDScriptList.get(i);
            List queryIDScriptAttributes = eGlobalScript.getAttributes();
            
            if( queryIDScriptAttributes.isEmpty() )
            {
                queryIDScript += eGlobalScript.getText();
            }
            else
            {
                for(int j=0;j<queryIDScriptAttributes.size();j++)
                {
                    Attribute scriptAttribute = (Attribute)queryIDScriptAttributes.get(j);
                    if( "src".equalsIgnoreCase(scriptAttribute.getName()) )
                    {
                        queryIDScript += loadScriptFile(scriptAttribute.getValue());
                    }
                }
            }
        }
        
        // SQL 문장의 Script 처리 
        List sqlchild = eXmlQueryID.getChildren("sql");
        Element sqlroot = (Element) sqlchild.get(0);
        List subscriptcheck = sqlroot.getChildren();
        String querystr = "";

        if( subscriptcheck.size() != 0 )
        {
            // 원본에 대한 회손을 방지하기 위해서 clone 으로 복사본을 만들어서 사용한다.
            Element newsqlnode = (Element) sqlroot.clone();
            List subscriptlist = newsqlnode.getChildren();
            for(int k = 0; k < subscriptlist.size(); k++)
            {
                Element subscript = (Element) subscriptlist.get(k);
                String script = "";
                String scriptrtnval = "";

                try
                {
                    script = getRunTimeScript(globalScript, queryIDScript, subscript.getText(), inVl);
                    scriptrtnval = getEvalScript(script, inVl); 
                    subscript.setText(scriptrtnval);
                }
                catch(Exception e)
                {
                    try
                    {
                        String   errormsg  = e.getMessage();
                        String[] allscript = script.split("\n");
                        String[] errorlist = errormsg.split("at line number ");
                        int     lineno     = Integer.parseInt(errorlist[1].trim());
                        for(int i=0;i<allscript.length;i++)
                        {
                            String scriptlog = (i+1)+": "+allscript[i];
                            System.out.println(scriptlog.trim());
                        }
                        
                        System.out.println(sQueryID + " : " + errorlist[0]+"\n"+lineno+": "+allscript[lineno-1]);
                        throw new Exception(errorlist[0]);                        
                    }
                    catch(Exception e2)
                    {
                        throw new Exception(sQueryID + "\n" + e2.getMessage());
                    }
                }
            }
            querystr = newsqlnode.getValue();
        }
        else
        {
            querystr = eXmlQueryID.getChildText("sql");
        }
        
        // /*[[ ]]*/ 로 처리되는 자바스크립트처리를 수행한다.
        ArrayList scriptlist = getScriptList(sQueryID, globalScript, queryIDScript, querystr, inVl);
        for(int i=0;i<scriptlist.size();i++)
        {
            HashMap result     = (HashMap)scriptlist.get(i);
            String  orgscript  = (String)result.get("SRC");
            String  evalresult = (String)result.get("RESULT");

            // 진짜 null / "" 을 "" 으로 설정해준다.  
            if( "null".equalsIgnoreCase(evalresult) || "".equalsIgnoreCase(evalresult) || evalresult == null )
            {
                evalresult = "";
            }
            
            querystr = replaceAll(querystr, "/*[["+orgscript+"]]*/", evalresult);
        }
        
        rtnVal.put("sql", querystr);
        return rtnVal;
    }
    

    /*
     * javascript 를 처리할때 발생하는 변수값이 없을때 오류가 발생하는데 클라이언트 변수전달이 안되는것은 오류가 아니도록 처리한다.
     *  
     * @author 최현수
     * @date 2014.07.08
     */
    public String getEvalScript(String javscript, VData inputData) throws Exception
    {
        String rtnval = "";
        
        try
        {
            // 입력파라미터에 대한 바인딩 변수처리  
            Bindings newbindings = scriptengine.createBindings();
            Iterator<?> keylist = inputData.keySet().iterator();
            while(keylist.hasNext())
            {
                String key     = (String)keylist.next();
                String value   = (String)inputData.getString(key);

                // 진짜 null / "" 을 "" 으로 설정해준다.  
                if( "null".equalsIgnoreCase(value) || "".equalsIgnoreCase(value) || value == null )
                {
                    newbindings.put(key, "");
                }
                else
                {
                    newbindings.put(key, value);
                }
            }
            
            //scriptengine.setBindings(newbindings, ScriptContext.ENGINE_SCOPE);
            rtnval = (String) scriptengine.eval(javscript, newbindings);
        }
        catch(ScriptException e)
        {
            String errormsg = e.getMessage();

            // 변수값이 존재하지 않을때는 해당변수를 "" 로 다시 설정을 해서 처리한다. 
            if( errormsg.indexOf("sun.org.mozilla.javascript.internal.EcmaError: ReferenceError:") != -1 )
            {
                String[] variablebuff = errormsg.split("\"");
                inputData.put(variablebuff[1], "");
                System.out.println("Javascript Warnning : Variable["+variablebuff[1]+ "] is undefined.");
                return getEvalScript(javscript, inputData);
            }
            else
            {
                throw new Exception(e.getMessage());
            }
        }
        
        return rtnval;
    }

    /*
     *  *[[ ]]* 로 처리되는 javascrpt 문장을 처리 한다. 
     * 
     * @author 최현수
     * @date 2014.07.06
     */
    public ArrayList getScriptList(String sQueryID, String globalfunction, String localfunction, String scriptstr, VData inputData) throws Exception
    {
        ArrayList scriptlist = new ArrayList();
        
        String[] buff = scriptstr.split("\\]\\]\\*\\/");
        for(int i=0;i<buff.length-1;i++)
        {
            String[] newbuff = buff[i].split("\\/\\*\\[\\[");
            scriptlist.add(newbuff[1]);
        }
        
        if( scriptlist.size() != 0 )
        {
            ScriptEngineManager mgr = new ScriptEngineManager();
            ScriptEngine engine = mgr.getEngineByName(GS_QUERY_SCRIPT);

            // 입력파라미터를 전역변수처리한다. 
            Iterator<?> keylist = inputData.keySet().iterator();
            while(keylist.hasNext())
            {
                String key     = (String)keylist.next();
                String value   = (String)inputData.getString(key);

                // 진짜 null / "" 을 "" 으로 설정해준다.  
                if( "null".equalsIgnoreCase(value) || "".equalsIgnoreCase(value) || value == null )
                {
                    engine.put(key, "");
                }
                else
                {
                    engine.put(key, value);
                }
            }            

            ArrayList rtnval = new ArrayList();
            for(int i=0;i<scriptlist.size();i++)
            {
                String evaltargetscript = "";
                
                try
                {
                    evaltargetscript = globalfunction+""+localfunction+""+scriptlist.get(i);

                    HashMap scriptinfo = new HashMap();
                    scriptinfo.put("SRC",    scriptlist.get(i));
                    scriptinfo.put("RESULT", getEvalScript(evaltargetscript, inputData));
                    rtnval.add(scriptinfo);
                }
                catch(Exception e)
                {
                    String[] allscript = evaltargetscript.split("\n");
                    String[] errorlist = e.getMessage().split("at line number ");
                    int     lineno     = Integer.parseInt(errorlist[1].trim());
                    for(int j=0;j<allscript.length;j++)
                    {
                        String scriptlog = (j+1)+": "+allscript[j];
                        System.out.println(scriptlog.trim());
                    }
                    
                    System.out.println(sQueryID + " : " + errorlist[0]+"\n"+lineno+": "+allscript[lineno-1]);
                    throw new Exception(errorlist[0]);                        
                }
            }
            return rtnval;
        }
        else
        {        
            return scriptlist;
        }        
    }
    
    /*
     * XMLQUERY에 들어있는 servicemapping 을 읽어서 그정보를 리턴한다.
     * 
     * @author 최현수
     * 
     * @date 2012.12.13
     */
    public HashMap getServiceMapping(String sServiceMappingID) throws Exception
    {
        HashMap mappingMap = new HashMap<String, String>();
        
        int splitPos = sServiceMappingID.indexOf(".");
        if(splitPos == -1) { throw new Exception(sServiceMappingID + " is wrong format!!!!!!!. Format is XmlQueryPath.serviceMappingID"); }

        String orgXmlQueryFileName = sServiceMappingID.substring(0, splitPos);
        String serviceMappingID = sServiceMappingID.substring(splitPos + 1);

        // FILEPATH "/" 를 "_._" 로 바꿔처리하기때문에 이를 변경해줘야한다.
        String xmlQueryFileName = orgXmlQueryFileName.replaceAll("/", "_._");

        // XML 파일을 찾는다.
        List eXmlQueryFileList = xmlQueryElement.getChildren(xmlQueryFileName.toLowerCase());
        if(eXmlQueryFileList.size() == 0) { throw new Exception(serviceMappingID + " is not found in " + GS_QUERY_PATH + "/" + orgXmlQueryFileName); }

        Element eXmlQueryFile = (Element) eXmlQueryFileList.get(0);

        // XMLQUERY 의 최신버전으로 수정되었는지의 여부를 첵크하여 Update된경우 해당 XMLQUERY만 리로드처리하여 최신버전으로 리턴한다.
        if("dynamic".equalsIgnoreCase(GS_QUERY_REFRESH))
        {
            String targetXMLFile = eXmlQueryFile.getAttributeValue("xmlfilename");
            String updateTime = eXmlQueryFile.getAttributeValue("lastModified");

            File xmlFile = new File(targetXMLFile);
            String lastupdateTime = xmlFile.lastModified() + "";
            if(!updateTime.equals(lastupdateTime))
            {
                xmlQueryElement.removeChildren(xmlQueryFileName);
                loadXmlFileToGlobal(targetXMLFile);

                return getServiceMapping(sServiceMappingID);
            }
        }

        // servicemapping을 먼저 찾는다.
        List eXmlQueryIDList = eXmlQueryFile.getChildren("service-mapping");
        if( eXmlQueryIDList.size() == 0) { throw new Exception("service-mapping is not exist."); }

        Element eServiceMapping = (Element)eXmlQueryIDList.get(0);
        List eServiceMappingIDList = eServiceMapping.getChildren(serviceMappingID);
        if( eServiceMappingIDList.size() == 0 ){ throw new Exception(serviceMappingID+" is not exist."); }

        Element eServiceMappingID = (Element)eServiceMappingIDList.get(0);
        mappingMap.put("in",  eServiceMappingID.getChildText("in"));
        mappingMap.put("out", eServiceMappingID.getChildText("out"));
        
        if( !eServiceMappingID.getChildren("validation").isEmpty() )
        {
            mappingMap.put("validation", eServiceMappingID.getChildren("validation").get(0));
        }        
        return mappingMap;
    }
    

    /*
     * SQL 의 if else 다중분기처리로직 script 로 다중분기처리하면 해당 내용은 사용하지 않으나 버리기에는 너무 아까운 소스라서..
     * 
     * @author 최현수
     * 
     * @date 2011.12.30
     */
    public String getMultipleIfSyntax(String syntax, VariableList params) throws Exception
    {
        int strlen = syntax.length();

        // 제일먼저 ( 시작을 찾아 안의 내용 조건절 START 찾는다.
        int condspos = syntax.indexOf("(");
        int condepos = 0;
        if(condspos == -1) { throw new Exception("Mutiple if syntax Error"); }

        // 조건절의 "." 을 찾아 split을 한다.
        String condsyntax = "";
        String variablename = "";
        String comparemethod = "";
        String comparevalue = "";
        boolean compareresult = false;

        String[] buff = syntax.substring(condspos + 1).split("[.]");
        String[] buff2 = buff[1].split("[ )]");
        variablename = buff[0].trim().toUpperCase();
        comparemethod = buff2[0].trim();

        // isnull / isnotnull 처리
        if("isnull".equalsIgnoreCase(comparemethod) || "isnotnull".equalsIgnoreCase(comparemethod))
        {
            // 조건절의 마지막 ) 를 찾는다.
            int braketendCount = 0;
            for(int i = condspos + 1; i < strlen; i++)
            {
                if(syntax.charAt(i) == ')')
                {
                    if(++braketendCount == 1)
                    {
                        condepos = i;
                        break;
                    }
                }
            }
            condsyntax = syntax.substring(condspos + 1, condepos);
            // System.out.println("조건절 => "+condsyntax);
            // System.out.println("variablename["+variablename+"]comparemethod["+comparemethod+"]comparevalue["+comparevalue+"]");
        }
        // equals / iequals / notequals / inotequals / gt / lt / gte / lte 처리
        else
        {
            // 조건절의 마지막 ) 를 찾는다.
            int braketendCount = 0;
            for(int i = condspos + 1; i < strlen; i++)
            {
                if(syntax.charAt(i) == ')')
                {
                    if(++braketendCount == 2)
                    {
                        condepos = i;
                        break;
                    }
                }
            }

            condsyntax = syntax.substring(condspos + 1, condepos);
            String[] condbuff = condsyntax.split("[.]");
            variablename = condbuff[0].trim().toUpperCase();
            String[] condbuff2 = condbuff[1].trim().split("[(]");
            comparemethod = condbuff2[0].trim();
            String[] condbuff3 = condbuff2[1].trim().split("[)]");
            String[] condbuff4 = condbuff3[0].trim().split("\"");
            comparevalue = condbuff4[1];

            // System.out.println("조건절 => "+condsyntax);
            // System.out.println("variablename["+variablename+"]comparemethod["+comparemethod+"]comparevalue["+comparevalue+"]");
        }

        // 해당조전절이 참인지를 판별한다.
        if(comparemethod.equalsIgnoreCase("isnull"))
        {
            comparevalue = params.getString(variablename) + "";
            if("".equals(comparevalue) || "null".equalsIgnoreCase(comparevalue))
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("isnotnull"))
        {
            comparevalue = params.getString(variablename) + "";
            if("".equals(comparevalue) || "null".equalsIgnoreCase(comparevalue))
                compareresult = false;
            else
                compareresult = true;
        }
        else if(comparemethod.equalsIgnoreCase("equals"))
        {
            String targetvalue = params.getString(variablename) + "";
            if(comparevalue.equals(targetvalue))
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("iequals"))
        {
            String targetvalue = params.getString(variablename) + "";
            if(comparevalue.equalsIgnoreCase(targetvalue))
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("notequals"))
        {
            String targetvalue = params.getString(variablename) + "";
            if(comparevalue.equals(targetvalue))
                compareresult = false;
            else
                compareresult = true;
        }
        else if(comparemethod.equalsIgnoreCase("notiequals"))
        {
            String targetvalue = params.getString(variablename) + "";
            if(comparevalue.equalsIgnoreCase(targetvalue))
                compareresult = false;
            else
                compareresult = true;
        }
        else if(comparemethod.equalsIgnoreCase("gt")) // 보다큰
        {
            Double targetvalue = params.getDouble(variablename);
            if(Double.parseDouble(comparevalue) < targetvalue)
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("gte")) // 같거나큰
        {
            Double targetvalue = params.getDouble(variablename);
            if(Double.parseDouble(comparevalue) <= targetvalue)
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("lt")) // 보다작은
        {
            Double targetvalue = params.getDouble(variablename);
            if(Double.parseDouble(comparevalue) > targetvalue)
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("lte")) // 같거나작은
        {
            Double targetvalue = params.getDouble(variablename);
            if(Double.parseDouble(comparevalue) >= targetvalue)
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("between")) // 사이
        {
            String[] comparelist = comparevalue.split(",");
            Double targetvalue = params.getDouble(variablename);

            if(Double.parseDouble(comparelist[0].trim()) < targetvalue && Double.parseDouble(comparelist[1].trim()) > targetvalue)
                compareresult = true;
            else
                compareresult = false;
        }
        else if(comparemethod.equalsIgnoreCase("betweene")) // 같은이 폼함된 사이
        {
            String[] comparelist = comparevalue.split(",");
            Double targetvalue = params.getDouble(variablename);

            if(Double.parseDouble(comparelist[0].trim()) <= targetvalue && Double.parseDouble(comparelist[1].trim()) >= targetvalue)
                compareresult = true;
            else
                compareresult = false;
        }

        // System.out.println("comparevalue["+compareresult+"]");

        String newsyntax = "";
        // 해당조건절이 참일경우 "if(){ }else if{ }" 의 if(){ } 의 "{}"블럭에대한 값을 찾아서 재귀호출한다.
        if(compareresult == true)
        {
            boolean bracestart = false;
            int bracestartpos = 0;
            int braceendpos = 0;
            int bracecount = 0;
            for(int i = condepos; i < strlen - 1; i++)
            {
                if(syntax.charAt(i) == '{')
                {
                    if(bracestart == false)
                    {
                        bracestartpos = i + 1;
                        bracestart = true;
                    }
                    ++bracecount;
                }

                if(syntax.charAt(i) == '}')
                    --bracecount;

                if(bracestart == true && bracecount == 0)
                {
                    braceendpos = i;
                    break;
                }
            }

            newsyntax = syntax.substring(bracestartpos, braceendpos);
            String checknewsyntax = newsyntax.toLowerCase().replaceAll(" ", "");

            // 처리결과 문장이 if(){} 를 포함하면 다시 재귀호출한다.
            if(checknewsyntax.indexOf("if(") != -1 && checknewsyntax.indexOf(")") != -1 && checknewsyntax.indexOf("{") != -1 && checknewsyntax.indexOf("}") != -1)
            {
                return getMultipleIfSyntax(newsyntax, params);
            }
            else
            {
                // else {} 의 경우는 {}안에 있는것만 리턴해준다.
                if(checknewsyntax.indexOf("{") != -1 && checknewsyntax.indexOf("}") != -1)
                {
                    int elsebracespos = 0;
                    int elsebraceepos = 0;
                    for(int i = 0; i < newsyntax.length(); i++)
                    {
                        if(newsyntax.charAt(i) == '{')
                        {
                            elsebracespos = i + 1;
                            break;
                        }
                    }

                    for(int i = newsyntax.length() - 1; i > 0; i--)
                    {
                        if(newsyntax.charAt(i) == '}')
                        {
                            elsebraceepos = i;
                            break;
                        }
                    }

                    return newsyntax.substring(elsebracespos, elsebraceepos);
                }
                else
                {
                    return newsyntax;
                }
            }
        }
        // 해당조건절이 참이 아닐경우 "if(){ }else if{ }" 의 다음조건절까지"if(){ }"를 찾아서 해당함수를 재귀호출한다.
        else
        {
            boolean bracestart = false;
            int braceendpos = 0;
            int bracecount = 0;
            for(int i = condepos; i < strlen - 1; i++)
            {
                if(syntax.charAt(i) == '{')
                {
                    if(bracestart == false)
                    {
                        bracestart = true;
                    }
                    ++bracecount;
                }

                if(syntax.charAt(i) == '}')
                    --bracecount;

                if(bracestart == true && bracecount == 0)
                {
                    braceendpos = i;
                    break;
                }
            }

            newsyntax = syntax.substring(braceendpos + 1);
            String checknewsyntax = newsyntax.toLowerCase().replaceAll(" ", "");
            // System.out.println("next if=>"+ newsyntax );

            // 처리결과 문장이 if(){} 를 포함하면 다시 재귀호출한다.
            if(checknewsyntax.indexOf("if(") != -1 && checknewsyntax.indexOf(")") != -1 && checknewsyntax.indexOf("{") != -1 && checknewsyntax.indexOf("}") != -1)
            {
                return getMultipleIfSyntax(newsyntax, params);
            }
            else
            {
                // else {} 의 경우는 {}안에 있는것만 리턴해준다.
                if(checknewsyntax.indexOf("{") != -1 && checknewsyntax.indexOf("}") != -1)
                {
                    int elsebracespos = 0;
                    int elsebraceepos = 0;
                    for(int i = 0; i < newsyntax.length(); i++)
                    {
                        if(newsyntax.charAt(i) == '{')
                        {
                            elsebracespos = i + 1;
                            break;
                        }
                    }

                    for(int i = newsyntax.length() - 1; i > 0; i--)
                    {
                        if(newsyntax.charAt(i) == '}')
                        {
                            elsebraceepos = i;
                            break;
                        }
                    }

                    return newsyntax.substring(elsebracespos, elsebraceepos);
                }
                else
                {
                    return newsyntax;
                }
            }
        }
    }

    /*
     * QUERYID에 해당하는 내용을 JDOM 의 Element속성 그대로 리턴한다. 
     * 추후 하위 Elemnet를 이용해서 작업할때 확장성을 고려해서 여분으로 메소드를 만듬.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public Element getQueryElement(String sQueryID) throws Exception
    {
        int splitPos = sQueryID.indexOf(".");
        if(splitPos == -1) { throw new Exception("Wrong format!!!!!!!. Format is XmlQueryPath.QueryID"); }

        String orgXmlQueryFileName = sQueryID.substring(0, splitPos);
        String queryID = sQueryID.substring(splitPos + 1);

        // FILEPATH "/" 를 "_._" 로 바꿔처리하기때문에 이를 변경해줘야한다.
        String xmlQueryFileName = orgXmlQueryFileName.replaceAll("/", "_._");

        // XML 파일을 찾는다.
        List eXmlQueryFileList = xmlQueryElement.getChildren(xmlQueryFileName);
        if(eXmlQueryFileList.size() == 0) { throw new Exception("Resource File " + orgXmlQueryFileName + " is not found error."); }

        Element eXmlQueryFile = (Element) eXmlQueryFileList.get(0);

        // XMLQUERY 의 최신버전으로 수정되었는지의 여부를 첵크하여 Update된경우 해당 XMLQUERY만 리로드처리하여 최신버전으로 리턴한다.
        String targetXMLFile = eXmlQueryFile.getAttributeValue("xmlfilename");
        String updateTime = eXmlQueryFile.getAttributeValue("lastModified");
        File xmlFile = new File(targetXMLFile);
        String lastupdateTime = xmlFile.lastModified() + "";
        if(!updateTime.equals(lastupdateTime))
        {
            xmlQueryElement.removeChildren(xmlQueryFileName);
            loadXmlFileToGlobal(targetXMLFile);

            return getQueryElement(sQueryID);
        }

        // QUERYID를 찾는다.
        List eXmlQueryIDList = eXmlQueryFile.getChildren(queryID);
        if(eXmlQueryIDList.size() == 0) { throw new Exception("QueryID " + queryID + " is not found error."); }

        return (Element) eXmlQueryIDList.get(0);
    }

    /*
     * Servlet Thread로 돌아가는 AbstractXPService에서 사용한 DBConnection 정보를 클리어한다. 키값은 해당 Thread의 uniqID이다.
     * 
     * @author 최현수
     * 
     * @date 2011.09.07
     */
    public static void removeUsedConnList2() throws Exception
    {
        String threadID = Thread.currentThread().getName();

        try
        {
            HashMap currentThreadMap = (HashMap) threadConMap.get(threadID);

            if(currentThreadMap == null)
            {
                return;
            }
            else
            {
                threadConMap.remove(threadID);
            }
        }
        catch (Exception e)
        {
            // DB Connection 을 전혀 사용하지 않은경우 아무런 상관이 없음.
            ;
        }
    }
    
    /*
     * 트랜잭션처리가 필요한지의 여부를 설정한다. 
     * @author 최현수
     * @date 2014.11.07
     */    
    public void setTransactionFlag() throws Exception
    {
        String threadID = Thread.currentThread().getName();
        String transactionflag = (String)threadConMap.get(threadID+"_TRANSACTION");
        if(transactionflag == null)
        {
            threadConMap.put(threadID+"_TRANSACTION", "true");
        }
    }    

    /*
     * commit/rollback 시 트랜잭션이 정말 필요한지의 여부를 리턴한다. 트랜잭션이 필요하면 true 없으면 false 
     * @author 최현수
     * @date 2014.11.07
     */    
    public boolean getTransactionFlag() throws Exception
    {
        String threadID = Thread.currentThread().getName();
        String transactionflag = (String)threadConMap.get(threadID+"_TRANSACTION");
        if(transactionflag == null)
            return false;
        else
            return true;
    }
    
    public void commit() throws Exception
    {
        String threadID = Thread.currentThread().getName();
        HashMap currentThreadMap = (HashMap) threadConMap.get(threadID);
        if(currentThreadMap == null)
        {
            return;
        }
        else
        {
            Iterator it = (Iterator) currentThreadMap.keySet().iterator();
            while (it.hasNext())
            {
                String key = (String) it.next();
                Connection con = (Connection) currentThreadMap.get(key);
                if(con != null)
                {
                    // 커밋이 필요한경우에만 커밋한다.
                    if( getTransactionFlag() == true )
                    {
                        try
                        {
                            con.commit();
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                        }
                    }

                    try
                    {
                        con.close();
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            threadConMap.remove(threadID);
            threadConMap.remove(threadID+"_TRANSACTION");
        }
    }

    public void rollback() throws Exception
    {
        String threadID = Thread.currentThread().getName();
        HashMap currentThreadMap = (HashMap) threadConMap.get(threadID);
        if(currentThreadMap == null)
        {
            return;
        }
        else
        {
            Iterator it = (Iterator) currentThreadMap.keySet().iterator();
            while (it.hasNext())
            {
                String key = (String) it.next();
                Connection con = (Connection) currentThreadMap.get(key);
                if(con != null)
                {
                    // 롤백이 필요한경우에만 롤백한다.
                    if( getTransactionFlag() == true )
                    {
                        try
                        {
                            con.rollback();
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                        }
                    }

                    try
                    {
                        con.close();
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            threadConMap.remove(threadID);
            threadConMap.remove(threadID+"_TRANSACTION");
        }
    }

    /*
     * DBConnection 을 리턴한다. XP MultiConnection 처리로 처리하지 않고 사용하는 프로그램에서 Connection 을 직접 Close 처리 하도록 하기위함. 
     * removeUsedConnList 로 Close하는 것 을 모르기때문에...
     * 
     * @param String connectionName
     * 
     * @return Connection
     * 
     * @author 최현수
     * 
     * @date 2012.01.27
     */
    public static Connection getConnectionPool(String connectionName) throws MissingResourceException, SQLException, NamingException
    {
        InitialContext context = new InitialContext();
        DataSource datasource;
        
        try
        {
            datasource = (DataSource) context.lookup(connectionName);            
        }
        catch(Exception e)
        {
            Context envContext  = (Context)context.lookup("java:/comp/env");
            datasource = (DataSource) envContext.lookup(connectionName);
        }
       
        Connection dbconn = datasource.getConnection();
        dbconn.setAutoCommit(false);        
        return dbconn;
    }

    /*
     * Servlet Thread로 돌아가는 AbstractXPService에서 연결요청한 DBConnection 의 유무를 판별해 그값을 리턴한다. 
     * 없으면 신규생성을 하고 있으면 존재하는 Connection 을 재사용한다.
     * 
     * @param String connectionName
     * 
     * @return Connection
     * 
     * @author 최현수
     * 
     * @date 2011.09.07
     */
    public Connection getConnection(String connectionName) throws Exception
    {
        Connection rtnVal = null;
        String threadID = Thread.currentThread().getName();

        // 기본DB를 GS_DB_01 로 설정한다.
        if(connectionName == null || "".equals(connectionName))
        {
            connectionName = GS_QUERY_CONNECTION;
        }
        else
        {
            connectionName = JsQueryUtil.getProperty(connectionName);
        }

        HashMap currentThreadMap = (HashMap) threadConMap.get(threadID);
        if(currentThreadMap == null)
        {
            currentThreadMap = new HashMap();
        }

        // 현재 Thread에 값이 존재하면 그값을 리턴한다.
        if(currentThreadMap.containsKey(connectionName))
        {
            rtnVal = (Connection) currentThreadMap.get(connectionName);

            // Connection 이 죽은 Connection 일때 이를 다시 Connection을 얻어와서 처리를 하도록한다.
            if(rtnVal.isClosed())
            {
                try
                {
                    rtnVal = getConnectionPool(connectionName);
                    rtnVal.setAutoCommit(false);

                    currentThreadMap.put(connectionName, rtnVal);
                    threadConMap.put(threadID, currentThreadMap);
                    return rtnVal;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    throw new Exception(e.getMessage());
                }
            }
            else
            {
                return rtnVal;
            }
        }
        else
        {
            try
            {
                rtnVal = getConnectionPool(connectionName);
                
                if(rtnVal.isClosed())
                {
                    System.out.println("################################ 죽은넘이잖어... 썅....");
                    
                    rtnVal.close();
                    rtnVal = getConnectionPool(connectionName);
                }
                
                rtnVal.setAutoCommit(false);
                currentThreadMap.put(connectionName, rtnVal);

                // 신규OPEN한 Thread Connection 정보를 Update한다.
                threadConMap.put(threadID, currentThreadMap);
                return rtnVal;
            }
            catch (Exception e)
            {
                e.printStackTrace();
                throw new Exception(e.getMessage());
            }
        }
    }

    /*
     * 현재 쓰레드AbstractXPService에서 사용한 ConnectionMap 을 리턴한다.
     * 
     * @author 최현수
     * 
     * @date 2011.08.12
     */
    public static HashMap getUsedConnList() throws Exception
    {
        try
        {
            return (HashMap) threadConMap.get(Thread.currentThread().getName());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /*
     * 동적 XML QUERY 를 javascript 로 처리를 할때 javascript의 Global Function 을 미리 로드한다.
     * 
     * @author 최현수
     * 
     * @date 2012.01.02
     */
    public static String loadScriptFile(String filename) throws Exception
    {
        String globaljs = "";

        try
        {
            // xmlquery.js 를 로드한다.
            BufferedReader instrm = new BufferedReader(new InputStreamReader(new FileInputStream(GS_QUERY_PATH + filename), "UTF8"));
            String tmpstr = "";
            while ((tmpstr = instrm.readLine()) != null)
            {
                globaljs += tmpstr + "\n";
            }
            instrm.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return globaljs;
    }

    /*
     * 동적 XML QUERY 를 javascript 로 처리를 할때 javascript의 Global Function 을 미리 로드한다.
     * 
     * @author 최현수
     * 
     * @date 2012.01.02
     */
    public String getRunTimeScript(String globalScript, String queryIDScript, String script, VData inVl)
    {
        // 문자열 " " ' ' 안에 들어있는 :변수를 #변수# 으로 변경을 한다.
        String newscript = "";
        ArrayList<String> bindParamList = new ArrayList<String>();

        char targetstr = 0;
        String scriptstring = "";
        boolean strflag = false;
        for(int i = 0; i < script.length(); i++)
        {
            if(i == 0)
            {
                newscript += script.charAt(i);
                continue;
            }

            if((script.charAt(i) == '\'' || script.charAt(i) == '\"') && script.charAt(i - 1) != '\\')
            {
                // 문자열시작
                if(strflag == false)
                {
                    scriptstring = "";
                    targetstr = script.charAt(i);
                    newscript += script.charAt(i);
                    strflag = true;

                    continue;
                }
                // 문자열종료
                else if(strflag == true && script.charAt(i) == targetstr)
                {
                    // 치환할 문자열의 값에 바인드 변수 표기가 있으면 이는 다른 문자로 바꿔버린다.
                    if(scriptstring.indexOf(":") != -1)
                    {
                        newscript += scriptstring.replaceAll(":", "_STRING_BIND_MARK_");
                    }
                    else
                    {
                        newscript += scriptstring;
                    }

                    strflag = false;
                    newscript += script.charAt(i);
                    continue;
                }
                else
                {
                    if(strflag == true)
                    {
                        scriptstring += script.charAt(i);
                        continue;
                    }
                }
            }
            else
            {
                if(strflag == true)
                {
                    scriptstring += script.charAt(i);
                }
                else
                {
                    newscript += script.charAt(i);
                }
            }
        }

        // javascript 에서 :변수 를 실제 문자열값으로 치환한다.
        String[] buff = newscript.split(":");
        for(int i = 1; i < buff.length; i++)
        {
            String[] bindParam = buff[i].split("[ ;\\.!=,)|+/*%\r\r\n\t-]");
            String bindParamName = bindParam[0].trim().toUpperCase() + "";
            if(!"".equals(bindParamName))
            {
                bindParamList.add(bindParamName);
            }
        }

        Collections.sort(bindParamList);
        for(int i = bindParamList.size() - 1; i > -1; i--)
        {
            String paramname = bindParamList.get(i);
            String paramvalue = "";

            try
            {
                paramvalue = inVl.getString(paramname) + "";
                if("null".equalsIgnoreCase(paramvalue))
                {
                    paramvalue = "";
                }
            }
            catch (Exception e)
            {
                paramvalue = "";
            }

            if(paramvalue.length() == 0)
            {
                newscript = newscript.replaceAll("(?i)\\:" + paramname, "\"\"");
            }
            else
            {
                try
                {
                    // 특수문자가 들어간경우는 String.replaceAll 을 쓸수가 없다..
                    if(paramvalue.indexOf("$") != -1)
                    {
                        newscript = replaceAll(newscript, ":" + paramname.toLowerCase(), "\"" + paramvalue + "\"");
                        newscript = replaceAll(newscript, ":" + paramname.toUpperCase(), "\"" + paramvalue + "\"");
                    }
                    else
                    {
                        newscript = newscript.replaceAll("(?i)\\:" + paramname, "\"" + paramvalue + "\"");
                    }
                }
                catch (Exception e)
                {
                    newscript = newscript.replaceAll("(?i)\\:" + paramname, "\"\"");
                }
            }
        }

        // 문자열의 "#_STRING_BIND_MARK_#" 를 ":" 로 치환한다.
        newscript = newscript.replaceAll("_STRING_BIND_MARK_", ":");

        return globalScript + queryIDScript + newscript;
    }

    /**
     * Replace 에 대한 버그가 있어 이를 보안처리함.
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

        if(index < 0) { return strSource; }

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
     * java Invoke 를 이용한 메소드 호출시 이전에 발생한 Exception 을 그대로 가지고 오기위해서 
     * threadID_EXCEPTION 에 값을 넣어두고 값을 빼서 사용을 하도록 구현
     * 
     * @param Object
     * @return N/A
     * @author 최현수 
     * @date 2012.09.28
     */
    public void setException(Object e)
    {
        String threadID = Thread.currentThread().getName();
        threadConMap.put(threadID+"_EXCEPTION", e);
    }

    /**
     * threadID_EXCEPTION 에 넣어둔 Exception 을 리턴한다.  
     * 
     * @return Object
     * @author 최현수 
     * @date 2012.09.28
     */
    public Object getException()
    {
        String threadID = Thread.currentThread().getName();
        
        try
        {
            Object exception = threadConMap.get(threadID+"_EXCEPTION");
            threadConMap.remove(threadID+"_EXCEPTION");
            return exception;
        }
        catch(Exception e)
        {
            return null;
        }
    }
}
