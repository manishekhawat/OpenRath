package org.openrath.launcher;

/**
 * A fingerprint of many values, to tell cheaply whether any of them changed since last time,
 * without building a long string of them all. No Android types, so it is unit-tested.
 */
final class Stamp {

    private long h = 1125899906842597L;

    Stamp reset() {
        h = 1125899906842597L;
        return this;
    }

    Stamp add(long v) {
        h = 31 * h + (v ^ (v >>> 32));
        h ^= h >>> 29;
        return this;
    }

    Stamp add(boolean v) {
        return add(v ? 1231 : 1237);
    }

    Stamp add(double v) {
        return add(Double.doubleToLongBits(v));
    }

    Stamp add(Object o) {
        return add(o == null ? 0 : o.hashCode() * 0x9E3779B97F4A7C15L + (o instanceof String ? ((String) o).length() : 0));
    }

    long value() {
        return h;
    }
}
