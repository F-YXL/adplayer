package io.github.fyxl.adplayer;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    public static final String NAME = "adplayer";

    public static final String KEY_LAYOUT = "layout";
    public static final String KEY_FOLDER_1 = "folder1";
    public static final String KEY_FOLDER_2 = "folder2";
    public static final String KEY_FOLDER_3 = "folder3";
    public static final String KEY_FOLDER_4 = "folder4";
    public static final String KEY_SECONDS = "image_seconds";
    public static final String KEY_SHUFFLE = "shuffle";
    public static final String KEY_MUTE = "mute";
    public static final String KEY_BOOT = "boot";
    public static final String KEY_REBOOT = "reboot_time";
    public static final String KEY_SCREEN_OFF = "screen_off_time";
    public static final String KEY_SCREEN_ON = "screen_on_time";
    public static final String KEY_PIN = "pin";
    public static final String KEY_POWER_ON = "power_on_time";
    public static final String KEY_POWER_OFF = "power_off_time";
    public static final String KEY_GUARDIAN = "guardian_seconds";
    public static final String KEY_LOOP = "loop_mode";

    public static final String DEFAULT_FOLDER = "/mnt/sdcard/广告素材";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int layout(Context c) {
        return get(c).getInt(KEY_LAYOUT, 0);
    }

    public static String folder(Context c, int panel) {
        String v = get(c).getString("folder" + panel, "");
        if (v == null || v.length() == 0) {
            if (panel == 1) {
                // 兼容旧版本（v1）保存过的 folder 键
                String legacy = get(c).getString("folder", "");
                if (legacy != null && legacy.length() > 0) return legacy;
                return DEFAULT_FOLDER;
            }
            return folder(c, 1);
        }
        return v;
    }

    public static int seconds(Context c) {
        return get(c).getInt(KEY_SECONDS, 5);
    }

    public static boolean shuffle(Context c) {
        return get(c).getBoolean(KEY_SHUFFLE, false);
    }

    public static boolean mute(Context c) {
        return get(c).getBoolean(KEY_MUTE, true);
    }

    public static boolean boot(Context c) {
        return get(c).getBoolean(KEY_BOOT, true);
    }

    public static String rebootTime(Context c) {
        return get(c).getString(KEY_REBOOT, "");
    }

    public static String screenOffTime(Context c) {
        return get(c).getString(KEY_SCREEN_OFF, "");
    }

    public static String screenOnTime(Context c) {
        return get(c).getString(KEY_SCREEN_ON, "");
    }

    public static String powerOnTime(Context c) {
        return get(c).getString(KEY_POWER_ON, "");
    }

    public static String powerOffTime(Context c) {
        return get(c).getString(KEY_POWER_OFF, "");
    }

    public static int guardianSeconds(Context c) {
        return get(c).getInt(KEY_GUARDIAN, 300);
    }

    /** 0 = 列表循环，1 = 单文件循环 */
    public static int loopMode(Context c) {
        return get(c).getInt(KEY_LOOP, 0);
    }

    public static String pin(Context c) {
        return get(c).getString(KEY_PIN, "");
    }
}
