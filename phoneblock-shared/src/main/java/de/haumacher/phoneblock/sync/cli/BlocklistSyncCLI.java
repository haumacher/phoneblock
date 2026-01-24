package de.haumacher.phoneblock.sync.cli;

import de.haumacher.phoneblock.sync.client.BlocklistClient;
import de.haumacher.phoneblock.sync.client.BlocklistSyncService;
import de.haumacher.phoneblock.sync.client.SyncResult;
import de.haumacher.phoneblock.sync.config.ConfigLoader;
import de.haumacher.phoneblock.sync.config.SyncConfig;
import de.haumacher.phoneblock.sync.filter.VoteThresholdFilter;
import de.haumacher.phoneblock.sync.storage.JsonBlocklistStorage;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Command-line interface for blocklist synchronization.
 */
public class BlocklistSyncCLI {

	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_ERROR = 1;
	private static final int EXIT_AUTH_ERROR = 2;
	private static final int EXIT_NETWORK_ERROR = 3;

	public static void main(String[] args) {
		try {
			SyncConfig config = parseArguments(args);
			config.validate();

			BlocklistClient client = new BlocklistClient(
				config.getApiBaseUrl() + "/api/blocklist",
				config.getBearerToken(),
				config.getConnectionTimeout()
			);

			JsonBlocklistStorage storage = new JsonBlocklistStorage(
				config.getStorageFile(),
				config.isPrettyPrint()
			);

			BlocklistSyncService service = new BlocklistSyncService(client, storage);

			// Add vote threshold filter if configured
			if (config.getMinVotes() > 0) {
				service.addFilter(new VoteThresholdFilter(config.getMinVotes()));
			}

			// Perform sync
			SyncResult result = service.sync();

			// Print result
			printResult(result);

			// Exit with appropriate code
			if (!result.isSuccess()) {
				String error = result.getErrorMessage();
				if (error != null && error.contains("Authentication failed")) {
					System.exit(EXIT_AUTH_ERROR);
				} else if (error != null && (error.contains("HTTP") || error.contains("Connection"))) {
					System.exit(EXIT_NETWORK_ERROR);
				} else {
					System.exit(EXIT_ERROR);
				}
			}

			System.exit(EXIT_SUCCESS);

		} catch (IllegalStateException e) {
			System.err.println("Configuration error: " + e.getMessage());
			System.exit(EXIT_ERROR);
		} catch (IOException e) {
			System.err.println("I/O error: " + e.getMessage());
			System.exit(EXIT_ERROR);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			System.exit(EXIT_ERROR);
		}
	}

	private static SyncConfig parseArguments(String[] args) throws IOException {
		SyncConfig config = null;
		boolean forceFull = false;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];

			switch (arg) {
				case "--help":
				case "-h":
					printHelp();
					System.exit(EXIT_SUCCESS);
					break;

				case "--config":
					if (i + 1 >= args.length) {
						throw new IllegalArgumentException("--config requires a file path");
					}
					config = ConfigLoader.loadFromFile(Paths.get(args[++i]));
					break;

				case "--api-url":
					if (i + 1 >= args.length) {
						throw new IllegalArgumentException("--api-url requires a URL");
					}
					if (config == null) config = ConfigLoader.createDefault();
					config.setApiBaseUrl(args[++i]);
					break;

				case "--bearer-token":
					if (i + 1 >= args.length) {
						throw new IllegalArgumentException("--bearer-token requires a token");
					}
					if (config == null) config = ConfigLoader.createDefault();
					config.setBearerToken(args[++i]);
					break;

				case "--storage":
					if (i + 1 >= args.length) {
						throw new IllegalArgumentException("--storage requires a file path");
					}
					if (config == null) config = ConfigLoader.createDefault();
					config.setStorageFile(Paths.get(args[++i]));
					break;

				case "--min-votes":
					if (i + 1 >= args.length) {
						throw new IllegalArgumentException("--min-votes requires a number");
					}
					if (config == null) config = ConfigLoader.createDefault();
					config.setMinVotes(Integer.parseInt(args[++i]));
					break;

				case "--pretty":
					if (config == null) config = ConfigLoader.createDefault();
					config.setPrettyPrint(true);
					break;

				case "--full":
					forceFull = true;
					break;

				default:
					throw new IllegalArgumentException("Unknown argument: " + arg);
			}
		}

		if (config == null) {
			System.err.println("No configuration provided. Use --config or provide individual options.");
			printHelp();
			System.exit(EXIT_ERROR);
		}

		return config;
	}

	private static void printResult(SyncResult result) {
		if (!result.isSuccess()) {
			System.err.println("Sync failed: " + result.getErrorMessage());
			return;
		}

		if (!result.hasChanges()) {
			System.out.println("No changes (version " + result.getNewVersion() + ")");
			return;
		}

		String syncType = result.wasFull() ? "Full" : "Incremental";
		System.out.println(syncType + " sync completed:");
		System.out.println("  Version: " + result.getPreviousVersion() + " → " + result.getNewVersion());
		System.out.println("  Entries: " + result.getEntryCount());
	}

	private static void printHelp() {
		System.out.println("PhoneBlock Blocklist Synchronization Tool");
		System.out.println();
		System.out.println("Usage: java -jar phoneblock-shared-cli.jar [options]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --config FILE          Load configuration from properties file");
		System.out.println("  --api-url URL          PhoneBlock API base URL");
		System.out.println("                         (default: https://phoneblock.net/phoneblock)");
		System.out.println("  --bearer-token TOKEN   Bearer token for authentication");
		System.out.println("  --storage FILE         Path to JSON storage file");
		System.out.println("  --min-votes N          Minimum votes threshold (default: 4)");
		System.out.println("  --pretty               Format JSON with indentation");
		System.out.println("  --full                 Force full sync (ignore local version)");
		System.out.println("  --help, -h             Show this help message");
		System.out.println();
		System.out.println("Configuration file format (.properties):");
		System.out.println("  sync.api.url=https://phoneblock.net/phoneblock");
		System.out.println("  sync.bearer.token=pbt_xxxxxxxxxxxxxxxxxxxxx");
		System.out.println("  sync.storage.file=/path/to/blocklist.json");
		System.out.println("  sync.min.votes=4");
		System.out.println("  sync.pretty.print=true");
		System.out.println("  sync.connection.timeout=30");
		System.out.println();
		System.out.println("Exit codes:");
		System.out.println("  0 - Success");
		System.out.println("  1 - General error");
		System.out.println("  2 - Authentication error");
		System.out.println("  3 - Network error");
	}
}
