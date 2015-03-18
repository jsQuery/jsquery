package com.jsquery.jsqueryengine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.SimpleTimeZone;

import com.jsquery.dao.JsQueryDao;

/**
 * 
 * @author 최현수
 * @date 2011.09.01
 */
public class JsQueryTestThread extends Thread 
{
    private JsQueryDao dao = new JsQueryDao();
    private String GS_QUERY_SELFTEST_LOG = JsQueryUtil.getProperty("GS_QUERY_SELFTEST_LOG");

    public void run()
    {
        String testtime      = getCurrentTime("yyyyMMddHHmmss");
        String teststarttime = getCurrentTime("yyyy-MM-dd HH:mm:ss");
        long threadid       = Thread.currentThread().getId();
        int  selectcount = 0;
        int  updatecount = 0;
        int  failcount   = 0;

        ArrayList querylist = new ArrayList();
        
        System.out.println("#########################################################################################################################");
        System.out.println("["+teststarttime+ "] "+threadid + " :: start");
        System.out.println("#########################################################################################################################");
        
        VDataSet dslog = new VDataSet();
        dslog.addColumn("TEST_DATE", VDataSet.STRING);
        dslog.addColumn("THREADID", VDataSet.STRING);
        dslog.addColumn("QUERYID", VDataSet.STRING);
        dslog.addColumn("RECORDS", VDataSet.STRING);
        dslog.addColumn("PASS_YN", VDataSet.STRING);
        dslog.addColumn("ERROR_MSG", VDataSet.STRING);
        dslog.addColumn("RUNTIME", VDataSet.STRING);       

        try
        {
            querylist = JsQueryEngine.getInstance().getTestQueryInfo();
            
            for(int i = 0;i < querylist.size();i++)
            {
                HashMap testquery = (HashMap) querylist.get(i);
                String queryid = (String) testquery.get("queryid");
                VData testdata = (VData) testquery.get("variable");

                VData logdata = new VData();
                logdata.add("TEST_DATE", testtime);
                logdata.add("THREADID",  threadid);
                logdata.add("QUERYID",   queryid);
                
                try
                {
                    long startTime = System.currentTimeMillis();
                    int rowcount = 0;

                    if(queryid.toLowerCase().indexOf(".select") != -1 || queryid.toLowerCase().indexOf(".search") != -1 || queryid.toLowerCase().indexOf(".get") != -1)
                    {
                        VDataSet outds = dao.select(queryid, testdata);
                        rowcount = outds.getRowCount();

                        long endTime = System.currentTimeMillis();
                        double runTime = (double) (endTime - startTime) / (double) 1000;
                        //System.out.println(threadid + " : " + queryid + " " + rowcount + " Records " + runTime + "Sec");
                        
                        logdata.add("RECORDS", rowcount);
                        logdata.add("RUNTIME", runTime);
                        logdata.add("PASS_YN", "Y");
                        dslog.addRow(logdata);
                        
                        //outds.clear();
                        outds = null;
                        
                        ++selectcount;
                    }
                    else
                    {
                        rowcount = dao.update(queryid, testdata);
                        long endTime = System.currentTimeMillis();
                        double runTime = (double) (endTime - startTime) / (double) 1000;

                        //System.out.println(threadid + " : " + queryid + " " + rowcount + " Records " + runTime + "Sec");
                        
                        logdata.add("RECORDS", 0);
                        logdata.add("RUNTIME", runTime);
                        logdata.add("PASS_YN", "Y");
                        dslog.addRow(logdata);
                        
                        ++updatecount;
                    }
                }
                catch(Exception e)
                {
                    ++failcount;

                    System.out.println("\n\n["+getCurrentTime("yyyy-MM-dd HH:mm:ss")+"] "+threadid + " :: "+queryid+ " "+e.getMessage());
                    
                    logdata.add("RECORDS",  0);
                    logdata.add("RUNTIME",  0);
                    logdata.add("PASS_YN",  "N"); 
                    logdata.add("ERROR_MSG",e.getMessage()); 
                    dslog.addRow(logdata);
                }
                finally
                {
                    dao.rollback();
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            // 시스템 테스트 정보를 DB에 저장한다.
            try
            {
                dao.save(GS_QUERY_SELFTEST_LOG, dslog);
                dao.commit();
            }
            catch(Exception e)
            {
                e.printStackTrace();
                try
                {
                    dao.rollback();
                }
                catch(Exception e1)
                {
                    e1.printStackTrace();
                }
            }
            
            System.out.println("\n\n#########################################################################################################################");
            System.out.println("["+getCurrentTime("yyyy-MM-dd HH:mm:ss")+"] "+threadid + " :: Runtime ["+teststarttime+" ~ "+getCurrentTime("yyyy-MM-dd HH:mm:ss").substring(11)+"] Execute total["+querylist.size()+"] select["+selectcount+"] update["+updatecount+"] fail["+failcount+"]");
            System.out.println("#########################################################################################################################");
            
            if( failcount > 0 )
            {
                System.out.println("SELECT * FROM T_COM_SYSTEM_TEST WHERE THREADID="+threadid+" AND TEST_DATE=TO_DATE('"+testtime+"','YYYYMMDDHH24MISS') AND PASS_YN='N'\n\n");
            }

            dao = null;
            querylist = null;
            dslog = null;
        }
    }
    
    public static String getCurrentTime(String format)
    {
        int millisPerHour = 60 * 60 * 1000;
        SimpleDateFormat fmt = new SimpleDateFormat(format);
        SimpleTimeZone timeZone = new SimpleTimeZone(9 * millisPerHour, "KST");
        fmt.setTimeZone(timeZone);
        return fmt.format(new java.util.Date(System.currentTimeMillis()));
    }
}
