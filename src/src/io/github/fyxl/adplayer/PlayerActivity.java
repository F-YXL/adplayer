package io.github.fyxl.adplayer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends Activity {

    private static final String TAG = "AdPlayer";

    private final Handler handler = new Handler();
    private final List<PanelView> panels = new ArrayList<PanelView>();
    private TextView stateView;
    private TextView toastView;
    private String lastScheduleKey = "";
    private HttpUploadServer uploadServer;
    private View drawer;
    private Button btnLoopList;
    private Button btnLoopSingle;
    private Button btnOpenSettings;
    private int drawerIndex = 0;
    private boolean organized = false;

    private final Runnable openSettingsRunnable = new Runnable() {
        public void run() { openSettings(); }
    };

    private final Runnable tickRunnable = new Runnable() {
        public void run() {
            tick();
            handler.postDelayed(this, 20000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        stateView = (TextView) findViewById(R.id.state_view);
        toastView = (TextView) findViewById(R.id.toast_view);
        hideSystemUi();
        Toast.makeText(this, "手机导入素材：http://" + HttpUploadServer.localIp() + ":" + HttpUploadServer.PORT,
                Toast.LENGTH_LONG).show();
        keepNetworkAlive();
        setupDrawer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        startUploadServer();
        buildPanels();
        autoOrganize();
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, 20000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releasePanels();
        handler.removeCallbacks(tickRunnable);
        handler.removeCallbacks(openSettingsRunnable);
    }

    /** 保持 Wi-Fi 常连、屏幕常亮，避免息屏后掉线连不上 */
    private void keepNetworkAlive() {
        Root.exec("svc power stayon true; svc wifi enable; settings put global wifi_sleep_policy 2");
    }

    private void setupDrawer() {
        drawer = findViewById(R.id.drawer);
        btnLoopList = (Button) findViewById(R.id.btn_loop_list);
        btnLoopSingle = (Button) findViewById(R.id.btn_loop_single);
        btnLoopList.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyLoopMode(0);
            }
        });
        btnLoopSingle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyLoopMode(1);
            }
        });
        btnOpenSettings = (Button) findViewById(R.id.btn_open_settings);
        btnOpenSettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hideDrawer();
                openSettings();
            }
        });
        updateLoopButtons();
    }

    private void showDrawer() {
        if (drawer == null) return;
        drawer.setVisibility(View.VISIBLE);
        drawerIndex = Prefs.loopMode(this) == 1 ? 1 : 0;
        renderDrawer();
    }

    private void renderDrawer() {
        Button[] btns = new Button[]{btnLoopList, btnLoopSingle, btnOpenSettings};
        String[] labels = new String[]{
                Prefs.loopMode(this) == 1 ? "列表循环" : "列表循环 ✓",
                Prefs.loopMode(this) == 1 ? "单文件循环 ✓" : "单文件循环",
                "完整设置"
        };
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] == null) continue;
            btns[i].setText(labels[i]);
            btns[i].setBackgroundResource(i == drawerIndex
                    ? R.drawable.bg_button_primary : R.drawable.bg_button_ghost);
            btns[i].setTextColor(i == drawerIndex ? 0xFFFFFFFF : 0xFFE9EEF5);
        }
    }

    private void activateDrawerItem() {
        if (drawerIndex == 0) applyLoopMode(0);
        else if (drawerIndex == 1) applyLoopMode(1);
        else {
            hideDrawer();
            openSettings();
        }
    }

    private void hideDrawer() {
        if (drawer == null) return;
        drawer.setVisibility(View.GONE);
        drawer.clearFocus();
    }

    private void applyLoopMode(int mode) {
        Prefs.get(this).edit().putInt(Prefs.KEY_LOOP, mode).commit();
        for (PanelView p : panels) p.setSingleLoop(mode == 1);
        updateLoopButtons();
        toast(mode == 1 ? "循环模式：单文件循环" : "循环模式：列表循环");
        hideDrawer();
    }

    private void updateLoopButtons() {
        boolean single = Prefs.loopMode(this) == 1;
        btnLoopList.setSelected(!single);
        btnLoopSingle.setSelected(single);
        btnLoopList.setText(!single ? "列表循环 ✓" : "列表循环");
        btnLoopSingle.setText(single ? "单文件循环 ✓" : "单文件循环");
    }

    private void autoOrganize() {
        if (organized) return;
        organized = true;
        new Thread(new Runnable() {
            public void run() {
                final int moved = Util.organize(Prefs.folder(PlayerActivity.this, 1));
                if (moved > 0) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            buildPanels();
                            toast("已自动分类 " + moved + " 个素材");
                        }
                    });
                }
            }
        }).start();
    }

    private void startUploadServer() {
        try {
            if (uploadServer != null) uploadServer.stop();
            uploadServer = new HttpUploadServer(new File(Prefs.folder(this, 1)), new File(getCacheDir(), "thumbs"));
            uploadServer.setOnChanged(new Runnable() {
                public void run() {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            buildPanels();
                        }
                    });
                }
            });
            uploadServer.start();
        } catch (Exception e) {
            Log.w(TAG, "start upload server failed " + e);
        }
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void buildPanels() {
        releasePanels();
        panels.clear();

        LinearLayout container = (LinearLayout) findViewById(R.id.panel_container);
        container.removeAllViews();

        int layout = Prefs.layout(this);
        int seconds = Prefs.seconds(this);
        boolean mute = Prefs.mute(this);
        boolean shuffle = Prefs.shuffle(this);

        if (layout == 0) {
            addRow(container, new int[]{1});
        } else if (layout == 1) {
            addRow(container, new int[]{1, 2});
        } else if (layout == 2) {
            addRow(container, new int[]{1});
            addRow(container, new int[]{2});
        } else {
            addRow(container, new int[]{1, 2});
            addRow(container, new int[]{3, 4});
        }

        for (PanelView p : panels) {
            p.configure(Prefs.folder(this, panels.indexOf(p) + 1), seconds, mute, shuffle, Prefs.loopMode(this) == 1);
            p.start();
        }
        updateState();
    }

    private void addRow(LinearLayout container, int[] indices) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        row.setLayoutParams(rowParams);

        for (int idx : indices) {
            PanelView panel = new PanelView(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            panel.setLayoutParams(p);
            row.addView(panel);
            panels.add(panel);
        }
        container.addView(row);
    }

    private void releasePanels() {
        for (PanelView p : panels) {
            p.release();
        }
    }

    private void updateState() {
        if (panels.isEmpty()) return;
        PanelView first = panels.get(0);
        if (first.size() == 0) {
            stateView.setText("无素材 — 长按屏幕或按遥控器 MENU 键进入设置");
        } else {
            stateView.setText(first.currentName());
        }
    }

    // ---------- 按键（遥控器） ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_DPAD_UP:
                    hideDrawer();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    drawerIndex = (drawerIndex + 2) % 3;
                    renderDrawer();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    drawerIndex = (drawerIndex + 1) % 3;
                    renderDrawer();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (event.getRepeatCount() == 0) activateDrawerItem();
                    return true;
                default:
                    return true;
            }
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                showDrawer();
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                applyLoopMode(Prefs.loopMode(this) == 1 ? 0 : 1);
                return true;

            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                openSettings();
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                nextAll();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
                prevAll();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (event.getEventTime() - event.getDownTime() > 1200) {
                    openSettings();
                    return true;
                }
                if (event.getRepeatCount() == 0) {
                    togglePauseAll();
                }
                return true;

            case KeyEvent.KEYCODE_BACK:
                toast("按 MENU 键进入设置");
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void nextAll() {
        for (PanelView p : panels) p.next();
        toast("下一个");
        updateState();
    }

    private void prevAll() {
        for (PanelView p : panels) p.prev();
        toast("上一个");
        updateState();
    }

    private void togglePauseAll() {
        boolean paused = panels.isEmpty() ? false : !panels.get(0).isPaused();
        for (PanelView p : panels) {
            if (p.isPaused() != paused) p.togglePause();
        }
        toast(paused ? "已暂停" : "继续播放");
    }

    private void toast(String text) {
        toastView.setText(text);
        toastView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideToast);
        handler.postDelayed(hideToast, 1500);
    }

    private final Runnable hideToast = new Runnable() {
        public void run() {
            toastView.setVisibility(View.GONE);
        }
    };

    // ---------- 触摸：长按进设置 ----------

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            handler.postDelayed(openSettingsRunnable, 1200);
        } else if (ev.getAction() == MotionEvent.ACTION_UP
                || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(openSettingsRunnable);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void openSettings() {
        handler.removeCallbacks(openSettingsRunnable);
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ---------- 定时任务 ----------

    private void tick() {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.CHINA);
        String now = f.format(new Date());

        String reboot = Prefs.rebootTime(this);
        if (reboot != null && reboot.trim().equals(now) && !lastScheduleKey.equals("reboot" + now)) {
            lastScheduleKey = "reboot" + now;
            Log.i(TAG, "scheduled reboot at " + now);
            Root.reboot();
        }

        String powerOff = Prefs.powerOffTime(this);
        if (powerOff != null && powerOff.trim().equals(now) && !lastScheduleKey.equals("off" + now)) {
            lastScheduleKey = "off" + now;
            String on = Prefs.powerOnTime(this);
            long wake = Root.nextEpoch(on);
            if (wake > 0) {
                Root.setWakeAlarm(wake);
                Log.i(TAG, "wake alarm set to " + on);
            }
            Log.i(TAG, "scheduled power off at " + now);
            Root.powerOff();
        }

        String off = Prefs.screenOffTime(this);
        if (off != null && off.trim().equals(now) && !lastScheduleKey.equals("off" + now)) {
            lastScheduleKey = "off" + now;
            Root.exec("input keyevent 26");
        }

        String on = Prefs.screenOnTime(this);
        if (on != null && on.trim().equals(now) && !lastScheduleKey.equals("on" + now)) {
            lastScheduleKey = "on" + now;
            Root.exec("input keyevent 26");
        }
    }

}
