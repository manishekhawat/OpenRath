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

import java.io.IOException;

/**
 * The wire to the phone: bytes in and out, nothing more. AaUsb.Link is the USB cable (the phone
 * in accessory mode); AaWireless.Link is a TCP socket for wireless Android Auto. AaSession does
 * the rest the same way on either.
 */
interface AaLink {

    /** Reads some bytes (at least one) into b, waiting as long as it takes; -1 or IOException when the wire is gone. */
    int read(byte[] b, int off, int len) throws IOException;

    /** Writes all the bytes, or throws. */
    void write(byte[] b, int off, int len) throws IOException;

    /** Ends the link; a read or write waiting on it returns or throws soon after. */
    void close();

    /** Says which phone this is, to tell an unplugged phone from another device. */
    String name();

    /** Whether this is a wireless link (Android Auto expects pings on those). */
    boolean wireless();
}
