package com.homingzone.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LauncherActivity extends Activity {
    private static final String[] FAVOURITES = {
            "com.netflix.ninja", "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom", "org.videolan.vlc", "com.amazon.mp3"};

    private final int dark = Color.rgb(9, 10, 14);
    private final int white = Color.WHITE;
    private final int muted = Color.argb(175, 255, 255, 255);
    private final Map<String, Integer> colorCache = new HashMap<>();

    private GradientDrawable backdrop;
    private ValueAnimator tintAnim;
    private int tint = Color.rgb(46, 64, 104);
    private TextView heroTitle, heroMeta, heroDesc, openButton, rowTitle;
    private ImageView heroIcon;
    private LinearLayout navBar, dots, rowBox;
    private List<ResolveInfo> allApps = new ArrayList<>();
    private List<ResolveInfo> shown = new ArrayList<>();
    private ResolveInfo current;
    private boolean showAll = false;
    private boolean started = false;

    // Free v3 API key from themoviedb.org (Settings > API). Leave empty to stay fully offline.
    private static final String TMDB_KEY = "";
    private static final String REGION = "IN";

    private static class Show { String title, overview, provider, pkg; Bitmap image; }
    private List<Show> shows = new ArrayList<>();
    private int showIdx = 0;
    private FrameLayout artLayer;
    private ImageView art;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotateTask = new Runnable() {
        @Override public void run() {
            if (shows.size() > 1) { showIdx = (showIdx + 1) % shows.size(); showHero(); }
            handler.postDelayed(this, 9000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        allApps = getLaunchableApps();
        buildScreen();
        fillRow(true);
        loadShows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (started) {
            allApps = getLaunchableApps();
            fillRow(true);
        }
        started = true;
        if (shows.size() > 1) { handler.removeCallbacks(rotateTask); handler.postDelayed(rotateTask, 9000); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotateTask);
    }

    @Override
    public void onBackPressed() {
        // A launcher has nowhere to go back to.
    }

    // ---------- Layout ----------

    private void buildScreen() {
        backdrop = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{tint, dark, dark});
        setTint(tint);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(42), dp(26), 0, dp(20));

        // Pill navigation
        navBar = new LinearLayout(this);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        navBar.setBackground(roundRect(Color.argb(95, 0, 0, 0), dp(30), Color.TRANSPARENT, 0));
        String[] tabs = {"Home", "Apps", "Settings"};
        for (int i = 0; i < tabs.length; i++) navBar.addView(navItem(tabs[i], i));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-2, -2);
        navLp.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(navBar, navLp);

        // Hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);

        heroTitle = text("", 54, white, true);
        heroTitle.setAllCaps(true);
        heroTitle.setLetterSpacing(0.14f);
        heroTitle.setMaxLines(1);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(heroTitle);

        heroMeta = text("", 16, muted, true);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-2, -2);
        metaLp.topMargin = dp(6);
        info.addView(heroMeta, metaLp);

        heroDesc = text("", 18, white, false);
        heroDesc.setMaxLines(2);
        heroDesc.setMaxWidth(dp(460));
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-2, -2);
        descLp.topMargin = dp(10);
        descLp.bottomMargin = dp(22);
        info.addView(heroDesc, descLp);

        openButton = text("Open", 18, white, true);
        openButton.setPadding(dp(34), dp(13), dp(34), dp(13));
        openButton.setFocusable(true);
        styleButton(false);
        openButton.setOnFocusChangeListener((v, focused) -> styleButton(focused));
        openButton.setOnClickListener(v -> {
            if (!shows.isEmpty()) launchPackage(shows.get(showIdx).pkg);
            else if (current != null) launch(current);
        });
        info.addView(openButton, new LinearLayout.LayoutParams(-2, -2));
        hero.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        heroIcon = new ImageView(this);
        heroIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(230), dp(230));
        iconLp.rightMargin = dp(150);
        hero.addView(heroIcon, iconLp);
        page.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1));

        // Pagination dots
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(-2, dp(24));
        dotsLp.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(dots, dotsLp);

        // Row
        rowTitle = text("Your apps", 24, white, true);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(-2, -2);
        rtLp.leftMargin = dp(14);
        page.addView(rowTitle, rtLp);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        rowBox = new LinearLayout(this);
        rowBox.setOrientation(LinearLayout.HORIZONTAL);
        rowBox.setPadding(dp(14), dp(12), dp(40), dp(14));
        scroll.addView(rowBox);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(214)));

        int screenH = getResources().getDisplayMetrics().heightPixels;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(backdrop);
        artLayer = new FrameLayout(this);
        artLayer.setVisibility(View.GONE);
        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artLayer.addView(art, new FrameLayout.LayoutParams(-1, -1));
        View side = new View(this);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(240, 9, 10, 14), Color.argb(110, 9, 10, 14), Color.argb(0, 9, 10, 14)}));
        artLayer.addView(side, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{dark, Color.argb(0, 9, 10, 14)}));
        artLayer.addView(bottom, new FrameLayout.LayoutParams(-1, (int) (screenH * 0.5f), Gravity.BOTTOM));
        TextView credit = text("Show data from TMDB", 11, Color.argb(110, 255, 255, 255), false);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cLp.topMargin = dp(14);
        cLp.rightMargin = dp(24);
        artLayer.addView(credit, cLp);
        root.addView(artLayer, new FrameLayout.LayoutParams(-1, -1));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private TextView navItem(String label, final int index) {
        final TextView t = text(label, 16, muted, true);
        t.setPadding(dp(26), dp(10), dp(26), dp(10));
        t.setFocusable(true);
        styleNav(t, false, tabSelected(index));
        t.setOnFocusChangeListener((v, focused) -> styleNav(t, focused, tabSelected(index)));
        t.setOnClickListener(v -> {
            if (index == 2) {
                try {
                    Intent s = new Intent(Settings.ACTION_SETTINGS);
                    s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(s);
                } catch (Exception ignored) { }
                return;
            }
            showAll = index == 1;
            for (int i = 0; i < navBar.getChildCount(); i++) {
                TextView other = (TextView) navBar.getChildAt(i);
                styleNav(other, other.hasFocus(), tabSelected(i));
            }
            fillRow(false);
        });
        return t;
    }

    private boolean tabSelected(int index) {
        return index == (showAll ? 1 : 0);
    }

    private void styleNav(TextView t, boolean focused, boolean selected) {
        t.setTextColor(focused ? dark : selected ? white : muted);
        t.setBackground(roundRect(focused ? Color.argb(238, 240, 240, 244)
                : selected ? Color.argb(60, 255, 255, 255) : Color.TRANSPARENT,
                dp(24), Color.TRANSPARENT, 0));
    }

    private void styleButton(boolean focused) {
        openButton.setTextColor(focused ? dark : white);
        openButton.setBackground(roundRect(focused ? Color.argb(240, 245, 245, 248)
                : Color.argb(85, 255, 255, 255), dp(10), Color.TRANSPARENT, 0));
    }

    // ---------- Row and cards ----------

    private void fillRow(boolean focusFirst) {
        rowBox.removeAllViews();
        shown = showAll ? allApps : favourites();
        rowTitle.setText(showAll ? "All apps" : "Your apps");
        for (ResolveInfo info : shown) rowBox.addView(appCard(info));
        select(shown.isEmpty() ? null : shown.get(0));
        if (focusFirst && rowBox.getChildCount() > 0) rowBox.getChildAt(0).requestFocus();
    }

    private List<ResolveInfo> favourites() {
        List<ResolveInfo> picked = new ArrayList<>();
        for (String pkg : FAVOURITES) {
            for (ResolveInfo info : allApps) {
                if (pkg.equals(info.activityInfo.packageName)) { picked.add(info); break; }
            }
        }
        if (picked.isEmpty()) {
            picked.addAll(allApps.subList(0, Math.min(allApps.size(), 12)));
        }
        return picked;
    }

    private View appCard(final ResolveInfo info) {
        final int cardColor = mix(colorFor(info), dark, 0.15f);
        final LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setFocusable(true);

        final FrameLayout card = new FrameLayout(this);
        card.setBackground(roundRect(cardColor, dp(12), Color.TRANSPARENT, 0));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(icon, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        item.addView(card, new LinearLayout.LayoutParams(dp(210), dp(118)));

        TextView name = text(label(info), 15, white, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(9);
        item.addView(name, nameLp);
        item.addView(text(kind(info), 14, Color.argb(130, 255, 255, 255), false));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(210), -2);
        lp.rightMargin = dp(18);
        item.setLayoutParams(lp);

        item.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f)
                    .setDuration(140).start();
            card.setBackground(roundRect(cardColor, dp(12),
                    focused ? white : Color.TRANSPARENT, focused ? dp(3) : 0));
            if (focused) select(info);
        });
        item.setOnClickListener(v -> launch(info));
        return item;
    }

    // ---------- Hero state ----------

    private void select(ResolveInfo info) {
        current = info;
        if (!shows.isEmpty()) return; // a recommended show owns the hero
        if (info == null) {
            heroTitle.setText("HomingZone");
            heroMeta.setText("");
            heroDesc.setText("No apps found yet.");
            heroIcon.setImageDrawable(null);
            updateDots(-1, 0);
            return;
        }
        String name = label(info);
        heroTitle.setText(name);
        heroMeta.setText(kind(info));
        heroDesc.setText(describe(kind(info), name));
        heroIcon.setImageDrawable(info.loadIcon(getPackageManager()));
        animateTint(colorFor(info));
        updateDots(shown.indexOf(info), shown.size());
    }

    private void updateDots(int index, int total) {
        dots.removeAllViews();
        int count = Math.min(total, 8);
        if (index < 0 || count < 2) return;
        int active = index * count / total;
        for (int i = 0; i < count; i++) {
            View d = new View(this);
            d.setBackground(roundRect(Color.argb(i == active ? 235 : 90, 255, 255, 255),
                    dp(4), Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(i == active ? 24 : 8), dp(8));
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            dots.addView(d, lp);
        }
    }

    private void setTint(int c) {
        tint = c;
        backdrop.setColors(new int[]{c, mix(c, dark, 0.6f), dark});
    }

    private void animateTint(int to) {
        if (tintAnim != null) tintAnim.cancel();
        tintAnim = ValueAnimator.ofObject(new ArgbEvaluator(), tint, to);
        tintAnim.setDuration(350);
        tintAnim.addUpdateListener(a -> setTint((Integer) a.getAnimatedValue()));
        tintAnim.start();
    }

    // ---------- Recommended shows (TMDB) ----------

    private void showHero() {
        Show s = shows.get(showIdx);
        artLayer.setVisibility(View.VISIBLE);
        heroIcon.setVisibility(View.GONE);
        heroTitle.setTextSize(44);
        heroTitle.setText(s.title);
        heroMeta.setText("Popular on " + s.provider);
        heroDesc.setMaxLines(3);
        heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        heroDesc.setText(s.overview);
        openButton.setText("Open " + s.provider);
        art.setImageBitmap(s.image);
        art.setAlpha(0f);
        art.animate().alpha(1f).setDuration(500).start();
        updateDots(showIdx, shows.size());
    }

    private void loadShows() {
        if (TMDB_KEY.isEmpty()) return;
        new Thread(() -> {
            final List<Show> out = new ArrayList<>();
            try {
                List<Show> a = fetchShows(8, "Netflix", "com.netflix.ninja");
                List<Show> b = fetchShows(119, "Prime Video", "com.amazon.amazonvideo.livingroom");
                for (int i = 0; i < 4; i++) {
                    if (i < a.size()) out.add(a.get(i));
                    if (i < b.size()) out.add(b.get(i));
                }
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                if (out.isEmpty()) return;
                shows = out;
                showIdx = 0;
                showHero();
                handler.removeCallbacks(rotateTask);
                handler.postDelayed(rotateTask, 9000);
            });
        }).start();
    }

    private List<Show> fetchShows(int provider, String name, String pkg) throws Exception {
        String url = "https://api.themoviedb.org/3/discover/tv?api_key=" + TMDB_KEY
                + "&with_watch_providers=" + provider + "&watch_region=" + REGION
                + "&sort_by=popularity.desc";
        JSONArray results = new JSONObject(readUrl(url)).getJSONArray("results");
        List<Show> list = new ArrayList<>();
        for (int i = 0; i < results.length() && list.size() < 4; i++) {
            JSONObject o = results.getJSONObject(i);
            String path = o.optString("backdrop_path", "");
            if (path.isEmpty() || "null".equals(path)) continue;
            Show s = new Show();
            s.title = o.optString("name");
            s.overview = o.optString("overview");
            s.provider = name;
            s.pkg = pkg;
            HttpURLConnection c = (HttpURLConnection) new URL("https://image.tmdb.org/t/p/w1280" + path).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            try (InputStream in = c.getInputStream()) { s.image = BitmapFactory.decodeStream(in); }
            if (s.image != null) list.add(s);
        }
        return list;
    }

    private String readUrl(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // ---------- App data ----------

    private List<ResolveInfo> getLaunchableApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> result = getPackageManager().queryIntentActivities(intent, 0);
        Collections.sort(result, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.CASE_INSENSITIVE_ORDER.compare(label(a), label(b));
            }
        });
        List<ResolveInfo> filtered = new ArrayList<>();
        for (ResolveInfo info : result) {
            if (!getPackageName().equals(info.activityInfo.packageName)) filtered.add(info);
        }
        return filtered;
    }

    private void launch(ResolveInfo info) { launchPackage(info.activityInfo.packageName); }

    private void launchPackage(String pkg) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        }
    }

    private String label(ResolveInfo info) {
        return String.valueOf(info.loadLabel(getPackageManager()));
    }

    private String kind(ResolveInfo info) {
        if (Build.VERSION.SDK_INT >= 26) {
            switch (info.activityInfo.applicationInfo.category) {
                case ApplicationInfo.CATEGORY_VIDEO: return "Video";
                case ApplicationInfo.CATEGORY_AUDIO: return "Music";
                case ApplicationInfo.CATEGORY_GAME: return "Game";
                default: break;
            }
        }
        return "App";
    }

    private String describe(String kind, String name) {
        if (kind.equals("Video")) return "Jump back into " + name + " and keep watching.";
        if (kind.equals("Music")) return "Open " + name + " and press play.";
        if (kind.equals("Game")) return "Launch " + name + " and start playing.";
        return "Open " + name + ".";
    }

    /** Average colour of the icon, pushed to a rich mid-tone so it works as a backdrop. */
    private int colorFor(ResolveInfo info) {
        String key = info.activityInfo.packageName;
        Integer hit = colorCache.get(key);
        if (hit != null) return hit;
        int result = Color.rgb(46, 64, 104);
        try {
            Drawable d = info.loadIcon(getPackageManager());
            Bitmap bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 24, 24);
            d.draw(new Canvas(bmp));
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) > 200) {
                        r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++;
                    }
                }
            }
            if (n > 0) {
                float[] hsv = new float[3];
                Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
                if (hsv[1] < 0.15f) hsv[0] = 215f; // grey or white icons get a calm blue
                hsv[1] = Math.max(0.45f, Math.min(hsv[1] + 0.15f, 0.85f));
                hsv[2] = 0.58f;
                result = Color.HSVToColor(hsv);
            }
        } catch (Exception ignored) { }
        colorCache.put(key, result);
        return result;
    }

    // ---------- Helpers ----------

    private int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) * (1 - t) + Color.red(b) * t),
                (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
