/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import de.haumacher.phoneblock.app.Rating;

/**
 * Interface for the spam report table.
 */
public interface SpamReports {
	
	@Insert("insert into SPAMREPORTS (PHONE, VOTES, LASTUPDATE, DATEADDED) values (#{phone}, #{votes}, #{now}, #{now})")
	void addReport(String phone, int votes, long now);
	
	@Select("select max(LASTUPDATE) from SPAMREPORTS")
	Long getLastUpdate();

	@Update("update SPAMREPORTS set VOTES = VOTES + #{delta}, LASTUPDATE = #{now} where PHONE = #{phone}")
	void addVote(String phone, int delta, long now);

	@Select("select count(1) from SPAMREPORTS where PHONE = #{phone}")
	boolean isKnown(String phone);

	@Select("select VOTES from SPAMREPORTS where PHONE = #{phone}")
	int getVotes(String phone);

	@Select("SELECT SUM(s.VOTES) FROM SPAMREPORTS s")
	Integer getTotalVotes();
	
	@Select("SELECT COUNT(1) FROM OLDREPORTS o")
	Integer getArchivedReportCount();
	
	@Delete("delete FROM SPAMREPORTS where PHONE = #{phone}")
	void delete(String phone);
	
	@Update("UPDATE SPAMREPORTS s"
			+ " SET s.VOTES = s.VOTES + (SELECT SUM(o.VOTES) FROM OLDREPORTS o WHERE s.PHONE = o.PHONE)"
			+ " WHERE s.LASTUPDATE <= #{now} AND (SELECT SUM(x.VOTES) FROM OLDREPORTS x WHERE s.PHONE = x.PHONE) > 0")
	int reactivateOldReportsWithNewVotes(long now);
	
	@Delete("DELETE FROM OLDREPORTS o "
			+ " WHERE (SELECT SUM(s.VOTES) FROM SPAMREPORTS s WHERE s.LASTUPDATE <= #{now} AND s.PHONE = o.PHONE) > 0")
	int deleteOldReportsWithNewVotes(long now);
	
	@Insert("INSERT INTO OLDREPORTS ("
			+ "SELECT * FROM SPAMREPORTS s WHERE "
			+ "CASE "
			+ "WHEN s.LASTUPDATE >= #{before} THEN 1=0 "
			+ "ELSE s.VOTES - (#{before} - s.LASTUPDATE)/1000/60/60/24/7/#{weekPerVote} < #{minVotes} "
			+ "END"
			+ ")")
	int archiveReportsWithLowVotes(long before, int minVotes, int weekPerVote);
	
	@Delete("DELETE FROM SPAMREPORTS s "
			+ " WHERE s.LASTUPDATE <= #{now} AND (SELECT SUM(o.VOTES) FROM OLDREPORTS o WHERE s.PHONE = o.PHONE) > 0")
	int deleteArchivedReports(long now);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS where LASTUPDATE >= #{after} order by LASTUPDATE desc")
	List<SpamReport> getLatestReports(long after);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS order by LASTUPDATE desc limit #{limit}")
	List<SpamReport> getAll(int limit);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS where PHONE = #{phone}")
	SpamReport getPhoneInfo(String phone);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from OLDREPORTS where PHONE = #{phone}")
	SpamReport getPhoneInfoArchived(String phone);
	
	@Select("SELECT PHONE, VOTES, LASTUPDATE, DATEADDED FROM SPAMREPORTS s"
			+ " WHERE s.LASTUPDATE >= #{notBefore}"
			+ " ORDER BY s.VOTES DESC LIMIT #{cnt}")
	List<SpamReport> getTopSpammers(int cnt, long notBefore);
	
	@Select("SELECT x.* FROM PUBLIC.SPAMREPORTS x"
			+ " WHERE VOTES >= #{minVotes} AND DATEADDED > 0 ORDER BY DATEADDED DESC LIMIT 10")
	List<SpamReport> getLatestBlocklistEntries(int minVotes);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS where VOTES >= #{minVotes} order by PHONE")
	List<SpamReport> getReports(int minVotes);
	
	@Select("select PHONE from SPAMREPORTS where VOTES >= #{minVotes} order by LASTUPDATE desc limit #{maxLength}")
	Set<String> getSpamList(int minVotes, int maxLength);
	
	@Select("SELECT COUNT(1) cnt, CASE WHEN s.VOTES < #{minVotes} THEN 0 WHEN s.VOTES < 6 THEN 1 ELSE 2 END confidence FROM SPAMREPORTS s GROUP BY confidence ORDER BY confidence DESC")
	List<Statistics> getStatistics(int minVotes);

	@Select("select s.COUNT from RATINGS s where s.PHONE=#{phone} and s.RATING=#{rating}")
	Integer getRatingCount(String phone, Rating rating);
	
	@Select("select s.RATING from RATINGS s where s.PHONE=#{phone} order by s.COUNT desc, s.RATING desc limit 1")
	Rating getRating(String phone);
	
	@Insert("insert into RATINGS (PHONE, RATING, COUNT) values (#{phone}, #{rating}, 1)")
	void addRating(String phone, Rating rating);
	
	@Update("update RATINGS s set s.COUNT = s.COUNT + 1 where s.PHONE=#{phone} and s.RATING=#{rating}")
	void incRating(String phone, Rating rating);
	
	@Update("update SEARCHES s set s.COUNT = s.COUNT + 1, LASTUPDATE=#{now} where s.PHONE=#{phone}")
	int incSearchCount(String phone, long now);
	
	@Insert("insert into SEARCHES (PHONE, LASTUPDATE) values (#{phone}, #{now})")
	void addSearchEntry(String phone, long now);
}
