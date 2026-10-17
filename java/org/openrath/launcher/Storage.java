package org.openrath.launcher;

import java.io.File;

/**
 * The app's own folder on the unit's storage, OpenRath/. Earlier versions (named Dzire Home) used
 * DzireHome/; that folder is renamed once so themes, backups, dashcam clips and documents carry over.
 */
final class Storage {

    static final String NAME = "OpenRath", OLD = "DzireHome";

    static File root() {
        return new File(android.os.Environment.getExternalStorageDirectory(), NAME);
    }

    static File dir(String sub) {
        return new File(root(), sub);
    }

    /** Rename OpenRath/ to OpenRath/ when only the old one exists; true when it moved. */
    static boolean migrate() {
        try {
            File old = new File(android.os.Environment.getExternalStorageDirectory(), OLD), now = root();
            return old.isDirectory() && !now.exists() && old.renameTo(now);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
