package io.github.fyxl.adplayer;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/** 遥控器数字键/上下键都能用的数字输入框。 */
public class NumberKeyDialog extends Dialog {

    public interface Listener {
        void onValue(int value);
    }

    private final String title;
    private final int initial;
    private final int min;
    private final int max;
    private final Listener listener;

    private int value;
    private String typed = "";
    private TextView valueText;
    private TextView hintText;

    public NumberKeyDialog(Context context, String title, int initial, int min, int max, Listener l) {
        super(context);
        this.title = title;
        this.initial = initial;
        this.min = min;
        this.max = max;
        this.listener = l;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_key_number);
        setTitle(null);
        value = Math.max(min, Math.min(max, initial));

        ((TextView) findViewById(R.id.dlg_title)).setText(title);
        valueText = (TextView) findViewById(R.id.text_value);
        hintText = (TextView) findViewById(R.id.text_hint);
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
    }

    private void render() {
        valueText.setText(String.valueOf(value));
        hintText.setText(typed.length() > 0
                ? "已输入：" + typed + "（OK 确定）"
                : "上下键加减 · 直接按数字键输入 · OK 确定 · 返回取消");
    }

    private void confirm() {
        if (listener != null) listener.onValue(value);
        dismiss();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int code = event.getKeyCode();
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            int digit = code - KeyEvent.KEYCODE_0;
            if (typed.length() < 5) {
                typed = typed + digit;
                int v = Integer.parseInt(typed);
                if (v > max) {
                    typed = String.valueOf(digit);
                    v = digit;
                }
                value = Math.max(min, Math.min(max, v));
                render();
            }
            return true;
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            int digit = code - KeyEvent.KEYCODE_NUMPAD_0;
            if (typed.length() < 5) {
                typed = typed + digit;
                int v = Integer.parseInt(typed);
                if (v > max) {
                    typed = String.valueOf(digit);
                    v = digit;
                }
                value = Math.max(min, Math.min(max, v));
                render();
            }
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                typed = "";
                value = value >= max ? max : value + 1;
                render();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                typed = "";
                value = value <= min ? min : value - 1;
                render();
                return true;
            case KeyEvent.KEYCODE_DEL:
                if (typed.length() > 0) {
                    typed = typed.substring(0, typed.length() - 1);
                    value = typed.length() > 0 ? Integer.parseInt(typed) : initial;
                    value = Math.max(min, Math.min(max, value));
                    render();
                }
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
