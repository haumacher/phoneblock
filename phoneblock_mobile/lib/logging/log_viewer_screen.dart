import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path/path.dart' as p;
import 'package:share_plus/share_plus.dart';

import 'package:phoneblock_mobile/l10n/app_localizations.dart';
import 'package:phoneblock_mobile/logging/app_logger.dart';

/// Subscreen under Settings that shows the current diagnostic log and lets
/// the user share or clear it.
class LogViewerScreen extends StatefulWidget {
  const LogViewerScreen({super.key});

  @override
  State<LogViewerScreen> createState() => _LogViewerScreenState();
}

class _LogViewerScreenState extends State<LogViewerScreen> {
  String _content = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final dir = AppLogger.instance.logDir;
    String text = '';
    if (dir != null) {
      text = await _readPair(dir);
    }
    if (!mounted) return;
    setState(() {
      _content = text;
      _loading = false;
    });
  }

  Future<String> _readPair(String dir) async {
    final buffer = StringBuffer();
    for (final name in const ['app.log.1', 'app.log']) {
      final file = File(p.join(dir, name));
      if (!await file.exists()) continue;
      try {
        buffer.writeln(await file.readAsString());
      } on IOException {
        await Future<void>.delayed(const Duration(milliseconds: 100));
        try {
          buffer.writeln(await file.readAsString());
        } on IOException {
          if (!mounted) return buffer.toString();
          final l10n = AppLocalizations.of(context)!;
          buffer.writeln(l10n.diagnosticLogReadError);
        }
      }
    }
    return buffer.toString();
  }

  Future<void> _share() async {
    final dir = AppLogger.instance.logDir;
    if (dir == null) return;
    final headerFile = await _writeHeader(dir);
    final files = <XFile>[
      if (await File(p.join(dir, 'app.log.1')).exists())
        XFile(p.join(dir, 'app.log.1')),
      if (await File(p.join(dir, 'app.log')).exists())
        XFile(p.join(dir, 'app.log')),
      XFile(headerFile.path),
    ];
    if (!mounted) return;
    final l10n = AppLocalizations.of(context)!;
    await Share.shareXFiles(files, subject: l10n.diagnosticLogShareSubject);
  }

  Future<File> _writeHeader(String dir) async {
    final info = await PackageInfo.fromPlatform();
    final androidInfo = await DeviceInfoPlugin().androidInfo;
    final header = '''
App:      ${info.appName} ${info.version}+${info.buildNumber}
OS:       Android ${androidInfo.version.release} (SDK ${androidInfo.version.sdkInt})
Device:   ${androidInfo.manufacturer} ${androidInfo.model}
Locale:   ${WidgetsBinding.instance.platformDispatcher.locale}
Time:     ${DateTime.now().toIso8601String()}
''';
    final ts = DateTime.now().toIso8601String().replaceAll(RegExp(r'[:.]'), '-');
    final file = File(p.join(dir, 'phoneblock-log-$ts.txt'));
    await file.writeAsString(header);
    return file;
  }

  Future<void> _confirmClear() async {
    final l10n = AppLocalizations.of(context)!;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(l10n.diagnosticLogClearConfirmTitle),
        content: Text(l10n.diagnosticLogClearConfirmBody),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(c, false),
              child: Text(MaterialLocalizations.of(c).cancelButtonLabel)),
          TextButton(
              onPressed: () => Navigator.pop(c, true),
              child: Text(l10n.diagnosticLogClear)),
        ],
      ),
    );
    if (ok == true) {
      await AppLogger.instance.clear();
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.diagnosticLog),
        actions: [
          IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: l10n.diagnosticLogRefresh,
              onPressed: _load),
          IconButton(
              icon: const Icon(Icons.share),
              tooltip: l10n.diagnosticLogShare,
              onPressed: _share),
          IconButton(
              icon: const Icon(Icons.delete_outline),
              tooltip: l10n.diagnosticLogClear,
              onPressed: _confirmClear),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _content.trim().isEmpty
              ? Center(child: Text(l10n.diagnosticLogEmpty))
              : Scrollbar(
                  child: SingleChildScrollView(
                    reverse: true,
                    padding: const EdgeInsets.all(12),
                    child: SelectableText(
                      _content,
                      style: const TextStyle(
                          fontFamily: 'monospace', fontSize: 12),
                    ),
                  ),
                ),
    );
  }
}
