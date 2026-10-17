package org.openrath.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional: with this switched on in Android's accessibility settings, steering-wheel buttons
 * work even when another app (navigation, music) fills the screen. It only looks at key presses
 * and only keeps the ones the owner has given an action.
 */
public class KeyService extends AccessibilityService {

    static volatile boolean running;

    @Override
    protected void onServiceConnected() {
        running = true;
        Keys.init(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        // the dashboard handles keys itself while it's on screen
        return !HomeActivity.visible && Keys.onKey(e);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        // child-lock: a locked app opened while moving goes straight back home
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        if (prefs == null) prefs = new Prefs(this);
        if (ChildLock.blocked(prefs, e.getPackageName().toString())) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            android.widget.Toast.makeText(this, Hindi.t(ChildLock.SORRY), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private Prefs prefs;

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }
}
