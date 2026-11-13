package com.homingzone.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HomingZone home screen: wallpaper, a small clock + profile pill in the top-right corner,
 * and one translucent glass dock holding the installed apps in a single horizontal row.
 *
 * All sizes are in dp. Reference screen: 1280x720 at 213 dpi, which is about 961x541 dp.
 */
public class LauncherActivity extends Activity {

    // ---- Wallpaper -------------------------------------------------------------------------
    // To use another wallpaper, drop a 1280x720 JPG named wallpaper_default.jpg into
    // app/src/main/res/drawable-nodpi/ (replacing the placeholder). Or add a new file there and
    // point this constant at it.
    private static final int WALLPAPER = R.drawable.wallpaper_default;

    // ---- Layout (dp) -----------------------------------------------------------------------
    private static final int SAFE_H = 48;        // keep content away from the TV's left/right edges
    private static final int SAFE_V = 27;        // ... and top/bottom edges (Android TV overscan guide)
    private static final int TILE_W = 144;       // 16:9, the same shape as an Android TV app banner
    private static final int TILE_H = 81;
    private static final int TILE_RADIUS = 14;
    private static final int TILE_GAP = 22;      // space between two neighbouring tiles
    private static final int DOCK_H = 128;
    private static final int DOCK_RADIUS = 30;
    private static final int DOCK_PAD = 22;      // inner left/right padding of the dock

    // ---- Focus look ------------------------------------------------------------------------
    // Neighbouring tiles would overlap at about 1 + 2 * TILE_GAP / TILE_W (= 1.3), so 1.15 is safe.
    private static final float FOCUS_SCALE = 1.15f;
    private static final int FOCUS_LIFT = 10;         // shadow height of the focused tile (dp)
    private static final int FOCUS_RING = 2;          // thin ring around the focused tile; 0 = off
    private static final int FOCUS_MS = 150;

    // Tiles whose artwork is loaded per UI-thread turn, so the screen never freezes on startup.
    private static final int ART_BATCH = 3;

    private static final int BLACK = Color.rgb(6, 8, 13);
    private static final int WHITE = Color.WHITE;
    private static final int LABEL = Color.argb(165, 255, 255, 255);
    private static final int GLASS_FILL = Color.argb(95, 124, 130, 144);

    /** One app in the dock. */
    private static final class AppItem {
        final ResolveInfo info;
        final String packageName;
        final String label;
        View entry;             // the dock's direct child
        FrameLayout tile;       // the focusable artwork
        Drawable ring;
        boolean artLoaded;

        AppItem(ResolveInfo info, String label) {
            this.info = info;
            this.packageName = info.activityInfo.packageName;
            this.label = label;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator decelerate = new DecelerateInterpolator();
    private final ViewOutlineProvider tileOutline = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(TILE_RADIUS));
        }
    };

    private FrameLayout root;
    private HorizontalScrollView appScroll;
    private LinearLayout dock;
    private TextView clock;
    private Drawable.ConstantState tileBackground;
    private Drawable.ConstantState ringShape;

    private List<AppItem> items = new ArrayList<>();
    private AppItem lastFocused;       // remembered so DOWN from the top bar returns to the dock
    private AppItem pendingScroll;     // scroll to this tile once the dock has been laid out
    private int nextArt;               // index of the next tile whose artwork is still to load
    private boolean appsDirty = true;  // true when the installed-app list may have changed
    private boolean resumed;
    private java.text.DateFormat timeFormat;

    // Refresh the dock shortly after a package change (several broadcasts often arrive together).
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() { refreshApps(); }
    };

    private final Runnable artworkLoader = new Runnable() {
        @Override public void run() {
            for (int i = 0; i < ART_BATCH && nextArt < items.size(); i++) {
                loadArtwork(items.get(nextArt++));
            }
            if (nextArt < items.size()) handler.post(this);
        }
    };

    // Installed / removed / updated apps. Registered only while this screen exists, no polling.
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            appsDirty = true;
            if (resumed) {
                handler.removeCallbacks(refreshTask);
                handler.postDelayed(refreshTask, 600);
            }
        }
    };

    // The system sends ACTION_TIME_TICK once a minute, so the clock needs no timer of its own.
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            timeFormat = null;   // re-read 12/24 h and time zone
            updateClock();
        }
    };

    // =========================================================================================
    // Lifecycle
    // =========================================================================================

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        buildUi();

        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);

        refreshApps();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        // Coming back from an app: keep the existing tiles (and the focused one) untouched
        // unless an install/uninstall happened while we were away.
        if (appsDirty) refreshApps();
        else if (root.findFocus() == null) focusTile(lastFocused != null ? lastFocused : first());

        IntentFilter time = new IntentFilter();
        time.addAction(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockReceiver, time);
        timeFormat = null;
        updateClock();
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(refreshTask);
        try { unregisterReceiver(clockReceiver); } catch (IllegalArgumentException ignored) { }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(packageReceiver); } catch (IllegalArgumentException ignored) { }
    }

    @Override public void onBackPressed() {
        // A launcher has nowhere to go back to.
    }

    // =========================================================================================
    // Screen construction
    // =========================================================================================

    private void buildUi() {
        getWindow().setBackgroundDrawable(new ColorDrawable(BLACK));
        root = new FrameLayout(this);
        // Shown behind the wallpaper, and kept if the wallpaper cannot be loaded.
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(20, 32, 48), BLACK}));

        addWallpaper();
        addShades();
        // The dock is added before the top bar so that, if nothing has focus yet, Android's
        // default focus search lands on the first app tile rather than the settings button.
        addDock();
        addTopBar();
        setContentView(root);
    }

    private void addWallpaper() {
        ImageView wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            wallpaper.setImageResource(WALLPAPER);
        } catch (OutOfMemoryError | RuntimeException e) {
            return; // keep the plain gradient
        }
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Dark overlays that keep the clock and dock readable on any wallpaper. */
    private void addShades() {
        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(40, 0, 0, 0));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(120, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(120), Gravity.TOP));

        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(200, 0, 0, 0)}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(280), Gravity.BOTTOM));
    }

    private void addDock() {
        // Shared drawable recipes, so every tile does not build its own from scratch.
        tileBackground = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(48, 50, 58), Color.rgb(24, 25, 31)}).getConstantState();
        if (FOCUS_RING > 0) {
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(Color.TRANSPARENT);
            ring.setCornerRadius(dp(TILE_RADIUS));
            ring.setStroke(dp(FOCUS_RING), Color.argb(215, 255, 255, 255));
            ringShape = ring.getConstantState();
        }

        // Translucent glass strip. (Real background blur is not available on Android 11 TVs
        // without a heavy cost, so this is a layered translucent gradient.)
        FrameLayout shell = new FrameLayout(this);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{GLASS_FILL, Color.argb(95, 58, 62, 74)});
        glass.setCornerRadius(dp(DOCK_RADIUS));
        shell.setBackground(glass);
        // The focused tile grows and casts a shadow, but both stay inside the dock's padding, so
        // the dock can safely clip everything to its rounded shape. That hides tiles that have
        // been scrolled past either end instead of letting them spill across the screen.
        final int dockRadius = dp(DOCK_RADIUS);
        shell.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dockRadius);
            }
        });
        shell.setClipToOutline(true);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        appScroll = new HorizontalScrollView(this) {
            // We scroll to the focused tile ourselves (see scrollToItem), so switch off the
            // built-in "scroll just enough to show the focused child" behaviour.
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                return 0;
            }
        };
        appScroll.setHorizontalScrollBarEnabled(false);
        appScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        appScroll.setFillViewport(false);
        appScroll.setClipChildren(false);
        appScroll.setClipToPadding(false);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dock.setPadding(dp(DOCK_PAD), 0, dp(DOCK_PAD), 0);
        dock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (pendingScroll != null) {
                AppItem target = pendingScroll;
                pendingScroll = null;
                scrollToItem(target, false);
            }
        });
        // Height = scroll height, so tiles sit vertically centred with room to scale up.
        appScroll.addView(dock, new HorizontalScrollView.LayoutParams(-2, -1));
        shell.addView(appScroll, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(DOCK_H),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.leftMargin = dp(SAFE_H);
        lp.rightMargin = dp(SAFE_H);
        lp.bottomMargin = dp(SAFE_V + 8);
        root.addView(shell, lp);
    }

    /** Settings button + a pill with the clock and a decorative profile icon, top-right. */
    private void addTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // Settings button (focusable).
        final ImageView settings = new ImageView(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settings.setPadding(dp(9), dp(9), dp(9), dp(9));
        settings.setContentDescription("Settings");
        settings.setFocusable(true);
        settings.setFocusableInTouchMode(true);
        styleSettings(settings, false);
        settings.setOnFocusChangeListener((v, focused) -> {
            styleSettings(settings, focused);
            settings.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f)
                    .setDuration(FOCUS_MS).setInterpolator(decelerate).start();
        });
        settings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Settings can't be opened", Toast.LENGTH_SHORT).show();
            }
        });
        // DOWN goes back to the app that was selected, not to whichever tile is nearest.
        settings.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN
                    && lastFocused != null && lastFocused.tile.isAttachedToWindow()) {
                lastFocused.tile.requestFocus();
                return true;
            }
            return false;
        });
        bar.addView(settings, new LinearLayout.LayoutParams(dp(38), dp(38)));

        // Clock + profile pill (not focusable).
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), dp(5), dp(5), dp(5));
        pill.setBackground(roundRect(GLASS_FILL, dp(50)));

        clock = text("", 15, Color.argb(235, 255, 255, 255), true);
        clock.setSingleLine(true);
        pill.addView(clock, new LinearLayout.LayoutParams(-2, -2));

        ImageView profile = new ImageView(this);
        profile.setImageResource(R.drawable.ic_profile);
        profile.setScaleType(ImageView.ScaleType.FIT_CENTER);
        profile.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable avatar = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(128, 142, 178), Color.rgb(66, 76, 110)});
        avatar.setShape(GradientDrawable.OVAL);
        profile.setBackground(avatar);
        profile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams profileLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        profileLp.leftMargin = dp(12);
        pill.addView(profile, profileLp);

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
        pillLp.leftMargin = dp(10);
        bar.addView(pill, pillLp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.topMargin = dp(SAFE_V);
        lp.rightMargin = dp(SAFE_H);
        root.addView(bar, lp);
    }

    private void styleSettings(ImageView button, boolean focused) {
        button.setBackground(roundRect(focused ? Color.argb(240, 245, 245, 248) : GLASS_FILL, dp(50)));
        if (focused) button.setColorFilter(Color.rgb(20, 22, 28));
        else button.clearColorFilter();
    }

    private void updateClock() {
        if (clock == null) return;
        if (timeFormat == null) timeFormat = DateFormat.getTimeFormat(this);
        clock.setText(timeFormat.format(new Date()));
    }

    // =========================================================================================
    // Apps
    // =========================================================================================

    /** Rebuilds the dock from the installed apps, keeping the user's place if possible. */
    private void refreshApps() {
        appsDirty = false;
        handler.removeCallbacks(refreshTask);
        handler.removeCallbacks(artworkLoader);

        boolean restoreFocus = dock.hasFocus() || root.findFocus() == null;
        String keepPackage = lastFocused != null ? lastFocused.packageName : null;

        items = queryApps();
        dock.removeAllViews();
        lastFocused = null;
        pendingScroll = null;
        nextArt = 0;

        AppItem target = null;
        for (AppItem item : items) {
            dock.addView(buildEntry(item));
            if (keepPackage != null && keepPackage.equals(item.packageName)) target = item;
        }
        if (items.isEmpty()) {
            TextView empty = text("No apps found", 15, LABEL, true);
            empty.setGravity(Gravity.CENTER);
            dock.addView(empty, new LinearLayout.LayoutParams(-2, -2));
        }
        if (target == null) target = first();
        if (restoreFocus) focusTile(target);
        if (!items.isEmpty()) handler.post(artworkLoader);
    }

    private AppItem first() {
        return items.isEmpty() ? null : items.get(0);
    }

    private void focusTile(AppItem item) {
        if (item != null && item.tile != null) item.tile.requestFocus();
    }

    /** One entry per installed app. TV (leanback) entries win, because they carry the banner. */
    private List<AppItem> queryApps() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPackage);
        collect(pm, Intent.CATEGORY_LAUNCHER, byPackage);

        List<AppItem> result = new ArrayList<>();
        for (ResolveInfo info : byPackage.values()) {
            if (isUsable(info)) result.add(new AppItem(info, String.valueOf(info.loadLabel(pm))));
        }
        // Labels are loaded once above, so sorting does not touch PackageManager again.
        Collections.sort(result, new Comparator<AppItem>() {
            @Override public int compare(AppItem a, AppItem b) {
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });
        return result;
    }

    private void collect(PackageManager pm, String category, Map<String, ResolveInfo> out) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(intent, 0);
        if (found == null) return;
        for (ResolveInfo info : found) {
            if (info == null || info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (!out.containsKey(pkg)) out.put(pkg, info);
        }
    }

    private boolean isUsable(ResolveInfo info) {
        return info.activityInfo.applicationInfo != null
                && info.activityInfo.enabled
                && info.activityInfo.applicationInfo.enabled
                && !getPackageName().equals(info.activityInfo.packageName);
    }

    // =========================================================================================
    // Dock tiles
    // =========================================================================================

    /** Builds one focusable tile (artwork is filled in later by the artwork loader). */
    private View buildEntry(final AppItem item) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(tileBackground.newDrawable());
        tile.setOutlineProvider(tileOutline);
        tile.setClipToOutline(true);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        tileLp.leftMargin = dp(TILE_GAP / 2);
        tileLp.rightMargin = dp(TILE_GAP / 2);
        tile.setLayoutParams(tileLp);

        item.entry = tile;
        item.tile = tile;
        item.ring = ringShape != null ? ringShape.newDrawable() : null;

        tile.setOnFocusChangeListener((v, focused) -> onTileFocus(item, focused));
        tile.setOnClickListener(v -> launch(item));
        return tile;
    }

    /** Puts the app's TV banner on its tile, or its icon on a dark tile if there is no banner. */
    private void loadArtwork(AppItem item) {
        if (item.artLoaded) return;
        item.artLoaded = true;
        PackageManager pm = getPackageManager();
        try {
            Drawable banner = item.info.activityInfo.loadBanner(pm);
            if (banner == null) banner = item.info.activityInfo.applicationInfo.loadBanner(pm);
            ImageView art = new ImageView(this);
            if (banner != null) {
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageDrawable(banner);
                item.tile.addView(art, new FrameLayout.LayoutParams(-1, -1));
            } else {
                art.setScaleType(ImageView.ScaleType.FIT_CENTER);
                art.setImageDrawable(item.info.loadIcon(pm));
                item.tile.addView(art, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
            }
        } catch (RuntimeException ignored) {
            // A broken icon must never crash the launcher; the tile keeps its dark background.
        }
    }

    private void onTileFocus(AppItem item, boolean focused) {
        float scale = focused ? FOCUS_SCALE : 1f;
        item.tile.animate().scaleX(scale).scaleY(scale)
                .translationZ(focused ? dp(FOCUS_LIFT) : 0f)
                .setDuration(FOCUS_MS).setInterpolator(decelerate).start();
        item.tile.setForeground(focused ? item.ring : null);
        if (focused) {
            lastFocused = item;
            scrollToItem(item, true);
        }
    }

    /** Keeps the focused tile in the middle of the dock (clamped at both ends). */
    private void scrollToItem(AppItem item, boolean animate) {
        View entry = item.entry;
        if (!entry.isLaidOut() || entry.getWidth() == 0) {
            pendingScroll = item;   // the dock is being rebuilt: do it right after layout
            return;
        }
        int viewport = appScroll.getWidth();
        int content = dock.getWidth();
        if (viewport <= 0 || content <= viewport) return;
        int target = entry.getLeft() + entry.getWidth() / 2 - viewport / 2;
        target = Math.max(0, Math.min(target, content - viewport));
        if (animate) appScroll.smoothScrollTo(target, 0);
        else appScroll.scrollTo(target, 0);
    }

    private void launch(AppItem item) {
        PackageManager pm = getPackageManager();
        // Prefer the TV screen of an app that has both a TV and a phone entry.
        Intent intent = pm.getLeanbackLaunchIntentForPackage(item.packageName);
        if (intent == null) intent = pm.getLaunchIntentForPackage(item.packageName);
        if (intent == null) {
            Toast.makeText(this, item.label + " can't be opened", Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, item.label + " can't be opened", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private TextView text(String value, float sizeSp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
