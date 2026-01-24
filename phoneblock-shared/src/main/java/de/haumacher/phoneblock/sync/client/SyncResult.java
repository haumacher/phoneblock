package de.haumacher.phoneblock.sync.client;

/**
 * Result of a blocklist synchronization operation.
 */
public class SyncResult {
	private final boolean success;
	private final long previousVersion;
	private final long newVersion;
	private final int entryCount;
	private final boolean wasFull;
	private final String errorMessage;

	private SyncResult(boolean success, long previousVersion, long newVersion, int entryCount, boolean wasFull, String errorMessage) {
		this.success = success;
		this.previousVersion = previousVersion;
		this.newVersion = newVersion;
		this.entryCount = entryCount;
		this.wasFull = wasFull;
		this.errorMessage = errorMessage;
	}

	/**
	 * Creates a successful sync result.
	 *
	 * @param previousVersion The version before the sync.
	 * @param newVersion The version after the sync.
	 * @param entryCount Number of entries in the blocklist after sync.
	 * @param wasFull Whether this was a full sync (true) or incremental (false).
	 */
	public static SyncResult success(long previousVersion, long newVersion, int entryCount, boolean wasFull) {
		return new SyncResult(true, previousVersion, newVersion, entryCount, wasFull, null);
	}

	/**
	 * Creates a result indicating no changes were made.
	 *
	 * @param version The current version (unchanged).
	 */
	public static SyncResult noChanges(long version) {
		return new SyncResult(true, version, version, 0, false, null);
	}

	/**
	 * Creates a failed sync result.
	 *
	 * @param errorMessage Description of the error.
	 */
	public static SyncResult failure(String errorMessage) {
		return new SyncResult(false, 0, 0, 0, false, errorMessage);
	}

	/**
	 * Whether the sync operation was successful.
	 */
	public boolean isSuccess() {
		return success;
	}

	/**
	 * The version before the sync operation.
	 */
	public long getPreviousVersion() {
		return previousVersion;
	}

	/**
	 * The version after the sync operation.
	 */
	public long getNewVersion() {
		return newVersion;
	}

	/**
	 * Number of entries in the blocklist after sync (0 if unchanged or failed).
	 */
	public int getEntryCount() {
		return entryCount;
	}

	/**
	 * Whether this was a full sync (true) or incremental update (false).
	 */
	public boolean wasFull() {
		return wasFull;
	}

	/**
	 * Error message if the sync failed, null otherwise.
	 */
	public String getErrorMessage() {
		return errorMessage;
	}

	/**
	 * Whether the version changed during this sync.
	 */
	public boolean hasChanges() {
		return newVersion != previousVersion;
	}
}
