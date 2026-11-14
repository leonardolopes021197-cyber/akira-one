package com.akira.one;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

/**
 * Compatibilidade com IDEs Android que não geram BuildConfig corretamente
 * (por exemplo, algumas versões/configurações do CodeAssist).
 */
public final class AppVersion {
    private AppVersion() {}

    public static String name(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    public static long code(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
