/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * Query interface for the block list table.
 */
public interface BlockList {
	
	@Select("select PHONE from BLOCKLIST where OWNER = #{owner} order by PHONE")
	List<String> getPersonalizations(long owner);

	@Select("select PHONE from EXCLUDES where OWNER = #{owner}")
	Set<String> getExcluded(long owner);

	@Insert("insert into BLOCKLIST (OWNER, PHONE) values (#{owner}, #{phone})")
	void addPersonalization(long owner, String phone);
	
	@Delete("delete from BLOCKLIST where OWNER=#{owner} and PHONE=#{phone}")
	boolean removePersonalization(long owner, String phone);
	
	@Insert("insert into EXCLUDES (OWNER, PHONE) values (#{owner}, #{phone})")
	void addExclude(long owner, String phone);
	
	@Delete("delete from EXCLUDES where OWNER=#{owner} and PHONE=#{phone}")
	boolean removeExclude(long owner, String phone);
	
}
