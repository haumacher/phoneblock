package de.haumacher.mailcheck.db;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * DB access interface for e-mail domain verification.
 */
public interface Domains {

	@Select("select DOMAIN_NAME, DISPOSABLE, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP from DOMAIN_CHECK where DOMAIN_NAME=#{domainName}")
	DBDomainCheck checkDomain(String domainName);
	
	@Insert("insert into DOMAIN_CHECK (DOMAIN_NAME, DISPOSABLE, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP) values (#{domainName}, #{disposable}, #{lastChanged}, #{sourceSystem}, #{mxHost}, #{mxIp})")
	int insertDomain(String domainName, boolean disposable, long lastChanged, String sourceSystem, String mxHost, String mxIp);

	@Select("select EMAIL_ADDRESS, DISPOSABLE, LAST_CHECKED, SOURCE_SYSTEM from EMAIL_CHECK where EMAIL_ADDRESS=#{emailAddress}")
	DBEmailCheck checkEmailAddress(String emailAddress);

	@Insert("insert into EMAIL_CHECK (EMAIL_ADDRESS, DISPOSABLE, LAST_CHECKED, SOURCE_SYSTEM) values (#{emailAddress}, #{disposable}, #{lastChecked}, #{sourceSystem})")
	int insertEmailCheck(String emailAddress, boolean disposable, long lastChecked, String sourceSystem);

	@Select("select MX_HOST as `key`, STATUS, LAST_UPDATED as lastUpdated from MX_HOST_STATUS where MX_HOST=#{mxHost}")
	DBMxStatus checkMxHost(String mxHost);

	@Insert("insert into MX_HOST_STATUS (MX_HOST, STATUS, LAST_UPDATED) values (#{mxHost}, #{status}, #{lastUpdated})")
	int insertMxHost(String mxHost, String status, long lastUpdated);

	@Update("update MX_HOST_STATUS set STATUS=#{status}, LAST_UPDATED=#{lastUpdated} where MX_HOST=#{mxHost}")
	int updateMxHostStatus(String mxHost, String status, long lastUpdated);

	@Select("select MX_IP as `key`, STATUS, LAST_UPDATED as lastUpdated from MX_IP_STATUS where MX_IP=#{mxIp}")
	DBMxStatus checkMxIp(String mxIp);

	@Insert("insert into MX_IP_STATUS (MX_IP, STATUS, LAST_UPDATED) values (#{mxIp}, #{status}, #{lastUpdated})")
	int insertMxIp(String mxIp, String status, long lastUpdated);

	@Update("update MX_IP_STATUS set STATUS=#{status}, LAST_UPDATED=#{lastUpdated} where MX_IP=#{mxIp}")
	int updateMxIpStatus(String mxIp, String status, long lastUpdated);

}
