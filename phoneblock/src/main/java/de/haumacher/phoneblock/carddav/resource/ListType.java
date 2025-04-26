package de.haumacher.phoneblock.carddav.resource;

import java.util.Objects;

final class ListType {
	private final int _minVotes;
	private final int _maxLength;
	private final boolean _wildcards;
	private String _dialPrefix;
	private boolean _national;

	public ListType(String dialPrefix, int minVotes, int maxLength, boolean wildcards, boolean national) {
		_dialPrefix = dialPrefix;
		_minVotes = minVotes;
		_maxLength = maxLength;
		_wildcards = wildcards;
		_national = national;
	}
	
	public static ListType valueOf(String dialPrefix, int minVotes, int maxLength, boolean wildcards, boolean national) {
		return new ListType(dialPrefix, minVotes, maxLength, wildcards, national);
	}

	public int getMinVotes() {
		return _minVotes;
	}

	public int getMaxLength() {
		return _maxLength;
	}
	
	public boolean useWildcards() {
		return _wildcards;
	}

	public boolean isNationalOnly() {
		return _national;
	}
	
	public String getDialPrefix() {
		return _dialPrefix;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(_dialPrefix, _maxLength, _minVotes, _national, _wildcards);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ListType other = (ListType) obj;
		return Objects.equals(_dialPrefix, other._dialPrefix) && _maxLength == other._maxLength
				&& _minVotes == other._minVotes && _national == other._national && _wildcards == other._wildcards;
	}

}