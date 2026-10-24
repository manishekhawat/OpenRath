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

import android.content.Context;
import android.content.res.Resources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * The head unit's certificate and private key. A phone only talks to a head unit that shows one
 * signed for car makers, and OpenRath does not ship one: it borrows the one inside an installed
 * Headunit Revived (or Open Headunit) app, else reads the two files the owner copied into
 * OpenRath/androidauto/ (cert.pem and privkey.pem). Nothing is copied out of either place.
 */
final class AaCert {

    /** Apps that carry a head unit certificate as raw resources named "cert" and "privkey". */
    static final String[] APPS = {"com.andrerinas.headunitrevived", "com.andrerinas.openheadunit"};
    static final String FOLDER = "androidauto", CERT = "cert.pem", KEY = "privkey.pem";

    /** Where the certificate came from, for the screen: an app's package name, "files", or "" when there is none. */
    static String from(Context c) {
        for (String pkg : APPS) {
            try {
                Resources r = c.getPackageManager().getResourcesForApplication(pkg);
                if (r.getIdentifier("cert", "raw", pkg) != 0 && r.getIdentifier("privkey", "raw", pkg) != 0) return pkg;
            } catch (Exception e) {
                // not installed
                continue;
            }
        }
        File d = Storage.dir(FOLDER);
        if (new File(d, CERT).isFile() && new File(d, KEY).isFile()) return "files";
        return "";
    }

    /** The TLS setup with the certificate, or null when there is none or it can't be read (noted for Diagnostics). */
    static SSLContext context(Context c) {
        String src = from(c);
        if (src.isEmpty()) return null;
        try {
            byte[] cert, key;
            if ("files".equals(src)) {
                File d = Storage.dir(FOLDER);
                cert = read(new FileInputStream(new File(d, CERT)));
                key = read(new FileInputStream(new File(d, KEY)));
            } else {
                Resources r = c.getPackageManager().getResourcesForApplication(src);
                cert = read(r.openRawResource(r.getIdentifier("cert", "raw", src)));
                key = read(r.openRawResource(r.getIdentifier("privkey", "raw", src)));
            }
            X509Certificate x = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(cert));
            PrivateKey k = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyDer(new String(key, "US-ASCII"))));
            SSLContext s = SSLContext.getInstance("TLS");
            s.init(new KeyManager[]{new OneKey(x, k)}, new TrustManager[]{new AnyPhone()}, null);
            return s;
        } catch (Exception e) {
            Diag.note("android auto certificate", e);
            return null;
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0 && o.size() < 65536) o.write(b, 0, n);
            return o.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The bytes between a PEM file's BEGIN and END lines of this kind ("PRIVATE KEY"), or null. */
    static byte[] pem(String text, String kind) {
        String begin = "-----BEGIN " + kind + "-----", end = "-----END " + kind + "-----";
        int a = text.indexOf(begin), b = a < 0 ? -1 : text.indexOf(end, a);
        if (a < 0 || b < 0) return null;
        String body = text.substring(a + begin.length(), b).replaceAll("\\s", "");
        try {
            return java.util.Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A private key file as PKCS#8 (what Java reads): "PRIVATE KEY" as it is, "RSA PRIVATE KEY" (PKCS#1) wrapped. */
    static byte[] keyDer(String text) throws IOException {
        byte[] k = pem(text, "PRIVATE KEY");
        if (k != null) return k;
        k = pem(text, "RSA PRIVATE KEY");
        if (k != null) return pkcs8(k);
        throw new IOException("no private key in the file");
    }

    /** Wraps a PKCS#1 RSA key in PKCS#8: version 0, the rsaEncryption algorithm, the key as an octet string. */
    static byte[] pkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octets = der(0x04, pkcs1);
        byte[] body = new byte[version.length + algorithm.length + octets.length];
        System.arraycopy(version, 0, body, 0, version.length);
        System.arraycopy(algorithm, 0, body, version.length, algorithm.length);
        System.arraycopy(octets, 0, body, version.length + algorithm.length, octets.length);
        return der(0x30, body);
    }

    /** One DER item: its tag, its length (short or long form), its bytes. */
    static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int n = content.length;
        if (n < 128) o.write(n);
        else {
            int bytes = n > 0xFFFFFF ? 4 : n > 0xFFFF ? 3 : n > 0xFF ? 2 : 1;
            o.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) o.write(n >> (8 * i));
        }
        o.write(content, 0, n);
        return o.toByteArray();
    }

    /** Hands TLS the one certificate and key, whatever it asks for (the reference's SingleKeyKeyManager). */
    private static final class OneKey extends X509ExtendedKeyManager {
        private static final String ALIAS = "openrath";
        private final X509Certificate cert;
        private final PrivateKey key;

        OneKey(X509Certificate cert, PrivateKey key) {
            this.cert = cert;
            this.key = key;
        }

        @Override
        public String[] getClientAliases(String type, Principal[] issuers) {
            return new String[]{ALIAS};
        }

        @Override
        public String chooseClientAlias(String[] type, Principal[] issuers, Socket s) {
            return ALIAS;
        }

        @Override
        public String chooseEngineClientAlias(String[] type, Principal[] issuers, SSLEngine e) {
            return ALIAS;
        }

        @Override
        public String[] getServerAliases(String type, Principal[] issuers) {
            return new String[]{ALIAS};
        }

        @Override
        public String chooseServerAlias(String type, Principal[] issuers, Socket s) {
            return ALIAS;
        }

        @Override
        public String chooseEngineServerAlias(String type, Principal[] issuers, SSLEngine e) {
            return ALIAS;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[]{cert};
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return key;
        }
    }

    /**
     * Accepts the phone's certificate without checking it, as every head unit and the reference do:
     * the phone's own certificate is signed by Google for phones, and only the cable or the car's
     * Wi-Fi leads to it.
     */
    private static final class AnyPhone implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String type) {
            // the head unit is the client; this is never asked
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String type) {
            // the phone: accepted, see above
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private AaCert() {
    }
}
