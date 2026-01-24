package de.haumacher.phoneblock.sync.client;

import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.sync.filter.BlocklistFilter;
import de.haumacher.phoneblock.sync.storage.BlocklistStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core synchronization service for blocklists.
 */
public class BlocklistSyncService {
	private static final Logger LOG = LoggerFactory.getLogger(BlocklistSyncService.class);

	private final BlocklistClient client;
	private final BlocklistStorage storage;
	private final List<BlocklistFilter> filters = new ArrayList<>();

	/**
	 * Creates a sync service.
	 *
	 * @param client The HTTP client for downloading blocklists.
	 * @param storage The storage for persisting blocklists.
	 */
	public BlocklistSyncService(BlocklistClient client, BlocklistStorage storage) {
		this.client = client;
		this.storage = storage;
	}

	/**
	 * Adds a filter to apply to synced blocklists.
	 *
	 * @param filter The filter to add.
	 */
	public void addFilter(BlocklistFilter filter) {
		filters.add(filter);
	}

	/**
	 * Synchronizes the blocklist.
	 *
	 * @return The result of the sync operation.
	 */
	public SyncResult sync() {
		return sync(false);
	}

	/**
	 * Synchronizes the blocklist.
	 *
	 * @param forceFull If true, performs a full sync even if incremental is possible.
	 * @return The result of the sync operation.
	 */
	public SyncResult sync(boolean forceFull) {
		try {
			long currentVersion = storage.getVersion();
			LOG.info("Starting sync from version {}", currentVersion);

			Blocklist update;
			boolean isFull;

			if (currentVersion == 0 || forceFull) {
				// First sync or forced full sync - download complete blocklist
				LOG.info("Performing full sync");
				update = client.downloadFullBlocklist();
				isFull = true;
			} else {
				// Incremental sync
				LOG.info("Performing incremental sync");
				update = client.downloadIncrementalUpdate(currentVersion);
				isFull = false;
			}

			if (update.getVersion() == currentVersion) {
				LOG.info("No changes (version {} unchanged)", currentVersion);
				return SyncResult.noChanges(currentVersion);
			}

			Blocklist merged;
			if (isFull) {
				merged = update;
			} else {
				// Load current blocklist and merge updates
				Blocklist current = storage.load();
				merged = mergeUpdates(current, update);
			}

			// Apply filters
			for (BlocklistFilter filter : filters) {
				LOG.debug("Applying filter: {}", filter.getClass().getSimpleName());
				merged = filter.apply(merged);
			}

			// Save to storage
			storage.save(merged);

			LOG.info("Sync completed: {} -> {} ({} entries, {})",
				currentVersion, merged.getVersion(), merged.getNumbers().size(),
				isFull ? "full" : "incremental");

			return SyncResult.success(currentVersion, merged.getVersion(),
				merged.getNumbers().size(), isFull);

		} catch (IOException e) {
			LOG.error("Sync failed", e);
			return SyncResult.failure(e.getMessage());
		}
	}

	/**
	 * Merges incremental updates into the current blocklist.
	 *
	 * @param current The current blocklist (may be null).
	 * @param updates The incremental updates.
	 * @return The merged blocklist.
	 */
	private Blocklist mergeUpdates(Blocklist current, Blocklist updates) {
		Map<String, BlockListEntry> phoneMap = new HashMap<>();

		// Start with current entries
		if (current != null) {
			for (BlockListEntry entry : current.getNumbers()) {
				phoneMap.put(entry.getPhone(), entry);
			}
			LOG.debug("Loaded {} existing entries", phoneMap.size());
		}

		// Apply updates
		int added = 0;
		int updated = 0;
		int deleted = 0;

		for (BlockListEntry update : updates.getNumbers()) {
			if (update.getVotes() <= 0) {
				// Deletion: votes <= 0 means remove
				if (phoneMap.remove(update.getPhone()) != null) {
					deleted++;
				}
			} else {
				// Addition or update
				BlockListEntry existing = phoneMap.put(update.getPhone(), update);
				if (existing == null) {
					added++;
				} else {
					updated++;
				}
			}
		}

		LOG.info("Merge: +{} added, ~{} updated, -{} deleted (total: {})",
			added, updated, deleted, phoneMap.size());

		// Build result
		return Blocklist.create()
			.setVersion(updates.getVersion())
			.setNumbers(new ArrayList<>(phoneMap.values()));
	}
}
