/// Returns `file:line` of the first stack frame not inside this package's
/// `lib/logging/` directory. Returns `null` if no such frame is found.
String? extractCallerSource([StackTrace? trace]) {
  final lines = (trace ?? StackTrace.current).toString().split('\n');
  final frame = RegExp(r'\(package:([^/]+)/(.+\.dart):(\d+)(?::\d+)?\)');
  for (final line in lines) {
    final m = frame.firstMatch(line);
    if (m == null) continue;
    final path = m.group(2)!;
    if (path.startsWith('logging/')) continue;
    final file = path.split('/').last;
    return '$file:${m.group(3)}';
  }
  return null;
}
