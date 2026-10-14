package com.flutteride.codweva;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;

/**
 * عارض الملفات غير المدعومة (PDF، APK، أرشيف، ثنائيات):
 *   - يعرض اسم الملف، حجمه، نوعه.
 *   - زر "فتح في تطبيق آخر".
 *   - زر "مشاركة".
 *   - رسالة توضح سبب عدم العرض.
 */
public final class UnsupportedViewer extends FrameLayout {

    private static final int COLOR_BG = 0xFF0D1117;
    private static final int COLOR_SURFACE = 0xFF161B22;
    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
    private static final int COLOR_PRIMARY_LIGHT = 0xFF58A6FF;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_MUTED = 0xFF8B949E;
    private static final int COLOR_BORDER = 0xFF30363D;

    private final Context context;
    private File currentFile;
    private FileKind currentKind;

    private TextView nameView;
    private TextView metaView;
    private TextView messageView;

    public UnsupportedViewer(Context context) {
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

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.setBackgroundColor(COLOR_BG);
        addView(content, new FrameLayout.LayoutParams(-1, -1));

        // 1) أيقونة رمزية
        FrameLayout iconFrame = new FrameLayout(context);
        int iconSize = dp(140);
        LinearLayout.LayoutParams iconFrameParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconFrame.setLayoutParams(iconFrameParams);
        iconFrame.setBackground(UiStyle.rounded(context, COLOR_SURFACE, 70, COLOR_BORDER, 2));

        View icon = new DocumentIconView(context);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams wrapper = new LinearLayout.LayoutParams(-2, -2);
        wrapper.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(iconFrame, wrapper);

        // 2) اسم الملف
        nameView = new TextView(context);
        nameView.setTextColor(COLOR_TEXT);
        nameView.setTextSize(16);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        nameView.setMaxLines(2);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(24);
        content.addView(nameView, nameParams);

        // 3) معلومات (حجم + نوع)
        metaView = new TextView(context);
        metaView.setTextColor(COLOR_MUTED);
        metaView.setTextSize(11);
        metaView.setTypeface(android.graphics.Typeface.MONOSPACE);
        metaView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(6);
        content.addView(metaView, metaParams);

        // 4) رسالة
        messageView = new TextView(context);
        messageView.setTextColor(COLOR_MUTED);
        messageView.setTextSize(13);
        messageView.setGravity(Gravity.CENTER);
        messageView.setText("لا يمكن عرض هذا النوع من الملفات داخل التطبيق.\n"
                + "يمكنك فتحه في تطبيق آخر أو مشاركته.");
        messageView.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(-1, -2);
        msgParams.topMargin = dp(28);
        msgParams.leftMargin = dp(16);
        msgParams.rightMargin = dp(16);
        content.addView(messageView, msgParams);

        // 5) الأزرار
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(-1, -2);
        btnRowParams.topMargin = dp(32);
        content.addView(btnRow, btnRowParams);

        Button btnOpen = makeButton("فتح في تطبيق آخر", true);
        btnOpen.setOnClickListener(v -> openExternally());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        openParams.setMarginEnd(dp(6));
        btnRow.addView(btnOpen, openParams);

        Button btnShare = makeButton("مشاركة", false);
        btnShare.setOnClickListener(v -> shareExternally());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        shareParams.setMarginStart(dp(6));
        btnRow.addView(btnShare, shareParams);
    }

    private Button makeButton(String text, boolean primary) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(primary ? 0xFFFFFFFF : COLOR_TEXT);
        btn.setTextSize(13);
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
    // التحميل
    // ============================================================
    public void loadFile(String path, FileKind kind) {
        this.currentFile = new File(path);
        this.currentKind = kind;

        if (!currentFile.exists() || !currentFile.isFile()) {
            nameView.setText("ملف غير موجود");
            metaView.setText("");
            return;
        }

        nameView.setText(currentFile.getName());
        String typeLabel = (kind == null) ? "ملف" : kind.getArabicName();
        metaView.setText(formatSize(currentFile.length()) + "  •  " + typeLabel);
    }

    // ============================================================
    // فتح خارجي
    // ============================================================
    private void openExternally() {
        if (currentFile == null || !currentFile.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", currentFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, guessMimeType(currentFile.getName()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "فتح باستخدام"));
        } catch (Exception e) {
            Toast.makeText(context, "لا يوجد تطبيق يدعم هذا النوع: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareExternally() {
        if (currentFile == null || !currentFile.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", currentFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(guessMimeType(currentFile.getName()));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "مشاركة"));
        } catch (Exception e) {
            Toast.makeText(context, "تعذر المشاركة: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String guessMimeType(String fileName) {
        if (fileName == null) return "*/*";
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".rar")) return "application/x-rar-compressed";
        if (lower.endsWith(".7z")) return "application/x-7z-compressed";
        if (lower.endsWith(".tar")) return "application/x-tar";
        if (lower.endsWith(".gz") || lower.endsWith(".tgz")) return "application/gzip";
        return "*/*";
    }

    public String getCurrentPath() {
        return currentFile == null ? null : currentFile.getAbsolutePath();
    }

    // ============================================================
    // أدوات
    // ============================================================
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    // ============================================================
    // أيقونة مستند مرسومة
    // ============================================================
    private static final class DocumentIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DocumentIconView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float size = Math.min(w, h);

            // ورقة بيضاء (مستطيل بزوايا دائرية + ركن مطوي)
            float paperW = size * 0.45f;
            float paperH = size * 0.58f;
            float left = cx - paperW / 2f;
            float top = cy - paperH / 2f;
            float right = cx + paperW / 2f;
            float bottom = cy + paperH / 2f;
            float radius = size * 0.04f;
            float fold = size * 0.12f;

            // الجزء الأساسي (بدون الركن المطوي)
            paint.setColor(0xFFE6EDF3);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);

            // مثلث الركن المطوي
            paint.setColor(0xFF8B949E);
            android.graphics.Path foldPath = new android.graphics.Path();
            foldPath.moveTo(right - fold, top);
            foldPath.lineTo(right, top + fold);
            foldPath.lineTo(right - fold, top + fold);
            foldPath.close();
            canvas.drawPath(foldPath, paint);

            // خطوط نصية
            paint.setColor(0xFF8B949E);
            paint.setStrokeWidth(size * 0.015f);
            float lineLeft = left + paperW * 0.15f;
            float lineRight = right - paperW * 0.15f;
            for (int i = 0; i < 4; i++) {
                float y = top + paperH * 0.45f + i * paperH * 0.10f;
                canvas.drawLine(lineLeft, y, lineRight, y, paint);
            }
        }
    }
}