package de.haumacher.mailcheck.db;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * DB access interface for e-mail domain verification.
 */
public interface Domains {

	@Select("select VAL from MAILCHECK_PROPERTIES where NAME=#{name}")
	String getProperty(String name);

	@Update("update MAILCHECK_PROPERTIES set VAL=#{value} where NAME=#{name}")
	int updateProperty(String name, String value);

	@Insert("insert into MAILCHECK_PROPERTIES (NAME, VAL) values (#{name}, #{value})")
	int insertProperty(String name, String value);

	@Select("select DOMAIN_NAME, STATUS AS statusName, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP from DOMAIN_CHECK where DOMAIN_NAME=#{domainName}")
	DBDomainCheck checkDomain(String domainName);

	@Insert("insert into DOMAIN_CHECK (DOMAIN_NAME, STATUS, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP) values (#{domainName}, #{status}, #{lastChanged}, #{sourceSystem}, #{mxHost}, #{mxIp})")
	int insertDomain(String domainName, String status, long lastChanged, String sourceSystem, String mxHost, String mxIp);

	@Select("select EMAIL_ADDRESS, DISPOSABLE, LAST_CHECKED, SOURCE_SYSTEM from EMAIL_CHECK where EMAIL_ADDRESS=#{emailAddress}")
	DBEmailCheck checkEmailAddress(String emailAddress);

	@Insert("insert into EMAIL_CHECK (EMAIL_ADDRESS, DISPOSABLE, LAST_CHECKED, SOURCE_SYSTEM) values (#{emailAddress}, #{disposable}, #{lastChecked}, #{sourceSystem})")
	int insertEmailCheck(String emailAddress, boolean disposable, long lastChecked, String sourceSystem);

	@Select("select DOMAIN_NAME, STATUS AS statusName, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP from DOMAIN_CHECK where MX_HOST is null")
	List<DBDomainCheck> findDomainsWithoutMx();

	@Select("select DOMAIN_NAME, STATUS AS statusName, LAST_CHANGED, SOURCE_SYSTEM, MX_HOST, MX_IP from DOMAIN_CHECK")
	List<DBDomainCheck> findAllDomains();

	@Update("update DOMAIN_CHECK set MX_HOST=#{mxHost}, MX_IP=#{mxIp} where DOMAIN_NAME=#{domainName}")
	int updateDomainMx(String domainName, String mxHost, String mxIp);

	@Delete("delete from MX_HOST_STATUS")
	int clearMxHostStatus();

	@Delete("delete from MX_IP_STATUS")
	int clearMxIpStatus();

	@Insert("INSERT INTO MX_HOST_STATUS (MX_HOST, MX_IP, STATUS, DOMAIN_COUNT, LAST_UPDATED) " +
		"SELECT MX_HOST, MAX(MX_IP), " +
		"  CASE " +
		"    WHEN MIN(CASE WHEN STATUS = #{disposable} THEN 1 ELSE 0 END) = 1 THEN #{disposable} " +
		"    WHEN MAX(CASE WHEN STATUS = #{disposable} THEN 1 ELSE 0 END) = 0 THEN #{safe} " +
		"    ELSE #{mixed} " +
		"  END, " +
		"  COUNT(*), " +
		"  MAX(LAST_CHANGED) " +
		"FROM DOMAIN_CHECK " +
		"WHERE MX_HOST IS NOT NULL AND MX_HOST <> '-' " +
		"GROUP BY MX_HOST")
	int aggregateMxHostStatus(@Param("disposable") String disposable, @Param("safe") String safe, @Param("mixed") String mixed);

	@Insert("INSERT INTO MX_IP_STATUS (MX_IP, STATUS, DOMAIN_COUNT, LAST_UPDATED) " +
		"SELECT MX_IP, " +
		"  CASE " +
		"    WHEN MIN(CASE WHEN STATUS = #{disposable} THEN 1 ELSE 0 END) = 1 THEN #{disposable} " +
		"    WHEN MAX(CASE WHEN STATUS = #{disposable} THEN 1 ELSE 0 END) = 0 THEN #{safe} " +
		"    ELSE #{mixed} " +
		"  END, " +
		"  COUNT(*), " +
		"  MAX(LAST_CHANGED) " +
		"FROM DOMAIN_CHECK " +
		"WHERE MX_IP IS NOT NULL " +
		"GROUP BY MX_IP")
	int aggregateMxIpStatus(@Param("disposable") String disposable, @Param("safe") String safe, @Param("mixed") String mixed);

	@Select("select MX_HOST as `key`, STATUS, DOMAIN_COUNT as domainCount, LAST_UPDATED as lastUpdated from MX_HOST_STATUS where MX_HOST=#{mxHost}")
	DBMxStatus checkMxHost(String mxHost);

	@Insert("insert into MX_HOST_STATUS (MX_HOST, MX_IP, STATUS, DOMAIN_COUNT, LAST_UPDATED) values (#{mxHost}, #{mxIp}, #{status}, 1, #{lastUpdated})")
	int insertMxHost(String mxHost, String mxIp, String status, long lastUpdated);

	@Update("update MX_HOST_STATUS set STATUS=#{status}, DOMAIN_COUNT=DOMAIN_COUNT+1, LAST_UPDATED=#{lastUpdated} where MX_HOST=#{mxHost}")
	int updateMxHostStatus(String mxHost, String status, long lastUpdated);

	@Update("update MX_HOST_STATUS set DOMAIN_COUNT=DOMAIN_COUNT+1, LAST_UPDATED=#{lastUpdated} where MX_HOST=#{mxHost}")
	int incrementMxHostCount(String mxHost, long lastUpdated);

	@Select("select MX_IP as `key`, STATUS, DOMAIN_COUNT as domainCount, LAST_UPDATED as lastUpdated from MX_IP_STATUS where MX_IP=#{mxIp}")
	DBMxStatus checkMxIp(String mxIp);

	@Insert("insert into MX_IP_STATUS (MX_IP, STATUS, DOMAIN_COUNT, LAST_UPDATED) values (#{mxIp}, #{status}, 1, #{lastUpdated})")
	int insertMxIp(String mxIp, String status, long lastUpdated);

	@Update("update MX_IP_STATUS set STATUS=#{status}, DOMAIN_COUNT=DOMAIN_COUNT+1, LAST_UPDATED=#{lastUpdated} where MX_IP=#{mxIp}")
	int updateMxIpStatus(String mxIp, String status, long lastUpdated);

	@Update("update MX_IP_STATUS set DOMAIN_COUNT=DOMAIN_COUNT+1, LAST_UPDATED=#{lastUpdated} where MX_IP=#{mxIp}")
	int incrementMxIpCount(String mxIp, long lastUpdated);

}
