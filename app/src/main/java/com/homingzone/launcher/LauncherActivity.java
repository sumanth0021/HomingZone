package com.homingzone.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private TextView heroTitle, heroSubtitle, clock;
    private ImageView heroIcon;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(new SimpleDateFormat("h:mm a  ·  EEE, d MMM", Locale.getDefault()).format(new Date()));
            handler.postDelayed(this, 30000);
        }
    };
    private List<ResolveInfo> apps = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
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

    @Override public void onBackPressed() { }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(39, 62, 82), Color.rgb(35, 43, 59), Color.rgb(10, 13, 20), black}));
        // Soft cinematic light behind the selected app.
        View glow = new View(this);
        GradientDrawable glowBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(95, 94, 126, 145), Color.argb(35, 71, 87, 111), Color.TRANSPARENT});
        glowBg.setCornerRadius(dp(36));
        glow.setBackground(glowBg);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(-1, 0, Gravity.TOP);
        glowLp.height = (int)(getResources().getDisplayMetrics().heightPixels * .76f);
        root.addView(glow, glowLp);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(42), dp(24), dp(42), 0);
        TextView brand = text("HomingZone", 19, Color.argb(225,255,255,255), true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        clock = text("", 14, muted, true);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(16), dp(9), dp(16), dp(9));
        clock.setBackground(roundRect(Color.argb(75,255,255,255), dp(22)));
        top.addView(clock);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, dp(72), Gravity.TOP);
        root.addView(top, topLp);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(64), 0, dp(50), dp(110));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        heroTitle = text("Welcome home", 42, white, true);
        heroTitle.setMaxLines(1);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heroTitle);
        heroSubtitle = text("Choose something to watch, play or enjoy.", 17, muted, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(12);
        copy.addView(heroSubtitle, subLp);
        hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        heroIcon = new ImageView(this);
        heroIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroIcon.setAlpha(.92f);
        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setBackground(roundRect(Color.argb(25,255,255,255), dp(34)));
        iconFrame.addView(heroIcon, new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER));
        LinearLayout.LayoutParams heroIconLp = new LinearLayout.LayoutParams(dp(220), dp(190));
        hero.addView(iconFrame, heroIconLp);
        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(-1, 0, Gravity.TOP);
        heroLp.topMargin = dp(68);
        heroLp.bottomMargin = dp(115);
        root.addView(hero, heroLp);

        // One centered, translucent Apple-TV-inspired dock.
        FrameLayout dockShell = new FrameLayout(this);
        dockShell.setPadding(dp(12), dp(12), dp(12), dp(12));
        dockShell.setBackground(roundRect(Color.argb(150, 12, 14, 20), dp(28)));
        dockShell.setElevation(dp(18));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(5), dp(4), dp(5), dp(4));
        scroll.addView(dock, new HorizontalScrollView.LayoutParams(-2, -2));
        dockShell.addView(scroll, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(-1, dp(132), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.leftMargin = dp(38);
        dockLp.rightMargin = dp(38);
        dockLp.bottomMargin = dp(30);
        root.addView(dockShell, dockLp);

        TextView hint = text("Use the remote to move  ·  OK to open", 12, Color.argb(125,255,255,255), false);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(-1, dp(22), Gravity.BOTTOM);
        hintLp.bottomMargin = dp(8);
        root.addView(hint, hintLp);
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
        if (first != null) select(first);
        else {
            heroTitle.setText("HomingZone");
            heroSubtitle.setText("No apps found. Install an app to see it here.");
            heroIcon.setImageDrawable(null);
        }
        if (requestFocus && dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
    }

    private void addUnique(List<ResolveInfo> target, List<ResolveInfo> incoming) {
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (ResolveInfo r : target) unique.put(r.activityInfo.packageName + "/" + r.activityInfo.name, r);
        for (ResolveInfo r : incoming) unique.put(r.activityInfo.packageName + "/" + r.activityInfo.name, r);
        target.clear();
        target.addAll(unique.values());
    }

    private View appTile(final ResolveInfo info) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setPadding(dp(9), dp(7), dp(9), dp(5));
        tile.setBackground(roundRect(Color.argb(12,255,255,255), dp(19)));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(66), dp(66)));
        TextView name = text(label(info), 11, Color.argb(210,255,255,255), true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(dp(92), dp(19));
        nameLp.topMargin = dp(3);
        tile.addView(name, nameLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(106));
        lp.leftMargin = dp(5);
        lp.rightMargin = dp(5);
        tile.setLayoutParams(lp);
        tile.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.16f : 1f).scaleY(focused ? 1.16f : 1f)
                    .alpha(focused ? 1f : .82f).setDuration(160).start();
            v.setBackground(roundRect(focused ? Color.argb(65,255,255,255) : Color.argb(12,255,255,255),
                    dp(19)));
            if (focused) select(info);
        });
        tile.setOnClickListener(v -> launch(info));
        return tile;
    }

    private void select(ResolveInfo info) {
        heroTitle.setText(label(info));
        heroSubtitle.setText("Ready to open  ·  " + info.activityInfo.packageName);
        heroIcon.setImageDrawable(info.loadIcon(getPackageManager()));
    }

    private void launch(ResolveInfo info) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(info.activityInfo.packageName);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            intent.setPackage(info.activityInfo.packageName);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(intent); } catch (Exception ignored) { }
        }
    }

    private String label(ResolveInfo info) {
        return String.valueOf(info.loadLabel(getPackageManager()));
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }
}
