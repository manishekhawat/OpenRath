package org.openrath.launcher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Voice commands in English, Hindi and Hinglish: "FM 91.1", "call Mom", "mummy ko call karo",
 * "take me home", "घर ले चलो", "गाना चलाओ", "volume badhao". Anything that isn't a command is
 * treated as a place to search for on the map. parse() has no Android types, so it is unit-tested.
 */
final class Voice {

    static final class Command {
        final String kind, arg;

        Command(String kind, String arg) {
            this.kind = kind;
            this.arg = arg;
        }

        @Override
        public String toString() {
            return kind + (arg.isEmpty() ? "" : ":" + arg);
        }
    }

    private static Command c(String kind, String arg) {
        return new Command(kind, arg == null ? "" : arg.trim());
    }

    private static boolean has(String s, String regex) {
        return Pattern.compile(regex).matcher(s).find();
    }

    /** Devanagari digits to 0–9, lower case, single spaces, "91 point 1" → "91.1". */
    static String normalise(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '०' && ch <= '९' ? (char) ('0' + (ch - '०')) : ch);
        String t = b.toString().toLowerCase(Locale.US).replaceAll("[?!,।]", " ").replaceAll("\\s+", " ").trim();
        return t.replaceAll("(\\d+) ?(point|पॉइंट|पॉइन्ट|dot) ?(\\d)", "$1.$3");
    }

    private static final String RADIO = "(fm|f m|एफएम|एफ एम|एफ़एम|radio|रेडियो)";
    private static final String GO = "(take me|navigate|^go |drive to|directions to|chalo|le chalo|ले चलो|चलो|jana hai|जाना है|जाना)";

    static Command parse(String heard) {
        if (heard == null) return null;
        String s = normalise(heard);
        if (s.isEmpty()) return null;

        // FM at a frequency: "fm 91.1", "radio 98.3 lagao", "91.1 एफएम"
        Matcher m = Pattern.compile(RADIO + "\\D{0,14}?(\\d{2,3}(\\.\\d)?)").matcher(s);
        if (m.find()) return c("fm", freq(m.group(2)));
        m = Pattern.compile("(\\d{2,3}\\.\\d) ?" + RADIO).matcher(s);
        if (m.find()) return c("fm", freq(m.group(1)));

        // answering the last message: "reply", "reply to the message", "जवाब दो"
        if (has(s, "^(reply|jawab do|जवाब दो|जवाब भेजो)( to (the |that )?message)?$")) return c("reply", "");

        // calls: "call mom", "phone 98450 12345", "mummy ko call karo", "पापा को फोन लगाओ"
        m = Pattern.compile("^(call|phone|dial|ring)( to)? (.+)$").matcher(s);
        if (m.find()) return c("call", strip(m.group(3)));
        m = Pattern.compile("^(.+?) (ko|को) (call|phone|फोन|फ़ोन|कॉल|kol)").matcher(s);
        if (m.find()) return c("call", strip(m.group(1)));

        // parking, dashcam, waterlogging, service centre, sharing the drive
        if (has(s, "(where (did i|i) park|where is (my|the) car|gaadi kaha|गाड़ी कहा|गाड़ी कहाँ|parking spot)")) return c("park", "");
        if (has(s, "(save (the )?(clip|video)|dashcam|डैशकैम)")) return c("clip", "");
        if (has(s, "(waterlog|water logging|water logged|paani bhara|पानी भरा|flooded)")) return c("flood", "");
        if (has(s, "(service cent|service station|workshop|सर्विस सेंटर|सर्विस सेन्टर)")) return c("service", "");
        if (has(s, "(share (my |the )?(trip|drive|location|ride)|location bhejo|लोकेशन भेजो)")) return c("share", "");
        if (has(s, "(parking (timer|reminder|ends)|parking (for|ke liye)|पार्किंग टाइमर)")) return c("parkTimer", "");
        if (has(s, "(split|hisaab|hisab|हिसाब|बाँट|बांट|share (the )?(cost|petrol|fuel|toll))")) return c("split", "");
        if (has(s, "(hotspot|wi-?fi|wifi|वाईफाई|वाई-फाई)")) return c("hotspot", "");
        if (has(s, "(weekly report|week report|this week|हफ्ते)")) return c("weekly", "");
        if (has(s, "(parking photo|parking picture|photo of (the )?parking)")) return c("parkPhoto", "");
        if (has(s, "(air quality|\\baqi\\b|pollution(?! (certificate|papers|check))|प्रदूषण|hawa kaisi)")) return c("aqi", "");
        // documents, fog mode, children's games
        if (has(s, "(documents|papers|show (the |my )?(rc|insurance|puc|licence|license)|kagaz|kaagaz|कागज|कागज़|डॉक्यूमेंट)")) return c("docs", "");
        if (has(s, "(fog|kohra|कोहरा|धुंध|dhundh)")) return c("fog", "");
        if (has(s, "(kids|games|game|bachche|bachchon|बच्चों|खेल|khel)")) return c("kids", "");
        // cab drivers: the fare meter, the day's earnings, the Cab menu
        if (has(s, "(\\bmeter\\b|मीटर)")) return c("meter", has(s, "(stop|band|बंद|khatam|ख़त्म|खत्म|end|off)") ? "stop" : "start");
        if (has(s, "(earnings|kamai|kamaai|कमाई|kitna kamaya|कितना कमाया|how much (did i|have i) (earn|made))")) return c("earnings", "");
        if (has(s, "(cab menu|taxi menu|कैब)")) return c("cab", "");
        if (has(s, "(\\b360\\b|three sixty|bird'?s eye|तीन सौ साठ)")) return c("view360", "");
        // the news: "read the news", "headlines", "aaj ki khabar", "समाचार सुनाओ"
        if (has(s, "(\\bnews\\b|headlines|khabar|khabre|khabren|samachar|समाचार|खबर|ख़बर|न्यूज़|न्यूज)")) return c("news", "");

        // questions about the car: fuel, engine, faults, battery, today's distance
        String ask = CarAnswer.topic(s);
        if (ask != null) return c("ask", ask);

        // volume
        if (has(s, "(volume|awaaz|awaz|आवाज़|आवाज)") || has(s, "^(louder|quieter|softer)")) {
            if (has(s, "(down|kam|कम|dheere|धीरे|धीमा|quieter|softer|low)")) return c("volDown", "");
            if (has(s, "(mute|band|बंद)")) return c("mute", "");
            return c("volUp", "");
        }

        // the radio and music screen: "open music", "show the radio", "gaane kholo", "रेडियो खोलो"
        if (has(s, "^(open|show)( the| my)? (music|songs|radio|fm|गाने|रेडियो)( app| screen| player)?$")
                || has(s, "^(music|songs|gaane|gane|radio|fm|गाने|म्यूजिक|म्यूज़िक|रेडियो)( app)? (kholo|खोलो|dikhao|दिखाओ)$"))
            return c("musicApp", has(s, RADIO) ? "radio" : "music");

        // music and radio on, next, previous, stop
        if (has(s, "^(next|agla|अगला|aage|आगे)( (song|gaana|गाना|station|track|channel))?$") || has(s, "^(next song|next station|अगला गाना)"))
            return c("next", "");
        if (has(s, "^(previous|pichla|पिछला|back|peeche)( (song|gaana|गाना|station|track|channel))?$")) return c("prev", "");
        if (has(s, "^(pause|stop|ruko|रुको|रोको|band karo|बंद करो|बंद)")) return c("pause", "");
        if (has(s, RADIO) && !has(s, GO)) return c("fm", "");
        if (has(s, "(music|gaana|gana|गाना|गाने|song|songs|संगीत|म्यूजिक|म्यूज़िक|bajao|बजाओ)")) return c("music", "");

        // places: home, office, petrol pump, a break, or anywhere by name
        boolean go = has(s, GO) || has(s, "^(navigate|directions)");
        if (has(s, "(^| )(home|ghar|घर)( |$)") && (go || s.split(" ").length <= 2)) return c("home", "");
        if (has(s, "(office|ऑफिस|ऑफ़िस|daftar|दफ्तर|दफ़्तर)") && (go || s.split(" ").length <= 2)) return c("office", "");
        if (has(s, "(petrol|पेट्रोल|fuel|diesel|cng)")) return c("fuel", "");
        if (has(s, "(dhaba|ढाबा|rest stop|break|chai|चाय|tea|coffee)")) return c("rest", "");
        m = Pattern.compile("^(take me to|navigate to|go to|drive to|directions to|navigate) (.+)$").matcher(s);
        if (m.find()) return c("search", m.group(2));
        m = Pattern.compile("^(.+?) (le chalo|ले चलो|chalo|चलो|jana hai|जाना है|जाना)$").matcher(s);
        if (m.find()) return c("search", m.group(1));
        return c("search", s);
    }

    private static String freq(String f) {
        float v = Float.parseFloat(f);
        if (v >= 875 && v <= 1080) v /= 10;   // "911" heard for 91.1
        return String.format(Locale.US, "%.1f", v);
    }

    /** "mom please", "to mom" → "mom". */
    private static String strip(String who) {
        return who.replaceAll("^(to|my) ", "").replaceAll(" (please|plz|now|karo|करो|lagao|लगाओ|kar do|कर दो)$", "").trim();
    }

    // names for the same person in English, Hinglish and Hindi
    private static final String[][] SAME = {
            {"mom", "mum", "mummy", "mumma", "mama", "maa", "ma", "mother", "मम्मी", "माँ", "मां", "माता"},
            {"dad", "daddy", "papa", "father", "pitaji", "पापा", "पिताजी"},
            {"wife", "biwi", "बीवी", "पत्नी"}, {"husband", "pati", "पति"},
            {"brother", "bhai", "bhaiya", "भाई", "भैया"}, {"sister", "didi", "behen", "दीदी", "बहन"},
            {"home", "ghar", "घर"}, {"office", "ऑफिस", "दफ्तर"}};

    /** Whether a favourite's name matches what was heard, allowing for the words above. */
    static boolean sameName(String favourite, String heard) {
        String f = normalise(favourite), h = normalise(heard);
        if (f.isEmpty() || h.isEmpty()) return false;
        if (f.equals(h) || f.contains(h) || h.contains(f)) return true;
        for (String[] group : SAME) {
            boolean a = false, b = false;
            for (String w : group) {
                if ((" " + f + " ").contains(" " + w + " ")) a = true;
                if ((" " + h + " ").contains(" " + w + " ")) b = true;
            }
            if (a && b) return true;
        }
        return false;
    }

    /** A phone number said aloud: digits only, at least 3, or "" when it isn't one. */
    static String number(String heard) {
        String d = normalise(heard).replaceAll("[^0-9+]", "");
        return d.replace("+", "").length() >= 3 && normalise(heard).replaceAll("[0-9+ -]", "").isEmpty() ? d : "";
    }

    private Voice() {
    }
}
