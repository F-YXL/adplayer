package io.github.fyxl.adplayer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 极简 HTTP 服务器：手机浏览器打开 http://设备IP:8080
 * 支持：上传（可传整个相册多选）、列表（带缩略图）、删除、存储信息。
 */
public class HttpUploadServer {

    private static final String TAG = "AdPlayer";
    public static final int PORT = 8080;

    private final File root;
    private final File cacheDir;
    public static final String FFMPEG = "/data/local/tmp/ffmpeg";
    private static final java.util.concurrent.ConcurrentHashMap<String, String> JOBS =
            new java.util.concurrent.ConcurrentHashMap<String, String>();
    private ServerSocket server;
    private volatile boolean running;
    private Runnable onChanged;

    public HttpUploadServer(File dir, File thumbs) {
        this.root = dir;
        this.cacheDir = thumbs;
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    public void setOnChanged(Runnable r) {
        this.onChanged = r;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    server = new ServerSocket();
                    server.setReuseAddress(true);
                    server.bind(new InetSocketAddress(PORT), 20);
                } catch (IOException e) {
                    Log.w(TAG, "http server bind failed: " + e);
                    running = false;
                    return;
                }
                Log.i(TAG, "http server listening on " + PORT + " root=" + root);
                while (running) {
                    try {
                        final Socket s = server.accept();
                        new Thread(new Runnable() {
                            public void run() {
                                handle(s);
                            }
                        }).start();
                    } catch (IOException e) {
                        if (running) Log.w(TAG, "accept failed " + e);
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
    }

    public static String localIp() {
        try {
            List<NetworkInterface> nis = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : nis) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private void handle(Socket socket) {
        InputStream in = null;
        OutputStream out = null;
        try {
            socket.setSoTimeout(60000);
            in = new BufferedInputStream(socket.getInputStream(), 65536);
            out = socket.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];

            int contentLength = -1;
            String hostHeader = null, originHeader = null;
            String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim().toLowerCase();
                    String value = line.substring(idx + 1).trim();
                    if ("content-length".equals(key)) {
                        contentLength = Integer.parseInt(value);
                    } else if ("host".equals(key)) {
                        hostHeader = value;
                    } else if ("origin".equals(key)) {
                        originHeader = value;
                    }
                }
            }

            // 写操作只接受同源页面发起的请求，挡住跨站伪造（CSRF）与 DNS rebinding
            if ("POST".equals(method) && originHeader != null && !sameAuthority(originHeader, hostHeader)) {
                send(out, 403, "text/plain; charset=utf-8", "cross-origin blocked");
                return;
            }

            String query = path.indexOf('?') >= 0 ? path.substring(path.indexOf('?') + 1) : "";
            String pathOnly = path.indexOf('?') >= 0 ? path.substring(0, path.indexOf('?')) : path;

            if ("GET".equals(method) && (pathOnly.equals("/") || pathOnly.startsWith("/index"))) {
                send(out, 200, "text/html; charset=utf-8", page());
            } else if ("GET".equals(method) && pathOnly.startsWith("/list")) {
                send(out, 200, "application/json; charset=utf-8", listJson());
            } else if ("GET".equals(method) && pathOnly.startsWith("/thumb")) {
                byte[] thumb = thumb(param(query, "path"));
                if (thumb == null) {
                    send(out, 404, "text/plain; charset=utf-8", "no thumb");
                } else {
                    writeBytes(out, 200, "image/jpeg", thumb);
                }
            } else if ("POST".equals(method) && pathOnly.startsWith("/transcode")) {
                File tf = resolveFile(param(query, "path"));
                if (tf == null || !tf.isFile()) {
                    send(out, 200, "text/plain; charset=utf-8", "missing");
                } else {
                    enqueueTranscode(tf);
                    send(out, 200, "text/plain; charset=utf-8", "queued");
                }
            } else if ("POST".equals(method) && pathOnly.startsWith("/transcode_all")) {
                final List<File> all = collect(root, "", 0, new ArrayList<String>());
                new Thread(new Runnable() {
                    public void run() {
                        for (File f : all) {
                            if (Util.isImage(f.getName())) continue;
                            if (!isAvcFile(f)) enqueueTranscode(f);
                        }
                    }
                }).start();
                send(out, 200, "text/plain; charset=utf-8", "scanning");
            } else if ("GET".equals(method) && pathOnly.startsWith("/jobs")) {
                send(out, 200, "application/json; charset=utf-8", jobsJson());
            } else if ("GET".equals(method) && pathOnly.startsWith("/probe")) {
                send(out, 200, "application/json; charset=utf-8", probe(param(query, "path")));
            } else if ("GET".equals(method) && pathOnly.startsWith("/view")) {
                File f = resolveFile(param(query, "path"));
                if (f == null || !f.isFile() || !Util.isImage(f.getName())) {
                    send(out, 404, "text/plain; charset=utf-8", "not found");
                } else {
                    byte[] data = readAll(f);
                    if (data == null) send(out, 500, "text/plain; charset=utf-8", "read error");
                    else writeBytes(out, 200, "image/jpeg", data);
                }
            } else if ("POST".equals(method) && pathOnly.startsWith("/upload")) {
                String name = param(query, "name");
                String dir = param(query, "dir");
                String type = param(query, "type");
                String sizeStr = param(query, "size");
                long expected = -1;
                try {
                    expected = Long.parseLong(sizeStr);
                } catch (Exception ignored) {
                }
                if (name == null || name.length() == 0) {
                    send(out, 400, "text/plain; charset=utf-8", "missing name");
                    return;
                }
                boolean isImage = "image".equals(type) || (type == null && Util.isImage(name));
                File targetDir;
                if (dir == null || dir.length() == 0) {
                    targetDir = new File(root, isImage ? "照片" : "视频");
                } else {
                    File requested = resolveDir(dir);
                    String folder = requested.getName();
                    if ("照片".equals(folder) && !isImage) targetDir = new File(root, "视频");
                    else if ("视频".equals(folder) && isImage) targetDir = new File(root, "照片");
                    else targetDir = requested;
                }
                if (!targetDir.exists()) targetDir.mkdirs();
                File target = uniqueFile(targetDir, sanitize(name));
                FileOutputStream fos = new FileOutputStream(target);
                long remaining = contentLength;
                byte[] buf = new byte[65536];
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int n = in.read(buf, 0, toRead);
                    if (n < 0) break;
                    fos.write(buf, 0, n);
                    remaining -= n;
                }
                fos.close();
                if (expected >= 0 && target.length() != expected) {
                    long got = target.length();
                    target.delete();
                    Log.w(TAG, "upload incomplete " + target + " got " + got + " want " + expected);
                    send(out, 200, "text/plain; charset=utf-8", "incomplete " + got + "/" + expected);
                    return;
                }
                String rel = target.getAbsolutePath().substring(root.getAbsolutePath().length());
                if (rel.startsWith("/")) rel = rel.substring(1);
                Log.i(TAG, "uploaded " + target.getAbsolutePath() + " " + target.length() + " bytes");
                if (!isImage) maybeTranscode(target);
                if (onChanged != null) onChanged.run();
                send(out, 200, "text/plain; charset=utf-8", "ok " + target.length() + " " + rel);
            } else if ("POST".equals(method) && pathOnly.startsWith("/delete")) {
                List<String> paths = params(query, "path");
                int okCount = 0;
                for (String rel : paths) {
                    File f = resolveFile(rel);
                    if (f == null || !f.isFile()) continue;
                    boolean ok = f.delete();
                    if (!ok) {
                        try {
                            Runtime.getRuntime().exec(new String[]{"su", "-c",
                                    "rm -f '" + f.getAbsolutePath().replace("'", "'\\''") + "'"});
                            Thread.sleep(300);
                        } catch (Exception ignored) {
                        }
                        ok = !f.exists();
                    }
                    if (ok) okCount++;
                }
                if (onChanged != null) onChanged.run();
                send(out, 200, "text/plain; charset=utf-8", "deleted " + okCount + "/" + paths.size());
            } else if ("POST".equals(method) && pathOnly.startsWith("/organize")) {
                int moved = Util.organize(root.getAbsolutePath());
                if (onChanged != null) onChanged.run();
                send(out, 200, "text/plain; charset=utf-8", "moved " + moved);
            } else if ("GET".equals(method) && pathOnly.startsWith("/space")) {
                send(out, 200, "text/plain; charset=utf-8", spaceInfo());
            } else {
                send(out, 404, "text/plain; charset=utf-8", "not found");
            }
        } catch (Exception e) {
            Log.w(TAG, "http handle error " + e);
        } finally {
            try {
                if (out != null) out.flush();
                if (out != null) out.close();
                if (in != null) in.close();
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Origin 的 authority（host[:port]）是否与 Host 头一致 */
    private static boolean sameAuthority(String origin, String host) {
        if (origin == null || host == null) return false;
        int p = origin.indexOf("://");
        String auth = p >= 0 ? origin.substring(p + 3) : origin;
        int slash = auth.indexOf('/');
        if (slash >= 0) auth = auth.substring(0, slash);
        return auth.equalsIgnoreCase(host);
    }

    private File resolveDir(String dir) {
        if (dir == null || dir.trim().length() == 0 || ".".equals(dir)) return root;
        File d = resolveFile(dir);
        if (d == null) return root;
        return (d.isDirectory() || !d.exists()) ? d : root;
    }

    private File resolveFile(String rel) {
        if (rel == null || rel.length() == 0) return null;
        try {
            File f = new File(root, rel.replace("\\", "/"));
            String canonRoot = root.getCanonicalPath();
            String canonFile = f.getCanonicalPath();
            if (!canonFile.equals(canonRoot) && !canonFile.startsWith(canonRoot + File.separator)) return null;
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    private String jobsJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : JOBS.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            String rel = e.getKey().substring(root.getAbsolutePath().length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            sb.append('"').append(rel.replace("\\", "\\\\").replace("\"", "\\\"")).append("\":\"")
                    .append(e.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    private boolean isAvcFile(File f) {
        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
        try {
            mmr.setDataSource(f.getAbsolutePath());
            String mime = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            String w = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String h = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            int width = w == null ? 0 : Integer.parseInt(w);
            int height = h == null ? 0 : Integer.parseInt(h);
            boolean avc = mime != null && (mime.contains("avc") || mime.contains("h264"));
            boolean small = width <= 1920 && height <= 1080;
            return avc && small;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                mmr.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private void maybeTranscode(final File f) {
        new Thread(new Runnable() {
            public void run() {
                if (!isAvcFile(f)) enqueueTranscode(f);
            }
        }).start();
    }

    private void enqueueTranscode(final File src) {
        final String key = src.getAbsolutePath();
        String cur = JOBS.get(key);
        if (cur != null && (cur.startsWith("queued") || cur.startsWith("converting"))) return;
        JOBS.put(key, "queued");
        new Thread(new Runnable() {
            public void run() {
                File tmp = new File(src.getParentFile(), "conv_" + System.currentTimeMillis() + ".mp4");
                try {
                    long durationMs = 0;
                    android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(src.getAbsolutePath());
                        String d = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (d != null) durationMs = Long.parseLong(d);
                    } catch (Throwable ignored) {
                    } finally {
                        try {
                            mmr.release();
                        } catch (Throwable ignored) {
                        }
                    }
                    Process p = Runtime.getRuntime().exec(new String[]{
                            FFMPEG, "-y", "-i", src.getAbsolutePath(),
                            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "26",
                            "-vf", "scale='min(1280,iw)':-2",
                            "-c:a", "aac", "-b:a", "96k",
                            "-movflags", "+faststart",
                            tmp.getAbsolutePath()});
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getErrorStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        int idx = line.indexOf("time=");
                        if (idx >= 0 && durationMs > 0) {
                            String t = line.substring(idx + 5).trim();
                            String[] parts = t.split("[:.]");
                            if (parts.length >= 4) {
                                try {
                                    long ms = (Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60
                                            + Long.parseLong(parts[2])) * 1000 + Long.parseLong(parts[3]) * 10;
                                    int pct = (int) Math.min(99, ms * 100 / durationMs);
                                    JOBS.put(key, "converting " + pct + "%");
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                    int code = p.waitFor();
                    if (code == 0 && tmp.exists() && tmp.length() > 0) {
                        String base = src.getName();
                        File backup = new File(src.getParentFile(), base + ".orig");
                        src.renameTo(backup);
                        if (!tmp.renameTo(src)) {
                            backup.renameTo(src);
                            JOBS.put(key, "failed");
                        } else {
                            long oldSize = backup.length();
                            backup.delete();
                            JOBS.put(key, "done");
                            Log.i(TAG, "transcoded " + src + " from " + (oldSize / 1024) + "KB");
                        }
                    } else {
                        tmp.delete();
                        JOBS.put(key, "failed");
                    }
                } catch (Throwable t) {
                    tmp.delete();
                    JOBS.put(key, "failed: " + t.getClass().getSimpleName());
                    Log.w(TAG, "transcode error " + t);
                }
            }
        }).start();
    }

    private String probe(String rel) {
        File f = resolveFile(rel);
        if (f == null || !f.isFile()) return "{\"ok\":false,\"err\":\"missing\"}";
        try {
            if (Util.isImage(f.getName())) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                boolean ok = o.outWidth > 0 && o.outHeight > 0;
                return "{\"ok\":" + ok + ",\"type\":\"image\",\"w\":" + o.outWidth + ",\"h\":" + o.outHeight + "}";
            }
            android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
            try {
                mmr.setDataSource(f.getAbsolutePath());
                String dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                String w = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String h = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String mime = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                String bitrate = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE);
                String fps = null;
                Bitmap frame = null;
                try {
                    frame = mmr.getFrameAtTime(0);
                } catch (Throwable ignored) {
                }
                boolean ok = frame != null || (dur != null && Long.parseLong(dur) > 0);
                if (frame != null) frame.recycle();
                return "{\"ok\":" + ok + ",\"type\":\"video\""
                        + ",\"dur\":" + (dur == null ? 0 : Long.parseLong(dur))
                        + ",\"w\":" + (w == null ? 0 : Integer.parseInt(w))
                        + ",\"h\":" + (h == null ? 0 : Integer.parseInt(h))
                        + ",\"mime\":\"" + (mime == null ? "" : mime) + "\""
                        + ",\"bitrate\":" + (bitrate == null ? 0 : Long.parseLong(bitrate))
                        + "}";
            } finally {
                try {
                    mmr.release();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            return "{\"ok\":false,\"err\":\"" + t.getClass().getSimpleName() + "\"}";
        }
    }

    private byte[] thumb(String rel) {
        File f = resolveFile(rel);
        if (f == null || !f.isFile()) return null;
        String key = Integer.toHexString((f.getAbsolutePath() + ":" + f.lastModified() + ":" + f.length()).hashCode());
        File cached = new File(cacheDir, key + ".jpg");
        if (cached.exists() && cached.length() > 0) {
            return readAll(cached);
        }
        Bitmap bmp = null;
        try {
            if (Util.isImage(f.getName())) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
                int sample = 1;
                while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            } else {
                android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                try {
                    mmr.setDataSource(f.getAbsolutePath());
                    bmp = mmr.getFrameAtTime(1000000);
                } finally {
                    try {
                        mmr.release();
                    } catch (Throwable ignored) {
                    }
                }
                if (bmp == null) {
                    try {
                        android.media.MediaMetadataRetriever m2 = new android.media.MediaMetadataRetriever();
                        m2.setDataSource(f.getAbsolutePath());
                        bmp = m2.getFrameAtTime();
                        m2.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "thumb decode failed " + t);
        }
        if (bmp == null) return null;
        int maxW = 420;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w > maxW && w > 0) {
            int nh = h * maxW / w;
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, maxW, nh, true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 78, bos);
        bmp.recycle();
        byte[] data = bos.toByteArray();
        try {
            FileOutputStream out = new FileOutputStream(cached);
            out.write(data);
            out.close();
        } catch (Exception ignored) {
        }
        return data;
    }

    private byte[] readAll(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            File c = new File(dir, base + " (" + i + ")" + ext);
            if (!c.exists()) return c;
        }
        return new File(dir, System.currentTimeMillis() + "-" + name);
    }

    private String sanitize(String name) {
        name = name.replace('\\', '_').replace('/', '_');
        if (name.startsWith(".")) name = "_" + name;
        return name;
    }

    private List<String> params(String query, String key) {
        List<String> out = new ArrayList<String>();
        if (query == null) return out;
        for (String kv : query.split("&")) {
            int idx = kv.indexOf('=');
            if (idx > 0 && kv.substring(0, idx).equals(key)) {
                try {
                    out.add(URLDecoder.decode(kv.substring(idx + 1), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    out.add(kv.substring(idx + 1));
                }
            }
        }
        return out;
    }

    private String param(String query, String key) {
        if (query == null) return null;
        for (String kv : query.split("&")) {
            int idx = kv.indexOf('=');
            if (idx > 0 && kv.substring(0, idx).equals(key)) {
                try {
                    return URLDecoder.decode(kv.substring(idx + 1), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return kv.substring(idx + 1);
                }
            }
        }
        return null;
    }

    private List<File> collect(File dir, String prefix, int depth, List<String> prefixes) {
        List<File> out = new ArrayList<File>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                if (depth < 3) {
                    out.addAll(collect(f, prefix + f.getName() + "/", depth + 1, prefixes));
                }
            } else if (Util.isMedia(f.getName())) {
                out.add(f);
                prefixes.add(prefix);
            }
        }
        return out;
    }

    private String listJson() {
        List<String> prefixes = new ArrayList<String>();
        List<File> files = collect(root, "", 0, prefixes);
        StringBuilder sb = new StringBuilder("[");
        String rootPath = root.getAbsolutePath();
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            String rel = f.getAbsolutePath().substring(rootPath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            if (i > 0) sb.append(',');
            sb.append("{\"path\":\"").append(esc(rel)).append("\",");
            sb.append("\"name\":\"").append(esc(f.getName())).append("\",");
            sb.append("\"size\":").append(f.length()).append(",");
            sb.append("\"type\":\"").append(Util.isImage(f.getName()) ? "image" : "video").append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String spaceInfo() {
        long free = root.getFreeSpace();
        long total = root.getTotalSpace();
        return "{\"free\":" + free + ",\"total\":" + total + "}";
    }

    private String page() {
        String ip = localIp();
        StringBuilder b = new StringBuilder();
        b.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">");
        b.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">");
        b.append("<meta http-equiv=\"Cache-Control\" content=\"no-store\">");
        b.append("<title>广告素材管理</title><style>");
        b.append("*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}");
        b.append("body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;margin:0;background:#0E1116;color:#E9EEF5;padding-bottom:86px}");
        b.append(".wrap{max-width:680px;margin:0 auto;padding:16px 14px}");
        b.append("h1{font-size:21px;margin:6px 0 2px}.sub{color:#7C8794;font-size:13px;line-height:1.5}");
        b.append(".card{background:#161A20;border:1px solid #242B34;border-radius:16px;padding:14px;margin:12px 0}");
        b.append(".h{font-size:14px;color:#B9C3CF;margin-bottom:10px;font-weight:600}");
        b.append("select{width:100%;padding:12px;border-radius:10px;border:1px solid #39434F;background:#10141A;color:#E9EEF5;font-size:15px}");
        b.append("button{border:0;border-radius:12px;background:#4C8DFF;color:#fff;font-size:16px;font-weight:600;padding:14px 16px}");
        b.append("button:disabled{opacity:.45}");
        b.append(".ghost{background:#1B2129;border:1px solid #39434F;color:#E9EEF5;font-weight:500}");
        b.append(".picks{display:flex;gap:10px}");
        b.append(".pickbtn{flex:1;position:relative;display:flex;flex-direction:column;align-items:center;gap:4px;padding:16px 8px;border-radius:14px;border:1px dashed #4C8DFF;background:#10141A;font-size:15px}");
        b.append(".pickbtn b{font-size:22px}");
        b.append(".hiddenfile{position:absolute;left:0;top:0;width:100%;height:100%;opacity:0}");
        b.append(".pf{display:flex;align-items:center;gap:8px;padding:9px 0;border-bottom:1px solid #242B34;font-size:13px}");
        b.append(".pf .pn{flex:1;word-break:break-all}");
        b.append(".x{background:#2A1A1C;color:#FF8080;border:1px solid #5A2A2E;padding:6px 10px;font-size:12px;border-radius:8px;font-weight:500}");
        b.append(".chips{display:flex;gap:8px;margin:2px 0 4px}");
        b.append(".chip{padding:7px 14px;border-radius:999px;background:#10141A;border:1px solid #39434F;font-size:13px;color:#B9C3CF}");
        b.append(".chip.on{background:#16324F;border-color:#4C8DFF;color:#E9EEF5}");
        b.append(".list{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:10px}");
        b.append(".item{position:relative;background:#10141A;border:1px solid #242B34;border-radius:12px;overflow:hidden}");
        b.append(".item.on{border-color:#4C8DFF;box-shadow:0 0 0 2px #4C8DFF33}");
        b.append(".thumbwrap{position:relative;width:100%;height:112px;background:#121822;display:flex;align-items:center;justify-content:center}");
        b.append(".thumb{position:absolute;left:0;top:0;width:100%;height:112px;object-fit:cover}");
        b.append(".ph{font-size:26px;color:#7C8794}");
        b.append(".vthumb{width:100%;height:112px;display:flex;align-items:center;justify-content:center;background:#121822;color:#7C8794;font-size:26px}");
        b.append(".meta{padding:8px 10px}.nm{font-size:12px;word-break:break-all;line-height:1.35}");
        b.append(".sz{color:#7C8794;font-size:11px;margin-top:3px}");
        b.append(".del{margin-top:6px;width:100%;padding:7px;font-size:12px;background:#2A1A1C;color:#FF8080;border:1px solid #5A2A2E}");
        b.append(".cb{position:absolute;top:6px;left:6px;background:#000A;border-radius:8px;padding:4px 6px;z-index:2}");
        b.append(".cb input{width:20px;height:20px;margin:0}");
        b.append(".bar{position:fixed;left:0;right:0;bottom:0;background:#161A20EE;backdrop-filter:blur(8px);border-top:1px solid #242B34;padding:10px 14px calc(10px + env(safe-area-inset-bottom));display:flex;align-items:center;gap:12px;z-index:9}");
        b.append(".bar label{display:flex;align-items:center;gap:8px;font-size:14px}");
        b.append(".bar input{width:20px;height:20px;margin:0}");
        b.append(".bar button{margin-left:auto;padding:11px 16px;font-size:15px}");
        b.append(".prog{height:8px;background:#0B0E12;border-radius:4px;overflow:hidden;margin-top:6px}");
        b.append(".fill{height:100%;width:0;background:#4C8DFF;transition:width .15s}");
        b.append(".pct{font-size:11px;color:#7C8794;margin-top:3px}");
        b.append(".ok{color:#5CD08A}.err{color:#FF6B6B}");
        b.append("</style></head><body><div class=\"wrap\">");
        b.append("<h1>广告素材管理</h1>");
        b.append("<div class=\"sub\">").append(ip).append(":").append(PORT).append(" · ").append(root.getName()).append(" · v1.2.1</div>");

        b.append("<div class=\"card\"><div class=\"h\">上传素材</div>");
        b.append("<div class=\"picks\">");
        b.append("<label class=\"pickbtn\"><b>🖼</b>选择图片<input id=\"fimg\" class=\"hiddenfile\" type=\"file\" accept=\"image/*\" multiple></label>");
        b.append("<label class=\"pickbtn\"><b>🎬</b>选择视频<input id=\"fvid\" class=\"hiddenfile\" type=\"file\" accept=\"video/*\" multiple></label>");
        b.append("</div>");
        b.append("<div id=\"pending\" style=\"margin-top:10px\"><div class=\"sub\">还没有选择文件</div></div>");
        b.append("<button id=\"up\" style=\"width:100%;margin-top:10px\">开始上传</button>");
        b.append("<div class=\"sub\" style=\"margin-top:8px\">图片自动进「照片」，视频自动进「视频」；同名文件自动加 (2)(3) 不会覆盖</div>");
        b.append("<button class=\"ghost\" style=\"width:100%;margin-top:10px\" onclick=\"organize()\">一键整理：把散落的图片/视频归位</button>");
        b.append("<button class=\"ghost\" style=\"width:100%;margin-top:8px\" onclick=\"transcodeAll()\">批量转码：把所有视频转成 H.264（较慢）</button>");
        b.append("<div id=\"st\" class=\"sub\" style=\"margin-top:8px\"></div></div>");

        b.append("<div class=\"card\"><div class=\"h\">素材列表 <span id=\"cnt\" class=\"sub\"></span> <span id=\"space\" class=\"sub\" style=\"float:right\"></span></div>");
        b.append("<div class=\"chips\"><div class=\"chip on\" id=\"chip_all\" onclick=\"setFilter('all')\">全部</div>");
        b.append("<div class=\"chip\" id=\"chip_img\" onclick=\"setFilter('image')\">照片</div>");
        b.append("<div class=\"chip\" id=\"chip_vid\" onclick=\"setFilter('video')\">视频</div></div>");
        b.append("<div id=\"list\" class=\"list\"></div></div>");

        b.append("<div class=\"bar\"><label><input type=\"checkbox\" id=\"all\" onchange=\"selAll(this)\">全选</label>");
        b.append("<span id=\"selcnt\" class=\"sub\">已选 0 个</span>");
        b.append("<button id=\"bdel\" disabled onclick=\"delSel(this)\">删除选中</button></div>");
        b.append("</div><script>");

        b.append("var pending=[],uploading=false,selected={},filter='all',items=[],jobsMap={};");
        b.append("function fmt(n){return n>1073741824?(n/1073741824).toFixed(1)+' GB':n>1048576?(n/1048576).toFixed(1)+' MB':(n/1024).toFixed(0)+' KB'}");
        b.append("function esc(s){return (''+s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/\"/g,'&quot;')}");

        b.append("function gather(){var ids=['fimg','fvid'];var added=0;");
        b.append("for(var k=0;k<ids.length;k++){var el=document.getElementById(ids[k]);if(!el||!el.files)continue;");
        b.append("for(var i=0;i<el.files.length;i++){var f=el.files[i];var dup=false;");
        b.append("for(var j=0;j<pending.length;j++){if(pending[j].name===f.name&&pending[j].size===f.size){dup=true;break;}}");
        b.append("if(!dup){pending.push(f);added++;}}}if(added)renderPending();return added;}");

        b.append("function renderPending(){var box=document.getElementById('pending');");
        b.append("if(!pending.length){box.innerHTML='<div class=\\'sub\\'>还没有选择文件</div>';return;}");
        b.append("var html='';for(var i=0;i<pending.length;i++){html+='<div class=\\'pf\\'><span class=\\'pn\\'>'+esc(pending[i].name)+'</span><span class=\\'sub\\'>'+fmt(pending[i].size)+'</span><button class=\\'x\\' onclick=\\'rmFile('+i+')\\'>移除</button></div>';}");
        b.append("html+='<div class=\\'sub\\' style=\\'margin-top:8px\\'>已选 '+pending.length+' 个，共 '+fmt(totalSize())+'</div>';box.innerHTML=html;}");
        b.append("function totalSize(){var t=0;for(var i=0;i<pending.length;i++)t+=pending[i].size;return t;}");
        b.append("function rmFile(i){pending.splice(i,1);renderPending();}");

        b.append("function doUpload(){if(uploading)return;gather();");
        b.append("var st=document.getElementById('st');");
        b.append("if(!pending.length){st.className='sub err';st.textContent='还没有选择文件，请点上面的「选择图片」或「选择视频」';return;}");
        b.append("uploading=true;var job=pending.slice();var q=document.getElementById('queue');");
        b.append("if(!q){q=document.createElement('div');q.id='queue';st.parentNode.insertBefore(q,st);}");
        b.append("var html='';for(var i=0;i<job.length;i++){html+='<div class=\\'pf\\' style=\\'display:block\\'><div class=\\'pn\\'>'+esc(job[i].name)+' <span class=\\'sub\\'>'+fmt(job[i].size)+'</span></div><div class=\\'prog\\'><div class=\\'fill\\' id=\\'fill'+i+'\\'></div></div><div class=\\'pct\\' id=\\'pct'+i+'\\'>等待</div></div>';}");
        b.append("q.innerHTML=html;document.getElementById('up').disabled=true;uploadNext(job,0);}");

        b.append("function uploadNext(job,i){");
        b.append("if(i>=job.length){document.getElementById('st').className='sub ok';document.getElementById('st').textContent='全部完成，共 '+job.length+' 个文件';uploading=false;pending=[];renderPending();document.getElementById('up').disabled=false;setTimeout(function(){var q=document.getElementById('queue');if(q)q.innerHTML='';},2500);load();return;}");
        b.append("var f=job[i];var type=(f.type&&f.type.indexOf('video')===0)?'video':'image';var xhr=new XMLHttpRequest();var t0=Date.now();");
        b.append("xhr.open('POST','/upload?name='+encodeURIComponent(f.name)+'&type='+type+'&size='+f.size);");
        b.append("xhr.upload.onprogress=function(e){if(!e.lengthComputable)return;var p=Math.round(e.loaded/e.total*100);");
        b.append("document.getElementById('fill'+i).style.width=p+'%';var sec=(Date.now()-t0)/1000;var speed=sec>0?(e.loaded/sec/1048576).toFixed(1):'0';");
        b.append("document.getElementById('pct'+i).textContent=p+'% '+speed+' MB/s ('+fmt(e.loaded)+'/'+fmt(e.total)+')';};");
        b.append("xhr.onload=function(){var txt=xhr.responseText||'';var ok=xhr.status===200&&txt.indexOf('ok')===0;");
        b.append("var parts=txt.split(' ');var dest=parts.length>2?parts.slice(2).join(' '):'';");
        b.append("var el=document.getElementById('pct'+i);");
        b.append("if(!ok){el.textContent=txt.indexOf('incomplete')===0?('传输不完整 '+txt.replace('incomplete ','')):('失败 '+xhr.status+' '+txt);");
        b.append("document.getElementById('fill'+i).style.background='#FF6B6B';document.getElementById('fill'+i).style.width='100%';uploading=false;document.getElementById('up').disabled=false;return;}");
        b.append("el.textContent='完成 → '+dest;document.getElementById('fill'+i).style.width='100%';document.getElementById('fill'+i).style.background='#5CD08A';");
        b.append("fetch('/probe?path='+encodeURIComponent(dest)).then(function(r){return r.json()}).then(function(p){");
        b.append("el.textContent='完成 → '+dest+(p.ok?' ✓ 可播放':' ⚠ 无法播放');el.className=p.ok?'pct':'pct err';}).catch(function(){});");
        b.append("uploadNext(job,i+1);};");
        b.append("xhr.onerror=function(){document.getElementById('pct'+i).textContent='网络中断';uploading=false;document.getElementById('up').disabled=false;};");
        b.append("xhr.send(f);}");

        b.append("document.getElementById('fimg').addEventListener('change',function(){gather();});");
        b.append("document.getElementById('fvid').addEventListener('change',function(){gather();});");
        b.append("document.getElementById('up').addEventListener('click',doUpload);");

        b.append("function setFilter(f){filter=f;");
        b.append("var ids={all:'chip_all',image:'chip_img',video:'chip_vid'};");
        b.append("for(var k in ids){var e=document.getElementById(ids[k]);e.className='chip'+(k===f?' on':'');}renderList();}");
        b.append("function renderList(){var a=items;if(filter!=='all'){a=items.filter(function(x){return x.type===filter});}");
        b.append("var html='';for(var k=0;k<a.length;k++){var x=a[k];var p=encodeURIComponent(x.path);");
        b.append("html+='<div class=\\'item'+(selected[p]?' on':'')+'\\' data-p=\\''+p+'\\' onclick=\\'tapItem(event,this)\\'>';");
        b.append("html+='<label class=\\'cb\\'><input type=\\'checkbox\\' class=\\'chk\\' data-p=\\''+p+'\\''+(selected[p]?' checked':'')+' onchange=\\'sel(this)\\'> </label>';");
        b.append("html+=x.type==='image'?'<img class=\\'thumb\\' loading=\\'lazy\\' src=\\'/thumb?path='+p+'\\'>':'<div class=\\'vthumb\\'>▶</div>';");
        b.append("html+='<div class=\\'meta\\'><div class=\\'nm\\'>'+esc(x.name)+'</div><div class=\\'sz\\'>'+fmt(x.size)+' · '+esc(x.path.indexOf('/')>0?x.path.substring(0,x.path.lastIndexOf('/')):'根目录')+'</div>';");
        b.append("html+='<button class=\\'del\\' onclick=\\'del(event,\\''+p+'\\',this)\\'>删除</button></div></div>';}");
        b.append("document.getElementById('list').innerHTML=html||'<div class=\\'sub\\'>没有素材</div>';syncSel();}");

        b.append("function load(){fetch('/list').then(function(r){return r.json()}).then(function(a){items=a;");
        b.append("fetch('/jobs').then(function(r){return r.json()}).then(function(j){jobsMap=j||{};renderList();");
        b.append("document.getElementById('cnt').textContent='('+a.length+')';renderList();});});");
        b.append("fetch('/space').then(function(r){return r.json()}).then(function(s){document.getElementById('space').textContent='剩余 '+fmt(s.free);});");
        b.append("var busy=false;for(var k in jobsMap){if(jobsMap[k].indexOf('converting')===0||jobsMap[k]==='queued')busy=true;}");
        b.append("if(busy){setTimeout(load,3000);}}");

        b.append("function tapItem(ev,el){var t=ev.target.tagName;if(t==='BUTTON'||t==='INPUT'||t==='LABEL')return;");
        b.append("var c=el.querySelector('.chk');c.checked=!c.checked;sel(c);}");
        b.append("function sel(c){var p=c.dataset.p;if(c.checked){selected[p]=1;}else{delete selected[p];}");
        b.append("var el=c.parentNode;while(el&&(''+el.className).indexOf('item')!==0){el=el.parentNode;}if(el)el.className='item'+(c.checked?' on':'');syncSel();}");
        b.append("function selAll(el){var boxes=document.querySelectorAll('.chk');for(var i=0;i<boxes.length;i++){boxes[i].checked=el.checked;var p=boxes[i].dataset.p;if(el.checked){selected[p]=1;}else{delete selected[p];}");
        b.append("var it=boxes[i].parentNode;while(it&&(''+it.className).indexOf('item')!==0){it=it.parentNode;}if(it)it.className='item'+(el.checked?' on':'');}syncSel();}");
        b.append("function syncSel(){var n=Object.keys(selected).length;document.getElementById('selcnt').textContent='已选 '+n+' 个';");
        b.append("document.getElementById('bdel').disabled=n===0;var boxes=document.querySelectorAll('.chk');");
        b.append("var all=document.getElementById('all');all.checked=boxes.length>0&&n===boxes.length;all.indeterminate=n>0&&n<boxes.length;}");

        b.append("function del(ev,p,btn){if(ev)ev.stopPropagation();");
        b.append("if(btn.dataset.armed!=='1'){btn.dataset.armed='1';btn.textContent='再点一次';setTimeout(function(){btn.dataset.armed='0';btn.textContent='删除';},4000);return;}");
        b.append("btn.textContent='删除中';btn.disabled=true;");
        b.append("fetch('/delete?path='+p,{method:'POST'}).then(function(r){return r.text()}).then(function(t){");
        b.append("delete selected[p];var st=document.getElementById('st');st.className=t.indexOf('deleted')===0?'sub ok':'sub err';");
        b.append("st.textContent=t.indexOf('deleted')===0?'已删除：'+decodeURIComponent(p):'删除失败：'+t;load();});}");

        b.append("function delSel(btn){var keys=Object.keys(selected);if(!keys.length)return;");
        b.append("if(btn.dataset.armed!=='1'){btn.dataset.armed='1';btn.textContent='再点一次确认';setTimeout(function(){btn.dataset.armed='0';btn.textContent='删除选中';},4000);return;}");
        b.append("var qs=keys.map(function(p){return 'path='+p}).join('&');btn.textContent='删除中';btn.disabled=true;");
        b.append("fetch('/delete?'+qs,{method:'POST'}).then(function(r){return r.text()}).then(function(t){");
        b.append("var st=document.getElementById('st');st.className='sub ok';st.textContent='批量删除完成：'+t;");
        b.append("selected={};document.getElementById('all').checked=false;btn.textContent='删除选中';btn.dataset.armed='0';btn.disabled=false;load();});}");

        b.append("function transcodeAll(){var st=document.getElementById('st');st.className='sub';st.textContent='正在检查并加入转码队列…';");
        b.append("fetch('/transcode_all',{method:'POST'}).then(function(r){return r.text()}).then(function(){st.className='sub ok';st.textContent='已加入队列，转码中会自动刷新';setTimeout(load,2000);});}");
        b.append("function organize(){var st=document.getElementById('st');st.className='sub';st.textContent='整理中…';");
        b.append("fetch('/organize',{method:'POST'}).then(function(r){return r.text()}).then(function(t){st.className='sub ok';st.textContent='整理完成：'+t;load();});}");

        b.append("renderPending();load();</script></body></html>");
        return b.toString();
    }

    private void send(OutputStream out, int code, String type, String body) throws IOException {
        writeBytes(out, code, type, body.getBytes("UTF-8"));
    }

    private void writeBytes(OutputStream out, int code, String type, byte[] bytes) throws IOException {
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " ERR") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(bytes);
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        int prev = -1;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] arr = bos.toByteArray();
                if (prev == '\r' && arr.length > 0) {
                    return new String(arr, 0, arr.length - 1, "UTF-8");
                }
                return new String(arr, "UTF-8");
            }
            bos.write(c);
            prev = c;
            if (bos.size() > 16384) break;
        }
        return bos.size() > 0 ? new String(bos.toByteArray(), "UTF-8") : null;
    }
}
