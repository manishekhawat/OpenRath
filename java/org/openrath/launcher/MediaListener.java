package org.openrath.launcher;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Granting it notification access lets the dashboard read what's playing, and feeds the
 * notification ticker: new messages from the apps the owner picked slide in along the top.
 */
public class MediaListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Prefs p = new Prefs(this);
            if (!Features.on(p, "ticker") && !Features.on(p, "readAloud")) return;
            String pkg = sbn.getPackageName();
            if (pkg.equals(getPackageName()) || !Ticker.watched(p, pkg)) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0 || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if (Notification.CATEGORY_TRANSPORT.equals(n.category) || Notification.CATEGORY_CALL.equals(n.category)) return;
            Bundle x = n.extras;
            CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = x.getCharSequence(Notification.EXTRA_TEXT);
            if (title == null && text == null) return;
            String app;
            try {
                app = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
            } catch (Exception e) {
                app = pkg;
            }
            if (!Ticker.post(app, title == null ? "" : title.toString(), text == null ? "" : text.toString())) return;
            sendBroadcast(new Intent(Ticker.CHANGED).setPackage(getPackageName()));
        } catch (Throwable ignored) {
        }
    }
}
