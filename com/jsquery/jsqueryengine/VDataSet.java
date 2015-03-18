package com.jsquery.jsqueryengine;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import com.jsquery.jsqueryengine.VData;

/**
 * 
 * @author 최현수
 * @date 2011.09.01
 */

public class VDataSet extends java.util.ArrayList
{
    private static final long serialVersionUID = 6193259997359860962L;

    public static final int UNDEFINED = 0;
    public static final int NULL = 1;
    public static final int STRING = 2;
    public static final int INT = 3;
    public static final int BOOLEAN = 4;
    public static final int LONG = 5;
    public static final int FLOAT = 6;
    public static final int DOUBLE = 7;
    public static final int BIG_DECIMAL = 8;
    public static final int DECIMAL = 8;
    public static final int DATE = 9;
    public static final int TIME = 10;
    public static final int DATE_TIME = 11;
    public static final int BLOB = 12;
    public static final int FILE = 13;
    public static final int INSERT = 1;
    public static final int UPDATE = 2;
    public static final int DELETE = 3;
    public static final int NORMAL = 4;

    public String name = null;
    int rowcount = 0;

    HashMap columInfo  = new HashMap();
    HashMap columIndex = new HashMap();
    ArrayList columNameList = new ArrayList();
    ArrayList rowTypeList = new ArrayList();
    ArrayList columData = new ArrayList();
    
    public VDataSet()
    {
    }
    
    public VDataSet(String name)
    {
        this.name = name;
    }
    
    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return this.name;
    }

    public boolean addColumn(String columID, int dataType)
    {
        columID = columID.toUpperCase();
        
        if( getColumInfo(columID) == null )
        {
            columIndex.put(columID, columNameList.size());

            VColumInfo columinfo = new VColumInfo();
            columinfo.name = columID;
            columinfo.dataType  = dataType;        
            columInfo.put(columID, columinfo);            
            columInfo.put(columNameList.size(), columinfo);            

            if( rowcount == 0 )
                columData.add(new ArrayList());
            else
            {
                ArrayList newColumData = new ArrayList();
                for(int i=0;i<rowcount;i++)
                {
                    newColumData.add(null);
                }
                columData.add(newColumData);
            }
                
            columNameList.add(columID);
            return true;
        }
        else
        {
            return false;
        }
    }
    
    public boolean addColumn(String columID, int dataType, int dataSize)
    {
        columID = columID.toUpperCase();

        if( getColumInfo(columID) == null )
        {
            columIndex.put(columID, columNameList.size());

            VColumInfo columinfo = new VColumInfo();
            columinfo.name = columID;
            columinfo.dataType  = dataType;        
            columinfo.dataSize  = dataSize;        
            columInfo.put(columID, columinfo);
            columInfo.put(columNameList.size(), columinfo);            

            if( rowcount == 0 )
                columData.add(new ArrayList());
            else
            {
                ArrayList newColumData = new ArrayList();
                for(int i=0;i<rowcount;i++)
                {
                    newColumData.add(null);
                }
                columData.add(newColumData);
            }

            columNameList.add(columID);
            return true;
        }
        else
        {
            return false;
        }
    }    

    public VColumInfo getColumInfo(String columnID)
    {
        try
        {
            return (VColumInfo) columInfo.get(columnID.toUpperCase());
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public VColumInfo getColumInfo(int index)
    {
        try
        {
            return (VColumInfo) columInfo.get(index);
        }
        catch(Exception e)
        {
            return null;
        }
    }

    public VColumInfo getColumnInfo(String columnID)
    {
        return getColumInfo(columnID);
    }

    public VColumInfo getColumnInfo(int index)
    {
        return getColumInfo(index);
    }
    
    public String getColumnID(int index)
    {
        return (String)columNameList.get(index);
    }

    public int getColumnCount()
    {
        return columNameList.size();
    }
    
    public ArrayList getColumnList()
    {
        return columNameList;
    }
    
    public void set(int row, String columID, Object value)
    {
        columID = columID.toUpperCase();
        
        if( rowcount <= row )
        {
            return;
        }

        int index = (Integer)columIndex.get(columID);
        ArrayList dataList = (ArrayList)columData.get(index);
        dataList.set(row, value);
    }

    public void set(int row, int index, Object value)
    {
        if( rowcount <= row )
        {
            return;
        }
        
        ArrayList dataList = (ArrayList)columData.get(index);
        dataList.set(row, value);
    }
    
    public VData getVData(int row)
    {
        if( rowcount <= row )
        {
            return null;
        }

        try
        {
            VData rtnval = new VData();
            for(int i=0;i<getColumnCount();i++)
            {
                ArrayList dataList = (ArrayList)columData.get(i);
                String strKey = (String)columNameList.get(i);
                try
                {
                    rtnval.add(strKey.toUpperCase(), dataList.get(row));
                }
                catch(Exception e)
                {
                    rtnval.add(strKey.toUpperCase(), null);
                }
            }
            
            return rtnval;
        }
        catch(Exception e)
        {
            return null;
        }
    }
    
    public int newRow()
    {
        try
        {
            for(int i=0;i<getColumnCount();i++)
            {
                ArrayList dataList = (ArrayList)columData.get(i);
                dataList.add(null);
            }
            
            rowTypeList.add(INSERT);
            ++rowcount;
            return rowcount-1;
        }
        catch(Exception e)
        {
            return -1;
        }
    }

    public int addRow(VData newData)
    {
        try
        {
            for(int i=0;i<getColumnCount();i++)
            {
                ArrayList dataList = (ArrayList)columData.get(i);
                
                try
                {
                    dataList.add(newData.get(columNameList.get(i)));
                }
                catch(Exception e)
                {
                    dataList.add(null);
                }
            }
            
            rowTypeList.add(INSERT);
            ++rowcount;            
            return rowcount-1;
        }
        catch(Exception e)
        {
            return -1;
        }
    }
    
    public int getRowCount()
    {
        return rowcount;
    }
    
    public Object getObject(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )       
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        return dataList.get(row);
    }

    public String getString(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            return (String)dataList.get(row);
        }
        catch(Exception e)
        {
            return null;            
        }
    }

    @SuppressWarnings("null")
	public int getInt(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            return Integer.parseInt((String)dataList.get(row));
        }
        catch(Exception e)
        {
            return (Integer)null;            
        }
    }   
    
    public Double getDouble(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            return Double.parseDouble((String)dataList.get(row));
        }
        catch(Exception e)
        {
            return (Double)null;            
        }
    }   
    
    
    public Float getFloat(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            return Float.parseFloat((String)dataList.get(row));
        }
        catch(Exception e)
        {
            return (Float)null;            
        }
    }    
    
    public Long getLong(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            return Long.parseLong((String)dataList.get(row));
        }
        catch(Exception e)
        {
            return (Long)null;            
        }
    }    

    public Date getDate(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            return (Date)dataList.get(row);
        }
        catch(Exception e)
        {
            return (Date)null;            
        }
    }     

    public Date getDate(int row, Object colum, String format)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            DateFormat formatter = new SimpleDateFormat(format);
            return (Date) formatter.parse((String)dataList.get(row));            
        }
        catch(Exception e)
        {
            return (Date)null;            
        }
    } 
    
    public byte[] getByte(int row, Object colum)
    {
        ArrayList dataList;
        
        if( colum instanceof String )        
            dataList = (ArrayList)columData.get((Integer)columIndex.get(((String) colum).toUpperCase()));
        else
            dataList = (ArrayList)columData.get((Integer)colum);

        try
        {
            return (byte[])dataList.get(row);            
        }
        catch(Exception e)
        {
            return null;            
        }
    }     
    
    public byte[] getBlob(int row, Object colum)
    {
        return getByte(row, colum);
    }        
    
    public boolean clearData()
    {
        try
        {
            for(int i=0;i<getColumnCount();i++)
            {
                ArrayList dataList = (ArrayList)columData.get(i);
                dataList.removeAll(dataList);
            }
            
            columData = new ArrayList();
            rowcount = 0;
            
            return true;
        }
        catch(Exception e)
        {
            return false;
        }        
    }

    /*
    public void clear()
    {
        try
        {
            for(int i=0;i<getColumnCount();i++)
            {
                ArrayList dataList = (ArrayList)columData.get(i);
                dataList.removeAll(dataList);
            }

            columInfo  = new HashMap();
            columIndex = new HashMap();
            columNameList = new ArrayList();
            rowTypeList = new ArrayList();            
            columData = new ArrayList();
            rowcount = 0;

            return;
        }
        catch(Exception e)
        {
            return;
        }        
    }
    */
    
    public void clear()
    {
        try
        {
            columInfo.clear();
            columIndex.clear();
            columNameList.clear();
            rowTypeList.clear();            
            columData.clear();
            
            columInfo = null;
            columIndex = null;
            columNameList = null;
            rowTypeList = null;            
            columData = null;
            
            rowcount = 0;
            return;
        }
        catch(Exception e)
        {
            return;
        }        
    }
    
    public int getRowType(int row)
    {
        return (Integer)rowTypeList.get(row);
    }
    
    public void setRowType(int row, int rowType)
    {
        if( rowcount <= row )
        {
            return;
        }        
        
        if( rowTypeList.size() != rowcount )
        {
            for(int i=rowTypeList.size();i<rowcount;i++)
            {
                rowTypeList.add(null);
            }
        }
        
        rowTypeList.set(row, rowType);
    }
    
    public boolean removeColumn(Object colum)
    {
        int columindex = 0;
        int columCount = getColumnCount();
        
        try
        {
            if( colum instanceof String )
            {
                columindex = (Integer)columIndex.get(colum);
                columInfo.remove(((String) colum).toUpperCase());
                columInfo.remove(columindex);
                columNameList.remove(columindex);
                columData.remove(columindex);
            }
            else
            {
                columindex = (Integer)colum;
                
                String columname = (String)columNameList.get(columindex);
                columInfo.remove(columname.toUpperCase());
                columInfo.remove(columindex);
                columNameList.remove(columindex);
                columData.remove(columindex);
            }
            
            // 삭제된 컬럼이후의 컬럼정보를 재정립한다.            
            if( columindex < columCount )
            {
                ArrayList oldcolumList = new ArrayList();
                for(int i=columindex+1;i<columCount;i++)
                {
                    VColumInfo columinfo = (VColumInfo)getColumInfo(i);
                    oldcolumList.add(columinfo);
                    columInfo.remove(columinfo.getName());
                    columInfo.remove(i);
                }
                
                for(int i=0;i<oldcolumList.size();i++)
                {
                    VColumInfo columinfo = (VColumInfo)oldcolumList.get(i);
                    columInfo.put(columinfo.getName().toLowerCase(), columinfo);
                    columInfo.put(columindex++, columinfo);
                }
            }
            
            return true;
        }
        catch(Exception e)
        {
            return false;
        }
    }
    
    public String toString()
    {
        StringBuffer buff = new StringBuffer();
        
        for(int i=0;i<this.getRowCount();i++)
        {
            buff.append(this.getVData(i).toString());    
        }
        
        return buff.toString();
    }
}
