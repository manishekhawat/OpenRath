package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The unit's contacts (synced from the phone by most Bluetooth apps): a list to call from, a name
 * for "call Priya" by voice, and the caller's name on the call screen. Read on the unit, never kept.
 * Needs the contacts permission, asked the first time the list opens.
 */
final class Contacts {

    static boolean allowed(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private static final Map<String, String> NAMES = new HashMap<>();

    /** The contact's name for a number, or "" (remembered while OpenRath runs). */
    static String nameFor(Context c, String number) {
        if (number == null || number.isEmpty() || !allowed(c)) return "";
        String got = NAMES.get(number);
        if (got != null) return got;
        got = "";
        try (Cursor q = c.getContentResolver().query(android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)), new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (q != null && q.moveToFirst()) got = q.getString(0);
        } catch (Exception e) {
            Diag.note("contact name", e);
        }
        if (got == null) got = "";
        NAMES.put(number, got);
        return got;
    }

    /** {name, number} per phone number: favourites (starred) first, then the most called, then A to Z. */
    static List<String[]> all(Context c) {
        List<String[]> out = new ArrayList<>();
        if (!allowed(c)) return out;
        String[] cols = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor q = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null,
                ContactsContract.CommonDataKinds.Phone.STARRED + " DESC, " + ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED + " DESC, "
                        + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE")) {
            while (q != null && q.moveToNext()) {
                String name = q.getString(0), num = q.getString(1);
                if (num == null || num.isEmpty()) continue;
                boolean dup = false;
                for (String[] o : out) dup |= Calls.same(o[1], num);
                if (!dup) out.add(new String[]{name == null ? num : name, num});
            }
        } catch (Exception e) {
            Diag.note("contacts", e);
        }
        return out;
    }

    /** The number of the contact whose name best matches what was said, or "" (for "call Priya"). */
    static String find(Context c, String said) {
        String best = "";
        int score = 0;
        for (String[] x : all(c)) {
            int s = match(x[0], said);
            if (s > score) {
                score = s;
                best = x[1];
            }
        }
        return best;
    }

    /** How well a contact's name matches what was said: 4 the same, 3 nearly (Voice.sameName), 2 a whole word of it, 1 a start of a word, 0 not. */
    static int match(String name, String said) {
        if (name == null || said == null) return 0;
        String n = name.trim().toLowerCase(java.util.Locale.ROOT), s = said.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return 0;
        if (n.equals(s)) return 4;
        if (Voice.sameName(name, said)) return 3;
        for (String w : n.split("\\s+")) if (w.equals(s)) return 2;
        for (String w : n.split("\\s+")) if (w.length() > 2 && s.length() > 2 && w.startsWith(s)) return 1;
        return 0;
    }

    /** Pick a contact to call. Typing a name works only when stopped; the list scrolls either way. */
    static void show(final HomeActivity a, final Unit unit) {
        if (!allowed(a)) {
            a.requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, 6);
            return;
        }
        final List<String[]> people = all(a);
        if (people.isEmpty()) {
            android.widget.Toast.makeText(a, Hindi.t("No contacts on the unit yet. Pair your phone and allow it to share contacts."),
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final List<String[]> shown = new ArrayList<>(people);
        final List<String> rows = new ArrayList<>();
        for (String[] p : shown) rows.add(p[0]);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final ArrayAdapter<String> ad = new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, rows) {
            @Override
            public android.view.View getView(int pos, android.view.View v, android.view.ViewGroup parent) {
                android.view.View out = super.getView(pos, v, parent);
                out.setMinimumHeight(Ui.px(HomeDock.TARGET));
                ((android.widget.TextView) out).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 22 * Ui.k);
                return out;
            }
        };
        if (!ChildLock.moving()) {
            EditText search = new EditText(a);
            search.setHint(Hindi.t("Search"));
            search.setSingleLine(true);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}

                @Override
                public void afterTextChanged(Editable e) {
                    String t = e.toString().trim().toLowerCase(java.util.Locale.ROOT);
                    shown.clear();
                    rows.clear();
                    for (String[] p : people) {
                        if (t.isEmpty() || p[0].toLowerCase(java.util.Locale.ROOT).contains(t) || p[1].replace(" ", "").contains(t)) {
                            shown.add(p);
                            rows.add(p[0]);
                        }
                    }
                    ad.notifyDataSetChanged();
                }
            });
            box.addView(search);
        }
        ListView list = new ListView(a);
        list.setAdapter(ad);
        box.addView(list, new LinearLayout.LayoutParams(-1, -2));
        final AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Contacts")).setView(box).setNegativeButton(Hindi.t("Cancel"), null).create();
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, android.view.View v, int pos, long id) {
                if (pos >= shown.size()) return;
                String[] p = shown.get(pos);
                dlg.dismiss();
                String err = unit == null ? Hindi.t("the stereo isn't ready") : unit.call(a, p[1]);
                android.widget.Toast.makeText(a, err == null ? Hindi.f("Calling %1$s…", p[0]) : Hindi.f("Couldn't call: %1$s", err),
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        dlg.show();
    }

    private Contacts() {
    }
}
