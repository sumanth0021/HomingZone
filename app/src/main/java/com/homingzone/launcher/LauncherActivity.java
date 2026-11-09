package com.homingzone.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LauncherActivity extends Activity {
    private final int white = Color.rgb(248, 249, 252);
    private final int muted = Color.rgb(214, 221, 232);
    private FrameLayout root;
    private View firstAppTile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        buildHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) buildHome();
    }

    private void buildHome() {
        firstAppTile = null;
        root = new FrameLayout(this);
        root.setFocusableInTouchMode(true);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.rgb(5, 17, 33),
                        Color.rgb(14, 31, 51),
                        Color.rgb(61, 37, 34),
                        Color.rgb(126, 55, 27)
                }));

        // Soft dark overlay keeps the text legible over the cinematic gradient.
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(70, 0, 7, 17),
                        Color.argb(22, 5, 10, 18),
                        Color.argb(90, 5, 9, 16)
                }));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        addTopNavigation();
        addHeroContent();
        addPagination();
        addAppDock();

        setContentView(root);
        root.requestFocus();
    }

    private void addTopNavigation() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        top.setPadding(dp(7), dp(6), dp(7), dp(6));
        top.setBackground(roundRect(Color.argb(145, 16, 19, 28), dp(34),
                Color.argb(55, 255, 255, 255), dp(1)));

        String[] labels = {"⌂  Home", "Apps", "Movies", "Series", "Library", "⌕"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = text(labels[i], i == 0 ? 15 : 14,
                    i == 0 ? Color.rgb(20, 24, 32) : white, i == 0);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setPadding(dp(i == 0 ? 20 : 15), dp(10), dp(i == 0 ? 20 : 15), dp(10));
            if (i == 0) {
                tab.setBackground(roundRect(Color.WHITE, dp(28), Color.TRANSPARENT, 0));
            } else {
                tab.setBackground(roundRect(Color.TRANSPARENT, dp(28), Color.TRANSPARENT, 0));
            }
            tab.setOnFocusChangeListener((v, focused) -> {
                if (focused && index != 0) {
                    v.setBackground(roundRect(Color.argb(65, 255, 255, 255), dp(28),
                            Color.argb(80, 255, 255, 255), dp(1)));
                } else if (!focused && index != 0) {
                    v.setBackground(roundRect(Color.TRANSPARENT, dp(28), Color.TRANSPARENT, 0));
                }
            });
            tab.setOnClickListener(v -> {
                if (index == 1) focusApps();
                else if (index > 1) Toast.makeText(this,
                        labels[index].replace("⌕", "Search") + " is coming in a future update",
                        Toast.LENGTH_SHORT).show();
            });
            top.addView(tab, new LinearLayout.LayoutParams(-2, dp(44)));
            if (i == 0) {
                View spacer = new View(this);
                top.addView(spacer, new LinearLayout.LayoutParams(dp(5), 1));
            }
        }

        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        navLp.topMargin = dp(22);
        root.addView(top, navLp);

        LinearLayout utilities = new LinearLayout(this);
        utilities.setGravity(Gravity.CENTER);
        utilities.setOrientation(LinearLayout.HORIZONTAL);
        String[] symbols = {"◉", "⚙", "●"};
        String[] names = {"Network", "Settings", "Profile"};
        for (int i = 0; i < symbols.length; i++) {
            final String name = names[i];
            TextView utility = text(symbols[i], i == 2 ? 21 : 20, white, true);
            utility.setGravity(Gravity.CENTER);
            utility.setFocusable(true);
            utility.setBackground(roundRect(Color.TRANSPARENT, dp(24), Color.TRANSPARENT, 0));
            utility.setOnFocusChangeListener((v, focused) ->
                    v.setBackground(roundRect(focused ? Color.argb(70,255,255,255) : Color.TRANSPARENT,
                            dp(24), Color.argb(focused ? 100 : 0,255,255,255), dp(1))));
            utility.setOnClickListener(v -> Toast.makeText(this,
                    name + " shortcut coming soon", Toast.LENGTH_SHORT).show());
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(dp(42), dp(42));
            utilities.addView(utility, up);
        }
        FrameLayout.LayoutParams utilLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.RIGHT);
        utilLp.topMargin = dp(22);
        utilLp.rightMargin = dp(35);
        root.addView(utilities, utilLp);
    }

    private void addHeroContent() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(0, 0, 0, 0);

        TextView eyebrow = text("A NEW WAY TO WATCH", 11,
                Color.rgb(210, 174, 145), true);
        eyebrow.setLetterSpacing(0.22f);
        hero.addView(eyebrow);

        TextView title = text("H O M E", 48, white, true);
        title.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.topMargin = dp(13);
        hero.addView(title, titleLp);

        TextView description = text(
                "Your entertainment, all in one place.\nPick something you love and settle in.",
                17, muted, false);
        description.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(dp(420), -2);
        descLp.topMargin = dp(10);
        hero.addView(description, descLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-2, -2);
        actionsLp.topMargin = dp(27);

        TextView explore = text("▶   Explore Apps", 15, Color.rgb(13, 17, 24), true);
        explore.setGravity(Gravity.CENTER);
        explore.setPadding(dp(22), 0, dp(22), 0);
        explore.setFocusable(true);
        explore.setBackground(roundRect(Color.WHITE, dp(30), Color.TRANSPARENT, 0));
        explore.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                    .setDuration(130).start();
        });
        explore.setOnClickListener(v -> focusApps());
        actions.addView(explore, new LinearLayout.LayoutParams(-2, dp(48)));

        TextView info = text("i", 20, white, true);
        info.setGravity(Gravity.CENTER);
        info.setBackground(roundRect(Color.argb(75, 255, 255, 255), dp(25),
                Color.argb(40, 255, 255, 255), dp(1)));
        info.setFocusable(true);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        infoLp.leftMargin = dp(15);
        actions.addView(info, infoLp);
        info.setOnClickListener(v -> Toast.makeText(this,
                "HomingZone — your TV, your space.", Toast.LENGTH_SHORT).show());
        hero.addView(actions, actionsLp);

        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(
                dp(500), dp(300), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        heroLp.leftMargin = dp(58);
        heroLp.topMargin = -dp(15);
        root.addView(hero, heroLp);
    }

    private void addPagination() {
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(i == 0 ? Color.WHITE : Color.argb(115, 225, 231, 241));
            shape.setCornerRadius(dp(8));
            dot.setBackground(shape);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(i == 0 ? 15 : 8), dp(8));
            lp.leftMargin = dp(5);
            lp.rightMargin = dp(5);
            dots.addView(dot, lp);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(18),
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lp.bottomMargin = dp(154);
        root.addView(dots, lp);
    }

    private void addAppDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(15), dp(12), dp(15), dp(12));
        dock.setBackground(roundRect(Color.argb(112, 29, 31, 40), dp(30),
                Color.argb(55, 255, 255, 255), dp(1)));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setFillViewport(false);

        LinearLayout appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER_VERTICAL);

        List<ResolveInfo> apps = getLaunchableApps();
        List<ResolveInfo> favourites = pickFavourites(apps);
        for (ResolveInfo info : favourites) {
            View tile = appTile(info);
            appRow.addView(tile);
            if (firstAppTile == null) firstAppTile = tile;
        }
        if (favourites.isEmpty()) {
            TextView empty = text("No launchable apps found", 14, white, false);
            appRow.addView(empty);
        }
        scroller.addView(appRow);
        dock.addView(scroller, new LinearLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(126),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.leftMargin = dp(34);
        dockLp.rightMargin = dp(34);
        dockLp.bottomMargin = dp(28);
        root.addView(dock, dockLp);
    }

    private List<ResolveInfo> getLaunchableApps() {
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        Intent normal = new Intent(Intent.ACTION_MAIN);
        normal.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : getPackageManager().queryIntentActivities(normal, 0)) {
            if (!getPackageName().equals(info.activityInfo.packageName))
                unique.put(info.activityInfo.packageName, info);
        }
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory("android.intent.category.LEANBACK_LAUNCHER");
        for (ResolveInfo info : getPackageManager().queryIntentActivities(leanback, 0)) {
            if (!getPackageName().equals(info.activityInfo.packageName))
                unique.put(info.activityInfo.packageName, info);
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

    private List<ResolveInfo> pickFavourites(List<ResolveInfo> all) {
        String[] preferred = {
                "com.netflix.ninja",
                "com.amazon.amazonvideo.livingroom",
                "com.google.android.youtube.tv",
                "org.videolan.vlc",
                "com.amazon.mp3",
                "com.disney.disneyplus"
        };
        List<ResolveInfo> picked = new ArrayList<>();
        for (String pkg : preferred) {
            for (ResolveInfo info : all) {
                if (pkg.equals(info.activityInfo.packageName)) {
                    picked.add(info);
                    break;
                }
            }
        }
        if (picked.isEmpty()) {
            for (int i = 0; i < Math.min(7, all.size()); i++) picked.add(all.get(i));
        }
        return picked;
    }

    private View appTile(final ResolveInfo info) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setPadding(dp(8), dp(7), dp(8), dp(7));
        tile.setBackground(roundRect(Color.argb(20, 255, 255, 255), dp(17),
                Color.argb(20, 255, 255, 255), dp(1)));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(61), dp(61)));

        TextView label = text(String.valueOf(info.loadLabel(getPackageManager())),
                10, Color.argb(225, 245, 247, 252), true);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                dp(126), dp(17));
        labelLp.topMargin = dp(4);
        tile.addView(label, labelLp);

        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(142), dp(102));
        tileLp.leftMargin = dp(6);
        tileLp.rightMargin = dp(6);
        tile.setLayoutParams(tileLp);

        tile.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.09f : 1f)
                    .scaleY(focused ? 1.09f : 1f).setDuration(130).start();
            view.setBackground(roundRect(
                    focused ? Color.argb(90, 220, 232, 250) : Color.argb(20, 255, 255, 255),
                    dp(17),
                    focused ? Color.argb(220, 255, 255, 255) : Color.argb(20, 255, 255, 255),
                    dp(focused ? 2 : 1)));
        });
        tile.setOnClickListener(view -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(
                    info.activityInfo.packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            }
        });
        return tile;
    }

    private void focusApps() {
        if (firstAppTile != null) {
            firstAppTile.requestFocus();
            firstAppTile.requestRectangleOnScreen(new android.graphics.Rect(
                    0, 0, firstAppTile.getWidth(), firstAppTile.getHeight()));
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
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
