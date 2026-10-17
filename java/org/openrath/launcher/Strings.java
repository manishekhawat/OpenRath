package org.openrath.launcher;

import java.util.ArrayList;
import java.util.List;

/** Reads the string tables of a dex file and of a binary AndroidManifest.xml. */
final class Strings {

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
    }

    private static int u32(byte[] b, int o) {
        return u16(b, o) | u16(b, o + 2) << 16;
    }

    /** All strings in a dex file (ASCII ones are all we need, so MUTF-8 is read as Latin-1). */
    static List<String> dex(byte[] b) {
        List<String> out = new ArrayList<>();
        if (b.length < 0x70 || b[0] != 'd' || b[1] != 'e' || b[2] != 'x') return out;
        int count = u32(b, 0x38), ids = u32(b, 0x3C);
        for (int i = 0; i < count; i++) {
            int at = ids + 4 * i;
            if (at + 4 > b.length) break;
            int o = u32(b, at);
            while (o < b.length && (b[o] & 0x80) != 0) o++;   // skip the uleb128 length
            o++;
            int end = o;
            while (end < b.length && b[end] != 0) end++;
            if (end - o >= 6 && end - o <= 120) out.add(new String(b, o, end - o, java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        return out;
    }

    /** The string pool of a binary XML file. */
    static List<String> axml(byte[] b) {
        List<String> out = new ArrayList<>();
        if (b.length < 36 || u16(b, 8) != 0x0001) return out;
        int pool = 8, header = u16(b, pool + 2);
        int count = u32(b, pool + 8), flags = u32(b, pool + 16), start = u32(b, pool + 20);
        boolean utf8 = (flags & 0x100) != 0;
        for (int i = 0; i < count; i++) {
            int at = pool + header + 4 * i;
            if (at + 4 > b.length) break;
            int o = pool + start + u32(b, at);
            if (o + 2 > b.length) break;
            try {
                if (utf8) {
                    o += (b[o] & 0x80) != 0 ? 2 : 1;          // length in characters
                    int n = b[o] & 0xFF;
                    if ((n & 0x80) != 0) {
                        n = (n & 0x7F) << 8 | (b[o + 1] & 0xFF);
                        o += 2;
                    } else o += 1;
                    out.add(new String(b, o, n, "UTF-8"));
                } else {
                    int n = u16(b, o);
                    if ((n & 0x8000) != 0) {
                        n = (n & 0x7FFF) << 16 | u16(b, o + 2);
                        o += 4;
                    } else o += 2;
                    out.add(new String(b, o, n * 2, "UTF-16LE"));
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private Strings() {
    }
}
