/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import de.haumacher.phoneblock.app.api.model.Rating;

/**
 * Interface for the spam report table.
 */
public interface SpamReports {
	
	@Select("select max(UPDATED) from NUMBERS")
	Long getLastUpdate();
	
	@Insert("""
			insert into NUMBERS (PHONE, SHA1, VOTES, DOWN_VOTES, UP_VOTES, UPDATED, ADDED, LASTPING, HEAT, SPAM_EVIDENCE, LEGIT_EVIDENCE)
			values (#{phone}, #{hash}, #{votes}, CASEWHEN (#{votes} > 0, #{votes}, 0), CASEWHEN (#{votes} < 0, 0 - #{votes}, 0), #{now}, #{now}, #{now}, #{heatInc}, #{spamEvidenceInc}, #{legitEvidenceInc})
			""")
	void addReport(String phone, byte[] hash, int votes, long now,
		double heatInc, double spamEvidenceInc, double legitEvidenceInc);
	
	@Select("""
			select PHONE from NUMBERS where SHA1=#{hash}
			""")
	String resolvePhoneHash(byte[] hash);
	
	@Update("""
			update NUMBERS set
				VOTES = VOTES + #{delta},
				DOWN_VOTES = DOWN_VOTES + CASEWHEN(#{delta} > 0, #{delta}, 0),
				UP_VOTES = UP_VOTES + CASEWHEN(#{delta} < 0, 0 - #{delta}, 0),
				UPDATED = GREATEST(UPDATED, #{now}),
				LASTPING = GREATEST(LASTPING, #{now}),
				HEAT = HEAT + #{heatInc},
				SPAM_EVIDENCE = SPAM_EVIDENCE + #{spamEvidenceInc},
				LEGIT_EVIDENCE = LEGIT_EVIDENCE + #{legitEvidenceInc}
			where PHONE = #{phone}
			""")
	int addVote(String phone, int delta, long now,
		double heatInc, double spamEvidenceInc, double legitEvidenceInc);

	/**
	 * Stores the SHA1 hash, but only while the number is spam-visible (#300 privacy guard).
	 *
	 * <p>The SHA1 column is effectively a reverse-lookup (rainbow) table, so it must
	 * only exist for numbers that are classified as spam — i.e. while
	 * {@code SPAM_EVIDENCE > LEGIT_EVIDENCE}. (The privacy-aware hash-prefix lookup
	 * {@code getPhoneInfosByHashPrefix} applies a stricter "displayed votes ≥ 1" cut on
	 * top, so a handful of barely-spam numbers may retain a hash without being returned —
	 * harmless, they are still spam.) The guard is in the {@code WHERE} clause (not a
	 * {@code CASEWHEN} over a NULL branch, which confuses H2's type inference for the
	 * {@code byte[]} parameter). Pair with {@link #clearPhoneHashIfLegit} after every event
	 * that changed the evidence columns.</p>
	 */
	@Update("update NUMBERS set SHA1 = #{hash} where PHONE = #{phone} and SPAM_EVIDENCE > LEGIT_EVIDENCE")
	void setPhoneHashIfSpam(String phone, byte[] hash);

	/**
	 * Clears the SHA1 hash once the classification has decayed below legitimate (#300).
	 * Counterpart to {@link #setPhoneHashIfSpam}; together they keep the hash in lock-step
	 * with spam visibility regardless of which side the latest evidence fell on.
	 */
	@Update("update NUMBERS set SHA1 = null where PHONE = #{phone} and SPAM_EVIDENCE <= LEGIT_EVIDENCE")
	void clearPhoneHashIfLegit(String phone);
	
	@Insert("""
			insert into NUMBERS_LOCALE (PHONE, DIAL, SEARCHES, CALLS, VOTES, HEAT, SPAM_EVIDENCE, LASTACCESS)
			values (#{phone}, #{dialPrefix}, #{searches}, #{calls}, #{votes}, #{heatInc}, #{spamEvidenceInc}, #{now})
			""")
	int insertNumberLocalization(String phone, String dialPrefix, int searches, int calls, int votes,
		double heatInc, double spamEvidenceInc, long now);

	@Update("""
			update NUMBERS_LOCALE set
				SEARCHES = SEARCHES + #{searches},
				CALLS = CALLS + #{calls},
				VOTES = VOTES + #{votes},
				HEAT = HEAT + #{heatInc},
				SPAM_EVIDENCE = SPAM_EVIDENCE + #{spamEvidenceInc},
				LASTACCESS = #{now}
			where PHONE = #{phone} and DIAL = #{dialPrefix}
			""")
	int updateNumberLocalization(String phone, String dialPrefix, int searches, int calls, int votes,
		double heatInc, double spamEvidenceInc, long now);
	
	@Update("update NUMBERS_AGGREGATION_10 set CNT = CNT + #{deltaCnt}, VOTES = VOTES + #{deltaVotes} where PREFIX = #{prefix}")
	int updateAggregation10(String prefix, int deltaCnt, int deltaVotes);

	@Update("update NUMBERS_AGGREGATION_100 set CNT = CNT + #{deltaCnt}, VOTES = VOTES + #{deltaVotes} where PREFIX = #{prefix}")
	int updateAggregation100(String prefix, int deltaCnt, int deltaVotes);

	/**
	 * Add block-level EMA increments to a {@code /10} aggregation row (#337).
	 *
	 * <p>Deliberately separate from {@link #updateAggregation10}: the EMAs are
	 * fed flat at every level (number + /10 + /100) regardless of the
	 * cnt/votes-promotion logic, so an unknown number's report can still drive
	 * the block-level signals even when no cnt/votes update applies to its
	 * /10 row (e.g. legitimate votes on brand-new numbers).</p>
	 */
	@Update("""
			update NUMBERS_AGGREGATION_10 set
				HEAT = HEAT + #{heatInc},
				SPAM_EVIDENCE = SPAM_EVIDENCE + #{spamEvidenceInc},
				LEGIT_EVIDENCE = LEGIT_EVIDENCE + #{legitEvidenceInc}
			where PREFIX = #{prefix}
			""")
	int addAggregation10Emas(String prefix, double heatInc, double spamEvidenceInc, double legitEvidenceInc);

	@Update("""
			update NUMBERS_AGGREGATION_100 set
				HEAT = HEAT + #{heatInc},
				SPAM_EVIDENCE = SPAM_EVIDENCE + #{spamEvidenceInc},
				LEGIT_EVIDENCE = LEGIT_EVIDENCE + #{legitEvidenceInc}
			where PREFIX = #{prefix}
			""")
	int addAggregation100Emas(String prefix, double heatInc, double spamEvidenceInc, double legitEvidenceInc);

	/**
	 * Fresh-row insert that carries only EMA values (no cnt/votes contribution).
	 * Used when an event reaches an aggregation block whose row does not yet
	 * exist — typically a legitimate vote, where the cnt/votes-promotion path
	 * would not create a row at all. The hash column is set so the row is
	 * reachable by the prefix-check range scan, just like rows created via
	 * {@code insertAggregation10WithHash}.
	 */
	@Insert("""
			insert into NUMBERS_AGGREGATION_10 (PREFIX, CNT, VOTES, SHA1, HEAT, SPAM_EVIDENCE, LEGIT_EVIDENCE)
			values (#{prefix}, 0, 0, #{hash}, #{heatInc}, #{spamEvidenceInc}, #{legitEvidenceInc})
			""")
	int insertAggregation10EmasOnly(String prefix, byte[] hash,
		double heatInc, double spamEvidenceInc, double legitEvidenceInc);

	@Insert("""
			insert into NUMBERS_AGGREGATION_100 (PREFIX, CNT, VOTES, SHA1, HEAT, SPAM_EVIDENCE, LEGIT_EVIDENCE)
			values (#{prefix}, 0, 0, #{hash}, #{heatInc}, #{spamEvidenceInc}, #{legitEvidenceInc})
			""")
	int insertAggregation100EmasOnly(String prefix, byte[] hash,
		double heatInc, double spamEvidenceInc, double legitEvidenceInc);

	@Insert("insert into NUMBERS_AGGREGATION_10 (PREFIX, CNT, VOTES) values (#{prefix}, #{cnt}, #{votes})")
	int insertAggregation10(String prefix, int cnt, int votes);

	@Insert("insert into NUMBERS_AGGREGATION_10 (PREFIX, CNT, VOTES, SHA1) values (#{prefix}, #{cnt}, #{votes}, #{hash})")
	int insertAggregation10WithHash(String prefix, int cnt, int votes, byte[] hash);

	@Insert("insert into NUMBERS_AGGREGATION_100 (PREFIX, CNT, VOTES) values (#{prefix}, #{cnt}, #{votes})")
	int insertAggregation100(String prefix, int cnt, int votes);

	@Insert("insert into NUMBERS_AGGREGATION_100 (PREFIX, CNT, VOTES, SHA1) values (#{prefix}, #{cnt}, #{votes}, #{hash})")
	int insertAggregation100WithHash(String prefix, int cnt, int votes, byte[] hash);

	@Select("select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_10 where PREFIX = #{prefix}")
	AggregationInfo getAggregation10(String prefix);

	@Select("select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_10 where SHA1 = #{hash}")
	AggregationInfo getAggregation10ByHash(byte[] hash);

	@Select("select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_100 where SHA1 = #{hash}")
	AggregationInfo getAggregation100ByHash(byte[] hash);

	@Select("""
			select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_10
			where SHA1 >= #{low} and SHA1 < #{high} and CNT >= #{minCnt}
			""")
	List<AggregationInfo> getAggregation10ByHashPrefix(byte[] low, byte[] high, int minCnt);

	@Select("""
			select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_100
			where SHA1 >= #{low} and SHA1 < #{high} and CNT >= #{minCnt}
			""")
	List<AggregationInfo> getAggregation100ByHashPrefix(byte[] low, byte[] high, int minCnt);

	@Update("update NUMBERS_AGGREGATION_10 set SHA1 = #{hash} where PREFIX = #{prefix}")
	int updateAggregation10Hash(String prefix, byte[] hash);

	@Update("update NUMBERS_AGGREGATION_100 set SHA1 = #{hash} where PREFIX = #{prefix}")
	int updateAggregation100Hash(String prefix, byte[] hash);
	
	@Select("select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_10")
	List<AggregationInfo> getAllAggregation10();

	@Select("select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_100 where PREFIX = #{prefix}")
	AggregationInfo getAggregation100(String prefix);

	@Select("select PREFIX, CNT, VOTES, HEAT as heat, SPAM_EVIDENCE as spamEvidence, LEGIT_EVIDENCE as legitEvidence from NUMBERS_AGGREGATION_100")
	List<AggregationInfo> getAllAggregation100();
	
	@Select("""
			SELECT max(s.LASTPING) 
			FROM NUMBERS s
			WHERE s.PHONE > #{prefix}
			AND s.PHONE < concat(#{prefix}, 'Z')
			AND LENGTH(s.PHONE) = #{expectedLength}
			AND s.SPAM_EVIDENCE > s.LEGIT_EVIDENCE
			""")
	Long getLastPingPrefix(String prefix, int expectedLength);

	// #{minRawSpam} = maxRawSpam(1): only list related numbers that still show at least
	// one vote, not every row with a sliver of net evidence that rounds to 0 (#300).
	@Select("""
			SELECT s.PHONE FROM NUMBERS s
			WHERE s.PHONE > #{prefix}
			AND s.PHONE < concat(#{prefix}, 'Z')
			AND LENGTH(s.PHONE) = #{expectedLength}
			AND (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{minRawSpam}
			order by s.PHONE
			""")
	List<String> getRelatedNumbers(String prefix, int expectedLength, double minRawSpam);

	/**
	 * Stamps LASTPING on all spam-positive numbers of a block.
	 *
	 * <p>Only used by the one-time bootstrap of pre-PROPERTIES legacy
	 * databases in {@code DB.setupSchema} (computing the initial aggregate
	 * last-ping per block). The former hot-path caller
	 * {@code pingRelatedNumbers} (#91) was removed: with the confidence
	 * model, a number's blocklist life is its evidence decay — which
	 * LASTPING does not influence — and the mass-spammer-block case is
	 * covered by the aggregation EMAs feeding wildcard blocking (#337).</p>
	 */
	@Update("""
			update NUMBERS s
			set
				s.LASTPING = GREATEST(s.LASTPING, #{now})
			where s.PHONE > #{prefix}
			and s.PHONE < concat(#{prefix}, 'Z')
			and LENGTH(s.PHONE) = #{expectedLength}
			and s.SPAM_EVIDENCE > s.LEGIT_EVIDENCE
			""")
	void sendPing(String prefix, int expectedLength, long now);
	
	@Select("""
			select count(1) from NUMBERS
			where PHONE = #{phone}
			""")
	boolean isKnown(String phone);

	@Select("""
			select VOTES from NUMBERS
			where PHONE = #{phone}
			""")
	Integer getVotes(String phone);

	@Select("SELECT SUM(s.DOWN_VOTES) + SUM(s.UP_VOTES) FROM NUMBERS s")
	Integer getTotalVotes();
	
	@Select("SELECT SUM(s.SEARCHES) FROM NUMBERS s")
	Integer getTotalSearches();
	
	@Update("""
			update NUMBERS s
			set
				CALLS = CALLS + 1,
				s.UPDATED = greatest(s.UPDATED, #{now}),
				s.LASTPING = greatest(s.LASTPING, #{now}),
				s.HEAT = s.HEAT + #{heatInc},
				s.SPAM_EVIDENCE = s.SPAM_EVIDENCE + #{spamEvidenceInc}
			where
				PHONE = #{phone}
			""")
	int recordCall(String phone, long now, double heatInc, double spamEvidenceInc);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
			where UPDATED >= #{after} and (SPAM_EVIDENCE - LEGIT_EVIDENCE) >= #{minRawSpam}
			order by UPDATED desc
			""")
	List<DBNumberInfo> getLatestReports(long after, double minRawSpam);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE from NUMBERS s
			where SPAM_EVIDENCE > LEGIT_EVIDENCE
			order by UPDATED desc
			limit #{limit}
			""")
	List<DBNumberInfo> getAll(int limit);
	
	// Trailing s.HEAT/SPAM_EVIDENCE/LEGIT_EVIDENCE columns engage the 19-argument
	// constructor of DBNumberInfo (confidence model, #334). Selects without those
	// columns continue to bind to the 16-arg constructor with EMAs defaulting to 0.
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
			where s.PHONE = #{phone}
			""")
	DBNumberInfo getPhoneInfo(String phone);

	// USE INDEX (NUMBERS_SHA1_IDX) is essential: H2 has no column histograms,
	// so it estimates the SHA1 range scan pessimistically (~25-50 % of the
	// table) and otherwise picks an inferior index, scanning every
	// active row to filter SHA1 in memory (~60-150 ms per call, 15 k page
	// reads). Forced to NUMBERS_SHA1_IDX the same query touches ~10 rows
	// and 4 page reads. See issue #329.
	// The net-evidence filter uses the projected visibility threshold #{minRawSpam}
	// (= maxRawSpam(1)), not a bare SPAM_EVIDENCE > LEGIT_EVIDENCE: the latter lets
	// through numbers whose decoded net evidence rounds to 0 displayed votes, which
	// would surface as votes=0 entries in /check-prefix (#300). Keeps the result in
	// lock-step with DBNumberInfo.getVotes rounding.
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s USE INDEX (NUMBERS_SHA1_IDX)
			where s.SHA1 >= #{low} and s.SHA1 < #{high} and (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{minRawSpam}
			""")
	List<DBNumberInfo> getPhoneInfosByHashPrefix(byte[] low, byte[] high, double minRawSpam);
	
	@Select("""
			select #{prefix}, max(s.ADDED), max(s.UPDATED), max(s.LASTSEARCH), sum(s.CALLS), sum(s.VOTES), sum(s.LEGITIMATE), sum(s.PING), sum(s.POLL), sum(s.ADVERTISING), sum(s.GAMBLE), sum(s.FRAUD), sum(s.SEARCHES), max(s.LASTPING), sum(s.SPAM_EVIDENCE) as PUBLISHED_SPAM_EVIDENCE, sum(s.LEGIT_EVIDENCE) as PUBLISHED_LEGIT_EVIDENCE
			from NUMBERS s
			where
				s.PHONE > #{prefix}
				and s.PHONE < concat(#{prefix}, 'Z')
				and LENGTH(s.PHONE) = #{expectedLength}
			""")
	DBNumberInfo getPhoneInfoAggregate(String prefix, int expectedLength);
	
	// Skip decayed numbers whose displayed votes have faded to 0: the net
	// decoded evidence rounds to 0 once (SPAM_EVIDENCE - LEGIT_EVIDENCE) drops
	// below #{minRawSpam} = maxRawSpam(1) (the projected "displayed votes >= 1"
	// cutoff, in lock-step with DBNumberInfo.getVotes rounding). Plain
	// SPAM_EVIDENCE > LEGIT_EVIDENCE would still navigate to a number that shows
	// no votes. See #300.
	@Select("""
			select s.PHONE from NUMBERS s
			WHERE s.PHONE > #{phone}
			AND (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{minRawSpam}
			ORDER BY s.PHONE
			LIMIT 1
			""")
	String getNextPhone(String phone, double minRawSpam);

	@Select("""
			select s.PHONE from NUMBERS s
			WHERE s.PHONE < #{phone}
			AND (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{minRawSpam}
			ORDER BY s.PHONE DESC
			LIMIT 1
			""")
	String getPrevPhone(String phone, double minRawSpam);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
			ORDER BY s.VOTES DESC LIMIT #{cnt}
			""")
	List<DBNumberInfo> getTopSpammers(int cnt);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
			ORDER BY s.SEARCHES DESC LIMIT #{cnt}
			""")
	List<DBNumberInfo> getTopSearchesOverall(int cnt);
	
	// The net-evidence visibility filter (SPAM_EVIDENCE - LEGIT_EVIDENCE) is a
	// computed expression, so on its own it forces a full-table scan plus a
	// top-N ADDED sort over every (mostly faded) row. The leading
	// SPAM_EVIDENCE >= #{maxRawSpam} term is redundant for the result —
	// LEGIT_EVIDENCE is never negative, so the net filter already implies it —
	// but it is a plain column range that lets H2 seek the small set of visible
	// candidates through NUMBERS_SPAM_EVIDENCE_IDX and sort only those by ADDED.
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
			WHERE SPAM_EVIDENCE >= #{maxRawSpam} AND (SPAM_EVIDENCE - LEGIT_EVIDENCE) >= #{maxRawSpam} AND ADDED > 0 ORDER BY ADDED DESC LIMIT 10
			""")
	List<DBNumberInfo> getLatestBlocklistEntries(double maxRawSpam);

	/**
	 * Heat-ranked blocklist (#336) — the top {@code limit} active spam numbers
	 * by current activity, for space-constrained clients (Fritz!Box phonebook,
	 * dongle local blocklist).
	 *
	 * <p>The visibility filter {@code SPAM_EVIDENCE >= maxRawSpam} (#342) is
	 * the decay-aware analogue of the old {@code VOTES >= minVotes} cut: the
	 * caller projects the configured {@code minVotes} threshold through
	 * {@link Ema#projectedThreshold} and the comparison happens against the
	 * raw EMA column, so the filter is index-backed and never calls
	 * {@code EXP()} per row. Heat decides the ordering within that set.</p>
	 */
	// Trailing HEAT/SPAM_EVIDENCE/LEGIT_EVIDENCE engage the 19-argument
	// DBNumberInfo constructor (#338) — toBlocklistEntry derives the displayed
	// `votes` from the decoded SPAM_EVIDENCE so blocklist consumers see the
	// same decay-aware semantic as the /api/check responses.
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
			where (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{maxRawSpam}
			order by s.HEAT desc
			limit #{limit}
			""")
	List<DBNumberInfo> getBlocklistByHeat(double maxRawSpam, int limit);

	/**
	 * Dial-aware Heat-ranked blocklist (#340). Same shape as
	 * {@link #getBlocklistByHeat}, but ordered by the region-scoped Heat so the top-N reflects
	 * activity reported from the caller's region rather than a global mix.
	 *
	 * <p>{@code INNER JOIN} on {@code NUMBERS_LOCALE} drops numbers that have
	 * no signal in this dial at all — exactly the intended behaviour for a
	 * regional blocklist. The index {@code NUMBERS_LOCALE_HEAT_IDX} on
	 * {@code (DIAL, HEAT DESC)} backs the order-by; no {@code EXP()} per
	 * row, because every row in a given dial shares the same decay factor.</p>
	 */
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE
			from NUMBERS_LOCALE l
			join NUMBERS s on s.PHONE = l.PHONE
			where l.DIAL = #{dial} and (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{maxRawSpam}
			order by l.HEAT desc
			limit #{limit}
			""")
	List<DBNumberInfo> getBlocklistByDialHeat(String dial, double maxRawSpam, int limit);

	/**
	 * Full blocklist for {@code /api/blocklist} (#342): the published state
	 * — bucket votes frozen at publication — filtered by the server-side
	 * visibility threshold. The block decision data is stable between
	 * sweeps; only the informational lastActivity and rating columns are
	 * joined live (the API makes no caching promise).
	 */
	@Select("""
			select b.PHONE, b.VOTES,
			       coalesce(s.LASTPING, 0) as LASTPING,
			       coalesce(s.LEGITIMATE, 0) as LEGITIMATE,
			       coalesce(s.PING, 0) as PING,
			       coalesce(s.POLL, 0) as POLL,
			       coalesce(s.ADVERTISING, 0) as ADVERTISING,
			       coalesce(s.GAMBLE, 0) as GAMBLE,
			       coalesce(s.FRAUD, 0) as FRAUD
			from BLOCKLIST b
			left join NUMBERS s on s.PHONE = b.PHONE
			where b.VOTES >= #{minVotes}
			order by b.PHONE
			""")
	List<DBBlocklistEntry> getBlocklist(int minVotes);
	
	@Select("""
			select s.PHONE, s.SEARCHES_CURRENT, s.SEARCHES, s.LASTSEARCH from NUMBERS s
			where (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{minRawSpam}
			order by s.SEARCHES_CURRENT desc
			limit #{limit}
			""")
	List<DBSearchInfo> getTopSearchesCurrent(int limit, double minRawSpam);
	
	/**
	 * Retrieves all historic versions for the given number not older than the given revision.
	 * 
	 * <p>
	 * Note: There is no entry for revisions in which the number has not changed from it's previous version.
	 * </p>
	 */
	@Select("""
			select h.RMIN, h.RMAX, h.PHONE, h.CALLS, h.VOTES, h.LEGITIMATE, h.PING, h.POLL, h.ADVERTISING, h.GAMBLE, h.FRAUD, h.SEARCHES from NUMBERS_HISTORY h
			where h.RMAX >= #{revision} and h.PHONE = #{phone} order by h.RMIN
			""")
	List<DBNumberHistory> getSearchHistory(int revision, String phone);

	/**
	 * The newest history entry for the given number that is not newer than the requested revision.
	 */
	@Select("""
			select h.RMIN, h.RMAX, h.PHONE, h.CALLS, h.VOTES, h.LEGITIMATE, h.PING, h.POLL, h.ADVERTISING, h.GAMBLE, h.FRAUD, h.SEARCHES from NUMBERS_HISTORY h
			where h.RMIN <= #{rev} and h.RMAX >= #{rev} and h.PHONE = #{phone}
			""")
	DBNumberHistory getHistoryEntry(String phone, int rev);

	@Select("""
			select h.RMIN, h.RMAX, h.PHONE, h.CALLS, h.VOTES, h.LEGITIMATE, h.PING, h.POLL, h.ADVERTISING, h.GAMBLE, h.FRAUD, h.SEARCHES from NUMBERS_HISTORY h
			where h.RMIN = #{rev}
			""")
	List<DBNumberHistory> getHistoryEntries(int rev);

	/**
	 * Per-revision (≈ per-day) increments of the cumulative {@code CALLS},
	 * {@code VOTES} and {@code SEARCHES} counters since the given revision.
	 *
	 * <p>
	 * For every number that changed in revision {@code h.RMIN} the increment is
	 * its snapshot value minus the value of the immediately preceding snapshot
	 * ({@code p.RMAX = h.RMIN - 1}, NULL for the first appearance). Summing over
	 * all numbers of a revision yields the global daily activity. Revisions in
	 * which no number changed do not appear (effectively never in production).
	 * </p>
	 */
	@Select("""
			select r.CREATED as created,
			       sum(h.CALLS - coalesce(p.CALLS, 0)) as calls,
			       sum(h.VOTES - coalesce(p.VOTES, 0)) as votes,
			       sum(h.SEARCHES - coalesce(p.SEARCHES, 0)) as searches
			from NUMBERS_HISTORY h
			join REVISION r on r.ID = h.RMIN
			left join NUMBERS_HISTORY p on p.PHONE = h.PHONE and p.RMAX = h.RMIN - 1
			where h.RMIN >= #{minRev}
			group by h.RMIN, r.CREATED
			order by h.RMIN
			""")
	List<DailyActivity> getActivityHistory(int minRev);
	
	@Select(
		"""
		<script>
		select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE from NUMBERS s
		where s.PHONE in
		    <foreach item="item" index="index" collection="numbers" open="(" separator="," close=")">
		        #{item}
		    </foreach>
		</script>
		""")
	List<DBNumberInfo> getNumbers(Collection<String> numbers);

	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE from NUMBERS s
			""")
	List<DBNumberInfo> getReports();

	/**
	 * The published blocklist state (#342): bucket votes plus the activity
	 * class of the given region (where the spam reports come from, #340),
	 * both frozen at publication; tombstones excluded. Deterministic between
	 * sweeps — used by the CardDAV pipeline so the address-book ETag does not
	 * flap when the TTL-expired cache regenerates between two releases.
	 */
	@Select("""
			select b.PHONE, b.VOTES, coalesce(bl.HEAT, 0) as HEAT
			from BLOCKLIST b
			left join BLOCKLIST_LOCALE bl on bl.PHONE = b.PHONE and bl.DIAL = #{dial}
			where b.VOTES > 0
			""")
	List<DBBlocklistEntry> getPublishedReports(String dial);
	
	@Select("""
			select PHONE from WHITELIST
			""")
	Set<String> getWhiteList();
	
	@Select("""
			select count(1) from WHITELIST
			where PHONE = #{phone}
			""")
	boolean isWhiteListed(String phone);
	
	@Select("""
			select PHONE from NUMBERS
			where (SPAM_EVIDENCE - LEGIT_EVIDENCE) >= #{maxRawSpam}
			""")
	List<String> getBlockList(double maxRawSpam);

	// 'reported' = shows at least one vote but below the block threshold; 'blocked' = at or
	// above it. The lower bound is #{reportedFloor} = maxRawSpam(1), not a bare
	// SPAM_EVIDENCE > LEGIT_EVIDENCE, so numbers whose net evidence rounds to 0 votes are
	// counted as neither (#300).
	@Select("""
			SELECT CASE WHEN (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) < #{maxRawSpam} THEN 'reported' ELSE 'blocked' END state, COUNT(1) cnt FROM NUMBERS s
			where (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{reportedFloor}
			GROUP BY state
			ORDER BY state
			""")
	List<Statistics> getStatistics(double maxRawSpam, double reportedFloor);

	// Count of currently visible (blocked) numbers. The leading
	// SPAM_EVIDENCE >= #{maxRawSpam} is redundant with the net-evidence filter
	// (LEGIT_EVIDENCE is never negative) but lets H2 count through
	// NUMBERS_SPAM_EVIDENCE_IDX over just the visible set instead of
	// full-scanning NUMBERS — see getLatestBlocklistEntries for the same trick.
	@Select("""
			SELECT COUNT(1) FROM NUMBERS s
			WHERE s.SPAM_EVIDENCE >= #{maxRawSpam} AND (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{maxRawSpam}
			""")
	int getActiveBlocklistCount(double maxRawSpam);

	@Select("SELECT DIAL AS dial, COUNT(1) AS cnt FROM NUMBERS_LOCALE WHERE SPAM_EVIDENCE >= #{maxRawSpam} GROUP BY DIAL ORDER BY cnt DESC")
	List<DailyCount> getBlockedNumbersByCountry(double maxRawSpam);

	@Update("""
			update NUMBERS s
			set
				s.LEGITIMATE = s.LEGITIMATE + casewhen(#{rating}='A_LEGITIMATE', #{delta}, 0),
				s.PING = s.PING + casewhen(#{rating}='C_PING', #{delta}, 0),
				s.POLL = s.POLL + casewhen(#{rating}='D_POLL', #{delta}, 0),
				s.ADVERTISING = s.ADVERTISING + casewhen(#{rating}='E_ADVERTISING', #{delta}, 0),
				s.GAMBLE = s.GAMBLE + casewhen(#{rating}='F_GAMBLE', #{delta}, 0),
				s.FRAUD = s.FRAUD + casewhen(#{rating}='G_FRAUD', #{delta}, 0),
				s.UPDATED = greatest(s.UPDATED, #{now}),
				s.LASTPING = casewhen(#{delta} > 0, greatest(s.LASTPING, #{now}), s.LASTPING)
			where s.PHONE = #{phone}
			""")
	int updateRating(String phone, Rating rating, int delta, long now);

	@Update("""
			update NUMBERS s
			set
				s.SEARCHES = s.SEARCHES + 1,
				s.SEARCHES_CURRENT = s.SEARCHES_CURRENT + 1,
				s.LASTSEARCH = GREATEST(s.LASTSEARCH, #{now}),
				s.LASTPING = GREATEST(s.LASTPING, #{now}),
				s.HEAT = s.HEAT + #{heatInc}
			where s.PHONE=#{phone}
			""")
	int incSearchCount(String phone, long now, double heatInc);

	@Select("""
			<script>
			select s.ID, s.PHONE, s.RATING, s.COMMENT, s.LOCALE, s.SERVICE, s.CREATED, s.UP, s.DOWN, s.USERID
			from COMMENTS s
			where s.PHONE=#{phone} and s.CLASSIFICATION &gt;= 0 and s.LOCALE in
			    <foreach item="item" index="index" collection="langs" open="(" separator="," close=")">
			        #{item}
			    </foreach>
			</script>
			""")
	List<DBUserComment> getComments(String phone, Collection<String> langs);

	@Select("""
			select s.ID, s.PHONE, s.RATING, s.COMMENT, s.LOCALE, s.SERVICE, s.CREATED, s.UP, s.DOWN, s.USERID
			from COMMENTS s
			where s.PHONE=#{phone} and s.CLASSIFICATION >= 0
			""")
	List<DBUserComment> getAnyComments(String phone);

	@Select("""
			<script>
			select s.ID, s.PHONE, s.RATING, s.COMMENT, s.LOCALE, s.SERVICE, s.CREATED, s.UP, s.DOWN, s.USERID
			from COMMENTS s
			where
				s.PHONE &gt; #{prefix}
				and s.PHONE &lt; concat(#{prefix}, 'Z')
				and LENGTH(s.PHONE) = #{expectedLength}
				and s.CLASSIFICATION &gt;= 0
				and s.LOCALE in
			    <foreach item="item" index="index" collection="langs" open="(" separator="," close=")">
			        #{item}
			    </foreach>
			</script>
			""")
	List<DBUserComment> getAllComments(String prefix, int expectedLength, Collection<String> langs);
	
	@Insert("""
			insert into COMMENTS (ID, PHONE, RATING, COMMENT, LOCALE, SERVICE, CREATED, USERID)
			values (#{id}, #{phone}, #{rating}, #{comment}, #{lang}, #{service}, #{created}, #{userId})
			""")
	void addComment(String id, String phone, Rating rating, String comment, String lang, String service, long created, Long userId);

	@Select("select s.ID, s.PHONE, s.RATING, s.COMMENT, s.LOCALE, s.SERVICE, s.CREATED, s.UP, s.DOWN, s.USERID from COMMENTS s where USERID = #{userId} and PHONE = #{phone} limit 1")
	DBUserComment getUserComment(long userId, String phone);

	@Delete("delete from COMMENTS where USERID = #{userId} and PHONE = #{phone}")
	int deleteUserComments(long userId, String phone);

	@Update("update COMMENTS set COMMENT = #{comment} where USERID = #{userId} and PHONE = #{phone}")
	int updateUserComment(long userId, String phone, String comment);

	@Update("update COMMENTS set RATING = #{rating} where USERID = #{userId} and PHONE = #{phone}")
	int updateUserRating(long userId, String phone, Rating rating);

	@Select("""
			<script>
			select s.PHONE, s.COMMENT, s.RATING
			from COMMENTS s
			where s.USERID = #{userId}
			and s.PHONE in
			<foreach item="item" index="index" collection="phones" open="(" separator="," close=")">
				#{item}
			</foreach>
			</script>
			""")
	List<DBPhoneComment> getUserComments(long userId, Collection<String> phones);

	@Update("update COMMENTS s set s.UP = s.UP + #{up}, s.DOWN = s.DOWN + #{down} where s.ID = #{id}")
	int updateCommentVotes(String id, int up, int down);
	
	@Select("select s.LASTMETA from NUMBERS s where s.PHONE=#{phone}")
	Long getLastMetaSearch(String phone);
	
	@Update("update NUMBERS s set s.LASTMETA=#{lastUpdate} where s.PHONE=#{phone}")
	int setLastMetaSearch(String phone, long lastUpdate);

	/**
	 * Atomically inserts or updates the LASTMETA timestamp for a phone number.
	 *
	 * <p>Uses H2's MERGE KEY syntax to avoid race conditions when multiple requests
	 * search for the same number simultaneously. This is atomic and will never fail
	 * with duplicate key violations.</p>
	 *
	 * <p>Note: In a race condition, ADDED may be overwritten by the second request,
	 * but since both requests happen at nearly the same time, this is acceptable.</p>
	 */
	// No SHA1 here: a meta-search placeholder row carries no spam evidence yet, so it must
	// not enter the reverse-lookup table (#300). The hash is populated later by
	// updatePhoneHashVisibility once an actual spam signal arrives.
	@Insert("MERGE INTO NUMBERS (PHONE, ADDED, LASTMETA) KEY (PHONE) VALUES (#{phone}, #{now}, #{now})")
	void mergeLastMetaSearch(String phone, long now);
	
	@Select("select COMMENT from SUMMARY s where s.PHONE = #{phone}")
	String getSummary(String phone);
	
	@Insert("INSERT INTO SUMMARY (PHONE, COMMENT, CREATED) VALUES (#{phone}, #{comment}, #{created})")
	void insertSummary(String phone, String comment, Long created);
	
	@Update("UPDATE SUMMARY SET COMMENT = #{comment}, CREATED = #{created} WHERE PHONE = #{phone}")
	int updateSummary(String phone, String comment, Long created);
	
	@Insert("insert into REVISION (CREATED) values (#{date})")
	@Options(useGeneratedKeys = true, keyColumn = "ID", keyProperty = "id")
	void storeRevision(Rev newRev);
	
	@Select("select max(ID) from REVISION")
	Integer getLastRevision();
	
	@Select("select CREATED from REVISION where id=#{rev}")
	Long getRevisionDate(int rev);

	/**
	 * Creation time of the most recent committed revision, or <code>null</code> if no revision
	 * exists yet.
	 *
	 * <p>
	 * This is the watermark for the next history snapshot. Reading the maximum committed timestamp
	 * (instead of the timestamp of "current revision id minus one") keeps the watermark correct even
	 * when a previous snapshot attempt failed and rolled back: the {@code IDENTITY} sequence behind
	 * {@link #storeRevision(Rev)} advances even on a rolled-back insert, so the preceding id may
	 * point to a revision that was never committed. Deriving the watermark from it would collapse
	 * {@code lastSnapshot} to that gap's date (or to <code>0</code>), turning every following run
	 * into a full-corpus snapshot.
	 * </p>
	 */
	@Select("select max(CREATED) from REVISION")
	Long getLastSnapshotTime();

	@Select("select min(ID) from REVISION")
	int getOldestRevision();

	/**
	 * Upper {@code PHONE} bound (inclusive) of the next batch of active numbers above
	 * {@code fromPhone}, or <code>null</code> if no active number remains. "Active" means
	 * {@code LASTPING > lastSnapshot}.
	 *
	 * <p>
	 * Drives the {@code PHONE}-range batching of the history snapshot so that no single transaction
	 * rewrites the whole table. The {@code PHONE} primary key makes the range scan cheap.
	 * </p>
	 */
	@Select("""
			select max(PHONE) from (
				select s.PHONE from NUMBERS s
				where s.LASTPING > #{lastSnapshot} and s.PHONE > #{fromPhone}
				order by s.PHONE
				limit #{batchSize}
			)
			""")
	String nextHistoryBatchBound(long lastSnapshot, String fromPhone, int batchSize);

	@Update("""
			update NUMBERS_HISTORY
			set
				RMAX = #{rev} - 1
			where
				RMAX = 0x7fffffff and
				PHONE > #{fromPhone} and PHONE <= #{toPhone} and
				PHONE in (select s.PHONE from NUMBERS s
					where s.LASTPING > #{lastSnapshot} and s.PHONE > #{fromPhone} and s.PHONE <= #{toPhone})
			""")
	int outdateHistorySnapshot(int rev, long lastSnapshot, String fromPhone, String toPhone);

	@Insert("""
			insert into NUMBERS_HISTORY (RMIN, RMAX, PHONE, CALLS, VOTES, DOWN_VOTES, UP_VOTES, LEGITIMATE, PING, POLL, ADVERTISING, GAMBLE, FRAUD, SEARCHES) (
				select #{rev}, 0x7fffffff, s.PHONE, s.CALLS, s.VOTES, s.DOWN_VOTES, s.UP_VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
				where s.LASTPING > #{lastSnapshot} and s.PHONE > #{fromPhone} and s.PHONE <= #{toPhone}
			)
			""")
	int createHistorySnapshot(int rev, long lastSnapshot, String fromPhone, String toPhone);

	/**
	 * Remove the backup (searches yesterday) from the current searches. Store the searches from the currently completed day into the backup.
	 * @param lastSnapshot Time when the last revision was created.
	 * @return The number of updated lines.
	 */
	@Update("""
			update NUMBERS s
			set
				s.SEARCHES_BACKUP = s.SEARCHES_CURRENT - s.SEARCHES_BACKUP,
				s.SEARCHES_CURRENT = s.SEARCHES_CURRENT - s.SEARCHES_BACKUP
			where s.LASTPING > #{lastSnapshot} and s.PHONE > #{fromPhone} and s.PHONE <= #{toPhone}
			""")
	int updateSearches(long lastSnapshot, String fromPhone, String toPhone);

	/**
	 * Deletes up to {@code batchSize} history rows of the given revision. Called repeatedly until it
	 * returns fewer than {@code batchSize} rows, so dropping a large revision never happens in a
	 * single oversized transaction.
	 */
	@Delete("delete from NUMBERS_HISTORY where RMIN=#{id} limit #{batchSize}")
	int cleanRevision(int id, int batchSize);
	
	@Delete("delete from REVISION where ID=#{id}")
	void removeRevision(int id);

	/**
	 * Publication sweep, addition/upgrade half (#342): merges the live
	 * visibility state into the BLOCKLIST table, quantized to vote buckets.
	 *
	 * <p>The source select picks every number whose live net evidence reaches
	 * the lowest bucket and computes its current bucket floor (2, 4, 10, 20,
	 * 50, 100). A BLOCKLIST row is written only when the bucket <em>changed</em>
	 * — numbers drifting inside their bucket cause no write at all, which is
	 * what keeps the H2 page churn of a sweep proportional to actual bucket
	 * flips. The redundant {@code SPAM_EVIDENCE >= t2} predicate makes the
	 * candidate scan an index range on {@code NUMBERS_SPAM_EVIDENCE_IDX}
	 * (legit evidence is non-negative, so it is a true superset).</p>
	 *
	 * <p>The bucket thresholds are per-sweep constants:
	 * {@code tN = DB.maxRawSpamAt(now, N)}.</p>
	 *
	 * @return number of rows inserted or updated — bucket flips only.
	 */
	@Update("""
		merge into BLOCKLIST b
		using (
			select s.PHONE,
				CASE WHEN s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE >= #{t100} THEN 100
				     WHEN s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE >= #{t50} THEN 50
				     WHEN s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE >= #{t20} THEN 20
				     WHEN s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE >= #{t10} THEN 10
				     WHEN s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE >= #{t4} THEN 4
				     ELSE 2 END as VOTES
			from NUMBERS s
			where s.SPAM_EVIDENCE >= #{t2} and s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE >= #{t2}
		) n on b.PHONE = n.PHONE
		when matched and b.VOTES <> n.VOTES then update set
			VOTES = n.VOTES, VERSION = #{version}
		when not matched then insert (PHONE, VOTES, VERSION)
			values (n.PHONE, n.VOTES, #{version})
		""")
	int publishBlocklistUpdates(long version,
		double t2, double t4, double t10, double t20, double t50, double t100);

	/**
	 * Publication sweep, removal half (#342): tombstones every published
	 * number whose live net evidence dropped below the lowest bucket —
	 * whether by decay or by legitimate votes. The scan runs over the small
	 * BLOCKLIST table with a primary-key probe into NUMBERS per row.
	 *
	 * @return number of rows tombstoned.
	 */
	@Update("""
		update BLOCKLIST b set VOTES = 0, VERSION = #{version}
		where b.VOTES > 0
		  and not exists (select 1 from NUMBERS s
		      where s.PHONE = b.PHONE and s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE >= #{t2})
		""")
	int publishBlocklistRemovals(long version, double t2);

	/**
	 * Publication sweep, per-region activity half (#340/#342): merges the
	 * log4 class of the decoded {@code NUMBERS_LOCALE.HEAT} into
	 * BLOCKLIST_LOCALE for every currently visible BLOCKLIST entry. The
	 * region is where the spam <em>reports</em> come from, not where the
	 * number originates — CardDAV ranks its capped per-region lists by this
	 * class. Writes only class flips; pure decay costs one class per two
	 * Heat half-lives (~4 weeks). Classes below 1 stay absent (rank 0);
	 * published rows decaying there are updated to 0 and swept by
	 * {@link #cleanupBlocklistLocale} once the number itself drops off.
	 *
	 * @return number of rows inserted or updated — class flips only.
	 */
	@Update("""
		merge into BLOCKLIST_LOCALE bl
		using (
			select l.PHONE, l.DIAL,
				CASE WHEN l.HEAT * #{heatDecode} < 1 THEN 0
				     ELSE CAST(FLOOR(LN(l.HEAT * #{heatDecode}) / LN(4)) AS INTEGER) END as HEAT
			from NUMBERS_LOCALE l
			join BLOCKLIST b on b.PHONE = l.PHONE
			where b.VOTES > 0
		) n on bl.PHONE = n.PHONE and bl.DIAL = n.DIAL
		when matched and bl.HEAT <> n.HEAT then update set HEAT = n.HEAT
		when not matched and n.HEAT > 0 then insert (PHONE, DIAL, HEAT)
			values (n.PHONE, n.DIAL, n.HEAT)
		""")
	int publishBlocklistLocale(double heatDecode);

	/**
	 * Removes the per-region rows of numbers that are no longer visible
	 * (tombstoned or pruned). Pure housekeeping — invisible numbers are
	 * filtered before ranking, so this never changes published content.
	 */
	@Delete("""
		delete from BLOCKLIST_LOCALE bl
		where not exists (select 1 from BLOCKLIST b
		    where b.PHONE = bl.PHONE and b.VOTES > 0)
		""")
	int cleanupBlocklistLocale();

	/**
	 * Removes tombstones that every client has had ample time to pick up.
	 *
	 * <p>The caller derives {@code maxVersion} from the pruning watermark
	 * (see {@code DB.publishBlocklist}): a property pair recording "version V
	 * existed at time T" proves every tombstone with {@code VERSION <= V} to
	 * be at least as old as T — no per-row timestamp needed. Incremental sync
	 * is forced to a full sync at least monthly, so an old tombstone only
	 * occupies space.</p>
	 */
	@Delete("delete from BLOCKLIST where VOTES = 0 and VERSION <= #{maxVersion}")
	int pruneBlocklistTombstones(long maxVersion);

	/**
	 * Gets all blocklist changes since the given version (#342).
	 *
	 * <p>Returns the frozen published state: bucket votes and the activity
	 * timestamp as of publication. Tombstones ({@code votes = 0}) signal
	 * removals. Category counters are joined live from NUMBERS — they only
	 * color the entry's rating; the left join keeps tombstones alive after a
	 * hard delete of the NUMBERS row (#341).</p>
	 */
	@Select("""
		select b.PHONE, b.VOTES,
		       coalesce(s.LASTPING, 0) as LASTPING,
		       coalesce(s.LEGITIMATE, 0) as LEGITIMATE,
		       coalesce(s.PING, 0) as PING,
		       coalesce(s.POLL, 0) as POLL,
		       coalesce(s.ADVERTISING, 0) as ADVERTISING,
		       coalesce(s.GAMBLE, 0) as GAMBLE,
		       coalesce(s.FRAUD, 0) as FRAUD
		from BLOCKLIST b
		left join NUMBERS s on s.PHONE = b.PHONE
		where b.VERSION > #{sinceVersion}
		order by b.VERSION, b.PHONE
		""")
	List<DBBlocklistEntry> getBlocklistChangesSince(long sinceVersion);

}
