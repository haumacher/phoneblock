package de.haumacher.phoneblock.sync.filter;

import de.haumacher.phoneblock.app.api.model.Blocklist;

/**
 * Filter that can be applied to a blocklist to modify its contents.
 */
public interface BlocklistFilter {
	/**
	 * Applies this filter to the given blocklist.
	 *
	 * @param blocklist The blocklist to filter.
	 * @return A new filtered blocklist.
	 */
	Blocklist apply(Blocklist blocklist);
}
