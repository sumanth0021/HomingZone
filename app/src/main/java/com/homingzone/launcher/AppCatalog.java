package com.homingzone.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which apps appear in the dock and keeps ONE cached {@link AppEntry} per app.
 *
 * Rules, in plain words:
 *  1. Apps that declare a TV (Leanback) launcher entry are always listed. This is what Google TV lists too.
 *  2. Apps that only have a normal phone-style launcher entry are listed unless they are preinstalled
 *     system apps that were never updated (those are mostly service screens nobody wants in a dock).
 *  3. One entry per app. If an app has several launcher activities, the TV one wins.
 *  4. HomingZone itself, the original Google TV home, and anything disabled or not launchable are skipped.
 *
 * All methods must be called on the main thread (nothing here is thread-safe on purpose; it keeps the code simple).
 */
final class AppCatalog {
    private static final String TAG = "HomingZone";

    /** Packages that never appear in the dock (HomingZone itself is excluded separately). Tune here. */
    private static final Set<String> HIDDEN_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.tvlauncher"));

    private final PackageManager pm;
    private final String ownPackage;
    /** package name -> entry. Reused between refreshes so labels and artwork are loaded only once per app. */
    private final Map<String, AppEntry> cache = new HashMap<>();

    AppCatalog(Context context) {
        Context app = context.getApplicationContext();
        pm = app.getPackageManager();
        ownPackage = app.getPackageName();
    }

    /** Forget one app's cached label and artwork (call when it was added, removed or updated). */
    void invalidate(String packageName) {
        if (packageName != null) cache.remove(packageName);
    }

    /**
     * Returns the apps to show, sorted by name. Unchanged apps come back as the SAME AppEntry object as last time,
     * so the caller can compare two results with List.equals() to learn whether anything changed.
     */
    List<AppEntry> load() {
        // package name -> the activity we will launch. LinkedHashMap keeps one entry per package.
        Map<String, ResolveInfo> chosen = new LinkedHashMap<>();
        Set<String> leanbackPackages = new HashSet<>();

        for (ResolveInfo ri : query(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            if (!isListable(ri)) continue;
            String pkg = ri.activityInfo.packageName;
            if (chosen.containsKey(pkg)) continue;
            chosen.put(pkg, ri);
            leanbackPackages.add(pkg);
        }
        for (ResolveInfo ri : query(Intent.CATEGORY_LAUNCHER)) {
            if (!isListable(ri) || isPlainSystemApp(ri)) continue;
            String pkg = ri.activityInfo.packageName;
            if (!chosen.containsKey(pkg)) chosen.put(pkg, ri);   // never replaces the TV activity chosen above
        }

        List<AppEntry> result = new ArrayList<>(chosen.size());
        for (Map.Entry<String, ResolveInfo> e : chosen.entrySet()) {
            String pkg = e.getKey();
            ResolveInfo ri = e.getValue();
            boolean leanback = leanbackPackages.contains(pkg);
            ComponentName component = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);

            AppEntry entry = cache.get(pkg);
            if (entry == null || !entry.component.equals(component) || entry.leanback != leanback) {
                entry = new AppEntry(pm, ri, leanback, component);
                cache.put(pkg, entry);
            }
            result.add(entry);
        }
        cache.keySet().retainAll(chosen.keySet());   // drop uninstalled apps so their artwork can be freed

        Collections.sort(result, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });
        return result;
    }

    private List<ResolveInfo> query(String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(intent, 0);
        return found != null ? found : Collections.<ResolveInfo>emptyList();
    }

    /** Safe to show and safe to launch: enabled, exported (otherwise startActivity would throw), not hidden. */
    private boolean isListable(ResolveInfo ri) {
        if (ri == null || ri.activityInfo == null || ri.activityInfo.applicationInfo == null) return false;
        ActivityInfo ai = ri.activityInfo;
        return ai.enabled && ai.exported && ai.applicationInfo.enabled
                && !ownPackage.equals(ai.packageName)
                && !HIDDEN_PACKAGES.contains(ai.packageName);
    }

    /** A preinstalled system app that has never been updated (an updated one, like Play Store, still counts as a real app). */
    private boolean isPlainSystemApp(ResolveInfo ri) {
        int flags = ri.activityInfo.applicationInfo.flags;
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
    }

    /** One launchable app: its name is read once, its artwork is loaded the first time it is needed, then kept. */
    static final class AppEntry {
        final String packageName;
        final String label;
        final boolean leanback;
        final ComponentName component;

        private final PackageManager pm;
        private final ResolveInfo info;
        private Drawable banner, icon;
        private boolean bannerLoaded;

        AppEntry(PackageManager pm, ResolveInfo info, boolean leanback, ComponentName component) {
            this.pm = pm;
            this.info = info;
            this.leanback = leanback;
            this.component = component;
            this.packageName = info.activityInfo.packageName;
            CharSequence name = null;
            try {
                name = info.loadLabel(pm);
            } catch (RuntimeException e) {
                Log.w(TAG, "No label for " + packageName, e);
            }
            this.label = name != null ? name.toString() : packageName;
        }

        /** Launches exactly the activity we discovered (works for TV-only apps, unlike a package-name lookup). */
        Intent launchIntent() {
            return new Intent(Intent.ACTION_MAIN)
                    .addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }

        /** The wide TV banner, or null when the app has none. Each call returns a fresh Drawable sharing the same pixels. */
        Drawable newBanner() {
            if (!bannerLoaded) {
                bannerLoaded = true;
                try {
                    Drawable d = info.activityInfo.loadBanner(pm);
                    if (d == null) d = info.activityInfo.applicationInfo.loadBanner(pm);
                    banner = d;
                } catch (RuntimeException e) {
                    Log.w(TAG, "Banner failed for " + packageName, e);
                }
            }
            return independentCopy(banner);
        }

        /** The app icon (never null). Each call returns a fresh Drawable sharing the same pixels. */
        Drawable newIcon() {
            if (icon == null) {
                try {
                    icon = info.loadIcon(pm);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Icon failed for " + packageName, e);
                }
                if (icon == null) icon = pm.getDefaultActivityIcon();
            }
            return independentCopy(icon);
        }

        /**
         * Two ImageViews must never share one Drawable object: a Drawable has a single size and a single owner,
         * so the second view would resize the first one's picture. A copy made from the Drawable's ConstantState
         * is a cheap wrapper around the same bitmap, so this costs no extra memory for the pixels.
         */
        private static Drawable independentCopy(Drawable d) {
            if (d == null) return null;
            Drawable.ConstantState state = d.getConstantState();
            return state != null ? state.newDrawable() : d;
        }
    }
}
