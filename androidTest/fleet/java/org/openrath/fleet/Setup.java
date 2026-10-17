package org.openrath.fleet;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

/**
 * Sets or clears OpenRath's managed settings, as a fleet's console would:
 *   adb shell am instrument -w -e fleet set org.openrath.fleet/.Setup
 *   adb shell am instrument -w -e fleet clear org.openrath.fleet/.Setup
 * The fleet app must be device owner first (adb shell dpm set-device-owner org.openrath.fleet/.Admin).
 */
public final class Setup extends Instrumentation {

    private String mode;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? null : arguments.getString("fleet");
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        Bundle out = new Bundle();
        int code = Activity.RESULT_OK;
        try {
            Context c = getContext();
            DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(c, Admin.class);
            Bundle r = new Bundle();
            if ("set".equals(mode)) {
                r.putString("level", "full");
                r.putBoolean("lockLevel", true);
                r.putString("featuresOn", "backup, reports");
                r.putString("featuresOff", "news,kids");
                r.putBoolean("devMode", false);
                r.putString("reportUrl", "https://fleet.example/openrath");
            }
            dpm.setApplicationRestrictions(admin, "org.openrath.launcher", r);
            out.putString(REPORT_KEY_STREAMRESULT, "fleet " + mode + ": done\n");
        } catch (Throwable e) {
            out.putString(REPORT_KEY_STREAMRESULT, "fleet " + mode + ": FAIL " + e + "\n");
            code = Activity.RESULT_CANCELED;
        }
        finish(code, out);
    }
}
