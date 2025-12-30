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
			insert into NUMBERS (PHONE, SHA1, VOTES, DOWN_VOTES, UP_VOTES, UPDATED, ADDED)
			values (#{phone}, #{hash}, #{votes}, CASEWHEN (#{votes} > 0, #{votes}, 0), CASEWHEN (#{votes} < 0, 0 - #{votes}, 0), #{now}, #{now})
			""")
	void addReport(String phone, byte[] hash, int votes, long now);
	
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
				ACTIVE = CASEWHEN(#{delta} > 0, true, ACTIVE)
			where PHONE = #{phone}
			""")
	int addVote(String phone, int delta, long now);
	
	@Insert("""
			insert into NUMBERS_LOCALE (PHONE, DIAL, SEARCHES, VOTES, CALLS, LASTACCESS) 
			values (#{phone}, #{dialPrefix}, #{searches}, #{votes}, #{calls}, #{now})
			""")
	int insertNumberLocalization(String phone, String dialPrefix, int searches, int votes, int calls, long now);
	
	@Update("""
			update NUMBERS_LOCALE set 
				SEARCHES = SEARCHES + #{searches},
				VOTES = VOTES + #{votes},
				CALLS = CALLS + #{calls},
				LASTACCESS = #{now}
			where PHONE = #{phone} and DIAL = #{dialPrefix}
			""")
	int updateNumberLocalization(String phone, String dialPrefix, int searches, int votes, int calls, long now);
	
	@Update("update NUMBERS_AGGREGATION_10 set CNT = CNT + #{deltaCnt}, VOTES = VOTES + #{deltaVotes} where PREFIX = #{prefix}")
	int updateAggregation10(String prefix, int deltaCnt, int deltaVotes);
	
	@Update("update NUMBERS_AGGREGATION_100 set CNT = CNT + #{deltaCnt}, VOTES = VOTES + #{deltaVotes} where PREFIX = #{prefix}")
	int updateAggregation100(String prefix, int deltaCnt, int deltaVotes);
	
	@Insert("insert into NUMBERS_AGGREGATION_10 (PREFIX, CNT, VOTES) values (#{prefix}, #{cnt}, #{votes})")
	int insertAggregation10(String prefix, int cnt, int votes);
	
	@Insert("insert into NUMBERS_AGGREGATION_100 (PREFIX, CNT, VOTES) values (#{prefix}, #{cnt}, #{votes})")
	int insertAggregation100(String prefix, int cnt, int votes);
	
	@Select("select PREFIX, CNT, VOTES from NUMBERS_AGGREGATION_10 where PREFIX = #{prefix}")
	AggregationInfo getAggregation10(String prefix);
	
	@Select("select PREFIX, CNT, VOTES from NUMBERS_AGGREGATION_10")
	List<AggregationInfo> getAllAggregation10();
	
	@Select("select PREFIX, CNT, VOTES from NUMBERS_AGGREGATION_100 where PREFIX = #{prefix}")
	AggregationInfo getAggregation100(String prefix);
	
	@Select("select PREFIX, CNT, VOTES from NUMBERS_AGGREGATION_100")
	List<AggregationInfo> getAllAggregation100();
	
	@Select("""
			SELECT max(s.LASTPING) 
			FROM NUMBERS s
			WHERE s.PHONE > #{prefix}
			AND s.PHONE < concat(#{prefix}, 'Z')
			AND LENGTH(s.PHONE) = #{expectedLength}
			AND s.VOTES > 0
			""")
	Long getLastPingPrefix(String prefix, int expectedLength);
	
	@Select("""
			SELECT s.PHONE FROM NUMBERS s
			WHERE s.PHONE > #{prefix}
			AND s.PHONE < concat(#{prefix}, 'Z')
			AND LENGTH(s.PHONE) = #{expectedLength}
			AND s.VOTES > 0
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
			and s.VOTES > 0
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
	
	@Select("SELECT SUM(s.COUNT) FROM RATINGS s")
	Integer getTotalRatings();
	
	@Select("SELECT SUM(s.SEARCHES) FROM NUMBERS s")
	Integer getTotalSearches();
	
	@Select("""
			SELECT COUNT(1) FROM NUMBERS o
			where not ACTIVE
			""")
	Integer getArchivedReportCount();
	
	@Select("""
			SELECT COUNT(1) FROM NUMBERS s
			where ACTIVE
			""")
	Integer getActiveReportCount();
	
	@Update("""
			update NUMBERS s
			set ACTIVE=false
			where ACTIVE
			and s.LASTPING < #{before}
			and s.VOTES - (#{before} - s.LASTPING)/1000/60/60/24/7/#{weekPerVote} < #{minVotes}
			""")
	int archiveReportsWithLowVotes(long before, int minVotes, int weekPerVote);
	
	@Update("""
			update NUMBERS s
			set
				CALLS = CALLS + 1,
				s.UPDATED = greatest(s.UPDATED, #{now}), 
				s.LASTPING = greatest(s.LASTPING, #{now}) 
			where
				PHONE = #{phone}
			""")
	void recordCall(String phone, long now);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			where UPDATED >= #{after} and VOTES > 0 and ACTIVE
			order by UPDATED desc
			""")
	List<DBNumberInfo> getLatestReports(long after);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			where VOTES > 0 and ACTIVE
			order by UPDATED desc
			limit #{limit}
			""")
	List<DBNumberInfo> getAll(int limit);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			where s.PHONE = #{phone}
			""")
	DBNumberInfo getPhoneInfo(String phone);
	
	@Select("""
			select #{prefix}, max(s.ADDED), max(s.UPDATED), max(s.LASTSEARCH), true, sum(s.CALLS), sum(s.VOTES), sum(s.LEGITIMATE), sum(s.PING), sum(s.POLL), sum(s.ADVERTISING), sum(s.GAMBLE), sum(s.FRAUD), sum(s.SEARCHES) 
			from NUMBERS s
			where 
				s.PHONE > #{prefix}
				and s.PHONE < concat(#{prefix}, 'Z')
				and LENGTH(s.PHONE) = #{expectedLength}
			""")
	DBNumberInfo getPhoneInfoAggregate(String prefix, int expectedLength);
	
	@Select("""
			select s.PHONE from NUMBERS s
			WHERE s.PHONE > #{phone}
			ORDER BY s.PHONE
			LIMIT 1
			""")
	String getNextPhone(String phone);

	@Select("""
			select s.PHONE from NUMBERS s
			WHERE s.PHONE < #{phone}
			ORDER BY s.PHONE DESC
			LIMIT 1
			""")
	String getPrevPhone(String phone);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			WHERE ACTIVE
			ORDER BY s.VOTES DESC LIMIT #{cnt}
			""")
	List<DBNumberInfo> getTopSpammers(int cnt);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			ORDER BY s.SEARCHES DESC LIMIT #{cnt}
			""")
	List<DBNumberInfo> getTopSearchesOverall(int cnt);
	
	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			WHERE ACTIVE and VOTES >= #{minVotes} AND ADDED > 0 ORDER BY ADDED DESC LIMIT 10
			""")
	List<DBNumberInfo> getLatestBlocklistEntries(int minVotes);

	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			where s.ACTIVE and s.VOTES >= #{minVotes}
			order by s.PHONE
			""")
	List<DBNumberInfo> getBlocklist(int minVotes);
	
	@Select("""
			select s.PHONE, s.SEARCHES_CURRENT, s.SEARCHES, s.LASTSEARCH from NUMBERS s
			where s.VOTES > 0
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
			select h.RMIN, h.RMAX, h.PHONE, h.ACTIVE, h.CALLS, h.VOTES, h.LEGITIMATE, h.PING, h.POLL, h.ADVERTISING, h.GAMBLE, h.FRAUD, h.SEARCHES from NUMBERS_HISTORY h
			where h.RMAX >= #{revision} and h.PHONE = #{phone} order by h.RMIN
			""")
	List<DBNumberHistory> getSearchHistory(int revision, String phone);

	/**
	 * The newest history entry for the given number that is not newer than the requested revision.
	 */
	@Select("""
			select h.RMIN, h.RMAX, h.PHONE, h.ACTIVE, h.CALLS, h.VOTES, h.LEGITIMATE, h.PING, h.POLL, h.ADVERTISING, h.GAMBLE, h.FRAUD, h.SEARCHES from NUMBERS_HISTORY h
			where h.RMIN <= #{rev} and h.RMAX >= #{rev} and h.PHONE = #{phone}
			""")
	DBNumberHistory getHistoryEntry(String phone, int rev);

	@Select("""
			select h.RMIN, h.RMAX, h.PHONE, h.ACTIVE, h.CALLS, h.VOTES, h.LEGITIMATE, h.PING, h.POLL, h.ADVERTISING, h.GAMBLE, h.FRAUD, h.SEARCHES from NUMBERS_HISTORY h
			where h.RMIN = #{rev}
			""")
	List<DBNumberHistory> getHistoryEntries(int rev);
	
	@Select(
		"""
		<script>
		select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
		where s.PHONE in 
		    <foreach item="item" index="index" collection="numbers" open="(" separator="," close=")">
		        #{item}
		    </foreach>
		</script>
		""")
	List<DBNumberInfo> getNumbers(Collection<String> numbers);

	@Select("""
			select s.PHONE, s.ADDED, s.UPDATED, s.LASTSEARCH, s.ACTIVE, s.CALLS, s.VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
			where ACTIVE
			""")
	List<DBNumberInfo> getReports();
	
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
			where ACTIVE and VOTES >= #{minVotes}
			""")
	List<String> getBlockList(int minVotes);
	
	@Select("""
			SELECT CASE WHEN s.VOTES < #{minVotes} THEN 'reported' ELSE 'blocked' END state, COUNT(1) cnt FROM NUMBERS s
			where s.ACTIVE and s.VOTES > 0
			GROUP BY state
			ORDER BY state
			""")
	List<Statistics> getStatistics(int minVotes);
	
	@Update("""
			update NUMBERS s
			set 
				s.LEGITIMATE = s.LEGITIMATE + casewhen(#{rating}='A_LEGITIMATE', 1, 0), 
				s.PING = s.PING + casewhen(#{rating}='C_PING', 1, 0), 
				s.POLL = s.POLL + casewhen(#{rating}='D_POLL', 1, 0), 
				s.ADVERTISING = s.ADVERTISING + casewhen(#{rating}='E_ADVERTISING', 1, 0), 
				s.GAMBLE = s.GAMBLE + casewhen(#{rating}='F_GAMBLE', 1, 0), 
				s.FRAUD = s.FRAUD + casewhen(#{rating}='G_FRAUD', 1, 0), 
				s.UPDATED = greatest(s.UPDATED, #{now}), 
				s.LASTPING = greatest(s.LASTPING, #{now}) 
			where s.PHONE = #{phone}
			""")
	int incRating(String phone, Rating rating, long now);
	
	@Update("""
			update NUMBERS s
			set 
				s.SEARCHES = s.SEARCHES + 1, 
				s.SEARCHES_CURRENT = s.SEARCHES_CURRENT + 1, 
				s.LASTSEARCH = GREATEST(s.LASTSEARCH, #{now}), 
				s.LASTPING = GREATEST(s.LASTPING, #{now})
			where s.PHONE=#{phone}
			""")
	int incSearchCount(String phone, long now);

	@Select("""
			<script>
			select s.ID, s.PHONE, s.RATING, s.COMMENT, s.LOCALE, s.SERVICE, s.CREATED, s.UP, s.DOWN, s.USERID 
			from COMMENTS s 
			where s.PHONE=#{phone} and s.LOCALE in 
			    <foreach item="item" index="index" collection="langs" open="(" separator="," close=")">
			        #{item}
			    </foreach>
			</script>
			""")
	List<DBUserComment> getComments(String phone, Collection<String> langs);
	
	@Select("""
			select s.ID, s.PHONE, s.RATING, s.COMMENT, s.LOCALE, s.SERVICE, s.CREATED, s.UP, s.DOWN, s.USERID 
			from COMMENTS s 
			where s.PHONE=#{phone}
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

	@Delete("delete from COMMENTS where USERID = #{userId} and PHONE = #{phone}")
	int deleteUserComment(long userId, String phone);

	@Update("update COMMENTS s set s.UP = s.UP + #{up}, s.DOWN = s.DOWN + #{down} where s.ID = #{id}")
	int updateCommentVotes(String id, int up, int down);
	
	@Select("select s.LASTMETA from NUMBERS s where s.PHONE=#{phone}")
	Long getLastMetaSearch(String phone);
	
	@Update("update NUMBERS s set s.LASTMETA=#{lastUpdate} where s.PHONE=#{phone}")
	int setLastMetaSearch(String phone, long lastUpdate);
	
	@Insert("insert into NUMBERS (PHONE, SHA1, ADDED, LASTMETA) values (#{phone}, #{hash}, #{now}, #{now})")
	void insertLastMetaSearch(String phone, byte[] hash, long now);
	
	@Select("SELECT PHONE FROM SUMMARY_REQUEST sr ORDER BY sr.PRIORITY LIMIT 1")
	String topSummaryRequest();
	
	@Delete("DELETE FROM SUMMARY_REQUEST WHERE PHONE = #{phone}")
	int dropSummaryRequest(String phone);
	
	@Insert("""
			INSERT INTO SUMMARY_REQUEST (PHONE)
			SELECT PHONE FROM (
				SELECT c.PHONE PHONE, COUNT(1) cnt, MAX(c.CREATED) lastComment, MAX(s.CREATED) lastSummary FROM COMMENTS c
				LEFT OUTER JOIN SUMMARY s
				ON s.PHONE = c.PHONE
				LEFT OUTER JOIN SUMMARY_REQUEST sr
				ON sr.PHONE = c.PHONE
				WHERE sr.PHONE IS NULL
				GROUP BY c.PHONE
			)
			WHERE cnt > 5 AND (lastSummary IS NULL OR lastSummary + 7 * 24 * 60 * 60 * 1000 < lastComment)
			ORDER BY cnt DESC
			""")
	int scheduleSummaryRequests();
	
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
			insert into NUMBERS_HISTORY (RMIN, RMAX, PHONE, ACTIVE, CALLS, VOTES, DOWN_VOTES, UP_VOTES, LEGITIMATE, PING, POLL, ADVERTISING, GAMBLE, FRAUD, SEARCHES) (
				select #{rev}, 0x7fffffff, s.PHONE, s.ACTIVE, s.CALLS, s.VOTES, s.DOWN_VOTES, s.UP_VOTES, s.LEGITIMATE, s.PING, s.POLL, s.ADVERTISING, s.GAMBLE, s.FRAUD, s.SEARCHES from NUMBERS s
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

}
