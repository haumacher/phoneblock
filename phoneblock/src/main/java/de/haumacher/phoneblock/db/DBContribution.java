package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.settings.Contribution;

public class DBContribution extends Contribution {
	
	public DBContribution(long id, Long userId, String sender, String tx, int amount, String message, long received, boolean ack) {
		setId(id);
		setUserId(userId);
		setSender(sender);
		setTx(tx);
		setAmount(amount);
		setMessage(message);
		setReceived(received);
		setAcknowledged(ack);
	}

}
