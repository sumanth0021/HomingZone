package com.homingzone.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LauncherActivity extends Activity {
    private final int white = Color.WHITE;
    private final int muted = Color.argb(190, 255, 255, 255);
    private final int black = Color.rgb(8, 10, 15);
    private FrameLayout root;
    private LinearLayout dock;
    private HorizontalScrollView appScroll;
    private TextView clock;
    private View settingsPanel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clock != null) {
                clock.setText(new SimpleDateFormat("h:mm a  ·  EEE, d MMM",
                        Locale.getDefault()).format(new Date()));
            }
            handler.postDelayed(this, 30000);
        }
    };
    private List<ResolveInfo> apps = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        buildUi();
        refreshApps(true);
        handler.post(clockTick);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshApps(false);
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
    }

    @Override public void onBackPressed() {
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            settingsPanel.setVisibility(View.GONE);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(39, 62, 82), Color.rgb(35, 43, 59),
                        Color.rgb(10, 13, 20), black}));

        View glow = new View(this);
        GradientDrawable glowBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(95, 94, 126, 145), Color.argb(35, 71, 87, 111),
                        Color.TRANSPARENT});
        glowBg.setCornerRadius(dp(36));
        glow.setBackground(glowBg);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(-1, 0, Gravity.TOP);
        glowLp.height = (int) (getResources().getDisplayMetrics().heightPixels * .76f);
        root.addView(glow, glowLp);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(42), dp(24), dp(42), 0);

        clock = text("", 14, muted, true);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(16), dp(9), dp(16), dp(9));
        clock.setBackground(roundRect(Color.argb(75, 255, 255, 255), dp(22)));
        top.addView(clock);

        TextView profile = control("●", 22);
        profile.setBackground(profileBackground());
        top.addView(profile, controlParams(dp(10)));

        TextView settings = control("⚙", 23);
        settings.setBackground(roundRect(Color.argb(75, 255, 255, 255), dp(21)));
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings, controlParams(0));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(72), Gravity.TOP));

        appScroll = new HorizontalScrollView(this);
        appScroll.setHorizontalScrollBarEnabled(false);
        appScroll.setClipToPadding(false);
        appScroll.setFillViewport(false);
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(42), dp(24), dp(42), dp(24));
        appScroll.addView(dock, new HorizontalScrollView.LayoutParams(-2, -2));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(-1, dp(150),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.bottomMargin = dp(38);
        root.addView(appScroll, dockLp);
        setContentView(root);
    }

    private void refreshApps(boolean requestFocus) {
        List<ResolveInfo> found = new ArrayList<>();
        PackageManager pm = getPackageManager();

        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        addUnique(found, pm.queryIntentActivities(launcher, 0));

        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory("android.intent.category.LEANBACK_LAUNCHER");
        addUnique(found, pm.queryIntentActivities(leanback, 0));

        Collections.sort(found, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.CASE_INSENSITIVE_ORDER.compare(label(a), label(b));
            }
        });
        apps = found;
        dock.removeAllViews();
        ResolveInfo first = null;
        for (ResolveInfo info : apps) {
            if (getPackageName().equals(info.activityInfo.packageName)) continue;
            if (first == null) first = info;
            dock.addView(appTile(info));
        }
        if (requestFocus && dock.getChildCount() > 0) {
            dock.getChildAt(0).requestFocus();
        }
    }

    private void addUnique(List<ResolveInfo> target, List<ResolveInfo> incoming) {
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (ResolveInfo info : target) {
            unique.put(info.activityInfo.packageName + "/" + info.activityInfo.name, info);
        }
        for (ResolveInfo info : incoming) {
            unique.put(info.activityInfo.packageName + "/" + info.activityInfo.name, info);
        }
        target.clear();
        target.addAll(unique.values());
    }

    private View appTile(final ResolveInfo info) {
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setPadding(dp(11), dp(11), dp(11), dp(11));
        tile.setBackground(roundRect(Color.argb(38, 20, 23, 30), dp(20)));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(78), dp(78)));

        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        tileParams.leftMargin = dp(8);
        tileParams.rightMargin = dp(8);
        tile.setLayoutParams(tileParams);
        tile.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.10f : 1f)
                    .scaleY(focused ? 1.10f : 1f)
                    .alpha(focused ? 1f : .86f)
                    .setDuration(160).start();
            view.setElevation(focused ? dp(12) : 0);
            view.setBackground(roundRect(
                    focused ? Color.argb(78, 255, 255, 255) : Color.argb(38, 20, 23, 30),
                    dp(20)));
            if (focused) {
                appScroll.post(() -> appScroll.requestChildRectangleOnScreen(view,
                        new Rect(0, 0, view.getWidth(), view.getHeight()), true));
            }
        });
        tile.setOnClickListener(view -> launch(info));
        return tile;
    }

    private TextView control(String value, int size) {
        TextView control = text(value, size, white, true);
        control.setGravity(Gravity.CENTER);
        control.setFocusable(true);
        control.setFocusableInTouchMode(true);
        control.setContentDescription(value);
        return control;
    }

    private LinearLayout.LayoutParams controlParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(42), dp(42));
        params.leftMargin = leftMargin;
        return params;
    }

    private GradientDrawable profileBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(64, 170, 239), Color.rgb(220, 76, 161),
                        Color.rgb(250, 190, 71)});
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    private void showSettings() {
        if (settingsPanel != null) {
            settingsPanel.setVisibility(View.VISIBLE);
            settingsPanel.requestFocus();
            return;
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(34), dp(28), dp(34), dp(28));
        panel.setFocusable(true);
        panel.setFocusableInTouchMode(true);
        panel.setBackground(roundRect(Color.argb(235, 16, 20, 29), dp(24)));

        panel.addView(text("Settings", 28, white, true));
        panel.addView(text("Wallpaper", 17, white, true), marginParams(0, 24, 0, 0));
        panel.addView(text("Current cinematic wallpaper • Selection coming soon",
                14, muted, false), marginParams(0, 6, 0, 0));
        panel.addView(text("Installed applications", 17, white, true),
                marginParams(0, 20, 0, 0));
        panel.addView(text(apps.size() + " launchable applications detected",
                14, muted, false), marginParams(0, 6, 0, 0));
        panel.addView(text("About HomingZone", 17, white, true),
                marginParams(0, 20, 0, 0));
        panel.addView(text("Lightweight TV launcher • Version 0.1.0",
                14, muted, false), marginParams(0, 6, 0, 0));
        panel.setOnClickListener(view -> panel.setVisibility(View.GONE));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(480), -2,
                Gravity.TOP | Gravity.RIGHT);
        panelParams.topMargin = dp(88);
        panelParams.rightMargin = dp(42);
        root.addView(panel, panelParams);
        settingsPanel = panel;
        panel.requestFocus();
    }

    private LinearLayout.LayoutParams marginParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private void launch(ResolveInfo info) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(
                info.activityInfo.packageName);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            intent.setPackage(info.activityInfo.packageName);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private String label(ResolveInfo info) {
        return String.valueOf(info.loadLabel(getPackageManager()));
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
