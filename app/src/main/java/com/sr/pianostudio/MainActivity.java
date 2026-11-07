package com.sr.pianostudio;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    static final int BG = 0xFF0B0E13;
    static final int CARD = 0xFF161B22;
    static final int CARD_HI = 0xFF21262E;
    static final int TEXT = 0xFFEFF3F8;
    static final int TEXT_DIM = 0xFF8A94A3;
    static final int ACCENT = 0xFF62C7FF;
    static final int ACCENT2 = 0xFF7C6BFF;
    static final int GREEN = 0xFF3DDC84;
    static final int RED = 0xFFFF5C7A;

    Handler ui = new Handler(Looper.getMainLooper());
    PianoEngine engine;
    PianoView piano;

    TextView tvStatus;
    Button btnRecord, btnPlay, btnMetro, btnLearn, btnLabel;
    SeekBar sbVolume;

    boolean recording = false;
    long recStart = 0;
    final ArrayList<int[]> recEvents = new ArrayList<int[]>();

    boolean playing = false;
    Runnable playTask;

    boolean metroOn = false;
    int bpm = 90;
    Runnable metroTask;
    int beatCount = 0;

    boolean learnOn = false;
    int[] learnSong = null;
    int learnIdx = 0;
    TextView tvLearnHint;
    LinearLayout learnBar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        engine = new PianoEngine(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(14), dp(10), dp(14), dp(6));
        TextView title = new TextView(this);
        title.setText("Piano Studio");
        title.setTextColor(TEXT);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        tvStatus = new TextView(this);
        tvStatus.setTextColor(TEXT_DIM);
        tvStatus.setTextSize(11);
        tvStatus.setText("Loading 0/88");
        titleBar.addView(tvStatus);
        root.addView(titleBar);

        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setPadding(dp(8), dp(2), dp(8), dp(4));
        btnRecord = mkBtn("录音", ACCENT2);
        btnPlay = mkBtn("回放", GREEN);
        btnMetro = mkBtn("节拍器", ACCENT);
        btnLearn = mkBtn("学习", 0xFFFFC857);
        toolRow.addView(btnRecord, btnLp());
        toolRow.addView(btnPlay, btnLp());
        toolRow.addView(btnMetro, btnLp());
        toolRow.addView(btnLearn, btnLp());
        root.addView(toolRow);

        LinearLayout ctlRow = new LinearLayout(this);
        ctlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctlRow.setGravity(Gravity.CENTER_VERTICAL);
        ctlRow.setPadding(dp(10), 0, dp(10), dp(4));
        TextView volLbl = new TextView(this);
        volLbl.setText("Volume");
        volLbl.setTextSize(12);
        volLbl.setTextColor(TEXT_DIM);
        ctlRow.addView(volLbl);
        sbVolume = new SeekBar(this);
        sbVolume.setMax(100);
        sbVolume.setProgress(80);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, dp(36), 1);
        ctlRow.addView(sbVolume, vp);
        btnLabel = mkBtn("音名", CARD_HI);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, dp(34));
        lp2.setMargins(dp(6), 0, 0, 0);
        ctlRow.addView(btnLabel, lp2);
        root.addView(ctlRow);

        learnBar = new LinearLayout(this);
        learnBar.setOrientation(LinearLayout.HORIZONTAL);
        learnBar.setGravity(Gravity.CENTER_VERTICAL);
        learnBar.setPadding(dp(14), dp(2), dp(14), dp(2));
        learnBar.setVisibility(View.GONE);
        tvLearnHint = new TextView(this);
        tvLearnHint.setTextColor(0xFFFFC857);
        tvLearnHint.setTextSize(13);
        learnBar.addView(tvLearnHint, new LinearLayout.LayoutParams(0, -2, 1));
        Button btnLearnStop = mkBtn("退出", RED);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(-2, dp(30));
        learnBar.addView(btnLearnStop, lsp);
        root.addView(learnBar);

        FrameLayout keyWrap = new FrameLayout(this);
        piano = new PianoView(this);
        piano.setListener(new PianoView.Listener() {
            public void onNoteOn(int midi, float vel) {
                engine.play(midi, vel);
                if (recording) recEvents.add(new int[]{(int)(System.currentTimeMillis() - recStart), midi, 1});
                if (learnOn) checkLearn(midi);
            }
            public void onNoteOff(int midi) {
                if (recording) recEvents.add(new int[]{(int)(System.currentTimeMillis() - recStart), midi, 0});
            }
        });
        keyWrap.addView(piano, new FrameLayout.LayoutParams(-1, -1));

        Button btnL = mkBtn("<", CARD_HI);
        FrameLayout.LayoutParams lllp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.LEFT | Gravity.TOP);
        lllp.setMargins(dp(8), dp(8), 0, 0);
        btnL.setAlpha(0.7f);
        keyWrap.addView(btnL, lllp);
        Button btnR = mkBtn(">", CARD_HI);
        FrameLayout.LayoutParams rllp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.RIGHT | Gravity.TOP);
        rllp.setMargins(0, dp(8), dp(8), 0);
        btnR.setAlpha(0.7f);
        keyWrap.addView(btnR, rllp);

        root.addView(keyWrap, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout chordRow = new LinearLayout(this);
        chordRow.setOrientation(LinearLayout.HORIZONTAL);
        chordRow.setPadding(dp(6), dp(6), dp(6), dp(8));
        String[][] chords = { {"C", "48,52,55"}, {"Dm", "50,53,57"}, {"Em", "52,55,59"}, {"F", "53,57,60"}, {"G", "55,59,62"}, {"Am", "57,60,64"} };
        for (int i = 0; i < chords.length; i++) {
            final String csv = chords[i][1];
            Button cb = mkBtn(chords[i][0], CARD_HI);
            cb.setTextSize(13);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { playChord(csv); }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(40), 1);
            clp.setMargins(dp(3), 0, dp(3), 0);
            chordRow.addView(cb, clp);
        }
        root.addView(chordRow);

        setContentView(root);

        btnRecord.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleRecord(); } });
        btnPlay.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { togglePlay(); } });
        btnMetro.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleMetro(); } });
        btnLearn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleLearn(); } });
        btnLabel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                piano.setShowLabels(!piano.isShowLabels());
                styleBtn(btnLabel, piano.isShowLabels() ? ACCENT2 : CARD_HI);
            }
        });
        btnL.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { piano.scrollBy(-piano.getWidth() * 0.6f); } });
        btnR.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { piano.scrollBy(piano.getWidth() * 0.6f); } });
        btnLearnStop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { stopLearn(); } });

        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { engine.setMasterVolume(p / 100f); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        ui.post(loadPoll);
    }

    Runnable loadPoll = new Runnable() {
        public void run() {
            if (engine.isReady()) {
                tvStatus.setText("OK " + engine.getTotalCount());
                tvStatus.setTextColor(GREEN);
                return;
            }
            tvStatus.setText("Load " + engine.getLoadedCount() + "/" + engine.getTotalCount());
            ui.postDelayed(this, 200);
        }
    };

    private void playChord(String csv) {
        String[] parts = csv.split(",");
        for (int k = 0; k < parts.length; k++) {
            final int m = Integer.parseInt(parts[k].trim());
            engine.play(m, 0.85f);
            piano.externalNoteOn(m);
            ui.postDelayed(new Runnable() { public void run() { piano.externalNoteOff(m); } }, 900);
        }
    }

    private void toggleRecord() {
        if (recording) {
            recording = false;
            styleBtn(btnRecord, ACCENT2);
            btnRecord.setText("录音");
            if (recEvents.isEmpty()) { toast("没有录到内容"); return; }
            toast("已录制 " + recEvents.size() + " 个事件");
        } else {
            if (playing) togglePlay();
            recEvents.clear();
            recording = true;
            recStart = System.currentTimeMillis();
            styleBtn(btnRecord, RED);
            btnRecord.setText("停止");
            toast("开始录音");
        }
    }

    private void togglePlay() {
        if (playing) { stopPlay(); return; }
        if (recEvents.isEmpty()) { toast("先录一段吧"); return; }
        playing = true;
        styleBtn(btnPlay, RED);
        final long t0 = System.currentTimeMillis();
        final int[] idx = {0};
        playTask = new Runnable() {
            public void run() {
                if (!playing) return;
                long now = System.currentTimeMillis() - t0;
                while (idx[0] < recEvents.size()) {
                    int[] ev = recEvents.get(idx[0]);
                    if (ev[0] > now) break;
                    if (ev[2] == 1) {
                        engine.play(ev[1], 0.85f);
                        piano.externalNoteOn(ev[1]);
                    } else {
                        piano.externalNoteOff(ev[1]);
                    }
                    idx[0]++;
                }
                if (idx[0] >= recEvents.size()) { stopPlay(); return; }
                ui.postDelayed(this, 8);
            }
        };
        ui.post(playTask);
    }

    private void stopPlay() {
        playing = false;
        styleBtn(btnPlay, GREEN);
        if (playTask != null) ui.removeCallbacks(playTask);
    }

    private void toggleMetro() {
        if (metroOn) {
            metroOn = false;
            styleBtn(btnMetro, ACCENT);
            if (metroTask != null) ui.removeCallbacks(metroTask);
            return;
        }
        metroOn = true;
        styleBtn(btnMetro, RED);
        beatCount = 0;
        metroTask = new Runnable() {
            public void run() {
                if (!metroOn) return;
                boolean accent = (beatCount % 4 == 0);
                engine.play(accent ? 96 : 84, accent ? 0.9f : 0.5f);
                beatCount++;
                ui.postDelayed(this, (long) (60000.0 / bpm));
            }
        };
        ui.post(metroTask);
    }

    private void toggleLearn() {
        if (learnOn) { stopLearn(); return; }
        learnSong = new int[]{60,60,67,67,69,69,67,65,65,64,64,62,62,60};
        learnOn = true;
        learnIdx = 0;
        learnBar.setVisibility(View.VISIBLE);
        updateLearnHint();
        toast("按提示弹奏：小星星");
    }

    private void updateLearnHint() {
        if (!learnOn || learnSong == null) return;
        if (learnIdx >= learnSong.length) { tvLearnHint.setText("太棒了，弹完整首！"); return; }
        tvLearnHint.setText("第 " + (learnIdx + 1) + "/" + learnSong.length + " 音: " + noteName(learnSong[learnIdx]));
    }

    private void checkLearn(int midi) {
        if (!learnOn || learnSong == null) return;
        if (learnIdx >= learnSong.length) return;
        if (midi == learnSong[learnIdx]) {
            learnIdx++;
            if (learnIdx >= learnSong.length) {
                tvLearnHint.setText("太棒了，弹完整首！");
                engine.play(96, 0.9f);
            } else {
                updateLearnHint();
            }
        }
    }

    private void stopLearn() {
        learnOn = false;
        learnSong = null;
        learnBar.setVisibility(View.GONE);
    }

    private String noteName(int midi) {
        String[] n = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
        return n[((midi % 12) + 12) % 12] + (midi / 12 - 1);
    }

    private Button mkBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(makeBg(color, 10));
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private void styleBtn(Button b, int color) {
        b.setBackground(makeBg(color, 10));
    }

    private GradientDrawable makeBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playing = false; metroOn = false;
        if (playTask != null) ui.removeCallbacks(playTask);
        if (metroTask != null) ui.removeCallbacks(metroTask);
        if (engine != null) engine.release();
    }
}