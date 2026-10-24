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

import java.io.ByteArrayOutputStream;

/**
 * The few bits of protocol buffers Android Auto needs, written by hand: numbers (varints), true or
 * false, text, bytes and messages inside messages. Writer builds a message field by field; Reader
 * walks one. No protobuf library: the message shapes are in the reference's .proto files.
 */
final class AaProto {

    static final int VARINT = 0, FIXED64 = 1, BYTES = 2, FIXED32 = 5;

    /** Builds one message. Each call adds a field; bytes() gives the encoded message. */
    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        /** A number field (int32, uint32, int64, enum). A negative int is sign-extended to 10 bytes, as protobuf does. */
        Writer num(int field, long v) {
            tag(field, VARINT);
            raw(out, v);
            return this;
        }

        Writer bool(int field, boolean b) {
            return num(field, b ? 1 : 0);
        }

        /** A sint32 field (zigzag, so small negative numbers stay short). */
        Writer sint(int field, int v) {
            tag(field, VARINT);
            raw(out, ((long) ((v << 1) ^ (v >> 31))) & 0xFFFFFFFFL);
            return this;
        }

        Writer text(int field, String s) {
            byte[] b;
            try {
                b = (s == null ? "" : s).getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                b = new byte[0];   // UTF-8 is always there
            }
            return bytes(field, b);
        }

        Writer bytes(int field, byte[] b) {
            tag(field, BYTES);
            raw(out, b.length);
            out.write(b, 0, b.length);
            return this;
        }

        /** A message inside this one. */
        Writer msg(int field, Writer w) {
            return bytes(field, w.bytes());
        }

        byte[] bytes() {
            return out.toByteArray();
        }

        private void tag(int field, int wire) {
            raw(out, ((long) field << 3) | wire);
        }
    }

    /** Writes v as a varint: seven bits a byte, lowest first, the top bit set on all but the last. */
    static void raw(ByteArrayOutputStream out, long v) {
        while ((v & ~0x7FL) != 0) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }

    /** The varint encoding of v alone (for the tests). */
    static byte[] varint(long v) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        raw(o, v);
        return o.toByteArray();
    }

    /**
     * Walks a message's fields: while (r.next()) { switch (r.field) ... }, reading each with num(),
     * bytes() or msg(), or skipping it with skip(). A broken message ends the walk (next() is false).
     */
    static final class Reader {
        private final byte[] b;
        private int pos;
        private final int end;
        int field, wire;
        private boolean read;

        Reader(byte[] b, int off, int len) {
            this.b = b;
            this.pos = off;
            this.end = Math.min(b.length, off + Math.max(0, len));
        }

        Reader(byte[] b) {
            this(b, 0, b.length);
        }

        boolean next() {
            if (field != 0 && !read) skip();
            read = false;
            if (pos >= end) return false;
            long t = varintAt();
            if (t < 0) return false;
            field = (int) (t >>> 3);
            wire = (int) (t & 7);
            return field > 0;
        }

        long num() {
            read = true;
            if (wire == VARINT) return varintAt();
            if (wire == FIXED64) return fixed(8);
            if (wire == FIXED32) return fixed(4);
            skipValue();
            return 0;
        }

        boolean bool() {
            return num() != 0;
        }

        /** A sint32 field (zigzag). */
        int sint() {
            long v = num();
            return (int) ((v >>> 1) ^ -(v & 1));
        }

        byte[] bytes() {
            read = true;
            if (wire != BYTES) {
                skipValue();
                return new byte[0];
            }
            long n = varintAt();
            if (n < 0 || n > end - pos) {
                pos = end;
                return new byte[0];
            }
            byte[] o = new byte[(int) n];
            System.arraycopy(b, pos, o, 0, (int) n);
            pos += (int) n;
            return o;
        }

        String text() {
            try {
                return new String(bytes(), "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                return "";   // UTF-8 is always there
            }
        }

        Reader msg() {
            return new Reader(bytes());
        }

        void skip() {
            read = true;
            skipValue();
        }

        private void skipValue() {
            if (wire == VARINT) varintAt();
            else if (wire == FIXED64) pos += 8;
            else if (wire == FIXED32) pos += 4;
            else if (wire == BYTES) {
                long n = varintAt();
                pos = n < 0 || n > end - pos ? end : pos + (int) n;
            } else pos = end;   // groups are not used by Android Auto
        }

        private long varintAt() {
            long v = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) {
                    pos = end;
                    return -1;
                }
                int x = b[pos++] & 0xFF;
                v |= (long) (x & 0x7F) << shift;
                if ((x & 0x80) == 0) return v;
            }
            return -1;
        }

        private long fixed(int n) {
            if (pos + n > end) {
                pos = end;
                return 0;
            }
            long v = 0;
            for (int i = 0; i < n; i++) v |= (long) (b[pos + i] & 0xFF) << (8 * i);
            pos += n;
            return v;
        }
    }

    /** The first number field `field` in a message, or `def` when it has none. */
    static long find(byte[] b, int off, int len, int field, long def) {
        Reader r = new Reader(b, off, len);
        while (r.next()) if (r.field == field && r.wire == VARINT) return r.num();
        return def;
    }

    private AaProto() {
    }
}
