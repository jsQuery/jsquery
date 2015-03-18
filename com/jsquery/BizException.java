package com.jsquery;

import java.util.PropertyResourceBundle;

import com.jsquery.jsqueryengine.JsQueryEngine;

/**
 * 사용자정의 오류를 설정한다. <BR>
 * 시스템오류는 에러코드 -1로 클라이언트에 값이 전달되어지고 해당 BizException 은 에러코드 -2로 리턴된다.
 * 
 * @author 최현수
 * @since 2012.08.08
 */
public class BizException extends Exception
{
    private static final long serialVersionUID = -4954079773449645332L;
    private static String langauge = null;
    private static String messageType = null;
    private static String messageCode = null;
    private static Object[] messageParam = null;
    private static String errorMessage = null;

    public final static String ERROR    = "-10";
    public final static String INFO     = "-20";
    public final static String WARNNING = "-30";
    

    /**
     * 클라이언트가 사용하는 LANGAUGE에 따라서 서버쪽 메세지의 LANGUAGE 도 따라가야하고 이를 처리함.
     * 
     * @param String lang 사용자 사용 LANGUAGE정보
     * @author 최현수
     * @date 2011.07.27
     */
    public void setLang(final String lang)
    {
        langauge = lang;
    }

    /**
     * 사용자정의 오류를 설정한다. <BR>
     * Exception 을 그대로 사용자오류로 리턴한다. 
     * 
     * @author 최현수
     * @throws Exception 
     * @since 2012.08.08
     */
    public BizException(final Exception sysException) throws Exception
    {
        messageType  = ERROR;
        errorMessage = sysException.getMessage();
        
        JsQueryEngine engine = JsQueryEngine.getInstance();
        engine.setException(sysException);
    }

    /**
     * 사용자정의 오류를 설정한다. <BR>
     * 사용자정의오류구분(info, warnning, error)의 구분과 에러메세지를 설정한다.
     * 
     * @author 최현수
     * @throws Exception 
     * @since 2012.08.08
     */
    public BizException(final String type, final String message) throws Exception
    {
        messageType = type;
        messageCode = message;
        errorMessage = message;

        JsQueryEngine engine = JsQueryEngine.getInstance();
        engine.setException(this);
    }
    
    /**
     * 사용자정의 오류를 설정한다. <BR>
     * 사용자정의오류구분(info, warnning, error)의 구분과 message-language.properties 에 정의된코드를 이용해서<BR>
     * Object[] parameter 의 값을 이용해서 완성된 에러메세지를 만들어 클라이언트에 리턴한다.  
     * 
     * @author 최현수
     * @throws Exception 
     * @since 2012.08.08
     */
    public BizException(String type, String code, Object ... params ) throws Exception
    {
        messageType = type;
        messageCode = code;
        messageParam = new Object[params.length];
        messageParam = params;

        JsQueryEngine engine = JsQueryEngine.getInstance();
        engine.setException(this);
    }
    

    /**
     * 에러에 대한 처리를 할때 메세지의 타입을 리턴한다.(에러/워닝/인포)
     * 
     * @param String lang 사용자 사용 LANGUAGE정보
     * @author 최현수
     * @date 2011.07.27
     */
    public String getMessageType()
    {
        return messageType;
    }

    /**
     * 입력 Language에 해당하는 Properties의 메세지코드로 에러메세지를 완성시켜 그값을 리턴한다.
     * 
     * @author 최현수
     * @date 2011.11.07
     */
    public String getClientMessage(String lang)
    {
        langauge = lang.toLowerCase();
        return getClientMessage();
    }

    /**
     * 에러에 대한 처리를 할때 메세지의 완성본을 만들어서 그 값을 리턴한다. 전체적으로 메세지 종류와 메세지포맷, 파라미터를 보관만 하고 getMessage가 호출되는 시점에서 실제적인 해당 언어별 메세지를 읽고 변수를 치환하고 완성된 메세지를 리턴한다.
     * 
     * @author 최현수
     * @date 2011.07.27
     */
    public String getClientMessage()
    {
        if(messageCode != null)
        {
            try
            {
                final PropertyResourceBundle messageProperties = (PropertyResourceBundle) PropertyResourceBundle.getBundle("message.message-" + langauge.toLowerCase());
                errorMessage = new String(messageProperties.getString(messageCode).getBytes("ISO-8859-1"), "UTF-8");
                if(messageParam != null)
                {
                    for(int i = 0; i < messageParam.length; i++)
                    {
                        errorMessage = errorMessage.replace("{"+i+"}", (String) messageParam[i]);
                    }
                }
            }
            catch (Exception e)
            {
                errorMessage = messageCode;
            }
        }
        return errorMessage;
    }
}
