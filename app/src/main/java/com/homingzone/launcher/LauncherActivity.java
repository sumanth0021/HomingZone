package com.homingzone.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LauncherActivity extends Activity {
    private final int bg = Color.rgb(10, 13, 22);
    private final int white = Color.rgb(245, 247, 252);
    private final int muted = Color.rgb(163, 173, 190);
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10, 13, 22));
        getWindow().setNavigationBarColor(Color.rgb(10, 13, 22));
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) buildScreen();
    }

    private void buildScreen() {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(24, 35, 57), Color.rgb(12, 17, 29), bg}));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(30), dp(24), dp(36));
        page.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = text("HomingZone", 15, white, true);
        brand.setLetterSpacing(0.18f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        String date = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(new Date());
        TextView dateView = text(date, 13, muted, false);
        top.addView(dateView);
        content.addView(top);

        addSpace(content, 38);
        TextView greeting = text(greeting(), 34, white, true);
        content.addView(greeting);
        TextView subtitle = text("Everything you watch, in one calm place.", 16, muted, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = dp(8);
        content.addView(subtitle, subLp);

        addSpace(content, 34);
        List<ResolveInfo> apps = getLaunchableApps();
        addAppRow("YOUR APPS", pickApps(apps, new String[]{
                "com.netflix.ninja", "com.google.android.youtube.tv",
                "com.amazon.amazonvideo.livingroom", "org.videolan.vlc",
                "com.amazon.mp3"}));
        addSpace(content, 28);
        addAppRow("ALL APPS", apps);

        addSpace(content, 24);
        TextView footer = text("Use your remote to explore  ·  Select an app to open", 12, muted, false);
        content.addView(footer);

        setContentView(page);
        if (content.getChildCount() > 0) {
            View first = findFirstFocusable(content);
            if (first != null) first.requestFocus();
        }
    }

    private List<ResolveInfo> getLaunchableApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> result = getPackageManager().queryIntentActivities(intent, 0);
        Collections.sort(result, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.CASE_INSENSITIVE_ORDER.compare(
                        String.valueOf(a.loadLabel(getPackageManager())),
                        String.valueOf(b.loadLabel(getPackageManager())));
            }
        });
        List<ResolveInfo> filtered = new ArrayList<>();
        for (ResolveInfo info : result) {
            if (!getPackageName().equals(info.activityInfo.packageName)) filtered.add(info);
        }
        return filtered;
    }

    private List<ResolveInfo> pickApps(List<ResolveInfo> all, String[] packages) {
        List<ResolveInfo> picked = new ArrayList<>();
        for (String pkg : packages) {
            for (ResolveInfo info : all) {
                if (pkg.equals(info.activityInfo.packageName)) {
                    picked.add(info);
                    break;
                }
            }
        }
        return picked;
    }

    private void addAppRow(String title, List<ResolveInfo> apps) {
        TextView heading = text(title, 13, muted, true);
        heading.setLetterSpacing(0.14f);
        content.addView(heading);

        if (apps.isEmpty()) {
            TextView empty = text("No apps found in this row yet.", 14, muted, false);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
            ep.topMargin = dp(12);
            content.addView(empty, ep);
            return;
        }

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(2), dp(14), dp(18), dp(14));

        for (ResolveInfo info : apps) row.addView(appTile(info));
        horizontal.addView(row);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        content.addView(horizontal, hp);
    }

    private View appTile(final ResolveInfo info) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setPadding(dp(12), dp(12), dp(12), dp(10));
        tile.setBackground(roundRect(Color.argb(35, 255, 255, 255), dp(16),
                Color.argb(35, 255, 255, 255), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(148), dp(126));
        lp.rightMargin = dp(12);
        tile.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView name = text(String.valueOf(info.loadLabel(getPackageManager())), 13, white, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, -2);
        nameLp.topMargin = dp(9);
        tile.addView(name, nameLp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.animate().scaleX(focused ? 1.06f : 1f)
                        .scaleY(focused ? 1.06f : 1f).setDuration(120).start();
                view.setBackground(roundRect(
                        focused ? Color.rgb(65, 83, 116) : Color.argb(35, 255, 255, 255),
                        dp(16),
                        focused ? Color.rgb(190, 207, 239) : Color.argb(35, 255, 255, 255),
                        focused ? dp(2) : dp(1)));
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(info.activityInfo.packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                }
            }
        });
        return tile;
    }

    private View findFirstFocusable(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.isFocusable()) return child;
            if (child instanceof ViewGroup) {
                View found = findFirstFocusable((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning.";
        if (hour < 17) return "Good afternoon.";
        return "Good evening.";
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
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
