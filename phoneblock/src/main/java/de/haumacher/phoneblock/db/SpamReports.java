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
	
	@Insert("insert into SPAMREPORTS (PHONE, VOTES, LASTUPDATE) values (#{phone}, #{votes}, #{now})")
	void addReport(String phone, int votes, long now);
	
	@Select("select max(LASTUPDATE) from SPAMREPORTS")
	long getLastUpdate();

	@Update("update SPAMREPORTS set VOTES = VOTES + #{delta}, LASTUPDATE = #{now} where PHONE = #{phone}")
	void addVote(String phone, int delta, long now);

	@Select("select count(1) from SPAMREPORTS where PHONE = #{phone}")
	boolean isKnown(String phone);

	@Select("select VOTES from SPAMREPORTS where PHONE = #{phone}")
	int getVotes(String phone);

	@Delete("delete FROM SPAMREPORTS where PHONE = #{phone}")
	void delete(String phone);
	
	@Select("select PHONE, VOTES, LASTUPDATE from SPAMREPORTS where LASTUPDATE >= #{after} order by LASTUPDATE desc")
	List<SpamReport> getLatestReports(long after);
	
	@Select("select PHONE, VOTES, LASTUPDATE from SPAMREPORTS where VOTES >= #{minVotes} order by PHONE")
	List<SpamReport> getReports(int minVotes);
	
	@Select("select PHONE from SPAMREPORTS where VOTES >= #{minVotes}")
	Set<String> getSpamList(int minVotes);
	
}
