package com.homingzone.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private final int white = Color.rgb(248, 249, 252);
    private final int muted = Color.rgb(220, 226, 236);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout heroContent;
    private TextView heroEyebrow, heroTitle, heroDescription, heroButton;
    private View firstDockTile;
    private final List<StreamingApp> streamingApps = new ArrayList<>();
    private int heroIndex = 0;
    private boolean resumed = false;

    private static class StreamingApp {
        String label, packageName, tagline;
        int[] colors;
        StreamingApp(String label, String packageName, String tagline, int[] colors) {
            this.label = label; this.packageName = packageName;
            this.tagline = tagline; this.colors = colors;
        }
    }

    private final Runnable rotateHero = new Runnable() {
        @Override public void run() {
            if (resumed && streamingApps.size() > 1) {
                heroIndex = (heroIndex + 1) % streamingApps.size();
                updateHero();
                handler.postDelayed(this, 12000);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        discoverStreamingApps();
        buildHome();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(rotateHero);
        handler.postDelayed(rotateHero, 12000);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(rotateHero);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void discoverStreamingApps() {
        streamingApps.clear();
        addIfInstalled("Netflix", "com.netflix.ninja",
                "Continue your entertainment journey.", new int[]{0xff32141b, 0xffa40d20, 0xffe22b32});
        addIfInstalled("Prime Video", "com.amazon.amazonvideo.livingroom",
                "Discover something great to watch.", new int[]{0xff071b42, 0xff0757a7, 0xff168de2});
        addIfInstalled("YouTube", "com.google.android.youtube.tv",
                "Explore videos, creators and more.", new int[]{0xff281013, 0xffb5121b, 0xffed3434});
        addIfInstalled("Disney+", "com.disney.disneyplus",
                "Step into stories from Disney and beyond.", new int[]{0xff07102f, 0xff122c73, 0xff3154a7});
        addIfInstalled("Apple TV", "com.apple.atve.androidtv.appletv",
                "Explore premium films and series.", new int[]{0xff101318, 0xff343b4b, 0xff68758c});
        addIfInstalled("JioHotstar", "in.startv.hotstar",
                "Find your next film, series or live moment.", new int[]{0xff10102f, 0xff282c86, 0xff5555d8});
        addIfInstalled("ZEE5", "com.zee5.aosp",
                "Explore entertainment from across languages.", new int[]{0xff1c1028, 0xff57246d, 0xff9a3d9c});
        addIfInstalled("Sony LIV", "com.sonyliv",
                "Discover shows, films and sports.", new int[]{0xff101a37, 0xff194b8e, 0xff247fc2});
        addIfInstalled("VLC", "org.videolan.vlc",
                "Play your own movies and videos.", new int[]{0xff202020, 0xff454545, 0xff777777});
        if (streamingApps.isEmpty()) {
            streamingApps.add(new StreamingApp("Your entertainment", "",
                    "Install a streaming app to see it featured here.",
                    new int[]{0xff10233d, 0xff243b5a, 0xff526b89}));
        }
    }

    private void addIfInstalled(String label, String pkg, String tagline, int[] colors) {
        if (getPackageManager().getLaunchIntentForPackage(pkg) != null) {
            streamingApps.add(new StreamingApp(label, pkg, tagline, colors));
        }
    }

    private void buildHome() {
        root = new FrameLayout(this);
        root.setFocusableInTouchMode(true);
        root.setBackground(gradient(new int[]{0xff07182d, 0xff142b43, 0xff49312c, 0xff9b4825}));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x99020b18, 0x22050a12, 0xaa050910}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        addTopBar();
        addHero();
        addDots();
        addAppDock();

        setContentView(root);
        root.requestFocus();
        updateHero();
    }

    private void addTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(7), dp(6), dp(7), dp(6));
        bar.setBackground(roundRect(0x99131722, dp(34), 0x44ffffff, dp(1)));
        String[] tabs = {"⌂  Home", "Apps", "Discover", "Library", "⌕"};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            TextView item = text(tabs[i], 14, i == 0 ? 0xff111722 : white, i == 0);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(17), dp(10), dp(17), dp(10));
            item.setFocusable(true);
            item.setBackground(roundRect(i == 0 ? Color.WHITE : Color.TRANSPARENT, dp(28), 0, 0));
            item.setOnFocusChangeListener((v, focused) -> {
                if (tab != 0) v.setBackground(roundRect(focused ? 0x55ffffff : Color.TRANSPARENT,
                        dp(28), focused ? 0x88ffffff : 0, dp(focused ? 1 : 0)));
            });
            item.setOnClickListener(v -> {
                if (tab == 1 || tab == 2) focusDock();
                else if (tab == 3) Toast.makeText(this, "Library is coming soon", Toast.LENGTH_SHORT).show();
                else if (tab == 4) Toast.makeText(this, "Search is coming soon", Toast.LENGTH_SHORT).show();
            });
            bar.addView(item, new LinearLayout.LayoutParams(-2, dp(43)));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(22);
        root.addView(bar, lp);

        TextView settings = text("⚙", 23, white, true);
        settings.setGravity(Gravity.CENTER);
        settings.setFocusable(true);
        settings.setBackground(roundRect(0x22ffffff, dp(25), 0x22ffffff, dp(1)));
        settings.setOnClickListener(v -> Toast.makeText(this, "Settings will be added next", Toast.LENGTH_SHORT).show());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.RIGHT);
        sp.topMargin = dp(22); sp.rightMargin = dp(35);
        root.addView(settings, sp);
    }

    private void addHero() {
        heroContent = new LinearLayout(this);
        heroContent.setOrientation(LinearLayout.VERTICAL);
        heroContent.setGravity(Gravity.CENTER_VERTICAL);
        heroEyebrow = text("FEATURED FOR YOU", 11, 0xffffc69e, true);
        heroEyebrow.setLetterSpacing(0.2f);
        heroContent.addView(heroEyebrow);

        heroTitle = text("HomingZone", 48, white, true);
        heroTitle.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(15);
        heroContent.addView(heroTitle, titleLp);

        heroDescription = text("Your entertainment, all in one place.", 17, muted, false);
        heroDescription.setMaxLines(3);
        heroDescription.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(dp(440), -2);
        descLp.topMargin = dp(10);
        heroContent.addView(heroDescription, descLp);

        heroButton = text("▶   Open app", 15, 0xff10151d, true);
        heroButton.setGravity(Gravity.CENTER);
        heroButton.setPadding(dp(22), 0, dp(22), 0);
        heroButton.setFocusable(true);
        heroButton.setBackground(roundRect(Color.WHITE, dp(30), 0, 0));
        heroButton.setOnFocusChangeListener((v, focused) ->
                v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(130).start());
        heroButton.setOnClickListener(v -> launchCurrentFeatured());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-2, dp(48));
        buttonLp.topMargin = dp(26);
        heroContent.addView(heroButton, buttonLp);

        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(dp(510), dp(320),
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        heroLp.leftMargin = dp(58);
        root.addView(heroContent, heroLp);
    }

    private void updateHero() {
        if (heroContent == null || streamingApps.isEmpty()) return;
        StreamingApp app = streamingApps.get(heroIndex % streamingApps.size());
        root.setBackground(gradient(app.colors));
        heroEyebrow.setText("AVAILABLE ON YOUR TV  ·  " + app.label.toUpperCase(Locale.getDefault()));
        heroTitle.setText(app.label);
        heroDescription.setText(app.tagline + "\nSelected from the streaming apps installed on this TV.");
        heroButton.setText("▶   Open " + app.label);
        heroButton.setEnabled(!app.packageName.isEmpty());
    }

    private void launchCurrentFeatured() {
        if (streamingApps.isEmpty()) return;
        StreamingApp app = streamingApps.get(heroIndex % streamingApps.size());
        if (app.packageName.isEmpty()) {
            Toast.makeText(this, "Install a streaming app to get started", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launch != null) startActivity(launch);
    }

    private void addDots() {
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            View dot = new View(this);
            dot.setBackground(roundRect(i == 0 ? Color.WHITE : 0x77ffffff, dp(8), 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(i == 0 ? 18 : 8), dp(8));
            lp.leftMargin = dp(5); lp.rightMargin = dp(5);
            dots.addView(dot, lp);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(15), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(157);
        root.addView(dots, lp);
    }

    private void addAppDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(14), dp(10), dp(14), dp(10));
        dock.setBackground(roundRect(0x99232631, dp(29), 0x44ffffff, dp(1)));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        firstDockTile = null;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        List<ResolveInfo> apps = getInstalledLaunchableApps();
        for (ResolveInfo info : apps) {
            View tile = appTile(info);
            row.addView(tile);
            if (firstDockTile == null) firstDockTile = tile;
        }
        scroll.addView(row);
        dock.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(128), Gravity.BOTTOM);
        lp.leftMargin = dp(34); lp.rightMargin = dp(34); lp.bottomMargin = dp(27);
        root.addView(dock, lp);
    }

    private List<ResolveInfo> getInstalledLaunchableApps() {
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        Intent regular = new Intent(Intent.ACTION_MAIN);
        regular.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : getPackageManager().queryIntentActivities(regular, 0)) {
            if (!getPackageName().equals(info.activityInfo.packageName)) unique.put(info.activityInfo.packageName, info);
        }
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory("android.intent.category.LEANBACK_LAUNCHER");
        for (ResolveInfo info : getPackageManager().queryIntentActivities(leanback, 0)) {
            if (!getPackageName().equals(info.activityInfo.packageName)) unique.put(info.activityInfo.packageName, info);
        }
        List<ResolveInfo> result = new ArrayList<>(unique.values());
        Collections.sort(result, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.CASE_INSENSITIVE_ORDER.compare(
                        String.valueOf(a.loadLabel(getPackageManager())),
                        String.valueOf(b.loadLabel(getPackageManager())));
            }
        });
        return result;
    }

    private View appTile(final ResolveInfo info) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setBackground(roundRect(0x18ffffff, dp(17), 0x22ffffff, dp(1)));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(62), dp(62)));
        TextView label = text(String.valueOf(info.loadLabel(getPackageManager())), 10, white, true);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        tile.addView(label, new LinearLayout.LayoutParams(dp(125), dp(18)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(140), dp(104));
        tlp.leftMargin = dp(6); tlp.rightMargin = dp(6);
        tile.setLayoutParams(tlp);
        tile.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(130).start();
            v.setBackground(roundRect(focused ? 0x66ffffff : 0x18ffffff, dp(17),
                    focused ? Color.WHITE : 0x22ffffff, dp(focused ? 2 : 1)));
        });
        tile.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(info.activityInfo.packageName);
            if (launch != null) startActivity(launch);
        });
        return tile;
    }

    private void focusDock() {
        if (firstDockTile != null) firstDockTile.requestFocus();
    }

    private GradientDrawable gradient(int[] colors) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        return v;
    }
    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
