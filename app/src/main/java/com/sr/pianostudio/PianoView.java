package com.sr.pianostudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * 钢琴键盘视图。支持多点触控、滑奏（拖动跨键自动触发），可横向滚动。
 */
public class PianoView extends View {

    public interface Listener {
        void onNoteOn(int midi, float velocity);
        void onNoteOff(int midi);
    }

    // 显示范围（以 C 对齐的白键起点）
    private int startMidi = 24;   // C1
    private int endMidi = 96;     // C7
    private int whiteCount = 0;

    private final Paint pWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pWhiteBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBlackHi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pActiveW = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pActiveB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Map<Integer, RectF> whiteRects = new HashMap<Integer, RectF>();
    private final Map<Integer, RectF> blackRects = new HashMap<Integer, RectF>();
    private final Map<Integer, Boolean> active = new HashMap<Integer, Boolean>();
    private final Map<Integer, Float> activeVel = new HashMap<Integer, Float>();

    private Listener listener;
    private float keyWidth = 60f;
    private float scrollX = 0f;
    private boolean showLabels = false;

    public PianoView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        pWhite.setColor(0xFFFAFAFA);
        pWhiteBorder.setStyle(Paint.Style.STROKE);
        pWhiteBorder.setColor(0xFFB0B6BE);
        pWhiteBorder.setStrokeWidth(2f);
        pBlack.setColor(0xFF14171C);
        pBlackHi.setColor(0xFF2A2F38);
        pActiveW.setColor(0xFF62C7FF);
        pActiveB.setColor(0xFF3AA8F0);
        pText.setColor(0xFF6B7280);
        pText.setTextAlign(Paint.Align.CENTER);
        pGlow.setStyle(Paint.Style.FILL);

        if (isInEditMode()) return;
    }

    public void setListener(Listener l) { this.listener = l; }
    public void setShowLabels(boolean b) { showLabels = b; invalidate(); }
    public boolean isShowLabels() { return showLabels; }

    public void setRange(int start, int end) {
        startMidi = start; endMidi = end;
        recountWhite(); invalidate();
    }

    private void recountWhite() {
        int c = 0;
        for (int m = startMidi; m <= endMidi; m++) if (!isBlack(m)) c++;
        whiteCount = c;
    }

    private static boolean isBlack(int midi) {
        int n = ((midi % 12) + 12) % 12;
        return n == 1 || n == 3 || n == 6 || n == 8 || n == 10;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // 默认把整个范围塞进屏幕宽度（至少 7 个白键宽）
        if (whiteCount == 0) recountWhite();
        keyWidth = Math.max(w / 7f, (float) w / Math.max(1, whiteCount));
        // 若白键太多放不下，强制一屏显示但保证可读，采用滚动
        if (whiteCount > 15) keyWidth = Math.max(w / 15f, keyWidth);
        layoutRects();
    }

    private void layoutRects() {
        whiteRects.clear();
        blackRects.clear();
        float h = getHeight();
        float whiteH = h;
        float blackH = h * 0.62f;

        float x = 0f;
        for (int m = startMidi; m <= endMidi; m++) {
            if (!isBlack(m)) {
                whiteRects.put(m, new RectF(x, 0, x + keyWidth, whiteH));
                x += keyWidth;
            }
        }
        // 黑键定位：位于相邻白键交界处
        for (int m = startMidi; m <= endMidi; m++) {
            if (isBlack(m)) {
                int leftWhite = m - 1;
                RectF lr = whiteRects.get(leftWhite);
                if (lr == null) continue;
                float cx = lr.right;
                float bw = keyWidth * 0.60f;
                blackRects.put(m, new RectF(cx - bw / 2f, 0, cx + bw / 2f, blackH));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(-scrollX, 0);

        float h = getHeight();

        // 白键
        for (Map.Entry<Integer, RectF> e : whiteRects.entrySet()) {
            int m = e.getKey();
            RectF r = e.getValue();
            boolean act = active.containsKey(m);
            if (act) {
                pGlow.setColor(0x5562C7FF);
                canvas.drawRoundRect(new RectF(r.left + 2, r.top, r.right - 2, r.bottom), 6, 6, pGlow);
                pActiveW.setColor(0xFF8FD8FF);
                canvas.drawRoundRect(r, 8, 8, pActiveW);
            } else {
                pWhite.setShader(new LinearGradient(r.left, 0, r.left, h,
                        new int[]{0xFFFFFFFF, 0xFFEFEFF2}, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, 8, 8, pWhite);
                pWhite.setShader(null);
            }
            canvas.drawRoundRect(r, 8, 8, pWhiteBorder);

            if (showLabels && !isBlack(m)) {
                pText.setTextSize(keyWidth * 0.28f);
                pText.setColor(act ? 0xFF0B3A5A : 0xFF9AA4B2);
                canvas.drawText(noteNameCn(m), r.centerX(), r.bottom - 10f, pText);
            }
        }

        // 黑键
        for (Map.Entry<Integer, RectF> e : blackRects.entrySet()) {
            int m = e.getKey();
            RectF r = e.getValue();
            boolean act = active.containsKey(m);
            if (act) {
                pActiveB.setColor(0xFF5FC0FF);
                canvas.drawRoundRect(r, 6, 6, pActiveB);
            } else {
                pBlack.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                        new int[]{0xFF2B313A, 0xFF101317}, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, 6, 6, pBlack);
                pBlack.setShader(null);
                // 顶部高光
                pBlackHi.setColor(0x33FFFFFF);
                canvas.drawRoundRect(new RectF(r.left + 3, r.top + 3, r.right - 3, r.top + r.height() * 0.25f), 4, 4, pBlackHi);
            }
        }

        canvas.restore();
    }

    private String noteNameCn(int midi) {
        String[] n = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
        return n[((midi % 12) + 12) % 12] + (midi / 12 - 1);
    }

    private int hitTest(float x, float y) {
        float tx = x + scrollX;
        // 先测黑键
        for (Map.Entry<Integer, RectF> e : blackRects.entrySet()) {
            if (e.getValue().contains(tx, y)) return e.getKey();
        }
        for (Map.Entry<Integer, RectF> e : whiteRects.entrySet()) {
            if (e.getValue().contains(tx, y)) return e.getKey();
        }
        return -1;
    }

    // pointerId -> midi
    private final Map<Integer, Integer> pointerNote = new HashMap<Integer, Integer>();
    private float lastX = 0f;
    private boolean dragging = false;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = ev.getActionIndex();
                int pid = ev.getPointerId(idx);
                float x = ev.getX(idx), y = ev.getY(idx);
                int m = hitTest(x, y);
                if (m >= 0) {
                    pointerNote.put(pid, m);
                    triggerOn(m, x, y);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int pid = ev.getPointerId(i);
                    if (!pointerNote.containsKey(pid)) continue;
                    float x = ev.getX(i), y = ev.getY(i);
                    int m = hitTest(x, y);
                    int cur = pointerNote.get(pid);
                    if (m >= 0 && m != cur) {
                        triggerOff(cur);
                        pointerNote.put(pid, m);
                        triggerOn(m, x, y);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = ev.getActionIndex();
                int pid = ev.getPointerId(idx);
                Integer m = pointerNote.remove(pid);
                if (m != null) triggerOff(m);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                for (Integer m : pointerNote.values()) triggerOff(m);
                pointerNote.clear();
                break;
            }
        }
        return true;
    }

    private void triggerOn(int midi, float x, float y) {
        active.put(midi, true);
        float vel = 0.8f;
        activeVel.put(midi, vel);
        invalidate();
        if (listener != null) listener.onNoteOn(midi, vel);
    }

    private void triggerOff(int midi) {
        active.remove(midi);
        activeVel.remove(midi);
        invalidate();
        if (listener != null) listener.onNoteOff(midi);
    }

    /** 外部高亮（如播放录音） */
    public void flashNote(int midi, final long ms) {
        active.put(midi, true);
        invalidate();
        postDelayed(new Runnable() {
            public void run() { /* keep simple */ }
        }, ms);
    }

    public void externalNoteOn(int midi) {
        active.put(midi, true);
        invalidate();
    }

    public void externalNoteOff(int midi) {
        active.remove(midi);
        invalidate();
    }

    public void scrollBy(float dx) {
        scrollX += dx;
        float contentW = 0;
        for (RectF r : whiteRects.values()) contentW = Math.max(contentW, r.right);
        float maxScroll = Math.max(0, contentW - getWidth());
        if (scrollX < 0) scrollX = 0;
        if (scrollX > maxScroll) scrollX = maxScroll;
        invalidate();
    }

    public int getStartMidi() { return startMidi; }
}