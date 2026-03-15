package de.haumacher.mailcheck.db;

/**
 * POJO for the {@code EMAIL_CHECK} table, used by MyBatis constructor-based mapping.
 */
public class DBEmailCheck {

	private final String _emailAddress;
	private final boolean _disposable;
	private final long _lastChecked;
	private final String _sourceSystem;

	public DBEmailCheck(String emailAddress, boolean disposable, long lastChecked, String sourceSystem) {
		_emailAddress = emailAddress;
		_disposable = disposable;
		_lastChecked = lastChecked;
		_sourceSystem = sourceSystem;
	}

	public String getEmailAddress() {
		return _emailAddress;
	}

	public boolean isDisposable() {
		return _disposable;
	}

	public long getLastChecked() {
		return _lastChecked;
	}

	public String getSourceSystem() {
		return _sourceSystem;
	}

}
