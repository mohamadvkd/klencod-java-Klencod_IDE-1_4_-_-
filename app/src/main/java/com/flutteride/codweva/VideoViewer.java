package com.flutteride.codweva;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
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
import android.widget.VideoView;

import java.io.File;
import java.util.Locale;

/**
 * عارض فيديو مع:
 *   - VideoView (يتوسط الشاشة).
 *   - أزرار: تشغيل/إيقاف، إعادة.
 *   - شريط تقدم قابل للسحب.
 *   - عرض الوقت الحالي / المدة.
 *   - معلومات الملف.
 */
public final class VideoViewer extends FrameLayout {

    private static final int COLOR_BG = 0xFF0D1117;
    private static final int COLOR_SURFACE = 0xFF161B22;
    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
    private static final int COLOR_PRIMARY_LIGHT = 0xFF58A6FF;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_MUTED = 0xFF8B949E;
    private static final int COLOR_BORDER = 0xFF30363D;

    private final Context context;
    private VideoView videoView;
    private TextView infoView;
    private TextView timeCurrentView;
    private TextView timeTotalView;
    private SeekBar seekBar;
    private Button btnPlay;
    private Button btnRestart;

    private String currentPath;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressUpdater;

    /** لتجنب التحديث أثناء سحب المستخدم للشريط. */
    private boolean isUserSeeking = false;

    /** أول مرة نُشغّل تلقائياً. */
    private boolean autoPlayed = false;

    public VideoViewer(Context context) {
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

        // 1) عرض الفيديو (يملأ المساحة العليا)
        FrameLayout videoContainer = new FrameLayout(context);
        videoContainer.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(context);
        FrameLayout.LayoutParams vvParams = new FrameLayout.LayoutParams(-1, -1);
        vvParams.gravity = Gravity.CENTER;
        videoContainer.addView(videoView, vvParams);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(-1, -1);
        containerParams.bottomMargin = dp(140); // مساحة لشريط التحكم
        addView(videoContainer, containerParams);

        // 2) معلومات الملف
        infoView = new TextView(context);
        infoView.setTextColor(COLOR_MUTED);
        infoView.setTextSize(10);
        infoView.setTypeface(android.graphics.Typeface.MONOSPACE);
        infoView.setPadding(dp(12), dp(4), dp(12), dp(4));
        infoView.setBackgroundColor(COLOR_SURFACE_ALT);
        infoView.setGravity(Gravity.CENTER);
        infoView.setSingleLine(true);
        infoView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);

        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(-1, dp(24));
        infoParams.gravity = Gravity.BOTTOM;
        infoParams.bottomMargin = dp(116);
        addView(infoView, infoParams);

        // 3) منطقة التحكم (أسفل الشاشة)
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(8));
        controls.setBackgroundColor(COLOR_SURFACE);

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(-1, dp(116));
        controlsParams.gravity = Gravity.BOTTOM;
        addView(controls, controlsParams);

        // صف 1: الوقت + شريط التقدم
        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);

        timeCurrentView = new TextView(context);
        timeCurrentView.setTextColor(COLOR_TEXT);
        timeCurrentView.setTextSize(11);
        timeCurrentView.setTypeface(android.graphics.Typeface.MONOSPACE);
        timeCurrentView.setText("00:00");
        timeCurrentView.setMinWidth(dp(48));
        timeCurrentView.setGravity(Gravity.CENTER);
        timeRow.addView(timeCurrentView);

        seekBar = new SeekBar(context);
        seekBar.setMax(1000);
        seekBar.setProgress(0);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        seekParams.setMarginStart(dp(6));
        seekParams.setMarginEnd(dp(6));
        timeRow.addView(seekBar, seekParams);

        timeTotalView = new TextView(context);
        timeTotalView.setTextColor(COLOR_TEXT);
        timeTotalView.setTextSize(11);
        timeTotalView.setTypeface(android.graphics.Typeface.MONOSPACE);
        timeTotalView.setText("00:00");
        timeTotalView.setMinWidth(dp(48));
        timeTotalView.setGravity(Gravity.CENTER);
        timeRow.addView(timeTotalView);

        controls.addView(timeRow, new LinearLayout.LayoutParams(-1, dp(44)));

        // صف 2: الأزرار
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        btnPlay = makeControlButton("تشغيل", true);
        btnPlay.setOnClickListener(v -> togglePlay());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, dp(44), 2f);
        playParams.setMarginEnd(dp(4));
        btnRow.addView(btnPlay, playParams);

        btnRestart = makeControlButton("إعادة", false);
        btnRestart.setOnClickListener(v -> restart());
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        restartParams.setMarginStart(dp(4));
        btnRow.addView(btnRestart, restartParams);

        controls.addView(btnRow, new LinearLayout.LayoutParams(-1, dp(52)));

        // 4) مستمعو SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && videoView != null) {
                    int duration = videoView.getDuration();
                    if (duration > 0) {
                        int pos = (int) ((long) duration * progress / 1000L);
                        videoView.seekTo(pos);
                        timeCurrentView.setText(formatTime(pos));
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isUserSeeking = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
            }
        });
    }

    private Button makeControlButton(String text, boolean primary) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(primary ? 0xFFFFFFFF : COLOR_TEXT);
        btn.setTextSize(12);
        btn.setAllCaps(false);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(8), 0, dp(8), 0);
        btn.setBackground(UiStyle.rounded(context,
                primary ? COLOR_PRIMARY : COLOR_SURFACE_ALT,
                10,
                primary ? COLOR_PRIMARY_LIGHT : COLOR_BORDER,
                1));
        return btn;
    }

    // ============================================================
    // تحميل الفيديو
    // ============================================================
    public void loadVideo(String path) {
        this.currentPath = path;
        this.autoPlayed = false;
        this.isUserSeeking = false;

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            showError("الملف غير موجود");
            return;
        }

        infoView.setText(file.getName() + "  •  " + formatSize(file.length()));

        try {
            videoView.setVideoPath(path);
        } catch (Exception e) {
            showError("تعذر تحميل الفيديو: " + e.getMessage());
            return;
        }

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                int duration = mp.getDuration();
                timeTotalView.setText(formatTime(duration));
                // تشغيل تلقائي أول مرة
                if (!autoPlayed) {
                    autoPlayed = true;
                    videoView.start();
                    btnPlay.setText("إيقاف");
                }
                startProgressUpdater();
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                btnPlay.setText("تشغيل");
                seekBar.setProgress(1000);
                stopProgressUpdater();
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                showError("تعذر تشغيل الفيديو (خطأ " + what + ")");
                btnPlay.setText("تشغيل");
                stopProgressUpdater();
                return true;
            }
        });
    }

    // ============================================================
    // التحكم
    // ============================================================
    private void togglePlay() {
        if (videoView == null) return;
        if (videoView.isPlaying()) {
            videoView.pause();
            btnPlay.setText("تشغيل");
            stopProgressUpdater();
        } else {
            videoView.start();
            btnPlay.setText("إيقاف");
            startProgressUpdater();
        }
    }

    private void restart() {
        if (videoView == null) return;
        videoView.seekTo(0);
        seekBar.setProgress(0);
        timeCurrentView.setText("00:00");
        if (!videoView.isPlaying()) {
            videoView.start();
            btnPlay.setText("إيقاف");
        }
        startProgressUpdater();
    }

    // ============================================================
    // تحديث شريط التقدم
    // ============================================================
    private void startProgressUpdater() {
        stopProgressUpdater();
        progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (videoView == null) return;
                try {
                    int duration = videoView.getDuration();
                    int position = videoView.getCurrentPosition();
                    if (duration > 0) {
                        int progress = (int) ((long) position * 1000L / duration);
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
        try {
            if (videoView != null) {
                if (videoView.isPlaying()) videoView.stopPlayback();
                videoView.suspend();
            }
        } catch (Exception ignored) {}
    }

    public String getCurrentPath() {
        return currentPath;
    }

    // ============================================================
    // أدوات
    // ============================================================
    private void showError(String msg) {
        if (infoView != null) infoView.setText(msg);
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
}