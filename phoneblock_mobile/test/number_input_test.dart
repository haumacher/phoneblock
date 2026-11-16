import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/number_input.dart';

void main() {
  group('parseNumberInput', () {
    test('trailing wildcard selects a blacklist rule even with the switch off',
        () {
      final result = parseNumberInput('  +49 30 555*  ',
          isBlacklist: true, wildcardSelected: false);
      expect(result, (phone: '+4930555', wildcard: true, error: null));
    });

    test('the wildcard switch accepts an optional trailing asterisk', () {
      for (final input in ['+4930555', '+4930555*']) {
        expect(
          parseNumberInput(input, isBlacklist: true, wildcardSelected: true),
          (phone: '+4930555', wildcard: true, error: null),
        );
      }
    });

    test('whitelist rejects asterisks anywhere', () {
      for (final input in ['+4930555*', '+49*30555', '*']) {
        expect(
          parseNumberInput(input, isBlacklist: false, wildcardSelected: false)
              .error,
          NumberInputError.wildcardOnWhitelist,
        );
      }
    });

    test('rejects letters, misplaced wildcards, hash and misplaced plus signs',
        () {
      for (final input in [
        '+4930abc',
        '+49*3055',
        '030#123',
        '030+123',
        '-+4930',
        '++4930',
        '030**'
      ]) {
        for (final wildcard in [false, true]) {
          expect(
            parseNumberInput(input,
                    isBlacklist: true, wildcardSelected: wildcard)
                .error,
            NumberInputError.invalidPhoneNumber,
            reason: '$input, wildcard: $wildcard',
          );
        }
      }
    });

    test('allows formatted exact numbers on both lists', () {
      for (final blacklist in [true, false]) {
        expect(
          parseNumberInput('+49 30 1234-567',
              isBlacklist: blacklist, wildcardSelected: false),
          (phone: '+49301234567', wildcard: false, error: null),
        );
        expect(
          parseNumberInput(' (030) / 123-456 ',
              isBlacklist: blacklist, wildcardSelected: false),
          (phone: '030123456', wildcard: false, error: null),
        );
      }
    });

    test('rejects empty input and input without digits', () {
      for (final input in ['', '   ', '+', '()- /', '*']) {
        expect(
          parseNumberInput(input, isBlacklist: true, wildcardSelected: false)
              .error,
          NumberInputError.invalidPhoneNumber,
        );
      }
    });
  });
}
