package com.homingzone.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
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

    // Dock geometry (Apple TV proportions: tile ~1.67:1, focused tile 1.2x, gap ~0.2 of tile width)
    private static final int TILE_W = 168;
    private static final int TILE_H = 100;
    private static final int TILE_RADIUS = 16;
    private static final int TILE_GAP = 17;       // margin on each side of a tile
    private static final float FOCUS_SCALE = 1.2f;

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
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(42), dp(24), dp(42), 0);
        clock = text("", 14, muted, true);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(16), dp(9), dp(16), dp(9));
        clock.setBackground(roundRect(Color.argb(75,255,255,255), dp(22)));
        top.addView(clock);
        View topSpacer = new View(this);
        top.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        TextView settings = text("⚙", 22, white, true);
        settings.setGravity(Gravity.CENTER);
        settings.setFocusable(true);
        settings.setFocusableInTouchMode(true);
        settings.setContentDescription("Settings");
        settings.setBackground(roundRect(Color.argb(75,255,255,255), dp(22)));
        settings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsParams.leftMargin = dp(12);
        top.addView(settings, settingsParams);
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

        // Translucent rounded strip with a hairline border, like the Apple TV dock.
        FrameLayout dockShell = new FrameLayout(this);
        GradientDrawable shellBg = roundRect(Color.argb(120, 28, 26, 24), dp(30));
        shellBg.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        dockShell.setBackground(shellBg);
        // The focused tile grows beyond its bounds, so no ancestor may clip it.
        dockShell.setClipChildren(false);
        dockShell.setClipToPadding(false);

        appScroll = new HorizontalScrollView(this);
        appScroll.setHorizontalScrollBarEnabled(false);
        appScroll.setClipToPadding(false);
        appScroll.setClipChildren(false);
        appScroll.setFillViewport(false);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dock.setPadding(dp(30), 0, dp(30), 0);
        // Child height = scroll height, so tiles are vertically centred with room to scale.
        appScroll.addView(dock, new HorizontalScrollView.LayoutParams(-2, -1));
        dockShell.addView(appScroll, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(-1, dp(148),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.leftMargin = dp(18);
        dockLp.rightMargin = dp(18);
        dockLp.bottomMargin = dp(32);
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
            if (!isUsable(info)) continue;
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
        for (ResolveInfo r : target) unique.put(r.activityInfo.packageName, r);
        for (ResolveInfo r : incoming) {
            if (!unique.containsKey(r.activityInfo.packageName)) {
                unique.put(r.activityInfo.packageName, r);
            }
        }
        target.clear();
        target.addAll(unique.values());
    }

    private boolean isUsable(ResolveInfo info) {
        return info != null
                && info.activityInfo != null
                && info.activityInfo.applicationInfo != null
                && info.activityInfo.enabled
                && info.activityInfo.applicationInfo.enabled;
    }

    /** One Apple-TV-style app entry with artwork and its installed app name. */
    private View appTile(final ResolveInfo info) {
        final PackageManager pm = getPackageManager();
        final int radius = dp(TILE_RADIUS);

        LinearLayout entry = new LinearLayout(this);
        entry.setOrientation(LinearLayout.VERTICAL);
        entry.setGravity(Gravity.CENTER_HORIZONTAL);
        entry.setFocusable(false);

        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setClipChildren(true);

        // Clip artwork to a rounded rectangle.
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        tile.setClipToOutline(true);

        // Prefer the TV banner (wide artwork); fall back to a dark tile with the app icon centred.
        Drawable banner = info.activityInfo.loadBanner(pm);
        if (banner == null && info.activityInfo.applicationInfo != null) {
            banner = info.activityInfo.applicationInfo.loadBanner(pm);
        }
        ImageView art = new ImageView(this);
        if (banner != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(banner);
            tile.addView(art, new FrameLayout.LayoutParams(-1, -1));
        } else {
            tile.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(44, 44, 48), Color.rgb(22, 22, 25)}));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageDrawable(info.loadIcon(pm));
            tile.addView(art, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
        }

        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        tile.setLayoutParams(tileLp);

        TextView name = text(label(info), 12, Color.argb(220, 255, 255, 255), false);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(dp(TILE_W), dp(24));
        nameLp.topMargin = dp(6);
        entry.addView(tile);
        entry.addView(name, nameLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H + 30));
        lp.leftMargin = dp(TILE_GAP);
        lp.rightMargin = dp(TILE_GAP);
        entry.setLayoutParams(lp);

        tile.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? FOCUS_SCALE : 1f;
            v.animate().scaleX(s).scaleY(s)
                    .translationZ(0)
                    .setDuration(160).start();
            if (focused) {
                select(info);
                // Include extra room so the scaled tile is fully visible when scrolling.
                final int pad = dp(30);
                appScroll.post(() -> appScroll.requestChildRectangleOnScreen(dock,
                        new android.graphics.Rect(v.getLeft() - pad, 0, v.getRight() + pad, dock.getHeight()), false));
            }
        });
        tile.setOnClickListener(v -> launch(info));
        return entry;
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