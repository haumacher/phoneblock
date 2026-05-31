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
	 * only exist for numbers that are actually spam — exactly the rows the privacy-aware
	 * hash-prefix lookup ({@code getPhoneInfosByHashPrefix}) returns, which filter on
	 * {@code SPAM_EVIDENCE > LEGIT_EVIDENCE}. The guard is in the {@code WHERE} clause (not
	 * a {@code CASEWHEN} over a NULL branch, which confuses H2's type inference for the
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

	@Select("""
			SELECT s.PHONE FROM NUMBERS s
			WHERE s.PHONE > #{prefix}
			AND s.PHONE < concat(#{prefix}, 'Z')
			AND LENGTH(s.PHONE) = #{expectedLength}
			AND s.SPAM_EVIDENCE > s.LEGIT_EVIDENCE
			order by s.PHONE
			""")
	List<String> getRelatedNumbers(String prefix, int expectedLength);

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
			where UPDATED >= #{after} and SPAM_EVIDENCE > LEGIT_EVIDENCE
			order by UPDATED desc
			""")
	List<DBNumberInfo> getLatestReports(long after);
	
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
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.LASTPING, s.SPAM_EVIDENCE as PUBLISHED_SPAM_EVIDENCE, s.LEGIT_EVIDENCE as PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s USE INDEX (NUMBERS_SHA1_IDX)
			where s.SHA1 >= #{low} and s.SHA1 < #{high} and s.SPAM_EVIDENCE > s.LEGIT_EVIDENCE
			""")
	List<DBNumberInfo> getPhoneInfosByHashPrefix(byte[] low, byte[] high);
	
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
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.PUBLISHED_LASTPING as LASTPING, s.PUBLISHED_SPAM_EVIDENCE, s.PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
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
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.PUBLISHED_LASTPING as LASTPING, s.PUBLISHED_SPAM_EVIDENCE, s.PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE
			from NUMBERS_LOCALE l
			join NUMBERS s on s.PHONE = l.PHONE
			where l.DIAL = #{dial} and (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{maxRawSpam}
			order by l.HEAT desc
			limit #{limit}
			""")
	List<DBNumberInfo> getBlocklistByDialHeat(String dial, double maxRawSpam, int limit);

	/**
	 * Full blocklist for {@code /api/blocklist} (#342): one row per visible
	 * number above the projected visibility threshold. SQL-side filter only —
	 * no Java post-step, the index {@code NUMBERS_SPAM_EVIDENCE_IDX} backs the
	 * predicate.
	 */
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES, s.PUBLISHED_LASTPING as LASTPING, s.PUBLISHED_SPAM_EVIDENCE, s.PUBLISHED_LEGIT_EVIDENCE, s.HEAT, s.SPAM_EVIDENCE, s.LEGIT_EVIDENCE from NUMBERS s
			where (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) >= #{maxRawSpam}
			order by s.PHONE
			""")
	List<DBNumberInfo> getBlocklist(double maxRawSpam);
	
	@Select("""
			select s.PHONE, s.SEARCHES_CURRENT, s.SEARCHES, s.LASTSEARCH from NUMBERS s
			where s.SPAM_EVIDENCE > s.LEGIT_EVIDENCE
			order by s.SEARCHES_CURRENT desc
			limit #{limit}
			""")
	List<DBSearchInfo> getTopSearchesCurrent(int limit);
	
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
	 * Reports as of the last released blocklist version: snapshot taken from
	 * {@code PUBLISHED_SPAM_EVIDENCE} (#342), last activity from
	 * PUBLISHED_LASTPING, restricted to entries that have been included in
	 * at least one release ({@code VERSION > 0}) and whose published net
	 * evidence is still positive (otherwise they are effectively a deletion
	 * in the released list). The result is stable between releases — used by
	 * the CardDAV pipeline so the address-book ETag does not flap on every
	 * individual vote.
	 */
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS,
			       s.VOTES, s.LEGITIMATE, s.PING, s.POLL,
			       s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES,
			       s.PUBLISHED_LASTPING as LASTPING,
			       s.PUBLISHED_SPAM_EVIDENCE, s.PUBLISHED_LEGIT_EVIDENCE
			from NUMBERS s
			where s.VERSION > 0
			  AND (s.PUBLISHED_SPAM_EVIDENCE - s.PUBLISHED_LEGIT_EVIDENCE) > 0
			""")
	List<DBNumberInfo> getPublishedReports();
	
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

	@Select("""
			SELECT CASE WHEN (s.SPAM_EVIDENCE - s.LEGIT_EVIDENCE) < #{maxRawSpam} THEN 'reported' ELSE 'blocked' END state, COUNT(1) cnt FROM NUMBERS s
			where s.SPAM_EVIDENCE > s.LEGIT_EVIDENCE
			GROUP BY state
			ORDER BY state
			""")
	List<Statistics> getStatistics(double maxRawSpam);

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
	
	@Select("select min(ID) from REVISION")
	int getOldestRevision();

	@Update("""
			update NUMBERS_HISTORY 
			set 
				RMAX = #{rev} - 1
			where
				PHONE in (select s.PHONE from NUMBERS s where s.LASTPING > #{lastSnapshot}) and
				RMAX = 0x7fffffff
			""")
	int outdateHistorySnapshot(int rev, long lastSnapshot);
	
	@Insert("""
			insert into NUMBERS_HISTORY (RMIN, RMAX, PHONE, CALLS, VOTES, DOWN_VOTES, UP_VOTES, LEGITIMATE, PING, POLL, ADVERTISING, GAMBLE, FRAUD, SEARCHES) (
				select #{rev}, 0x7fffffff, s.PHONE, s.CALLS, s.VOTES, s.DOWN_VOTES, s.UP_VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
				where s.LASTPING > #{lastSnapshot}
			)
			""")
	int createHistorySnapshot(int rev, long lastSnapshot);
	
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
			where s.LASTPING > #{lastSnapshot}
			""")
	int updateSearches(long lastSnapshot);
	
	@Delete("delete from NUMBERS_HISTORY where RMIN=#{id}")
	int cleanRevision(int id);
	
	@Delete("delete from REVISION where ID=#{id}")
	void removeRevision(int id);

	/**
	 * Snapshot-driven blocklist version assignment (#342). The
	 * {@link de.haumacher.phoneblock.scheduler.BlocklistVersionService} sweep
	 * is the single source of {@code VERSION} bumps; the obsolete event-driven
	 * {@code crossesThreshold} → {@code PENDING_UPDATE} path was removed.
	 *
	 * <p>A row gets the new version when either:
	 * <ul>
	 * <li>its visibility class flipped between the last snapshot and now —
	 *     {@code (current_net &gt;= currentMaxRawSpam)} XOR
	 *     {@code (published_net &gt;= lastMaxRawSpam)}. Decay-induced flips
	 *     (a number that decays below the floor over time, with no new votes)
	 *     are detected this way too, in the same sweep as event-driven flips.</li>
	 * <li>or the row had activity since the last sweep — refreshes
	 *     {@code PUBLISHED_LASTPING} / {@code PUBLISHED_SPAM_EVIDENCE} /
	 *     {@code PUBLISHED_LEGIT_EVIDENCE} for already-published rows so the
	 *     snapshot tracks recent changes without flipping visibility.</li>
	 * </ul>
	 *
	 * @param version           the new global blocklist version.
	 * @param lastAssignTime    timestamp of the previous sweep. Rows with
	 *                          {@code LASTPING > lastAssignTime} are picked up
	 *                          as recently active.
	 * @param currentMaxRawSpam projected visibility threshold at this sweep
	 *                          (= {@code Ema.projectedThreshold(minVotes - 0.5, now, tau)}).
	 * @param lastMaxRawSpam    same projection at the previous sweep's
	 *                          timestamp — applied against
	 *                          {@code PUBLISHED_SPAM_EVIDENCE -
	 *                          PUBLISHED_LEGIT_EVIDENCE} to reconstruct the
	 *                          visibility class as it was at the last release.
	 * @return number of rows touched.
	 */
	@Update("""
		update NUMBERS set
			VERSION = #{version},
			PUBLISHED_LASTPING = LASTPING,
			PUBLISHED_SPAM_EVIDENCE = SPAM_EVIDENCE,
			PUBLISHED_LEGIT_EVIDENCE = LEGIT_EVIDENCE
		where
			(((SPAM_EVIDENCE - LEGIT_EVIDENCE) >= #{currentMaxRawSpam})
			 <> ((PUBLISHED_SPAM_EVIDENCE - PUBLISHED_LEGIT_EVIDENCE) >= #{lastMaxRawSpam}))
		   OR (VERSION > 0 AND LASTPING > #{lastAssignTime})
		""")
	int assignBlocklistVersion(long version, long lastAssignTime,
		double currentMaxRawSpam, double lastMaxRawSpam);

	/**
	 * Gets all blocklist changes since the given version.
	 *
	 * <p>Returns entries with {@code VERSION > sinceVersion}. The
	 * snapshot-driven sweep (#342) writes whatever
	 * {@code PUBLISHED_SPAM_EVIDENCE} / {@code PUBLISHED_LEGIT_EVIDENCE} are
	 * at the moment of publication — rows that decayed below the visibility
	 * threshold get the new (low) snapshot values written, so
	 * {@code toBlocklistEntry} decodes them to {@code votes = 0} and clients
	 * treat the entry as a removal. No CASE-when gating needed.</p>
	 */
	@Select("""
		select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.CALLS,
		       s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES,
		       s.PUBLISHED_LASTPING as LASTPING,
		       s.PUBLISHED_SPAM_EVIDENCE,
		       s.PUBLISHED_LEGIT_EVIDENCE,
		       s.HEAT,
		       s.SPAM_EVIDENCE,
		       s.LEGIT_EVIDENCE
		from NUMBERS s
		where s.VERSION > #{sinceVersion}
		order by s.VERSION, s.PHONE
		""")
	List<DBNumberInfo> getBlocklistChangesSince(long sinceVersion);

}
