package com.akira.one;

import android.content.Context;
import android.provider.Settings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class DeviceIdentity {
    private DeviceIdentity() {}

    public static String get(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isBlank()) androidId = "akira-unknown-device";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(androidId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format(Locale.US, "%02X", hash[i]));
            String raw = sb.toString();
            return raw.substring(0,4) + "-" + raw.substring(4,8) + "-" + raw.substring(8,12);
        } catch (Exception e) {
            return androidId.toUpperCase(Locale.US);
        }
    }
}
