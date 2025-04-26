package de.haumacher.phoneblock.credits;

import java.util.Date;

public class MessageDetails {
	
	public final String msg;
	public final String tx;
	public final int amount;
	public final Date date;
	public final String sender;
	public final String uid;

	public MessageDetails(String msg, String tx, int amount, Date date, String sender, String uid) {
		this.msg = msg;
		this.tx = tx;
		this.amount = amount;
		this.date = date;
		this.sender = sender;
		this.uid = uid;
	}

}