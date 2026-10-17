package org.openrath.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;

/**
 * The last call made or received, for the phone box: who it was and the number to call back.
 * Seen from Topway's Bluetooth service while a call rings or is dialled, and from Android's call
 * log when the Bluetooth app writes to it. Kept on the unit only.
 */
final class Calls {

    /** {number, name, dir ("in", "out" or ""), at} */
    static String[] last(Context c, Prefs p) {
        String[] mine = split(p.str("lastCall", ""));
        String[] log = fromLog(c);
        String[] best = mine == null ? log : log == null ? mine : Long.parseLong(log[3]) > Long.parseLong(mine[3]) + 60000 ? log : mine;
        if (best != null && best[1].isEmpty()) best[1] = nameFor(p, best[0]);
        return best;
    }

    /** Remember a call; a later event for the same number only fills in what's missing. */
    static void note(Prefs p, String number, String name, String dir, long at) {
        if (number == null || number.isEmpty()) return;
        String[] old = split(p.str("lastCall", ""));
        if (old != null && same(old[0], number) && at - Long.parseLong(old[3]) < 10 * 60000) {
            if (name.isEmpty()) name = old[1];
            if (dir.isEmpty()) dir = old[2];
            at = Long.parseLong(old[3]);
            if (name.equals(old[1]) && dir.equals(old[2])) return;
        }
        String line = clean(number) + "|" + clean(name) + "|" + dir + "|" + at;
        p.put("lastCall", line);
        p.put("callHistory", remember(p.str("callHistory", ""), line, 20));
    }

    /**
     * The call history kept by OpenRath (the Bluetooth app keeps its own list where apps can't read
     * it): newest first, one "number|name|dir|at" a line, a repeat of the newest call only updated.
     */
    static String remember(String history, String line, int max) {
        String[] f = split(line);
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String s : history.split("\n")) if (split(s) != null) l.add(s);
        if (!l.isEmpty() && f != null) {
            String[] top = split(l.get(0));
            if (same(top[0], f[0]) && Math.abs(Long.parseLong(f[3]) - Long.parseLong(top[3])) < 10 * 60000) l.remove(0);
        }
        l.add(0, line);
        while (l.size() > max) l.remove(l.size() - 1);
        return join(l);
    }

    private static String join(java.util.List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) b.append(b.length() == 0 ? "" : "\n").append(s);
        return b.toString();
    }

    /** The last calls, newest first, from OpenRath's own history and Android's call log together. */
    static java.util.List<String[]> recent(Context c, Prefs p, int n) {
        java.util.List<String[]> all = new java.util.ArrayList<>();
        for (String s : p.str("callHistory", "").split("\n")) {
            String[] f = split(s);
            if (f != null) all.add(f);
        }
        all.addAll(fromLog(c, n));
        return merge(all, n, p);
    }

    /** Newest first, the same call from both lists only once (same number within two minutes). */
    static java.util.List<String[]> merge(java.util.List<String[]> all, int n, Prefs p) {
        java.util.Collections.sort(all, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Long.compare(Long.parseLong(b[3]), Long.parseLong(a[3]));
            }
        });
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String[] f : all) {
            boolean dup = false;
            for (String[] o : out) dup |= same(o[0], f[0]) && Math.abs(Long.parseLong(o[3]) - Long.parseLong(f[3])) < 120000;
            if (dup) continue;
            if (f[1].isEmpty() && p != null) f[1] = nameFor(p, f[0]);
            out.add(f);
            if (out.size() >= n) break;
        }
        return out;
    }

    static String[] split(String v) {
        String[] f = v.split("\\|", -1);
        if (f.length < 4 || f[0].isEmpty()) return null;
        try {
            Long.parseLong(f[3]);
        } catch (Exception e) {
            return null;
        }
        return f;
    }

    /** The same phone number, ignoring spaces, dashes and a country code. */
    static boolean same(String a, String b) {
        String x = a.replaceAll("[^0-9]", ""), y = b.replaceAll("[^0-9]", "");
        if (x.length() >= 10 && y.length() >= 10) return x.endsWith(y.substring(y.length() - 10)) && y.endsWith(x.substring(x.length() - 10));
        return x.equals(y);
    }

    private static String clean(String s) {
        return s.replace('|', ' ').trim();
    }

    /** A favourite's name for this number, or "". */
    static String nameFor(Prefs p, String number) {
        for (int i = 1; i <= 3; i++) {
            String v = p.str("fav" + i, "");
            int bar = v.indexOf('|');
            if (bar > 0 && same(v.substring(bar + 1), number)) return v.substring(0, bar);
        }
        return "";
    }

    /** The newest entry in Android's call log, when allowed and when there is one. */
    private static String[] fromLog(Context c) {
        java.util.List<String[]> l = fromLog(c, 1);
        return l.isEmpty() ? null : l.get(0);
    }

    /** The newest n entries of Android's call log, when allowed. */
    private static java.util.List<String[]> fromLog(Context c, int n) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23 && c.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return out;
            Cursor q = c.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE},
                    null, null, CallLog.Calls.DATE + " DESC");
            if (q == null) return out;
            try {
                while (q.moveToNext() && out.size() < n) {
                    if (q.getString(0) == null || q.getString(0).isEmpty()) continue;
                    int type = q.getInt(2);
                    String dir = type == CallLog.Calls.OUTGOING_TYPE ? "out" : type == CallLog.Calls.MISSED_TYPE ? "missed" : "in";
                    out.add(new String[]{q.getString(0), q.getString(1) == null ? "" : q.getString(1), dir, String.valueOf(q.getLong(3))});
                }
            } finally {
                q.close();
            }
        } catch (Throwable e) {
            return out;
        }
        return out;
    }

    private Calls() {
    }
}
