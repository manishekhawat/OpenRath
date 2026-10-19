package org.openrath.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.location.Location;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

/**
 * The map box: the map page in a WebView that follows the car, with search (typed or spoken),
 * saved places, parking and service centres, and the bridge through which the page asks the
 * app to navigate, speak, call or save things. Tiles are kept on the unit, so places already
 * seen show without internet.
 */
final class MapCard extends Cards.Card {

    /** When the map's renderer last died, newest first (boot clock). */
    private static final long[] deaths = new long[2];

    static void died(long at) {
        deaths[1] = deaths[0];
        deaths[0] = at;
    }

    /** True when the map died twice in five minutes: show a note instead, until five minutes pass. */
    static boolean resting(long now, long last, long before) {
        return before > 0 && last - before < 5 * 60000 && now - last < 5 * 60000;
    }

    static boolean resting() {
        return resting(android.os.SystemClock.elapsedRealtime(), deaths[0], deaths[1]);
    }

    static final String MAP_BASE = "https://openrath.local/";
    final WebView web;
    double lastLat, lastLon;
    long lastPark = -1;
    boolean ready;

    @SuppressLint("SetJavaScriptEnabled")
    MapCard(Context c) {
        super(c);
        web = new WebView(c);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        web.setBackgroundColor(0xFF0E0F12);
        ws.setDomStorageEnabled(true);
        final android.content.res.AssetManager assets = c.getAssets();
        web.setWebViewClient(new WebViewClient() {
            // the map's renderer died (out of memory, a WebView update): without this Android
            // closes OpenRath with it. Drop the map; the dashboard rebuilds it, or shows a note
            // after repeated crashes.
            @Override
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                died(android.os.SystemClock.elapsedRealtime());
                try {
                    removeView(v);
                    v.destroy();
                } catch (Throwable ignored) {
                }
                ready = false;
                final Context ctx = getContext();
                if (ctx instanceof HomeActivity) post(new Runnable() {
                    @Override
                    public void run() {
                        ((HomeActivity) ctx).build();
                    }
                });
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                ready = true;
                lastLat = lastLon = 0;
                lastPark = -1;
            }

            // The page is served from a made-up https address so the map tile servers see a
            // normal web page asking for tiles (some refuse requests from local files).
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, String url) {
                // map tiles: kept on the unit so places already seen show without internet
                if (Tiles.isTile(url) && host != null && Features.on(host.prefs(), "offlineMap")) return Tiles.serve(v.getContext(), url);
                if (!url.startsWith(MAP_BASE)) return null;
                String name = url.substring(MAP_BASE.length());
                int q = name.indexOf('?');
                if (q >= 0) name = name.substring(0, q);
                String type = name.endsWith(".js") ? "application/javascript"
                        : name.endsWith(".css") ? "text/css" : name.endsWith(".png") ? "image/png" : "text/html";
                try {
                    return new android.webkit.WebResourceResponse(type, "UTF-8", assets.open("map/" + name));
                } catch (java.io.IOException e) {
                    return null;
                }
            }
        });
        web.loadUrl(MAP_BASE + "map.html");
        final float r = Ui.RADIUS * Ui.k;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        setClipToOutline(true);
        web.addJavascriptInterface(new Bridge(), "OpenRath");
        web.setOnLongClickListener(edit);
        addView(web, new LayoutParams(-1, -1));
        View ring = new View(c);
        android.graphics.drawable.GradientDrawable g = Ui.panel();
        g.setColors(new int[]{0, 0});
        ring.setBackground(g);
        ring.setClickable(false);
        addView(ring, new LayoutParams(-1, -1));
    }

    /** What the map page can ask of the app. */
    final class Bridge {
        /** Turn-by-turn in Google Maps (when navigation is set to Google Maps). */
        @android.webkit.JavascriptInterface
        public void navigate(final double lat, final double lon, final String name) {
            post(new Runnable() {
                @Override
                public void run() {
                    host.navigate(lat, lon, name);
                }
            });
        }

        @android.webkit.JavascriptInterface
        public boolean online() {
            return Player.online(getContext());
        }

        /** The offline map app Go hands over to without internet, or "" when there is none. */
        @android.webkit.JavascriptInterface
        public String offlineNavName() {
            String pkg = OfflineNav.app(getContext(), host.prefs());
            return pkg == null ? "" : OfflineNav.label(getContext(), pkg);
        }

        @android.webkit.JavascriptInterface
        public void navigateOffline(final double lat, final double lon, final String name) {
            post(new Runnable() {
                @Override
                public void run() {
                    host.navigateOffline(lat, lon, name);
                }
            });
        }

        /** "own" to guide in this map, "google" to hand over to Google Maps. */
        @android.webkit.JavascriptInterface
        public String navMode() {
            return host.prefs().source("navi");
        }

        @android.webkit.JavascriptInterface
        public void setNavMode(String v) {
            host.prefs().setSource("navi", "google".equals(v) ? "google" : "own");
        }

        @android.webkit.JavascriptInterface
        public void speak(final String text) {
            post(new Runnable() {
                @Override
                public void run() {
                    host.speak(text);
                }
            });
        }

        /** Rain expected on the planned route ("" = none), shown in the weather box. */
        @android.webkit.JavascriptInterface
        public void routeRain(final String text) {
            post(new Runnable() {
                @Override
                public void run() {
                    Hub.routeRain = text == null ? "" : text;
                }
            });
        }

        /** Rupees per toll plaza, for the route's toll estimate. */
        @android.webkit.JavascriptInterface
        public int tollRate() {
            return Features.on(host.prefs(), "tolls") ? host.prefs().num("tollRate", 100) : 0;
        }

        /** Save where the car is now as the parking spot. */
        @android.webkit.JavascriptInterface
        public void parkHere(final double lat, final double lon) {
            post(new Runnable() {
                @Override
                public void run() {
                    Drive.setPark(getContext(), lat, lon);
                }
            });
        }

        /** Call a place's phone number through the unit's Bluetooth phone. */
        @android.webkit.JavascriptInterface
        public void call(final String number) {
            post(new Runnable() {
                @Override
                public void run() {
                    host.call(number);
                }
            });
        }

        /** "Mine" on a service centre: kept for the service booking reminder. */
        @android.webkit.JavascriptInterface
        public void saveService(String name, String phone) {
            host.prefs().put("myService", (name == null ? "" : name.replace("|", " ")) + "|" + (phone == null ? "" : phone));
        }

        /** Where the map is taking you, for the kids' distance countdown. */
        @android.webkit.JavascriptInterface
        public void destination(String name, double lat, double lon) {
            if (!host.prefs().str("dest", "").startsWith((name == null ? "" : name.replace("|", " ")) + "|"))
                host.prefs().put("destNick", "");   // a new place: the children's name for the old one no longer fits
            host.prefs().put("dest", (name == null ? "" : name.replace("|", " ")) + "|" + String.format(Locale.US, "%.6f|%.6f", lat, lon));
        }

        /** The map's saved Home and Office, so drives can be named for the commute insights. */
        @android.webkit.JavascriptInterface
        public void savedPlace(String name, double lat, double lon) {
            if ("Home".equals(name) || "Office".equals(name))
                host.prefs().put("place:" + name, String.format(Locale.US, "%.6f,%.6f", lat, lon));
        }

        /** Whether the parking panel shows its QR code. */
        @android.webkit.JavascriptInterface
        public boolean parkLink() {
            return Features.on(host.prefs(), "parkLink");
        }

        /** Tap on the parking panel's code: show it full screen and bright, which phones read far better. */
        @android.webkit.JavascriptInterface
        public void bigQr() {
            post(new Runnable() {
                @Override
                public void run() {
                    host.parkQr();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public String carBrand() {
            return Car.brandName(host.prefs());
        }

        /** Words to find the brand's workshops by in OpenStreetMap names, as a regular expression. */
        @android.webkit.JavascriptInterface
        public String brandWords() {
            return Car.searchWords(Car.brand(host.prefs()), Car.brandName(host.prefs()));
        }

        @android.webkit.JavascriptInterface
        public boolean serviceFinder() {
            return Features.on(host.prefs(), "serviceFinder");
        }

        @android.webkit.JavascriptInterface
        public void voice() {
            post(new Runnable() {
                @Override
                public void run() {
                    host.voiceSearch(MapCard.this);
                }
            });
        }
    }

    /** Run a function of the map page, e.g. "goFav('Home')". */
    void js(String code) {
        web.evaluateJavascript(code, null);
    }

    /** What voice search heard (null: nothing, or no speech recogniser on the unit). */
    void voiceResult(String heard) {
        String js = heard == null ? "voiceResult(null)" : "voiceResult(" + org.json.JSONObject.quote(heard) + ")";
        web.evaluateJavascript(js, null);
    }

    @Override
    void fit(int w, int h) {
    }

    @Override
    public void changed(Hub h) {
        if (ready && Drive.parkAt != lastPark) {
            lastPark = Drive.parkAt;
            if (Drive.parkAt > 0) web.evaluateJavascript(String.format(Locale.US, "setPark(%.6f,%.6f,%d)",
                    Drive.parkLat, Drive.parkLon, Drive.parkAt), null);
        }
        Location f = h.fix;
        if (!ready || f == null) return;
        if (Math.abs(f.getLatitude() - lastLat) < 1e-5 && Math.abs(f.getLongitude() - lastLon) < 1e-5) return;
        lastLat = f.getLatitude();
        lastLon = f.getLongitude();
        web.evaluateJavascript(String.format(Locale.US, "setPos(%.6f,%.6f,%.0f,%.1f)",
                lastLat, lastLon, f.hasBearing() && f.getSpeed() > 1 ? f.getBearing() : -1f,
                f.hasSpeed() ? f.getSpeed() : 0f), null);
    }

    // ------------------------------------------------------------------ kept between rebuilds

    /*
     * A WebView costs tens of megabytes and seconds to load the map page, so the dashboard keeps one
     * map box while any page shows the map, and moves it into each rebuilt dashboard (see
     * HomeActivity.build); it is destroyed once no page has the map, or the dashboard closes.
     */

    /** The page still runs: its renderer hasn't died and it hasn't been destroyed. */
    boolean alive() {
        return !gone && web.getParent() == this;
    }

    private boolean gone, paused;

    /** Into a rebuilt dashboard: out of the old one, in the look of the (maybe new) theme. */
    void reuse() {
        android.view.ViewParent p = getParent();
        if (p instanceof android.view.ViewGroup) ((android.view.ViewGroup) p).removeView(this);
        animate().cancel();
        setTranslationX(0);
        setTranslationY(0);
        setScaleX(1);
        setScaleY(1);
        setAlpha(1);
        setElevation(0);
        setBackground(Ui.panel());
        View ring = getChildCount() > 1 ? getChildAt(getChildCount() - 1) : null;
        if (ring != null && ring != web) {
            android.graphics.drawable.GradientDrawable g = Ui.panel();
            g.setColors(new int[]{0, 0});
            ring.setBackground(g);
        }
        final float r = Ui.RADIUS * Ui.k;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        resumeMap();
    }

    /** Off screen but kept (another page): the page stops drawing and animating. */
    void pauseMap() {
        if (gone || paused) return;
        paused = true;
        try {
            web.onPause();
        } catch (Throwable ignored) {
        }
    }

    void resumeMap() {
        if (gone || !paused) return;
        paused = false;
        try {
            web.onResume();
        } catch (Throwable ignored) {
        }
    }

    /** Let the page go for good. */
    void destroy() {
        if (gone) return;
        gone = true;
        ready = false;
        android.view.ViewParent p = getParent();
        if (p instanceof android.view.ViewGroup) ((android.view.ViewGroup) p).removeView(this);
        try {
            web.onPause();
            web.stopLoading();
            removeView(web);
            web.removeAllViews();
            web.destroy();
        } catch (Throwable ignored) {
        }
    }
}
