package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Local-only launcher settings. */
public final class SettingsActivity extends Activity {

    private static final int PICK_WALLPAPER = 42;
    private static final String DEFAULT_WALLPAPER = "photo:tessellated-pavement.jpg";

    public static void prewarmCache(android.content.Context context) {
        // Settings has no remote cache or background service to prewarm.
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, float size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        return view;
    }

    private Button action(String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setOnClickListener(listener);
        return button;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(32), dp(48), dp(32));
        root.setBackgroundColor(Color.rgb(8, 8, 10));

        root.addView(label("HomingZone Launcher Settings", 24),
                new LinearLayout.LayoutParams(-1, dp(58)));
        root.addView(label("Wallpaper", 18),
                new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(action("Choose local image", v -> chooseWallpaper()),
                new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(action("Restore default background", v -> restoreWallpaper()),
                new LinearLayout.LayoutParams(-1, dp(54)));

        int appCount = installedLaunchableAppCount();
        root.addView(label("Installed launchable applications: " + appCount, 16),
                new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(label("About HomingZone\nClassic launcher with local settings and wallpaper storage.", 16),
                new LinearLayout.LayoutParams(-1, dp(76)));
        root.addView(label("Version " + versionName(), 14),
                new LinearLayout.LayoutParams(-1, dp(42)));

        setContentView(root);
        root.post(() -> {
            View first = root.getChildAt(2);
            if (first != null) first.requestFocus();
        });
    }

    private void chooseWallpaper() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        startActivityForResult(pick, PICK_WALLPAPER);
    }

    private void restoreWallpaper() {
        Profiles.homePrefs(this).edit().putString("wallpaper", DEFAULT_WALLPAPER).apply();
        Toast.makeText(this, "Default background restored", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(uri, flags);
            }
            Bitmap bitmap = CustomMedia.decodeImageOrIcoFromUri(this, uri, 1920, 1080);
            if (bitmap == null) {
                throw new IllegalArgumentException("Unsupported or unreadable image");
            }
            if (!CustomMedia.setCustomClassicWallpaper(this, bitmap)) {
                throw new IllegalStateException("Could not save wallpaper");
            }
            bitmap.recycle();
            Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.CUSTOM_WALL_KEY).apply();
            Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Wallpaper could not be applied", Toast.LENGTH_LONG).show();
        }
    }

    private int installedLaunchableAppCount() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<?> leanback = getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL);
        query.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<?> launcher = getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL);
        java.util.HashSet<String> packages = new java.util.HashSet<>();
        for (Object item : leanback) {
            if (item instanceof android.content.pm.ResolveInfo) {
                packages.add(((android.content.pm.ResolveInfo) item).activityInfo.packageName);
            }
        }
        for (Object item : launcher) {
            if (item instanceof android.content.pm.ResolveInfo) {
                packages.add(((android.content.pm.ResolveInfo) item).activityInfo.packageName);
            }
        }
        return packages.size();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
