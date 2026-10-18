package org.openrath.launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * 360 view of the owner's car: OpenRath's own 3D model (Car3D) in the car's shape, colour and number
 * plate, turned with a finger or by the Top, Front, Side and Rear buttons. Free, with no unlock codes.
 * It also finds the stereo's own 360 camera app (the one that stitches the four cameras, on units
 * that have the kit) and opens it; OpenRath can't read those cameras itself.
 */
final class View360 {

    /** Names that 360 camera apps go by. */
    static boolean looks360(String pkg, String label) {
        String s = (pkg + " " + label).toLowerCase(Locale.US);
        return s.matches(".*(360|\\bavm\\b|\\.avm|panoram|surround|bird.?s?.?eye|around.?view|全景).*");
    }

    /** The stereo's 360 camera app: the one chosen in Settings, else the first that looks like one. */
    static String camApp(Context c, Prefs p) {
        String chosen = p.app("cam360");
        if (chosen != null) return chosen;
        try {
            PackageManager pm = c.getPackageManager();
            List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            for (ResolveInfo r : l) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                if (looks360(pkg, String.valueOf(r.loadLabel(pm)))) return pkg;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Open the stereo's 360 camera app; false when there isn't one. */
    static boolean openCam(Context c, Prefs p) {
        String pkg = camApp(c, p);
        Intent i = pkg == null ? null : Compat.launch(c.getPackageManager(), pkg);
        if (i == null) return false;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The car, turned by touch, easing towards the view a button asks for. */
    static final class CarView extends View {
        final Car3D painter = new Car3D();
        final Car3D.Cam cam = new Car3D.Cam();
        final Prefs prefs;
        List<Car3D.Face> faces;
        double[] dims;
        String shape = "";
        boolean lightsOn;
        private double goYaw = Double.NaN, goPitch = Double.NaN;
        private float lx, ly;
        private long touchedAt;

        CarView(Context c, Prefs p) {
            super(c);
            prefs = p;
            setBackgroundColor(0xFF07080A);
        }

        void view(double yaw, double pitch) {
            // the short way round
            double d = Math.IEEEremainder(yaw - cam.yaw, 2 * Math.PI);
            goYaw = cam.yaw + d;
            goPitch = pitch;
            touchedAt = System.currentTimeMillis();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            String s = Car3D.shape(prefs);
            if (!s.equals(shape)) {
                shape = s;
                faces = Car3D.build(s);
                dims = Car3D.dims(s);
            }
            long now = System.currentTimeMillis();
            if (!Double.isNaN(goYaw)) {
                cam.yaw += (goYaw - cam.yaw) * .18;
                cam.pitch += (goPitch - cam.pitch) * .18;
                if (Math.abs(goYaw - cam.yaw) < .002 && Math.abs(goPitch - cam.pitch) < .002) goYaw = goPitch = Double.NaN;
            } else if (now - touchedAt > 4000) {
                cam.yaw += .006;   // turns slowly by itself when left alone
            }
            painter.draw(c, cam, faces, dims, Car3D.colour(prefs), Car3D.plate(prefs), Features.on(prefs, "cab"), lightsOn);
            if (isShown()) postInvalidateDelayed(33);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            touchedAt = System.currentTimeMillis();
            goYaw = goPitch = Double.NaN;
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                cam.yaw -= (e.getX() - lx) * .010;
                cam.pitch = Math.max(.10, Math.min(1.53, cam.pitch + (e.getY() - ly) * .006));
                invalidate();
            }
            lx = e.getX();
            ly = e.getY();
            return true;
        }
    }

    static void show(final HomeActivity a) {
        final Prefs p = a.prefs;
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(a);
        final CarView car = new CarView(a, p);
        car.lightsOn = a.hub.lightsOn();
        root.addView(car, new FrameLayout.LayoutParams(-1, -1));

        TextView title = Ui.label(a, Hindi.f("360 VIEW  ·  %1$s", Car.name(p).isEmpty() ? Hindi.t("YOUR CAR") : Car.name(p).toUpperCase(Locale.getDefault())));
        title.setTextSize(0, 14 * Ui.k);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        tl.leftMargin = tl.topMargin = Ui.px(20);
        root.addView(title, tl);

        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER);
        final String cam = camApp(a, p);
        String[] names = {"Top", "Front", "Left", "Right", "Rear", "Colour", "Shape", "Plate", cam != null ? "360 camera" : null, "Close"};
        for (final String n : names) {
            if (n == null) continue;
            TextView b = PhoneCard.pill(a, Hindi.t(n), "Close".equals(n) ? 0xFF7A2328 : "360 camera".equals(n) ? 0xFF1F6F45 : 0);
            b.setTextSize(0, 15 * Ui.k);
            b.setPadding(Ui.px(16), 0, Ui.px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.px(46));
            lp.leftMargin = lp.rightMargin = Ui.px(5);
            bar.addView(b, lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (n) {
                        case "Top": car.view(car.cam.yaw, 1.53); break;
                        case "Front": car.view(0, .30); break;
                        case "Rear": car.view(Math.PI, .30); break;
                        case "Left": car.view(-Math.PI / 2, .22); break;
                        case "Right": car.view(Math.PI / 2, .22); break;
                        case "Colour": colour(a, car); break;
                        case "Shape": shape(a, car); break;
                        case "Plate": plate(a, car); break;
                        case "360 camera":
                            d.dismiss();
                            openCam(a, p);
                            break;
                        default: d.dismiss();
                    }
                }
            });
        }
        android.widget.HorizontalScrollView sc = new android.widget.HorizontalScrollView(a);
        sc.setHorizontalScrollBarEnabled(false);
        sc.addView(bar);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.px(18);
        root.addView(sc, bl);
        d.setContentView(root);
        d.show();
    }

    private static void colour(HomeActivity a, final CarView car) {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Car colour"))
                .setItems(Cab.tr(Car3D.COLOUR_NAMES), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int w) {
                        car.prefs.put("carColour", String.valueOf(Car3D.COLOURS[w]));
                        car.invalidate();
                    }
                }).show();
    }

    private static void shape(HomeActivity a, final CarView car) {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Car shape"))
                .setItems(Cab.tr(Car3D.SHAPE_NAMES), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int w) {
                        car.prefs.put("carShape", Car3D.SHAPES[w]);
                        car.invalidate();
                    }
                }).show();
    }

    private static void plate(HomeActivity a, final CarView car) {
        final android.widget.EditText e = new android.widget.EditText(a);
        e.setSingleLine(true);
        e.setText(Car3D.plate(car.prefs));
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Number plate (e.g. DL 3C AB 1234)")).setView(e)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int w) {
                        car.prefs.put("carPlate", e.getText().toString().trim().toUpperCase(Locale.US));
                        car.invalidate();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private View360() {
    }
}
