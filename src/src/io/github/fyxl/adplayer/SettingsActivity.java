package io.github.fyxl.adplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.NumberPicker;

public class SettingsActivity extends Activity {

    private static final int REQ_FOLDER = 1001;

    private Button[] layoutButtons = new Button[4];
    private Button btnModeList;
    private Button btnModeSingle;
    private int loopMode = 0;
    private int selectedLayout = 0;
    private TextView[] folders = new TextView[4];
    private TextView editSeconds;
    private TextView editReboot;
    private TextView editScreenOff;
    private TextView editScreenOn;
    private TextView editPin;
    private TextView editPowerOn;
    private TextView editPowerOff;
    private TextView editGuardian;
    private Button checkShuffle;
    private Button checkMute;
    private Button checkBoot;
    private boolean shuffleOn = false;
    private boolean muteOn = true;
    private boolean bootOn = true;
    private View[] folderRows = new View[4];
    private TextView layoutHint;
    private int browsingPanel = 1;
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        bindViews();

        String pin = Prefs.pin(this);
        if (pin != null && pin.length() > 0) {
            askPin(pin);
        } else {
            unlocked = true;
            fillValues();
        }
    }

    private void bindViews() {
        int[] layoutIds = new int[]{R.id.btn_layout0, R.id.btn_layout1, R.id.btn_layout2, R.id.btn_layout3};
        for (int i = 0; i < layoutIds.length; i++) {
            final int index = i;
            layoutButtons[i] = (Button) findViewById(layoutIds[i]);
            layoutButtons[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    selectLayout(index);
                }
            });
        }

        folders[0] = (TextView) findViewById(R.id.edit_folder1);
        folders[1] = (TextView) findViewById(R.id.edit_folder2);
        folders[2] = (TextView) findViewById(R.id.edit_folder3);
        folders[3] = (TextView) findViewById(R.id.edit_folder4);

        editSeconds = (TextView) findViewById(R.id.edit_seconds);
        editReboot = (TextView) findViewById(R.id.edit_reboot);
        editScreenOff = (TextView) findViewById(R.id.edit_screen_off);
        editScreenOn = (TextView) findViewById(R.id.edit_screen_on);
        btnModeList = (Button) findViewById(R.id.btn_mode_list);
        btnModeSingle = (Button) findViewById(R.id.btn_mode_single);
        btnModeList.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loopMode = 0;
                renderLoop();
            }
        });
        btnModeSingle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loopMode = 1;
                renderLoop();
            }
        });

        editPin = (TextView) findViewById(R.id.edit_pin);
        editPowerOn = (TextView) findViewById(R.id.edit_power_on);
        editPowerOff = (TextView) findViewById(R.id.edit_power_off);
        editGuardian = (TextView) findViewById(R.id.edit_guardian);

        editPowerOn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTimeDialog(editPowerOn, "每天定时开机");
            }
        });
        editPowerOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTimeDialog(editPowerOff, "每天定时关机");
            }
        });
        editGuardian.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNumberDialog(editGuardian, "守护间隔（秒，10-3600）", 300, 10, 3600);
            }
        });

        editSeconds.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNumberDialog(editSeconds, "每张图片停留秒数（1-120）", 5, 1, 120);
            }
        });
        editReboot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTimeDialog(editReboot, "每天定时重启");
            }
        });
        editScreenOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTimeDialog(editScreenOff, "软件熄屏时间");
            }
        });
        editScreenOn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTimeDialog(editScreenOn, "软件亮屏时间");
            }
        });
        editPin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNumberDialog(editPin, "设置页密码（0 表示不设密码）", 0, 0, 9999);
            }
        });

        checkShuffle = (Button) findViewById(R.id.check_shuffle);
        checkMute = (Button) findViewById(R.id.check_mute);
        checkBoot = (Button) findViewById(R.id.check_boot);

        checkShuffle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                shuffleOn = !shuffleOn;
                renderToggle(checkShuffle, "随机播放顺序", shuffleOn);
            }
        });
        checkMute.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                muteOn = !muteOn;
                renderToggle(checkMute, "视频静音", muteOn);
            }
        });
        checkBoot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bootOn = !bootOn;
                renderToggle(checkBoot, "开机自动启动", bootOn);
            }
        });

        folderRows[0] = findViewById(R.id.row_folder1);
        folderRows[1] = findViewById(R.id.row_folder2);
        folderRows[2] = findViewById(R.id.row_folder3);
        folderRows[3] = findViewById(R.id.row_folder4);
        layoutHint = (TextView) findViewById(R.id.text_layout_hint);

        int[] folderIds = new int[]{R.id.edit_folder1, R.id.edit_folder2, R.id.edit_folder3, R.id.edit_folder4};
        for (int i = 0; i < folderIds.length; i++) {
            final int panelIdx = i + 1;
            findViewById(folderIds[i]).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openPicker(panelIdx);
                }
            });
        }

        int[] browseIds = new int[]{R.id.btn_browse1, R.id.btn_browse2, R.id.btn_browse3, R.id.btn_browse4};
        for (int i = 0; i < browseIds.length; i++) {
            final int panel = i + 1;
            findViewById(browseIds[i]).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openPicker(panel);
                }
            });
        }

        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                save();
            }
        });
    }

    private void renderLoop() {
        btnModeList.setSelected(loopMode == 0);
        btnModeSingle.setSelected(loopMode == 1);
    }

    private void renderToggle(Button btn, String label, boolean on) {
        if (btn == null) return;
        btn.setSelected(on);
        btn.setText(label + (on ? "：开" : "：关"));
    }

    private void showTimeDialog(final TextView target, String title) {
        int h = 22, m = 0;
        String cur = target.getText().toString().trim();
        if (cur.matches("\\d{1,2}:\\d{1,2}")) {
            String[] p = cur.split(":");
            try {
                h = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
            } catch (Exception ignored) {
            }
        }
        new TimeKeyDialog(this, title, h, m, new TimeKeyDialog.Listener() {
            public void onTime(int hour, int minute) {
                target.setText(pad(hour) + ":" + pad(minute));
            }

            public void onClear() {
                target.setText("");
            }
        }).show();
    }

    private String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private void showNumberDialog(final TextView target, String title, int def, int min, int max) {
        int cur = def;
        try {
            cur = Integer.parseInt(target.getText().toString().trim());
        } catch (Exception ignored) {
        }
        new NumberKeyDialog(this, title, cur, min, max, new NumberKeyDialog.Listener() {
            public void onValue(int v) {
                target.setText(String.valueOf(v));
            }
        }).show();
    }

    private void openPicker(int panel) {
        browsingPanel = panel;
        Intent intent = new Intent(SettingsActivity.this, FolderPickerActivity.class);
        intent.putExtra("start", folders[panel - 1].getText().toString());
        startActivityForResult(intent, REQ_FOLDER);
    }

    private void askPin(final String correct) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("请输入密码")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (correct.equals(input.getText().toString().trim())) {
                            unlocked = true;
                            fillValues();
                        } else {
                            finish();
                        }
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void applyLayoutVisibility(int position) {
        int visibleCount;
        String hint;
        if (position == 0) {
            visibleCount = 1;
            hint = "单屏全屏：只需要 1 个素材文件夹，整屏循环播放";
        } else if (position == 1) {
            visibleCount = 2;
            hint = "左右分屏：左边用区域 1，右边用区域 2，各自独立循环";
        } else if (position == 2) {
            visibleCount = 2;
            hint = "上下分屏：上边用区域 1，下边用区域 2，各自独立循环";
        } else {
            visibleCount = 4;
            hint = "四宫格：4 个区域各自独立循环；建议只让区域 1 放视频，其余放图片";
        }
        for (int i = 0; i < 4; i++) {
            folderRows[i].setVisibility(i < visibleCount ? View.VISIBLE : View.GONE);
        }
        layoutHint.setText(hint);
    }

    private void selectLayout(int index) {
        selectedLayout = index;
        for (int i = 0; i < layoutButtons.length; i++) {
            if (layoutButtons[i] != null) layoutButtons[i].setSelected(i == index);
        }
        applyLayoutVisibility(index);
    }

    private void fillValues() {
        selectLayout(Prefs.layout(this));
        if (layoutButtons[0] != null) {
            final Button firstBtn = layoutButtons[0];
            firstBtn.postDelayed(new Runnable() {
                public void run() {
                    View f = getCurrentFocus();
                    if (f != null && f instanceof EditText) {
                        f.clearFocus();
                    }
                    firstBtn.requestFocus();
                }
            }, 400);
        }
        for (int i = 1; i <= 4; i++) {
            folders[i - 1].setText(Prefs.folder(this, i));
        }
        editSeconds.setText(String.valueOf(Prefs.seconds(this)));
        shuffleOn = Prefs.shuffle(this);
        muteOn = Prefs.mute(this);
        bootOn = Prefs.boot(this);
        renderToggle(checkShuffle, "随机播放顺序", shuffleOn);
        renderToggle(checkMute, "视频静音", muteOn);
        renderToggle(checkBoot, "开机自动启动", bootOn);
        editReboot.setText(Prefs.rebootTime(this));
        editScreenOff.setText(Prefs.screenOffTime(this));
        editScreenOn.setText(Prefs.screenOnTime(this));
        editPin.setText(Prefs.pin(this));
        loopMode = Prefs.loopMode(this);
        renderLoop();
        editPowerOn.setText(Prefs.powerOnTime(this));
        editPowerOff.setText(Prefs.powerOffTime(this));
        editGuardian.setText(String.valueOf(Prefs.guardianSeconds(this)));
        ((TextView) findViewById(R.id.text_upload_url)).setText(
                "手机导入素材：浏览器打开 http://" + HttpUploadServer.localIp() + ":" + HttpUploadServer.PORT);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean isOk = code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (isOk) {
            View f = getCurrentFocus();
            if (f == checkShuffle || f == checkMute || f == checkBoot) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    f.performClick();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK && data != null) {
            folders[browsingPanel - 1].setText(data.getStringExtra("path"));
        }
    }

    private void save() {
        if (!unlocked) return;
        int seconds;
        try {
            seconds = Integer.parseInt(editSeconds.getText().toString().trim());
        } catch (Exception e) {
            seconds = 5;
        }
        if (seconds < 1) seconds = 1;

        SharedPreferences.Editor e = Prefs.get(this).edit();
        e.putInt(Prefs.KEY_LAYOUT, selectedLayout);
        for (int i = 1; i <= 4; i++) {
            String v = folders[i - 1].getText().toString().trim();
            if (v.length() == 0 && i == 1) v = Prefs.DEFAULT_FOLDER;
            e.putString("folder" + i, v);
        }
        e.putInt(Prefs.KEY_SECONDS, seconds);
        e.putBoolean(Prefs.KEY_SHUFFLE, shuffleOn);
        e.putBoolean(Prefs.KEY_MUTE, muteOn);
        e.putBoolean(Prefs.KEY_BOOT, bootOn);
        e.putString(Prefs.KEY_REBOOT, editReboot.getText().toString().trim());
        e.putString(Prefs.KEY_SCREEN_OFF, editScreenOff.getText().toString().trim());
        e.putString(Prefs.KEY_SCREEN_ON, editScreenOn.getText().toString().trim());
        String pinText = editPin.getText().toString().trim();
        if (pinText.length() > 0 && pinText.length() < 4) {
            while (pinText.length() < 4) pinText = "0" + pinText;
        }
        if ("0000".equals(pinText)) pinText = "";
        e.putString(Prefs.KEY_PIN, pinText);
        String powerOn = editPowerOn.getText().toString().trim();
        e.putString(Prefs.KEY_POWER_ON, powerOn);
        e.putString(Prefs.KEY_POWER_OFF, editPowerOff.getText().toString().trim());
        int gSec = 300;
        try {
            gSec = Integer.parseInt(editGuardian.getText().toString().trim());
        } catch (Exception ignored) {
        }
        if (gSec < 10) gSec = 10;
        e.putInt(Prefs.KEY_GUARDIAN, gSec);
        e.putInt(Prefs.KEY_LOOP, loopMode);
        e.commit();

        Root.applyGuardianInterval(this, gSec);
        long wake = Root.nextEpoch(powerOn);
        if (wake > 0) Root.setWakeAlarm(wake);

        Intent i = new Intent(this, PlayerActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }
}
