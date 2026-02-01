import 'package:phoneblock_mobile/state.dart';

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
  final int? lastFetchTimestamp;
  final String? blocklistVersion;
  final String? phonebookId;
  final String? sipDeviceId;
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
    this.lastFetchTimestamp,
    this.blocklistVersion,
    this.phonebookId,
    this.sipDeviceId,
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
      lastFetchTimestamp: map['last_fetch_timestamp'] as int?,
      blocklistVersion: map['blocklist_version'] as String?,
      phonebookId: map['phonebook_id'] as String?,
      sipDeviceId: map['sip_device_id'] as String?,
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
      'last_fetch_timestamp': lastFetchTimestamp,
      'blocklist_version': blocklistVersion,
      'phonebook_id': phonebookId,
      'sip_device_id': sipDeviceId,
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
    int? lastFetchTimestamp,
    String? blocklistVersion,
    String? phonebookId,
    String? sipDeviceId,
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
      lastFetchTimestamp: lastFetchTimestamp ?? this.lastFetchTimestamp,
      blocklistVersion: blocklistVersion ?? this.blocklistVersion,
      phonebookId: phonebookId ?? this.phonebookId,
      sipDeviceId: sipDeviceId ?? this.sipDeviceId,
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

/// A call from Fritz!Box call history.
class FritzBoxCall {
  final int? id;
  final String fritzboxId;
  final String phoneNumber;
  final String? name;
  final DateTime timestamp;
  final int duration;
  final FritzBoxCallType callType;
  final String? device;
  final int votes;
  final int votesWildcard;
  final Rating? rating;
  final String? label;
  final String? location;
  final int syncedAt;

  FritzBoxCall({
    this.id,
    required this.fritzboxId,
    required this.phoneNumber,
    this.name,
    required this.timestamp,
    this.duration = 0,
    required this.callType,
    this.device,
    this.votes = 0,
    this.votesWildcard = 0,
    this.rating,
    this.label,
    this.location,
    required this.syncedAt,
  });

  /// Creates a FritzBoxCall from a database map.
  factory FritzBoxCall.fromMap(Map<String, dynamic> map) {
    Rating? rating;
    final ratingStr = map['rating'] as String?;
    if (ratingStr != null) {
      rating = _parseRating(ratingStr);
    }

    return FritzBoxCall(
      id: map['id'] as int?,
      fritzboxId: map['fritzbox_id'] as String,
      phoneNumber: map['phone_number'] as String,
      name: map['name'] as String?,
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] as int),
      duration: map['duration'] as int? ?? 0,
      callType: FritzBoxCallType.fromCode(map['call_type'] as int),
      device: map['device'] as String?,
      votes: map['votes'] as int? ?? 0,
      votesWildcard: map['votes_wildcard'] as int? ?? 0,
      rating: rating,
      label: map['label'] as String?,
      location: map['location'] as String?,
      syncedAt: map['synced_at'] as int,
    );
  }

  /// Converts to a database map.
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'fritzbox_id': fritzboxId,
      'phone_number': phoneNumber,
      'name': name,
      'timestamp': timestamp.millisecondsSinceEpoch,
      'duration': duration,
      'call_type': callType.code,
      'device': device,
      'votes': votes,
      'votes_wildcard': votesWildcard,
      'rating': rating != null ? _ratingToString(rating!) : null,
      'label': label,
      'location': location,
      'synced_at': syncedAt,
    };
  }

  /// Returns true if this call should be considered spam.
  bool get isSpam => votes > 0 || votesWildcard > 0;

  /// Returns true if this is an incoming call.
  bool get isIncoming =>
      callType == FritzBoxCallType.incoming ||
      callType == FritzBoxCallType.missed ||
      callType == FritzBoxCallType.activeIncoming ||
      callType == FritzBoxCallType.rejected ||
      callType == FritzBoxCallType.tam;

  static String _ratingToString(Rating rating) {
    switch (rating) {
      case Rating.aLEGITIMATE: return 'A_LEGITIMATE';
      case Rating.uNKNOWN: return 'UNKNOWN';
      case Rating.pING: return 'PING';
      case Rating.pOLL: return 'POLL';
      case Rating.aDVERTISING: return 'ADVERTISING';
      case Rating.gAMBLE: return 'GAMBLE';
      case Rating.fRAUD: return 'FRAUD';
    }
  }

  static Rating? _parseRating(String ratingStr) {
    switch (ratingStr) {
      case 'A_LEGITIMATE': return Rating.aLEGITIMATE;
      case 'UNKNOWN': return Rating.uNKNOWN;
      case 'PING': return Rating.pING;
      case 'POLL': return Rating.pOLL;
      case 'ADVERTISING': return Rating.aDVERTISING;
      case 'GAMBLE': return Rating.gAMBLE;
      case 'FRAUD': return Rating.fRAUD;
      default: return null;
    }
  }
}

/// A unified call entry combining mobile and Fritz!Box calls.
class UnifiedCall {
  final CallSource source;
  final String phoneNumber;
  final DateTime timestamp;
  final bool wasBlocked;
  final int votes;
  final int votesWildcard;
  final Rating? rating;
  final String? label;
  final String? location;

  /// Mobile-specific: the original call ID for database operations.
  final int? mobileCallId;

  /// Fritz!Box-specific fields.
  final int? duration;
  final String? device;
  final FritzBoxCallType? fritzboxCallType;
  final int? fritzboxCallId;

  UnifiedCall({
    required this.source,
    required this.phoneNumber,
    required this.timestamp,
    required this.wasBlocked,
    this.votes = 0,
    this.votesWildcard = 0,
    this.rating,
    this.label,
    this.location,
    this.mobileCallId,
    this.duration,
    this.device,
    this.fritzboxCallType,
    this.fritzboxCallId,
  });

  /// Returns the display name for this call.
  String get displayName => label ?? phoneNumber;

  /// Returns true if this call has spam indicators.
  bool get hasSpamIndicators => votes > 0 || votesWildcard > 0;

  /// Returns the effective rating for display.
  Rating get effectiveRating {
    if (rating != null && rating != Rating.uNKNOWN) {
      return rating!;
    }
    if (wasBlocked || hasSpamIndicators) {
      return Rating.uNKNOWN;
    }
    return Rating.aLEGITIMATE;
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
