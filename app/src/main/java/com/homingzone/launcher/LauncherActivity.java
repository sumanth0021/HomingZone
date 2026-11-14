package com.homingzone.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.SharedPreferences;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HomingZone home screen: wallpaper, a small clock + profile pill in the top-right corner,
 * and one translucent glass dock holding the installed apps in a single horizontal row.
 *
 * All sizes are in dp. Reference screen: 1280x720 at 213 dpi, which is about 961x541 dp.
 */
public class LauncherActivity extends Activity {

    // ---- Wallpaper -------------------------------------------------------------------------
    // The default is a minimal black cinematic background. Selected images are stored as
    // persistable Storage Access Framework URIs and decoded to the display size off the UI thread.

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
    private static final String ORDER_PREFS = "app_order";
    private static final String ORDER_KEY = "packages";
    private static final String WALLPAPER_PREFS = "wallpaper";
    private static final String WALLPAPER_URI_KEY = "uri";
    private static final int PICK_SETTINGS = 43;

    private static final int BLACK = Color.rgb(6, 8, 13);
    private static final int WHITE = Color.WHITE;
    private static final int LABEL = Color.argb(165, 255, 255, 255);
    private static final int GLASS_FILL = Color.argb(95, 124, 130, 144);

    // ---- Liquid Glass dock -----------------------------------------------------------------
    // The dock shows a blurred copy of the scene behind it. The blur is baked once from a tiny
    // version of the wallpaper (about 1/8 of the screen size), so it costs almost no memory or
    // CPU. Tune the look with the numbers below.
    private static final int FROST_SCALE = 8;         // blurred backdrop is made at 1/8 screen size
    private static final int FROST_SOURCE_W = 320;    // wallpaper is decoded at about this width
    private static final int FROST_RADIUS = 3;        // blur radius at that size (about 28 px on screen)
    private static final int FROST_PASSES = 3;        // 3 box blurs together look like a soft Gaussian
    private static final int GLASS_TINT_TOP = 44;     // white veil over the blur (0-255): top ...
    private static final int GLASS_TINT_BOTTOM = 28;  // ... and bottom (keeps dark tiles visible)
    private static final int RIM_TOP = 190;           // brightness of the thin glass edge: top ...
    private static final int RIM_MIDDLE = 55;         // ... middle ...
    private static final int RIM_BOTTOM = 110;        // ... and bottom
    private static final float RIM_WIDTH_DP = 1.5f;
    private static final int SHEEN_TOP = 34;          // soft light along the top of the glass

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
    private ImageView wallpaperView;
    private volatile Bitmap wallpaperBitmap;
    private final ExecutorService wallpaperExecutor = Executors.newSingleThreadExecutor();
    private int wallpaperLoadId;
    private FrameLayout dockShell;
    private final List<View> shades = new ArrayList<>();   // dark overlays above the wallpaper
    private int frostKey;                                  // dock size the current glass was built for
    private final Runnable frostUpdater = new Runnable() {
        @Override public void run() { updateDockFrost(); }
    };
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
    private boolean reorderMode;
    private AppItem reorderItem;
    private boolean longPressActivated;
    private final Runnable enterReorderMode = new Runnable() {
        @Override public void run() {
            if (reorderItem != null && reorderItem.tile != null && reorderItem.tile.hasFocus()) {
                reorderMode = true;
                longPressActivated = true;
                Toast.makeText(LauncherActivity.this,
                        "Move with LEFT/RIGHT · OK to save · BACK to cancel",
                        Toast.LENGTH_SHORT).show();
                reorderItem.tile.animate().scaleX(1.2f).scaleY(1.2f)
                        .setDuration(FOCUS_MS).setInterpolator(decelerate).start();
            }
        }
    };

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
        wallpaperExecutor.shutdownNow();
        if (wallpaperBitmap != null) {
            wallpaperBitmap.recycle();
            wallpaperBitmap = null;
        }
        try { unregisterReceiver(packageReceiver); } catch (IllegalArgumentException ignored) { }
    }

    @Override public void onBackPressed() {
        if (reorderMode) {
            reorderMode = false;
            reorderItem = null;
            if (lastFocused != null) {
                lastFocused.tile.requestFocus();
            }
            return;
        }
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
                new int[]{Color.rgb(12, 14, 20), BLACK}));

        addWallpaper();
        addShades();
        // The dock is added before the top bar so that, if nothing has focus yet, Android's
        // default focus search lands on the first app tile rather than the settings button.
        addDock();
        addTopBar();
        setContentView(root);
    }

    private void addWallpaper() {
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));
        loadWallpaperAsync();
    }

    private void loadWallpaperAsync() {
        final int loadId = ++wallpaperLoadId;
        final String savedUri = getSharedPreferences(WALLPAPER_PREFS, MODE_PRIVATE)
                .getString(WALLPAPER_URI_KEY, "");
        wallpaperExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                if (!savedUri.isEmpty()) {
                    bitmap = decodeForDisplay(Uri.parse(savedUri));
                }
            } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
                // The built-in image remains visible if the selected file is invalid or too large.
            }
            final Bitmap result = bitmap;
            handler.post(() -> {
                if (isFinishing() || loadId != wallpaperLoadId) {
                    if (result != null) result.recycle();
                    return;
                }
                Bitmap old = wallpaperBitmap;
                wallpaperBitmap = result;
                if (result != null) wallpaperView.setImageBitmap(result);
                else wallpaperView.setImageDrawable(null);
                if (old != null && old != result) old.recycle();
                updateDockFrost();
            });
        });
    }

    /** Dark overlays that keep the clock and dock readable on any wallpaper. */
    private void addShades() {
        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(40, 0, 0, 0));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        shades.add(dim);

        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(120, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(120), Gravity.TOP));
        shades.add(top);

        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(200, 0, 0, 0)}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(280), Gravity.BOTTOM));
        shades.add(bottom);
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

        // Liquid Glass strip: a blurred copy of the wallpaper behind the dock, a light veil and a
        // thin bright edge (see the "Liquid Glass dock" section). Live background blur is not
        // available on Android 11 TVs without a heavy cost, so the blur is baked once instead.
        final FrameLayout shell = new FrameLayout(this);
        dockShell = shell;
        shell.setBackground(glassBackground(null));   // plain glass until the blur is ready
        shell.setForeground(new GlassRim(dp(DOCK_RADIUS),
                RIM_WIDTH_DP * getResources().getDisplayMetrics().density));
        // Build (or rebuild) the blur whenever the dock gets its size.
        shell.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int key = (r - l) * 31 + (b - t);
            if (key != frostKey) {
                frostKey = key;
                handler.post(frostUpdater);   // not inside the layout pass
            }
        });
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

    /** Wi-Fi and Settings buttons + a pill with the clock and a decorative profile icon, top-right. */
    private void addTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // Wi-Fi button (focusable): opens the TV's Wi-Fi settings.
        ImageView wifi = topButton("Wi-Fi");
        wifi.setImageDrawable(new WifiIcon());
        wifi.setOnClickListener(v -> openSystemScreen(Settings.ACTION_WIFI_SETTINGS,
                Settings.ACTION_SETTINGS));
        LinearLayout.LayoutParams wifiLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        wifiLp.rightMargin = dp(10);
        bar.addView(wifi, wifiLp);

        // Settings button (focusable). Wallpaper and launcher details live in the
        // lightweight HomingZone settings screen.
        ImageView settings = topButton("Settings");
        settings.setImageResource(R.drawable.ic_settings);
        settings.setOnClickListener(v -> {
            try {
                startActivityForResult(new Intent(this, WallpaperSettingsActivity.class), PICK_SETTINGS);
            } catch (Exception e) {
                Toast.makeText(this, "Settings can't be opened", Toast.LENGTH_SHORT).show();
            }
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_SETTINGS && resultCode == RESULT_OK) loadWallpaperAsync();
    }

    /**
     * A round glass button for the top bar. It lights up and grows when focused, and DOWN takes
     * you back to the app that was selected, not to whichever tile is nearest.
     */
    private ImageView topButton(String description) {
        final ImageView button = new ImageView(this);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        styleTopButton(button, false);
        button.setOnFocusChangeListener((v, focused) -> {
            styleTopButton(button, focused);
            button.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f)
                    .setDuration(FOCUS_MS).setInterpolator(decelerate).start();
        });
        button.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN
                    && lastFocused != null && lastFocused.tile.isAttachedToWindow()) {
                lastFocused.tile.requestFocus();
                return true;
            }
            return false;
        });
        return button;
    }

    private void styleTopButton(ImageView button, boolean focused) {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                focused
                        ? new int[]{Color.rgb(250, 250, 252), Color.rgb(214, 218, 228)}
                        : new int[]{Color.argb(122, 145, 151, 169), Color.argb(76, 62, 67, 82)});
        background.setCornerRadius(dp(50));
        button.setBackground(background);
        if (focused) button.setColorFilter(Color.rgb(20, 22, 28));
        else button.clearColorFilter();
    }

    /** Opens the first of these system screens that exists on this TV. */
    private void openSystemScreen(String... actions) {
        for (String action : actions) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (RuntimeException ignored) {
                // not available on this TV: try the next one
            }
        }
        Toast.makeText(this, "Settings can't be opened", Toast.LENGTH_SHORT).show();
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

        items = applySavedOrder(queryApps());
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

    private List<AppItem> applySavedOrder(List<AppItem> discovered) {
        SharedPreferences preferences = getSharedPreferences(ORDER_PREFS, MODE_PRIVATE);
        String saved = preferences.getString(ORDER_KEY, "");
        if (saved.isEmpty() || discovered.size() < 2) return discovered;

        Map<String, AppItem> byPackage = new LinkedHashMap<>();
        for (AppItem item : discovered) byPackage.put(item.packageName, item);
        List<AppItem> ordered = new ArrayList<>(discovered.size());
        for (String packageName : saved.split("\\|")) {
            AppItem item = byPackage.remove(packageName);
            if (item != null) ordered.add(item);
        }
        ordered.addAll(byPackage.values());
        return ordered;
    }

    private void saveOrder() {
        StringBuilder value = new StringBuilder();
        for (AppItem item : items) {
            if (value.length() > 0) value.append('|');
            value.append(item.packageName);
        }
        getSharedPreferences(ORDER_PREFS, MODE_PRIVATE)
                .edit().putString(ORDER_KEY, value.toString()).apply();
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
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (isConfirmKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        reorderItem = item;
                        handler.postDelayed(enterReorderMode, android.view.ViewConfiguration
                                .getLongPressTimeout());
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(enterReorderMode);
                    if (longPressActivated) {
                        longPressActivated = false;
                        return true;
                    }
                    if (reorderMode) {
                        reorderMode = false;
                        reorderItem = null;
                        saveOrder();
                        return true;
                    }
                    launch(item);
                    return true;
                }
            }
            if (reorderMode && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    moveItem(item, -1);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    moveItem(item, 1);
                    return true;
                }
            }
            return false;
        });
        tile.setOnClickListener(v -> launch(item));
        return tile;
    }

    private boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void moveItem(AppItem item, int direction) {
        int from = items.indexOf(item);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= items.size()) return;

        Collections.swap(items, from, to);
        View moving = item.entry;
        dock.removeViewAt(from);
        dock.addView(moving, to);
        item.tile.requestFocus();
        scrollToItem(item, true);
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
    // Liquid Glass dock
    // =========================================================================================

    /** Rebuilds the dock's blurred backdrop from the current wallpaper. Safe to call any time. */
    private void updateDockFrost() {
        if (dockShell == null || root == null || dockShell.getWidth() <= 0
                || dockShell.getHeight() <= 0 || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return;
        }
        final int width = dockShell.getWidth();
        final int height = dockShell.getHeight();
        final int left = dockShell.getLeft();
        final int top = dockShell.getTop();
        final int loadId = wallpaperLoadId;
        wallpaperExecutor.execute(() -> {
            Bitmap frost = null;
            try {
                frost = buildFrost(width, height, left, top);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // Keep the plain glass: a failed blur must never break the launcher.
            }
            final Bitmap result = frost;
            handler.post(() -> {
                if (isFinishing() || loadId != wallpaperLoadId
                        || dockShell.getWidth() != width || dockShell.getHeight() != height) {
                    if (result != null) result.recycle();
                    return;
                }
                dockShell.setBackground(glassBackground(result));
            });
        });
    }

    /**
     * The scene behind the dock (wallpaper + dark overlays), shrunk to 1/8, blurred, then cut
     * down to the dock's area. Returns null if the wallpaper cannot be read.
     */
    private Bitmap buildFrost(int dockWidth, int dockHeight, int dockLeft, int dockTop) {
        Bitmap art = decodeWallpaperSmall();
        if (art == null) return null;

        final int screenW = root.getWidth();
        final int screenH = root.getHeight();
        final int w = Math.max(1, Math.round(screenW / (float) FROST_SCALE));
        final int h = Math.max(1, Math.round(screenH / (float) FROST_SCALE));
        final float sx = w / (float) screenW;
        final float sy = h / (float) screenH;

        Bitmap scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scene);

        // Wallpaper, cropped to fill the screen exactly like the wallpaper view (CENTER_CROP).
        float fill = Math.max((float) w / art.getWidth(), (float) h / art.getHeight());
        canvas.save();
        canvas.translate((w - art.getWidth() * fill) / 2f, (h - art.getHeight() * fill) / 2f);
        canvas.scale(fill, fill);
        canvas.drawBitmap(art, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.restore();
        art.recycle();

        // Approximate the dark overlays without drawing Views from the worker thread.
        canvas.scale(sx, sy);
        canvas.drawColor(Color.argb(40, 0, 0, 0));
        Paint shadePaint = new Paint();
        shadePaint.setShader(new LinearGradient(0, 0, 0, 120,
                Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, screenW, 120, shadePaint);
        shadePaint.setShader(new LinearGradient(0, screenH - 280, 0, screenH,
                Color.TRANSPARENT, Color.argb(200, 0, 0, 0), Shader.TileMode.CLAMP));
        canvas.drawRect(0, screenH - 280, screenW, screenH, shadePaint);

        // Blur the whole small scene, so light from around the dock bleeds into it too.
        int[] px = new int[w * h];
        int[] tmp = new int[px.length];
        scene.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < FROST_PASSES; i++) {
            boxBlur(px, tmp, w, h, FROST_RADIUS, true);
            boxBlur(tmp, px, w, h, FROST_RADIUS, false);
        }
        scene.setPixels(px, 0, w, 0, 0, w, h);

        // Keep only the part that lies behind the dock.
        int x = clamp(Math.round(dockLeft * sx), 0, w - 1);
        int y = clamp(Math.round(dockTop * sy), 0, h - 1);
        int cw = clamp(Math.round(dockWidth * sx), 1, w - x);
        int ch = clamp(Math.round(dockHeight * sy), 1, h - y);
        Bitmap frost = Bitmap.createBitmap(scene, x, y, cw, ch);
        if (frost != scene) scene.recycle();
        return frost;
    }

    /** The dock's background: blurred scene + a light veil, or plain translucent glass without it. */
    private Drawable glassBackground(Bitmap frost) {
        if (frost == null) {
            return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{GLASS_FILL, Color.argb(95, 58, 62, 74)});
        }
        BitmapDrawable blur = new BitmapDrawable(getResources(), frost);
        blur.setFilterBitmap(true);   // smooth scaling up from the tiny bitmap
        GradientDrawable veil = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(GLASS_TINT_TOP, 255, 255, 255),
                        Color.argb(GLASS_TINT_BOTTOM, 255, 255, 255)});
        return new LayerDrawable(new Drawable[]{blur, veil});
    }

    /** The current wallpaper (your picked image, else the built-in one) at about 320 px wide. */
    private Bitmap decodeWallpaperSmall() {
        if (wallpaperBitmap != null) {
            return Bitmap.createScaledBitmap(wallpaperBitmap, FROST_SOURCE_W,
                    Math.max(1, Math.round(FROST_SOURCE_W * wallpaperBitmap.getHeight()
                            / (float) wallpaperBitmap.getWidth())), true);
        }
        String saved = getSharedPreferences(WALLPAPER_PREFS, MODE_PRIVATE)
                .getString(WALLPAPER_URI_KEY, "");
        if (!saved.isEmpty()) {
            Bitmap picked = decodeSmall(Uri.parse(saved), 0);
            if (picked != null) return picked;
        }
        Bitmap black = Bitmap.createBitmap(FROST_SOURCE_W, Math.max(1, FROST_SOURCE_W * 9 / 16),
                Bitmap.Config.ARGB_8888);
        black.eraseColor(BLACK);
        return black;
    }

    private Bitmap decodeForDisplay(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        decode(uri, 0, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int targetWidth = getResources().getDisplayMetrics().widthPixels;
        int targetHeight = getResources().getDisplayMetrics().heightPixels;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidth
                && bounds.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return decode(uri, 0, options);
    }

    /** Decodes either a picked image (uri) or a built-in resource, shrunk with inSampleSize. */
    private Bitmap decodeSmall(Uri uri, int resId) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            decode(uri, resId, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;

            int sample = 1;
            while (options.outWidth / (sample * 2) >= FROST_SOURCE_W) sample *= 2;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;   // a normal (not hardware) bitmap
            return decode(uri, resId, options);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private Bitmap decode(Uri uri, int resId, BitmapFactory.Options options) throws IOException {
        if (uri == null) return BitmapFactory.decodeResource(getResources(), resId, options);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return in == null ? null : BitmapFactory.decodeStream(in, null, options);
        }
    }

    /** One horizontal or vertical box-blur pass over opaque pixels (edge pixels repeat). */
    private static void boxBlur(int[] src, int[] dst, int w, int h, int radius, boolean horizontal) {
        final int span = 2 * radius + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int red = 0, green = 0, blue = 0;
                for (int k = -radius; k <= radius; k++) {
                    int sx = horizontal ? clamp(x + k, 0, w - 1) : x;
                    int sy = horizontal ? y : clamp(y + k, 0, h - 1);
                    int p = src[sy * w + sx];
                    red += (p >> 16) & 0xFF;
                    green += (p >> 8) & 0xFF;
                    blue += p & 0xFF;
                }
                dst[y * w + x] = 0xFF000000 | ((red / span) << 16) | ((green / span) << 8) | (blue / span);
            }
        }
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }

    /** Thin bright edge and a soft light along the top: what makes the dock read as glass. */
    private static final class GlassRim extends Drawable {
        private final float radius;
        private final float stroke;
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF outer = new RectF();
        private final RectF inner = new RectF();

        GlassRim(float radius, float stroke) {
            this.radius = radius;
            this.stroke = stroke;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(stroke);
        }

        @Override protected void onBoundsChange(Rect b) {
            outer.set(b);
            float half = stroke / 2f;
            inner.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
            // Light fading out over the top 45% of the glass.
            sheen.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.45f,
                    Color.argb(SHEEN_TOP, 255, 255, 255), Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP));
            // Edge: brightest on top, dim in the middle, a little light again along the bottom.
            rim.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    new int[]{Color.argb(RIM_TOP, 255, 255, 255),
                            Color.argb(RIM_MIDDLE, 255, 255, 255),
                            Color.argb(RIM_BOTTOM, 255, 255, 255)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas canvas) {
            canvas.drawRoundRect(outer, radius, radius, sheen);
            canvas.drawRoundRect(inner, radius - stroke / 2f, radius - stroke / 2f, rim);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A Wi-Fi symbol (a dot and three arcs), drawn in code so it needs no extra resource file. */
    private static final class WifiIcon extends Drawable {
        // Sizes are shares of the icon size. The dot sits low so the whole symbol looks centred.
        private static final float[] RADII = {0.24f, 0.46f, 0.68f};
        private static final float DOT_Y = 0.83f;
        private static final float DOT_RADIUS = 0.075f;
        private static final float STROKE = 0.10f;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();

        WifiIcon() {
            paint.setColor(Color.WHITE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height());
            float cx = b.exactCenterX();
            float cy = b.top + size * DOT_Y;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * STROKE);
            for (float r : RADII) {
                arc.set(cx - r * size, cy - r * size, cx + r * size, cy + r * size);
                canvas.drawArc(arc, 225f, 90f, false, paint);   // the top quarter of each circle
            }
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, size * DOT_RADIUS, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);   // lets the focused button turn the icon dark
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private TextView text(String value, float sizeSp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(assetTypeface(medium ? "Inter-Medium.ttf" : "Inter-Regular.ttf",
                Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)));
        return t;
    }

    private Typeface assetTypeface(String name, Typeface fallback) {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/" + name);
        } catch (RuntimeException ignored) {
            return fallback;
        }
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