package de.haumacher.phoneblock_mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings of the country filter (#709): blocks calls from countries the user does not allow.
 *
 * <p>The allowed countries are stored as ISO region codes (e.g. {@code DE,AT}). Allowing a
 * country allows its calling code, so allowing the USA also allows all other countries of the
 * North American Numbering Plan (+1).</p>
 */
final class CountryFilter {

    static final String KEY_ENABLED = "country_filter_enabled";
    static final String KEY_COUNTRIES = "allowed_countries";

    private CountryFilter() {
        // Static utility.
    }

    /**
     * The calling codes the user allows, {@code null} if the country filter is off or no allowed
     * country is known (the filter never blocks everything).
     */
    static Set<Integer> allowedCallingCodes(Context context) {
        SharedPreferences prefs = MainActivity.getPreferences(context);
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            return null;
        }
        Set<Integer> codes = callingCodes(allowedCountries(prefs, homeCountry(context)));
        return codes.isEmpty() ? null : codes;
    }

    /** The allowed countries, the home country if none are stored yet. */
    static List<String> allowedCountries(SharedPreferences prefs, String homeCountry) {
        List<String> result = new ArrayList<>();
        String stored = prefs.getString(KEY_COUNTRIES, null);
        if (stored != null) {
            for (String region : stored.split(",")) {
                if (!region.isEmpty()) {
                    result.add(region);
                }
            }
        }
        if (result.isEmpty() && homeCountry != null) {
            result.add(homeCountry);
        }
        return result;
    }

    /** Stores the country filter settings. */
    static void store(SharedPreferences prefs, boolean enabled, Collection<String> countries) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_COUNTRIES, String.join(",", countries))
            .apply();
    }

    /** The calling codes of the given ISO regions; unknown regions are skipped. */
    static Set<Integer> callingCodes(Collection<String> regions) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        Set<Integer> codes = new HashSet<>();
        for (String region : regions) {
            int code = util.getCountryCodeForRegion(region);
            if (code > 0) {
                codes.add(code);
            }
        }
        return codes;
    }

    /**
     * The user's home country as ISO region code: the SIM country, else the network country, else
     * the country of the device locale. {@code null} if none of them is a known region.
     */
    static String homeCountry(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        List<String> candidates = new ArrayList<>();
        if (tm != null) {
            candidates.add(tm.getSimCountryIso());
            candidates.add(tm.getNetworkCountryIso());
        }
        candidates.add(Locale.getDefault().getCountry());
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            String region = candidate.toUpperCase(Locale.ROOT);
            if (util.getCountryCodeForRegion(region) > 0) {
                return region;
            }
        }
        return null;
    }

    /**
     * All countries with a calling code, named in the given language and sorted by name.
     *
     * @param languageTag The UI language, e.g. {@code de}.
     * @return Maps with {@code region} (ISO code), {@code name} and {@code code} (calling code).
     */
    static List<Map<String, Object>> countries(String languageTag) {
        Locale language = languageTag != null ? Locale.forLanguageTag(languageTag) : Locale.getDefault();
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String region : util.getSupportedRegions()) {
            int code = util.getCountryCodeForRegion(region);
            if (code <= 0) {
                continue;
            }
            Map<String, Object> country = new HashMap<>();
            country.put("region", region);
            country.put("name", new Locale.Builder().setRegion(region).build().getDisplayCountry(language));
            country.put("code", code);
            result.add(country);
        }
        Collator collator = Collator.getInstance(language);
        result.sort((a, b) -> collator.compare((String) a.get("name"), (String) b.get("name")));
        return result;
    }
}
