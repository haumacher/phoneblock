/// The source of a call in the unified timeline.
enum CallSource {
  /// Call from mobile phone screening.
  mobile,

  /// Call from Fritz!Box call history.
  fritzbox,
}

/// Fritz!Box call type from the call list.
enum FritzBoxCallType {
  /// Incoming call.
  incoming(1),

  /// Missed call (no answer).
  missed(2),

  /// Outgoing call.
  outgoing(3),

  /// Active incoming call.
  activeIncoming(9),

  /// Rejected incoming call.
  rejected(10),

  /// Active outgoing call.
  activeOutgoing(11),

  /// Incoming call, handled by answering machine.
  tam(6);

  final int code;
  const FritzBoxCallType(this.code);

  static FritzBoxCallType fromCode(int code) {
    return FritzBoxCallType.values.firstWhere(
      (type) => type.code == code,
      orElse: () => FritzBoxCallType.missed,
    );
  }
}

/// Fritz!Box connection states.
enum FritzBoxConnectionState {
  /// No credentials stored.
  notConfigured,

  /// Configured but not on home network.
  offline,

  /// Connected and operational.
  connected,

  /// Connection error.
  error,
}

/// Blocklist sync mode for Fritz!Box integration.
enum BlocklistMode {
  /// No blocklist sync.
  none,

  /// Use app-managed blocklist (recommended).
  appManaged,

  /// Use CardDAV phonebook.
  cardDav,
}

/// Fritz!Box configuration stored in the database.
class FritzBoxConfig {
  final int id;
  final String? host;
  final String? fritzosVersion;
  final String? username;
  final String? password;
  final BlocklistMode blocklistMode;
  final bool answerbotEnabled;
  final int? answerbotId;
  /// Phone time of the last call list sync, for display only.
  final int? lastFetchTimestamp;

  /// Highest Fritz!Box call list ID completely handled by the call list sync
  /// (imported or deliberately skipped). Null before the first sync with ID
  /// tracking (fresh setup or upgrade from the time-based sync).
  final int? lastCallId;

  /// True: calls from callers with a Fritz!Box phonebook name are imported
  /// (shown with that name, not looked up at PhoneBlock). False: they are
  /// skipped. Defaults to true when not set.
  final bool logKnownCallers;
  final String? blocklistVersion;
  final String? phonebookId;
  final String? sipDeviceId;
  final String? sipUsername;
  final String? countryCode;
  final String? intlPrefix;
  final String? trunkPrefix;
  final int createdAt;
  final int updatedAt;

  FritzBoxConfig({
    this.id = 1,
    this.host,
    this.fritzosVersion,
    this.username,
    this.password,
    this.blocklistMode = BlocklistMode.none,
    this.answerbotEnabled = false,
    this.answerbotId,
    this.lastFetchTimestamp,
    this.lastCallId,
    this.logKnownCallers = true,
    this.blocklistVersion,
    this.phonebookId,
    this.sipDeviceId,
    this.sipUsername,
    this.countryCode,
    this.intlPrefix,
    this.trunkPrefix,
    required this.createdAt,
    required this.updatedAt,
  });

  /// Creates a FritzBoxConfig from a database map.
  factory FritzBoxConfig.fromMap(Map<String, dynamic> map) {
    return FritzBoxConfig(
      id: map['id'] as int,
      host: map['host'] as String?,
      fritzosVersion: map['fritzos_version'] as String?,
      username: map['username'] as String?,
      password: map['password'] as String?,
      blocklistMode: _parseBlocklistMode(map['blocklist_mode'] as String?),
      answerbotEnabled: (map['answerbot_enabled'] as int?) == 1,
      answerbotId: map['answerbot_id'] as int?,
      lastFetchTimestamp: map['last_fetch_timestamp'] as int?,
      lastCallId: map['last_call_id'] as int?,
      // Missing value (NULL) means the default: on.
      logKnownCallers: (map['log_known_callers'] as int?) != 0,
      blocklistVersion: map['blocklist_version'] as String?,
      phonebookId: map['phonebook_id'] as String?,
      sipDeviceId: map['sip_device_id'] as String?,
      sipUsername: map['sip_username'] as String?,
      countryCode: map['country_code'] as String?,
      intlPrefix: map['intl_prefix'] as String?,
      trunkPrefix: map['trunk_prefix'] as String?,
      createdAt: map['created_at'] as int,
      updatedAt: map['updated_at'] as int,
    );
  }

  /// Converts to a database map.
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'host': host,
      'fritzos_version': fritzosVersion,
      'username': username,
      'password': password,
      'blocklist_mode': blocklistMode.name,
      'answerbot_enabled': answerbotEnabled ? 1 : 0,
      'answerbot_id': answerbotId,
      'last_fetch_timestamp': lastFetchTimestamp,
      'last_call_id': lastCallId,
      'log_known_callers': logKnownCallers ? 1 : 0,
      'blocklist_version': blocklistVersion,
      'phonebook_id': phonebookId,
      'sip_device_id': sipDeviceId,
      'sip_username': sipUsername,
      'country_code': countryCode,
      'intl_prefix': intlPrefix,
      'trunk_prefix': trunkPrefix,
      'created_at': createdAt,
      'updated_at': updatedAt,
    };
  }

  /// Creates a copy with modified fields.
  FritzBoxConfig copyWith({
    int? id,
    String? host,
    String? fritzosVersion,
    String? username,
    String? password,
    BlocklistMode? blocklistMode,
    bool? answerbotEnabled,
    int? answerbotId,
    int? lastFetchTimestamp,
    int? lastCallId,
    bool? logKnownCallers,
    String? blocklistVersion,
    String? phonebookId,
    String? sipDeviceId,
    String? sipUsername,
    String? countryCode,
    String? intlPrefix,
    String? trunkPrefix,
    int? createdAt,
    int? updatedAt,
  }) {
    return FritzBoxConfig(
      id: id ?? this.id,
      host: host ?? this.host,
      fritzosVersion: fritzosVersion ?? this.fritzosVersion,
      username: username ?? this.username,
      password: password ?? this.password,
      blocklistMode: blocklistMode ?? this.blocklistMode,
      answerbotEnabled: answerbotEnabled ?? this.answerbotEnabled,
      answerbotId: answerbotId ?? this.answerbotId,
      lastFetchTimestamp: lastFetchTimestamp ?? this.lastFetchTimestamp,
      lastCallId: lastCallId ?? this.lastCallId,
      logKnownCallers: logKnownCallers ?? this.logKnownCallers,
      blocklistVersion: blocklistVersion ?? this.blocklistVersion,
      phonebookId: phonebookId ?? this.phonebookId,
      sipDeviceId: sipDeviceId ?? this.sipDeviceId,
      sipUsername: sipUsername ?? this.sipUsername,
      countryCode: countryCode ?? this.countryCode,
      intlPrefix: intlPrefix ?? this.intlPrefix,
      trunkPrefix: trunkPrefix ?? this.trunkPrefix,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  /// Returns true if the config has valid credentials.
  bool get hasCredentials => host != null && username != null && password != null;

  static BlocklistMode _parseBlocklistMode(String? mode) {
    if (mode == null) return BlocklistMode.none;
    return BlocklistMode.values.firstWhere(
      (m) => m.name == mode,
      orElse: () => BlocklistMode.none,
    );
  }
}

/// Device information from Fritz!Box discovery.
class FritzBoxDeviceInfo {
  final String host;
  final String? modelName;
  final String? fritzosVersion;
  final String? serialNumber;

  FritzBoxDeviceInfo({
    required this.host,
    this.modelName,
    this.fritzosVersion,
    this.serialNumber,
  });

  /// Returns a display string for this device.
  String get displayName {
    if (modelName != null) {
      return '$modelName (${fritzosVersion ?? host})';
    }
    return host;
  }
}
