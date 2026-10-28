package io.github.fyxl.adplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PanelView extends FrameLayout {

    private static final String TAG = "AdPlayer";

    private final ImageView image;
    private final VideoView video;
    private final TextView hint;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> playlist = new ArrayList<File>();

    private int index = 0;
    private boolean paused = false;
    private boolean started = false;
    private int imageSeconds = 5;
    private boolean mute = true;
    private boolean singleLoop = false;
    private String folder = "";

    private final Runnable nextRunnable = new Runnable() {
        public void run() { next(); }
    };

    public PanelView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.panel, this, true);
        image = (ImageView) findViewById(R.id.panel_image);
        video = (VideoView) findViewById(R.id.panel_video);
        hint = (TextView) findViewById(R.id.panel_hint);

        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // consumed here so clicks do not leak to parent gesture logic
            }
        });
    }

    public void configure(String folder, int imageSeconds, boolean mute, boolean shuffle, boolean singleLoop) {
        this.singleLoop = singleLoop;
        this.folder = folder;
        this.imageSeconds = Math.max(1, imageSeconds);
        this.mute = mute;
        playlist.clear();
        playlist.addAll(Util.scan(folder, shuffle));
        index = 0;
        Log.i(TAG, "panel " + folder + " -> " + playlist.size() + " files");
    }

    public void start() {
        started = true;
        paused = false;
        if (playlist.isEmpty()) {
            showHint("这个区域没有素材\n" + folder);
            return;
        }
        hint.setVisibility(GONE);
        playCurrent();
    }

    public void release() {
        started = false;
        handler.removeCallbacks(nextRunnable);
        try {
            video.stopPlayback();
        } catch (Exception ignored) {
        }
        image.setImageDrawable(null);
    }

    public boolean isPaused() {
        return paused;
    }

    public void togglePause() {
        if (!started) return;
        paused = !paused;
        if (paused) {
            handler.removeCallbacks(nextRunnable);
            if (video.isPlaying()) video.pause();
        } else {
            if (video.getVisibility() == VISIBLE) {
                video.start();
            } else if (image.getVisibility() == VISIBLE) {
                handler.postDelayed(nextRunnable, imageSeconds * 1000L);
            } else {
                playCurrent();
            }
        }
    }

    public void setSingleLoop(boolean v) {
        singleLoop = v;
    }

    public boolean isSingleLoop() {
        return singleLoop;
    }

    public void next() {
        if (!started || playlist.isEmpty()) return;
        if (singleLoop) {
            playCurrent();
            return;
        }
        index = (index + 1) % playlist.size();
        playCurrent();
    }

    public void prev() {
        if (!started || playlist.isEmpty()) return;
        index = (index - 1 + playlist.size()) % playlist.size();
        playCurrent();
    }

    public String currentName() {
        if (playlist.isEmpty()) return "";
        return playlist.get(index).getName();
    }

    public int size() {
        return playlist.size();
    }

    private void showHint(String text) {
        hint.setText(text);
        hint.setVisibility(VISIBLE);
        image.setVisibility(GONE);
        video.setVisibility(GONE);
    }

    private void playCurrent() {
        handler.removeCallbacks(nextRunnable);
        if (playlist.isEmpty()) return;
        final File file = playlist.get(index);

        if (Util.isImage(file.getName())) {
            try {
                video.stopPlayback();
            } catch (Exception ignored) {
            }
            video.setVisibility(GONE);
            Bitmap bm = decodeSampled(file, 1920, 1080);
            if (bm == null) {
                handler.postDelayed(nextRunnable, 300);
                return;
            }
            image.setImageBitmap(bm);
            image.setVisibility(VISIBLE);
            if (!paused) handler.postDelayed(nextRunnable, imageSeconds * 1000L);
        } else {
            image.setVisibility(GONE);
            video.setVisibility(VISIBLE);
            video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer mp) {
                    if (mute) mp.setVolume(0f, 0f);
                    if (!paused) video.start();
                }
            });
            video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    if (singleLoop) playCurrent(); else next();
                }
            });
            video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.w(TAG, "video error " + what + "/" + extra + " " + file);
                    showHint("无法播放：" + file.getName()
                            + "\n可能是文件损坏或编码不支持（建议 MP4 / H.264）");
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            hint.setVisibility(GONE);
                            next();
                        }
                    }, 4000);
                    return true;
                }
            });
            try {
                video.setVideoPath(file.getAbsolutePath());
                video.requestFocus();
            } catch (Exception e) {
                handler.postDelayed(nextRunnable, 500);
            }
        }
    }

    private Bitmap decodeSampled(File file, int reqW, int reqH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= reqW && opts.outHeight / (sample * 2) >= reqH) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }
}
