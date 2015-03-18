package com.jsquery.jsqueryengine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 
 * @author 최현수
 * @date 2011.09.01
 */

public class JsQueryUtil
{
    private static final String           CONFIG_RESOURCE = "spring.context";
    private static PropertyResourceBundle m_properties;

    /**
     * Property File로부터 특정 Key값을 읽어들임<br>
     * 
     * @param key 찾고자하는 Key
     * @return 찾고자하는 Key에 대응되는 값
     */
    public static String getProperty(String key) throws MissingResourceException
    {
        Object obj = null;
        if( m_properties == null )
        {
            m_properties = readResources(CONFIG_RESOURCE);
        }
        
        try
        {
            obj = m_properties.getObject(key);
            if( obj == null )
            {
                System.out.println("Property '" + key + "' Not Defined");
                throw new MissingResourceException("No Define", CONFIG_RESOURCE, key);
            }
            return obj.toString();
        }
        catch(Exception e)
        {
            return "";
        }        
    }
    
    /**
     * Properties 의 모든값을 HASHMAP 으로 리턴을 한다. 
     * 
     * @param key 찾고자하는 Key
     * @return 찾고자하는 Key에 대응되는 값
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static HashMap getProperties() throws Exception
    {
        HashMap rtnval = new HashMap();
        
        Iterator<?> iterator = m_properties.keySet().iterator();
        while(iterator.hasNext())
        {
            String key = (String) iterator.next();
            rtnval.put(key, m_properties.getString(key));
        }            
        return rtnval;
    }    

    /**
     * Property File을 찾는 함수 <br>
     * 
     * @param propertiesName 찾고자하는 Property File 이름
     * @return 찾은 PropertyResourceBuldle 값
     */
    public static PropertyResourceBundle readResources(String propertiesName)
    {
        try
        {
            PropertyResourceBundle m_properties = (PropertyResourceBundle) PropertyResourceBundle.getBundle(propertiesName);
            return m_properties;
        }
        catch( MissingResourceException e )
        {
            System.out.println("Cannot find properties file : " + propertiesName + ".properties");
            throw new RuntimeException("Fatal Error : resource bundle not found : " + propertiesName);
        }
    }

    /**
     * 등록 불가한 파일의 확장자를 체크함.
     * 
     * @param strExt 확장자 명
     * @return true/false
     */
    public static boolean checkFileExt(String strExt)
    {
        if( strExt == null || strExt.length() == 0 ) return true;

        String[] sFilter = JsQueryUtil.getProperty("GS_FTP_FILE_EXT").split("/");
        int i_cnt = sFilter.length;

        if( i_cnt == 0 ) return true;

        for( int ii = 0; ii < i_cnt; ii++ )
        {
            if( strExt.equalsIgnoreCase(sFilter[ii]) ) return false;
        }

        return true;
    }

    public static boolean stricmp(String str1, String str2) throws Exception
    {
        if( isNull(str1) && isNull(str2) ) // 둘다널이면 같은거고
        return true;

        if( isNull(str1) && !isNull(str2) ) // 하나만널이고 다른건 널이 아니면 틀린것이고
        return false;

        if( !isNull(str1) && isNull(str2) ) // 하나만널이고 다른건 널이 아니면 틀린것이고
        return false;

        if( str1.toUpperCase().equals(str2.toUpperCase()) ) return true;
        else
            return false;
    }

    public static boolean isNull(String value)
    {
        if( value == null ) return true;

        try
        {
            if( value.length() > 0 ) return false;
            else
                return true;
        }
        catch( NullPointerException e )
        {
            return false;
        }
    }

    /**
     * session Util - Spring에서 제공하는 RequestContextHolder 를 이용하여 request 객체를 service까지 전달하지 않고 사용할 수 있게 해줌
     * 
     */

    /**
     * attribute 값을 가져 오기 위한 method
     * 
     * @param String attribute key name
     * @return Object attribute obj
     */
    public static Object getSessionAttribute(String name) throws Exception
    {
        return (Object) RequestContextHolder.getRequestAttributes().getAttribute(name, RequestAttributes.SCOPE_SESSION);
    }

    /**
     * attribute 설정 method
     * 
     * @param String attribute key name
     * @param Object attribute obj
     * @return void
     */
    public static void setSessionAttribute(String name, Object object) throws Exception
    {
        RequestContextHolder.getRequestAttributes().setAttribute(name, object, RequestAttributes.SCOPE_SESSION);
    }

    /**
     * 설정한 attribute 삭제
     * 
     * @param String attribute key name
     * @return void
     */
    public static void removeSessionAttribute(String name) throws Exception
    {
        RequestContextHolder.getRequestAttributes().removeAttribute(name, RequestAttributes.SCOPE_SESSION);
    }

    /**
     * session id
     * 
     * @param void
     * @return String SessionId 값
     */
    public static String getSessionId() throws Exception
    {
        return RequestContextHolder.getRequestAttributes().getSessionId();
    }      
    
    /**
     * HttpServletRequest를 이용한 getRemoteAddr()가 원하는 실제 클라이언트의 IP를 리턴해주지 못하는 현사이
     * 발생하고 이러한 현상때문에 EP_TRAY정보에서 사용자정보를 가지고 올때 IP가 틀리다는 오동작을 초래하고 이를 궁극적으로 박기위함
     * 
     * @param String strSource
     * @param String strFrom
     * @param String strTo
     * @return replacec처리문장.
     * @author naver 검색..
     * @date 2011.11.09
     */
    public static String getClientIP(HttpServletRequest request)
    {
        String s_ip = request.getHeader("Proxy-Client-IP");
        if(s_ip == null)
        {
            s_ip = request.getHeader("WL-Proxy-Client-IP");
            if(s_ip == null)
            {
                s_ip = request.getHeader("X-Forwared-For");
                if(s_ip == null)
                {
                    s_ip = request.getRemoteAddr();
                }
            }
        }

        return s_ip;
    }    
}
