package de.haumacher.phoneblock.sync.filter;

import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter that removes entries below a minimum vote threshold.
 */
public class VoteThresholdFilter implements BlocklistFilter {
	private final int minVotes;

	/**
	 * Creates a vote threshold filter.
	 *
	 * @param minVotes Minimum number of votes required for an entry to be kept.
	 */
	public VoteThresholdFilter(int minVotes) {
		this.minVotes = minVotes;
	}

	@Override
	public Blocklist apply(Blocklist blocklist) {
		List<BlockListEntry> filtered = blocklist.getNumbers().stream()
			.filter(entry -> entry.getVotes() >= minVotes)
			.collect(Collectors.toList());

		return Blocklist.create()
			.setVersion(blocklist.getVersion())
			.setNumbers(filtered);
	}

	public int getMinVotes() {
		return minVotes;
	}
}
