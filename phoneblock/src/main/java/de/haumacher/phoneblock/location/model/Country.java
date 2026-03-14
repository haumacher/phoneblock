package de.haumacher.phoneblock.location.model;

/**
 * Country description from the database at https://github.com/datasets/country-codes
 */
public class Country extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.location.model.Country} instance.
	 */
	public static de.haumacher.phoneblock.location.model.Country create() {
		return new de.haumacher.phoneblock.location.model.Country();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.location.model.Country} type in JSON format. */
	public static final String COUNTRY__TYPE = "Country";

	/** @see #getFIFA() */
	public static final String FIFA__PROP = "FIFA";

	/** @see #getDialPrefixes() */
	public static final String DIAL_PREFIXES__PROP = "Dial";

	/** @see #getISO31661Alpha3() */
	public static final String ISO_3166_1_ALPHA_3__PROP = "ISO3166-1-Alpha-3";

	/** @see #getMARC() */
	public static final String MARC__PROP = "MARC";

	/** @see #isIndependent() */
	public static final String INDEPENDENT__PROP = "is_independent";

	/** @see #getISO31661Numeric() */
	public static final String ISO_3166_1_NUMERIC__PROP = "ISO3166-1-numeric";

	/** @see #getGAUL() */
	public static final String GAUL__PROP = "GAUL";

	/** @see #getFIPS() */
	public static final String FIPS__PROP = "FIPS";

	/** @see #getWMO() */
	public static final String WMO__PROP = "WMO";

	/** @see #getISO31661Alpha2() */
	public static final String ISO_3166_1_ALPHA_2__PROP = "ISO3166-1-Alpha-2";

	/** @see #getITU() */
	public static final String ITU__PROP = "ITU";

	/** @see #getIOC() */
	public static final String IOC__PROP = "IOC";

	/** @see #getDS() */
	public static final String DS__PROP = "DS";

	/** @see #getUNTERMSpanishFormal() */
	public static final String UNTERM_SPANISH_FORMAL__PROP = "UNTERM Spanish Formal";

	/** @see #getGlobalCode() */
	public static final String GLOBAL_CODE__PROP = "Global Code";

	/** @see #getIntermediateRegionCode() */
	public static final String INTERMEDIATE_REGION_CODE__PROP = "Intermediate Region Code";

	/** @see #getOfficialNameFr() */
	public static final String OFFICIAL_NAME_FR__PROP = "official_name_fr";

	/** @see #getUNTERMFrenchShort() */
	public static final String UNTERM_FRENCH_SHORT__PROP = "UNTERM French Short";

	/** @see #getISO4217CurrencyName() */
	public static final String ISO_4217_CURRENCY_NAME__PROP = "ISO4217-currency_name";

	/** @see #getUNTERMRussianFormal() */
	public static final String UNTERM_RUSSIAN_FORMAL__PROP = "UNTERM Russian Formal";

	/** @see #getUNTERMEnglishShort() */
	public static final String UNTERM_ENGLISH_SHORT__PROP = "UNTERM English Short";

	/** @see #getISO4217CurrencyAlphabeticCode() */
	public static final String ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP = "ISO4217-currency_alphabetic_code";

	/** @see #getSmallIslandDevelopingStatesSIDS() */
	public static final String SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP = "Small Island Developing States (SIDS)";

	/** @see #getUNTERMSpanishShort() */
	public static final String UNTERM_SPANISH_SHORT__PROP = "UNTERM Spanish Short";

	/** @see #getISO4217CurrencyNumericCode() */
	public static final String ISO_4217_CURRENCY_NUMERIC_CODE__PROP = "ISO4217-currency_numeric_code";

	/** @see #getUNTERMChineseFormal() */
	public static final String UNTERM_CHINESE_FORMAL__PROP = "UNTERM Chinese Formal";

	/** @see #getUNTERMFrenchFormal() */
	public static final String UNTERM_FRENCH_FORMAL__PROP = "UNTERM French Formal";

	/** @see #getUNTERMRussianShort() */
	public static final String UNTERM_RUSSIAN_SHORT__PROP = "UNTERM Russian Short";

	/** @see #getM49() */
	public static final String M_49__PROP = "M49";

	/** @see #getSubRegionCode() */
	public static final String SUB_REGION_CODE__PROP = "Sub-region Code";

	/** @see #getRegionCode() */
	public static final String REGION_CODE__PROP = "Region Code";

	/** @see #getOfficialNameAr() */
	public static final String OFFICIAL_NAME_AR__PROP = "official_name_ar";

	/** @see #getISO4217CurrencyMinorUnit() */
	public static final String ISO_4217_CURRENCY_MINOR_UNIT__PROP = "ISO4217-currency_minor_unit";

	/** @see #getUNTERMArabicFormal() */
	public static final String UNTERM_ARABIC_FORMAL__PROP = "UNTERM Arabic Formal";

	/** @see #getUNTERMChineseShort() */
	public static final String UNTERM_CHINESE_SHORT__PROP = "UNTERM Chinese Short";

	/** @see #getLandLockedDevelopingCountriesLLDC() */
	public static final String LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP = "Land Locked Developing Countries (LLDC)";

	/** @see #getIntermediateRegionName() */
	public static final String INTERMEDIATE_REGION_NAME__PROP = "Intermediate Region Name";

	/** @see #getOfficialNameEs() */
	public static final String OFFICIAL_NAME_ES__PROP = "official_name_es";

	/** @see #getUNTERMEnglishFormal() */
	public static final String UNTERM_ENGLISH_FORMAL__PROP = "UNTERM English Formal";

	/** @see #getOfficialNameCn() */
	public static final String OFFICIAL_NAME_CN__PROP = "official_name_cn";

	/** @see #getOfficialNameEn() */
	public static final String OFFICIAL_NAME_EN__PROP = "official_name_en";

	/** @see #getISO4217CurrencyCountryName() */
	public static final String ISO_4217_CURRENCY_COUNTRY_NAME__PROP = "ISO4217-currency_country_name";

	/** @see #getLeastDevelopedCountriesLDC() */
	public static final String LEAST_DEVELOPED_COUNTRIES_LDC__PROP = "Least Developed Countries (LDC)";

	/** @see #getRegionName() */
	public static final String REGION_NAME__PROP = "Region Name";

	/** @see #getUNTERMArabicShort() */
	public static final String UNTERM_ARABIC_SHORT__PROP = "UNTERM Arabic Short";

	/** @see #getSubRegionName() */
	public static final String SUB_REGION_NAME__PROP = "Sub-region Name";

	/** @see #getOfficialNameRu() */
	public static final String OFFICIAL_NAME_RU__PROP = "official_name_ru";

	/** @see #getGlobalName() */
	public static final String GLOBAL_NAME__PROP = "Global Name";

	/** @see #getCapital() */
	public static final String CAPITAL__PROP = "Capital";

	/** @see #getContinent() */
	public static final String CONTINENT__PROP = "Continent";

	/** @see #getTLD() */
	public static final String TLD__PROP = "TLD";

	/** @see #getLanguages() */
	public static final String LANGUAGES__PROP = "Languages";

	/** @see #getGeonameID() */
	public static final String GEONAME_ID__PROP = "Geoname ID";

	/** @see #getCLDRDisplayName() */
	public static final String CLDR_DISPLAY_NAME__PROP = "CLDR display name";

	/** @see #getEDGAR() */
	public static final String EDGAR__PROP = "EDGAR";

	/** @see #getWikidataId() */
	public static final String WIKIDATA_ID__PROP = "wikidata_id";

	/** @see #getTrunkPrefixes() */
	public static final String TRUNK_PREFIXES__PROP = "TrunkPrefix";

	/** @see #getInternationalPrefixes() */
	public static final String INTERNATIONAL_PREFIXES__PROP = "InternationalPrefix";

	private String _fIFA = "";

	private final java.util.List<String> _dialPrefixes = new java.util.ArrayList<>();

	private String _iSO31661Alpha3 = "";

	private String _mARC = "";

	private boolean _independent = false;

	private String _iSO31661Numeric = "";

	private String _gAUL = "";

	private String _fIPS = "";

	private String _wMO = "";

	private String _iSO31661Alpha2 = "";

	private String _iTU = "";

	private String _iOC = "";

	private String _dS = "";

	private String _uNTERMSpanishFormal = "";

	private String _globalCode = "";

	private String _intermediateRegionCode = "";

	private String _officialNameFr = "";

	private String _uNTERMFrenchShort = "";

	private String _iSO4217CurrencyName = "";

	private String _uNTERMRussianFormal = "";

	private String _uNTERMEnglishShort = "";

	private String _iSO4217CurrencyAlphabeticCode = "";

	private String _smallIslandDevelopingStatesSIDS = "";

	private String _uNTERMSpanishShort = "";

	private String _iSO4217CurrencyNumericCode = "";

	private String _uNTERMChineseFormal = "";

	private String _uNTERMFrenchFormal = "";

	private String _uNTERMRussianShort = "";

	private String _m49 = "";

	private String _subRegionCode = "";

	private String _regionCode = "";

	private String _officialNameAr = "";

	private String _iSO4217CurrencyMinorUnit = "";

	private String _uNTERMArabicFormal = "";

	private String _uNTERMChineseShort = "";

	private String _landLockedDevelopingCountriesLLDC = "";

	private String _intermediateRegionName = "";

	private String _officialNameEs = "";

	private String _uNTERMEnglishFormal = "";

	private String _officialNameCn = "";

	private String _officialNameEn = "";

	private String _iSO4217CurrencyCountryName = "";

	private String _leastDevelopedCountriesLDC = "";

	private String _regionName = "";

	private String _uNTERMArabicShort = "";

	private String _subRegionName = "";

	private String _officialNameRu = "";

	private String _globalName = "";

	private String _capital = "";

	private String _continent = "";

	private String _tLD = "";

	private final java.util.List<String> _languages = new java.util.ArrayList<>();

	private String _geonameID = "";

	private String _cLDRDisplayName = "";

	private String _eDGAR = "";

	private String _wikidataId = "";

	private final java.util.List<String> _trunkPrefixes = new java.util.ArrayList<>();

	private final java.util.List<String> _internationalPrefixes = new java.util.ArrayList<>();

	/**
	 * Creates a {@link Country} instance.
	 *
	 * @see de.haumacher.phoneblock.location.model.Country#create()
	 */
	protected Country() {
		super();
	}

	public final String getFIFA() {
		return _fIFA;
	}

	/**
	 * @see #getFIFA()
	 */
	public de.haumacher.phoneblock.location.model.Country setFIFA(String value) {
		internalSetFIFA(value);
		return this;
	}

	/** Internal setter for {@link #getFIFA()} without chain call utility. */
	protected final void internalSetFIFA(String value) {
		_fIFA = value;
	}

	public final java.util.List<String> getDialPrefixes() {
		return _dialPrefixes;
	}

	/**
	 * @see #getDialPrefixes()
	 */
	public de.haumacher.phoneblock.location.model.Country setDialPrefixes(java.util.List<? extends String> value) {
		internalSetDialPrefixes(value);
		return this;
	}

	/** Internal setter for {@link #getDialPrefixes()} without chain call utility. */
	protected final void internalSetDialPrefixes(java.util.List<? extends String> value) {
		_dialPrefixes.clear();
		_dialPrefixes.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getDialPrefixes()} list.
	 */
	public de.haumacher.phoneblock.location.model.Country addDialPrefix(String value) {
		internalAddDialPrefix(value);
		return this;
	}

	/** Implementation of {@link #addDialPrefix(String)} without chain call utility. */
	protected final void internalAddDialPrefix(String value) {
		_dialPrefixes.add(value);
	}

	/**
	 * Removes a value from the {@link #getDialPrefixes()} list.
	 */
	public final void removeDialPrefix(String value) {
		_dialPrefixes.remove(value);
	}

	public final String getISO31661Alpha3() {
		return _iSO31661Alpha3;
	}

	/**
	 * @see #getISO31661Alpha3()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO31661Alpha3(String value) {
		internalSetISO31661Alpha3(value);
		return this;
	}

	/** Internal setter for {@link #getISO31661Alpha3()} without chain call utility. */
	protected final void internalSetISO31661Alpha3(String value) {
		_iSO31661Alpha3 = value;
	}

	public final String getMARC() {
		return _mARC;
	}

	/**
	 * @see #getMARC()
	 */
	public de.haumacher.phoneblock.location.model.Country setMARC(String value) {
		internalSetMARC(value);
		return this;
	}

	/** Internal setter for {@link #getMARC()} without chain call utility. */
	protected final void internalSetMARC(String value) {
		_mARC = value;
	}

	public final boolean isIndependent() {
		return _independent;
	}

	/**
	 * @see #isIndependent()
	 */
	public de.haumacher.phoneblock.location.model.Country setIndependent(boolean value) {
		internalSetIndependent(value);
		return this;
	}

	/** Internal setter for {@link #isIndependent()} without chain call utility. */
	protected final void internalSetIndependent(boolean value) {
		_independent = value;
	}

	public final String getISO31661Numeric() {
		return _iSO31661Numeric;
	}

	/**
	 * @see #getISO31661Numeric()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO31661Numeric(String value) {
		internalSetISO31661Numeric(value);
		return this;
	}

	/** Internal setter for {@link #getISO31661Numeric()} without chain call utility. */
	protected final void internalSetISO31661Numeric(String value) {
		_iSO31661Numeric = value;
	}

	public final String getGAUL() {
		return _gAUL;
	}

	/**
	 * @see #getGAUL()
	 */
	public de.haumacher.phoneblock.location.model.Country setGAUL(String value) {
		internalSetGAUL(value);
		return this;
	}

	/** Internal setter for {@link #getGAUL()} without chain call utility. */
	protected final void internalSetGAUL(String value) {
		_gAUL = value;
	}

	public final String getFIPS() {
		return _fIPS;
	}

	/**
	 * @see #getFIPS()
	 */
	public de.haumacher.phoneblock.location.model.Country setFIPS(String value) {
		internalSetFIPS(value);
		return this;
	}

	/** Internal setter for {@link #getFIPS()} without chain call utility. */
	protected final void internalSetFIPS(String value) {
		_fIPS = value;
	}

	public final String getWMO() {
		return _wMO;
	}

	/**
	 * @see #getWMO()
	 */
	public de.haumacher.phoneblock.location.model.Country setWMO(String value) {
		internalSetWMO(value);
		return this;
	}

	/** Internal setter for {@link #getWMO()} without chain call utility. */
	protected final void internalSetWMO(String value) {
		_wMO = value;
	}

	public final String getISO31661Alpha2() {
		return _iSO31661Alpha2;
	}

	/**
	 * @see #getISO31661Alpha2()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO31661Alpha2(String value) {
		internalSetISO31661Alpha2(value);
		return this;
	}

	/** Internal setter for {@link #getISO31661Alpha2()} without chain call utility. */
	protected final void internalSetISO31661Alpha2(String value) {
		_iSO31661Alpha2 = value;
	}

	public final String getITU() {
		return _iTU;
	}

	/**
	 * @see #getITU()
	 */
	public de.haumacher.phoneblock.location.model.Country setITU(String value) {
		internalSetITU(value);
		return this;
	}

	/** Internal setter for {@link #getITU()} without chain call utility. */
	protected final void internalSetITU(String value) {
		_iTU = value;
	}

	public final String getIOC() {
		return _iOC;
	}

	/**
	 * @see #getIOC()
	 */
	public de.haumacher.phoneblock.location.model.Country setIOC(String value) {
		internalSetIOC(value);
		return this;
	}

	/** Internal setter for {@link #getIOC()} without chain call utility. */
	protected final void internalSetIOC(String value) {
		_iOC = value;
	}

	public final String getDS() {
		return _dS;
	}

	/**
	 * @see #getDS()
	 */
	public de.haumacher.phoneblock.location.model.Country setDS(String value) {
		internalSetDS(value);
		return this;
	}

	/** Internal setter for {@link #getDS()} without chain call utility. */
	protected final void internalSetDS(String value) {
		_dS = value;
	}

	public final String getUNTERMSpanishFormal() {
		return _uNTERMSpanishFormal;
	}

	/**
	 * @see #getUNTERMSpanishFormal()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMSpanishFormal(String value) {
		internalSetUNTERMSpanishFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMSpanishFormal()} without chain call utility. */
	protected final void internalSetUNTERMSpanishFormal(String value) {
		_uNTERMSpanishFormal = value;
	}

	public final String getGlobalCode() {
		return _globalCode;
	}

	/**
	 * @see #getGlobalCode()
	 */
	public de.haumacher.phoneblock.location.model.Country setGlobalCode(String value) {
		internalSetGlobalCode(value);
		return this;
	}

	/** Internal setter for {@link #getGlobalCode()} without chain call utility. */
	protected final void internalSetGlobalCode(String value) {
		_globalCode = value;
	}

	public final String getIntermediateRegionCode() {
		return _intermediateRegionCode;
	}

	/**
	 * @see #getIntermediateRegionCode()
	 */
	public de.haumacher.phoneblock.location.model.Country setIntermediateRegionCode(String value) {
		internalSetIntermediateRegionCode(value);
		return this;
	}

	/** Internal setter for {@link #getIntermediateRegionCode()} without chain call utility. */
	protected final void internalSetIntermediateRegionCode(String value) {
		_intermediateRegionCode = value;
	}

	public final String getOfficialNameFr() {
		return _officialNameFr;
	}

	/**
	 * @see #getOfficialNameFr()
	 */
	public de.haumacher.phoneblock.location.model.Country setOfficialNameFr(String value) {
		internalSetOfficialNameFr(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameFr()} without chain call utility. */
	protected final void internalSetOfficialNameFr(String value) {
		_officialNameFr = value;
	}

	public final String getUNTERMFrenchShort() {
		return _uNTERMFrenchShort;
	}

	/**
	 * @see #getUNTERMFrenchShort()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMFrenchShort(String value) {
		internalSetUNTERMFrenchShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMFrenchShort()} without chain call utility. */
	protected final void internalSetUNTERMFrenchShort(String value) {
		_uNTERMFrenchShort = value;
	}

	public final String getISO4217CurrencyName() {
		return _iSO4217CurrencyName;
	}

	/**
	 * @see #getISO4217CurrencyName()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyName(String value) {
		internalSetISO4217CurrencyName(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyName()} without chain call utility. */
	protected final void internalSetISO4217CurrencyName(String value) {
		_iSO4217CurrencyName = value;
	}

	public final String getUNTERMRussianFormal() {
		return _uNTERMRussianFormal;
	}

	/**
	 * @see #getUNTERMRussianFormal()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMRussianFormal(String value) {
		internalSetUNTERMRussianFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMRussianFormal()} without chain call utility. */
	protected final void internalSetUNTERMRussianFormal(String value) {
		_uNTERMRussianFormal = value;
	}

	public final String getUNTERMEnglishShort() {
		return _uNTERMEnglishShort;
	}

	/**
	 * @see #getUNTERMEnglishShort()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMEnglishShort(String value) {
		internalSetUNTERMEnglishShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMEnglishShort()} without chain call utility. */
	protected final void internalSetUNTERMEnglishShort(String value) {
		_uNTERMEnglishShort = value;
	}

	public final String getISO4217CurrencyAlphabeticCode() {
		return _iSO4217CurrencyAlphabeticCode;
	}

	/**
	 * @see #getISO4217CurrencyAlphabeticCode()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyAlphabeticCode(String value) {
		internalSetISO4217CurrencyAlphabeticCode(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyAlphabeticCode()} without chain call utility. */
	protected final void internalSetISO4217CurrencyAlphabeticCode(String value) {
		_iSO4217CurrencyAlphabeticCode = value;
	}

	public final String getSmallIslandDevelopingStatesSIDS() {
		return _smallIslandDevelopingStatesSIDS;
	}

	/**
	 * @see #getSmallIslandDevelopingStatesSIDS()
	 */
	public de.haumacher.phoneblock.location.model.Country setSmallIslandDevelopingStatesSIDS(String value) {
		internalSetSmallIslandDevelopingStatesSIDS(value);
		return this;
	}

	/** Internal setter for {@link #getSmallIslandDevelopingStatesSIDS()} without chain call utility. */
	protected final void internalSetSmallIslandDevelopingStatesSIDS(String value) {
		_smallIslandDevelopingStatesSIDS = value;
	}

	public final String getUNTERMSpanishShort() {
		return _uNTERMSpanishShort;
	}

	/**
	 * @see #getUNTERMSpanishShort()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMSpanishShort(String value) {
		internalSetUNTERMSpanishShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMSpanishShort()} without chain call utility. */
	protected final void internalSetUNTERMSpanishShort(String value) {
		_uNTERMSpanishShort = value;
	}

	public final String getISO4217CurrencyNumericCode() {
		return _iSO4217CurrencyNumericCode;
	}

	/**
	 * @see #getISO4217CurrencyNumericCode()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyNumericCode(String value) {
		internalSetISO4217CurrencyNumericCode(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyNumericCode()} without chain call utility. */
	protected final void internalSetISO4217CurrencyNumericCode(String value) {
		_iSO4217CurrencyNumericCode = value;
	}

	public final String getUNTERMChineseFormal() {
		return _uNTERMChineseFormal;
	}

	/**
	 * @see #getUNTERMChineseFormal()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMChineseFormal(String value) {
		internalSetUNTERMChineseFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMChineseFormal()} without chain call utility. */
	protected final void internalSetUNTERMChineseFormal(String value) {
		_uNTERMChineseFormal = value;
	}

	public final String getUNTERMFrenchFormal() {
		return _uNTERMFrenchFormal;
	}

	/**
	 * @see #getUNTERMFrenchFormal()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMFrenchFormal(String value) {
		internalSetUNTERMFrenchFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMFrenchFormal()} without chain call utility. */
	protected final void internalSetUNTERMFrenchFormal(String value) {
		_uNTERMFrenchFormal = value;
	}

	public final String getUNTERMRussianShort() {
		return _uNTERMRussianShort;
	}

	/**
	 * @see #getUNTERMRussianShort()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMRussianShort(String value) {
		internalSetUNTERMRussianShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMRussianShort()} without chain call utility. */
	protected final void internalSetUNTERMRussianShort(String value) {
		_uNTERMRussianShort = value;
	}

	public final String getM49() {
		return _m49;
	}

	/**
	 * @see #getM49()
	 */
	public de.haumacher.phoneblock.location.model.Country setM49(String value) {
		internalSetM49(value);
		return this;
	}

	/** Internal setter for {@link #getM49()} without chain call utility. */
	protected final void internalSetM49(String value) {
		_m49 = value;
	}

	public final String getSubRegionCode() {
		return _subRegionCode;
	}

	/**
	 * @see #getSubRegionCode()
	 */
	public de.haumacher.phoneblock.location.model.Country setSubRegionCode(String value) {
		internalSetSubRegionCode(value);
		return this;
	}

	/** Internal setter for {@link #getSubRegionCode()} without chain call utility. */
	protected final void internalSetSubRegionCode(String value) {
		_subRegionCode = value;
	}

	public final String getRegionCode() {
		return _regionCode;
	}

	/**
	 * @see #getRegionCode()
	 */
	public de.haumacher.phoneblock.location.model.Country setRegionCode(String value) {
		internalSetRegionCode(value);
		return this;
	}

	/** Internal setter for {@link #getRegionCode()} without chain call utility. */
	protected final void internalSetRegionCode(String value) {
		_regionCode = value;
	}

	public final String getOfficialNameAr() {
		return _officialNameAr;
	}

	/**
	 * @see #getOfficialNameAr()
	 */
	public de.haumacher.phoneblock.location.model.Country setOfficialNameAr(String value) {
		internalSetOfficialNameAr(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameAr()} without chain call utility. */
	protected final void internalSetOfficialNameAr(String value) {
		_officialNameAr = value;
	}

	public final String getISO4217CurrencyMinorUnit() {
		return _iSO4217CurrencyMinorUnit;
	}

	/**
	 * @see #getISO4217CurrencyMinorUnit()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyMinorUnit(String value) {
		internalSetISO4217CurrencyMinorUnit(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyMinorUnit()} without chain call utility. */
	protected final void internalSetISO4217CurrencyMinorUnit(String value) {
		_iSO4217CurrencyMinorUnit = value;
	}

	public final String getUNTERMArabicFormal() {
		return _uNTERMArabicFormal;
	}

	/**
	 * @see #getUNTERMArabicFormal()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMArabicFormal(String value) {
		internalSetUNTERMArabicFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMArabicFormal()} without chain call utility. */
	protected final void internalSetUNTERMArabicFormal(String value) {
		_uNTERMArabicFormal = value;
	}

	public final String getUNTERMChineseShort() {
		return _uNTERMChineseShort;
	}

	/**
	 * @see #getUNTERMChineseShort()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMChineseShort(String value) {
		internalSetUNTERMChineseShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMChineseShort()} without chain call utility. */
	protected final void internalSetUNTERMChineseShort(String value) {
		_uNTERMChineseShort = value;
	}

	public final String getLandLockedDevelopingCountriesLLDC() {
		return _landLockedDevelopingCountriesLLDC;
	}

	/**
	 * @see #getLandLockedDevelopingCountriesLLDC()
	 */
	public de.haumacher.phoneblock.location.model.Country setLandLockedDevelopingCountriesLLDC(String value) {
		internalSetLandLockedDevelopingCountriesLLDC(value);
		return this;
	}

	/** Internal setter for {@link #getLandLockedDevelopingCountriesLLDC()} without chain call utility. */
	protected final void internalSetLandLockedDevelopingCountriesLLDC(String value) {
		_landLockedDevelopingCountriesLLDC = value;
	}

	public final String getIntermediateRegionName() {
		return _intermediateRegionName;
	}

	/**
	 * @see #getIntermediateRegionName()
	 */
	public de.haumacher.phoneblock.location.model.Country setIntermediateRegionName(String value) {
		internalSetIntermediateRegionName(value);
		return this;
	}

	/** Internal setter for {@link #getIntermediateRegionName()} without chain call utility. */
	protected final void internalSetIntermediateRegionName(String value) {
		_intermediateRegionName = value;
	}

	public final String getOfficialNameEs() {
		return _officialNameEs;
	}

	/**
	 * @see #getOfficialNameEs()
	 */
	public de.haumacher.phoneblock.location.model.Country setOfficialNameEs(String value) {
		internalSetOfficialNameEs(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameEs()} without chain call utility. */
	protected final void internalSetOfficialNameEs(String value) {
		_officialNameEs = value;
	}

	public final String getUNTERMEnglishFormal() {
		return _uNTERMEnglishFormal;
	}

	/**
	 * @see #getUNTERMEnglishFormal()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMEnglishFormal(String value) {
		internalSetUNTERMEnglishFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMEnglishFormal()} without chain call utility. */
	protected final void internalSetUNTERMEnglishFormal(String value) {
		_uNTERMEnglishFormal = value;
	}

	public final String getOfficialNameCn() {
		return _officialNameCn;
	}

	/**
	 * @see #getOfficialNameCn()
	 */
	public de.haumacher.phoneblock.location.model.Country setOfficialNameCn(String value) {
		internalSetOfficialNameCn(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameCn()} without chain call utility. */
	protected final void internalSetOfficialNameCn(String value) {
		_officialNameCn = value;
	}

	public final String getOfficialNameEn() {
		return _officialNameEn;
	}

	/**
	 * @see #getOfficialNameEn()
	 */
	public de.haumacher.phoneblock.location.model.Country setOfficialNameEn(String value) {
		internalSetOfficialNameEn(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameEn()} without chain call utility. */
	protected final void internalSetOfficialNameEn(String value) {
		_officialNameEn = value;
	}

	public final String getISO4217CurrencyCountryName() {
		return _iSO4217CurrencyCountryName;
	}

	/**
	 * @see #getISO4217CurrencyCountryName()
	 */
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyCountryName(String value) {
		internalSetISO4217CurrencyCountryName(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyCountryName()} without chain call utility. */
	protected final void internalSetISO4217CurrencyCountryName(String value) {
		_iSO4217CurrencyCountryName = value;
	}

	public final String getLeastDevelopedCountriesLDC() {
		return _leastDevelopedCountriesLDC;
	}

	/**
	 * @see #getLeastDevelopedCountriesLDC()
	 */
	public de.haumacher.phoneblock.location.model.Country setLeastDevelopedCountriesLDC(String value) {
		internalSetLeastDevelopedCountriesLDC(value);
		return this;
	}

	/** Internal setter for {@link #getLeastDevelopedCountriesLDC()} without chain call utility. */
	protected final void internalSetLeastDevelopedCountriesLDC(String value) {
		_leastDevelopedCountriesLDC = value;
	}

	public final String getRegionName() {
		return _regionName;
	}

	/**
	 * @see #getRegionName()
	 */
	public de.haumacher.phoneblock.location.model.Country setRegionName(String value) {
		internalSetRegionName(value);
		return this;
	}

	/** Internal setter for {@link #getRegionName()} without chain call utility. */
	protected final void internalSetRegionName(String value) {
		_regionName = value;
	}

	public final String getUNTERMArabicShort() {
		return _uNTERMArabicShort;
	}

	/**
	 * @see #getUNTERMArabicShort()
	 */
	public de.haumacher.phoneblock.location.model.Country setUNTERMArabicShort(String value) {
		internalSetUNTERMArabicShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMArabicShort()} without chain call utility. */
	protected final void internalSetUNTERMArabicShort(String value) {
		_uNTERMArabicShort = value;
	}

	public final String getSubRegionName() {
		return _subRegionName;
	}

	/**
	 * @see #getSubRegionName()
	 */
	public de.haumacher.phoneblock.location.model.Country setSubRegionName(String value) {
		internalSetSubRegionName(value);
		return this;
	}

	/** Internal setter for {@link #getSubRegionName()} without chain call utility. */
	protected final void internalSetSubRegionName(String value) {
		_subRegionName = value;
	}

	public final String getOfficialNameRu() {
		return _officialNameRu;
	}

	/**
	 * @see #getOfficialNameRu()
	 */
	public de.haumacher.phoneblock.location.model.Country setOfficialNameRu(String value) {
		internalSetOfficialNameRu(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameRu()} without chain call utility. */
	protected final void internalSetOfficialNameRu(String value) {
		_officialNameRu = value;
	}

	public final String getGlobalName() {
		return _globalName;
	}

	/**
	 * @see #getGlobalName()
	 */
	public de.haumacher.phoneblock.location.model.Country setGlobalName(String value) {
		internalSetGlobalName(value);
		return this;
	}

	/** Internal setter for {@link #getGlobalName()} without chain call utility. */
	protected final void internalSetGlobalName(String value) {
		_globalName = value;
	}

	public final String getCapital() {
		return _capital;
	}

	/**
	 * @see #getCapital()
	 */
	public de.haumacher.phoneblock.location.model.Country setCapital(String value) {
		internalSetCapital(value);
		return this;
	}

	/** Internal setter for {@link #getCapital()} without chain call utility. */
	protected final void internalSetCapital(String value) {
		_capital = value;
	}

	public final String getContinent() {
		return _continent;
	}

	/**
	 * @see #getContinent()
	 */
	public de.haumacher.phoneblock.location.model.Country setContinent(String value) {
		internalSetContinent(value);
		return this;
	}

	/** Internal setter for {@link #getContinent()} without chain call utility. */
	protected final void internalSetContinent(String value) {
		_continent = value;
	}

	public final String getTLD() {
		return _tLD;
	}

	/**
	 * @see #getTLD()
	 */
	public de.haumacher.phoneblock.location.model.Country setTLD(String value) {
		internalSetTLD(value);
		return this;
	}

	/** Internal setter for {@link #getTLD()} without chain call utility. */
	protected final void internalSetTLD(String value) {
		_tLD = value;
	}

	public final java.util.List<String> getLanguages() {
		return _languages;
	}

	/**
	 * @see #getLanguages()
	 */
	public de.haumacher.phoneblock.location.model.Country setLanguages(java.util.List<? extends String> value) {
		internalSetLanguages(value);
		return this;
	}

	/** Internal setter for {@link #getLanguages()} without chain call utility. */
	protected final void internalSetLanguages(java.util.List<? extends String> value) {
		_languages.clear();
		_languages.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getLanguages()} list.
	 */
	public de.haumacher.phoneblock.location.model.Country addLanguage(String value) {
		internalAddLanguage(value);
		return this;
	}

	/** Implementation of {@link #addLanguage(String)} without chain call utility. */
	protected final void internalAddLanguage(String value) {
		_languages.add(value);
	}

	/**
	 * Removes a value from the {@link #getLanguages()} list.
	 */
	public final void removeLanguage(String value) {
		_languages.remove(value);
	}

	public final String getGeonameID() {
		return _geonameID;
	}

	/**
	 * @see #getGeonameID()
	 */
	public de.haumacher.phoneblock.location.model.Country setGeonameID(String value) {
		internalSetGeonameID(value);
		return this;
	}

	/** Internal setter for {@link #getGeonameID()} without chain call utility. */
	protected final void internalSetGeonameID(String value) {
		_geonameID = value;
	}

	public final String getCLDRDisplayName() {
		return _cLDRDisplayName;
	}

	/**
	 * @see #getCLDRDisplayName()
	 */
	public de.haumacher.phoneblock.location.model.Country setCLDRDisplayName(String value) {
		internalSetCLDRDisplayName(value);
		return this;
	}

	/** Internal setter for {@link #getCLDRDisplayName()} without chain call utility. */
	protected final void internalSetCLDRDisplayName(String value) {
		_cLDRDisplayName = value;
	}

	public final String getEDGAR() {
		return _eDGAR;
	}

	/**
	 * @see #getEDGAR()
	 */
	public de.haumacher.phoneblock.location.model.Country setEDGAR(String value) {
		internalSetEDGAR(value);
		return this;
	}

	/** Internal setter for {@link #getEDGAR()} without chain call utility. */
	protected final void internalSetEDGAR(String value) {
		_eDGAR = value;
	}

	public final String getWikidataId() {
		return _wikidataId;
	}

	/**
	 * @see #getWikidataId()
	 */
	public de.haumacher.phoneblock.location.model.Country setWikidataId(String value) {
		internalSetWikidataId(value);
		return this;
	}

	/** Internal setter for {@link #getWikidataId()} without chain call utility. */
	protected final void internalSetWikidataId(String value) {
		_wikidataId = value;
	}

	public final java.util.List<String> getTrunkPrefixes() {
		return _trunkPrefixes;
	}

	/**
	 * @see #getTrunkPrefixes()
	 */
	public de.haumacher.phoneblock.location.model.Country setTrunkPrefixes(java.util.List<? extends String> value) {
		internalSetTrunkPrefixes(value);
		return this;
	}

	/** Internal setter for {@link #getTrunkPrefixes()} without chain call utility. */
	protected final void internalSetTrunkPrefixes(java.util.List<? extends String> value) {
		_trunkPrefixes.clear();
		_trunkPrefixes.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getTrunkPrefixes()} list.
	 */
	public de.haumacher.phoneblock.location.model.Country addTrunkPrefix(String value) {
		internalAddTrunkPrefix(value);
		return this;
	}

	/** Implementation of {@link #addTrunkPrefix(String)} without chain call utility. */
	protected final void internalAddTrunkPrefix(String value) {
		_trunkPrefixes.add(value);
	}

	/**
	 * Removes a value from the {@link #getTrunkPrefixes()} list.
	 */
	public final void removeTrunkPrefix(String value) {
		_trunkPrefixes.remove(value);
	}

	public final java.util.List<String> getInternationalPrefixes() {
		return _internationalPrefixes;
	}

	/**
	 * @see #getInternationalPrefixes()
	 */
	public de.haumacher.phoneblock.location.model.Country setInternationalPrefixes(java.util.List<? extends String> value) {
		internalSetInternationalPrefixes(value);
		return this;
	}

	/** Internal setter for {@link #getInternationalPrefixes()} without chain call utility. */
	protected final void internalSetInternationalPrefixes(java.util.List<? extends String> value) {
		_internationalPrefixes.clear();
		_internationalPrefixes.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getInternationalPrefixes()} list.
	 */
	public de.haumacher.phoneblock.location.model.Country addInternationalPrefix(String value) {
		internalAddInternationalPrefix(value);
		return this;
	}

	/** Implementation of {@link #addInternationalPrefix(String)} without chain call utility. */
	protected final void internalAddInternationalPrefix(String value) {
		_internationalPrefixes.add(value);
	}

	/**
	 * Removes a value from the {@link #getInternationalPrefixes()} list.
	 */
	public final void removeInternationalPrefix(String value) {
		_internationalPrefixes.remove(value);
	}

	@Override
	public String jsonType() {
		return COUNTRY__TYPE;
	}

	static final java.util.List<String> PROPERTIES;
	static {
		java.util.List<String> local = java.util.Arrays.asList(
			FIFA__PROP, 
			DIAL_PREFIXES__PROP, 
			ISO_3166_1_ALPHA_3__PROP, 
			MARC__PROP, 
			INDEPENDENT__PROP, 
			ISO_3166_1_NUMERIC__PROP, 
			GAUL__PROP, 
			FIPS__PROP, 
			WMO__PROP, 
			ISO_3166_1_ALPHA_2__PROP, 
			ITU__PROP, 
			IOC__PROP, 
			DS__PROP, 
			UNTERM_SPANISH_FORMAL__PROP, 
			GLOBAL_CODE__PROP, 
			INTERMEDIATE_REGION_CODE__PROP, 
			OFFICIAL_NAME_FR__PROP, 
			UNTERM_FRENCH_SHORT__PROP, 
			ISO_4217_CURRENCY_NAME__PROP, 
			UNTERM_RUSSIAN_FORMAL__PROP, 
			UNTERM_ENGLISH_SHORT__PROP, 
			ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP, 
			SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP, 
			UNTERM_SPANISH_SHORT__PROP, 
			ISO_4217_CURRENCY_NUMERIC_CODE__PROP, 
			UNTERM_CHINESE_FORMAL__PROP, 
			UNTERM_FRENCH_FORMAL__PROP, 
			UNTERM_RUSSIAN_SHORT__PROP, 
			M_49__PROP, 
			SUB_REGION_CODE__PROP, 
			REGION_CODE__PROP, 
			OFFICIAL_NAME_AR__PROP, 
			ISO_4217_CURRENCY_MINOR_UNIT__PROP, 
			UNTERM_ARABIC_FORMAL__PROP, 
			UNTERM_CHINESE_SHORT__PROP, 
			LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP, 
			INTERMEDIATE_REGION_NAME__PROP, 
			OFFICIAL_NAME_ES__PROP, 
			UNTERM_ENGLISH_FORMAL__PROP, 
			OFFICIAL_NAME_CN__PROP, 
			OFFICIAL_NAME_EN__PROP, 
			ISO_4217_CURRENCY_COUNTRY_NAME__PROP, 
			LEAST_DEVELOPED_COUNTRIES_LDC__PROP, 
			REGION_NAME__PROP, 
			UNTERM_ARABIC_SHORT__PROP, 
			SUB_REGION_NAME__PROP, 
			OFFICIAL_NAME_RU__PROP, 
			GLOBAL_NAME__PROP, 
			CAPITAL__PROP, 
			CONTINENT__PROP, 
			TLD__PROP, 
			LANGUAGES__PROP, 
			GEONAME_ID__PROP, 
			CLDR_DISPLAY_NAME__PROP, 
			EDGAR__PROP, 
			WIKIDATA_ID__PROP, 
			TRUNK_PREFIXES__PROP, 
			INTERNATIONAL_PREFIXES__PROP);
		PROPERTIES = java.util.Collections.unmodifiableList(local);
	}

	static final java.util.Set<String> TRANSIENT_PROPERTIES;
	static {
		java.util.HashSet<String> tmp = new java.util.HashSet<>();
		tmp.addAll(java.util.Arrays.asList(
				));
		TRANSIENT_PROPERTIES = java.util.Collections.unmodifiableSet(tmp);
	}

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public java.util.Set<String> transientProperties() {
		return TRANSIENT_PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case FIFA__PROP: return getFIFA();
			case DIAL_PREFIXES__PROP: return getDialPrefixes();
			case ISO_3166_1_ALPHA_3__PROP: return getISO31661Alpha3();
			case MARC__PROP: return getMARC();
			case INDEPENDENT__PROP: return isIndependent();
			case ISO_3166_1_NUMERIC__PROP: return getISO31661Numeric();
			case GAUL__PROP: return getGAUL();
			case FIPS__PROP: return getFIPS();
			case WMO__PROP: return getWMO();
			case ISO_3166_1_ALPHA_2__PROP: return getISO31661Alpha2();
			case ITU__PROP: return getITU();
			case IOC__PROP: return getIOC();
			case DS__PROP: return getDS();
			case UNTERM_SPANISH_FORMAL__PROP: return getUNTERMSpanishFormal();
			case GLOBAL_CODE__PROP: return getGlobalCode();
			case INTERMEDIATE_REGION_CODE__PROP: return getIntermediateRegionCode();
			case OFFICIAL_NAME_FR__PROP: return getOfficialNameFr();
			case UNTERM_FRENCH_SHORT__PROP: return getUNTERMFrenchShort();
			case ISO_4217_CURRENCY_NAME__PROP: return getISO4217CurrencyName();
			case UNTERM_RUSSIAN_FORMAL__PROP: return getUNTERMRussianFormal();
			case UNTERM_ENGLISH_SHORT__PROP: return getUNTERMEnglishShort();
			case ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP: return getISO4217CurrencyAlphabeticCode();
			case SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP: return getSmallIslandDevelopingStatesSIDS();
			case UNTERM_SPANISH_SHORT__PROP: return getUNTERMSpanishShort();
			case ISO_4217_CURRENCY_NUMERIC_CODE__PROP: return getISO4217CurrencyNumericCode();
			case UNTERM_CHINESE_FORMAL__PROP: return getUNTERMChineseFormal();
			case UNTERM_FRENCH_FORMAL__PROP: return getUNTERMFrenchFormal();
			case UNTERM_RUSSIAN_SHORT__PROP: return getUNTERMRussianShort();
			case M_49__PROP: return getM49();
			case SUB_REGION_CODE__PROP: return getSubRegionCode();
			case REGION_CODE__PROP: return getRegionCode();
			case OFFICIAL_NAME_AR__PROP: return getOfficialNameAr();
			case ISO_4217_CURRENCY_MINOR_UNIT__PROP: return getISO4217CurrencyMinorUnit();
			case UNTERM_ARABIC_FORMAL__PROP: return getUNTERMArabicFormal();
			case UNTERM_CHINESE_SHORT__PROP: return getUNTERMChineseShort();
			case LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP: return getLandLockedDevelopingCountriesLLDC();
			case INTERMEDIATE_REGION_NAME__PROP: return getIntermediateRegionName();
			case OFFICIAL_NAME_ES__PROP: return getOfficialNameEs();
			case UNTERM_ENGLISH_FORMAL__PROP: return getUNTERMEnglishFormal();
			case OFFICIAL_NAME_CN__PROP: return getOfficialNameCn();
			case OFFICIAL_NAME_EN__PROP: return getOfficialNameEn();
			case ISO_4217_CURRENCY_COUNTRY_NAME__PROP: return getISO4217CurrencyCountryName();
			case LEAST_DEVELOPED_COUNTRIES_LDC__PROP: return getLeastDevelopedCountriesLDC();
			case REGION_NAME__PROP: return getRegionName();
			case UNTERM_ARABIC_SHORT__PROP: return getUNTERMArabicShort();
			case SUB_REGION_NAME__PROP: return getSubRegionName();
			case OFFICIAL_NAME_RU__PROP: return getOfficialNameRu();
			case GLOBAL_NAME__PROP: return getGlobalName();
			case CAPITAL__PROP: return getCapital();
			case CONTINENT__PROP: return getContinent();
			case TLD__PROP: return getTLD();
			case LANGUAGES__PROP: return getLanguages();
			case GEONAME_ID__PROP: return getGeonameID();
			case CLDR_DISPLAY_NAME__PROP: return getCLDRDisplayName();
			case EDGAR__PROP: return getEDGAR();
			case WIKIDATA_ID__PROP: return getWikidataId();
			case TRUNK_PREFIXES__PROP: return getTrunkPrefixes();
			case INTERNATIONAL_PREFIXES__PROP: return getInternationalPrefixes();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case FIFA__PROP: internalSetFIFA((String) value); break;
			case DIAL_PREFIXES__PROP: internalSetDialPrefixes(de.haumacher.msgbuf.util.Conversions.asList(String.class, value)); break;
			case ISO_3166_1_ALPHA_3__PROP: internalSetISO31661Alpha3((String) value); break;
			case MARC__PROP: internalSetMARC((String) value); break;
			case INDEPENDENT__PROP: internalSetIndependent((boolean) value); break;
			case ISO_3166_1_NUMERIC__PROP: internalSetISO31661Numeric((String) value); break;
			case GAUL__PROP: internalSetGAUL((String) value); break;
			case FIPS__PROP: internalSetFIPS((String) value); break;
			case WMO__PROP: internalSetWMO((String) value); break;
			case ISO_3166_1_ALPHA_2__PROP: internalSetISO31661Alpha2((String) value); break;
			case ITU__PROP: internalSetITU((String) value); break;
			case IOC__PROP: internalSetIOC((String) value); break;
			case DS__PROP: internalSetDS((String) value); break;
			case UNTERM_SPANISH_FORMAL__PROP: internalSetUNTERMSpanishFormal((String) value); break;
			case GLOBAL_CODE__PROP: internalSetGlobalCode((String) value); break;
			case INTERMEDIATE_REGION_CODE__PROP: internalSetIntermediateRegionCode((String) value); break;
			case OFFICIAL_NAME_FR__PROP: internalSetOfficialNameFr((String) value); break;
			case UNTERM_FRENCH_SHORT__PROP: internalSetUNTERMFrenchShort((String) value); break;
			case ISO_4217_CURRENCY_NAME__PROP: internalSetISO4217CurrencyName((String) value); break;
			case UNTERM_RUSSIAN_FORMAL__PROP: internalSetUNTERMRussianFormal((String) value); break;
			case UNTERM_ENGLISH_SHORT__PROP: internalSetUNTERMEnglishShort((String) value); break;
			case ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP: internalSetISO4217CurrencyAlphabeticCode((String) value); break;
			case SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP: internalSetSmallIslandDevelopingStatesSIDS((String) value); break;
			case UNTERM_SPANISH_SHORT__PROP: internalSetUNTERMSpanishShort((String) value); break;
			case ISO_4217_CURRENCY_NUMERIC_CODE__PROP: internalSetISO4217CurrencyNumericCode((String) value); break;
			case UNTERM_CHINESE_FORMAL__PROP: internalSetUNTERMChineseFormal((String) value); break;
			case UNTERM_FRENCH_FORMAL__PROP: internalSetUNTERMFrenchFormal((String) value); break;
			case UNTERM_RUSSIAN_SHORT__PROP: internalSetUNTERMRussianShort((String) value); break;
			case M_49__PROP: internalSetM49((String) value); break;
			case SUB_REGION_CODE__PROP: internalSetSubRegionCode((String) value); break;
			case REGION_CODE__PROP: internalSetRegionCode((String) value); break;
			case OFFICIAL_NAME_AR__PROP: internalSetOfficialNameAr((String) value); break;
			case ISO_4217_CURRENCY_MINOR_UNIT__PROP: internalSetISO4217CurrencyMinorUnit((String) value); break;
			case UNTERM_ARABIC_FORMAL__PROP: internalSetUNTERMArabicFormal((String) value); break;
			case UNTERM_CHINESE_SHORT__PROP: internalSetUNTERMChineseShort((String) value); break;
			case LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP: internalSetLandLockedDevelopingCountriesLLDC((String) value); break;
			case INTERMEDIATE_REGION_NAME__PROP: internalSetIntermediateRegionName((String) value); break;
			case OFFICIAL_NAME_ES__PROP: internalSetOfficialNameEs((String) value); break;
			case UNTERM_ENGLISH_FORMAL__PROP: internalSetUNTERMEnglishFormal((String) value); break;
			case OFFICIAL_NAME_CN__PROP: internalSetOfficialNameCn((String) value); break;
			case OFFICIAL_NAME_EN__PROP: internalSetOfficialNameEn((String) value); break;
			case ISO_4217_CURRENCY_COUNTRY_NAME__PROP: internalSetISO4217CurrencyCountryName((String) value); break;
			case LEAST_DEVELOPED_COUNTRIES_LDC__PROP: internalSetLeastDevelopedCountriesLDC((String) value); break;
			case REGION_NAME__PROP: internalSetRegionName((String) value); break;
			case UNTERM_ARABIC_SHORT__PROP: internalSetUNTERMArabicShort((String) value); break;
			case SUB_REGION_NAME__PROP: internalSetSubRegionName((String) value); break;
			case OFFICIAL_NAME_RU__PROP: internalSetOfficialNameRu((String) value); break;
			case GLOBAL_NAME__PROP: internalSetGlobalName((String) value); break;
			case CAPITAL__PROP: internalSetCapital((String) value); break;
			case CONTINENT__PROP: internalSetContinent((String) value); break;
			case TLD__PROP: internalSetTLD((String) value); break;
			case LANGUAGES__PROP: internalSetLanguages(de.haumacher.msgbuf.util.Conversions.asList(String.class, value)); break;
			case GEONAME_ID__PROP: internalSetGeonameID((String) value); break;
			case CLDR_DISPLAY_NAME__PROP: internalSetCLDRDisplayName((String) value); break;
			case EDGAR__PROP: internalSetEDGAR((String) value); break;
			case WIKIDATA_ID__PROP: internalSetWikidataId((String) value); break;
			case TRUNK_PREFIXES__PROP: internalSetTrunkPrefixes(de.haumacher.msgbuf.util.Conversions.asList(String.class, value)); break;
			case INTERNATIONAL_PREFIXES__PROP: internalSetInternationalPrefixes(de.haumacher.msgbuf.util.Conversions.asList(String.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.location.model.Country readCountry(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.location.model.Country result = new de.haumacher.phoneblock.location.model.Country();
		result.readContent(in);
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(FIFA__PROP);
		out.value(getFIFA());
		out.name(DIAL_PREFIXES__PROP);
		out.beginArray();
		for (String x : getDialPrefixes()) {
			out.value(x);
		}
		out.endArray();
		out.name(ISO_3166_1_ALPHA_3__PROP);
		out.value(getISO31661Alpha3());
		out.name(MARC__PROP);
		out.value(getMARC());
		out.name(INDEPENDENT__PROP);
		out.value(isIndependent());
		out.name(ISO_3166_1_NUMERIC__PROP);
		out.value(getISO31661Numeric());
		out.name(GAUL__PROP);
		out.value(getGAUL());
		out.name(FIPS__PROP);
		out.value(getFIPS());
		out.name(WMO__PROP);
		out.value(getWMO());
		out.name(ISO_3166_1_ALPHA_2__PROP);
		out.value(getISO31661Alpha2());
		out.name(ITU__PROP);
		out.value(getITU());
		out.name(IOC__PROP);
		out.value(getIOC());
		out.name(DS__PROP);
		out.value(getDS());
		out.name(UNTERM_SPANISH_FORMAL__PROP);
		out.value(getUNTERMSpanishFormal());
		out.name(GLOBAL_CODE__PROP);
		out.value(getGlobalCode());
		out.name(INTERMEDIATE_REGION_CODE__PROP);
		out.value(getIntermediateRegionCode());
		out.name(OFFICIAL_NAME_FR__PROP);
		out.value(getOfficialNameFr());
		out.name(UNTERM_FRENCH_SHORT__PROP);
		out.value(getUNTERMFrenchShort());
		out.name(ISO_4217_CURRENCY_NAME__PROP);
		out.value(getISO4217CurrencyName());
		out.name(UNTERM_RUSSIAN_FORMAL__PROP);
		out.value(getUNTERMRussianFormal());
		out.name(UNTERM_ENGLISH_SHORT__PROP);
		out.value(getUNTERMEnglishShort());
		out.name(ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP);
		out.value(getISO4217CurrencyAlphabeticCode());
		out.name(SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP);
		out.value(getSmallIslandDevelopingStatesSIDS());
		out.name(UNTERM_SPANISH_SHORT__PROP);
		out.value(getUNTERMSpanishShort());
		out.name(ISO_4217_CURRENCY_NUMERIC_CODE__PROP);
		out.value(getISO4217CurrencyNumericCode());
		out.name(UNTERM_CHINESE_FORMAL__PROP);
		out.value(getUNTERMChineseFormal());
		out.name(UNTERM_FRENCH_FORMAL__PROP);
		out.value(getUNTERMFrenchFormal());
		out.name(UNTERM_RUSSIAN_SHORT__PROP);
		out.value(getUNTERMRussianShort());
		out.name(M_49__PROP);
		out.value(getM49());
		out.name(SUB_REGION_CODE__PROP);
		out.value(getSubRegionCode());
		out.name(REGION_CODE__PROP);
		out.value(getRegionCode());
		out.name(OFFICIAL_NAME_AR__PROP);
		out.value(getOfficialNameAr());
		out.name(ISO_4217_CURRENCY_MINOR_UNIT__PROP);
		out.value(getISO4217CurrencyMinorUnit());
		out.name(UNTERM_ARABIC_FORMAL__PROP);
		out.value(getUNTERMArabicFormal());
		out.name(UNTERM_CHINESE_SHORT__PROP);
		out.value(getUNTERMChineseShort());
		out.name(LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP);
		out.value(getLandLockedDevelopingCountriesLLDC());
		out.name(INTERMEDIATE_REGION_NAME__PROP);
		out.value(getIntermediateRegionName());
		out.name(OFFICIAL_NAME_ES__PROP);
		out.value(getOfficialNameEs());
		out.name(UNTERM_ENGLISH_FORMAL__PROP);
		out.value(getUNTERMEnglishFormal());
		out.name(OFFICIAL_NAME_CN__PROP);
		out.value(getOfficialNameCn());
		out.name(OFFICIAL_NAME_EN__PROP);
		out.value(getOfficialNameEn());
		out.name(ISO_4217_CURRENCY_COUNTRY_NAME__PROP);
		out.value(getISO4217CurrencyCountryName());
		out.name(LEAST_DEVELOPED_COUNTRIES_LDC__PROP);
		out.value(getLeastDevelopedCountriesLDC());
		out.name(REGION_NAME__PROP);
		out.value(getRegionName());
		out.name(UNTERM_ARABIC_SHORT__PROP);
		out.value(getUNTERMArabicShort());
		out.name(SUB_REGION_NAME__PROP);
		out.value(getSubRegionName());
		out.name(OFFICIAL_NAME_RU__PROP);
		out.value(getOfficialNameRu());
		out.name(GLOBAL_NAME__PROP);
		out.value(getGlobalName());
		out.name(CAPITAL__PROP);
		out.value(getCapital());
		out.name(CONTINENT__PROP);
		out.value(getContinent());
		out.name(TLD__PROP);
		out.value(getTLD());
		out.name(LANGUAGES__PROP);
		out.beginArray();
		for (String x : getLanguages()) {
			out.value(x);
		}
		out.endArray();
		out.name(GEONAME_ID__PROP);
		out.value(getGeonameID());
		out.name(CLDR_DISPLAY_NAME__PROP);
		out.value(getCLDRDisplayName());
		out.name(EDGAR__PROP);
		out.value(getEDGAR());
		out.name(WIKIDATA_ID__PROP);
		out.value(getWikidataId());
		out.name(TRUNK_PREFIXES__PROP);
		out.beginArray();
		for (String x : getTrunkPrefixes()) {
			out.value(x);
		}
		out.endArray();
		out.name(INTERNATIONAL_PREFIXES__PROP);
		out.beginArray();
		for (String x : getInternationalPrefixes()) {
			out.value(x);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case FIFA__PROP: setFIFA(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DIAL_PREFIXES__PROP: {
				java.util.List<String> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
				setDialPrefixes(newValue);
			}
			break;
			case ISO_3166_1_ALPHA_3__PROP: setISO31661Alpha3(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MARC__PROP: setMARC(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case INDEPENDENT__PROP: setIndependent(in.nextBoolean()); break;
			case ISO_3166_1_NUMERIC__PROP: setISO31661Numeric(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case GAUL__PROP: setGAUL(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case FIPS__PROP: setFIPS(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case WMO__PROP: setWMO(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ISO_3166_1_ALPHA_2__PROP: setISO31661Alpha2(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ITU__PROP: setITU(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case IOC__PROP: setIOC(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DS__PROP: setDS(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_SPANISH_FORMAL__PROP: setUNTERMSpanishFormal(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case GLOBAL_CODE__PROP: setGlobalCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case INTERMEDIATE_REGION_CODE__PROP: setIntermediateRegionCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case OFFICIAL_NAME_FR__PROP: setOfficialNameFr(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_FRENCH_SHORT__PROP: setUNTERMFrenchShort(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ISO_4217_CURRENCY_NAME__PROP: setISO4217CurrencyName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_RUSSIAN_FORMAL__PROP: setUNTERMRussianFormal(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_ENGLISH_SHORT__PROP: setUNTERMEnglishShort(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP: setISO4217CurrencyAlphabeticCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP: setSmallIslandDevelopingStatesSIDS(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_SPANISH_SHORT__PROP: setUNTERMSpanishShort(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ISO_4217_CURRENCY_NUMERIC_CODE__PROP: setISO4217CurrencyNumericCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_CHINESE_FORMAL__PROP: setUNTERMChineseFormal(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_FRENCH_FORMAL__PROP: setUNTERMFrenchFormal(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_RUSSIAN_SHORT__PROP: setUNTERMRussianShort(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case M_49__PROP: setM49(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SUB_REGION_CODE__PROP: setSubRegionCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case REGION_CODE__PROP: setRegionCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case OFFICIAL_NAME_AR__PROP: setOfficialNameAr(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ISO_4217_CURRENCY_MINOR_UNIT__PROP: setISO4217CurrencyMinorUnit(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_ARABIC_FORMAL__PROP: setUNTERMArabicFormal(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_CHINESE_SHORT__PROP: setUNTERMChineseShort(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP: setLandLockedDevelopingCountriesLLDC(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case INTERMEDIATE_REGION_NAME__PROP: setIntermediateRegionName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case OFFICIAL_NAME_ES__PROP: setOfficialNameEs(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_ENGLISH_FORMAL__PROP: setUNTERMEnglishFormal(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case OFFICIAL_NAME_CN__PROP: setOfficialNameCn(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case OFFICIAL_NAME_EN__PROP: setOfficialNameEn(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ISO_4217_CURRENCY_COUNTRY_NAME__PROP: setISO4217CurrencyCountryName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LEAST_DEVELOPED_COUNTRIES_LDC__PROP: setLeastDevelopedCountriesLDC(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case REGION_NAME__PROP: setRegionName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case UNTERM_ARABIC_SHORT__PROP: setUNTERMArabicShort(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SUB_REGION_NAME__PROP: setSubRegionName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case OFFICIAL_NAME_RU__PROP: setOfficialNameRu(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case GLOBAL_NAME__PROP: setGlobalName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CAPITAL__PROP: setCapital(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CONTINENT__PROP: setContinent(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TLD__PROP: setTLD(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LANGUAGES__PROP: {
				java.util.List<String> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
				setLanguages(newValue);
			}
			break;
			case GEONAME_ID__PROP: setGeonameID(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CLDR_DISPLAY_NAME__PROP: setCLDRDisplayName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case EDGAR__PROP: setEDGAR(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case WIKIDATA_ID__PROP: setWikidataId(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TRUNK_PREFIXES__PROP: {
				java.util.List<String> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
				setTrunkPrefixes(newValue);
			}
			break;
			case INTERNATIONAL_PREFIXES__PROP: {
				java.util.List<String> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
				setInternationalPrefixes(newValue);
			}
			break;
			default: super.readField(in, field);
		}
	}

}
