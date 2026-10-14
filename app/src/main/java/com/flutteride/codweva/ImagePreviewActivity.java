package com.flutteride.codweva;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * شاشة عرض الصور كاملة الشاشة مع التنقل.
 */
public final class ImagePreviewActivity extends AppCompatActivity {

    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_TEXT_DIM = 0xFF8B949E;

    private String templateId;
    private TemplateInfo template;
    private List<File> previewFiles = new ArrayList<>();
    private ViewPager2 viewPager;
    private TextView counterView;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        templateId = getIntent().getStringExtra("template_id");
        int startIndex = getIntent().getIntExtra("start_index", 0);

        if (templateId == null) { finish(); return; }
        template = TemplateManager.findById(this, templateId);
        if (template == null) { finish(); return; }

        // جهّز العرض
        TemplateManager.prepareShowcase(this, template);
        previewFiles = new ArrayList<>(template.previewFiles);

        if (previewFiles.isEmpty()) {
            Toast.makeText(this, "لا توجد معاينات", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        buildLayout();
        if (viewPager != null) {
            int safeIndex = Math.max(0, Math.min(startIndex, previewFiles.size() - 1));
            viewPager.setCurrentItem(safeIndex, false);
        }
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BLACK);

        // ============ ViewPager للصور ============
        viewPager = new ViewPager2(this);
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPager.setBackgroundColor(COLOR_BLACK);
        viewPager.setAdapter(new ImagePagerAdapter(previewFiles));
        viewPager.setOffscreenPageLimit(1);

        FrameLayout.LayoutParams pagerParams =
                new FrameLayout.LayoutParams(-1, -1);
        root.addView(viewPager, pagerParams);

        // ============ زر X (إغلاق) ============
        Button closeBtn = new Button(this);
        closeBtn.setText("X");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(18);
        closeBtn.setAllCaps(false);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v -> finish());

        FrameLayout.LayoutParams closeParams =
                new FrameLayout.LayoutParams(dp(56), dp(56));
        closeParams.gravity = Gravity.TOP | Gravity.START;
        closeParams.setMargins(dp(12), dp(12), 0, 0);
        root.addView(closeBtn, closeParams);

        // ============ العداد (1 / 3) ============
        counterView = new TextView(this);
        counterView.setTextColor(COLOR_TEXT);
        counterView.setTextSize(14);
        counterView.setTypeface(null, android.graphics.Typeface.BOLD);
        counterView.setBackgroundColor(0x55000000);
        counterView.setPadding(dp(12), dp(6), dp(12), dp(6));

        FrameLayout.LayoutParams counterParams =
                new FrameLayout.LayoutParams(-2, -2);
        counterParams.gravity = Gravity.TOP | Gravity.END;
        counterParams.setMargins(0, dp(20), dp(20), 0);
        root.addView(counterView, counterParams);

        // ============ شريط سفلي ============
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(0x33000000);
        bottomBar.setPadding(dp(8), dp(10), dp(8), dp(10));

        Button prevBtn = new Button(this);
        prevBtn.setText("السابق");
        prevBtn.setTextColor(Color.WHITE);
        prevBtn.setAllCaps(false);
        prevBtn.setTextSize(13);
        prevBtn.setBackgroundColor(Color.TRANSPARENT);
        prevBtn.setOnClickListener(v -> {
            int cur = viewPager.getCurrentItem();
            if (cur > 0) viewPager.setCurrentItem(cur - 1, true);
        });
        bottomBar.addView(prevBtn, new LinearLayout.LayoutParams(dp(100), dp(44)));

        View spacer = new View(this);
        bottomBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        Button nextBtn = new Button(this);
        nextBtn.setText("التالي");
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setAllCaps(false);
        nextBtn.setTextSize(13);
        nextBtn.setBackgroundColor(Color.TRANSPARENT);
        nextBtn.setOnClickListener(v -> {
            int cur = viewPager.getCurrentItem();
            if (cur < previewFiles.size() - 1) viewPager.setCurrentItem(cur + 1, true);
        });
        bottomBar.addView(nextBtn, new LinearLayout.LayoutParams(dp(100), dp(44)));

        FrameLayout.LayoutParams bottomParams =
                new FrameLayout.LayoutParams(-1, -2);
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bottomParams);

        // ============ مراقبة التغيير ============
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateCounter(position);
            }
        });

        // عرض العداد الحالي
        updateCounter(0);

        setContentView(root);
    }

    private void updateCounter(int index) {
        if (counterView != null) {
            counterView.setText((index + 1) + " / " + previewFiles.size());
        }
    }

    // ============================================================
    // Adapter
    // ============================================================
    private class ImagePagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ImagePagerAdapter.VH> {

        private final List<File> files;

        ImagePagerAdapter(List<File> files) {
            this.files = files;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            frame.setBackgroundColor(COLOR_BLACK);

            ImageView img = new ImageView(parent.getContext());
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            frame.addView(img, new FrameLayout.LayoutParams(-1, -1));

            return new VH(frame, img);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            File f = files.get(position);
            if (f == null || !f.exists()) return;

            // فكّ بحجم محدود
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);

            int target = dp(1400);
            int sample = 1;
            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            while (maxDim / (sample * 2) >= target) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (bmp != null) {
                holder.image.setImageBitmap(bmp);
            }
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            final ImageView image;
            VH(View itemView, ImageView image) {
                super(itemView);
                this.image = image;
            }
        }
    }
}