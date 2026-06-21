/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Result of resolving a personalization entry by SHA1 hash.
 */
public class DBPersonalization {

	private String phone;
	private boolean blocked;
	private long created;
	private long lastActivity;

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}

	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	/**
	 * Time of the user's last spam/legit activity on this number (epoch millis). The user's
	 * capped contribution to the number's evidence is {@code Ema.increment(1, lastActivity)};
	 * see the per-user evidence cap.
	 */
	public long getLastActivity() {
		return lastActivity;
	}

	public void setLastActivity(long lastActivity) {
		this.lastActivity = lastActivity;
	}

}
