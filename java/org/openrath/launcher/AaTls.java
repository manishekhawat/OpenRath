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
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

/**
 * The encryption: TLS with the head unit as the client, carried inside Android Auto messages
 * (type 3 on the control channel) during the handshake, then one TLS record per frame. An
 * SSLEngine does the work; this class only moves its bytes.
 */
final class AaTls {

    /** How the handshake reaches the phone: the plain handshake messages' payloads, each way. */
    interface Wire {
        byte[] readHandshake() throws IOException;

        void writeHandshake(byte[] data) throws IOException;
    }

    private static final long HANDSHAKE_MS = 15000;

    private final SSLEngine engine;
    private final ByteBuffer plain;

    AaTls(SSLContext context) {
        // the host and port are only a name for the session cache, never looked up
        engine = context.createSSLEngine("android-auto", 5277);
        engine.setUseClientMode(true);
        plain = ByteBuffer.allocate(Math.max(engine.getSession().getApplicationBufferSize(), 32768) + 64);
    }

    /** The TLS handshake; throws when it fails or takes over 15 seconds. */
    void handshake(Wire wire) throws IOException {
        engine.beginHandshake();
        long until = System.currentTimeMillis() + HANDSHAKE_MS;
        ByteBuffer in = ByteBuffer.allocate(0);
        ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize() + 64);
        while (true) {
            SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
            if (hs == SSLEngineResult.HandshakeStatus.FINISHED || hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) return;
            if (System.currentTimeMillis() > until) throw new IOException("TLS handshake timed out");
            switch (hs) {
                case NEED_WRAP: {
                    out.clear();
                    SSLEngineResult r = engine.wrap(ByteBuffer.allocate(0), out);
                    tasks();
                    if (r.getStatus() != SSLEngineResult.Status.OK && r.getStatus() != SSLEngineResult.Status.CLOSED) {
                        throw new SSLException("TLS wrap: " + r.getStatus());
                    }
                    out.flip();
                    byte[] b = new byte[out.remaining()];
                    out.get(b);
                    if (b.length > 0) wire.writeHandshake(b);
                    break;
                }
                case NEED_UNWRAP: {
                    if (!in.hasRemaining()) in = join(in, wire.readHandshake());
                    plain.clear();
                    SSLEngineResult r = engine.unwrap(in, plain);
                    tasks();
                    if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        in = join(in, wire.readHandshake());   // half a record: the rest is in the next message
                    } else if (r.getStatus() != SSLEngineResult.Status.OK) {
                        throw new SSLException("TLS unwrap: " + r.getStatus());
                    }
                    break;
                }
                case NEED_TASK:
                    tasks();
                    break;
                default:
                    throw new SSLException("TLS handshake: " + hs);
            }
        }
    }

    /** What is left of `in` followed by `more`. */
    private static ByteBuffer join(ByteBuffer in, byte[] more) {
        ByteBuffer b = ByteBuffer.allocate(in.remaining() + more.length);
        b.put(in).put(more).flip();
        return b;
    }

    private void tasks() {
        Runnable t;
        while ((t = engine.getDelegatedTask()) != null) t.run();
    }

    /** One message, encrypted. */
    synchronized byte[] encrypt(byte[] b, int off, int len) throws IOException {
        ByteBuffer src = ByteBuffer.wrap(b, off, len);
        ByteArrayOutputStream o = new ByteArrayOutputStream(len + 64);
        ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize() + 64);
        while (src.hasRemaining()) {
            out.clear();
            SSLEngineResult r = engine.wrap(src, out);
            if (r.getStatus() != SSLEngineResult.Status.OK) throw new SSLException("TLS wrap: " + r.getStatus());
            out.flip();
            o.write(out.array(), 0, out.limit());
        }
        return o.toByteArray();
    }

    /** The start of a TLS record cut at the end of the last frame, kept for the next one. */
    private byte[] carry = new byte[0];

    /** One frame's body, decrypted (may be empty: TLS sometimes sends records of its own). */
    synchronized byte[] decrypt(byte[] b, int off, int len) throws IOException {
        ByteBuffer src;
        if (carry.length > 0) {
            src = ByteBuffer.allocate(carry.length + len);
            src.put(carry).put(b, off, len).flip();
            carry = new byte[0];
        } else src = ByteBuffer.wrap(b, off, len);
        ByteArrayOutputStream o = new ByteArrayOutputStream(len);
        while (src.hasRemaining()) {
            plain.clear();
            SSLEngineResult r = engine.unwrap(src, plain);
            if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                if (src.remaining() > 65536) throw new SSLException("TLS record too long");
                carry = new byte[src.remaining()];   // half a record: the rest comes in the next frame
                src.get(carry);
                break;
            }
            if (r.getStatus() != SSLEngineResult.Status.OK) throw new SSLException("TLS unwrap: " + r.getStatus());
            plain.flip();
            o.write(plain.array(), 0, plain.limit());
            if (r.bytesConsumed() == 0 && r.bytesProduced() == 0) break;
        }
        return o.toByteArray();
    }

    void close() {
        try {
            engine.closeOutbound();
        } catch (Throwable e) {
            Diag.note("android auto tls close", e);
        }
    }
}
