package de.haumacher.phoneblock.location.model;

/**
 * Country description from the database at https://github.com/datasets/country-codes
 */
public interface Country extends de.haumacher.msgbuf.data.DataObject, de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.location.model.Country} instance.
	 */
	static de.haumacher.phoneblock.location.model.Country create() {
		return new de.haumacher.phoneblock.location.model.impl.Country_Impl();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.location.model.Country} type in JSON format. */
	String COUNTRY__TYPE = "Country";

	/** @see #getFIFA() */
	String FIFA__PROP = "FIFA";

	/** @see #getDialPrefixes() */
	String DIAL_PREFIXES__PROP = "Dial";

	/** @see #getISO31661Alpha3() */
	String ISO_3166_1_ALPHA_3__PROP = "ISO3166-1-Alpha-3";

	/** @see #getMARC() */
	String MARC__PROP = "MARC";

	/** @see #isIndependent() */
	String INDEPENDENT__PROP = "is_independent";

	/** @see #getISO31661Numeric() */
	String ISO_3166_1_NUMERIC__PROP = "ISO3166-1-numeric";

	/** @see #getGAUL() */
	String GAUL__PROP = "GAUL";

	/** @see #getFIPS() */
	String FIPS__PROP = "FIPS";

	/** @see #getWMO() */
	String WMO__PROP = "WMO";

	/** @see #getISO31661Alpha2() */
	String ISO_3166_1_ALPHA_2__PROP = "ISO3166-1-Alpha-2";

	/** @see #getITU() */
	String ITU__PROP = "ITU";

	/** @see #getIOC() */
	String IOC__PROP = "IOC";

	/** @see #getDS() */
	String DS__PROP = "DS";

	/** @see #getUNTERMSpanishFormal() */
	String UNTERM_SPANISH_FORMAL__PROP = "UNTERM Spanish Formal";

	/** @see #getGlobalCode() */
	String GLOBAL_CODE__PROP = "Global Code";

	/** @see #getIntermediateRegionCode() */
	String INTERMEDIATE_REGION_CODE__PROP = "Intermediate Region Code";

	/** @see #getOfficialNameFr() */
	String OFFICIAL_NAME_FR__PROP = "official_name_fr";

	/** @see #getUNTERMFrenchShort() */
	String UNTERM_FRENCH_SHORT__PROP = "UNTERM French Short";

	/** @see #getISO4217CurrencyName() */
	String ISO_4217_CURRENCY_NAME__PROP = "ISO4217-currency_name";

	/** @see #getUNTERMRussianFormal() */
	String UNTERM_RUSSIAN_FORMAL__PROP = "UNTERM Russian Formal";

	/** @see #getUNTERMEnglishShort() */
	String UNTERM_ENGLISH_SHORT__PROP = "UNTERM English Short";

	/** @see #getISO4217CurrencyAlphabeticCode() */
	String ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP = "ISO4217-currency_alphabetic_code";

	/** @see #getSmallIslandDevelopingStatesSIDS() */
	String SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP = "Small Island Developing States (SIDS)";

	/** @see #getUNTERMSpanishShort() */
	String UNTERM_SPANISH_SHORT__PROP = "UNTERM Spanish Short";

	/** @see #getISO4217CurrencyNumericCode() */
	String ISO_4217_CURRENCY_NUMERIC_CODE__PROP = "ISO4217-currency_numeric_code";

	/** @see #getUNTERMChineseFormal() */
	String UNTERM_CHINESE_FORMAL__PROP = "UNTERM Chinese Formal";

	/** @see #getUNTERMFrenchFormal() */
	String UNTERM_FRENCH_FORMAL__PROP = "UNTERM French Formal";

	/** @see #getUNTERMRussianShort() */
	String UNTERM_RUSSIAN_SHORT__PROP = "UNTERM Russian Short";

	/** @see #getM49() */
	String M_49__PROP = "M49";

	/** @see #getSubRegionCode() */
	String SUB_REGION_CODE__PROP = "Sub-region Code";

	/** @see #getRegionCode() */
	String REGION_CODE__PROP = "Region Code";

	/** @see #getOfficialNameAr() */
	String OFFICIAL_NAME_AR__PROP = "official_name_ar";

	/** @see #getISO4217CurrencyMinorUnit() */
	String ISO_4217_CURRENCY_MINOR_UNIT__PROP = "ISO4217-currency_minor_unit";

	/** @see #getUNTERMArabicFormal() */
	String UNTERM_ARABIC_FORMAL__PROP = "UNTERM Arabic Formal";

	/** @see #getUNTERMChineseShort() */
	String UNTERM_CHINESE_SHORT__PROP = "UNTERM Chinese Short";

	/** @see #getLandLockedDevelopingCountriesLLDC() */
	String LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP = "Land Locked Developing Countries (LLDC)";

	/** @see #getIntermediateRegionName() */
	String INTERMEDIATE_REGION_NAME__PROP = "Intermediate Region Name";

	/** @see #getOfficialNameEs() */
	String OFFICIAL_NAME_ES__PROP = "official_name_es";

	/** @see #getUNTERMEnglishFormal() */
	String UNTERM_ENGLISH_FORMAL__PROP = "UNTERM English Formal";

	/** @see #getOfficialNameCn() */
	String OFFICIAL_NAME_CN__PROP = "official_name_cn";

	/** @see #getOfficialNameEn() */
	String OFFICIAL_NAME_EN__PROP = "official_name_en";

	/** @see #getISO4217CurrencyCountryName() */
	String ISO_4217_CURRENCY_COUNTRY_NAME__PROP = "ISO4217-currency_country_name";

	/** @see #getLeastDevelopedCountriesLDC() */
	String LEAST_DEVELOPED_COUNTRIES_LDC__PROP = "Least Developed Countries (LDC)";

	/** @see #getRegionName() */
	String REGION_NAME__PROP = "Region Name";

	/** @see #getUNTERMArabicShort() */
	String UNTERM_ARABIC_SHORT__PROP = "UNTERM Arabic Short";

	/** @see #getSubRegionName() */
	String SUB_REGION_NAME__PROP = "Sub-region Name";

	/** @see #getOfficialNameRu() */
	String OFFICIAL_NAME_RU__PROP = "official_name_ru";

	/** @see #getGlobalName() */
	String GLOBAL_NAME__PROP = "Global Name";

	/** @see #getCapital() */
	String CAPITAL__PROP = "Capital";

	/** @see #getContinent() */
	String CONTINENT__PROP = "Continent";

	/** @see #getTLD() */
	String TLD__PROP = "TLD";

	/** @see #getLanguages() */
	String LANGUAGES__PROP = "Languages";

	/** @see #getGeonameID() */
	String GEONAME_ID__PROP = "Geoname ID";

	/** @see #getCLDRDisplayName() */
	String CLDR_DISPLAY_NAME__PROP = "CLDR display name";

	/** @see #getEDGAR() */
	String EDGAR__PROP = "EDGAR";

	/** @see #getWikidataId() */
	String WIKIDATA_ID__PROP = "wikidata_id";

	/** @see #getTrunkPrefixes() */
	String TRUNK_PREFIXES__PROP = "TrunkPrefix";

	/** @see #getInternationalPrefixes() */
	String INTERNATIONAL_PREFIXES__PROP = "InternationalPrefix";

	/** Identifier for the property {@link #getFIFA()} in binary format. */
	static final int FIFA__ID = 1;

	/** Identifier for the property {@link #getDialPrefixes()} in binary format. */
	static final int DIAL_PREFIXES__ID = 2;

	/** Identifier for the property {@link #getISO31661Alpha3()} in binary format. */
	static final int ISO_3166_1_ALPHA_3__ID = 3;

	/** Identifier for the property {@link #getMARC()} in binary format. */
	static final int MARC__ID = 4;

	/** Identifier for the property {@link #isIndependent()} in binary format. */
	static final int INDEPENDENT__ID = 5;

	/** Identifier for the property {@link #getISO31661Numeric()} in binary format. */
	static final int ISO_3166_1_NUMERIC__ID = 6;

	/** Identifier for the property {@link #getGAUL()} in binary format. */
	static final int GAUL__ID = 7;

	/** Identifier for the property {@link #getFIPS()} in binary format. */
	static final int FIPS__ID = 8;

	/** Identifier for the property {@link #getWMO()} in binary format. */
	static final int WMO__ID = 9;

	/** Identifier for the property {@link #getISO31661Alpha2()} in binary format. */
	static final int ISO_3166_1_ALPHA_2__ID = 10;

	/** Identifier for the property {@link #getITU()} in binary format. */
	static final int ITU__ID = 11;

	/** Identifier for the property {@link #getIOC()} in binary format. */
	static final int IOC__ID = 12;

	/** Identifier for the property {@link #getDS()} in binary format. */
	static final int DS__ID = 13;

	/** Identifier for the property {@link #getUNTERMSpanishFormal()} in binary format. */
	static final int UNTERM_SPANISH_FORMAL__ID = 14;

	/** Identifier for the property {@link #getGlobalCode()} in binary format. */
	static final int GLOBAL_CODE__ID = 15;

	/** Identifier for the property {@link #getIntermediateRegionCode()} in binary format. */
	static final int INTERMEDIATE_REGION_CODE__ID = 16;

	/** Identifier for the property {@link #getOfficialNameFr()} in binary format. */
	static final int OFFICIAL_NAME_FR__ID = 17;

	/** Identifier for the property {@link #getUNTERMFrenchShort()} in binary format. */
	static final int UNTERM_FRENCH_SHORT__ID = 18;

	/** Identifier for the property {@link #getISO4217CurrencyName()} in binary format. */
	static final int ISO_4217_CURRENCY_NAME__ID = 19;

	/** Identifier for the property {@link #getUNTERMRussianFormal()} in binary format. */
	static final int UNTERM_RUSSIAN_FORMAL__ID = 20;

	/** Identifier for the property {@link #getUNTERMEnglishShort()} in binary format. */
	static final int UNTERM_ENGLISH_SHORT__ID = 21;

	/** Identifier for the property {@link #getISO4217CurrencyAlphabeticCode()} in binary format. */
	static final int ISO_4217_CURRENCY_ALPHABETIC_CODE__ID = 22;

	/** Identifier for the property {@link #getSmallIslandDevelopingStatesSIDS()} in binary format. */
	static final int SMALL_ISLAND_DEVELOPING_STATES_SIDS__ID = 23;

	/** Identifier for the property {@link #getUNTERMSpanishShort()} in binary format. */
	static final int UNTERM_SPANISH_SHORT__ID = 24;

	/** Identifier for the property {@link #getISO4217CurrencyNumericCode()} in binary format. */
	static final int ISO_4217_CURRENCY_NUMERIC_CODE__ID = 25;

	/** Identifier for the property {@link #getUNTERMChineseFormal()} in binary format. */
	static final int UNTERM_CHINESE_FORMAL__ID = 26;

	/** Identifier for the property {@link #getUNTERMFrenchFormal()} in binary format. */
	static final int UNTERM_FRENCH_FORMAL__ID = 27;

	/** Identifier for the property {@link #getUNTERMRussianShort()} in binary format. */
	static final int UNTERM_RUSSIAN_SHORT__ID = 28;

	/** Identifier for the property {@link #getM49()} in binary format. */
	static final int M_49__ID = 29;

	/** Identifier for the property {@link #getSubRegionCode()} in binary format. */
	static final int SUB_REGION_CODE__ID = 30;

	/** Identifier for the property {@link #getRegionCode()} in binary format. */
	static final int REGION_CODE__ID = 31;

	/** Identifier for the property {@link #getOfficialNameAr()} in binary format. */
	static final int OFFICIAL_NAME_AR__ID = 32;

	/** Identifier for the property {@link #getISO4217CurrencyMinorUnit()} in binary format. */
	static final int ISO_4217_CURRENCY_MINOR_UNIT__ID = 33;

	/** Identifier for the property {@link #getUNTERMArabicFormal()} in binary format. */
	static final int UNTERM_ARABIC_FORMAL__ID = 34;

	/** Identifier for the property {@link #getUNTERMChineseShort()} in binary format. */
	static final int UNTERM_CHINESE_SHORT__ID = 35;

	/** Identifier for the property {@link #getLandLockedDevelopingCountriesLLDC()} in binary format. */
	static final int LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__ID = 36;

	/** Identifier for the property {@link #getIntermediateRegionName()} in binary format. */
	static final int INTERMEDIATE_REGION_NAME__ID = 37;

	/** Identifier for the property {@link #getOfficialNameEs()} in binary format. */
	static final int OFFICIAL_NAME_ES__ID = 38;

	/** Identifier for the property {@link #getUNTERMEnglishFormal()} in binary format. */
	static final int UNTERM_ENGLISH_FORMAL__ID = 39;

	/** Identifier for the property {@link #getOfficialNameCn()} in binary format. */
	static final int OFFICIAL_NAME_CN__ID = 40;

	/** Identifier for the property {@link #getOfficialNameEn()} in binary format. */
	static final int OFFICIAL_NAME_EN__ID = 41;

	/** Identifier for the property {@link #getISO4217CurrencyCountryName()} in binary format. */
	static final int ISO_4217_CURRENCY_COUNTRY_NAME__ID = 42;

	/** Identifier for the property {@link #getLeastDevelopedCountriesLDC()} in binary format. */
	static final int LEAST_DEVELOPED_COUNTRIES_LDC__ID = 43;

	/** Identifier for the property {@link #getRegionName()} in binary format. */
	static final int REGION_NAME__ID = 44;

	/** Identifier for the property {@link #getUNTERMArabicShort()} in binary format. */
	static final int UNTERM_ARABIC_SHORT__ID = 45;

	/** Identifier for the property {@link #getSubRegionName()} in binary format. */
	static final int SUB_REGION_NAME__ID = 46;

	/** Identifier for the property {@link #getOfficialNameRu()} in binary format. */
	static final int OFFICIAL_NAME_RU__ID = 47;

	/** Identifier for the property {@link #getGlobalName()} in binary format. */
	static final int GLOBAL_NAME__ID = 48;

	/** Identifier for the property {@link #getCapital()} in binary format. */
	static final int CAPITAL__ID = 49;

	/** Identifier for the property {@link #getContinent()} in binary format. */
	static final int CONTINENT__ID = 50;

	/** Identifier for the property {@link #getTLD()} in binary format. */
	static final int TLD__ID = 51;

	/** Identifier for the property {@link #getLanguages()} in binary format. */
	static final int LANGUAGES__ID = 52;

	/** Identifier for the property {@link #getGeonameID()} in binary format. */
	static final int GEONAME_ID__ID = 53;

	/** Identifier for the property {@link #getCLDRDisplayName()} in binary format. */
	static final int CLDR_DISPLAY_NAME__ID = 54;

	/** Identifier for the property {@link #getEDGAR()} in binary format. */
	static final int EDGAR__ID = 55;

	/** Identifier for the property {@link #getWikidataId()} in binary format. */
	static final int WIKIDATA_ID__ID = 56;

	/** Identifier for the property {@link #getTrunkPrefixes()} in binary format. */
	static final int TRUNK_PREFIXES__ID = 57;

	/** Identifier for the property {@link #getInternationalPrefixes()} in binary format. */
	static final int INTERNATIONAL_PREFIXES__ID = 58;

	String getFIFA();

	/**
	 * @see #getFIFA()
	 */
	de.haumacher.phoneblock.location.model.Country setFIFA(String value);

	java.util.List<String> getDialPrefixes();

	/**
	 * @see #getDialPrefixes()
	 */
	de.haumacher.phoneblock.location.model.Country setDialPrefixes(java.util.List<? extends String> value);

	/**
	 * Adds a value to the {@link #getDialPrefixes()} list.
	 */
	de.haumacher.phoneblock.location.model.Country addDialPrefixe(String value);

	/**
	 * Removes a value from the {@link #getDialPrefixes()} list.
	 */
	void removeDialPrefixe(String value);

	String getISO31661Alpha3();

	/**
	 * @see #getISO31661Alpha3()
	 */
	de.haumacher.phoneblock.location.model.Country setISO31661Alpha3(String value);

	String getMARC();

	/**
	 * @see #getMARC()
	 */
	de.haumacher.phoneblock.location.model.Country setMARC(String value);

	boolean isIndependent();

	/**
	 * @see #isIndependent()
	 */
	de.haumacher.phoneblock.location.model.Country setIndependent(boolean value);

	String getISO31661Numeric();

	/**
	 * @see #getISO31661Numeric()
	 */
	de.haumacher.phoneblock.location.model.Country setISO31661Numeric(String value);

	String getGAUL();

	/**
	 * @see #getGAUL()
	 */
	de.haumacher.phoneblock.location.model.Country setGAUL(String value);

	String getFIPS();

	/**
	 * @see #getFIPS()
	 */
	de.haumacher.phoneblock.location.model.Country setFIPS(String value);

	String getWMO();

	/**
	 * @see #getWMO()
	 */
	de.haumacher.phoneblock.location.model.Country setWMO(String value);

	String getISO31661Alpha2();

	/**
	 * @see #getISO31661Alpha2()
	 */
	de.haumacher.phoneblock.location.model.Country setISO31661Alpha2(String value);

	String getITU();

	/**
	 * @see #getITU()
	 */
	de.haumacher.phoneblock.location.model.Country setITU(String value);

	String getIOC();

	/**
	 * @see #getIOC()
	 */
	de.haumacher.phoneblock.location.model.Country setIOC(String value);

	String getDS();

	/**
	 * @see #getDS()
	 */
	de.haumacher.phoneblock.location.model.Country setDS(String value);

	String getUNTERMSpanishFormal();

	/**
	 * @see #getUNTERMSpanishFormal()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMSpanishFormal(String value);

	String getGlobalCode();

	/**
	 * @see #getGlobalCode()
	 */
	de.haumacher.phoneblock.location.model.Country setGlobalCode(String value);

	String getIntermediateRegionCode();

	/**
	 * @see #getIntermediateRegionCode()
	 */
	de.haumacher.phoneblock.location.model.Country setIntermediateRegionCode(String value);

	String getOfficialNameFr();

	/**
	 * @see #getOfficialNameFr()
	 */
	de.haumacher.phoneblock.location.model.Country setOfficialNameFr(String value);

	String getUNTERMFrenchShort();

	/**
	 * @see #getUNTERMFrenchShort()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMFrenchShort(String value);

	String getISO4217CurrencyName();

	/**
	 * @see #getISO4217CurrencyName()
	 */
	de.haumacher.phoneblock.location.model.Country setISO4217CurrencyName(String value);

	String getUNTERMRussianFormal();

	/**
	 * @see #getUNTERMRussianFormal()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMRussianFormal(String value);

	String getUNTERMEnglishShort();

	/**
	 * @see #getUNTERMEnglishShort()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMEnglishShort(String value);

	String getISO4217CurrencyAlphabeticCode();

	/**
	 * @see #getISO4217CurrencyAlphabeticCode()
	 */
	de.haumacher.phoneblock.location.model.Country setISO4217CurrencyAlphabeticCode(String value);

	String getSmallIslandDevelopingStatesSIDS();

	/**
	 * @see #getSmallIslandDevelopingStatesSIDS()
	 */
	de.haumacher.phoneblock.location.model.Country setSmallIslandDevelopingStatesSIDS(String value);

	String getUNTERMSpanishShort();

	/**
	 * @see #getUNTERMSpanishShort()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMSpanishShort(String value);

	String getISO4217CurrencyNumericCode();

	/**
	 * @see #getISO4217CurrencyNumericCode()
	 */
	de.haumacher.phoneblock.location.model.Country setISO4217CurrencyNumericCode(String value);

	String getUNTERMChineseFormal();

	/**
	 * @see #getUNTERMChineseFormal()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMChineseFormal(String value);

	String getUNTERMFrenchFormal();

	/**
	 * @see #getUNTERMFrenchFormal()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMFrenchFormal(String value);

	String getUNTERMRussianShort();

	/**
	 * @see #getUNTERMRussianShort()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMRussianShort(String value);

	String getM49();

	/**
	 * @see #getM49()
	 */
	de.haumacher.phoneblock.location.model.Country setM49(String value);

	String getSubRegionCode();

	/**
	 * @see #getSubRegionCode()
	 */
	de.haumacher.phoneblock.location.model.Country setSubRegionCode(String value);

	String getRegionCode();

	/**
	 * @see #getRegionCode()
	 */
	de.haumacher.phoneblock.location.model.Country setRegionCode(String value);

	String getOfficialNameAr();

	/**
	 * @see #getOfficialNameAr()
	 */
	de.haumacher.phoneblock.location.model.Country setOfficialNameAr(String value);

	String getISO4217CurrencyMinorUnit();

	/**
	 * @see #getISO4217CurrencyMinorUnit()
	 */
	de.haumacher.phoneblock.location.model.Country setISO4217CurrencyMinorUnit(String value);

	String getUNTERMArabicFormal();

	/**
	 * @see #getUNTERMArabicFormal()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMArabicFormal(String value);

	String getUNTERMChineseShort();

	/**
	 * @see #getUNTERMChineseShort()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMChineseShort(String value);

	String getLandLockedDevelopingCountriesLLDC();

	/**
	 * @see #getLandLockedDevelopingCountriesLLDC()
	 */
	de.haumacher.phoneblock.location.model.Country setLandLockedDevelopingCountriesLLDC(String value);

	String getIntermediateRegionName();

	/**
	 * @see #getIntermediateRegionName()
	 */
	de.haumacher.phoneblock.location.model.Country setIntermediateRegionName(String value);

	String getOfficialNameEs();

	/**
	 * @see #getOfficialNameEs()
	 */
	de.haumacher.phoneblock.location.model.Country setOfficialNameEs(String value);

	String getUNTERMEnglishFormal();

	/**
	 * @see #getUNTERMEnglishFormal()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMEnglishFormal(String value);

	String getOfficialNameCn();

	/**
	 * @see #getOfficialNameCn()
	 */
	de.haumacher.phoneblock.location.model.Country setOfficialNameCn(String value);

	String getOfficialNameEn();

	/**
	 * @see #getOfficialNameEn()
	 */
	de.haumacher.phoneblock.location.model.Country setOfficialNameEn(String value);

	String getISO4217CurrencyCountryName();

	/**
	 * @see #getISO4217CurrencyCountryName()
	 */
	de.haumacher.phoneblock.location.model.Country setISO4217CurrencyCountryName(String value);

	String getLeastDevelopedCountriesLDC();

	/**
	 * @see #getLeastDevelopedCountriesLDC()
	 */
	de.haumacher.phoneblock.location.model.Country setLeastDevelopedCountriesLDC(String value);

	String getRegionName();

	/**
	 * @see #getRegionName()
	 */
	de.haumacher.phoneblock.location.model.Country setRegionName(String value);

	String getUNTERMArabicShort();

	/**
	 * @see #getUNTERMArabicShort()
	 */
	de.haumacher.phoneblock.location.model.Country setUNTERMArabicShort(String value);

	String getSubRegionName();

	/**
	 * @see #getSubRegionName()
	 */
	de.haumacher.phoneblock.location.model.Country setSubRegionName(String value);

	String getOfficialNameRu();

	/**
	 * @see #getOfficialNameRu()
	 */
	de.haumacher.phoneblock.location.model.Country setOfficialNameRu(String value);

	String getGlobalName();

	/**
	 * @see #getGlobalName()
	 */
	de.haumacher.phoneblock.location.model.Country setGlobalName(String value);

	String getCapital();

	/**
	 * @see #getCapital()
	 */
	de.haumacher.phoneblock.location.model.Country setCapital(String value);

	String getContinent();

	/**
	 * @see #getContinent()
	 */
	de.haumacher.phoneblock.location.model.Country setContinent(String value);

	String getTLD();

	/**
	 * @see #getTLD()
	 */
	de.haumacher.phoneblock.location.model.Country setTLD(String value);

	java.util.List<String> getLanguages();

	/**
	 * @see #getLanguages()
	 */
	de.haumacher.phoneblock.location.model.Country setLanguages(java.util.List<? extends String> value);

	/**
	 * Adds a value to the {@link #getLanguages()} list.
	 */
	de.haumacher.phoneblock.location.model.Country addLanguage(String value);

	/**
	 * Removes a value from the {@link #getLanguages()} list.
	 */
	void removeLanguage(String value);

	String getGeonameID();

	/**
	 * @see #getGeonameID()
	 */
	de.haumacher.phoneblock.location.model.Country setGeonameID(String value);

	String getCLDRDisplayName();

	/**
	 * @see #getCLDRDisplayName()
	 */
	de.haumacher.phoneblock.location.model.Country setCLDRDisplayName(String value);

	String getEDGAR();

	/**
	 * @see #getEDGAR()
	 */
	de.haumacher.phoneblock.location.model.Country setEDGAR(String value);

	String getWikidataId();

	/**
	 * @see #getWikidataId()
	 */
	de.haumacher.phoneblock.location.model.Country setWikidataId(String value);

	java.util.List<String> getTrunkPrefixes();

	/**
	 * @see #getTrunkPrefixes()
	 */
	de.haumacher.phoneblock.location.model.Country setTrunkPrefixes(java.util.List<? extends String> value);

	/**
	 * Adds a value to the {@link #getTrunkPrefixes()} list.
	 */
	de.haumacher.phoneblock.location.model.Country addTrunkPrefixe(String value);

	/**
	 * Removes a value from the {@link #getTrunkPrefixes()} list.
	 */
	void removeTrunkPrefixe(String value);

	java.util.List<String> getInternationalPrefixes();

	/**
	 * @see #getInternationalPrefixes()
	 */
	de.haumacher.phoneblock.location.model.Country setInternationalPrefixes(java.util.List<? extends String> value);

	/**
	 * Adds a value to the {@link #getInternationalPrefixes()} list.
	 */
	de.haumacher.phoneblock.location.model.Country addInternationalPrefixe(String value);

	/**
	 * Removes a value from the {@link #getInternationalPrefixes()} list.
	 */
	void removeInternationalPrefixe(String value);

	@Override
	public de.haumacher.phoneblock.location.model.Country registerListener(de.haumacher.msgbuf.observer.Listener l);

	@Override
	public de.haumacher.phoneblock.location.model.Country unregisterListener(de.haumacher.msgbuf.observer.Listener l);

	/** Reads a new instance from the given reader. */
	static de.haumacher.phoneblock.location.model.Country readCountry(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.location.model.impl.Country_Impl result = new de.haumacher.phoneblock.location.model.impl.Country_Impl();
		result.readContent(in);
		return result;
	}

	/** Reads a new instance from the given reader. */
	static de.haumacher.phoneblock.location.model.Country readCountry(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.location.model.Country result = de.haumacher.phoneblock.location.model.impl.Country_Impl.readCountry_Content(in);
		in.endObject();
		return result;
	}

	/** Creates a new {@link Country} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static Country readCountry(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.location.model.impl.Country_Impl.readCountry_XmlContent(in);
	}

}
