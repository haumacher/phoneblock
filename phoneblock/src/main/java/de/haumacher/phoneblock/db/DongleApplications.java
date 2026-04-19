package de.haumacher.phoneblock.db;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for the DONGLE_APPLICATION table.
 */
public interface DongleApplications {

	@Insert("""
			INSERT INTO DONGLE_APPLICATION (
				USER_ID, CREATED,
				NAME, STREET, ZIP, CITY, COUNTRY,
				PROVIDER, PROVIDER_OTHER, CONNECTION_TYPE, ROUTER_KIND, ROUTER_MODEL, SPAM_FREQUENCY,
				SKILL_LEVEL, ALLOW_PUBLISH,
				NOTES,
				STATUS
			) VALUES (
				#{userId}, #{created},
				#{name}, #{street}, #{zip}, #{city}, #{country},
				#{provider}, #{providerOther}, #{connectionType}, #{routerKind}, #{routerModel}, #{spamFrequency},
				#{skillLevel}, #{allowPublish},
				#{notes},
				#{status}
			)
			""")
	@Options(useGeneratedKeys = true, keyColumn = "ID", keyProperty = "id")
	void insert(DBDongleApplication application);

	@Select("""
			SELECT
				ID AS id, USER_ID AS userId, CREATED AS created,
				NAME AS name, STREET AS street, ZIP AS zip, CITY AS city, COUNTRY AS country,
				PROVIDER AS provider, PROVIDER_OTHER AS providerOther,
				CONNECTION_TYPE AS connectionType, ROUTER_KIND AS routerKind, ROUTER_MODEL AS routerModel,
				SPAM_FREQUENCY AS spamFrequency,
				SKILL_LEVEL AS skillLevel, ALLOW_PUBLISH AS allowPublish,
				NOTES AS notes, STATUS AS status
			FROM DONGLE_APPLICATION
			WHERE USER_ID = #{userId}
			ORDER BY CREATED DESC
			""")
	List<DBDongleApplication> getByUser(long userId);

	@Select("SELECT COUNT(*) FROM DONGLE_APPLICATION WHERE USER_ID = #{userId}")
	int countByUser(long userId);
}
