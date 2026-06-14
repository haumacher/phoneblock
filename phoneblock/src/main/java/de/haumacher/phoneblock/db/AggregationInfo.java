package de.haumacher.phoneblock.db;

/**
 * A row of one of the prefix-aggregation tables ({@code NUMBERS_AGGREGATION_10},
 * {@code NUMBERS_AGGREGATION_100}). The same shape is reused for both. A row exists only for a
 * block that currently qualifies as spam (#300 follow-up); {@link #getCnt()} is its current spam
 * member count. The block-spam decision is driven by
 * {@link DB#qualifyingSpamBlock(AggregationInfo, AggregationInfo)} and the decoded evidence.
 */
public class AggregationInfo {

	private String prefix;
	private int cnt;
	private double rawHeat;
	private double spamEvidence;
	private double legitEvidence;

	public AggregationInfo(String prefix, int cnt) {
		this.prefix = prefix;
		this.cnt = cnt;
	}

	public AggregationInfo(String prefix, int cnt,
			double heat, double spamEvidence, double legitEvidence) {
		this.prefix = prefix;
		this.cnt = cnt;
		this.rawHeat = heat;
		this.spamEvidence = spamEvidence;
		this.legitEvidence = legitEvidence;
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
	 * Current spam member count of the block (#300 follow-up):
	 * <ul>
	 * <li>{@code NUMBERS_AGGREGATION_10}: distinct currently-spam numbers in the 10-block.</li>
	 * <li>{@code NUMBERS_AGGREGATION_100}: total currently-spam numbers in the 100-block (mirrors
	 *     the {@code MEMBERS} column). A present row means the block qualifies as spam.</li>
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

}
