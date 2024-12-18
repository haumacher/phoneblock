package de.haumacher.phoneblock.db;

/**
 * Information about number with same prefix.
 */
public class AggregationInfo {
	
	private String prefix;
	private int cnt;
	private int votes;
	
	public AggregationInfo(String prefix, int cnt, int votes) {
		this.prefix = prefix;
		this.cnt = cnt;
		this.votes = votes;
	}

	/**
	 * The prefix of the phone number.
	 */
	public String getPrefix() {
		return prefix;
	}

	/**
	 * @see #getPrefix()
	 */
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * The count of phone numbers with the same {@link #getPrefix()}.
	 */
	public int getCnt() {
		return cnt;
	}

	/**
	 * @see #getCnt()
	 */
	public void setCnt(int cnt) {
		this.cnt = cnt;
	}

	/**
	 * The total number of votes against all phone numbers with the same {@link #getPrefix()}.
	 */
	public int getVotes() {
		return votes;
	}

	/**
	 * @see #getVotes()
	 */
	public void setVotes(int votes) {
		this.votes = votes;
	}
	
}
