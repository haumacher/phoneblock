/// Converts a phone number to international format using Fritz!Box dialing parameters.
///
/// [phone] - The raw phone number from the Fritz!Box call list.
/// [lkz] - Country calling code without "+" (e.g., "49" for Germany).
/// [lkzPrefix] - International dialing prefix (e.g., "00" for Europe, "011" for US).
/// [okzPrefix] - Domestic trunk prefix (e.g., "0" for Germany, "" for Italy).
///
/// Returns the phone number in international format (starting with "+"), or null if invalid.
String? toInternationalForm(String phone, String lkz, String lkzPrefix, String okzPrefix) {
  String? plus;

  if (phone.startsWith(lkzPrefix) && lkzPrefix.isNotEmpty) {
    if (phone.startsWith('${lkzPrefix}0') && lkzPrefix == '00') {
      // 000... is not a phone number
      return null;
    }
    plus = '+${phone.substring(lkzPrefix.length)}';
  } else if (phone.startsWith('+')) {
    plus = phone;
  } else if (okzPrefix.isNotEmpty && phone.startsWith(okzPrefix)) {
    plus = '+$lkz${phone.substring(okzPrefix.length)}';
  } else if (okzPrefix.isEmpty) {
    plus = '+$lkz$phone';
  }

  if (plus == null || plus.length <= 8) {
    return null;
  }

  return plus;
}

/// Masks all but the last three digits of [phone] for the user-visible log.
///
/// Example: `030123456` becomes `******456`. Non-digit characters (such as a
/// leading "+") are kept.
String maskPhoneNumber(String phone) {
  if (phone.isEmpty) return '(none)';
  var keep = 3;
  final out = StringBuffer();
  for (var i = phone.length - 1; i >= 0; i--) {
    final c = phone[i];
    final isDigit = c.codeUnitAt(0) >= 0x30 && c.codeUnitAt(0) <= 0x39;
    if (isDigit && keep > 0) {
      out.write(c);
      keep--;
    } else if (isDigit) {
      out.write('*');
    } else {
      out.write(c);
    }
  }
  return out.toString().split('').reversed.join();
}
