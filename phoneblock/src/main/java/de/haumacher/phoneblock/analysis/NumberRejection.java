/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

/**
 * The reason why {@link NumberAnalyzer} does not accept a text as phone number.
 *
 * @see NumberAnalyzer#diagnose(String, String)
 */
public class NumberRejection {

	/**
	 * The kind of rejection.
	 */
	public enum Reason {
		/**
		 * The text contains no digits, or a wildcard, or starts with <code>000</code>.
		 */
		NO_NUMBER,

		/**
		 * The text is too short for a complete phone number.
		 */
		TOO_SHORT,

		/**
		 * A number in national format does not start with the trunk prefix of the user's country.
		 */
		MISSING_TRUNK_PREFIX,

		/**
		 * The number does not start with any assigned country code.
		 */
		UNKNOWN_COUNTRY_CODE,

		/**
		 * The country code is followed by the trunk prefix of that country, e.g. <code>+49 0...</code>.
		 */
		TRUNK_PREFIX_AFTER_COUNTRY_CODE,

		/**
		 * The number has fewer digits than the numbering plan requires for its area or service code.
		 */
		TOO_SHORT_FOR_PREFIX,
	}

	private final Reason _reason;
	private String _plus;
	private String _countryCode;
	private String _trunkPrefix;
	private String _prefix;
	private int _minDigits;
	private int _digits;

	NumberRejection(Reason reason) {
		_reason = reason;
	}

	/**
	 * The kind of rejection.
	 */
	public Reason getReason() {
		return _reason;
	}

	/**
	 * The number in international format, if it could be computed.
	 */
	public String getPlus() {
		return _plus;
	}

	NumberRejection setPlus(String plus) {
		_plus = plus;
		return this;
	}

	/**
	 * The country code (e.g. <code>+49</code>) of the number, or of the user's country for
	 * {@link Reason#MISSING_TRUNK_PREFIX}.
	 */
	public String getCountryCode() {
		return _countryCode;
	}

	NumberRejection setCountryCode(String countryCode) {
		_countryCode = countryCode;
		return this;
	}

	/**
	 * The trunk prefix (e.g. <code>0</code>) that is missing or misplaced.
	 */
	public String getTrunkPrefix() {
		return _trunkPrefix;
	}

	NumberRejection setTrunkPrefix(String trunkPrefix) {
		_trunkPrefix = trunkPrefix;
		return this;
	}

	/**
	 * The area or service code in national format (e.g. <code>032</code>) for
	 * {@link Reason#TOO_SHORT_FOR_PREFIX}.
	 */
	public String getPrefix() {
		return _prefix;
	}

	NumberRejection setPrefix(String prefix) {
		_prefix = prefix;
		return this;
	}

	/**
	 * The minimum number of digits after the country code required for {@link #getPrefix()}.
	 */
	public int getMinDigits() {
		return _minDigits;
	}

	NumberRejection setMinDigits(int minDigits) {
		_minDigits = minDigits;
		return this;
	}

	/**
	 * The actual number of digits after the country code.
	 */
	public int getDigits() {
		return _digits;
	}

	NumberRejection setDigits(int digits) {
		_digits = digits;
		return this;
	}

	@Override
	public String toString() {
		return _reason.name();
	}

}
