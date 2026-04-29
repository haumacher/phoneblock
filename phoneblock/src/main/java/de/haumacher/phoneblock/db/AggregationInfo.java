package de.haumacher.phoneblock.db;

/**
 * A row of one of the prefix-aggregation tables ({@code NUMBERS_AGGREGATION_10},
 * {@code NUMBERS_AGGREGATION_100}). The same shape is reused for both, but the
 * meaning of {@link #getCnt()} / {@link #getVotes()} is asymmetric — see those
 * accessors for the per-table interpretation, and
 * {@link DB#computeWildcardVotes(AggregationInfo, AggregationInfo)} for how the
 * two are combined.
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
	 * Per-table interpretation:
	 * <ul>
	 * <li>{@code NUMBERS_AGGREGATION_10}: number of distinct phone numbers reported in the
	 *     10-block identified by {@link #getPrefix()}.</li>
	 * <li>{@code NUMBERS_AGGREGATION_100}: number of 10-sub-blocks within the 100-block
	 *     that have crossed {@link DB#MIN_AGGREGATE_10}. Promotion (and demotion) happens
	 *     in {@code DB.updateAggregation10}.</li>
	 * </ul>
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
	 * Per-table interpretation:
	 * <ul>
	 * <li>{@code NUMBERS_AGGREGATION_10}: sum of votes across all phone numbers reported in
	 *     this 10-block.</li>
	 * <li>{@code NUMBERS_AGGREGATION_100}: sum of votes across only those 10-sub-blocks
	 *     that have crossed {@link DB#MIN_AGGREGATE_10}. An unqualified sub-block's votes
	 *     are <em>not</em> contained here — they are added separately by
	 *     {@link DB#computeWildcardVotes} when it consults both aggregation levels.</li>
	 * </ul>
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
