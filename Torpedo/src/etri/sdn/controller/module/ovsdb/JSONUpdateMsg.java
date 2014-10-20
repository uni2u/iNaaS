package etri.sdn.controller.module.ovsdb;

import java.util.ArrayList;

import etri.sdn.controller.module.ovsdb.JSONShowReplyMsg.ShowResult;

public class JSONUpdateMsg {

	//update message has "id", "method" and "params"
	private int id;
	private String method;
	private ArrayList<ShowResult> params;
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getMethod() {
		return method;
	}
	
	public void setMethod(String method) {
		this.method = method;
	}
	
	public ArrayList<ShowResult> getParams() {
		return params;
	}
	
	public void setParams(ArrayList<ShowResult> params) {
		this.params = params;
	}
}
