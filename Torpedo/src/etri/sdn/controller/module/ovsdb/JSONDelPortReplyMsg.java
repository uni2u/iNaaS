package etri.sdn.controller.module.ovsdb;

import java.util.ArrayList;

public class JSONDelPortReplyMsg {

	private int id;
	private Object error;
	private ArrayList<Object> result;
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public Object getError() {
		return error;
	}
	
	public void setError(Object error) {
		this.error = error;
	}
	
	public ArrayList<Object> getResult() {
		return result;
	}
	
	public void setResult(ArrayList<Object> result) {
		this.result = result;
	}
}
