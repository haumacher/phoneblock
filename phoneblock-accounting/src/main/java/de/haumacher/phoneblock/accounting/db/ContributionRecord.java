/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting.db;

/**
 * Record representing a contribution entry to be stored in the database.
 */
public class ContributionRecord {

	private long id;
	private Long userId;
	private String sender;
	private String tx;
	private int amount;
	private String message;
	private long received;

	/**
	 * Creates a new contribution record.
	 */
	public ContributionRecord() {
		// Default constructor for MyBatis
	}

	/**
	 * Creates a new contribution record with all fields.
	 *
	 * @param userId The user ID who made the contribution (can be null)
	 * @param sender The sender's name
	 * @param tx The transaction identifier (sender + date)
	 * @param amount The amount in cents
	 * @param message The purpose/message field
	 * @param received The timestamp when the contribution was received (Unix milliseconds)
	 */
	public ContributionRecord(Long userId, String sender, String tx, int amount, String message, long received) {
		this.userId = userId;
		this.sender = sender;
		this.tx = tx;
		this.amount = amount;
		this.message = message;
		this.received = received;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getSender() {
		return sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getTx() {
		return tx;
	}

	public void setTx(String tx) {
		this.tx = tx;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getReceived() {
		return received;
	}

	public void setReceived(long received) {
		this.received = received;
	}
}
