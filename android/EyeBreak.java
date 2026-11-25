package io.github.aganzefelicite.eyebreak;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

// 20-20-20 for the phone: after 20 min of screen-on time, a 1-minute full-screen break overlay
// with a random line from messages.txt (falls back to a notification until "Display over other
// apps" is allowed). Plain Java, no AndroidX; built by build.sh.
public class EyeBreak {
    static final long WORK = 20 * 60_000, REST = 60_000;

    public static class Main extends Activity {
        Button allow;

        @Override protected void onCreate(Bundle b) {
            super.onCreate(b);
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
            // system "Let app always run in background?" prompt (= Battery: Unrestricted)
            if (!getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()))
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            startForegroundService(new Intent(this, Svc.class));
            TextView t = new TextView(this);
            t.setText("EyeBreak is running.\n\nAfter every 20 minutes of screen time a 1-minute break covers the screen: look at something 20 feet away.\n\nIt starts by itself when the phone boots.");
            t.setTextSize(18);
            allow = new Button(this);
            allow.setText("Allow full-screen breaks");
            allow.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
            Button test = new Button(this);
            test.setText("Test a break now");
            test.setOnClickListener(v -> startService(new Intent(this, Svc.class).setAction("now")));
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(48, 160, 48, 48);
            l.addView(t);
            l.addView(allow);
            l.addView(test);
            setContentView(l);
        }

        @Override protected void onResume() {
            super.onResume();
            allow.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        }
    }

    public static class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            c.startForegroundService(new Intent(c, Svc.class));
        }
    }

    public static class Svc extends Service {
        final Handler h = new Handler(Looper.getMainLooper());
        final List<String> messages = new ArrayList<>();
        final Random rnd = new Random();
        long left = WORK, since, offAt;
        boolean on;
        String last = "";

        final Runnable fire = () -> { showBreak(); left = WORK + REST; since = now(); h.postDelayed(this.fire, left); };

        // only screen-on time counts; screen off for a full break's length resets the timer
        final BroadcastReceiver screen = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { setScreen(Intent.ACTION_SCREEN_ON.equals(i.getAction())); }
        };

        static long now() { return SystemClock.elapsedRealtime(); }

        void setScreen(boolean nowOn) {
            if (nowOn == on) return;
            on = nowOn;
            if (on) {
                if (now() - offAt >= REST) left = WORK;
                since = now();
                h.postDelayed(fire, left);
            } else {
                h.removeCallbacks(fire);
                left = Math.max(0, left - (now() - since));
                offAt = now();
            }
        }

        @Override public void onCreate() {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("status", "Running", NotificationManager.IMPORTANCE_MIN));
            nm.createNotificationChannel(new NotificationChannel("break", "Eye breaks", NotificationManager.IMPORTANCE_HIGH));
            Notification status = new Notification.Builder(this, "status")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("EyeBreak is on")
                .setContentText("1-minute eye break every 20 min of screen time")
                .setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, status, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, status);

            try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("messages.txt"), "UTF-8"))) {
                for (String s; (s = r.readLine()) != null; ) {
                    s = s.trim();
                    if (!s.isEmpty() && !s.startsWith("#")) messages.add(s);
                }
            } catch (IOException ignored) { }
            if (messages.isEmpty()) messages.add("👀 Blink slowly a few times.");

            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screen, f);  // system broadcasts only, so no export flag needed
            setScreen(getSystemService(PowerManager.class).isInteractive());
        }

        @Override public int onStartCommand(Intent i, int flags, int id) {
            if (i != null && "now".equals(i.getAction())) { h.removeCallbacks(fire); fire.run(); }
            return START_STICKY;
        }

        View overlay;
        CountDownTimer timer;

        void showBreak() {
            String m;
            do m = messages.get(rnd.nextInt(messages.size())); while (messages.size() > 1 && m.equals(last));
            last = m;
            if (Settings.canDrawOverlays(this)) showOverlay(m); else notifyBreak(m);
        }

        void showOverlay(String m) {
            if (overlay != null) return;
            TextView count = text(String.valueOf(REST / 1000), 96);
            LinearLayout v = new LinearLayout(this);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setGravity(Gravity.CENTER);
            v.setPadding(64, 64, 64, 64);
            v.setBackgroundColor(0xEB000000);
            v.addView(text("Look at something 20 feet away", 26));
            v.addView(count);
            v.addView(text(m, 20));
            // full-screen, touch-blocking layer above every app; not focusable so the keyboard isn't hijacked
            getSystemService(WindowManager.class).addView(overlay = v, new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT));
            timer = new CountDownTimer(REST, 1000) {
                @Override public void onTick(long ms) { count.setText(String.valueOf((ms + 999) / 1000)); }
                @Override public void onFinish() {
                    closeOverlay();  // buzz: your eyes are off the screen
                    getSystemService(Vibrator.class).vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }.start();
        }

        void closeOverlay() {
            if (overlay != null) getSystemService(WindowManager.class).removeView(overlay);
            overlay = null;
        }

        TextView text(String s, int sp) {
            TextView t = new TextView(this);
            t.setText(s);
            t.setTextSize(sp);
            t.setTextColor(Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, 24, 0, 24);
            return t;
        }

        void notifyBreak(String m) {
            getSystemService(NotificationManager.class).notify(2, new Notification.Builder(this, "break")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Eye break: look 20 feet away")
                .setContentText(m)
                .setStyle(new Notification.BigTextStyle().bigText(m))
                .setUsesChronometer(true).setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + REST)
                .setTimeoutAfter(REST)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build());
        }

        @Override public void onDestroy() {
            unregisterReceiver(screen);
            h.removeCallbacksAndMessages(null);
            if (timer != null) timer.cancel();
            closeOverlay();
        }
        @Override public IBinder onBind(Intent i) { return null; }
    }
}
