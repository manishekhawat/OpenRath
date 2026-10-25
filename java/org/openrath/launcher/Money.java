package org.openrath.launcher;

import java.util.Locale;

/**
 * The owner's country, for money and the India-only parts (FASTag, India's holidays, UPI). The app
 * writes prices with ₹; outside India every ₹ shown on screen becomes the local currency's sign
 * (done in Hindi.t and Hindi.f, which all text on screen goes through) and spoken prices use its name.
 * The country is the unit's own unless chosen in Settings > Dashboard.
 */
final class Money {

    /** The countries offered in Settings, India first; any other country still works from the unit's own. */
    static final String[] COUNTRIES = {"IN", "PK", "BD", "NP", "LK", "AE", "SA", "GB", "US", "CA", "AU", "NZ", "SG", "MY", "ZA",
            "DE", "FR", "IT", "ES", "NL"};

    static String country = "IN";

    /** The SIM's or the mobile network's country ("in", "gb"), read once; "" when there is neither. */
    static String phoneCountry = "";

    /** The first two-letter country of: the owner's choice, the unit's language, the SIM, the network; else IN. Pure. */
    static String pick(String... tries) {
        String c = first(tries);
        return c.isEmpty() ? "IN" : c;
    }

    /** The first two-letter country code among these, in capitals; "" when there is none. */
    private static String first(String... tries) {
        for (String c : tries) {
            String t = c == null ? "" : c.trim();
            if (t.length() == 2 && Character.isLetter(t.charAt(0)) && Character.isLetter(t.charAt(1))) return t.toUpperCase(Locale.US);
        }
        return "";
    }

    static String country(Prefs p) {
        return pick(p.str("country", ""), Locale.getDefault().getCountry(), phoneCountry);
    }

    /** Reads the SIM's country, then the network's, for a unit whose language names no country. */
    static void phone(android.content.Context c) {
        if (!phoneCountry.isEmpty() || c == null) return;
        try {
            android.telephony.TelephonyManager t = (android.telephony.TelephonyManager) c.getSystemService(android.content.Context.TELEPHONY_SERVICE);
            if (t == null) return;
            // the network's country is unreliable on CDMA phones, so only the SIM's counts there
            String net = t.getPhoneType() == android.telephony.TelephonyManager.PHONE_TYPE_CDMA ? "" : t.getNetworkCountryIso();
            phoneCountry = first(t.getSimCountryIso(), net);
        } catch (Exception e) {
            // no phone service on this unit
        }
    }

    static void load(Prefs p) {
        country = country(p);
    }

    static void load(android.content.Context c, Prefs p) {
        phone(c);
        load(p);
    }

    /** Countries that group digits the South Asian way: 1,00,000 for a lakh. */
    private static final String LAKH = ",IN,PK,BD,NP,";
    /** Countries that write 1.000 for a thousand. */
    private static final String DOTS = ",DE,IT,ES,NL,BE,AT,PT,GR,SI,HR,ID,BR,AR,TR,DK,";

    /** A whole amount with the country's digit grouping: "1,00,000" in India, "100,000" in the UK, "100.000" in Germany. Pure. */
    static String group(long v, String cc) {
        String s = Long.toString(Math.abs(v)), sep = cc != null && DOTS.contains("," + cc + ",") ? "." : ",";
        boolean lakh = cc == null || LAKH.contains("," + cc + ",");
        StringBuilder b = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            int left = n - i;   // digits still to write, this one included
            if (i > 0 && (left == 3 || (left > 3 && (lakh ? (left - 3) % 2 == 0 : left % 3 == 0)))) b.append(sep);
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    static String group(long v) {
        return group(v, country);
    }

    static boolean india() {
        return "IN".equals(country);
    }

    private static final String EURO = ",DE,FR,IT,ES,NL,BE,AT,PT,IE,FI,GR,LU,SK,SI,EE,LV,LT,CY,MT,HR,";

    /** The sign written before an amount: "₹", "$", "£", "€", "AED ". Pure, for the tests. */
    static String symbol(String cc) {
        if (cc == null || "IN".equals(cc)) return "₹";
        if (EURO.contains("," + cc + ",")) return "€";
        switch (cc) {
            case "US": case "CA": case "AU": case "NZ": case "SG": return "$";
            case "GB": return "£";
            case "PK": case "NP": case "LK": return "Rs ";
            case "BD": return "৳";
            case "MY": return "RM ";
            case "ZA": return "R ";
            case "JP": case "CN": return "¥";
            default:
                try {
                    return java.util.Currency.getInstance(new Locale("", cc)).getCurrencyCode() + " ";
                } catch (Exception e) {
                    return "₹";
                }
        }
    }

    /** The currency's name to speak: "rupees", "dollars". Pure, for the tests. */
    static String spoken(String cc) {
        if (cc == null || "IN".equals(cc)) return "rupees";
        if (EURO.contains("," + cc + ",")) return "euros";
        switch (cc) {
            case "US": case "CA": case "AU": case "NZ": case "SG": return "dollars";
            case "GB": return "pounds";
            case "PK": case "NP": case "LK": return "rupees";
            case "BD": return "taka";
            case "AE": return "dirhams";
            case "SA": return "riyals";
            case "MY": return "ringgit";
            case "ZA": return "rand";
            default:
                try {
                    return java.util.Currency.getInstance(new Locale("", cc)).getDisplayName(Locale.ENGLISH);
                } catch (Exception e) {
                    return "rupees";
                }
        }
    }

    static String spoken() {
        return spoken(country);
    }

    /** Text with ₹ in it, in the owner's currency. Pure apart from the country, for the tests. */
    static String shown(String s, String cc) {
        if (s == null || "IN".equals(cc) || s.indexOf('₹') < 0) return s;
        return s.replace("₹", symbol(cc));
    }

    static String shown(String s) {
        return shown(s, country);
    }

    /** "Target reached: ₹2,000" read out: "Target reached: rupees 2,000" (or dollars, and so on). */
    static String say(String s) {
        return s.replace("₹", spoken() + " ").replace(symbol(country), spoken() + " ");
    }

    /** A country's name for the Settings list: "India", "United Kingdom". */
    static String name(String cc) {
        return new Locale("", cc).getDisplayCountry(Locale.ENGLISH);
    }

    private Money() {
    }
}
