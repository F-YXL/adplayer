package io.github.fyxl.adplayer;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/** 只用遥控器方向键就能调时间的对话框：上下改数值，左右切时/分，OK 确定。 */
public class TimeKeyDialog extends Dialog {

    public interface Listener {
        void onTime(int hour, int minute);
        void onClear();
    }

    private final String title;
    private final int initHour;
    private final int initMinute;
    private final Listener listener;

    private int hour;
    private int minute;
    private int unit = 0; // 0=hour 1=minute
    private TextView hourText;
    private TextView minuteText;

    public TimeKeyDialog(Context context, String title, int hour, int minute, Listener l) {
        super(context);
        this.title = title;
        this.initHour = hour;
        this.initMinute = minute;
        this.listener = l;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_key_time);
        setTitle(null);
        hour = Math.max(0, Math.min(23, initHour));
        minute = Math.max(0, Math.min(59, initMinute));

        ((TextView) findViewById(R.id.dlg_title)).setText(title);
        hourText = (TextView) findViewById(R.id.text_hour);
        minuteText = (TextView) findViewById(R.id.text_minute);
        render();

        findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirm();
            }
        });
        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cancel();
            }
        });
        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (listener != null) listener.onClear();
                dismiss();
            }
        });
    }

    private void render() {
        hourText.setText(pad(hour));
        minuteText.setText(pad(minute));
        hourText.setTextColor(unit == 0 ? 0xFF4C8DFF : 0xFFE9EEF5);
        minuteText.setTextColor(unit == 1 ? 0xFF4C8DFF : 0xFFE9EEF5);
    }

    private String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private void confirm() {
        if (listener != null) listener.onTime(hour, minute);
        dismiss();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (unit == 0) hour = (hour + 1) % 24; else minute = (minute + 1) % 60;
                render();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (unit == 0) hour = (hour + 23) % 24; else minute = (minute + 59) % 60;
                render();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                unit = 0;
                render();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                unit = 1;
                render();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                confirm();
                return true;
            case KeyEvent.KEYCODE_BACK:
                cancel();
                return true;
            default:
                return true;
        }
    }
}
