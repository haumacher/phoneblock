/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import org.apache.ibatis.annotations.Update;

/**
 * One-time SQL statements run only from the schema-migration hooks in
 * {@link DB#setupSchema}.
 *
 * <p>These statements reconstruct or drop columns exactly once when a database
 * is upgraded across the relevant version boundary. They are deliberately kept
 * out of the live data-access mappers ({@link SpamReports} etc.) so the
 * everyday query surface is not polluted with migration-only code. The
 * computation stays in SQL (the EMA projection is expressible with
 * {@code EXP()}), and the constants from {@link Ema} / {@link Signals} are
 * still passed as bind parameters so the migration and the forward path share
 * one source of truth.</p>
 */
public interface MigrationStatements {

	/**
	 * Backfill the confidence-model EMA columns from the cumulative counters
	 * (epic #300 / migration 29). Runs once at migration time.
	 *
	 * <p>Treats all existing events as if they happened at
	 * {@code max(LASTPING, UPDATED)} for the row, then projects to the EMA
	 * reference epoch {@code t0}. This is intentionally on the recent end of
	 * the plausible range: earlier would over-decay, later is impossible.
	 * A row that went silent a year ago therefore decays the whole history
	 * by a year — exactly what the Heat-based archiver (#335) wants to see.</p>
	 *
	 * <p>Bind parameters carry the constants from {@link Ema} and
	 * {@link Signals} so changes there stay in one place rather than being
	 * scattered through SQL literals.</p>
	 */
	// The CAST(... AS DOUBLE PRECISION) around each EXP() numerator is essential:
	// without it H2 infers the bind parameters as BIGINT (the type of the
	// LASTPING/UPDATED columns they are combined with) and computes
	// (eventTime - t0) / tau as integer division. For the classification axis
	// tau (~125 d in millis) dwarfs the numerator of any recently active row,
	// so the quotient truncates to 0 and EXP(0) = 1 — the projection silently
	// vanishes and the column ends up equal to the bare vote count. The forward
	// path is unaffected because it computes EXP in Java (Ema.increment).
	@Update("""
			update NUMBERS set
				HEAT = ((DOWN_VOTES + UP_VOTES) * #{voteHeatW}
				        + CALLS * #{callHeatW}
				        + SEARCHES * #{searchHeatW})
				     * EXP(CAST(GREATEST(LASTPING, UPDATED) - #{t0Millis} AS DOUBLE PRECISION) / #{tauHeatMillis}),
				SPAM_EVIDENCE = (DOWN_VOTES * #{voteEvidenceW} + CALLS * #{callEvidenceW})
				              * EXP(CAST(GREATEST(LASTPING, UPDATED) - #{t0Millis} AS DOUBLE PRECISION) / #{tauClassMillis}),
				LEGIT_EVIDENCE = UP_VOTES * #{voteEvidenceW}
				               * EXP(CAST(GREATEST(LASTPING, UPDATED) - #{t0Millis} AS DOUBLE PRECISION) / #{tauClassMillis})
			where GREATEST(LASTPING, UPDATED) > 0
			  and (DOWN_VOTES > 0 or UP_VOTES > 0 or CALLS > 0 or SEARCHES > 0)
			""")
	int backfillNumbersEmas(double t0Millis, double tauHeatMillis, double tauClassMillis,
		double voteHeatW, double voteEvidenceW, double callHeatW, double callEvidenceW, double searchHeatW);

	/**
	 * Backfill the /10 aggregation EMAs as the sum of the per-number EMAs in
	 * the block (#337). Matches the forward path semantic: every event adds
	 * the same projected value to the number, /10 and /100 rows.
	 *
	 * <p>Must run <em>after</em> {@link #backfillNumbersEmas}.</p>
	 */
	@Update("""
			update NUMBERS_AGGREGATION_10 a set
				HEAT = COALESCE((select sum(n.HEAT) from NUMBERS n
				                 where n.PHONE > a.PREFIX
				                   and n.PHONE < concat(a.PREFIX, 'Z')
				                   and length(n.PHONE) = length(a.PREFIX) + 1), 0),
				SPAM_EVIDENCE = COALESCE((select sum(n.SPAM_EVIDENCE) from NUMBERS n
				                          where n.PHONE > a.PREFIX
				                            and n.PHONE < concat(a.PREFIX, 'Z')
				                            and length(n.PHONE) = length(a.PREFIX) + 1), 0),
				LEGIT_EVIDENCE = COALESCE((select sum(n.LEGIT_EVIDENCE) from NUMBERS n
				                           where n.PHONE > a.PREFIX
				                             and n.PHONE < concat(a.PREFIX, 'Z')
				                             and length(n.PHONE) = length(a.PREFIX) + 1), 0)
			""")
	int backfillAggregation10Emas();

	/**
	 * Backfill the /100 aggregation EMAs as the sum of the per-number EMAs in
	 * the block (#337). Must run after {@link #backfillNumbersEmas}.
	 */
	@Update("""
			update NUMBERS_AGGREGATION_100 a set
				HEAT = COALESCE((select sum(n.HEAT) from NUMBERS n
				                 where n.PHONE > a.PREFIX
				                   and n.PHONE < concat(a.PREFIX, 'Z')
				                   and length(n.PHONE) = length(a.PREFIX) + 2), 0),
				SPAM_EVIDENCE = COALESCE((select sum(n.SPAM_EVIDENCE) from NUMBERS n
				                          where n.PHONE > a.PREFIX
				                            and n.PHONE < concat(a.PREFIX, 'Z')
				                            and length(n.PHONE) = length(a.PREFIX) + 2), 0),
				LEGIT_EVIDENCE = COALESCE((select sum(n.LEGIT_EVIDENCE) from NUMBERS n
				                           where n.PHONE > a.PREFIX
				                             and n.PHONE < concat(a.PREFIX, 'Z')
				                             and length(n.PHONE) = length(a.PREFIX) + 2), 0)
			""")
	int backfillAggregation100Emas();

	/**
	 * Backfill {@code NUMBERS_LOCALE.HEAT} from the cumulative per-dial counters
	 * (epic #300 / migration 30). Each row's events are projected to the EMA
	 * reference epoch as if they all happened at {@code LASTACCESS}.
	 *
	 * <p>Note: pre-existing rows only carry CALLS from the answerbot path;
	 * the dominant call-report signal was not feeding {@code NUMBERS_LOCALE}
	 * until #340 wired it in. The backfill therefore mostly reflects votes
	 * and searches — fresh call-report activity is what populates the Heat
	 * column going forward.</p>
	 */
	@Update("""
			update NUMBERS_LOCALE set
				HEAT = (VOTES * #{voteHeatW}
				        + CALLS * #{callHeatW}
				        + SEARCHES * #{searchHeatW})
				     * EXP(CAST(LASTACCESS - #{t0Millis} AS DOUBLE PRECISION) / #{tauHeatMillis})
			where LASTACCESS > 0
			  and (VOTES > 0 or CALLS > 0 or SEARCHES > 0)
			""")
	int backfillNumbersLocaleHeat(double t0Millis, double tauHeatMillis,
		double voteHeatW, double callHeatW, double searchHeatW);

	/**
	 * Backfill {@code NUMBERS_LOCALE.SPAM_EVIDENCE} from the per-region
	 * {@code VOTES} counter (#342 / migration 31). Per-region votes feed only
	 * the spam side of the classification axis — there is no per-region
	 * up-vote counter — so the projection uses the direct-vote evidence
	 * weight as the per-event contribution, with {@code LASTACCESS} as the
	 * assumed event time. Mirrors {@link #backfillNumbersLocaleHeat} for the
	 * classification axis.
	 */
	@Update("""
			update NUMBERS_LOCALE set
				SPAM_EVIDENCE = (VOTES * #{voteEvidenceW} + CALLS * #{callEvidenceW})
				              * EXP(CAST(LASTACCESS - #{t0Millis} AS DOUBLE PRECISION) / #{tauClassMillis})
			where LASTACCESS > 0 and (VOTES > 0 or CALLS > 0)
			""")
	int backfillNumbersLocaleSpamEvidence(double t0Millis, double tauClassMillis,
		double voteEvidenceW, double callEvidenceW);

	/**
	 * Backfill {@code NUMBERS.PUBLISHED_SPAM_EVIDENCE} (#342 / migration 31).
	 * No real snapshot history exists at migration time; we seed with the
	 * current {@code SPAM_EVIDENCE} so the row's "as-published" view starts
	 * equal to the live view. The next scheduled
	 * {@code BlocklistVersionService} sweep will move both forward together.
	 */
	@Update("update NUMBERS set PUBLISHED_SPAM_EVIDENCE = SPAM_EVIDENCE where SPAM_EVIDENCE > 0")
	int backfillPublishedSpamEvidence();

	/**
	 * Drops {@code NUMBERS.PUBLISHED_VOTES} after the snapshot has been
	 * seeded into {@code PUBLISHED_SPAM_EVIDENCE} (#342).
	 */
	@Update("ALTER TABLE NUMBERS DROP COLUMN PUBLISHED_VOTES")
	void dropNumbersPublishedVotes();

	/**
	 * Backfill {@code NUMBERS.PUBLISHED_LEGIT_EVIDENCE} (#342 / migration 32).
	 * No snapshot history exists at migration time; seed with the current
	 * {@code LEGIT_EVIDENCE} so the row's "as-published" view starts equal to
	 * the live view, same as the {@code PUBLISHED_SPAM_EVIDENCE} backfill in
	 * migration 31. The next scheduled sweep moves both forward together.
	 */
	@Update("update NUMBERS set PUBLISHED_LEGIT_EVIDENCE = LEGIT_EVIDENCE where LEGIT_EVIDENCE > 0")
	int backfillPublishedLegitEvidence();

	/** Drops the obsolete PENDING_UPDATE column (#342 / migration 32). */
	@Update("ALTER TABLE NUMBERS DROP COLUMN PENDING_UPDATE")
	void dropNumbersPendingUpdate();

}
