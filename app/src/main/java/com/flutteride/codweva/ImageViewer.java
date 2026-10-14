package com.flutteride.codweva;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/**
 * عارض صور كامل:
 *   - عرض بحجم الشاشة (مع تقليص تلقائي للصور الضخمة).
 *   - تكبير بإصبعين (بحدود MIN_SCALE..MAX_SCALE).
 *   - تحريك (pan) عند التكبير.
 *   - نقرة مزدوجة → إعادة تعيين.
 *   - أزرار تحكم أسفل.
 *   - معلومات الملف في الأسفل.
 */
public final class ImageViewer extends FrameLayout {

    private static final int COLOR_BG = 0xFF0D1117;
    private static final int COLOR_SURFACE = 0xFF161B22;
    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_MUTED = 0xFF8B949E;
    private static final int COLOR_BORDER = 0xFF30363D;

    /**
     * حدود الزووم.
     * NOTE: القيم هنا نسبية إلى fitScale الأولي (بعد التوسيط).
     *       إذا أردت أن تكون الحدود مطلقة (نسبة من الحجم الأصلي)، فهذا يتطلب
     *       تتبع fitScale بشكل منفصل. حالياً الحدود بسيطة: 0.5× إلى 5× من fitScale.
     */
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 5.0f;

    private final Context context;
    private ImageView imageView;
    private TextView infoView;

    private Bitmap currentBitmap;
    private String currentPath;

    // ============================================================
    // حالة اللمس
    // ============================================================
    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    private final PointF start = new PointF();
    private final PointF mid = new PointF();
    private float oldDist = 1f;

    // للنقر المزدوج
    private long lastTapTime = 0L;

    public ImageViewer(Context context) {
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

        // 1) حاوية الصورة
        FrameLayout imageContainer = new FrameLayout(context);
        imageContainer.setBackgroundColor(COLOR_BG);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(-1, -1);
        containerParams.bottomMargin = dp(96);
        addView(imageContainer, containerParams);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setBackgroundColor(COLOR_BG);
        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(-1, -1);
        imageContainer.addView(imageView, imgParams);

        // 2) شريط المعلومات
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
        infoParams.bottomMargin = dp(72);
        addView(infoView, infoParams);

        // 3) شريط الأزرار
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setPadding(dp(8), dp(8), dp(8), dp(8));
        toolbar.setBackgroundColor(COLOR_SURFACE);

        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(-1, dp(72));
        toolbarParams.gravity = Gravity.BOTTOM;
        addView(toolbar, toolbarParams);

        Button btnZoomOut = makeToolbarButton("−");
        btnZoomOut.setOnClickListener(v -> applyZoom(1f / 1.25f));
        toolbar.addView(btnZoomOut, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button btnReset = makeToolbarButton("إعادة تعيين");
        btnReset.setTextSize(12);
        btnReset.setOnClickListener(v -> resetView());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(52), 1.4f);
        resetParams.setMarginStart(dp(6));
        resetParams.setMarginEnd(dp(6));
        toolbar.addView(btnReset, resetParams);

        Button btnZoomIn = makeToolbarButton("+");
        btnZoomIn.setOnClickListener(v -> applyZoom(1.25f));
        toolbar.addView(btnZoomIn, new LinearLayout.LayoutParams(0, dp(52), 1f));

        // 4) إعداد اللمس
        setupTouch();
    }

    private Button makeToolbarButton(String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(COLOR_TEXT);
        btn.setTextSize(16);
        btn.setAllCaps(false);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(8), 0, dp(8), 0);
        btn.setBackground(UiStyle.rounded(context, COLOR_SURFACE_ALT, 10, COLOR_BORDER, 1));
        return btn;
    }

    // ============================================================
    // تحميل صورة
    // ============================================================
    public void loadImage(String path) {
        this.currentPath = path;
        this.currentBitmap = null;
        this.matrix.reset();
        this.savedMatrix.reset();
        imageView.setImageBitmap(null);

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            showError("الملف غير موجود");
            return;
        }

        // 1) قراءة الأبعاد
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);

        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            showError("تعذر قراءة الصورة");
            return;
        }

        int origW = opts.outWidth;
        int origH = opts.outHeight;

        // 2) حساب inSampleSize
        int reqW = 2048;
        int reqH = 2048;
        int sample = 1;
        while ((origW / (sample * 2)) >= reqW && (origH / (sample * 2)) >= reqH) {
            sample *= 2;
        }

        // 3) فك الصورة
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap tempBmp = null;
        try {
            tempBmp = BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError oom) {
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                tempBmp = BitmapFactory.decodeFile(path, opts);
            } catch (OutOfMemoryError oom2) {
                showError("الصورة كبيرة جداً للعرض");
                return;
            }
        }

        if (tempBmp == null) {
            showError("تعذر فك ترميز الصورة");
            return;
        }

        final Bitmap bmp = tempBmp;
        currentBitmap = bmp;
        imageView.setImageBitmap(bmp);

        // 4) ضبط المصفوفة الأولية
        post(() -> {
            if (imageView.getWidth() == 0 || imageView.getHeight() == 0) return;
            if (bmp.isRecycled()) return;
            matrix.reset();
            float viewW = imageView.getWidth();
            float viewH = imageView.getHeight();
            float imgW = bmp.getWidth();
            float imgH = bmp.getHeight();
            float fitScale = Math.min(viewW / imgW, viewH / imgH);
            if (fitScale <= 0 || Float.isNaN(fitScale) || Float.isInfinite(fitScale)) fitScale = 1f;
            matrix.postScale(fitScale, fitScale);
            float dx = (viewW - imgW * fitScale) / 2f;
            float dy = (viewH - imgH * fitScale) / 2f;
            matrix.postTranslate(dx, dy);
            imageView.setImageMatrix(matrix);
        });

        // 5) تحديث سطر المعلومات
        updateInfo(file, origW, origH, sample);
    }

    private void updateInfo(File file, int origW, int origH, int sample) {
        StringBuilder sb = new StringBuilder();
        sb.append(file.getName());
        sb.append("  •  ").append(formatSize(file.length()));
        sb.append("  •  ").append(origW).append("×").append(origH);
        if (sample > 1) {
            sb.append(" (مُقلّصة 1/").append(sample).append(")");
        }
        infoView.setText(sb.toString());
    }

    private void showError(String msg) {
        if (infoView != null) infoView.setText(msg);
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    // ============================================================
    // اللمس
    // ============================================================
    private void setupTouch() {
        imageView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        savedMatrix.set(matrix);
                        start.set(event.getX(), event.getY());
                        mode = DRAG;
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        oldDist = spacing(event);
                        if (oldDist > 10f) {
                            savedMatrix.set(matrix);
                            midPoint(mid, event);
                            mode = ZOOM;
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                            imageView.setImageMatrix(matrix);
                        } else if (mode == ZOOM) {
                            float newDist = spacing(event);
                            if (newDist > 10f) {
                                // ✅ ابدأ من المصفوفة المحفوظة
                                matrix.set(savedMatrix);

                                // ✅ احسب المقياس الحالي من savedMatrix
                                float[] savedValues = new float[9];
                                savedMatrix.getValues(savedValues);
                                float savedScale = savedValues[Matrix.MSCALE_X];
                                if (savedScale <= 0f) savedScale = 1f;

                                // ✅ النسبة المطلوبة
                                float desiredFactor = newDist / oldDist;
                                float targetScale = savedScale * desiredFactor;

                                // ✅ اقصر إلى الحدود
                                if (targetScale < MIN_SCALE) targetScale = MIN_SCALE;
                                if (targetScale > MAX_SCALE) targetScale = MAX_SCALE;

                                // ✅ النسبة الفعلية بعد التقصير
                                float actualFactor = targetScale / savedScale;
                                matrix.postScale(actualFactor, actualFactor, mid.x, mid.y);
                                imageView.setImageMatrix(matrix);
                            }
                        }
                        break;

                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mode = NONE;
                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < 300) {
                            resetView();
                            lastTapTime = 0;
                        } else {
                            lastTapTime = now;
                        }
                        break;
                }
                return true;
            }
        });
    }

    private float spacing(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void midPoint(PointF point, MotionEvent event) {
        if (event.getPointerCount() < 2) return;
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2f, y / 2f);
    }

    // ============================================================
    // أزرار التحكم
    // ============================================================
    private void applyZoom(float factor) {
        if (imageView == null || currentBitmap == null) return;
        float cx = imageView.getWidth() / 2f;
        float cy = imageView.getHeight() / 2f;

        // ✅ المقياس الحالي
        float currentScaleValue = getCurrentScale();
        if (currentScaleValue <= 0f) currentScaleValue = 1f;

        float targetScale = currentScaleValue * factor;

        // ✅ اقصر إلى الحدود
        if (targetScale < MIN_SCALE) targetScale = MIN_SCALE;
        if (targetScale > MAX_SCALE) targetScale = MAX_SCALE;

        // إذا لم يتغيّر شيء فعلاً، اخرج
        if (Math.abs(targetScale - currentScaleValue) < 0.0001f) return;

        float actualFactor = targetScale / currentScaleValue;
        matrix.postScale(actualFactor, actualFactor, cx, cy);
        imageView.setImageMatrix(matrix);
    }

    /** يُرجع المقياس الحالي الفعلي. */
    private float getCurrentScale() {
        float[] values = new float[9];
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void resetView() {
        if (currentBitmap == null || imageView.getWidth() == 0) return;
        matrix.reset();
        float viewW = imageView.getWidth();
        float viewH = imageView.getHeight();
        float imgW = currentBitmap.getWidth();
        float imgH = currentBitmap.getHeight();
        float fitScale = Math.min(viewW / imgW, viewH / imgH);
        if (fitScale <= 0 || Float.isNaN(fitScale) || Float.isInfinite(fitScale)) fitScale = 1f;
        matrix.postScale(fitScale, fitScale);
        float dx = (viewW - imgW * fitScale) / 2f;
        float dy = (viewH - imgH * fitScale) / 2f;
        matrix.postTranslate(dx, dy);
        imageView.setImageMatrix(matrix);
    }

    // ============================================================
    // دورة الحياة
    // ============================================================
    public void release() {
        try {
            imageView.setImageBitmap(null);
        } catch (Exception ignored) {}
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
        currentBitmap = null;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public boolean hasBitmap() {
        return currentBitmap != null && !currentBitmap.isRecycled();
    }

    // ============================================================
    // أدوات
    // ============================================================
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}