package com.flutteride.codweva;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/**
 * عارض صوتي بأسلوب أفقي بسيط:
 *   - بطاقة من الأعلى لأسفل (تملأ العرض، ارتفاع صغير).
 *   - صف علوي: أيقونة دائرية (56dp) + اسم الملف + معلومات.
 *   - شريط تقدم مع الأوقات.
 *   - أزرار: تشغيل/إيقاف، إعادة.
 */
public final class AudioViewer extends FrameLayout {

    private static final int COLOR_BG = 0xFF0D1117;
    private static final int COLOR_SURFACE = 0xFF161B22;
    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
    private static final int COLOR_PRIMARY_LIGHT = 0xFF58A6FF;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_MUTED = 0xFF8B949E;
    private static final int COLOR_BORDER = 0xFF30363D;

    private final Context context;
    private MediaPlayer mediaPlayer;
    private String currentPath;

    private TextView nameView;
    private TextView metaView;
    private TextView timeCurrentView;
    private TextView timeTotalView;
    private SeekBar seekBar;
    private Button btnPlay;
    private Button btnRestart;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressUpdater;
    private boolean isUserSeeking = false;
    private int durationMs = 0;

    public AudioViewer(Context context) {
        super(context);
        this.context = context;
        setBackgroundColor(COLOR_BG);
        buildLayout();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ============================================================
    // البناء
    // ============================================================
    private void buildLayout() {
        setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        // حاوية عمودية تتوسط عمودياً وأفقياً
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setPadding(dp(16), dp(16), dp(16), dp(16));

        FrameLayout.LayoutParams wrapperParams = new FrameLayout.LayoutParams(-1, -1);
        addView(wrapper, wrapperParams);

        // ✅ بطاقة بسيطة: عرض كامل (ناقص padding)، ارتفاع حسب المحتوى
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(UiStyle.rounded(context, COLOR_SURFACE, 16, COLOR_BORDER, 1));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapper.addView(card, cardParams);

        // ═══════════════════════════════════════════════════════
        // الصف 1: أيقونة + نصوص
        // ═══════════════════════════════════════════════════════
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // أيقونة دائرية 56dp — باستخدام ImageView بحجم ثابت
        ImageView icon = new ImageView(context);
        int iconSize = dp(56);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(iconSize, iconSize);
        icon.setLayoutParams(iconParams);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // خلفية دائرية زرقاء
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(COLOR_PRIMARY);
        icon.setBackground(iconBg);

        // الصورة الرمزية (نوتة موسيقية)
        icon.setImageDrawable(new MusicNoteDrawable());
        topRow.addView(icon);

        // عمود النصوص
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textColParams.setMarginStart(dp(12));
        topRow.addView(textCol, textColParams);

        nameView = new TextView(context);
        nameView.setTextColor(COLOR_TEXT);
        nameView.setTextSize(14);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        textCol.addView(nameView, new LinearLayout.LayoutParams(-1, -2));

        metaView = new TextView(context);
        metaView.setTextColor(COLOR_MUTED);
        metaView.setTextSize(11);
        metaView.setTypeface(android.graphics.Typeface.MONOSPACE);
        metaView.setSingleLine(true);
        metaView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(2);
        textCol.addView(metaView, metaParams);

        card.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        // ═══════════════════════════════════════════════════════
        // الصف 2: شريط التقدم
        // ═══════════════════════════════════════════════════════
        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timeRowParams = new LinearLayout.LayoutParams(-1, -2);
        timeRowParams.topMargin = dp(14);
        card.addView(timeRow, timeRowParams);

        timeCurrentView = new TextView(context);
        timeCurrentView.setTextColor(COLOR_TEXT);
        timeCurrentView.setTextSize(10);
        timeCurrentView.setTypeface(android.graphics.Typeface.MONOSPACE);
        timeCurrentView.setText("00:00");
        timeCurrentView.setMinWidth(dp(36));
        timeCurrentView.setGravity(Gravity.CENTER);
        timeRow.addView(timeCurrentView);

        seekBar = new SeekBar(context);
        seekBar.setMax(1000);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, dp(28), 1f);
        seekParams.setMarginStart(dp(6));
        seekParams.setMarginEnd(dp(6));
        timeRow.addView(seekBar, seekParams);

        timeTotalView = new TextView(context);
        timeTotalView.setTextColor(COLOR_TEXT);
        timeTotalView.setTextSize(10);
        timeTotalView.setTypeface(android.graphics.Typeface.MONOSPACE);
        timeTotalView.setText("00:00");
        timeTotalView.setMinWidth(dp(36));
        timeTotalView.setGravity(Gravity.CENTER);
        timeRow.addView(timeTotalView);

        // ═══════════════════════════════════════════════════════
        // الصف 3: الأزرار
        // ═══════════════════════════════════════════════════════
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(-1, -2);
        btnRowParams.topMargin = dp(12);
        card.addView(btnRow, btnRowParams);

        btnPlay = makeControlButton("تشغيل", true);
        btnPlay.setOnClickListener(v -> togglePlay());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, dp(42), 2f);
        playParams.setMarginEnd(dp(5));
        btnRow.addView(btnPlay, playParams);

        btnRestart = makeControlButton("إعادة", false);
        btnRestart.setOnClickListener(v -> restart());
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        restartParams.setMarginStart(dp(5));
        btnRow.addView(btnRestart, restartParams);

        // ═══════════════════════════════════════════════════════
        // SeekBar Listener
        // ═══════════════════════════════════════════════════════
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && durationMs > 0) {
                    int pos = (int) ((long) durationMs * progress / 1000L);
                    mediaPlayer.seekTo(pos);
                    timeCurrentView.setText(formatTime(pos));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) { isUserSeeking = false; }
        });
    }

    private Button makeControlButton(String text, boolean primary) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(primary ? 0xFFFFFFFF : COLOR_TEXT);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(6), 0, dp(6), 0);
        btn.setBackground(UiStyle.rounded(context,
                primary ? COLOR_PRIMARY : COLOR_SURFACE_ALT,
                10,
                primary ? COLOR_PRIMARY_LIGHT : COLOR_BORDER,
                1));
        return btn;
    }

    // ============================================================
    // تحميل الصوت
    // ============================================================
    public void loadAudio(String path) {
        this.currentPath = path;
        this.isUserSeeking = false;
        this.durationMs = 0;
        release();

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            showError("الملف غير موجود");
            return;
        }

        nameView.setText(file.getName());
        metaView.setText(formatSize(file.length()));

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    durationMs = mp.getDuration();
                    timeTotalView.setText(formatTime(durationMs));
                    File f = new File(path);
                    metaView.setText(formatSize(f.length()) + "  •  " + formatTime(durationMs));
                    try {
                        mp.start();
                        btnPlay.setText("إيقاف");
                        startProgressUpdater();
                    } catch (Exception ignored) {}
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    btnPlay.setText("تشغيل");
                    seekBar.setProgress(1000);
                    stopProgressUpdater();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    showError("تعذر تشغيل الملف الصوتي (خطأ " + what + ")");
                    btnPlay.setText("تشغيل");
                    stopProgressUpdater();
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            showError("تعذر تحميل الملف الصوتي: " + e.getMessage());
        }
    }

    // ============================================================
    // التحكم
    // ============================================================
    private void togglePlay() {
        if (mediaPlayer == null) return;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlay.setText("تشغيل");
                stopProgressUpdater();
            } else {
                mediaPlayer.start();
                btnPlay.setText("إيقاف");
                startProgressUpdater();
            }
        } catch (Exception ignored) {}
    }

    private void restart() {
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.seekTo(0);
            seekBar.setProgress(0);
            timeCurrentView.setText("00:00");
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                btnPlay.setText("إيقاف");
            }
            startProgressUpdater();
        } catch (Exception ignored) {}
    }

    // ============================================================
    // تحديث شريط التقدم
    // ============================================================
    private void startProgressUpdater() {
        stopProgressUpdater();
        progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer == null) return;
                try {
                    int position = mediaPlayer.getCurrentPosition();
                    if (durationMs > 0) {
                        int progress = (int) ((long) position * 1000L / durationMs);
                        if (!isUserSeeking) {
                            seekBar.setProgress(progress);
                            timeCurrentView.setText(formatTime(position));
                        }
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 500L);
            }
        };
        handler.post(progressUpdater);
    }

    private void stopProgressUpdater() {
        if (progressUpdater != null) {
            handler.removeCallbacks(progressUpdater);
            progressUpdater = null;
        }
    }

    // ============================================================
    // دورة الحياة
    // ============================================================
    public void release() {
        stopProgressUpdater();
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    public String getCurrentPath() {
        return currentPath;
    }

    // ============================================================
    // أدوات
    // ============================================================
    private void showError(String msg) {
        if (metaView != null) metaView.setText(msg);
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    private String formatTime(int ms) {
        if (ms < 0) ms = 0;
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        int hour = min / 60;
        if (hour > 0) {
            min = min % 60;
            return String.format(Locale.US, "%d:%02d:%02d", hour, min, sec);
        }
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    // ============================================================
    // أيقونة نوتة موسيقية كـ Drawable (بحجم محدد)
    // ============================================================
    private static final class MusicNoteDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final int COLOR_NOTE = 0xFFFFFFFF;

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (bounds == null || bounds.width() == 0 || bounds.height() == 0) return;

            float w = bounds.width();
            float h = bounds.height();
            float cx = bounds.left + w / 2f;
            float cy = bounds.top + h / 2f;
            float size = Math.min(w, h);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_NOTE);

            // رأس النوتة (بيضاوي أبيض)
            float noteCx = cx - size * 0.05f;
            float noteCy = cy + size * 0.15f;
            canvas.drawOval(
                    noteCx - size * 0.18f,
                    noteCy - size * 0.12f,
                    noteCx + size * 0.18f,
                    noteCy + size * 0.12f,
                    paint);

            // ساق النوتة (مستطيل رفيع)
            float stemX = noteCx + size * 0.14f;
            float stemTop = cy - size * 0.38f;
            float stemBottom = noteCy;
            canvas.drawRect(stemX - size * 0.025f, stemTop, stemX + size * 0.025f, stemBottom, paint);

            // رأس صغير على قمة الساق
            canvas.drawCircle(stemX, stemTop, size * 0.055f, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}