package org.openrath.launcher;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

/**
 * Managed settings for fleets (Android's app restrictions, set from a device-management console;
 * res/xml/app_restrictions.xml lists them). A fleet can pick and lock the level, turn features on or
 * off and lock them, hide developer mode, and give the https address usage reports go to. With no
 * management nothing here does anything, and every switch stays the owner's.
 */
final class Managed {

    /** Apply whatever the device's management has set; called at start and when it changes. */
    static void apply(Context c, Prefs p) {
        Bundle b = null;
        try {
            RestrictionsManager rm = (RestrictionsManager) c.getSystemService(Context.RESTRICTIONS_SERVICE);
            if (rm != null) b = rm.getApplicationRestrictions();
        } catch (Throwable ignored) {
        }
        apply(p, b == null ? new Bundle() : b);
    }

    static void apply(Prefs p, Bundle b) {
        String level = b.getString("level", "");
        int li = java.util.Arrays.asList(Features.LEVELS).indexOf(level);
        p.put("m:lockLevel", li >= 0 && b.getBoolean("lockLevel", false) ? "on" : "off");
        if (li >= 0 && !level.equals(p.str("m:levelApplied", ""))) {
            Features.setLevel(p, li);
            p.put("m:levelApplied", level);
        } else if (li < 0) p.put("m:levelApplied", "");
        // locked features: last time's are released first, then this time's set and locked
        for (String id : ids(p.str("m:features", ""))) p.put("m:feat:" + id, "");
        StringBuilder all = new StringBuilder();
        for (String[] pair : new String[][]{{"featuresOn", "on"}, {"featuresOff", "off"}}) {
            for (String id : ids(b.getString(pair[0], ""))) {
                if (Features.get(id) == null) continue;
                id = Features.parent(id);   // an old id locks the switch it became part of
                p.put("m:feat:" + id, "");
                Features.set(p, id, "on".equals(pair[1]));
                p.put("m:feat:" + id, pair[1]);
                all.append(id).append(',');
            }
        }
        p.put("m:features", all.toString());
        boolean devAllowed = b.getBoolean("devMode", true);
        p.put("m:noDev", devAllowed ? "off" : "on");
        if (!devAllowed) p.put("devMode", "off");
        String url = b.getString("reportUrl", "");
        p.put("reportUrl", Reports.sendable(url) ? url.trim() : "");
    }

    /** "a, b,c" → {"a", "b", "c"}. */
    static String[] ids(String list) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : list == null ? new String[0] : list.split("[,\\s]+")) if (!s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }

    /** The value a fleet locked this feature to ("on" or "off"), or null when it's the owner's. */
    static String lockedTo(Prefs p, String id) {
        String v = p.str("m:feat:" + id, "");
        return v.isEmpty() ? null : v;
    }

    static boolean levelLocked(Prefs p) {
        return "on".equals(p.str("m:lockLevel", "off"));
    }

    static boolean devBlocked(Prefs p) {
        return "on".equals(p.str("m:noDev", "off"));
    }

    static final String NOTE = "Set by your organisation";

    private Managed() {
    }
}
