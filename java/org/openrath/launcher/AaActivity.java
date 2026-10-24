/*
 * OpenRath's built-in Android Auto receiver.
 *
 * Derived from Headunit Revived / Open Headunit by Andre Rinas
 * (github.com/andreknieriem/headunit-revived), which is based on headunit by Michael Reid
 * (github.com/mikereidis/headunit, Copyright 2011-2015 Michael A. Reid). Credit to Michael Reid
 * (headunit) and Andre Rinas (Headunit Revived / Open Headunit).
 * Copyright (C) Manish Shekhawat and the OpenRath contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. It is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See LICENSES/AGPL-3.0.txt for the full text.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openrath.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The Android Auto screen: the phone's picture over the whole screen, kept in proportion, and
 * touches passed back to the phone. Before a phone is connected it says what to do (plug it in,
 * or what is missing). Its own small screen rather than a page of the dashboard, because the
 * picture needs a surface of its own that lives and dies with what is on screen, and because
 * Android starts it directly when a phone is plugged in. Back, Home and the phone's own exit
 * button bring the dashboard back; the phone's sound goes on until it is unplugged.
 */
public class AaActivity extends Activity implements SurfaceHolder.Callback, AaSession.Ui {

    private FrameLayout root;
    private SurfaceView picture;
    private TextView text;
    private boolean surfaceReady;
    private AaSession attached;

    /** Opens the screen (from the music box's phone button, or when a phone starts Android Auto). */
    static void open(Context c) {
        try {
            c.startActivity(new Intent(c, AaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception e) {
            Diag.note("android auto screen", e);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs p = new Prefs(this);
        if (!Features.on(p, "androidAuto")) {
            finish();   // plugged in while the switch is off: stay out of the way
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        picture = new SurfaceView(this);
        picture.getHolder().addCallback(this);
        root.addView(picture, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        text = new TextView(this);
        text.setTextColor(Ui.SILVER);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        text.setGravity(Gravity.CENTER);
        int pad = (int) (32 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        root.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int bt, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || bt - t != ob - ot) place();
            }
        });
        picture.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return touch(v, e);
            }
        });
        setContentView(root);
        immersive();
    }

    private void immersive() {
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root == null) return;
        AaSession.ui(this);
        AaUsb.listen(this);
        AaUsb.check(this, true);
        askWireless();
        immersive();
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        AaSession.ui(null);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (root != null) AaUsb.check(this, true);   // another phone plugged in, or the music box's button
    }

    private boolean askedWireless;

    /** With wireless on: Android's questions for Bluetooth and the phone's Wi-Fi network, once per visit. */
    private void askWireless() {
        if (askedWireless || !Features.on(new Prefs(this), "androidAutoWireless")) return;
        askedWireless = true;
        java.util.List<String> want = new java.util.ArrayList<>();
        want.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (android.os.Build.VERSION.SDK_INT >= 31) want.add("android.permission.BLUETOOTH_CONNECT");
        if (android.os.Build.VERSION.SDK_INT >= 33) want.add("android.permission.NEARBY_WIFI_DEVICES");
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String w : want) if (checkSelfPermission(w) != android.content.pm.PackageManager.PERMISSION_GRANTED) missing.add(w);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 7);
    }

    /** Sizes the picture to the screen, in proportion and centred. */
    private void place() {
        AaSession s = AaSession.current();
        int code = AaScreen.pick(root.getWidth(), root.getHeight());
        int vw = s != null ? s.videoWidth() : AaScreen.width(code), vh = s != null ? s.videoHeight() : AaScreen.height(code);
        int[] f = AaScreen.fit(root.getWidth(), root.getHeight(), vw, vh);
        if (f[2] <= 0 || f[3] <= 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) picture.getLayoutParams();
        if (lp.width == f[2] && lp.height == f[3]) return;
        lp.width = f[2];
        lp.height = f[3];
        lp.gravity = Gravity.CENTER;
        picture.setLayoutParams(lp);
    }

    /** What the screen says while there's no picture, and the picture hooked up when a session is there. */
    private void render() {
        AaSession s = AaSession.current();
        if (s != null) {
            bind(s);
            if (text.getText().length() == 0) text.setText(Hindi.t("Connecting to the phone…"));
            return;
        }
        if (AaCert.from(this).isEmpty()) {
            text.setText(Hindi.t("Android Auto needs a head unit certificate, which OpenRath can't include. Install the Headunit Revived app on this unit (OpenRath uses its certificate), or copy cert.pem and privkey.pem into the androidauto folder inside OpenRath's folder."));
        } else if (!AaSession.lastEnd.isEmpty()) {
            text.setText(AaSession.lastEnd + "\n\n" + Hindi.t("Plug your phone into the unit's USB port to start Android Auto"));
        } else if (Features.on(new Prefs(this), "androidAutoWireless")) {
            text.setText(Hindi.t("Plug your phone into the unit's USB port to start Android Auto") + "\n\n"
                    + Hindi.t("Or keep Bluetooth on: a paired phone with wireless Android Auto connects by itself in a minute"));
        } else {
            text.setText(Hindi.t("Plug your phone into the unit's USB port to start Android Auto"));
        }
        text.setVisibility(View.VISIBLE);
    }

    /** Puts a session's picture on this screen once both are there. */
    private void bind(AaSession s) {
        if (!surfaceReady || s == null || attached == s) return;
        attached = s;
        place();
        s.attach(picture.getHolder().getSurface());
    }

    private boolean touch(View v, MotionEvent e) {
        AaSession s = AaSession.current();
        if (s == null || attached != s) return false;
        int action = e.getActionMasked();
        if (action > MotionEvent.ACTION_POINTER_UP) return true;   // hover and scroll: not for the phone
        int n = e.getPointerCount();
        int[] ids = new int[n], xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = e.getPointerId(i);
            xs[i] = AaScreen.toVideo(e.getX(i), 0, v.getWidth(), s.videoWidth());
            ys[i] = AaScreen.toVideo(e.getY(i), 0, v.getHeight(), s.videoHeight());
        }
        s.touch(action, e.getActionIndex(), ids, xs, ys);
        if (action == MotionEvent.ACTION_DOWN) text.setVisibility(View.GONE);
        return true;
    }

    // ---- the surface

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        bind(AaSession.current());
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int ht) {
        // the decoder scales to whatever size the surface has
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        AaSession s = attached;
        attached = null;
        if (s != null) s.detach();
    }

    // ---- the session

    @Override
    public void show(String line) {
        text.setText(line);
        text.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
        bind(AaSession.current());
    }

    @Override
    public void leave() {
        finish();
    }

    @Override
    public void ended(String why) {
        if (why != null && !why.isEmpty()) Toast.makeText(this, why, Toast.LENGTH_LONG).show();
        finish();   // the dashboard comes back
    }
}
