package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** TV friendly home settings with a visual wallpaper picker. */
public final class SettingsActivity extends Activity {

    private static final int REQ_WALLPAPER_STORAGE = 43;
    private static final String DEFAULT_WALLPAPER = "photo:tessellated-pavement.jpg";
    private final java.util.ArrayList<View> presetCards = new java.util.ArrayList<>();
    private String selectedWallpaper;
    private ImageView heroPreview;
    private TextView selectedTitle;

    public static void prewarmCache(android.content.Context context) {
        // The settings screen uses local assets only; there is no remote cache to prewarm.
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable shape(int color, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(null);
        selectedWallpaper = Profiles.homePrefs(this).getString("wallpaper", DEFAULT_WALLPAPER);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF090A0E);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(28), dp(56), dp(36));
        scroll.addView(page);

        TextView heading = label("Home settings", 28, Color.WHITE);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        page.addView(heading, new LinearLayout.LayoutParams(-1, dp(42)));
        page.addView(label("Choose a wallpaper", 17, 0xFFB8BBC7), new LinearLayout.LayoutParams(-1, dp(30)));

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(shape(0xFF171921, 0xFF30333D, 1, 18));
        hero.setClipToOutline(true);
        heroPreview = new ImageView(this);
        heroPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(heroPreview, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x00000000}));
        hero.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(22), 0, dp(22), dp(18));
        caption.setGravity(Gravity.BOTTOM);
        selectedTitle = label("", 21, Color.WHITE);
        selectedTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        caption.addView(selectedTitle, new LinearLayout.LayoutParams(-1, dp(32)));
        caption.addView(label("Preview of your home screen background", 13, 0xFFD0D2D9));
        hero.addView(caption, new FrameLayout.LayoutParams(-1, -1));
        page.addView(hero, new LinearLayout.LayoutParams(-1, dp(250)));
        updateHero();

        TextView presetsTitle = label("Built-in wallpapers", 18, Color.WHITE);
        presetsTitle.setPadding(0, dp(18), 0, dp(9));
        page.addView(presetsTitle);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setUseDefaultMargins(false);
        page.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < MainActivity.WALL_KEYS.length; i++) {
            final int index = i;
            View card = makePresetCard(i);
            presetCards.add(card);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(150);
            lp.columnSpec = GridLayout.spec(i % 5, 1f);
            lp.rowSpec = GridLayout.spec(i / 5);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(card, lp);
            card.setOnClickListener(v -> applyWallpaper(MainActivity.WALL_KEYS[index]));
        }
        refreshSelection();

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(18), 0, 0);
        Button local = action("Choose a photo…", v -> chooseWallpaper());
        actions.addView(local, new LinearLayout.LayoutParams(dp(220), dp(54)));
        Button reset = action("Restore default", v -> applyWallpaper(DEFAULT_WALLPAPER));
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(dp(200), dp(54));
        resetLp.leftMargin = dp(12);
        actions.addView(reset, resetLp);
        page.addView(actions);
        page.addView(label("Use the directional pad to browse. Choose a photo to use an image from your TV or USB drive.",
                13, 0xFF9094A1), new LinearLayout.LayoutParams(-1, dp(42)));

        setContentView(scroll);
        page.post(() -> {
            View selected = null;
            for (View card : presetCards) if (Boolean.TRUE.equals(card.getTag())) selected = card;
            (selected != null ? selected : local).requestFocus();
        });
    }

    private View makePresetCard(int index) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setBackground(shape(0xFF1A1C24, 0xFF343741, 1, 13));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(previewBitmap(MainActivity.WALL_KEYS[index]));
        card.addView(image, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x00000000}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        TextView name = label(MainActivity.wallName(this, index), 13, Color.WHITE);
        name.setPadding(dp(12), 0, dp(8), dp(11));
        name.setGravity(Gravity.BOTTOM | Gravity.START);
        card.addView(name, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f)
                    .setDuration(150).start();
            v.setElevation(focused ? dp(10) : 0);
            updateCardBorder((FrameLayout) v, Boolean.TRUE.equals(v.getTag()), focused);
        });
        return card;
    }

    private void updateCardBorder(FrameLayout card, boolean selected, boolean focused) {
        card.setBackground(shape(0xFF1A1C24, selected || focused ? 0xFFE8EAF2 : 0xFF343741,
                selected || focused ? 2 : 1, 13));
    }

    private Bitmap previewBitmap(String key) {
        if (key != null && key.startsWith("photo:")) {
            return Scenes.loadAssetBitmap(this, "backgrounds/" + key.substring(6), 480, 270);
        }
        return MainActivity.makeWallpaperBase(key == null ? "aurora" : key);
    }

    private Bitmap currentPreview() {
        if (MainActivity.CUSTOM_WALL_KEY.equals(selectedWallpaper)
                || CustomMedia.isCustomWallpaperEnabled(this, CustomMedia.SCOPE_CLASSIC)) {
            Bitmap custom = CustomMedia.getCustomClassicWallpaper(this);
            if (custom != null) return custom;
        }
        Bitmap preview = previewBitmap(selectedWallpaper);
        return preview != null ? preview : previewBitmap("aurora");
    }

    private void updateHero() {
        Bitmap bitmap = currentPreview();
        if (bitmap != null) heroPreview.setImageBitmap(bitmap);
        int index = java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(selectedWallpaper);
        selectedTitle.setText(index >= 0 ? MainActivity.wallName(this, index)
                : (MainActivity.CUSTOM_WALL_KEY.equals(selectedWallpaper) ? "Your photo" : "Wallpaper"));
    }

    private void refreshSelection() {
        for (int i = 0; i < presetCards.size(); i++) {
            View card = presetCards.get(i);
            boolean selected = MainActivity.WALL_KEYS[i].equals(selectedWallpaper);
            card.setTag(selected);
            updateCardBorder((FrameLayout) card, selected, card.hasFocus());
        }
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

    private void applyWallpaper(String key) {
        CustomMedia.setCustomWallpaperEnabled(this, CustomMedia.SCOPE_CLASSIC, false);
        selectedWallpaper = key;
        Profiles.homePrefs(this).edit().putString("wallpaper", key).apply();
        updateHero();
        refreshSelection();
        Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void chooseWallpaper() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_IMAGES"
                : "android.permission.READ_EXTERNAL_STORAGE";
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQ_WALLPAPER_STORAGE);
            return;
        }
        showWallpaperBrowser();
    }

    private void showWallpaperBrowser() {
        CustomMedia.showImageFilePicker(this, "Choose wallpaper", false, 1920, 1080,
                null, null, this::applyPickedWallpaper);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WALLPAPER_STORAGE) {
            // The browser also offers Android's picker as a fallback if access is declined.
            showWallpaperBrowser();
        }
    }

    private void applyPickedWallpaper(Bitmap bitmap) {
        try {
            if (bitmap == null) throw new IllegalArgumentException("Unsupported or unreadable image");
            if (!CustomMedia.setCustomClassicWallpaper(this, bitmap)) throw new IllegalStateException("Could not save wallpaper");
            CustomMedia.setCustomWallpaperEnabled(this, CustomMedia.SCOPE_CLASSIC, true);
            selectedWallpaper = MainActivity.CUSTOM_WALL_KEY;
            Profiles.homePrefs(this).edit().putString("wallpaper", selectedWallpaper).apply();
            Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Wallpaper could not be applied", Toast.LENGTH_LONG).show();
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        CustomMedia.onActivityResult(requestCode, resultCode, data);
    }

    private int installedLaunchableAppCount() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<?> leanback = getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL);
        query.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<?> launcher = getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL);
        java.util.HashSet<String> packages = new java.util.HashSet<>();
        for (Object item : leanback) if (item instanceof android.content.pm.ResolveInfo) packages.add(((android.content.pm.ResolveInfo) item).activityInfo.packageName);
        for (Object item : launcher) if (item instanceof android.content.pm.ResolveInfo) packages.add(((android.content.pm.ResolveInfo) item).activityInfo.packageName);
        return packages.size();
    }
}
