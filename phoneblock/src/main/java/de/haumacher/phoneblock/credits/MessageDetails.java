package de.haumacher.phoneblock.credits;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageDetails {
	private static final Logger LOG = LoggerFactory.getLogger(MessageDetails.class);
	
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

	public void dump() {
		LOG.info("=== Received donation ===");
		LOG.info("Sender:  " + sender);
		LOG.info("User:    " + uid);
		LOG.info("Amount:  " + amount);
		LOG.info("Date:    " + date);
		LOG.info("Message: " + msg);
		LOG.info("TX:      " + tx);
	}
}