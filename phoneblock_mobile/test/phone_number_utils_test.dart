import 'package:flutter_test/flutter_test.dart';
import 'package:phoneblock_mobile/fritzbox/phone_number_utils.dart';

void main() {
  group('toInternationalForm', () {
    // German Fritz!Box (lkz="49", lkzPrefix="00", okzPrefix="0")
    test('German local number with trunk prefix', () {
      expect(toInternationalForm('022376922894', '49', '00', '0'), '+4922376922894');
    });

    test('German international number with 00 prefix', () {
      expect(toInternationalForm('00441234567890', '49', '00', '0'), '+441234567890');
    });

    test('already international with + prefix', () {
      expect(toInternationalForm('+441234567890', '49', '00', '0'), '+441234567890');
    });

    test('rejects 000 prefix as invalid', () {
      expect(toInternationalForm('000123456', '49', '00', '0'), isNull);
    });

    // US Fritz!Box (lkz="1", lkzPrefix="011", okzPrefix="1")
    test('US local number with trunk prefix', () {
      expect(toInternationalForm('12125551234', '1', '011', '1'), '+12125551234');
    });

    test('US international number with 011 prefix', () {
      expect(toInternationalForm('01144207946000', '1', '011', '1'), '+44207946000');
    });

    // Italian Fritz!Box (lkz="39", lkzPrefix="00", okzPrefix="")
    test('Italian number with empty trunk prefix', () {
      expect(toInternationalForm('0612345678', '39', '00', ''), '+390612345678');
    });

    // Hungarian Fritz!Box (lkz="36", lkzPrefix="00", okzPrefix="06")
    test('Hungarian local number with 06 trunk prefix', () {
      expect(toInternationalForm('0611234567', '36', '00', '06'), '+3611234567');
    });

    test('rejects number without valid trunk prefix', () {
      expect(toInternationalForm('5551234', '49', '00', '0'), isNull);
    });

    test('rejects too-short number', () {
      expect(toInternationalForm('012345', '49', '00', '0'), isNull);
    });
  });
}
