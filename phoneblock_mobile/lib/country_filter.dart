import 'package:flutter/material.dart';

import 'l10n/l10n_extensions.dart';
import 'logging/app_logger.dart';
import 'main.dart' show platform;

/// A country the country filter can allow (#709).
class Country {
  /// ISO region code, e.g. `AT`.
  final String region;

  /// Country name in the UI language, e.g. `Österreich`.
  final String name;

  /// International calling code, e.g. `43`.
  final int code;

  const Country(this.region, this.name, this.code);

  /// Display label, e.g. `Österreich (+43)`.
  String get label => '$name (+$code)';
}

/// Settings of the country filter, stored natively where `CallChecker` reads them.
class CountryFilterSettings {
  /// Whether calls from countries not in [countries] are blocked.
  final bool enabled;

  /// The allowed countries as ISO region codes. Pre-filled with the home country.
  final List<String> countries;

  const CountryFilterSettings({required this.enabled, required this.countries});
}

/// Loads the country filter settings.
Future<CountryFilterSettings> loadCountryFilter() async {
  final result = await platform.invokeMapMethod<String, dynamic>('getCountryFilter');
  return CountryFilterSettings(
    enabled: result?['enabled'] as bool? ?? false,
    countries: List<String>.from(result?['countries'] as List? ?? const []),
  );
}

/// Stores the country filter settings.
Future<void> saveCountryFilter(CountryFilterSettings settings) async {
  await platform.invokeMethod('setCountryFilter', {
    'enabled': settings.enabled,
    'countries': settings.countries,
  });
}

/// Loads all countries named in the given language, sorted by name.
Future<List<Country>> loadCountries(String languageTag) async {
  final result = await platform.invokeListMethod<Map>('getCountries', languageTag);
  return [
    for (final country in result ?? const <Map>[])
      Country(country['region'] as String, country['name'] as String, country['code'] as int),
  ];
}

/// Display labels of the given regions, e.g. `Deutschland (+49)`.
List<String> countryLabels(List<String> regions, List<Country> countries) {
  final byRegion = {for (final country in countries) country.region: country};
  return [for (final region in regions) byRegion[region]?.label ?? region];
}

/// Settings screen of the country filter: switch and list of allowed countries.
class CountryFilterScreen extends StatefulWidget {
  const CountryFilterScreen({super.key});

  @override
  State<CountryFilterScreen> createState() => _CountryFilterScreenState();
}

class _CountryFilterScreenState extends State<CountryFilterScreen> {
  CountryFilterSettings? _settings;
  List<Country> _countries = const [];

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_settings == null) {
      _load(Localizations.localeOf(context).toLanguageTag());
    }
  }

  Future<void> _load(String languageTag) async {
    try {
      final settings = await loadCountryFilter();
      final countries = await loadCountries(languageTag);
      if (!mounted) return;
      setState(() {
        _settings = settings;
        _countries = countries;
      });
    } catch (e, s) {
      AppLogger.instance.error('settings', 'Error loading country filter', e, s);
    }
  }

  Future<void> _save(BuildContext context, CountryFilterSettings settings) async {
    try {
      await saveCountryFilter(settings);
      if (!context.mounted) return;
      setState(() => _settings = settings);
    } catch (e, s) {
      AppLogger.instance.error('settings', 'Error saving country filter', e, s);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.errorSaving), backgroundColor: Colors.red),
      );
    }
  }

  Future<void> _remove(BuildContext context, String region) async {
    final settings = _settings!;
    if (settings.countries.length <= 1) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.countryFilterKeepOne)),
      );
      return;
    }
    await _save(context, CountryFilterSettings(
      enabled: settings.enabled,
      countries: [for (final c in settings.countries) if (c != region) c],
    ));
  }

  Future<void> _add(BuildContext context) async {
    final settings = _settings!;
    final region = await Navigator.push<String>(
      context,
      MaterialPageRoute(
        builder: (context) => CountryPickerScreen(
          countries: [for (final c in _countries) if (!settings.countries.contains(c.region)) c],
          allCountries: _countries,
        ),
      ),
    );
    if (region == null || !context.mounted) return;
    await _save(context, CountryFilterSettings(
      enabled: settings.enabled,
      countries: [...settings.countries, region],
    ));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final settings = _settings;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.countryFilterTitle)),
      body: settings == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                SwitchListTile(
                  title: Text(l10n.countryFilterTitle),
                  subtitle: Text(l10n.countryFilterDescription),
                  value: settings.enabled,
                  onChanged: (value) => _save(context,
                      CountryFilterSettings(enabled: value, countries: settings.countries)),
                ),
                if (settings.enabled) ...[
                  const Divider(),
                  Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text(
                      l10n.allowedCountries,
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.grey),
                    ),
                  ),
                  for (final region in settings.countries)
                    ListTile(
                      title: Text(countryLabels([region], _countries).single),
                      trailing: IconButton(
                        icon: const Icon(Icons.close),
                        tooltip: l10n.remove,
                        onPressed: () => _remove(context, region),
                      ),
                    ),
                  ListTile(
                    leading: const Icon(Icons.add),
                    title: Text(l10n.addCountry),
                    onTap: () => _add(context),
                  ),
                ],
              ],
            ),
    );
  }
}

/// Searchable list of countries; pops with the ISO region code of the selected country.
class CountryPickerScreen extends StatefulWidget {
  /// The countries to choose from.
  final List<Country> countries;

  /// All countries, to name the others sharing a calling code (e.g. +1).
  final List<Country> allCountries;

  const CountryPickerScreen({super.key, required this.countries, required this.allCountries});

  @override
  State<CountryPickerScreen> createState() => _CountryPickerScreenState();
}

class _CountryPickerScreenState extends State<CountryPickerScreen> {
  String _query = '';

  bool _matches(Country country) {
    final query = _query.trim().toLowerCase();
    if (query.isEmpty) return true;
    final digits = query.startsWith('+') ? query.substring(1) : query;
    return country.name.toLowerCase().contains(query) || '${country.code}'.startsWith(digits);
  }

  /// The other countries with the same calling code, e.g. `USA, Kanada, Jamaika, …`.
  String? _sharedWith(Country country) {
    final others = [
      for (final c in widget.allCountries)
        if (c.code == country.code && c.region != country.region) c.name,
    ];
    if (others.isEmpty) return null;
    return others.length > 3 ? '${others.take(3).join(', ')}, …' : others.join(', ');
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final matches = widget.countries.where(_matches).toList();
    return Scaffold(
      appBar: AppBar(title: Text(l10n.addCountry)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: TextField(
              autofocus: true,
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.search),
                hintText: l10n.searchCountry,
              ),
              onChanged: (value) => setState(() => _query = value),
            ),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: matches.length,
              itemBuilder: (context, index) {
                final country = matches[index];
                final shared = _sharedWith(country);
                return ListTile(
                  title: Text(country.label),
                  subtitle: shared == null ? null : Text(l10n.countrySharedCallingCode(shared)),
                  onTap: () => Navigator.pop(context, country.region),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
