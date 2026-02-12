import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:phoneblock_mobile/fritzbox/fritzbox_models.dart';
import 'package:phoneblock_mobile/storage.dart';
import 'package:sqflite/sqflite.dart';

/// Storage keys for secure credential storage.
const _kFritzboxHost = 'fritzbox_host';
const _kFritzboxUsername = 'fritzbox_username';
const _kFritzboxPassword = 'fritzbox_password';

/// Database helper for Fritz!Box configuration and call history.
class FritzBoxStorage {
  static final FritzBoxStorage instance = FritzBoxStorage._init();

  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );

  FritzBoxStorage._init();

  /// Gets the database instance from the main storage.
  Future<Database> get _database async {
    return await ScreenedCallsDatabase.instance.database;
  }

  // ==================== Credential Storage ====================

  /// Saves Fritz!Box credentials securely.
  Future<void> saveCredentials({
    required String host,
    required String username,
    required String password,
  }) async {
    await _secureStorage.write(key: _kFritzboxHost, value: host);
    await _secureStorage.write(key: _kFritzboxUsername, value: username);
    await _secureStorage.write(key: _kFritzboxPassword, value: password);
  }

  /// Retrieves Fritz!Box credentials.
  /// Returns null if credentials are not stored.
  Future<FritzBoxCredentials?> getCredentials() async {
    final host = await _secureStorage.read(key: _kFritzboxHost);
    final username = await _secureStorage.read(key: _kFritzboxUsername);
    final password = await _secureStorage.read(key: _kFritzboxPassword);

    if (host == null || username == null || password == null) {
      return null;
    }

    return FritzBoxCredentials(
      host: host,
      username: username,
      password: password,
    );
  }

  /// Clears all stored credentials.
  Future<void> clearCredentials() async {
    await _secureStorage.delete(key: _kFritzboxHost);
    await _secureStorage.delete(key: _kFritzboxUsername);
    await _secureStorage.delete(key: _kFritzboxPassword);
  }

  /// Returns true if credentials are stored.
  Future<bool> hasCredentials() async {
    final host = await _secureStorage.read(key: _kFritzboxHost);
    return host != null;
  }

  // ==================== Configuration Storage ====================

  /// Gets the current Fritz!Box configuration.
  /// Returns null if no configuration exists.
  Future<FritzBoxConfig?> getConfig() async {
    final db = await _database;
    final result = await db.query(
      'fritzbox_config',
      where: 'id = ?',
      whereArgs: [1],
    );

    if (result.isEmpty) {
      return null;
    }

    return FritzBoxConfig.fromMap(result.first);
  }

  /// Saves or updates the Fritz!Box configuration.
  Future<void> saveConfig(FritzBoxConfig config) async {
    final db = await _database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final map = config.toMap();
    map['updated_at'] = now;

    final existing = await getConfig();
    if (existing == null) {
      map['created_at'] = now;
      await db.insert('fritzbox_config', map);
    } else {
      await db.update(
        'fritzbox_config',
        map,
        where: 'id = ?',
        whereArgs: [1],
      );
    }
  }

  /// Updates specific fields in the configuration.
  Future<void> updateConfig({
    String? host,
    String? fritzosVersion,
    BlocklistMode? blocklistMode,
    bool? answerbotEnabled,
    int? answerbotId,
    int? lastFetchTimestamp,
    String? blocklistVersion,
    String? phonebookId,
    String? sipDeviceId,
  }) async {
    final existing = await getConfig();
    final now = DateTime.now().millisecondsSinceEpoch;

    final config = existing?.copyWith(
      host: host,
      fritzosVersion: fritzosVersion,
      blocklistMode: blocklistMode,
      answerbotEnabled: answerbotEnabled,
      answerbotId: answerbotId,
      lastFetchTimestamp: lastFetchTimestamp,
      blocklistVersion: blocklistVersion,
      phonebookId: phonebookId,
      sipDeviceId: sipDeviceId,
      updatedAt: now,
    ) ?? FritzBoxConfig(
      host: host,
      fritzosVersion: fritzosVersion,
      blocklistMode: blocklistMode ?? BlocklistMode.none,
      answerbotEnabled: answerbotEnabled ?? false,
      answerbotId: answerbotId,
      lastFetchTimestamp: lastFetchTimestamp,
      blocklistVersion: blocklistVersion,
      phonebookId: phonebookId,
      sipDeviceId: sipDeviceId,
      createdAt: now,
      updatedAt: now,
    );

    await saveConfig(config);
  }

  /// Deletes the Fritz!Box configuration.
  Future<void> deleteConfig() async {
    final db = await _database;
    await db.delete('fritzbox_config', where: 'id = ?', whereArgs: [1]);
  }

}

/// Represents Fritz!Box login credentials.
class FritzBoxCredentials {
  final String host;
  final String username;
  final String password;

  FritzBoxCredentials({
    required this.host,
    required this.username,
    required this.password,
  });
}
