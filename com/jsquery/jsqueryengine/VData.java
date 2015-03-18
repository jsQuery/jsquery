package com.jsquery.jsqueryengine;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 
 * @author 최현수
 * @date 2011.09.01
 */

public class VData extends java.util.HashMap
{
    private static final long serialVersionUID = -3565070078441180471L;

    public void add(Object key, Object value)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }

        put(key, value);
    }

    public String getString(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }

        try
        {
            return (String)get(key);
        }
        catch(Exception e)
        {
            try
            {
                return get(key)+"";
            }
            catch(Exception e2)
            {
                return "";
            }
        }
    }

    public byte[] getBlob(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }

        try
        {
            return (byte[]) get(key);
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public byte[] getByte(Object key)
    {
        return getBlob(key);
    }

    public boolean getBoolean(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }

        try
        {
            String value = getString(key);

            if("true".equalsIgnoreCase(value))
                return true;
            else if("false".equalsIgnoreCase(value))
                return false;
            else if("1".equalsIgnoreCase(value))
                return true;
            else if("0".equalsIgnoreCase(value))
                return false;
            else if("y".equalsIgnoreCase(value))
                return true;
            else if("n".equalsIgnoreCase(value))
                return false;
            else
                return false;
        }
        catch(Exception e)
        {
            return false;
        }
    }

    public Date getDateTime(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }
        
        return (Date) get(key);
    }

    public Date getDateTime(Object key, String format) throws ParseException
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }
        
        DateFormat formatter = new SimpleDateFormat(format);
        return (Date) formatter.parse(getString(key));
    }

    public Double getDouble(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }
        
        try
        {
            Object value = get(key);

            if(value instanceof Double)
            {
                return (Double) value;
            }
            else
            {
                return Double.parseDouble(getString(key));
            }
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public Float getFloat(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }
        
        try
        {
            Object value = get(key);

            if(value instanceof Float)
            {
                return (Float) value;
            }
            else
            {
                return Float.parseFloat(getString(key));
            }
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public Long getLong(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }
        
        try
        {
            Object value = get(key);

            if(value instanceof Long)
            {
                return (Long) value;
            }
            else
            {
                return Long.parseLong(getString(key));
            }
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public int getInt(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }
        
        try
        {
            Object value = get(key);

            if(value instanceof Integer)
            {
                return (Integer) value;
            }
            else
            {
                return Integer.parseInt(getString(key));
            }
        }
        catch(Exception e)
        {
            return 0;
        }
    }

    public Object getObject(Object key)
    {
        if(key instanceof String)
        {
            key = ((String) key).toUpperCase();
        }
        
        try
        {
            return get(key);
        }
        catch(Exception e)
        {
            return null;
        }
    }

}
