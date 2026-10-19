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

    /** A file named by a theme or a backup: the bare name only, never a path ("." and ".." give `fallback`). */
    static String bareName(String name, String fallback) {
        String n = name == null ? "" : new File(name).getName();
        return n.isEmpty() || n.equals(".") || n.equals("..") ? fallback : n;
    }

    /** The folder a .zip unpacks into, from its file name; null when no usable name is left ("..zip"). */
    static String folderFor(String zipName) {
        String folder = zipName == null ? "" : zipName.replaceAll("(?i)\\.zip$", "").replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
        if (folder.isEmpty() || folder.replace(".", "").isEmpty()) return null;   // ".", ".." and the like
        return folder;
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
