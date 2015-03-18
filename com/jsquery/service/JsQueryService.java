package com.jsquery.service;

import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jsquery.jsqueryengine.VData;
import com.jsquery.jsqueryengine.VDataSet;
import com.jsquery.jsqueryengine.VDataSetList;

/**
 *
 * 클라이언트 요청에 대한 Service를 구현하기위한 Interface Class로써 Service개발에 필요한 모든 Method를 제공한다.
 *                
 * @author 최현수
 * @since 2012.04.09
 * @version 1.0
 */
public interface JsQueryService 
{
    public void invokeMethod(HttpServletRequest request, HttpServletResponse response, VData inVl, VDataSetList inDl, VData outVl, VDataSetList outDl) throws Exception;
    
    public void addDataSet(VDataSetList outDl, String datasetName, VDataSet outDs) throws Exception;
    public void makeLog(int loglevel, Object logvalue) throws Exception;
    public boolean isNull(String value) throws Exception;
    public void commit() throws Exception;
    public void rollback() throws Exception;
    
	public VDataSet executeQueryForList(String sQueryID, VData inVl) throws Exception;
    public VDataSet executeQueryForList(String sQueryID, VDataSet inDs) throws Exception;
    public VData executeQueryForRowData(String sQueryID, VData inVl) throws Exception;
	public int executeUpdate(String sConnectionName, String sQueryStr, VData inVl) throws Exception;
	public int executeUpdate(String sQueryID, VData inVl) throws Exception;
	public int executeUpdate(String sQueryID, VDataSet inDs) throws Exception;

	public VDataSet select(String queryid, VDataSet inDs) throws Exception;
    public VDataSet select(String queryid, VData inVl) throws Exception;
    public VDataSet select(String queryid) throws Exception;
    public VData selectRow(String queryid, VData inVl) throws Exception;
    public VData selectRow(String queryid, VDataSet inDs) throws Exception;
    public VData selectRow(String queryid) throws Exception;
    public int update(String queryId, VData inVl) throws Exception;
    public int update(String queryId, VDataSet inDs) throws Exception;
    public int update(String queryId) throws Exception;
    public int save(String sSaveQueryID, VDataSet inputDs) throws Exception;
    
    public VDataSet callProcedure(String sQueryID, VDataSet inputDs) throws Exception;    
    public VData callProcedure(String sQueryID, VData inVl) throws Exception;    
    public VDataSet VDataToVDataSet(VData inVl) throws Exception;
    public VData getVData(VDataSet inDs, int nrow) throws Exception;    
    
    public String readFileContents(String path) throws Exception;
    public String parseHtmlTemplate(String path, HashMap input) throws Exception;
}
