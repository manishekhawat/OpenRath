package com.android.fmradio;

/**
 * The unit's own FM chip driver (libfmjni, MediaTek's FM radio), with exactly the class name and
 * methods the stock radio app (com.tw.radio) uses, so the library binds to it. OpenRath uses it to
 * play FM without the stock radio app (org.openrath.launcher.Tuner). Loading may be refused on
 * units that keep the library to system apps; Tuner then says so and the old way is used.
 */
public class FmNative {

    static {
        System.loadLibrary("fmjni");
    }

    public static class FmAudioThresholdParms {
        int hbound, lbound, power_th;
        byte phyt, snr_th;
    }

    public static class FmRegCtlParms {
        int addr, val;
        byte err, rw_flag;
    }

    public static class FmSeekCriteriaParms {
        int freq_offset_th, noise_power_th, pilot_power_th, rssi_th;
        byte snr_th;
    }

    public static native short activeAf();

    public static native short[] autoScan(int band);

    public static native boolean closeDev();

    public static native int getAudioParm(FmAudioThresholdParms p);

    public static native int getBler();

    public static native byte[] getLrText();

    public static native byte[] getPs();

    public static native int getRssi();

    public static native int getSnr();

    public static native int getTuneParm(FmSeekCriteriaParms p);

    public static native int isRdsSupport();

    public static native boolean openDev();

    public static native boolean powerDown(int type);

    public static native boolean powerUp(float mhz);

    public static native short readRds();

    public static native int readRegParm(FmRegCtlParms p);

    public static native float seek(float mhz, boolean up);

    public static native int setAudioParm(FmAudioThresholdParms p);

    public static native int setDx(int on);

    public static native int setMute(boolean mute);

    public static native int setRds(boolean on);

    public static native int setTuneParm(FmSeekCriteriaParms p);

    public static native boolean stopScan();

    public static native int switchAntenna(int antenna);

    public static native boolean tune(float mhz);

    public static native int writeRegParm(FmRegCtlParms p);
}
