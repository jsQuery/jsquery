package com.jsquery.jsqueryengine;

import javax.servlet.http.HttpServlet;

/**
 * 웹로직서버서 시작시 XMLQUERY 를 로딩하여 메모리에 JDOM에 탑재한다. 
 * 필요시 해당클래스를 호출하여 XMLQUERY를 다시 로딩시킬 수 있다.
 * 
 * @author 최현수
 * @date 2011.09.01
 */

public class JsQueryLoader extends HttpServlet
{
    private static final long serialVersionUID = 7033607548258882998L;

    /**
     * 해당메소드를 통해서 다시클래스를 로딩한다. 
     * 
     * @param HttpServletRequest request
     * @param HttpServletResponse response
     * @author 최현수
     * @date 2011.07.25
     */
    public void loadXmlQuery() throws Exception
    {
        try
        {
            JsQueryEngine.getInstance();
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }
    
}
