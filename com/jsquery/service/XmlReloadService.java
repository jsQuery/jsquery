package com.jsquery.service;

import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Service;

import com.jsquery.jsqueryengine.JsQueryEngine;
import com.jsquery.jsqueryengine.JsQueryTestThread;
import com.jsquery.jsqueryengine.JsQueryUtil;
import com.jsquery.jsqueryengine.VData;
import com.jsquery.jsqueryengine.VDataSetList;
import com.jsquery.service.impl.JsQueryServiceImpl;

@Service("com/XmlReloadService")
public class XmlReloadService extends JsQueryServiceImpl
{
    private int GS_QUERY_TEST_THREAD = new Integer(JsQueryUtil.getProperty("GS_QUERY_TEST_THREAD"));

    public void service(HttpServletRequest request, HttpServletResponse response, VData inVd, VDataSetList inVdsl, VData outVd, VDataSetList outVdsl) throws Exception
    {
        JsQueryEngine engine = JsQueryEngine.getInstance();
        engine.reload();
    }

    public void systemQueryTest(HttpServletRequest request, HttpServletResponse response, VData inVd, VDataSetList inVdsl, VData outVd, VDataSetList outVdsl) throws Exception
    {
        ArrayList<JsQueryTestThread> selftestlist = new ArrayList<JsQueryTestThread>();
        
        for(int i = 0;i < GS_QUERY_TEST_THREAD;i++)
        {
            JsQueryTestThread selftestthread = new JsQueryTestThread();
            selftestthread.start();
            selftestlist.add(selftestthread);
        }

        int deletecount = 0;
        while(true)
        {
            Thread.sleep(1000);
            
            if( deletecount == GS_QUERY_TEST_THREAD ) break;
            for(int i = 0;i < GS_QUERY_TEST_THREAD;i++)
            {
                JsQueryTestThread selftestthread = (JsQueryTestThread)selftestlist.get(i);
                if( selftestthread != null )
                {
                    if( !selftestthread.isAlive() )
                    {
                        ++deletecount;
                        selftestthread.interrupt();
                        selftestthread = null;
                        selftestlist.set(i, selftestthread);
                    }
                }
            }
        }
        
        selftestlist = null;
    }
}
