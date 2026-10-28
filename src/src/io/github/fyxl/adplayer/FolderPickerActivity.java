package io.github.fyxl.adplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FolderPickerActivity extends Activity {

    private File current;
    private TextView textPath;
    private ListView list;
    private final List<File> entries = new ArrayList<File>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);

        textPath = (TextView) findViewById(R.id.text_path);
        list = (ListView) findViewById(R.id.list);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);

        String start = getIntent().getStringExtra("start");
        File f = (start == null || start.length() == 0) ? new File("/mnt/sdcard") : new File(start);
        while (f != null && !f.exists()) {
            f = f.getParentFile();
        }
        current = (f == null) ? new File("/mnt/sdcard") : f;
        if (!current.isDirectory()) current = current.getParentFile();
        refresh();

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File sel = entries.get(position);
                if (sel == null) {
                    File up = current.getParentFile();
                    if (up != null) {
                        current = up;
                        refresh();
                    }
                } else if (sel.isDirectory()) {
                    current = sel;
                    refresh();
                } else {
                    Toast.makeText(FolderPickerActivity.this,
                            "这是素材文件，不用进入；点右下角「使用此文件夹」即可",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button up = (Button) findViewById(R.id.btn_up);
        up.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File p = current.getParentFile();
                if (p != null) {
                    current = p;
                    refresh();
                }
            }
        });

        Button choose = (Button) findViewById(R.id.btn_choose);
        choose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent data = new Intent();
                data.putExtra("path", current.getAbsolutePath());
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    private void refresh() {
        entries.clear();
        adapter.clear();

        File parent = current.getParentFile();
        if (parent != null) {
            entries.add(null);
            adapter.add(".. 上一级");
        }

        File[] all = current.listFiles();
        List<File> sub = new ArrayList<File>();
        List<File> media = new ArrayList<File>();
        if (all != null) {
            for (File f : all) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) sub.add(f);
                else if (Util.isMedia(f.getName())) media.add(f);
            }
        }
        java.util.Comparator<File> cmp = new java.util.Comparator<File>() {
            public int compare(File a, File b) {
                return Util.naturalCompare(a.getName(), b.getName());
            }
        };
        Collections.sort(sub, cmp);
        Collections.sort(media, cmp);

        for (File d : sub) {
            entries.add(d);
            int count = 0;
            File[] kids = d.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (k.isFile() && Util.isMedia(k.getName())) count++;
                }
            }
            adapter.add("[文件夹] " + d.getName() + "   (" + count + " 个素材)");
        }

        int images = 0, videos = 0;
        for (File f : media) {
            entries.add(f);
            if (Util.isImage(f.getName())) images++; else videos++;
            adapter.add((Util.isImage(f.getName()) ? "[图片] " : "[视频] ")
                    + f.getName() + "   (" + readableSize(f.length()) + ")");
        }

        textPath.setText(current.getAbsolutePath()
                + "\n本文件夹：" + media.size() + " 个素材（图片 " + images + " · 视频 " + videos + "）");
        adapter.notifyDataSetChanged();
    }

    private String readableSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return (bytes / (1024L * 1024L * 1024L)) + " GB";
        if (bytes >= 1024L * 1024L) return (bytes / (1024L * 1024L)) + " MB";
        if (bytes >= 1024L) return (bytes / 1024L) + " KB";
        return bytes + " B";
    }
}
