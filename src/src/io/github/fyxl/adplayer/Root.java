package io.github.fyxl.adplayer;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;

public class Root {

    private static final String TAG = "AdPlayer";

    public static void exec(String cmd) {
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        } catch (Exception e) {
            Log.w(TAG, "root exec failed: " + cmd + " " + e);
        }
    }

    /**
     * 把守护间隔写进厂商守护进程的配置，并重启守护服务。
     *
     * 下面的 com.guardian、u0_a45、ETV_TIMER_DEAL.xml 是参考机型（RK3128 广告机）
     * 出厂固件里的常量。换机型时：用 pm list packages 找到实际守护包名，
     * 用 ls -l /data/data/<包名> 找到 uid，再改这里。
     */
    public static void applyGuardianInterval(Context ctx, int seconds) {
        String xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                + "<map>\n"
                + "    <boolean name=\"openPower\" value=\"true\" />\n"
                + "    <string name=\"packageNameGuardian\">" + ctx.getPackageName() + "</string>\n"
                + "    <int name=\"LanguageDesc\" value=\"0\" />\n"
                + "    <int name=\"checkTime\" value=\"" + (seconds * 1000) + "\" />\n"
                + "    <int name=\"ScreenHeight\" value=\"1080\" />\n"
                + "    <int name=\"ScreenWidth\" value=\"1920\" />\n"
                + "    <boolean name=\"isOpen\" value=\"true\" />\n"
                + "</map>\n";
        try {
            File f = new File(ctx.getFilesDir(), "guardian.xml");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(xml.getBytes("UTF-8"));
            fos.close();
            String path = f.getAbsolutePath();
            exec("cp " + path + " /data/data/com.guardian/shared_prefs/ETV_TIMER_DEAL.xml"
                    + " && chown u0_a45:u0_a45 /data/data/com.guardian/shared_prefs/ETV_TIMER_DEAL.xml"
                    + " && chmod 660 /data/data/com.guardian/shared_prefs/ETV_TIMER_DEAL.xml"
                    + " && am force-stop com.guardian"
                    + " && am startservice -n com.guardian/.service.GuardianService");
        } catch (Exception e) {
            Log.w(TAG, "applyGuardianInterval failed " + e);
        }
    }

    /** 设置 RTC 唤醒时间（epoch 秒） */
    public static void setWakeAlarm(long epochSeconds) {
        exec("echo 0 > /sys/class/rtc/rtc0/wakealarm; echo " + epochSeconds
                + " > /sys/class/rtc/rtc0/wakealarm");
    }

    public static void powerOff() {
        exec("reboot -p");
    }

    public static void reboot() {
        exec("reboot");
    }

    /** 把 "HH:mm" 换算成“下一次到达”的 epoch 秒，格式不对返回 0 */
    public static long nextEpoch(String hhmm) {
        if (hhmm == null) return 0;
        String t = hhmm.trim();
        if (t.length() < 4 || t.indexOf(':') < 0) return 0;
        try {
            String[] p = t.split(":");
            int h = Integer.parseInt(p[0].trim());
            int m = Integer.parseInt(p[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return 0;
            Calendar c = Calendar.getInstance();
            Calendar target = Calendar.getInstance();
            target.set(Calendar.HOUR_OF_DAY, h);
            target.set(Calendar.MINUTE, m);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);
            if (!target.after(c)) {
                target.add(Calendar.DAY_OF_MONTH, 1);
            }
            return target.getTimeInMillis() / 1000;
        } catch (Exception e) {
            return 0;
        }
    }
}
