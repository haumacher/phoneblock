package de.haumacher.phoneblock.mail.check.db;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * DB access interface for e-mail domain verification.
 */
public interface Domains {

	@Select("select DOMAIN_NAME, DISPOSABLE, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP from DOMAIN_CHECK where DOMAIN_NAME=#{domainName}")
	DBDomainCheck checkDomain(String domainName);
	
	@Insert("insert into DOMAIN_CHECK (DOMAIN_NAME, DISPOSABLE, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP) values (#{domainName}, #{disposable}, #{lastChanged}, #{sourceSystem}, #{mxHost}, #{mxIp})")
	int insertDomain(String domainName, boolean disposable, long lastChanged, int sourceSystem, String mxHost, String mxIp);
	
}
