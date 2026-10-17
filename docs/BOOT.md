# Boot logo and animation

OpenRath can make a still boot logo and a boot animation with your car's badge (or your own logo picture, or the OpenRath wheel), the brand and the car's name: **Settings > Themes > Boot logo and animation**. Pick the screen size (this screen is listed first) and it saves `bootlogo.bmp` and `bootanimation.zip` to `Download/OpenRath boot <size>`, and onto a USB stick if one is plugged in and writable.

Android doesn't let an app install either file, so the stereo has to. What follows is the general route; menu names, codes and folders differ between stereo makers, board versions and sellers.

## Before you start

- Photograph every page of the stereo's factory settings (CAN bus, steering keys, amplifier). A reset can wipe them.
- Note the logo that's chosen now, so you can pick it again.
- For the animation: keep firmware that matches your exact board version on a USB stick. If a changed animation stops the stereo starting, reflashing is the way back.

## Still boot logo (low risk)

The file is a 24-bit BMP at the screen's exact size.

1. Put `bootlogo.bmp` in the top folder of a FAT32 USB stick, and a copy in a folder named `logo` on it (firmwares differ in where they look). The app does this itself when it can write to the stick.
2. Open the stereo's Settings > Car settings (or Vehicle settings) > Factory settings. The code is in the manual or from the seller; on Topway TS units `123456` and `8888` are often reported. Avoid codes reported as factory reset (on Topway, `7890`).
3. Find Logo, Car logo or Boot logo, choose Custom or Import, and pick the BMP.
4. Save and restart.

A bad logo only affects the first seconds after power-on; the stereo still starts, and you can go back to the menu.

## Boot animation

### With the stereo's own boot animation app (no root)

Some firmwares (for example Topway UIS8141E boards on Android 8.1) have a Boot animation app. OpenRath offers to open it when it finds one. Import `bootanimation.zip` from the USB stick and apply it. On Topway the import is reported to be in the notification shade's MENU, with the password `Topway`.

### With ADB (needs a firmware that allows adb root)

```
adb root
adb remount
adb shell ls -l /system/media/bootanimation.zip /oem/media/bootanimation.zip /product/media/bootanimation.zip
adb pull /system/media/bootanimation.zip bootanimation-original.zip      # keep this safe
adb push bootanimation.zip /system/media/bootanimation.zip
adb shell chmod 644 /system/media/bootanimation.zip
adb shell chown root:root /system/media/bootanimation.zip
adb shell sync
adb reboot
```

Use whichever of the three paths exists. If `adb root` refuses, this firmware doesn't allow it; use the stereo's app instead. If `remount` fails over verity, `adb disable-verity`, reboot, and start again. To undo, push `bootanimation-original.zip` back the same way.

If the stereo sits on the boot screen for more than about three minutes, the animation was not accepted: reflash the firmware.

## What's in the files

- `bootlogo.bmp`: 24-bit, uncompressed, the screen's size, the animation's last frame.
- `bootanimation.zip`: stored (not compressed), with `desc.txt` (`<width> <height> 25`, play `part0` once, then hold `part1` until Android has started), 90 frames in `part0` (3.6 s) and one in `part1`. The mark fades in (the wheel rolls in), a light sweeps across it, a line grows under it, then the brand and the car's name appear.
