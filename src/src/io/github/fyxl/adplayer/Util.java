package io.github.fyxl.adplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Util {

    public static boolean isMedia(String name) {
        String n = name.toLowerCase();
        int dot = n.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = n.substring(dot + 1);
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp")
                || ext.equals("webp") || ext.equals("gif")
                || ext.equals("mp4") || ext.equals("3gp") || ext.equals("mkv") || ext.equals("avi")
                || ext.equals("webm") || ext.equals("mov") || ext.equals("ts") || ext.equals("m4v")
                || ext.equals("flv") || ext.equals("wmv") || ext.equals("mpg") || ext.equals("mpeg");
    }

    public static boolean isImage(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".bmp") || n.endsWith(".webp") || n.endsWith(".gif");
    }

    public static List<File> scan(String folder, boolean shuffle) {
        List<File> out = new ArrayList<File>();
        File dir = new File(folder);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        scanDir(dir, 0, out);
        if (shuffle) {
            Collections.shuffle(out, new Random(System.currentTimeMillis()));
        } else {
            Collections.sort(out, new Comparator<File>() {
                public int compare(File a, File b) {
                    return naturalCompare(a.getName(), b.getName());
                }
            });
        }
        return out;
    }

    private static void scanDir(File dir, int depth, List<File> out) {
        if (dir == null || depth > 3) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (f.getName().startsWith(".")) continue;
                scanDir(f, depth + 1, out);
            } else if (isMedia(f.getName())) {
                out.add(f);
            }
        }
    }

    /** 把根目录里散落的媒体文件按类型归到 照片/ 视频/ 子目录，返回移动数量 */
    public static int organize(String folder) {
        File root = new File(folder);
        if (!root.isDirectory()) return 0;
        File imgDir = new File(root, "照片");
        File vidDir = new File(root, "视频");
        File[] files = root.listFiles();
        if (files == null) return 0;
        int moved = 0;
        for (File f : files) {
            if (!f.isFile() || !isMedia(f.getName())) continue;
            File dir = isImage(f.getName()) ? imgDir : vidDir;
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, f.getName());
            if (dest.exists()) continue;
            if (f.renameTo(dest)) {
                moved++;
            } else {
                try {
                    java.io.FileInputStream in = new java.io.FileInputStream(f);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    out.close();
                    if (f.delete()) moved++;
                } catch (Exception ignored) {
                }
            }
        }
        return moved;
    }

    public static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (si < a.length() && Character.isDigit(a.charAt(si))) si++;
                while (sj < b.length() && Character.isDigit(b.charAt(sj))) sj++;
                int la = si - i, lb = sj - j;
                if (la != lb) return la < lb ? -1 : 1;   // 位数不同，短的更小；不解析整数，避免溢出
                int cmp = a.substring(i, si).compareTo(b.substring(j, sj));
                if (cmp != 0) return cmp < 0 ? -1 : 1;
                i = si;
                j = sj;
            } else {
                if (ca != cb) return ca - cb;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }
}
