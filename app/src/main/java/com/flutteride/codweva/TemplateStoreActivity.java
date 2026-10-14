package com.flutteride.codweva;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * شاشة "متجر القوالب".
 * - شبكة من عمودين
 * - كل بطاقة: أيقونة كبيرة + اسم القالب
 * - النقر على بطاقة -> فتح TemplateDetailActivity
 * - تستخدم cache القرص للأيقونات (لا فك ZIP إلا مرة واحدة)
 *
 * ✅ مُصحّح: لا تكرار للقوالب عند الفتح
 */
public final class TemplateStoreActivity extends AppCompatActivity {

    // ============================================================
    // ثوابت الألوان
    // ============================================================
    private static final int COLOR_BACKGROUND = 0xFF0D1117;
    private static final int COLOR_SURFACE = 0xFF161B22;
    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
    private static final int COLOR_TEXT_PRIMARY = 0xFFE6EDF3;
    private static final int COLOR_TEXT_SECONDARY = 0xFF8B949E;
    private static final int COLOR_BORDER = 0xFF30363D;

    // ============================================================
    // الحقول
    // ============================================================
    private LinearLayout gridContainer;
    private LinearLayout emptyState;
    private ScrollView scrollView;

    /** ✅ علامة لمنع التحميل المزدوج */
    private boolean templatesLoaded = false;
    private boolean isLoading = false;

    private final Map<String, Bitmap> iconMemoryCache = new HashMap<>();

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("القوالب المتاحة");
        buildLayout();
        loadTemplatesAsync();
    }

    // ✅ onResume لم يعد يُعيد التحميل — لمنع التكرار
    @Override
    protected void onResume() {
        super.onResume();
        // لا نُعيد التحميل — القوالب ثابتة ولا تتغير
        // (لو أردت تحديثًا يدويًا، ضع زر "تحديث" في الواجهة)
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Bitmap bmp : iconMemoryCache.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        iconMemoryCache.clear();
    }

    // ============================================================
    // بناء الواجهة
    // ============================================================
    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // الترويسة
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackgroundColor(COLOR_SURFACE);

        Button backBtn = new Button(this);
        backBtn.setText("رجوع");
        backBtn.setTextColor(COLOR_TEXT_PRIMARY);
        backBtn.setAllCaps(false);
        backBtn.setTextSize(13);
        backBtn.setBackground(UiStyle.rounded(this, COLOR_SURFACE_ALT, 10, COLOR_BORDER, 1));
        backBtn.setOnClickListener(v -> finish());
        header.addView(backBtn, new LinearLayout.LayoutParams(dp(80), dp(40)));

        TextView title = new TextView(this);
        title.setText("اختر قالباً");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleParams);

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(dp(80), dp(40)));

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // شبكة القوالب
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);

        gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setPadding(dp(12), dp(12), dp(12), dp(24));
        scrollView.addView(gridContainer, new ScrollView.LayoutParams(-1, -2));

        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        // حالة "لا توجد قوالب"
        emptyState = new LinearLayout(this);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(24), dp(80), dp(24), dp(80));
        emptyState.setVisibility(View.GONE);

        TextView emptyIcon = new TextView(this);
        emptyIcon.setText("لا توجد قوالب");
        emptyIcon.setTextSize(20);
        emptyIcon.setTextColor(COLOR_TEXT_SECONDARY);
        emptyIcon.setGravity(Gravity.CENTER);
        emptyState.addView(emptyIcon);

        TextView emptyHint = new TextView(this);
        emptyHint.setText("ضع ملفات ZIP في مجلد assets/templates/");
        emptyHint.setTextSize(13);
        emptyHint.setTextColor(COLOR_TEXT_SECONDARY);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, dp(12), 0, 0);
        emptyState.addView(emptyHint);

        root.addView(emptyState, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    // ============================================================
    // تحميل القوالب
    // ============================================================
    private void loadTemplatesAsync() {
        // ✅ حماية من الاستدعاء المتكرر
        if (isLoading) return;
        isLoading = true;

        new Thread(() -> {
            final List<TemplateInfo> templates = TemplateManager.loadTemplates(this);
            runOnUiThread(() -> {
                isLoading = false;
                displayTemplates(templates);
            });
        }, "template-load").start();
    }

    private void displayTemplates(List<TemplateInfo> templates) {
        if (gridContainer == null) return;

        // ✅ حذف صريح لكل العناصر القديمة
        gridContainer.removeAllViews();

        if (templates == null || templates.isEmpty()) {
            gridContainer.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            templatesLoaded = true;
            return;
        }

        gridContainer.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        // نبني الصفوف: كل صف عمودان
        for (int i = 0; i < templates.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            TemplateInfo t1 = templates.get(i);
            row.addView(buildCard(t1), new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (i + 1 < templates.size()) {
                TemplateInfo t2 = templates.get(i + 1);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                p.setMarginStart(dp(8));
                row.addView(buildCard(t2), p);
            } else {
                View spacer = new View(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                p.setMarginStart(dp(8));
                row.addView(spacer, p);
            }

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(12);
            gridContainer.addView(row, rowParams);
        }

        templatesLoaded = true;
    }

    // ============================================================
    // بطاقة قالب واحدة
    // ============================================================
    private View buildCard(TemplateInfo template) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(16), dp(12), dp(14));
        card.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 18, COLOR_BORDER, 1));
        card.setClickable(true);
        card.setFocusable(true);

        // الأيقونة
        final FrameLayout iconFrame = new FrameLayout(this);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(120), dp(120));
        iconFrame.setLayoutParams(iconParams);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setColor(COLOR_SURFACE_ALT);
        iconBg.setCornerRadius(dp(22));
        iconFrame.setBackground(iconBg);

        // صورة افتراضية
        ImageView defaultIcon = new ImageView(this);
        defaultIcon.setImageResource(R.drawable.ic_launcher);
        defaultIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        defaultIcon.setAlpha(0.35f);
        FrameLayout.LayoutParams defParams = new FrameLayout.LayoutParams(-1, -1);
        defParams.gravity = Gravity.CENTER;
        int defPad = dp(28);
        defParams.setMargins(defPad, defPad, defPad, defPad);
        iconFrame.addView(defaultIcon, defParams);

        // ImageView للأيقونة الحقيقية
        final ImageView iconView = new ImageView(this);
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams iconImgParams = new FrameLayout.LayoutParams(-1, -1);
        iconFrame.addView(iconView, iconImgParams);

        iconFrame.setClipToOutline(true);
        iconFrame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(22));
            }
        });

        card.addView(iconFrame);

        // اسم القالب
        TextView name = new TextView(this);
        name.setText(template.displayName);
        name.setTextColor(COLOR_TEXT_PRIMARY);
        name.setTextSize(15);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(14);
        card.addView(name, nameParams);

        // النقر
        card.setOnClickListener(v -> openTemplate(template));

        // تحميل الأيقونة
        loadTemplateIcon(template, iconView);

        return card;
    }

    // ============================================================
    // تحميل أيقونة القالب مع cache متعدد المستويات
    // ============================================================
    private void loadTemplateIcon(final TemplateInfo template, final ImageView iconView) {
        if (template == null || iconView == null) return;

        // 1. cache الذاكرة
        Bitmap cachedMem = iconMemoryCache.get(template.id);
        if (cachedMem != null && !cachedMem.isRecycled()) {
            iconView.setImageBitmap(cachedMem);
            return;
        }

        // 2. cache القرص
        Bitmap cachedDisk = IconCache.loadTemplateIcon(this, template);
        if (cachedDisk != null && !cachedDisk.isRecycled()) {
            iconMemoryCache.put(template.id, cachedDisk);
            iconView.setImageBitmap(cachedDisk);
            return;
        }

        // 3. لم يوجد - استخرج في الخلفية
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File showcaseDir = TemplateManager.prepareShowcase(
                            TemplateStoreActivity.this, template);
                    if (showcaseDir == null) return;

                    File iconFile = template.iconFile;
                    if (iconFile == null || !iconFile.exists()) return;

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(iconFile.getAbsolutePath(), opts);

                    int targetSize = dp(240);
                    int sample = 1;
                    int maxDim = Math.max(opts.outWidth, opts.outHeight);
                    while (maxDim / (sample * 2) >= targetSize) sample *= 2;

                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

                    final Bitmap bmp = BitmapFactory.decodeFile(iconFile.getAbsolutePath(), opts);
                    if (bmp == null || bmp.isRecycled()) return;

                    iconMemoryCache.put(template.id, bmp);

                    iconView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            if (bmp.isRecycled()) return;
                            iconView.setImageBitmap(bmp);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }, "template-icon").start();
    }

    // ============================================================
    // فتح تفاصيل القالب
    // ============================================================
    private void openTemplate(TemplateInfo template) {
        Intent intent = new Intent(this, TemplateDetailActivity.class);
        intent.putExtra("template_id", template.id);
        startActivity(intent);
    }
}