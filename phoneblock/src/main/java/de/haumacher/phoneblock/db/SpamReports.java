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
			+ " SELECT s.* FROM SPAMREPORTS s"
			+ " WHERE s.LASTUPDATE < #{before} AND s.VOTES < #{minVotes})")
	int archiveReportsWithLowVotes(long before, int minVotes);
	
	@Delete("DELETE FROM SPAMREPORTS s "
			+ " WHERE s.LASTUPDATE <= #{now} AND (SELECT SUM(o.VOTES) FROM OLDREPORTS o WHERE s.PHONE = o.PHONE) > 0")
	int deleteArchivedReports(long now);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS where LASTUPDATE >= #{after} order by LASTUPDATE desc")
	List<SpamReport> getLatestReports(long after);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS order by LASTUPDATE desc")
	List<SpamReport> getAll();
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS where PHONE = #{phone}")
	SpamReport getPhoneInfo(String phone);
	
	@Select("SELECT PHONE, VOTES, LASTUPDATE, DATEADDED FROM SPAMREPORTS s"
			+ " WHERE s.LASTUPDATE >= #{notBefore}"
			+ " ORDER BY s.VOTES DESC LIMIT #{cnt}")
	List<SpamReport> getTopSpammers(int cnt, long notBefore);
	
	@Select("SELECT x.* FROM PUBLIC.SPAMREPORTS x"
			+ " WHERE VOTES >= #{minVotes} AND DATEADDED > 0 ORDER BY DATEADDED DESC LIMIT 10")
	List<SpamReport> getLatestBlocklistEntries(int minVotes);
	
	@Select("select PHONE, VOTES, LASTUPDATE, DATEADDED from SPAMREPORTS where VOTES >= #{minVotes} order by PHONE")
	List<SpamReport> getReports(int minVotes);
	
	@Select("select PHONE from SPAMREPORTS where VOTES >= #{minVotes}")
	Set<String> getSpamList(int minVotes);
	
	@Select("SELECT COUNT(1) cnt, CASE WHEN s.VOTES < #{minVotes} THEN 0 WHEN s.VOTES < 6 THEN 1 ELSE 2 END confidence FROM SPAMREPORTS s GROUP BY confidence ORDER BY confidence DESC")
	List<Statistics> getStatistics(int minVotes);
}
