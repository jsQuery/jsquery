package com.jsquery.jsqueryengine;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class SystemQueryTester implements ApplicationListener<ApplicationEvent>
{
    public void onApplicationEvent(ApplicationEvent event)
    {
        try
        { 
            JsQueryEngine engine = JsQueryEngine.getInstance();
            
            if( engine.SELF_TEST_RUN_FLAG == false )
            {
                // 단한번만 돌고 더이상 안돌도록 설정한다. 
                engine.SELF_TEST_RUN_FLAG = true;

                if( "true".equalsIgnoreCase(JsQueryUtil.getProperty("GS_QUERY_SELFTEST")) )
                {
                    JsQueryTestThread selftestthread = new JsQueryTestThread();
                    selftestthread.start();
                }
            }
        } 
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
