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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * شاشة تفاصيل القالب:
 *  - أيقونة كبيرة + اسم + حجم
 *  - زر "إنشاء مشروع"
 *  - صف أفقي من المعاينات (قابلة للنقر)
 *  - حوار إنشاء المشروع مع تحقق قوي
 *  - تستخدم cache القرص للأيقونة والمعاينات
 */
public final class TemplateDetailActivity extends AppCompatActivity {

    // ============================================================
    // ثوابت الألوان
    // ============================================================
    private static final int COLOR_BACKGROUND = 0xFF0D1117;
    private static final int COLOR_SURFACE = 0xFF161B22;
    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
    private static final int COLOR_PRIMARY_LIGHT = 0xFF58A6FF;
    private static final int COLOR_TEXT_PRIMARY = 0xFFE6EDF3;
    private static final int COLOR_TEXT_SECONDARY = 0xFF8B949E;
    private static final int COLOR_BORDER = 0xFF30363D;
    private static final int COLOR_ERROR = 0xFFCF222E;

    // ============================================================
    // الحقول
    // ============================================================
    private String templateId;
    private TemplateInfo template;
    private LinearLayout previewsRow;
    private ImageView iconView;
    private TextView nameView;
    private TextView nameEnView;
    private TextView sizeView;
    private TextView noPreviewsView;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        templateId = getIntent().getStringExtra("template_id");
        if (templateId == null || templateId.isEmpty()) {
            finish();
            return;
        }
        template = TemplateManager.findById(this, templateId);
        if (template == null) {
            Toast.makeText(this, "القالب غير موجود", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setTitle(template.displayName);
        buildLayout();
        loadShowcaseAsync();
    }

    // ============================================================
    // الواجهة
    // ============================================================
    private void buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // ترويسة القالب
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(16), dp(14), dp(16));
        header.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 20, COLOR_BORDER, 1));

        // الأيقونة
        FrameLayout iconFrame = new FrameLayout(this);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(100), dp(100));
        iconFrame.setLayoutParams(iconParams);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(COLOR_SURFACE_ALT);
        iconBg.setCornerRadius(dp(20));
        iconFrame.setBackground(iconBg);

        ImageView defaultIcon = new ImageView(this);
        defaultIcon.setImageResource(R.drawable.ic_launcher);
        defaultIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        defaultIcon.setAlpha(0.35f);
        FrameLayout.LayoutParams defParams = new FrameLayout.LayoutParams(-1, -1);
        defParams.gravity = Gravity.CENTER;
        int defPad = dp(24);
        defParams.setMargins(defPad, defPad, defPad, defPad);
        iconFrame.addView(defaultIcon, defParams);

        iconView = new ImageView(this);
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams iconImgParams = new FrameLayout.LayoutParams(-1, -1);
        iconFrame.addView(iconView, iconImgParams);

        iconFrame.setClipToOutline(true);
        iconFrame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });

        header.addView(iconFrame);

        // عمود النصوص
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams =
                new LinearLayout.LayoutParams(0, -2, 1f);
        textColParams.setMarginStart(dp(14));
        header.addView(textCol, textColParams);

        nameView = new TextView(this);
        nameView.setText(template.displayName);
        nameView.setTextColor(COLOR_TEXT_PRIMARY);
        nameView.setTextSize(22);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setMaxLines(2);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(nameView);

        nameEnView = new TextView(this);
        nameEnView.setText(template.englishName);
        nameEnView.setTextColor(COLOR_TEXT_SECONDARY);
        nameEnView.setTextSize(13);
        LinearLayout.LayoutParams nameEnParams = new LinearLayout.LayoutParams(-1, -2);
        nameEnParams.topMargin = dp(4);
        textCol.addView(nameEnView, nameEnParams);

        sizeView = new TextView(this);
        sizeView.setText("الحجم: " + template.getSizeLabel());
        sizeView.setTextColor(COLOR_TEXT_SECONDARY);
        sizeView.setTextSize(12);
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(-1, -2);
        sizeParams.topMargin = dp(6);
        textCol.addView(sizeView, sizeParams);

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // زر "إنشاء مشروع"
        Button createBtn = new Button(this);
        createBtn.setText("إنشاء مشروع من هذا القالب");
        createBtn.setTextSize(15);
        createBtn.setTextColor(0xFFFFFFFF);
        createBtn.setAllCaps(false);
        createBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        createBtn.setBackground(UiStyle.rounded(this, COLOR_PRIMARY, 14,
                COLOR_PRIMARY_LIGHT, 1));
        createBtn.setOnClickListener(v -> showCreateProjectDialog());
        LinearLayout.LayoutParams createBtnParams =
                new LinearLayout.LayoutParams(-1, dp(52));
        createBtnParams.topMargin = dp(20);
        root.addView(createBtn, createBtnParams);

        // عنوان "معاينات"
        TextView previewsTitle = new TextView(this);
        previewsTitle.setText("معاينات");
        previewsTitle.setTextColor(COLOR_TEXT_PRIMARY);
        previewsTitle.setTextSize(17);
        previewsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams prevTitleParams =
                new LinearLayout.LayoutParams(-1, -2);
        prevTitleParams.topMargin = dp(28);
        prevTitleParams.bottomMargin = dp(12);
        root.addView(previewsTitle, prevTitleParams);

        // صف المعاينات (أفقي)
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        previewsRow = new LinearLayout(this);
        previewsRow.setOrientation(LinearLayout.HORIZONTAL);
        previewsRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        hScroll.addView(previewsRow, new HorizontalScrollView.LayoutParams(-2, -2));

        root.addView(hScroll, new LinearLayout.LayoutParams(-1, -2));

        // رسالة افتراضية عند عدم وجود معاينات
        noPreviewsView = new TextView(this);
        noPreviewsView.setText("لا توجد معاينات لهذا القالب");
        noPreviewsView.setTextColor(COLOR_TEXT_SECONDARY);
        noPreviewsView.setTextSize(13);
        noPreviewsView.setGravity(Gravity.CENTER);
        noPreviewsView.setPadding(dp(20), dp(40), dp(20), dp(40));
        noPreviewsView.setVisibility(View.GONE);
        root.addView(noPreviewsView, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(root);
        setContentView(scroll);
    }

    // ============================================================
    // تحميل الأيقونة والمعاينات (مع cache)
    // ============================================================
    private void loadShowcaseAsync() {
        new Thread(() -> {
            try {
                // ✅ 1. جرّب الأيقونة من cache الذاكرة (لا نحتاجها هنا، الـ ID مباشر)
                // ✅ 2. جرّب الأيقونة من cache القرص
                Bitmap cachedIcon = IconCache.loadTemplateIcon(this, template);

                // ✅ 3. المعاينات: prepareShowcase سيحاول القرص ثم يفك
                File showcaseDir = TemplateManager.prepareShowcase(this, template);
                if (showcaseDir == null) return;

                // ✅ 4. إذا لم تُوجد الأيقونة في الذاكرة/القرص، استخرجها من ملف الأيقونة
                Bitmap iconBmp = cachedIcon;
                if ((iconBmp == null || iconBmp.isRecycled())
                        && template.iconFile != null && template.iconFile.exists()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(template.iconFile.getAbsolutePath(), opts);

                    int target = dp(220);
                    int sample = 1;
                    int maxDim = Math.max(opts.outWidth, opts.outHeight);
                    while (maxDim / (sample * 2) >= target) sample *= 2;
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    iconBmp = BitmapFactory.decodeFile(template.iconFile.getAbsolutePath(), opts);

                    // ✅ 5. احفظها في cache القرص (لو لم تكن موجودة)
                    if (iconBmp != null && !iconBmp.isRecycled()) {
                        IconCache.saveTemplateIcon(this, template, iconBmp);
                    }
                }

                // فك المعاينات
                List<Bitmap> previews = new ArrayList<>();
                for (File f : template.previewFiles) {
                    if (f == null || !f.exists()) continue;
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getAbsolutePath(), opts);

                    int target = dp(700);
                    int sample = 1;
                    int maxDim = Math.max(opts.outWidth, opts.outHeight);
                    while (maxDim / (sample * 2) >= target) sample *= 2;
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                    if (bmp != null) previews.add(bmp);
                }

                final Bitmap finalIcon = iconBmp;
                final List<Bitmap> finalPreviews = previews;

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (finalIcon != null && !finalIcon.isRecycled()) {
                        iconView.setImageBitmap(finalIcon);
                    }
                    displayPreviews(finalPreviews);
                });
            } catch (Exception ignored) {}
        }, "template-showcase").start();
    }

    private void displayPreviews(List<Bitmap> previews) {
        if (previewsRow == null) return;
        previewsRow.removeAllViews();

        if (previews.isEmpty()) {
            if (noPreviewsView != null) noPreviewsView.setVisibility(View.VISIBLE);
            return;
        }
        if (noPreviewsView != null) noPreviewsView.setVisibility(View.GONE);

        for (int i = 0; i < previews.size(); i++) {
            Bitmap bmp = previews.get(i);
            final int index = i;

            FrameLayout card = new FrameLayout(this);
            int cardWidth = dp(180);
            int cardHeight = dp(320);
            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(cardWidth, cardHeight);
            if (i > 0) cardParams.setMarginStart(dp(10));
            card.setLayoutParams(cardParams);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_SURFACE_ALT);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), COLOR_BORDER);
            card.setBackground(bg);
            card.setClipToOutline(true);
            card.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
                }
            });

            ImageView img = new ImageView(this);
            img.setImageBitmap(bmp);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));

            card.setOnClickListener(v -> openPreview(index));

            previewsRow.addView(card);
        }
    }

    // ============================================================
    // فتح صورة المعاينة كاملة
    // ============================================================
    private void openPreview(int index) {
        Intent intent = new Intent(this, ImagePreviewActivity.class);
        intent.putExtra("template_id", templateId);
        intent.putExtra("start_index", index);
        startActivity(intent);
    }

    // ============================================================
    // حوار إنشاء المشروع مع تحقق قوي
    // ============================================================
    private void showCreateProjectDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.setBackgroundColor(COLOR_BACKGROUND);

        // اسم التطبيق
        TextView nameLabel = new TextView(this);
        nameLabel.setText("اسم التطبيق");
        nameLabel.setTextColor(COLOR_TEXT_PRIMARY);
        nameLabel.setTextSize(13);
        nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(nameLabel);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("مثلاً: رفيق المسلم");
        nameInput.setText(template.displayName);
        nameInput.setTextColor(COLOR_TEXT_PRIMARY);
        nameInput.setHintTextColor(COLOR_TEXT_SECONDARY);
        nameInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_BORDER, 1));
        nameInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        nameInput.setSingleLine(true);
        nameInput.setMaxLines(1);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(6);
        root.addView(nameInput, nameParams);

        final TextView nameError = new TextView(this);
        nameError.setTextColor(COLOR_ERROR);
        nameError.setTextSize(11);
        nameError.setPadding(dp(4), dp(4), dp(4), dp(14));
        nameError.setVisibility(View.GONE);
        root.addView(nameError);

        final TextView nameHint = new TextView(this);
        nameHint.setText("ملاحظة: لا يستخدم في اسم الحزمة أو الملفات - فقط للعرض");
        nameHint.setTextColor(COLOR_TEXT_SECONDARY);
        nameHint.setTextSize(10);
        nameHint.setPadding(dp(4), 0, dp(4), dp(10));
        root.addView(nameHint);

        // اسم الحزمة
        TextView pkgLabel = new TextView(this);
        pkgLabel.setText("اسم الحزمة (Package)");
        pkgLabel.setTextColor(COLOR_TEXT_PRIMARY);
        pkgLabel.setTextSize(13);
        pkgLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(pkgLabel);

        final EditText pkgInput = new EditText(this);
        pkgInput.setHint("com.example.myapp");
        pkgInput.setText("com.example." + template.id.replaceAll("[^a-z0-9_]", ""));
        pkgInput.setTextColor(COLOR_TEXT_PRIMARY);
        pkgInput.setHintTextColor(COLOR_TEXT_SECONDARY);
        pkgInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_BORDER, 1));
        pkgInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        pkgInput.setSingleLine(true);
        LinearLayout.LayoutParams pkgParams = new LinearLayout.LayoutParams(-1, -2);
        pkgParams.topMargin = dp(6);
        root.addView(pkgInput, pkgParams);

        final TextView pkgError = new TextView(this);
        pkgError.setTextColor(COLOR_ERROR);
        pkgError.setTextSize(11);
        pkgError.setPadding(dp(4), dp(4), dp(4), dp(14));
        pkgError.setVisibility(View.GONE);
        root.addView(pkgError);

        final TextView pkgHint = new TextView(this);
        pkgHint.setText("ملاحظة: حروف صغيرة وأرقام ونقاط فقط: com.ahmed.quran");
        pkgHint.setTextColor(COLOR_TEXT_SECONDARY);
        pkgHint.setTextSize(10);
        pkgHint.setPadding(dp(4), 0, dp(4), dp(14));
        root.addView(pkgHint);

        // Target API
        TextView apiLabel = new TextView(this);
        apiLabel.setText("Target API");
        apiLabel.setTextColor(COLOR_TEXT_PRIMARY);
        apiLabel.setTextSize(13);
        apiLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(apiLabel);

        final EditText apiInput = new EditText(this);
        apiInput.setHint("33");
        apiInput.setText("33");
        apiInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        apiInput.setTextColor(COLOR_TEXT_PRIMARY);
        apiInput.setHintTextColor(COLOR_TEXT_SECONDARY);
        apiInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_BORDER, 1));
        apiInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        apiInput.setSingleLine(true);
        LinearLayout.LayoutParams apiParams = new LinearLayout.LayoutParams(-1, -2);
        apiParams.topMargin = dp(6);
        apiParams.bottomMargin = dp(18);
        root.addView(apiInput, apiParams);

        // نوع المستودع
        TextView repoLabel = new TextView(this);
        repoLabel.setText("نوع المستودع على GitHub:");
        repoLabel.setTextColor(COLOR_TEXT_PRIMARY);
        repoLabel.setTextSize(13);
        repoLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(repoLabel);

        final RadioGroup repoGroup = new RadioGroup(this);
        repoGroup.setOrientation(RadioGroup.VERTICAL);
        repoGroup.setPadding(dp(4), dp(6), dp(4), dp(4));

        RadioButton rbPublic = new RadioButton(this);
        rbPublic.setText("عام (Public) - موصى به");
        rbPublic.setTextColor(COLOR_TEXT_PRIMARY);
        rbPublic.setTextSize(13);
        rbPublic.setId(android.R.id.button1);
        repoGroup.addView(rbPublic);

        TextView tvPublicInfo = new TextView(this);
        tvPublicInfo.setText("بناء مجاني غير محدود\nمناسب للمشاريع التعليمية\nالكود مرئي للجميع");
        tvPublicInfo.setTextSize(11);
        tvPublicInfo.setTextColor(COLOR_TEXT_SECONDARY);
        tvPublicInfo.setPadding(dp(30), 0, 0, dp(10));
        tvPublicInfo.setLineSpacing(0, 1.15f);
        repoGroup.addView(tvPublicInfo);

        RadioButton rbPrivate = new RadioButton(this);
        rbPrivate.setText("خاص (Private)");
        rbPrivate.setTextColor(COLOR_TEXT_PRIMARY);
        rbPrivate.setTextSize(13);
        rbPrivate.setId(android.R.id.button2);
        repoGroup.addView(rbPrivate);

        TextView tvPrivateInfo = new TextView(this);
        tvPrivateInfo.setText("كودك خاص تماماً\n2,000 دقيقة بناء مجاناً/شهر\nبعدها قد تحتاج دفع");
        tvPrivateInfo.setTextSize(11);
        tvPrivateInfo.setTextColor(COLOR_TEXT_SECONDARY);
        tvPrivateInfo.setPadding(dp(30), 0, 0, dp(10));
        tvPrivateInfo.setLineSpacing(0, 1.15f);
        repoGroup.addView(tvPrivateInfo);

        repoGroup.check(android.R.id.button1);
        root.addView(repoGroup);

        // الحوار
        final ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إنشاء مشروع من القالب")
                .setView(scroll)
                .setPositiveButton("إنشاء", null)
                .setNegativeButton("إلغاء", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

                String name = nameInput.getText().toString();
                String pkg = pkgInput.getText().toString().trim();
                String api = apiInput.getText().toString().trim();

                // إخفاء الأخطاء القديمة
                nameError.setVisibility(View.GONE);
                pkgError.setVisibility(View.GONE);
                nameInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_BORDER, 1));
                pkgInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_BORDER, 1));

                boolean ok = true;

                // 1) تحقق من اسم التطبيق
                String nameErr = TemplateInstaller.validateAppName(name);
                if (nameErr != null) {
                    nameError.setText("خطأ: " + nameErr);
                    nameError.setVisibility(View.VISIBLE);
                    nameInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_ERROR, 2));
                    ok = false;
                }

                // 2) تحقق من اسم الحزمة
                if (pkg.isEmpty()) {
                    pkgError.setText("خطأ: أدخل اسم الحزمة");
                    pkgError.setVisibility(View.VISIBLE);
                    pkgInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_ERROR, 2));
                    ok = false;
                } else if (!isValidPackage(pkg)) {
                    pkgError.setText("خطأ: صيغة غير صحيحة. مثال: com.example.myapp");
                    pkgError.setVisibility(View.VISIBLE);
                    pkgInput.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_ERROR, 2));
                    ok = false;
                }

                // 3) تحقق من API
                if (api.isEmpty()) {
                    apiInput.setError("أدخل Target API");
                    ok = false;
                } else {
                    try {
                        int level = Integer.parseInt(api);
                        if (level < 21 || level > 35) {
                            apiInput.setError("يجب أن يكون بين 21 و 35");
                            ok = false;
                        }
                    } catch (NumberFormatException e) {
                        apiInput.setError("رقم غير صحيح");
                        ok = false;
                    }
                }

                if (!ok) {
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_UP));
                    return;
                }

                boolean isPublicRepo = (repoGroup.getCheckedRadioButtonId() == android.R.id.button1);

                // 4) حساب الاسم النظيف قبل الإنشاء
                String cleanName = TemplateInstaller.sanitizeAppNameForDisplay(name);

                if (!cleanName.equals(name.trim())) {
                    new AlertDialog.Builder(this)
                            .setTitle("تنبيه: تم تعديل الاسم")
                            .setMessage("الاسم الأصلي:\n«" + name + "»\n\n"
                                    + "الاسم بعد التنظيف:\n«" + cleanName + "»\n\n"
                                    + "هل تريد المتابعة بالاسم المُنظَّف؟")
                            .setPositiveButton("نعم، تابع", (dd, ww) -> {
                                dialog.dismiss();
                                createProject(cleanName, pkg, api, isPublicRepo);
                            })
                            .setNegativeButton("تعديل يدويًا", (dd, ww) -> {
                                nameInput.setText(cleanName);
                                nameInput.setSelection(cleanName.length());
                                nameInput.requestFocus();
                            })
                            .setNeutralButton("إلغاء", null)
                            .show();
                    return;
                }

                dialog.dismiss();
                createProject(cleanName, pkg, api, isPublicRepo);
            });
        });

        dialog.show();
        styleDialog(dialog);
    }

    // ============================================================
    // التحقق من صحة اسم الحزمة
    // ============================================================
    private boolean isValidPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String[] parts = pkg.split("\\.");
        if (parts.length < 2) return false;
        for (String part : parts) {
            if (part.isEmpty()) return false;
            if (!Character.isLetter(part.charAt(0))) return false;
            if (Character.isUpperCase(part.charAt(0))) return false;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                    return false;
                }
            }
        }
        return true;
    }

    // ============================================================
    // إنشاء المشروع فعلياً
    // ============================================================
    private void createProject(String appName, String packageName,
                                String apiLevel, boolean isPublicRepo) {
        try {
            File baseDir = new File(getFilesDir(), "projects");
            if (!baseDir.exists()) baseDir.mkdirs();

            String folderName = "project_" + System.currentTimeMillis();
            File projectDir = new File(baseDir, folderName);
            if (!projectDir.mkdirs()) {
                Toast.makeText(this, "تعذر إنشاء المجلد", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean ok = TemplateManager.install(
                    this, template, projectDir,
                    appName, packageName, apiLevel, isPublicRepo);

            if (!ok) {
                deleteRecursive(projectDir);
                Toast.makeText(this, "تعذر تثبيت القالب - تحقق من الأسماء",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // حفظ معلومات المشروع
            getSharedPreferences("flutter_ide_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("name_" + folderName, appName)
                    .putString("package_" + folderName, packageName)
                    .putString("api_" + folderName, apiLevel)
                    .putBoolean("repo_public_" + folderName, isPublicRepo)
                    .apply();

            Toast.makeText(this, "تم إنشاء المشروع بنجاح", Toast.LENGTH_LONG).show();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        file.delete();
    }

    // ============================================================
    // تطبيق نمط الحوار
    // ============================================================
    private void styleDialog(AlertDialog dialog) {
        if (dialog == null) return;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    UiStyle.rounded(this, COLOR_BACKGROUND, 16, COLOR_BORDER, 1));
        }
        int titleId = dialog.getContext().getResources().getIdentifier(
                "android:id/alertTitle", null, null);
        if (titleId != 0) {
            TextView t = dialog.findViewById(titleId);
            if (t != null) t.setTextColor(COLOR_TEXT_PRIMARY);
        }
        TextView msg = dialog.findViewById(android.R.id.message);
        if (msg != null) msg.setTextColor(COLOR_TEXT_PRIMARY);
        Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(COLOR_PRIMARY_LIGHT);
        Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (neg != null) neg.setTextColor(COLOR_TEXT_PRIMARY);
        Button neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neu != null) neu.setTextColor(COLOR_TEXT_PRIMARY);
    }
}