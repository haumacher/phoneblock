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
