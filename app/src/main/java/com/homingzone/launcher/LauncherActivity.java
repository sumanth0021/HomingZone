package com.homingzone.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private static final String TAG = "HomingZone";
    private AppCatalog catalog;
    private List<AppCatalog.AppEntry> apps = new ArrayList<>();
    private boolean listBuilt;      // true once the dock has been filled at least once
    private boolean resumed;        // true while the launcher is on screen
    private boolean dirty;          // an app was installed/removed/updated while we were not looking
    private boolean receiverRegistered;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() { if (resumed) refreshApps(); }
    };

    /** Told by Android when an app is added, removed, updated or enabled/disabled. Costs nothing while idle (no polling). */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // Enable/disable ("changed") is picked up by the next comparison; the others also drop the cached artwork.
            if (!Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction()) && intent.getData() != null) {
                catalog.invalidate(intent.getData().getSchemeSpecificPart());
            }
            dirty = true;
            if (resumed) {   // wait a moment: installs often send several broadcasts in a row
                handler.removeCallbacks(refreshTask);
                handler.postDelayed(refreshTask, 400);
            }
        }
    };

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
        catalog = new AppCatalog(this);
        buildUi();
        refreshApps();
        registerPackageReceiver();
        handler.post(clockTick);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (dirty) refreshApps();   // only if an app changed while we were away; otherwise focus stays exactly where it was
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(refreshTask);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (receiverRegistered) {
            unregisterReceiver(packageReceiver);
            receiverRegistered = false;
        }
        handler.removeCallbacksAndMessages(null);
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
        receiverRegistered = true;
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

    /**
     * Re-reads the installed apps and rebuilds the dock, but only if the list really changed.
     * Focus is kept on the same app (or the nearest one) so returning from an app never throws you back to the start.
     */
    private void refreshApps() {
        dirty = false;
        List<AppCatalog.AppEntry> found = catalog.load();
        if (listBuilt && found.equals(apps)) return;   // identical list: leave the views and the focus alone

        // Remember where focus was before the old tiles are thrown away.
        int focusedIndex = listBuilt ? indexOfFocusedTile() : -1;
        AppCatalog.AppEntry focusedEntry =
                focusedIndex >= 0 && focusedIndex < apps.size() ? apps.get(focusedIndex) : null;
        boolean giveDockFocus = !listBuilt || focusedIndex >= 0 || getCurrentFocus() == null;

        apps = found;
        listBuilt = true;
        dock.removeAllViews();
        for (AppCatalog.AppEntry entry : apps) dock.addView(appTile(entry));

        if (apps.isEmpty()) {
            heroTitle.setText("HomingZone");
            heroSubtitle.setText("No apps found. Install an app to see it here.");
            heroIcon.setImageDrawable(null);
            return;
        }
        int target = 0;
        if (focusedEntry != null) {
            int same = apps.indexOf(focusedEntry);
            target = same >= 0 ? same : Math.min(focusedIndex, apps.size() - 1);   // app vanished: stay near it
        }
        if (giveDockFocus) dock.getChildAt(target).requestFocus();   // focus listener updates the hero
        else select(apps.get(target));
    }

    private int indexOfFocusedTile() {
        for (int n = 0; n < dock.getChildCount(); n++) {
            if (dock.getChildAt(n).hasFocus()) return n;
        }
        return -1;
    }

    /** One Apple-TV-style app entry with artwork and its installed app name. */
    private View appTile(final AppCatalog.AppEntry app) {
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
        Drawable banner = app.newBanner();   // cached after the first load; null when the app has no banner
        ImageView art = new ImageView(this);
        if (banner != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(banner);
            tile.addView(art, new FrameLayout.LayoutParams(-1, -1));
        } else {
            tile.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(44, 44, 48), Color.rgb(22, 22, 25)}));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageDrawable(app.newIcon());
            tile.addView(art, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
        }

        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        tile.setLayoutParams(tileLp);

        TextView name = text(app.label, 12, Color.argb(220, 255, 255, 255), false);
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
                select(app);
                // Include extra room so the scaled tile is fully visible when scrolling.
                final int pad = dp(30);
                appScroll.post(() -> appScroll.requestChildRectangleOnScreen(dock,
                        new android.graphics.Rect(v.getLeft() - pad, 0, v.getRight() + pad, dock.getHeight()), false));
            }
        });
        tile.setOnClickListener(v -> launch(app));
        return entry;
    }

    private void select(AppCatalog.AppEntry entry) {
        heroTitle.setText(entry.label);
        heroSubtitle.setText("Ready to open  ·  " + entry.packageName);
        heroIcon.setImageDrawable(entry.newIcon());   // from the cache: no PackageManager call per key press
    }

    private void launch(AppCatalog.AppEntry entry) {
        try {
            startActivity(entry.launchIntent());
        } catch (RuntimeException e) {   // ActivityNotFoundException, SecurityException, ...
            Log.w(TAG, "Could not launch " + entry.packageName, e);
            Toast.makeText(this, "Can't open " + entry.label, Toast.LENGTH_SHORT).show();
            handler.post(refreshTask);   // the app may have just been removed: make sure the dock is current
        }
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