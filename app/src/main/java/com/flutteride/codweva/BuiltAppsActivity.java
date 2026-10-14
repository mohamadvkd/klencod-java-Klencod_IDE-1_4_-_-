package com.flutteride.codweva;

import androidx.appcompat.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// مكتبة داخلية لملفات APK التي نجح بناؤها - مع عرض أيقونة كل تطبيق (cache على القرص)
public final class BuiltAppsActivity extends AppCompatActivity {
    private static final int BG = 0xFF0D1117;
    private static final int SURFACE = 0xFF161B22;
    private static final int SURFACE_ALT = 0xFF21262D;
    private static final int PRIMARY = 0xFF1F6FEB;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int MUTED = TEXT;
    private static final int BORDER = 0xFF30363D;

    private BuiltAppsStore store;
    private LinearLayout list;
    private final Map<String, Bitmap> apkIconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new BuiltAppsStore(this);
        setTitle("مكتبة التطبيقات المبنية");
        buildLayout();

        // تنظيف cache القديم في الخلفية (أكثر من 30 يومًا)
        cleanupOldCachesAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null && list != null) render();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // تنظيف cache الذاكرة فقط - أيقونات القرص تبقى
        for (Bitmap bmp : apkIconCache.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        apkIconCache.clear();
    }

    // ============================================================
    // تنظيف cache القديم (أكثر من 30 يومًا)
    // ============================================================
    private void cleanupOldCachesAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
                    File dir = new File(getCacheDir(), "apk_icons");
                    if (!dir.isDirectory()) return;
                    File[] files = dir.listFiles();
                    if (files == null) return;
                    for (File f : files) {
                        if (f.isFile() && f.lastModified() < monthAgo) f.delete();
                    }
                } catch (Exception ignored) {}
            }
        }, "cache-cleanup").start();
    }

    // ============================================================
    // الواجهة
    // ============================================================
    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(18), dp(20), dp(20));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // الترويسة
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(16));
        header.setBackground(UiStyle.rounded(this, SURFACE, 18, BORDER, 1));
        TextView title = UiStyle.title(this, "مكتبة التطبيقات", 21);
        header.addView(title, params(0, -2, 1, 0));
        Button refresh = button("تحديث", SURFACE_ALT);
        refresh.setOnClickListener(v -> render());
        header.addView(refresh, params(-2, -2, 0, 0));
        root.addView(header, params(-1, -2, 0, 0));

        TextView subtitle = label("كل APK تم بناؤه محفوظ هنا ويمكن تثبيته من جديد لاحقًا.", 13, TEXT);
        subtitle.setPadding(dp(8), dp(12), dp(8), dp(14));
        root.addView(subtitle, params(-1, -2, 0, 0));

        // قائمة التطبيقات
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, params(-1, 0, 1, 0));

        setContentView(root);
        render();
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        List<File> apps = store.list();
        if (apps.isEmpty()) {
            TextView empty = label("لا توجد تطبيقات مبنية بعد.\nبعد نجاح أي عملية بناء ستظهر النسخة هنا تلقائيًا.", 15, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(80), dp(20), dp(80));
            empty.setBackground(UiStyle.rounded(this, SURFACE, 16, BORDER, 1));
            list.addView(empty, params(-1, -2, 0, 0));
            return;
        }
        for (File app : apps) addCard(app);
    }

    // ============================================================
    // بطاقة التطبيق - مع أيقونة
    // ============================================================
    private void addCard(final File app) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(UiStyle.rounded(this, SURFACE, 16, BORDER, 1));

        // الأيقونة - placeholder ثم async
        final FrameLayout iconContainer = new FrameLayout(this);
        int iconSize = dp(52);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconContainer.setLayoutParams(iconParams);

        // placeholder مؤقت (مربع رمادي)
        TextView placeholder = new TextView(this);
        placeholder.setText("");
        GradientDrawable placeholderBg = new GradientDrawable();
        placeholderBg.setCornerRadius(dp(12));
        placeholderBg.setColor(SURFACE_ALT);
        placeholderBg.setStroke(dp(1), BORDER);
        placeholder.setBackground(placeholderBg);
        placeholder.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        iconContainer.addView(placeholder);

        card.addView(iconContainer);

        // العمود النصي
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcParams = new LinearLayout.LayoutParams(0, -2, 1f);
        tcParams.setMarginStart(dp(14));
        card.addView(textCol, tcParams);

        String titleText = app.getName();
        if (titleText.toLowerCase(Locale.US).endsWith(".apk")) {
            titleText = titleText.substring(0, titleText.length() - 4);
        }

        TextView titleView = new TextView(this);
        titleView.setText(titleText);
        titleView.setTextColor(TEXT);
        titleView.setTextSize(15);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(titleView);

        TextView detailsView = new TextView(this);
        detailsView.setText(formatSize(app.length()) + "  •  " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(app.lastModified())));
        detailsView.setTextColor(TEXT);
        detailsView.setTextSize(11);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
        detailsParams.topMargin = dp(4);
        textCol.addView(detailsView, detailsParams);

        // أزرار الإجراء
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams acParams = new LinearLayout.LayoutParams(-2, -2);
        acParams.setMarginStart(dp(8));
        card.addView(actions, acParams);

        Button install = button("تثبيت", PRIMARY);
        install.setTextSize(12);
        install.setOnClickListener(v -> new FlutterBuildApi(this).installApk(app));
        LinearLayout.LayoutParams instParams = new LinearLayout.LayoutParams(dp(70), dp(40));
        actions.addView(install, instParams);

        Button delete = button("حذف", SURFACE_ALT);
        delete.setTextSize(12);
        delete.setOnClickListener(v -> confirmDelete(app));
        LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(dp(60), dp(40));
        delParams.setMarginStart(dp(6));
        actions.addView(delete, delParams);

        // غلاف بهامش
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(-1, -2);
        wrapParams.setMargins(0, dp(4), 0, dp(4));
        wrapper.setLayoutParams(wrapParams);
        wrapper.addView(card, new LinearLayout.LayoutParams(-1, -2));

        list.addView(wrapper);

        // تحميل الأيقونة (مع cache على القرص)
        loadApkIconAsync(app, iconContainer);
    }

    // ============================================================
    // تحميل أيقونة APK (ذاكرة -> قرص -> استخراج)
    // ============================================================
    private void loadApkIconAsync(final File apkFile, final FrameLayout container) {
        if (apkFile == null || container == null) return;

        // 1. جرّب الذاكرة أولًا (الأسرع)
        Bitmap cachedMem = apkIconCache.get(apkFile.getAbsolutePath());
        if (cachedMem != null && !cachedMem.isRecycled()) {
            applyIconToContainer(container, cachedMem);
            return;
        }

        // 2. جرّب القرص (سريع جدًا، بدون فك APK)
        Bitmap cachedDisk = IconCache.loadApkIcon(this, apkFile);
        if (cachedDisk != null && !cachedDisk.isRecycled()) {
            apkIconCache.put(apkFile.getAbsolutePath(), cachedDisk);
            applyIconToContainer(container, cachedDisk);
            return;
        }

        // 3. لم يُوجد - استخرج من APK (async)
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = extractApkIcon(apkFile);
                if (bmp == null || bmp.isRecycled()) return;

                // حفظ في الذاكرة + القرص
                apkIconCache.put(apkFile.getAbsolutePath(), bmp);
                IconCache.saveApkIcon(BuiltAppsActivity.this, apkFile, bmp);

                container.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (bmp.isRecycled()) return;
                        applyIconToContainer(container, bmp);
                    }
                });
            }
        }, "apk-icon-loader").start();
    }

    // يضع الأيقونة في الحاوية
    private void applyIconToContainer(FrameLayout container, Bitmap bmp) {
        if (container == null || bmp == null || bmp.isRecycled()) return;
        if (container.getChildCount() == 0) return;

        container.removeAllViews();
        ImageView iconView = new ImageView(this);
        iconView.setImageBitmap(bmp);
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iconView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        iconView.setClipToOutline(true);
        iconView.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });
        container.addView(iconView);
    }

    // ============================================================
    // استخراج الأيقونة من APK (المرة الأولى فقط)
    // ============================================================
    // يفتح APK كملف ZIP ويبحث عن ic_launcher.png داخل مجلدات drawable و mipmap
    // إذا فشل، يستخدم PackageManager كخيار احتياطي
    private Bitmap extractApkIcon(File apkFile) {
        if (apkFile == null || !apkFile.isFile()) return null;

        String key = apkFile.getAbsolutePath();
        if (apkIconCache.containsKey(key)) {
            Bitmap cached = apkIconCache.get(key);
            if (cached != null && !cached.isRecycled()) return cached;
        }

        Bitmap bestIcon = null;
        int bestDensity = -1;

        // المحاولة 1: قراءة APK كـ ZIP
        ZipInputStream zip = null;
        try {
            zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(apkFile)));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null) continue;

                // ابحث عن أيقونة launcher
                boolean isLauncherIcon = name.endsWith("ic_launcher.png")
                        || name.endsWith("ic_launcher_foreground.png")
                        || name.endsWith("ic_launcher.webp")
                        || name.endsWith("app_icon.png");

                if (!isLauncherIcon) continue;

                // أولوية للكثافة الأعلى
                int priority = getDensityPriority(name);
                if (priority < bestDensity) continue;

                // اقرأ البايتات
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    bos.write(buffer, 0, count);
                    if (bos.size() > 3 * 1024 * 1024) break; // حد 3 ميغابايت
                }
                zip.closeEntry();

                byte[] bytes = bos.toByteArray();
                if (bytes.length == 0) continue;

                // فك الصورة مع تحديد حجم العينة
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (opts.outWidth <= 0 || opts.outHeight <= 0) continue;

                int targetPx = dp(96);
                int sample = 1;
                int maxDim = Math.max(opts.outWidth, opts.outHeight);
                while (maxDim / (sample * 2) >= targetPx) sample *= 2;

                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (bmp != null) {
                    if (bestIcon != null && !bestIcon.isRecycled()) bestIcon.recycle();
                    bestIcon = bmp;
                    bestDensity = priority;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (zip != null) try { zip.close(); } catch (Exception ignored) {}
        }

        // المحاولة 2: PackageManager كخيار احتياطي
        if (bestIcon == null) {
            try {
                android.content.pm.PackageInfo info = getPackageManager()
                        .getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
                if (info != null && info.applicationInfo != null) {
                    android.content.pm.ApplicationInfo appInfo = info.applicationInfo;
                    appInfo.sourceDir = apkFile.getAbsolutePath();
                    appInfo.publicSourceDir = apkFile.getAbsolutePath();
                    android.graphics.drawable.Drawable d = appInfo.loadIcon(getPackageManager());
                    if (d != null) {
                        int size = dp(96);
                        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
                        d.setBounds(0, 0, size, size);
                        d.draw(canvas);
                        bestIcon = bmp;
                    }
                }
            } catch (Exception ignored) {}
        }

        apkIconCache.put(key, bestIcon);
        return bestIcon;
    }

    // أولوية كثافة الصورة
    private int getDensityPriority(String path) {
        if (path == null) return 0;
        if (path.contains("xxxhdpi")) return 5;
        if (path.contains("xxhdpi")) return 4;
        if (path.contains("xhdpi")) return 3;
        if (path.contains("hdpi")) return 2;
        if (path.contains("mdpi")) return 1;
        return 0;
    }

    // تنظيف cache الأيقونة من الذاكرة والقرص
    private void invalidateApkIconCache(File apkFile) {
        if (apkFile == null) return;
        // من الذاكرة
        Bitmap old = apkIconCache.remove(apkFile.getAbsolutePath());
        if (old != null && !old.isRecycled()) old.recycle();
        // من القرص
        IconCache.removeApkIcon(this, apkFile);
    }

    // ============================================================
    // حذف التطبيق
    // ============================================================
    private void confirmDelete(File app) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("حذف التطبيق")
                .setMessage("هل تريد حذف نسخة APK هذه من المكتبة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, which) -> {
                    try {
                        invalidateApkIconCache(app);   // من الذاكرة والقرص
                        store.delete(app);
                        render();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(this, e.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                }).create();
        dialog.show();
        UiStyle.styleDialog(dialog);
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinHeight(dp(46));
        button.setPadding(dp(12), dp(5), dp(12), dp(5));
        button.setBackground(UiStyle.rounded(this, color, 12,
                color == PRIMARY ? 0xFF58A6FF : BORDER, 1));
        return button;
    }

    private LinearLayout.LayoutParams params(int width, int height, float weight, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(margin), dp(5), dp(margin), dp(5));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}