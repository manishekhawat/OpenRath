package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The music and radio boxes' menu (a tap on the box's heading): where the sound comes from, the
 * music apps to switch between, the app's library to play from (its playlists, albums, podcasts or
 * stations, for apps that share them with other apps the way Android Auto reads them) and what's
 * up next in its queue. While moving, lists stop at 12 rows, like Android Auto's.
 */
final class Browse {

    /** Rows shown in a list while the car moves. */
    static final int MOVING_ROWS = 12;

    /** At most `max` of `all` while moving, all of them when stopped. Pure, for the tests. */
    static <T> List<T> trim(List<T> all, boolean moving, int max) {
        return moving && all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    /** The apps that share a library with other apps (a MediaBrowserService), as package names. */
    static List<String> browsable(PackageManager pm) {
        List<String> out = new ArrayList<>();
        try {
            for (ResolveInfo r : pm.queryIntentServices(new Intent("android.media.browse.MediaBrowserService"), 0)) {
                String p = r.serviceInfo.packageName;
                if (!out.contains(p)) out.add(p);
            }
        } catch (Exception e) {
            Diag.note("music apps", e);
        }
        return out;
    }

    private static ComponentName service(PackageManager pm, String pkg) {
        for (ResolveInfo r : pm.queryIntentServices(new Intent("android.media.browse.MediaBrowserService").setPackage(pkg), 0))
            return new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
        return null;
    }

    static String label(PackageManager pm, String pkg) {
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    /** The box's menu. */
    static void menu(final HomeActivity a, final boolean radio, final Hub.Media now, final Runnable changed) {
        final PackageManager pm = a.getPackageManager();
        final String what = radio ? "radio" : "music", key = radio ? "media:radio" : "media:music";
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        String cur = a.prefs.source(what);
        for (final String src : radio ? Prefs.RADIO_SRC : Prefs.MUSIC_SRC) {
            labels.add((src.equals(cur) ? "✓  " : "      ") + Hindi.t(sourceLabel(radio, src)));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    a.prefs.setSource(what, src);
                    changed.run();
                }
            });
        }
        if (now != null && now.controller != null) {
            final MediaController c = now.controller;
            final String name = label(pm, c.getPackageName());
            if (service(pm, c.getPackageName()) != null) {
                labels.add(Hindi.f("Browse %1$s", name));
                acts.add(new Runnable() {
                    @Override
                    public void run() {
                        open(a, c.getPackageName());
                    }
                });
            }
            List<MediaSession.QueueItem> q = c.getQueue();
            if (q != null && !q.isEmpty()) {
                labels.add(Hindi.t("Up next"));
                acts.add(new Runnable() {
                    @Override
                    public void run() {
                        queue(a, c);
                    }
                });
            }
        }
        String chosen = a.prefs.app(key);
        List<String> apps = new ArrayList<>(a.hub.sessionPkgs);
        for (String p : browsable(pm)) if (!apps.contains(p) && !p.equals(a.getPackageName())) apps.add(p);
        for (final String p : apps) {
            if (radio != Hub.looksLikeRadio(p)) continue;
            labels.add((p.equals(chosen) ? "♪  " : "      ") + label(pm, p));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    a.prefs.setApp(key, p);   // the box follows this app now
                    a.prefs.setSource(what, "app");
                    changed.run();
                    if (service(pm, p) != null) open(a, p);
                    else a.openApp(key, label(pm, p));
                }
            });
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t(radio ? "Radio" : "Music"))
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        acts.get(w).run();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static String sourceLabel(boolean radio, String src) {
        if ("auto".equals(src)) return radio ? "Auto: online radio with internet, else the radio app" : "Auto: the music app with internet, else music files";
        if ("web".equals(src)) return "Online radio";
        if ("files".equals(src)) return "Music files";
        return radio ? "Radio app" : "Music app";
    }

    /** What's up next in the app's queue; a tap jumps to it. */
    static void queue(final HomeActivity a, final MediaController c) {
        List<MediaSession.QueueItem> q = trim(c.getQueue(), ChildLock.moving(), MOVING_ROWS * 2);
        final List<MediaSession.QueueItem> items = new ArrayList<>(q);
        String[] names = new String[items.size()];
        for (int i = 0; i < names.length; i++) {
            CharSequence t = items.get(i).getDescription().getTitle(), s = items.get(i).getDescription().getSubtitle();
            names[i] = (t == null ? "" : t) + (s == null ? "" : "  ·  " + s);
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Up next"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        c.getTransportControls().skipToQueueItem(items.get(w).getQueueId());
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static MediaBrowser browser;

    /** Connect to the app's library and show its top level. */
    static void open(final HomeActivity a, final String pkg) {
        ComponentName svc = service(a.getPackageManager(), pkg);
        if (svc == null) return;
        if (browser != null) {
            try {
                browser.disconnect();
            } catch (Throwable ignored) {
                // already gone
            }
        }
        final String name = label(a.getPackageManager(), pkg);
        final MediaBrowser[] b = new MediaBrowser[1];
        b[0] = new MediaBrowser(a, svc, new MediaBrowser.ConnectionCallback() {
            @Override
            public void onConnected() {
                level(a, b[0], b[0].getRoot(), name);
            }

            @Override
            public void onConnectionFailed() {
                Toast.makeText(a, Hindi.f("%1$s doesn't share its library with other apps. Opening it instead.", name), Toast.LENGTH_LONG).show();
                Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }, null);
        browser = b[0];
        try {
            b[0].connect();
        } catch (Throwable e) {
            Diag.note("music library", e);
        }
    }

    /** One level of the library: folders open the next level, songs and stations play. */
    private static void level(final HomeActivity a, final MediaBrowser b, final String id, final String title) {
        try {
            b.subscribe(id, new MediaBrowser.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(String parent, List<MediaBrowser.MediaItem> kids) {
                    b.unsubscribe(parent);
                    final List<MediaBrowser.MediaItem> items = trim(kids, ChildLock.moving(), MOVING_ROWS);
                    if (items.isEmpty()) {
                        Toast.makeText(a, Hindi.t("Nothing here"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = new String[items.size()];
                    for (int i = 0; i < names.length; i++) {
                        CharSequence t = items.get(i).getDescription().getTitle();
                        names[i] = (items.get(i).isBrowsable() ? "›  " : "♪  ") + (t == null ? "" : t);
                    }
                    new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(title)
                            .setItems(names, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    MediaBrowser.MediaItem it = items.get(w);
                                    CharSequence t = it.getDescription().getTitle();
                                    if (it.isBrowsable()) level(a, b, it.getMediaId(), t == null ? title : t.toString());
                                    else play(a, b, it.getMediaId());
                                }
                            }).setNegativeButton(Hindi.t("Close"), null).show();
                }

                @Override
                public void onError(String parent) {
                    Toast.makeText(a, Hindi.t("The music app didn't answer"), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Throwable e) {
            Diag.note("music library level", e);
        }
    }

    private static void play(HomeActivity a, MediaBrowser b, String mediaId) {
        try {
            new MediaController(a, b.getSessionToken()).getTransportControls().playFromMediaId(mediaId, null);
            a.hub.notifyIfChanged(true);
        } catch (Throwable e) {
            Diag.note("music play", e);
            Toast.makeText(a, Hindi.t("The music app didn't answer"), Toast.LENGTH_SHORT).show();
        }
    }

    private Browse() {
    }
}
