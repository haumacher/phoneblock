/// The reason a personalized-list number cannot be submitted.
enum NumberInputError {
  /// The input is empty or contains unsupported characters.
  invalidPhoneNumber,

  /// Wildcard rules are only supported on the blacklist.
  wildcardOnWhitelist,
}

/// Parses and validates a number entered for a personalized list.
///
/// The returned phone is stripped of formatting separators; wildcard prefixes
/// still go through the existing prefix normalization before they are stored.
({String phone, bool wildcard, NumberInputError? error}) parseNumberInput(
  String input, {
  required bool isBlacklist,
  required bool wildcardSelected,
}) {
  var phone = input.trim();
  if (!isBlacklist && phone.contains('*')) {
    return (
      phone: phone,
      wildcard: false,
      error: NumberInputError.wildcardOnWhitelist
    );
  }

  final trailingWildcard = isBlacklist && phone.endsWith('*');
  if (trailingWildcard) {
    phone = phone.substring(0, phone.length - 1);
  }
  final wildcard = isBlacklist && (wildcardSelected || trailingWildcard);
  if (!RegExp(r'^\+?[0-9\s()/\-]+$').hasMatch(phone)) {
    return (
      phone: phone,
      wildcard: wildcard,
      error: NumberInputError.invalidPhoneNumber
    );
  }
  phone = phone.replaceAll(RegExp(r'[\s\-()/]'), '');
  if (!RegExp(r'^\+?[0-9]+$').hasMatch(phone)) {
    return (
      phone: phone,
      wildcard: wildcard,
      error: NumberInputError.invalidPhoneNumber
    );
  }
  return (phone: phone, wildcard: wildcard, error: null);
}
