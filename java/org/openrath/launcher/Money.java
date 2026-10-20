package org.openrath.launcher;

import java.util.Locale;

/**
 * The owner's country, for money and the India-only parts (FASTag, India's holidays, UPI). The app
 * writes prices with ₹; outside India every ₹ shown on screen becomes the local currency's sign
 * (done in Hindi.t and Hindi.f, which all text on screen goes through) and spoken prices use its name.
 * The country is the unit's own unless chosen in Settings > Units.
 */
final class Money {

    /** The countries offered in Settings, India first; any other country still works from the unit's own. */
    static final String[] COUNTRIES = {"IN", "PK", "BD", "NP", "LK", "AE", "SA", "GB", "US", "CA", "AU", "NZ", "SG", "MY", "ZA",
            "DE", "FR", "IT", "ES", "NL"};

    static String country = "IN";

    static String country(Prefs p) {
        String c = p.str("country", "");
        if (c.isEmpty()) c = Locale.getDefault().getCountry();
        return c == null || c.length() != 2 ? "IN" : c.toUpperCase(Locale.US);
    }

    static void load(Prefs p) {
        country = country(p);
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
