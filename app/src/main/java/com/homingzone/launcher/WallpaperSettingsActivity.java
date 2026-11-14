package com.homingzone.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight classic-mode settings screen kept independent from the home screen. */
public final class WallpaperSettingsActivity extends Activity {
    private static final String PREFS = "wallpaper";
    private static final String URI_KEY = "uri";
    private static final int PICK_IMAGE = 71;
    private static final int PREVIEW_W = 640;
    private static final int PREVIEW_H = 360;
    private static final int BLACK = Color.rgb(6, 8, 13);

    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private ImageView preview;
    private TextView status;
    private Uri pendingUri;
    private Bitmap previewBitmap;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BLACK);
        buildUi();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(URI_KEY, "");
        if (!saved.isEmpty()) {
            pendingUri = Uri.parse(saved);
            loadPreview(pendingUri);
        }
    }

    @Override protected void onDestroy() {
        decoder.shutdownNow();
        if (previewBitmap != null) {
            previewBitmap.recycle();
            previewBitmap = null;
        }
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(21, 24, 34), BLACK});
        root.setBackground(background);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(34), dp(56), dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("Settings", 28, Color.WHITE, true);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView subtitle = text("HomingZone · Classic mode", 15, Color.argb(175, 255, 255, 255), false);
        content.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(30)));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(25, 29, 42), BLACK}));
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setColor(Color.TRANSPARENT);
        previewBg.setCornerRadius(dp(18));
        preview.setBackground(previewBg);
        preview.setClipToOutline(true);
        preview.setOutlineProvider(new ViewOutlineProviderCompat(dp(18)));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(202));
        previewLp.topMargin = dp(12);
        previewLp.bottomMargin = dp(18);
        content.addView(preview, previewLp);

        status = text("Default cinematic background", 14, Color.argb(190, 255, 255, 255), false);
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));

        content.addView(action("Choose wallpaper", v -> openPicker()));
        content.addView(action("Apply", v -> apply()));
        content.addView(action("Cancel", v -> finish()));
        content.addView(action("Restore default black background", v -> restoreDefault()));

        TextView apps = text("Installed applications", 18, Color.WHITE, true);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(-1, dp(32));
        sectionLp.topMargin = dp(20);
        content.addView(apps, sectionLp);
        AppCatalog catalog = new AppCatalog(this);
        List<AppCatalog.AppEntry> entries = catalog.load();
        content.addView(info("Launchable apps: " + entries.size()
                + "\nOnly installed, exported TV or regular launcher apps are shown; duplicates are removed."));

        TextView about = text("About HomingZone", 18, Color.WHITE, true);
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(-1, dp(32));
        aboutLp.topMargin = dp(18);
        content.addView(about, aboutLp);
        content.addView(info("HomingZone\nVersion " + version()
                + "\nA lightweight classic Android TV launcher."));

        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private TextView action(String label, View.OnClickListener listener) {
        TextView row = text(label, 16, Color.WHITE, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setBackground(rowBackground(false));
        row.setOnFocusChangeListener((v, focused) -> v.setBackground(rowBackground(focused)));
        row.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView info(String value) {
        TextView row = text(value, 14, Color.argb(180, 255, 255, 255), false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        row.setFocusable(false);
        return row;
    }

    private void openPicker() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/jpeg", "image/png", "image/webp"});
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(picker, PICK_IMAGE);
        } catch (RuntimeException e) {
            status.setText("Wallpaper picker unavailable");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // Some TV file providers grant a readable URI without persistable access.
        }
        pendingUri = uri;
        loadPreview(uri);
    }

    private void loadPreview(Uri uri) {
        status.setText("Loading wallpaper preview…");
        decoder.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decode(uri);
            } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
                // Keep the current preview and report the problem on the UI thread.
            }
            final Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (isFinishing()) {
                    if (result != null) result.recycle();
                    return;
                }
                if (result == null) {
                    status.setText("Wallpaper unavailable. Choose another image or restore default.");
                    return;
                }
                if (previewBitmap != null) previewBitmap.recycle();
                previewBitmap = result;
                preview.setImageBitmap(result);
                status.setText("Preview ready. Press Apply to use this wallpaper.");
            });
        });
    }

    private Bitmap decode(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        decodeStream(uri, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= PREVIEW_W
                && bounds.outHeight / (sample * 2) >= PREVIEW_H) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return decodeStream(uri, options);
    }

    private Bitmap decodeStream(Uri uri, BitmapFactory.Options options) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        }
    }

    private void apply() {
        if (pendingUri == null || previewBitmap == null) {
            status.setText("Choose a valid wallpaper before applying.");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(URI_KEY, pendingUri.toString()).apply();
        setResult(RESULT_OK);
        finish();
    }

    private void restoreDefault() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(URI_KEY).apply();
        setResult(RESULT_OK);
        finish();
    }

    private TextView text(String value, float size, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(assetTypeface(medium ? "Inter-SemiBold.ttf" : "Inter-Regular.ttf",
                Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)));
        return view;
    }

    private Typeface assetTypeface(String name, Typeface fallback) {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/" + name);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private GradientDrawable rowBackground(boolean focused) {
        GradientDrawable row = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                focused
                        ? new int[]{Color.rgb(250, 250, 252), Color.rgb(212, 216, 226)}
                        : new int[]{Color.argb(120, 71, 77, 95), Color.argb(70, 38, 42, 55)});
        row.setCornerRadius(dp(14));
        return row;
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.1.0";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private static final class ViewOutlineProviderCompat extends android.view.ViewOutlineProvider {
        private final int radius;

        ViewOutlineProviderCompat(int radius) {
            this.radius = radius;
        }

        @Override public void getOutline(View view, android.graphics.Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    }
}
