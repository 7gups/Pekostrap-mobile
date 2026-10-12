package com.voidstrap.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.core.content.pm.PackageInfoCompat;

import java.util.ArrayList;
import java.util.List;

public final class Targets {
    public static final String PEKORA = "pro.pekora.client";
    public static final String DOWNLOAD = "https://pekora.pro/setup/android/pekora.apk";
    public static final String PEKORA_2018 = "com.roblox.clienr";
    public static final String[] ALL = {PEKORA, PEKORA_2018};
    public static final String DOWNLOAD_2018 = "https://pekora.pro";

    public static final class State {
        public String pkg;
        public boolean installed;
        public boolean enabled;
        public boolean launchable;
        public String versionName;
        public long versionCode;
        public Drawable icon;

        public boolean ready() {
            return installed && enabled && launchable;
        }
    }

    public static boolean known(String pkg) {
        for (String p : ALL) if (p.equals(pkg)) return true;
        return false;
    }

    public static int nameRes(String pkg) {
        return PEKORA_2018.equals(pkg) ? R.string.target_pekora_2018 : R.string.target_pekora;
    }

    public static String downloadUrl(String pkg) {
        return PEKORA_2018.equals(pkg) ? DOWNLOAD_2018 : DOWNLOAD;
    }

    public static State resolve(Context c, String pkg) {
        State s = new State();
        s.pkg = pkg;
        PackageManager pm = c.getPackageManager();
        try {
            PackageInfo info = Build.VERSION.SDK_INT >= 33
                    ? pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                    : pm.getPackageInfo(pkg, 0);
            ApplicationInfo ai = info.applicationInfo;
            s.installed = true;
            s.enabled = ai == null || ai.enabled;
            s.versionName = info.versionName;
            s.versionCode = PackageInfoCompat.getLongVersionCode(info);
            s.launchable = pm.getLaunchIntentForPackage(pkg) != null;
            if (ai != null) s.icon = ai.loadIcon(pm);
        } catch (PackageManager.NameNotFoundException ignored) {
            s.installed = false;
        }
        return s;
    }

    public static String selected(Context c) {
        String p = Store.get(c).setting("target", PEKORA);
        return known(p) ? p : PEKORA;
    }

    public static List<String> offered(Context c) {
        List<String> out = new ArrayList<>();
        for (String p : ALL) out.add(p);
        return out;
    }
}
