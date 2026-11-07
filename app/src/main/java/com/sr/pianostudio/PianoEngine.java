package com.sr.pianostudio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;

import java.util.HashMap;

/**
 * 真实钢琴采样播放引擎。
 * 使用 SoundPool 加载 88 键真实钢琴采样（FluidR3_GM 声库），低延迟多声部并发。
 */
public class PianoEngine {

    private static final String[] NAMES = {"c","db","d","eb","e","f","gb","g","ab","a","bb","b"};
    public static final int LOW = 21;    // A0
    public static final int HIGH = 108;  // C8

    private SoundPool pool;
    private final HashMap<Integer, Integer> soundIds = new HashMap<Integer, Integer>();
    private volatile int loadedCount = 0;
    private volatile int totalCount = 1;
    private volatile boolean ready = false;
    private float master = 1.0f;

    public PianoEngine(Context ctx) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(48)
                .setAudioAttributes(attrs)
                .build();
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                loadedCount++;
                if (loadedCount >= totalCount) ready = true;
            }
        });

        int count = 0;
        for (int m = LOW; m <= HIGH; m++) {
            String nm = nameOf(m);
            int resId = ctx.getResources().getIdentifier(nm, "raw", ctx.getPackageName());
            if (resId != 0) {
                int id = pool.load(ctx, resId, 1);
                soundIds.put(m, id);
                count++;
            }
        }
        totalCount = Math.max(1, count);
    }

    /** midi 转文件名，如 60 -> c4, 61 -> db4 */
    public static String nameOf(int midi) {
        int n = ((midi % 12) + 12) % 12;
        int oct = midi / 12 - 1;
        return NAMES[n] + oct;
    }

    public boolean isReady() { return ready; }
    public int getLoadedCount() { return loadedCount; }
    public int getTotalCount() { return totalCount; }

    public void setMasterVolume(float v) {
        master = Math.max(0f, Math.min(1f, v));
    }

    public float getMasterVolume() { return master; }

    /** 播放一个音，velocity 0..1 */
    public void play(int midi, float velocity) {
        if (!ready && loadedCount < 1) return;
        Integer sid = soundIds.get(midi);
        if (sid == null) return;
        float v = master * (0.30f + 0.70f * Math.max(0f, Math.min(1f, velocity)));
        try {
            pool.play(sid, v, v, 1, 0, 1.0f);
        } catch (Exception ignored) {}
    }

    public void release() {
        if (pool != null) {
            try { pool.release(); } catch (Exception ignored) {}
            pool = null;
        }
        soundIds.clear();
    }
}