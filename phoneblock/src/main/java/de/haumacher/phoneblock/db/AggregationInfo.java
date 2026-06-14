package de.haumacher.phoneblock.db;

/**
 * A row of one of the prefix-aggregation tables ({@code NUMBERS_AGGREGATION_10},
 * {@code NUMBERS_AGGREGATION_100}). The same shape is reused for both, but the
 * meaning of {@link #getCnt()} / {@link #getVotes()} is asymmetric — see those
 * accessors for the per-table interpretation, and
 * {@link DB#computeBlockSpamEvidence(AggregationInfo, AggregationInfo, long)} for how the
 * two are combined.
 */
public class AggregationInfo {

	private String prefix;
	private int cnt;
	private int votes;
	private double rawHeat;
	private double spamEvidence;
	private double legitEvidence;
	private int members;
	private int tens;

	public AggregationInfo(String prefix, int cnt, int votes) {
		this.prefix = prefix;
		this.cnt = cnt;
		this.votes = votes;
	}

	public AggregationInfo(String prefix, int cnt, int votes,
			double heat, double spamEvidence, double legitEvidence) {
		this.prefix = prefix;
		this.cnt = cnt;
		this.votes = votes;
		this.rawHeat = heat;
		this.spamEvidence = spamEvidence;
		this.legitEvidence = legitEvidence;
	}

	/** Constructor for the {@code /100} aggregation selects, which also carry MEMBERS and TENS. */
	public AggregationInfo(String prefix, int cnt, int votes,
			double heat, double spamEvidence, double legitEvidence, int members, int tens) {
		this(prefix, cnt, votes, heat, spamEvidence, legitEvidence);
		this.members = members;
		this.tens = tens;
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
	 *     {@link DB#computeBlockSpamEvidence(AggregationInfo, AggregationInfo, long)} when it consults both aggregation levels.</li>
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

	/** Raw projected-EMA {@code HEAT} (block-level activity, #337). Decay with {@link Ema#decode}. */
	public double getRawHeat() {
		return rawHeat;
	}

	public void setRawHeat(double rawHeat) {
		this.rawHeat = rawHeat;
	}

	/** Raw projected-EMA {@code SPAM_EVIDENCE} (block-level classification, #337). */
	public double getSpamEvidence() {
		return spamEvidence;
	}

	public void setSpamEvidence(double spamEvidence) {
		this.spamEvidence = spamEvidence;
	}

	/** Raw projected-EMA {@code LEGIT_EVIDENCE} (block-level classification, #337). */
	public double getLegitEvidence() {
		return legitEvidence;
	}

	public void setLegitEvidence(double legitEvidence) {
		this.legitEvidence = legitEvidence;
	}

	/** {@code /100} only: total currently-spam numbers in the block (#300 follow-up). */
	public int getMembers() {
		return members;
	}

	public void setMembers(int members) {
		this.members = members;
	}

	/** {@code /100} only: number of /10 sub-blocks with at least {@code SPREAD_TEN_CONTRIB} members. */
	public int getTens() {
		return tens;
	}

	public void setTens(int tens) {
		this.tens = tens;
	}

}
