package com.jsquery.jsqueryengine;

import java.util.ArrayList;

/**
 * 
 * @author 최현수
 * @date 2011.09.01
 */

public class VDataSetList
{
    ArrayList datasetList = new ArrayList();
    
    public VDataSet get(String name)
    {
        for(int i=0;i<datasetList.size();i++)
        {
            VDataSet ds = (VDataSet)datasetList.get(i);
            if( name.equalsIgnoreCase(ds.getName()) )
            {
                return ds;
            }
        }
        return null;
    }

    public VDataSet get(int index)
    {
        if( datasetList.size() <= index )
        {
            return null;            
        }
        
        return (VDataSet)datasetList.get(index);
    }
    
    public void add(VDataSet vds)
    {
        datasetList.add(vds);
    }
    
    public int size()
    {
        return datasetList.size();
    }
}
