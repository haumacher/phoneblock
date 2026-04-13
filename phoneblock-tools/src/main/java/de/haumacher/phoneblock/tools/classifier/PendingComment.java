/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

public class PendingComment {

	private String id;
	private String phone;
	private String rating;
	private String comment;
	private int up;
	private int down;
	private long created;

	public PendingComment() {}

	public PendingComment(String id, String phone, String rating, String comment, int up, int down, long created) {
		this.id = id;
		this.phone = phone;
		this.rating = rating;
		this.comment = comment;
		this.up = up;
		this.down = down;
		this.created = created;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public String getRating() { return rating; }
	public void setRating(String rating) { this.rating = rating; }
	public String getComment() { return comment; }
	public void setComment(String comment) { this.comment = comment; }
	public int getUp() { return up; }
	public void setUp(int up) { this.up = up; }
	public int getDown() { return down; }
	public void setDown(int down) { this.down = down; }
	public long getCreated() { return created; }
	public void setCreated(long created) { this.created = created; }
}
