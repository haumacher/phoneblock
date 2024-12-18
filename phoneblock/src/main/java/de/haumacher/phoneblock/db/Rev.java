package de.haumacher.phoneblock.db;

/**
 * A revision to look up historic information.
 */
public class Rev {

	private int id;
	private long date;
	
	public Rev(long date) {
		this.date = date;
	}
	
	public int getId() {
		return id;
	}
	
	public long getDate() {
		return date;
	}
	
	public void setDate(long date) {
		this.date = date;
	}
	
	public void setId(int id) {
		this.id = id;
	}
}
