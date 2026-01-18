package de.haumacher.phoneblock.location.model.impl;

/**
 * Implementation of {@link de.haumacher.phoneblock.location.model.Country}.
 */
public class Country_Impl extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.phoneblock.location.model.Country {

	private String _fIFA = "";

	private final java.util.List<String> _dialPrefixes = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(Country_Impl.this, DIAL_PREFIXES__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(Country_Impl.this, DIAL_PREFIXES__PROP, index, element);
		}
	};

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

	private final java.util.List<String> _languages = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(Country_Impl.this, LANGUAGES__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(Country_Impl.this, LANGUAGES__PROP, index, element);
		}
	};

	private String _geonameID = "";

	private String _cLDRDisplayName = "";

	private String _eDGAR = "";

	private String _wikidataId = "";

	private final java.util.List<String> _trunkPrefixes = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(Country_Impl.this, TRUNK_PREFIXES__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(Country_Impl.this, TRUNK_PREFIXES__PROP, index, element);
		}
	};

	private final java.util.List<String> _internationalPrefixes = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(Country_Impl.this, INTERNATIONAL_PREFIXES__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(Country_Impl.this, INTERNATIONAL_PREFIXES__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link Country_Impl} instance.
	 *
	 * @see de.haumacher.phoneblock.location.model.Country#create()
	 */
	public Country_Impl() {
		super();
	}

	@Override
	public final String getFIFA() {
		return _fIFA;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setFIFA(String value) {
		internalSetFIFA(value);
		return this;
	}

	/** Internal setter for {@link #getFIFA()} without chain call utility. */
	protected final void internalSetFIFA(String value) {
		_listener.beforeSet(this, FIFA__PROP, value);
		_fIFA = value;
	}

	@Override
	public final java.util.List<String> getDialPrefixes() {
		return _dialPrefixes;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setDialPrefixes(java.util.List<? extends String> value) {
		internalSetDialPrefixes(value);
		return this;
	}

	/** Internal setter for {@link #getDialPrefixes()} without chain call utility. */
	protected final void internalSetDialPrefixes(java.util.List<? extends String> value) {
		_dialPrefixes.clear();
		_dialPrefixes.addAll(value);
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country addDialPrefixe(String value) {
		internalAddDialPrefixe(value);
		return this;
	}

	/** Implementation of {@link #addDialPrefixe(String)} without chain call utility. */
	protected final void internalAddDialPrefixe(String value) {
		_dialPrefixes.add(value);
	}

	@Override
	public final void removeDialPrefixe(String value) {
		_dialPrefixes.remove(value);
	}

	@Override
	public final String getISO31661Alpha3() {
		return _iSO31661Alpha3;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO31661Alpha3(String value) {
		internalSetISO31661Alpha3(value);
		return this;
	}

	/** Internal setter for {@link #getISO31661Alpha3()} without chain call utility. */
	protected final void internalSetISO31661Alpha3(String value) {
		_listener.beforeSet(this, ISO_3166_1_ALPHA_3__PROP, value);
		_iSO31661Alpha3 = value;
	}

	@Override
	public final String getMARC() {
		return _mARC;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setMARC(String value) {
		internalSetMARC(value);
		return this;
	}

	/** Internal setter for {@link #getMARC()} without chain call utility. */
	protected final void internalSetMARC(String value) {
		_listener.beforeSet(this, MARC__PROP, value);
		_mARC = value;
	}

	@Override
	public final boolean isIndependent() {
		return _independent;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setIndependent(boolean value) {
		internalSetIndependent(value);
		return this;
	}

	/** Internal setter for {@link #isIndependent()} without chain call utility. */
	protected final void internalSetIndependent(boolean value) {
		_listener.beforeSet(this, INDEPENDENT__PROP, value);
		_independent = value;
	}

	@Override
	public final String getISO31661Numeric() {
		return _iSO31661Numeric;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO31661Numeric(String value) {
		internalSetISO31661Numeric(value);
		return this;
	}

	/** Internal setter for {@link #getISO31661Numeric()} without chain call utility. */
	protected final void internalSetISO31661Numeric(String value) {
		_listener.beforeSet(this, ISO_3166_1_NUMERIC__PROP, value);
		_iSO31661Numeric = value;
	}

	@Override
	public final String getGAUL() {
		return _gAUL;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setGAUL(String value) {
		internalSetGAUL(value);
		return this;
	}

	/** Internal setter for {@link #getGAUL()} without chain call utility. */
	protected final void internalSetGAUL(String value) {
		_listener.beforeSet(this, GAUL__PROP, value);
		_gAUL = value;
	}

	@Override
	public final String getFIPS() {
		return _fIPS;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setFIPS(String value) {
		internalSetFIPS(value);
		return this;
	}

	/** Internal setter for {@link #getFIPS()} without chain call utility. */
	protected final void internalSetFIPS(String value) {
		_listener.beforeSet(this, FIPS__PROP, value);
		_fIPS = value;
	}

	@Override
	public final String getWMO() {
		return _wMO;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setWMO(String value) {
		internalSetWMO(value);
		return this;
	}

	/** Internal setter for {@link #getWMO()} without chain call utility. */
	protected final void internalSetWMO(String value) {
		_listener.beforeSet(this, WMO__PROP, value);
		_wMO = value;
	}

	@Override
	public final String getISO31661Alpha2() {
		return _iSO31661Alpha2;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO31661Alpha2(String value) {
		internalSetISO31661Alpha2(value);
		return this;
	}

	/** Internal setter for {@link #getISO31661Alpha2()} without chain call utility. */
	protected final void internalSetISO31661Alpha2(String value) {
		_listener.beforeSet(this, ISO_3166_1_ALPHA_2__PROP, value);
		_iSO31661Alpha2 = value;
	}

	@Override
	public final String getITU() {
		return _iTU;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setITU(String value) {
		internalSetITU(value);
		return this;
	}

	/** Internal setter for {@link #getITU()} without chain call utility. */
	protected final void internalSetITU(String value) {
		_listener.beforeSet(this, ITU__PROP, value);
		_iTU = value;
	}

	@Override
	public final String getIOC() {
		return _iOC;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setIOC(String value) {
		internalSetIOC(value);
		return this;
	}

	/** Internal setter for {@link #getIOC()} without chain call utility. */
	protected final void internalSetIOC(String value) {
		_listener.beforeSet(this, IOC__PROP, value);
		_iOC = value;
	}

	@Override
	public final String getDS() {
		return _dS;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setDS(String value) {
		internalSetDS(value);
		return this;
	}

	/** Internal setter for {@link #getDS()} without chain call utility. */
	protected final void internalSetDS(String value) {
		_listener.beforeSet(this, DS__PROP, value);
		_dS = value;
	}

	@Override
	public final String getUNTERMSpanishFormal() {
		return _uNTERMSpanishFormal;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMSpanishFormal(String value) {
		internalSetUNTERMSpanishFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMSpanishFormal()} without chain call utility. */
	protected final void internalSetUNTERMSpanishFormal(String value) {
		_listener.beforeSet(this, UNTERM_SPANISH_FORMAL__PROP, value);
		_uNTERMSpanishFormal = value;
	}

	@Override
	public final String getGlobalCode() {
		return _globalCode;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setGlobalCode(String value) {
		internalSetGlobalCode(value);
		return this;
	}

	/** Internal setter for {@link #getGlobalCode()} without chain call utility. */
	protected final void internalSetGlobalCode(String value) {
		_listener.beforeSet(this, GLOBAL_CODE__PROP, value);
		_globalCode = value;
	}

	@Override
	public final String getIntermediateRegionCode() {
		return _intermediateRegionCode;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setIntermediateRegionCode(String value) {
		internalSetIntermediateRegionCode(value);
		return this;
	}

	/** Internal setter for {@link #getIntermediateRegionCode()} without chain call utility. */
	protected final void internalSetIntermediateRegionCode(String value) {
		_listener.beforeSet(this, INTERMEDIATE_REGION_CODE__PROP, value);
		_intermediateRegionCode = value;
	}

	@Override
	public final String getOfficialNameFr() {
		return _officialNameFr;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setOfficialNameFr(String value) {
		internalSetOfficialNameFr(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameFr()} without chain call utility. */
	protected final void internalSetOfficialNameFr(String value) {
		_listener.beforeSet(this, OFFICIAL_NAME_FR__PROP, value);
		_officialNameFr = value;
	}

	@Override
	public final String getUNTERMFrenchShort() {
		return _uNTERMFrenchShort;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMFrenchShort(String value) {
		internalSetUNTERMFrenchShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMFrenchShort()} without chain call utility. */
	protected final void internalSetUNTERMFrenchShort(String value) {
		_listener.beforeSet(this, UNTERM_FRENCH_SHORT__PROP, value);
		_uNTERMFrenchShort = value;
	}

	@Override
	public final String getISO4217CurrencyName() {
		return _iSO4217CurrencyName;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyName(String value) {
		internalSetISO4217CurrencyName(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyName()} without chain call utility. */
	protected final void internalSetISO4217CurrencyName(String value) {
		_listener.beforeSet(this, ISO_4217_CURRENCY_NAME__PROP, value);
		_iSO4217CurrencyName = value;
	}

	@Override
	public final String getUNTERMRussianFormal() {
		return _uNTERMRussianFormal;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMRussianFormal(String value) {
		internalSetUNTERMRussianFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMRussianFormal()} without chain call utility. */
	protected final void internalSetUNTERMRussianFormal(String value) {
		_listener.beforeSet(this, UNTERM_RUSSIAN_FORMAL__PROP, value);
		_uNTERMRussianFormal = value;
	}

	@Override
	public final String getUNTERMEnglishShort() {
		return _uNTERMEnglishShort;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMEnglishShort(String value) {
		internalSetUNTERMEnglishShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMEnglishShort()} without chain call utility. */
	protected final void internalSetUNTERMEnglishShort(String value) {
		_listener.beforeSet(this, UNTERM_ENGLISH_SHORT__PROP, value);
		_uNTERMEnglishShort = value;
	}

	@Override
	public final String getISO4217CurrencyAlphabeticCode() {
		return _iSO4217CurrencyAlphabeticCode;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyAlphabeticCode(String value) {
		internalSetISO4217CurrencyAlphabeticCode(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyAlphabeticCode()} without chain call utility. */
	protected final void internalSetISO4217CurrencyAlphabeticCode(String value) {
		_listener.beforeSet(this, ISO_4217_CURRENCY_ALPHABETIC_CODE__PROP, value);
		_iSO4217CurrencyAlphabeticCode = value;
	}

	@Override
	public final String getSmallIslandDevelopingStatesSIDS() {
		return _smallIslandDevelopingStatesSIDS;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setSmallIslandDevelopingStatesSIDS(String value) {
		internalSetSmallIslandDevelopingStatesSIDS(value);
		return this;
	}

	/** Internal setter for {@link #getSmallIslandDevelopingStatesSIDS()} without chain call utility. */
	protected final void internalSetSmallIslandDevelopingStatesSIDS(String value) {
		_listener.beforeSet(this, SMALL_ISLAND_DEVELOPING_STATES_SIDS__PROP, value);
		_smallIslandDevelopingStatesSIDS = value;
	}

	@Override
	public final String getUNTERMSpanishShort() {
		return _uNTERMSpanishShort;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMSpanishShort(String value) {
		internalSetUNTERMSpanishShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMSpanishShort()} without chain call utility. */
	protected final void internalSetUNTERMSpanishShort(String value) {
		_listener.beforeSet(this, UNTERM_SPANISH_SHORT__PROP, value);
		_uNTERMSpanishShort = value;
	}

	@Override
	public final String getISO4217CurrencyNumericCode() {
		return _iSO4217CurrencyNumericCode;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyNumericCode(String value) {
		internalSetISO4217CurrencyNumericCode(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyNumericCode()} without chain call utility. */
	protected final void internalSetISO4217CurrencyNumericCode(String value) {
		_listener.beforeSet(this, ISO_4217_CURRENCY_NUMERIC_CODE__PROP, value);
		_iSO4217CurrencyNumericCode = value;
	}

	@Override
	public final String getUNTERMChineseFormal() {
		return _uNTERMChineseFormal;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMChineseFormal(String value) {
		internalSetUNTERMChineseFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMChineseFormal()} without chain call utility. */
	protected final void internalSetUNTERMChineseFormal(String value) {
		_listener.beforeSet(this, UNTERM_CHINESE_FORMAL__PROP, value);
		_uNTERMChineseFormal = value;
	}

	@Override
	public final String getUNTERMFrenchFormal() {
		return _uNTERMFrenchFormal;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMFrenchFormal(String value) {
		internalSetUNTERMFrenchFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMFrenchFormal()} without chain call utility. */
	protected final void internalSetUNTERMFrenchFormal(String value) {
		_listener.beforeSet(this, UNTERM_FRENCH_FORMAL__PROP, value);
		_uNTERMFrenchFormal = value;
	}

	@Override
	public final String getUNTERMRussianShort() {
		return _uNTERMRussianShort;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMRussianShort(String value) {
		internalSetUNTERMRussianShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMRussianShort()} without chain call utility. */
	protected final void internalSetUNTERMRussianShort(String value) {
		_listener.beforeSet(this, UNTERM_RUSSIAN_SHORT__PROP, value);
		_uNTERMRussianShort = value;
	}

	@Override
	public final String getM49() {
		return _m49;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setM49(String value) {
		internalSetM49(value);
		return this;
	}

	/** Internal setter for {@link #getM49()} without chain call utility. */
	protected final void internalSetM49(String value) {
		_listener.beforeSet(this, M_49__PROP, value);
		_m49 = value;
	}

	@Override
	public final String getSubRegionCode() {
		return _subRegionCode;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setSubRegionCode(String value) {
		internalSetSubRegionCode(value);
		return this;
	}

	/** Internal setter for {@link #getSubRegionCode()} without chain call utility. */
	protected final void internalSetSubRegionCode(String value) {
		_listener.beforeSet(this, SUB_REGION_CODE__PROP, value);
		_subRegionCode = value;
	}

	@Override
	public final String getRegionCode() {
		return _regionCode;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setRegionCode(String value) {
		internalSetRegionCode(value);
		return this;
	}

	/** Internal setter for {@link #getRegionCode()} without chain call utility. */
	protected final void internalSetRegionCode(String value) {
		_listener.beforeSet(this, REGION_CODE__PROP, value);
		_regionCode = value;
	}

	@Override
	public final String getOfficialNameAr() {
		return _officialNameAr;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setOfficialNameAr(String value) {
		internalSetOfficialNameAr(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameAr()} without chain call utility. */
	protected final void internalSetOfficialNameAr(String value) {
		_listener.beforeSet(this, OFFICIAL_NAME_AR__PROP, value);
		_officialNameAr = value;
	}

	@Override
	public final String getISO4217CurrencyMinorUnit() {
		return _iSO4217CurrencyMinorUnit;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyMinorUnit(String value) {
		internalSetISO4217CurrencyMinorUnit(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyMinorUnit()} without chain call utility. */
	protected final void internalSetISO4217CurrencyMinorUnit(String value) {
		_listener.beforeSet(this, ISO_4217_CURRENCY_MINOR_UNIT__PROP, value);
		_iSO4217CurrencyMinorUnit = value;
	}

	@Override
	public final String getUNTERMArabicFormal() {
		return _uNTERMArabicFormal;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMArabicFormal(String value) {
		internalSetUNTERMArabicFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMArabicFormal()} without chain call utility. */
	protected final void internalSetUNTERMArabicFormal(String value) {
		_listener.beforeSet(this, UNTERM_ARABIC_FORMAL__PROP, value);
		_uNTERMArabicFormal = value;
	}

	@Override
	public final String getUNTERMChineseShort() {
		return _uNTERMChineseShort;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMChineseShort(String value) {
		internalSetUNTERMChineseShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMChineseShort()} without chain call utility. */
	protected final void internalSetUNTERMChineseShort(String value) {
		_listener.beforeSet(this, UNTERM_CHINESE_SHORT__PROP, value);
		_uNTERMChineseShort = value;
	}

	@Override
	public final String getLandLockedDevelopingCountriesLLDC() {
		return _landLockedDevelopingCountriesLLDC;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setLandLockedDevelopingCountriesLLDC(String value) {
		internalSetLandLockedDevelopingCountriesLLDC(value);
		return this;
	}

	/** Internal setter for {@link #getLandLockedDevelopingCountriesLLDC()} without chain call utility. */
	protected final void internalSetLandLockedDevelopingCountriesLLDC(String value) {
		_listener.beforeSet(this, LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__PROP, value);
		_landLockedDevelopingCountriesLLDC = value;
	}

	@Override
	public final String getIntermediateRegionName() {
		return _intermediateRegionName;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setIntermediateRegionName(String value) {
		internalSetIntermediateRegionName(value);
		return this;
	}

	/** Internal setter for {@link #getIntermediateRegionName()} without chain call utility. */
	protected final void internalSetIntermediateRegionName(String value) {
		_listener.beforeSet(this, INTERMEDIATE_REGION_NAME__PROP, value);
		_intermediateRegionName = value;
	}

	@Override
	public final String getOfficialNameEs() {
		return _officialNameEs;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setOfficialNameEs(String value) {
		internalSetOfficialNameEs(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameEs()} without chain call utility. */
	protected final void internalSetOfficialNameEs(String value) {
		_listener.beforeSet(this, OFFICIAL_NAME_ES__PROP, value);
		_officialNameEs = value;
	}

	@Override
	public final String getUNTERMEnglishFormal() {
		return _uNTERMEnglishFormal;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMEnglishFormal(String value) {
		internalSetUNTERMEnglishFormal(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMEnglishFormal()} without chain call utility. */
	protected final void internalSetUNTERMEnglishFormal(String value) {
		_listener.beforeSet(this, UNTERM_ENGLISH_FORMAL__PROP, value);
		_uNTERMEnglishFormal = value;
	}

	@Override
	public final String getOfficialNameCn() {
		return _officialNameCn;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setOfficialNameCn(String value) {
		internalSetOfficialNameCn(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameCn()} without chain call utility. */
	protected final void internalSetOfficialNameCn(String value) {
		_listener.beforeSet(this, OFFICIAL_NAME_CN__PROP, value);
		_officialNameCn = value;
	}

	@Override
	public final String getOfficialNameEn() {
		return _officialNameEn;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setOfficialNameEn(String value) {
		internalSetOfficialNameEn(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameEn()} without chain call utility. */
	protected final void internalSetOfficialNameEn(String value) {
		_listener.beforeSet(this, OFFICIAL_NAME_EN__PROP, value);
		_officialNameEn = value;
	}

	@Override
	public final String getISO4217CurrencyCountryName() {
		return _iSO4217CurrencyCountryName;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setISO4217CurrencyCountryName(String value) {
		internalSetISO4217CurrencyCountryName(value);
		return this;
	}

	/** Internal setter for {@link #getISO4217CurrencyCountryName()} without chain call utility. */
	protected final void internalSetISO4217CurrencyCountryName(String value) {
		_listener.beforeSet(this, ISO_4217_CURRENCY_COUNTRY_NAME__PROP, value);
		_iSO4217CurrencyCountryName = value;
	}

	@Override
	public final String getLeastDevelopedCountriesLDC() {
		return _leastDevelopedCountriesLDC;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setLeastDevelopedCountriesLDC(String value) {
		internalSetLeastDevelopedCountriesLDC(value);
		return this;
	}

	/** Internal setter for {@link #getLeastDevelopedCountriesLDC()} without chain call utility. */
	protected final void internalSetLeastDevelopedCountriesLDC(String value) {
		_listener.beforeSet(this, LEAST_DEVELOPED_COUNTRIES_LDC__PROP, value);
		_leastDevelopedCountriesLDC = value;
	}

	@Override
	public final String getRegionName() {
		return _regionName;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setRegionName(String value) {
		internalSetRegionName(value);
		return this;
	}

	/** Internal setter for {@link #getRegionName()} without chain call utility. */
	protected final void internalSetRegionName(String value) {
		_listener.beforeSet(this, REGION_NAME__PROP, value);
		_regionName = value;
	}

	@Override
	public final String getUNTERMArabicShort() {
		return _uNTERMArabicShort;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setUNTERMArabicShort(String value) {
		internalSetUNTERMArabicShort(value);
		return this;
	}

	/** Internal setter for {@link #getUNTERMArabicShort()} without chain call utility. */
	protected final void internalSetUNTERMArabicShort(String value) {
		_listener.beforeSet(this, UNTERM_ARABIC_SHORT__PROP, value);
		_uNTERMArabicShort = value;
	}

	@Override
	public final String getSubRegionName() {
		return _subRegionName;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setSubRegionName(String value) {
		internalSetSubRegionName(value);
		return this;
	}

	/** Internal setter for {@link #getSubRegionName()} without chain call utility. */
	protected final void internalSetSubRegionName(String value) {
		_listener.beforeSet(this, SUB_REGION_NAME__PROP, value);
		_subRegionName = value;
	}

	@Override
	public final String getOfficialNameRu() {
		return _officialNameRu;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setOfficialNameRu(String value) {
		internalSetOfficialNameRu(value);
		return this;
	}

	/** Internal setter for {@link #getOfficialNameRu()} without chain call utility. */
	protected final void internalSetOfficialNameRu(String value) {
		_listener.beforeSet(this, OFFICIAL_NAME_RU__PROP, value);
		_officialNameRu = value;
	}

	@Override
	public final String getGlobalName() {
		return _globalName;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setGlobalName(String value) {
		internalSetGlobalName(value);
		return this;
	}

	/** Internal setter for {@link #getGlobalName()} without chain call utility. */
	protected final void internalSetGlobalName(String value) {
		_listener.beforeSet(this, GLOBAL_NAME__PROP, value);
		_globalName = value;
	}

	@Override
	public final String getCapital() {
		return _capital;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setCapital(String value) {
		internalSetCapital(value);
		return this;
	}

	/** Internal setter for {@link #getCapital()} without chain call utility. */
	protected final void internalSetCapital(String value) {
		_listener.beforeSet(this, CAPITAL__PROP, value);
		_capital = value;
	}

	@Override
	public final String getContinent() {
		return _continent;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setContinent(String value) {
		internalSetContinent(value);
		return this;
	}

	/** Internal setter for {@link #getContinent()} without chain call utility. */
	protected final void internalSetContinent(String value) {
		_listener.beforeSet(this, CONTINENT__PROP, value);
		_continent = value;
	}

	@Override
	public final String getTLD() {
		return _tLD;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setTLD(String value) {
		internalSetTLD(value);
		return this;
	}

	/** Internal setter for {@link #getTLD()} without chain call utility. */
	protected final void internalSetTLD(String value) {
		_listener.beforeSet(this, TLD__PROP, value);
		_tLD = value;
	}

	@Override
	public final java.util.List<String> getLanguages() {
		return _languages;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setLanguages(java.util.List<? extends String> value) {
		internalSetLanguages(value);
		return this;
	}

	/** Internal setter for {@link #getLanguages()} without chain call utility. */
	protected final void internalSetLanguages(java.util.List<? extends String> value) {
		_languages.clear();
		_languages.addAll(value);
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country addLanguage(String value) {
		internalAddLanguage(value);
		return this;
	}

	/** Implementation of {@link #addLanguage(String)} without chain call utility. */
	protected final void internalAddLanguage(String value) {
		_languages.add(value);
	}

	@Override
	public final void removeLanguage(String value) {
		_languages.remove(value);
	}

	@Override
	public final String getGeonameID() {
		return _geonameID;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setGeonameID(String value) {
		internalSetGeonameID(value);
		return this;
	}

	/** Internal setter for {@link #getGeonameID()} without chain call utility. */
	protected final void internalSetGeonameID(String value) {
		_listener.beforeSet(this, GEONAME_ID__PROP, value);
		_geonameID = value;
	}

	@Override
	public final String getCLDRDisplayName() {
		return _cLDRDisplayName;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setCLDRDisplayName(String value) {
		internalSetCLDRDisplayName(value);
		return this;
	}

	/** Internal setter for {@link #getCLDRDisplayName()} without chain call utility. */
	protected final void internalSetCLDRDisplayName(String value) {
		_listener.beforeSet(this, CLDR_DISPLAY_NAME__PROP, value);
		_cLDRDisplayName = value;
	}

	@Override
	public final String getEDGAR() {
		return _eDGAR;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setEDGAR(String value) {
		internalSetEDGAR(value);
		return this;
	}

	/** Internal setter for {@link #getEDGAR()} without chain call utility. */
	protected final void internalSetEDGAR(String value) {
		_listener.beforeSet(this, EDGAR__PROP, value);
		_eDGAR = value;
	}

	@Override
	public final String getWikidataId() {
		return _wikidataId;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setWikidataId(String value) {
		internalSetWikidataId(value);
		return this;
	}

	/** Internal setter for {@link #getWikidataId()} without chain call utility. */
	protected final void internalSetWikidataId(String value) {
		_listener.beforeSet(this, WIKIDATA_ID__PROP, value);
		_wikidataId = value;
	}

	@Override
	public final java.util.List<String> getTrunkPrefixes() {
		return _trunkPrefixes;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setTrunkPrefixes(java.util.List<? extends String> value) {
		internalSetTrunkPrefixes(value);
		return this;
	}

	/** Internal setter for {@link #getTrunkPrefixes()} without chain call utility. */
	protected final void internalSetTrunkPrefixes(java.util.List<? extends String> value) {
		_trunkPrefixes.clear();
		_trunkPrefixes.addAll(value);
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country addTrunkPrefixe(String value) {
		internalAddTrunkPrefixe(value);
		return this;
	}

	/** Implementation of {@link #addTrunkPrefixe(String)} without chain call utility. */
	protected final void internalAddTrunkPrefixe(String value) {
		_trunkPrefixes.add(value);
	}

	@Override
	public final void removeTrunkPrefixe(String value) {
		_trunkPrefixes.remove(value);
	}

	@Override
	public final java.util.List<String> getInternationalPrefixes() {
		return _internationalPrefixes;
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country setInternationalPrefixes(java.util.List<? extends String> value) {
		internalSetInternationalPrefixes(value);
		return this;
	}

	/** Internal setter for {@link #getInternationalPrefixes()} without chain call utility. */
	protected final void internalSetInternationalPrefixes(java.util.List<? extends String> value) {
		_internationalPrefixes.clear();
		_internationalPrefixes.addAll(value);
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country addInternationalPrefixe(String value) {
		internalAddInternationalPrefixe(value);
		return this;
	}

	/** Implementation of {@link #addInternationalPrefixe(String)} without chain call utility. */
	protected final void internalAddInternationalPrefixe(String value) {
		_internationalPrefixes.add(value);
	}

	@Override
	public final void removeInternationalPrefixe(String value) {
		_internationalPrefixes.remove(value);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.location.model.Country registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.location.model.Country unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return COUNTRY__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
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
			INTERNATIONAL_PREFIXES__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
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
			default: return de.haumacher.phoneblock.location.model.Country.super.get(field);
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
				in.beginArray();
				while (in.hasNext()) {
					addDialPrefixe(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
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
				in.beginArray();
				while (in.hasNext()) {
					addLanguage(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
			}
			break;
			case GEONAME_ID__PROP: setGeonameID(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CLDR_DISPLAY_NAME__PROP: setCLDRDisplayName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case EDGAR__PROP: setEDGAR(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case WIKIDATA_ID__PROP: setWikidataId(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TRUNK_PREFIXES__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addTrunkPrefixe(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
			}
			break;
			case INTERNATIONAL_PREFIXES__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addInternationalPrefixe(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(FIFA__ID);
		out.value(getFIFA());
		out.name(DIAL_PREFIXES__ID);
		{
			java.util.List<String> values = getDialPrefixes();
			out.beginArray(de.haumacher.msgbuf.binary.DataType.STRING, values.size());
			for (String x : values) {
				out.value(x);
			}
			out.endArray();
		}
		out.name(ISO_3166_1_ALPHA_3__ID);
		out.value(getISO31661Alpha3());
		out.name(MARC__ID);
		out.value(getMARC());
		out.name(INDEPENDENT__ID);
		out.value(isIndependent());
		out.name(ISO_3166_1_NUMERIC__ID);
		out.value(getISO31661Numeric());
		out.name(GAUL__ID);
		out.value(getGAUL());
		out.name(FIPS__ID);
		out.value(getFIPS());
		out.name(WMO__ID);
		out.value(getWMO());
		out.name(ISO_3166_1_ALPHA_2__ID);
		out.value(getISO31661Alpha2());
		out.name(ITU__ID);
		out.value(getITU());
		out.name(IOC__ID);
		out.value(getIOC());
		out.name(DS__ID);
		out.value(getDS());
		out.name(UNTERM_SPANISH_FORMAL__ID);
		out.value(getUNTERMSpanishFormal());
		out.name(GLOBAL_CODE__ID);
		out.value(getGlobalCode());
		out.name(INTERMEDIATE_REGION_CODE__ID);
		out.value(getIntermediateRegionCode());
		out.name(OFFICIAL_NAME_FR__ID);
		out.value(getOfficialNameFr());
		out.name(UNTERM_FRENCH_SHORT__ID);
		out.value(getUNTERMFrenchShort());
		out.name(ISO_4217_CURRENCY_NAME__ID);
		out.value(getISO4217CurrencyName());
		out.name(UNTERM_RUSSIAN_FORMAL__ID);
		out.value(getUNTERMRussianFormal());
		out.name(UNTERM_ENGLISH_SHORT__ID);
		out.value(getUNTERMEnglishShort());
		out.name(ISO_4217_CURRENCY_ALPHABETIC_CODE__ID);
		out.value(getISO4217CurrencyAlphabeticCode());
		out.name(SMALL_ISLAND_DEVELOPING_STATES_SIDS__ID);
		out.value(getSmallIslandDevelopingStatesSIDS());
		out.name(UNTERM_SPANISH_SHORT__ID);
		out.value(getUNTERMSpanishShort());
		out.name(ISO_4217_CURRENCY_NUMERIC_CODE__ID);
		out.value(getISO4217CurrencyNumericCode());
		out.name(UNTERM_CHINESE_FORMAL__ID);
		out.value(getUNTERMChineseFormal());
		out.name(UNTERM_FRENCH_FORMAL__ID);
		out.value(getUNTERMFrenchFormal());
		out.name(UNTERM_RUSSIAN_SHORT__ID);
		out.value(getUNTERMRussianShort());
		out.name(M_49__ID);
		out.value(getM49());
		out.name(SUB_REGION_CODE__ID);
		out.value(getSubRegionCode());
		out.name(REGION_CODE__ID);
		out.value(getRegionCode());
		out.name(OFFICIAL_NAME_AR__ID);
		out.value(getOfficialNameAr());
		out.name(ISO_4217_CURRENCY_MINOR_UNIT__ID);
		out.value(getISO4217CurrencyMinorUnit());
		out.name(UNTERM_ARABIC_FORMAL__ID);
		out.value(getUNTERMArabicFormal());
		out.name(UNTERM_CHINESE_SHORT__ID);
		out.value(getUNTERMChineseShort());
		out.name(LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__ID);
		out.value(getLandLockedDevelopingCountriesLLDC());
		out.name(INTERMEDIATE_REGION_NAME__ID);
		out.value(getIntermediateRegionName());
		out.name(OFFICIAL_NAME_ES__ID);
		out.value(getOfficialNameEs());
		out.name(UNTERM_ENGLISH_FORMAL__ID);
		out.value(getUNTERMEnglishFormal());
		out.name(OFFICIAL_NAME_CN__ID);
		out.value(getOfficialNameCn());
		out.name(OFFICIAL_NAME_EN__ID);
		out.value(getOfficialNameEn());
		out.name(ISO_4217_CURRENCY_COUNTRY_NAME__ID);
		out.value(getISO4217CurrencyCountryName());
		out.name(LEAST_DEVELOPED_COUNTRIES_LDC__ID);
		out.value(getLeastDevelopedCountriesLDC());
		out.name(REGION_NAME__ID);
		out.value(getRegionName());
		out.name(UNTERM_ARABIC_SHORT__ID);
		out.value(getUNTERMArabicShort());
		out.name(SUB_REGION_NAME__ID);
		out.value(getSubRegionName());
		out.name(OFFICIAL_NAME_RU__ID);
		out.value(getOfficialNameRu());
		out.name(GLOBAL_NAME__ID);
		out.value(getGlobalName());
		out.name(CAPITAL__ID);
		out.value(getCapital());
		out.name(CONTINENT__ID);
		out.value(getContinent());
		out.name(TLD__ID);
		out.value(getTLD());
		out.name(LANGUAGES__ID);
		{
			java.util.List<String> values = getLanguages();
			out.beginArray(de.haumacher.msgbuf.binary.DataType.STRING, values.size());
			for (String x : values) {
				out.value(x);
			}
			out.endArray();
		}
		out.name(GEONAME_ID__ID);
		out.value(getGeonameID());
		out.name(CLDR_DISPLAY_NAME__ID);
		out.value(getCLDRDisplayName());
		out.name(EDGAR__ID);
		out.value(getEDGAR());
		out.name(WIKIDATA_ID__ID);
		out.value(getWikidataId());
		out.name(TRUNK_PREFIXES__ID);
		{
			java.util.List<String> values = getTrunkPrefixes();
			out.beginArray(de.haumacher.msgbuf.binary.DataType.STRING, values.size());
			for (String x : values) {
				out.value(x);
			}
			out.endArray();
		}
		out.name(INTERNATIONAL_PREFIXES__ID);
		{
			java.util.List<String> values = getInternationalPrefixes();
			out.beginArray(de.haumacher.msgbuf.binary.DataType.STRING, values.size());
			for (String x : values) {
				out.value(x);
			}
			out.endArray();
		}
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.location.model.Country} from a polymorphic composition. */
	public static de.haumacher.phoneblock.location.model.Country readCountry_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.location.model.impl.Country_Impl result = new Country_Impl();
		result.readContent(in);
		return result;
	}

	/** Helper for reading all fields of this instance. */
	protected final void readContent(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		while (in.hasNext()) {
			int field = in.nextName();
			readField(in, field);
		}
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case FIFA__ID: setFIFA(in.nextString()); break;
			case DIAL_PREFIXES__ID: {
				in.beginArray();
				while (in.hasNext()) {
					addDialPrefixe(in.nextString());
				}
				in.endArray();
			}
			break;
			case ISO_3166_1_ALPHA_3__ID: setISO31661Alpha3(in.nextString()); break;
			case MARC__ID: setMARC(in.nextString()); break;
			case INDEPENDENT__ID: setIndependent(in.nextBoolean()); break;
			case ISO_3166_1_NUMERIC__ID: setISO31661Numeric(in.nextString()); break;
			case GAUL__ID: setGAUL(in.nextString()); break;
			case FIPS__ID: setFIPS(in.nextString()); break;
			case WMO__ID: setWMO(in.nextString()); break;
			case ISO_3166_1_ALPHA_2__ID: setISO31661Alpha2(in.nextString()); break;
			case ITU__ID: setITU(in.nextString()); break;
			case IOC__ID: setIOC(in.nextString()); break;
			case DS__ID: setDS(in.nextString()); break;
			case UNTERM_SPANISH_FORMAL__ID: setUNTERMSpanishFormal(in.nextString()); break;
			case GLOBAL_CODE__ID: setGlobalCode(in.nextString()); break;
			case INTERMEDIATE_REGION_CODE__ID: setIntermediateRegionCode(in.nextString()); break;
			case OFFICIAL_NAME_FR__ID: setOfficialNameFr(in.nextString()); break;
			case UNTERM_FRENCH_SHORT__ID: setUNTERMFrenchShort(in.nextString()); break;
			case ISO_4217_CURRENCY_NAME__ID: setISO4217CurrencyName(in.nextString()); break;
			case UNTERM_RUSSIAN_FORMAL__ID: setUNTERMRussianFormal(in.nextString()); break;
			case UNTERM_ENGLISH_SHORT__ID: setUNTERMEnglishShort(in.nextString()); break;
			case ISO_4217_CURRENCY_ALPHABETIC_CODE__ID: setISO4217CurrencyAlphabeticCode(in.nextString()); break;
			case SMALL_ISLAND_DEVELOPING_STATES_SIDS__ID: setSmallIslandDevelopingStatesSIDS(in.nextString()); break;
			case UNTERM_SPANISH_SHORT__ID: setUNTERMSpanishShort(in.nextString()); break;
			case ISO_4217_CURRENCY_NUMERIC_CODE__ID: setISO4217CurrencyNumericCode(in.nextString()); break;
			case UNTERM_CHINESE_FORMAL__ID: setUNTERMChineseFormal(in.nextString()); break;
			case UNTERM_FRENCH_FORMAL__ID: setUNTERMFrenchFormal(in.nextString()); break;
			case UNTERM_RUSSIAN_SHORT__ID: setUNTERMRussianShort(in.nextString()); break;
			case M_49__ID: setM49(in.nextString()); break;
			case SUB_REGION_CODE__ID: setSubRegionCode(in.nextString()); break;
			case REGION_CODE__ID: setRegionCode(in.nextString()); break;
			case OFFICIAL_NAME_AR__ID: setOfficialNameAr(in.nextString()); break;
			case ISO_4217_CURRENCY_MINOR_UNIT__ID: setISO4217CurrencyMinorUnit(in.nextString()); break;
			case UNTERM_ARABIC_FORMAL__ID: setUNTERMArabicFormal(in.nextString()); break;
			case UNTERM_CHINESE_SHORT__ID: setUNTERMChineseShort(in.nextString()); break;
			case LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__ID: setLandLockedDevelopingCountriesLLDC(in.nextString()); break;
			case INTERMEDIATE_REGION_NAME__ID: setIntermediateRegionName(in.nextString()); break;
			case OFFICIAL_NAME_ES__ID: setOfficialNameEs(in.nextString()); break;
			case UNTERM_ENGLISH_FORMAL__ID: setUNTERMEnglishFormal(in.nextString()); break;
			case OFFICIAL_NAME_CN__ID: setOfficialNameCn(in.nextString()); break;
			case OFFICIAL_NAME_EN__ID: setOfficialNameEn(in.nextString()); break;
			case ISO_4217_CURRENCY_COUNTRY_NAME__ID: setISO4217CurrencyCountryName(in.nextString()); break;
			case LEAST_DEVELOPED_COUNTRIES_LDC__ID: setLeastDevelopedCountriesLDC(in.nextString()); break;
			case REGION_NAME__ID: setRegionName(in.nextString()); break;
			case UNTERM_ARABIC_SHORT__ID: setUNTERMArabicShort(in.nextString()); break;
			case SUB_REGION_NAME__ID: setSubRegionName(in.nextString()); break;
			case OFFICIAL_NAME_RU__ID: setOfficialNameRu(in.nextString()); break;
			case GLOBAL_NAME__ID: setGlobalName(in.nextString()); break;
			case CAPITAL__ID: setCapital(in.nextString()); break;
			case CONTINENT__ID: setContinent(in.nextString()); break;
			case TLD__ID: setTLD(in.nextString()); break;
			case LANGUAGES__ID: {
				in.beginArray();
				while (in.hasNext()) {
					addLanguage(in.nextString());
				}
				in.endArray();
			}
			break;
			case GEONAME_ID__ID: setGeonameID(in.nextString()); break;
			case CLDR_DISPLAY_NAME__ID: setCLDRDisplayName(in.nextString()); break;
			case EDGAR__ID: setEDGAR(in.nextString()); break;
			case WIKIDATA_ID__ID: setWikidataId(in.nextString()); break;
			case TRUNK_PREFIXES__ID: {
				in.beginArray();
				while (in.hasNext()) {
					addTrunkPrefixe(in.nextString());
				}
				in.endArray();
			}
			break;
			case INTERNATIONAL_PREFIXES__ID: {
				in.beginArray();
				while (in.hasNext()) {
					addInternationalPrefixe(in.nextString());
				}
				in.endArray();
			}
			break;
			default: in.skipValue(); 
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.location.model.Country} type. */
	public static final String COUNTRY__XML_ELEMENT = "country";

	/** XML attribute or element name of a {@link #getFIFA} property. */
	private static final String FIFA__XML_ATTR = "FIFA";

	/** XML attribute or element name of a {@link #getDialPrefixes} property. */
	private static final String DIAL_PREFIXES__XML_ATTR = "Dial";

	/** XML attribute or element name of a {@link #getISO31661Alpha3} property. */
	private static final String ISO_3166_1_ALPHA_3__XML_ATTR = "ISO3166-1-Alpha-3";

	/** XML attribute or element name of a {@link #getMARC} property. */
	private static final String MARC__XML_ATTR = "MARC";

	/** XML attribute or element name of a {@link #isIndependent} property. */
	private static final String INDEPENDENT__XML_ATTR = "is_independent";

	/** XML attribute or element name of a {@link #getISO31661Numeric} property. */
	private static final String ISO_3166_1_NUMERIC__XML_ATTR = "ISO3166-1-numeric";

	/** XML attribute or element name of a {@link #getGAUL} property. */
	private static final String GAUL__XML_ATTR = "GAUL";

	/** XML attribute or element name of a {@link #getFIPS} property. */
	private static final String FIPS__XML_ATTR = "FIPS";

	/** XML attribute or element name of a {@link #getWMO} property. */
	private static final String WMO__XML_ATTR = "WMO";

	/** XML attribute or element name of a {@link #getISO31661Alpha2} property. */
	private static final String ISO_3166_1_ALPHA_2__XML_ATTR = "ISO3166-1-Alpha-2";

	/** XML attribute or element name of a {@link #getITU} property. */
	private static final String ITU__XML_ATTR = "ITU";

	/** XML attribute or element name of a {@link #getIOC} property. */
	private static final String IOC__XML_ATTR = "IOC";

	/** XML attribute or element name of a {@link #getDS} property. */
	private static final String DS__XML_ATTR = "DS";

	/** XML attribute or element name of a {@link #getUNTERMSpanishFormal} property. */
	private static final String UNTERM_SPANISH_FORMAL__XML_ATTR = "UNTERM Spanish Formal";

	/** XML attribute or element name of a {@link #getGlobalCode} property. */
	private static final String GLOBAL_CODE__XML_ATTR = "Global Code";

	/** XML attribute or element name of a {@link #getIntermediateRegionCode} property. */
	private static final String INTERMEDIATE_REGION_CODE__XML_ATTR = "Intermediate Region Code";

	/** XML attribute or element name of a {@link #getOfficialNameFr} property. */
	private static final String OFFICIAL_NAME_FR__XML_ATTR = "official_name_fr";

	/** XML attribute or element name of a {@link #getUNTERMFrenchShort} property. */
	private static final String UNTERM_FRENCH_SHORT__XML_ATTR = "UNTERM French Short";

	/** XML attribute or element name of a {@link #getISO4217CurrencyName} property. */
	private static final String ISO_4217_CURRENCY_NAME__XML_ATTR = "ISO4217-currency_name";

	/** XML attribute or element name of a {@link #getUNTERMRussianFormal} property. */
	private static final String UNTERM_RUSSIAN_FORMAL__XML_ATTR = "UNTERM Russian Formal";

	/** XML attribute or element name of a {@link #getUNTERMEnglishShort} property. */
	private static final String UNTERM_ENGLISH_SHORT__XML_ATTR = "UNTERM English Short";

	/** XML attribute or element name of a {@link #getISO4217CurrencyAlphabeticCode} property. */
	private static final String ISO_4217_CURRENCY_ALPHABETIC_CODE__XML_ATTR = "ISO4217-currency_alphabetic_code";

	/** XML attribute or element name of a {@link #getSmallIslandDevelopingStatesSIDS} property. */
	private static final String SMALL_ISLAND_DEVELOPING_STATES_SIDS__XML_ATTR = "Small Island Developing States (SIDS)";

	/** XML attribute or element name of a {@link #getUNTERMSpanishShort} property. */
	private static final String UNTERM_SPANISH_SHORT__XML_ATTR = "UNTERM Spanish Short";

	/** XML attribute or element name of a {@link #getISO4217CurrencyNumericCode} property. */
	private static final String ISO_4217_CURRENCY_NUMERIC_CODE__XML_ATTR = "ISO4217-currency_numeric_code";

	/** XML attribute or element name of a {@link #getUNTERMChineseFormal} property. */
	private static final String UNTERM_CHINESE_FORMAL__XML_ATTR = "UNTERM Chinese Formal";

	/** XML attribute or element name of a {@link #getUNTERMFrenchFormal} property. */
	private static final String UNTERM_FRENCH_FORMAL__XML_ATTR = "UNTERM French Formal";

	/** XML attribute or element name of a {@link #getUNTERMRussianShort} property. */
	private static final String UNTERM_RUSSIAN_SHORT__XML_ATTR = "UNTERM Russian Short";

	/** XML attribute or element name of a {@link #getM49} property. */
	private static final String M_49__XML_ATTR = "M49";

	/** XML attribute or element name of a {@link #getSubRegionCode} property. */
	private static final String SUB_REGION_CODE__XML_ATTR = "Sub-region Code";

	/** XML attribute or element name of a {@link #getRegionCode} property. */
	private static final String REGION_CODE__XML_ATTR = "Region Code";

	/** XML attribute or element name of a {@link #getOfficialNameAr} property. */
	private static final String OFFICIAL_NAME_AR__XML_ATTR = "official_name_ar";

	/** XML attribute or element name of a {@link #getISO4217CurrencyMinorUnit} property. */
	private static final String ISO_4217_CURRENCY_MINOR_UNIT__XML_ATTR = "ISO4217-currency_minor_unit";

	/** XML attribute or element name of a {@link #getUNTERMArabicFormal} property. */
	private static final String UNTERM_ARABIC_FORMAL__XML_ATTR = "UNTERM Arabic Formal";

	/** XML attribute or element name of a {@link #getUNTERMChineseShort} property. */
	private static final String UNTERM_CHINESE_SHORT__XML_ATTR = "UNTERM Chinese Short";

	/** XML attribute or element name of a {@link #getLandLockedDevelopingCountriesLLDC} property. */
	private static final String LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__XML_ATTR = "Land Locked Developing Countries (LLDC)";

	/** XML attribute or element name of a {@link #getIntermediateRegionName} property. */
	private static final String INTERMEDIATE_REGION_NAME__XML_ATTR = "Intermediate Region Name";

	/** XML attribute or element name of a {@link #getOfficialNameEs} property. */
	private static final String OFFICIAL_NAME_ES__XML_ATTR = "official_name_es";

	/** XML attribute or element name of a {@link #getUNTERMEnglishFormal} property. */
	private static final String UNTERM_ENGLISH_FORMAL__XML_ATTR = "UNTERM English Formal";

	/** XML attribute or element name of a {@link #getOfficialNameCn} property. */
	private static final String OFFICIAL_NAME_CN__XML_ATTR = "official_name_cn";

	/** XML attribute or element name of a {@link #getOfficialNameEn} property. */
	private static final String OFFICIAL_NAME_EN__XML_ATTR = "official_name_en";

	/** XML attribute or element name of a {@link #getISO4217CurrencyCountryName} property. */
	private static final String ISO_4217_CURRENCY_COUNTRY_NAME__XML_ATTR = "ISO4217-currency_country_name";

	/** XML attribute or element name of a {@link #getLeastDevelopedCountriesLDC} property. */
	private static final String LEAST_DEVELOPED_COUNTRIES_LDC__XML_ATTR = "Least Developed Countries (LDC)";

	/** XML attribute or element name of a {@link #getRegionName} property. */
	private static final String REGION_NAME__XML_ATTR = "Region Name";

	/** XML attribute or element name of a {@link #getUNTERMArabicShort} property. */
	private static final String UNTERM_ARABIC_SHORT__XML_ATTR = "UNTERM Arabic Short";

	/** XML attribute or element name of a {@link #getSubRegionName} property. */
	private static final String SUB_REGION_NAME__XML_ATTR = "Sub-region Name";

	/** XML attribute or element name of a {@link #getOfficialNameRu} property. */
	private static final String OFFICIAL_NAME_RU__XML_ATTR = "official_name_ru";

	/** XML attribute or element name of a {@link #getGlobalName} property. */
	private static final String GLOBAL_NAME__XML_ATTR = "Global Name";

	/** XML attribute or element name of a {@link #getCapital} property. */
	private static final String CAPITAL__XML_ATTR = "Capital";

	/** XML attribute or element name of a {@link #getContinent} property. */
	private static final String CONTINENT__XML_ATTR = "Continent";

	/** XML attribute or element name of a {@link #getTLD} property. */
	private static final String TLD__XML_ATTR = "TLD";

	/** XML attribute or element name of a {@link #getLanguages} property. */
	private static final String LANGUAGES__XML_ATTR = "Languages";

	/** XML attribute or element name of a {@link #getGeonameID} property. */
	private static final String GEONAME_ID__XML_ATTR = "Geoname ID";

	/** XML attribute or element name of a {@link #getCLDRDisplayName} property. */
	private static final String CLDR_DISPLAY_NAME__XML_ATTR = "CLDR display name";

	/** XML attribute or element name of a {@link #getEDGAR} property. */
	private static final String EDGAR__XML_ATTR = "EDGAR";

	/** XML attribute or element name of a {@link #getWikidataId} property. */
	private static final String WIKIDATA_ID__XML_ATTR = "wikidata_id";

	/** XML attribute or element name of a {@link #getTrunkPrefixes} property. */
	private static final String TRUNK_PREFIXES__XML_ATTR = "TrunkPrefix";

	/** XML attribute or element name of a {@link #getInternationalPrefixes} property. */
	private static final String INTERNATIONAL_PREFIXES__XML_ATTR = "InternationalPrefix";

	@Override
	public String getXmlTagName() {
		return COUNTRY__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(FIFA__XML_ATTR, getFIFA());
		out.writeAttribute(DIAL_PREFIXES__XML_ATTR, getDialPrefixes().stream().map(x -> x).collect(java.util.stream.Collectors.joining(", ")));
		out.writeAttribute(ISO_3166_1_ALPHA_3__XML_ATTR, getISO31661Alpha3());
		out.writeAttribute(MARC__XML_ATTR, getMARC());
		out.writeAttribute(INDEPENDENT__XML_ATTR, Boolean.toString(isIndependent()));
		out.writeAttribute(ISO_3166_1_NUMERIC__XML_ATTR, getISO31661Numeric());
		out.writeAttribute(GAUL__XML_ATTR, getGAUL());
		out.writeAttribute(FIPS__XML_ATTR, getFIPS());
		out.writeAttribute(WMO__XML_ATTR, getWMO());
		out.writeAttribute(ISO_3166_1_ALPHA_2__XML_ATTR, getISO31661Alpha2());
		out.writeAttribute(ITU__XML_ATTR, getITU());
		out.writeAttribute(IOC__XML_ATTR, getIOC());
		out.writeAttribute(DS__XML_ATTR, getDS());
		out.writeAttribute(UNTERM_SPANISH_FORMAL__XML_ATTR, getUNTERMSpanishFormal());
		out.writeAttribute(GLOBAL_CODE__XML_ATTR, getGlobalCode());
		out.writeAttribute(INTERMEDIATE_REGION_CODE__XML_ATTR, getIntermediateRegionCode());
		out.writeAttribute(OFFICIAL_NAME_FR__XML_ATTR, getOfficialNameFr());
		out.writeAttribute(UNTERM_FRENCH_SHORT__XML_ATTR, getUNTERMFrenchShort());
		out.writeAttribute(ISO_4217_CURRENCY_NAME__XML_ATTR, getISO4217CurrencyName());
		out.writeAttribute(UNTERM_RUSSIAN_FORMAL__XML_ATTR, getUNTERMRussianFormal());
		out.writeAttribute(UNTERM_ENGLISH_SHORT__XML_ATTR, getUNTERMEnglishShort());
		out.writeAttribute(ISO_4217_CURRENCY_ALPHABETIC_CODE__XML_ATTR, getISO4217CurrencyAlphabeticCode());
		out.writeAttribute(SMALL_ISLAND_DEVELOPING_STATES_SIDS__XML_ATTR, getSmallIslandDevelopingStatesSIDS());
		out.writeAttribute(UNTERM_SPANISH_SHORT__XML_ATTR, getUNTERMSpanishShort());
		out.writeAttribute(ISO_4217_CURRENCY_NUMERIC_CODE__XML_ATTR, getISO4217CurrencyNumericCode());
		out.writeAttribute(UNTERM_CHINESE_FORMAL__XML_ATTR, getUNTERMChineseFormal());
		out.writeAttribute(UNTERM_FRENCH_FORMAL__XML_ATTR, getUNTERMFrenchFormal());
		out.writeAttribute(UNTERM_RUSSIAN_SHORT__XML_ATTR, getUNTERMRussianShort());
		out.writeAttribute(M_49__XML_ATTR, getM49());
		out.writeAttribute(SUB_REGION_CODE__XML_ATTR, getSubRegionCode());
		out.writeAttribute(REGION_CODE__XML_ATTR, getRegionCode());
		out.writeAttribute(OFFICIAL_NAME_AR__XML_ATTR, getOfficialNameAr());
		out.writeAttribute(ISO_4217_CURRENCY_MINOR_UNIT__XML_ATTR, getISO4217CurrencyMinorUnit());
		out.writeAttribute(UNTERM_ARABIC_FORMAL__XML_ATTR, getUNTERMArabicFormal());
		out.writeAttribute(UNTERM_CHINESE_SHORT__XML_ATTR, getUNTERMChineseShort());
		out.writeAttribute(LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__XML_ATTR, getLandLockedDevelopingCountriesLLDC());
		out.writeAttribute(INTERMEDIATE_REGION_NAME__XML_ATTR, getIntermediateRegionName());
		out.writeAttribute(OFFICIAL_NAME_ES__XML_ATTR, getOfficialNameEs());
		out.writeAttribute(UNTERM_ENGLISH_FORMAL__XML_ATTR, getUNTERMEnglishFormal());
		out.writeAttribute(OFFICIAL_NAME_CN__XML_ATTR, getOfficialNameCn());
		out.writeAttribute(OFFICIAL_NAME_EN__XML_ATTR, getOfficialNameEn());
		out.writeAttribute(ISO_4217_CURRENCY_COUNTRY_NAME__XML_ATTR, getISO4217CurrencyCountryName());
		out.writeAttribute(LEAST_DEVELOPED_COUNTRIES_LDC__XML_ATTR, getLeastDevelopedCountriesLDC());
		out.writeAttribute(REGION_NAME__XML_ATTR, getRegionName());
		out.writeAttribute(UNTERM_ARABIC_SHORT__XML_ATTR, getUNTERMArabicShort());
		out.writeAttribute(SUB_REGION_NAME__XML_ATTR, getSubRegionName());
		out.writeAttribute(OFFICIAL_NAME_RU__XML_ATTR, getOfficialNameRu());
		out.writeAttribute(GLOBAL_NAME__XML_ATTR, getGlobalName());
		out.writeAttribute(CAPITAL__XML_ATTR, getCapital());
		out.writeAttribute(CONTINENT__XML_ATTR, getContinent());
		out.writeAttribute(TLD__XML_ATTR, getTLD());
		out.writeAttribute(LANGUAGES__XML_ATTR, getLanguages().stream().map(x -> x).collect(java.util.stream.Collectors.joining(", ")));
		out.writeAttribute(GEONAME_ID__XML_ATTR, getGeonameID());
		out.writeAttribute(CLDR_DISPLAY_NAME__XML_ATTR, getCLDRDisplayName());
		out.writeAttribute(EDGAR__XML_ATTR, getEDGAR());
		out.writeAttribute(WIKIDATA_ID__XML_ATTR, getWikidataId());
		out.writeAttribute(TRUNK_PREFIXES__XML_ATTR, getTrunkPrefixes().stream().map(x -> x).collect(java.util.stream.Collectors.joining(", ")));
		out.writeAttribute(INTERNATIONAL_PREFIXES__XML_ATTR, getInternationalPrefixes().stream().map(x -> x).collect(java.util.stream.Collectors.joining(", ")));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.location.model.Country} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static Country_Impl readCountry_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		Country_Impl result = new Country_Impl();
		result.readContentXml(in);
		return result;
	}

	/** Reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	protected final void readContentXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		for (int n = 0, cnt = in.getAttributeCount(); n < cnt; n++) {
			String name = in.getAttributeLocalName(n);
			String value = in.getAttributeValue(n);

			readFieldXmlAttribute(name, value);
		}
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}
			assert event == javax.xml.stream.XMLStreamConstants.START_ELEMENT;

			String localName = in.getLocalName();
			readFieldXmlElement(in, localName);
		}
	}

	/** Parses the given attribute value and assigns it to the field with the given name. */
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case FIFA__XML_ATTR: {
				setFIFA(value);
				break;
			}
			case DIAL_PREFIXES__XML_ATTR: {
				setDialPrefixes(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case ISO_3166_1_ALPHA_3__XML_ATTR: {
				setISO31661Alpha3(value);
				break;
			}
			case MARC__XML_ATTR: {
				setMARC(value);
				break;
			}
			case INDEPENDENT__XML_ATTR: {
				setIndependent(Boolean.parseBoolean(value));
				break;
			}
			case ISO_3166_1_NUMERIC__XML_ATTR: {
				setISO31661Numeric(value);
				break;
			}
			case GAUL__XML_ATTR: {
				setGAUL(value);
				break;
			}
			case FIPS__XML_ATTR: {
				setFIPS(value);
				break;
			}
			case WMO__XML_ATTR: {
				setWMO(value);
				break;
			}
			case ISO_3166_1_ALPHA_2__XML_ATTR: {
				setISO31661Alpha2(value);
				break;
			}
			case ITU__XML_ATTR: {
				setITU(value);
				break;
			}
			case IOC__XML_ATTR: {
				setIOC(value);
				break;
			}
			case DS__XML_ATTR: {
				setDS(value);
				break;
			}
			case UNTERM_SPANISH_FORMAL__XML_ATTR: {
				setUNTERMSpanishFormal(value);
				break;
			}
			case GLOBAL_CODE__XML_ATTR: {
				setGlobalCode(value);
				break;
			}
			case INTERMEDIATE_REGION_CODE__XML_ATTR: {
				setIntermediateRegionCode(value);
				break;
			}
			case OFFICIAL_NAME_FR__XML_ATTR: {
				setOfficialNameFr(value);
				break;
			}
			case UNTERM_FRENCH_SHORT__XML_ATTR: {
				setUNTERMFrenchShort(value);
				break;
			}
			case ISO_4217_CURRENCY_NAME__XML_ATTR: {
				setISO4217CurrencyName(value);
				break;
			}
			case UNTERM_RUSSIAN_FORMAL__XML_ATTR: {
				setUNTERMRussianFormal(value);
				break;
			}
			case UNTERM_ENGLISH_SHORT__XML_ATTR: {
				setUNTERMEnglishShort(value);
				break;
			}
			case ISO_4217_CURRENCY_ALPHABETIC_CODE__XML_ATTR: {
				setISO4217CurrencyAlphabeticCode(value);
				break;
			}
			case SMALL_ISLAND_DEVELOPING_STATES_SIDS__XML_ATTR: {
				setSmallIslandDevelopingStatesSIDS(value);
				break;
			}
			case UNTERM_SPANISH_SHORT__XML_ATTR: {
				setUNTERMSpanishShort(value);
				break;
			}
			case ISO_4217_CURRENCY_NUMERIC_CODE__XML_ATTR: {
				setISO4217CurrencyNumericCode(value);
				break;
			}
			case UNTERM_CHINESE_FORMAL__XML_ATTR: {
				setUNTERMChineseFormal(value);
				break;
			}
			case UNTERM_FRENCH_FORMAL__XML_ATTR: {
				setUNTERMFrenchFormal(value);
				break;
			}
			case UNTERM_RUSSIAN_SHORT__XML_ATTR: {
				setUNTERMRussianShort(value);
				break;
			}
			case M_49__XML_ATTR: {
				setM49(value);
				break;
			}
			case SUB_REGION_CODE__XML_ATTR: {
				setSubRegionCode(value);
				break;
			}
			case REGION_CODE__XML_ATTR: {
				setRegionCode(value);
				break;
			}
			case OFFICIAL_NAME_AR__XML_ATTR: {
				setOfficialNameAr(value);
				break;
			}
			case ISO_4217_CURRENCY_MINOR_UNIT__XML_ATTR: {
				setISO4217CurrencyMinorUnit(value);
				break;
			}
			case UNTERM_ARABIC_FORMAL__XML_ATTR: {
				setUNTERMArabicFormal(value);
				break;
			}
			case UNTERM_CHINESE_SHORT__XML_ATTR: {
				setUNTERMChineseShort(value);
				break;
			}
			case LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__XML_ATTR: {
				setLandLockedDevelopingCountriesLLDC(value);
				break;
			}
			case INTERMEDIATE_REGION_NAME__XML_ATTR: {
				setIntermediateRegionName(value);
				break;
			}
			case OFFICIAL_NAME_ES__XML_ATTR: {
				setOfficialNameEs(value);
				break;
			}
			case UNTERM_ENGLISH_FORMAL__XML_ATTR: {
				setUNTERMEnglishFormal(value);
				break;
			}
			case OFFICIAL_NAME_CN__XML_ATTR: {
				setOfficialNameCn(value);
				break;
			}
			case OFFICIAL_NAME_EN__XML_ATTR: {
				setOfficialNameEn(value);
				break;
			}
			case ISO_4217_CURRENCY_COUNTRY_NAME__XML_ATTR: {
				setISO4217CurrencyCountryName(value);
				break;
			}
			case LEAST_DEVELOPED_COUNTRIES_LDC__XML_ATTR: {
				setLeastDevelopedCountriesLDC(value);
				break;
			}
			case REGION_NAME__XML_ATTR: {
				setRegionName(value);
				break;
			}
			case UNTERM_ARABIC_SHORT__XML_ATTR: {
				setUNTERMArabicShort(value);
				break;
			}
			case SUB_REGION_NAME__XML_ATTR: {
				setSubRegionName(value);
				break;
			}
			case OFFICIAL_NAME_RU__XML_ATTR: {
				setOfficialNameRu(value);
				break;
			}
			case GLOBAL_NAME__XML_ATTR: {
				setGlobalName(value);
				break;
			}
			case CAPITAL__XML_ATTR: {
				setCapital(value);
				break;
			}
			case CONTINENT__XML_ATTR: {
				setContinent(value);
				break;
			}
			case TLD__XML_ATTR: {
				setTLD(value);
				break;
			}
			case LANGUAGES__XML_ATTR: {
				setLanguages(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case GEONAME_ID__XML_ATTR: {
				setGeonameID(value);
				break;
			}
			case CLDR_DISPLAY_NAME__XML_ATTR: {
				setCLDRDisplayName(value);
				break;
			}
			case EDGAR__XML_ATTR: {
				setEDGAR(value);
				break;
			}
			case WIKIDATA_ID__XML_ATTR: {
				setWikidataId(value);
				break;
			}
			case TRUNK_PREFIXES__XML_ATTR: {
				setTrunkPrefixes(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case INTERNATIONAL_PREFIXES__XML_ATTR: {
				setInternationalPrefixes(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			default: {
				// Skip unknown attribute.
			}
		}
	}

	/** Reads the element under the cursor and assigns its contents to the field with the given name. */
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
			case FIFA__XML_ATTR: {
				setFIFA(in.getElementText());
				break;
			}
			case DIAL_PREFIXES__XML_ATTR: {
				setDialPrefixes(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case ISO_3166_1_ALPHA_3__XML_ATTR: {
				setISO31661Alpha3(in.getElementText());
				break;
			}
			case MARC__XML_ATTR: {
				setMARC(in.getElementText());
				break;
			}
			case INDEPENDENT__XML_ATTR: {
				setIndependent(Boolean.parseBoolean(in.getElementText()));
				break;
			}
			case ISO_3166_1_NUMERIC__XML_ATTR: {
				setISO31661Numeric(in.getElementText());
				break;
			}
			case GAUL__XML_ATTR: {
				setGAUL(in.getElementText());
				break;
			}
			case FIPS__XML_ATTR: {
				setFIPS(in.getElementText());
				break;
			}
			case WMO__XML_ATTR: {
				setWMO(in.getElementText());
				break;
			}
			case ISO_3166_1_ALPHA_2__XML_ATTR: {
				setISO31661Alpha2(in.getElementText());
				break;
			}
			case ITU__XML_ATTR: {
				setITU(in.getElementText());
				break;
			}
			case IOC__XML_ATTR: {
				setIOC(in.getElementText());
				break;
			}
			case DS__XML_ATTR: {
				setDS(in.getElementText());
				break;
			}
			case UNTERM_SPANISH_FORMAL__XML_ATTR: {
				setUNTERMSpanishFormal(in.getElementText());
				break;
			}
			case GLOBAL_CODE__XML_ATTR: {
				setGlobalCode(in.getElementText());
				break;
			}
			case INTERMEDIATE_REGION_CODE__XML_ATTR: {
				setIntermediateRegionCode(in.getElementText());
				break;
			}
			case OFFICIAL_NAME_FR__XML_ATTR: {
				setOfficialNameFr(in.getElementText());
				break;
			}
			case UNTERM_FRENCH_SHORT__XML_ATTR: {
				setUNTERMFrenchShort(in.getElementText());
				break;
			}
			case ISO_4217_CURRENCY_NAME__XML_ATTR: {
				setISO4217CurrencyName(in.getElementText());
				break;
			}
			case UNTERM_RUSSIAN_FORMAL__XML_ATTR: {
				setUNTERMRussianFormal(in.getElementText());
				break;
			}
			case UNTERM_ENGLISH_SHORT__XML_ATTR: {
				setUNTERMEnglishShort(in.getElementText());
				break;
			}
			case ISO_4217_CURRENCY_ALPHABETIC_CODE__XML_ATTR: {
				setISO4217CurrencyAlphabeticCode(in.getElementText());
				break;
			}
			case SMALL_ISLAND_DEVELOPING_STATES_SIDS__XML_ATTR: {
				setSmallIslandDevelopingStatesSIDS(in.getElementText());
				break;
			}
			case UNTERM_SPANISH_SHORT__XML_ATTR: {
				setUNTERMSpanishShort(in.getElementText());
				break;
			}
			case ISO_4217_CURRENCY_NUMERIC_CODE__XML_ATTR: {
				setISO4217CurrencyNumericCode(in.getElementText());
				break;
			}
			case UNTERM_CHINESE_FORMAL__XML_ATTR: {
				setUNTERMChineseFormal(in.getElementText());
				break;
			}
			case UNTERM_FRENCH_FORMAL__XML_ATTR: {
				setUNTERMFrenchFormal(in.getElementText());
				break;
			}
			case UNTERM_RUSSIAN_SHORT__XML_ATTR: {
				setUNTERMRussianShort(in.getElementText());
				break;
			}
			case M_49__XML_ATTR: {
				setM49(in.getElementText());
				break;
			}
			case SUB_REGION_CODE__XML_ATTR: {
				setSubRegionCode(in.getElementText());
				break;
			}
			case REGION_CODE__XML_ATTR: {
				setRegionCode(in.getElementText());
				break;
			}
			case OFFICIAL_NAME_AR__XML_ATTR: {
				setOfficialNameAr(in.getElementText());
				break;
			}
			case ISO_4217_CURRENCY_MINOR_UNIT__XML_ATTR: {
				setISO4217CurrencyMinorUnit(in.getElementText());
				break;
			}
			case UNTERM_ARABIC_FORMAL__XML_ATTR: {
				setUNTERMArabicFormal(in.getElementText());
				break;
			}
			case UNTERM_CHINESE_SHORT__XML_ATTR: {
				setUNTERMChineseShort(in.getElementText());
				break;
			}
			case LAND_LOCKED_DEVELOPING_COUNTRIES_LLDC__XML_ATTR: {
				setLandLockedDevelopingCountriesLLDC(in.getElementText());
				break;
			}
			case INTERMEDIATE_REGION_NAME__XML_ATTR: {
				setIntermediateRegionName(in.getElementText());
				break;
			}
			case OFFICIAL_NAME_ES__XML_ATTR: {
				setOfficialNameEs(in.getElementText());
				break;
			}
			case UNTERM_ENGLISH_FORMAL__XML_ATTR: {
				setUNTERMEnglishFormal(in.getElementText());
				break;
			}
			case OFFICIAL_NAME_CN__XML_ATTR: {
				setOfficialNameCn(in.getElementText());
				break;
			}
			case OFFICIAL_NAME_EN__XML_ATTR: {
				setOfficialNameEn(in.getElementText());
				break;
			}
			case ISO_4217_CURRENCY_COUNTRY_NAME__XML_ATTR: {
				setISO4217CurrencyCountryName(in.getElementText());
				break;
			}
			case LEAST_DEVELOPED_COUNTRIES_LDC__XML_ATTR: {
				setLeastDevelopedCountriesLDC(in.getElementText());
				break;
			}
			case REGION_NAME__XML_ATTR: {
				setRegionName(in.getElementText());
				break;
			}
			case UNTERM_ARABIC_SHORT__XML_ATTR: {
				setUNTERMArabicShort(in.getElementText());
				break;
			}
			case SUB_REGION_NAME__XML_ATTR: {
				setSubRegionName(in.getElementText());
				break;
			}
			case OFFICIAL_NAME_RU__XML_ATTR: {
				setOfficialNameRu(in.getElementText());
				break;
			}
			case GLOBAL_NAME__XML_ATTR: {
				setGlobalName(in.getElementText());
				break;
			}
			case CAPITAL__XML_ATTR: {
				setCapital(in.getElementText());
				break;
			}
			case CONTINENT__XML_ATTR: {
				setContinent(in.getElementText());
				break;
			}
			case TLD__XML_ATTR: {
				setTLD(in.getElementText());
				break;
			}
			case LANGUAGES__XML_ATTR: {
				setLanguages(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case GEONAME_ID__XML_ATTR: {
				setGeonameID(in.getElementText());
				break;
			}
			case CLDR_DISPLAY_NAME__XML_ATTR: {
				setCLDRDisplayName(in.getElementText());
				break;
			}
			case EDGAR__XML_ATTR: {
				setEDGAR(in.getElementText());
				break;
			}
			case WIKIDATA_ID__XML_ATTR: {
				setWikidataId(in.getElementText());
				break;
			}
			case TRUNK_PREFIXES__XML_ATTR: {
				setTrunkPrefixes(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case INTERNATIONAL_PREFIXES__XML_ATTR: {
				setInternationalPrefixes(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			default: {
				internalSkipUntilMatchingEndElement(in);
			}
		}
	}

	protected static final void internalSkipUntilMatchingEndElement(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		int level = 0;
		while (true) {
			switch (in.next()) {
				case javax.xml.stream.XMLStreamConstants.START_ELEMENT: level++; break;
				case javax.xml.stream.XMLStreamConstants.END_ELEMENT: if (level == 0) { return; } else { level--; break; }
			}
		}
	}

}
