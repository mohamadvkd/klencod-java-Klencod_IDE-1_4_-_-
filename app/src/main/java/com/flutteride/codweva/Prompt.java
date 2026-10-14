package com.flutteride.codweva;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class Prompt {

    // أنماط التصميم الثلاثة (بدون رموز)
    private static final String[] STYLE_NAMES = {
            "أنيق وبسيط",
            "عصري وجريء",
            "نظيف وعملي"
    };

    private static final String[] STYLE_PROMPTS = {
            // النمط الأول: أنيق
            "Design a Flutter application with an elegant and minimal interface, relying on wide white spaces,\n" +
                    "calm colors (such as white, light gray, and a soft primary color).\n" +
                    "Use clean fonts and appropriate font sizes, with a focus on proper spacing between elements.\n" +
                    "Make the design comfortable for the eyes, with touches of subtle shadows and rounded corners.\n" +
                    "Ensure the experience is smooth and intuitive, so the user feels comfortable and confident.\n" +
                    "\nDetails of the application I want to design:",

            // النمط الثاني: عصري
            "Design a Flutter application with a modern and bold interface, relying on vibrant and harmonious colors,\n" +
                    "with color gradients and non-traditional geometric shapes.\n" +
                    "Use modern and bold fonts for titles, with distinctive icons and light animation upon interaction.\n" +
                    "Make the design dynamic and attractive, using cards and buttons with 3D effects.\n" +
                    "Ensure the design is enjoyable and motivating to use, with simple surprising elements.\n" +
                    "\nDetails of the application I want to design:",

            // النمط الثالث: عملي
            "Design a Flutter application with a clean and functional interface, focusing on ease of use and clarity.\n" +
                    "Rely on high contrast between background and text, with neutral colors and gray shades,\n" +
                    "and a distinctive color for interactive elements (buttons, links, notifications).\n" +
                    "Use an organized layout with clear headings and logical content division.\n" +
                    "Ensure all interactive elements are large enough, with appropriate spacing,\n" +
                    "and a distraction-free experience, so the user can complete tasks quickly and smoothly.\n" +
                    "\nDetails of the application I want to design:"
    };

    // الواجهة الرئيسية
    public static void showPromptDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("اختر نمط التصميم");

        builder.setAdapter(UiStyle.dialogListAdapter(context, STYLE_NAMES), (dialog, which) -> {
            showDescriptionInput(context, which);
        });

        builder.setNegativeButton("إلغاء", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        UiStyle.styleDialog(dialog);
    }

    // صندوق إدخال وصف التطبيق
    private static void showDescriptionInput(Context context, int styleIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("صف تطبيقك");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 18));

        final EditText input = new EditText(context);
        input.setHint("مثال: تطبيق لإدارة المهام مع الإشعارات...");
        input.setHintTextColor(0xFFE6EDF3);
        input.setTextColor(0xFFE6EDF3);
        input.setBackground(UiStyle.rounded(context, 0xFF161B22, 12, 0xFF30363D, 1));
        input.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        layout.addView(input);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("إنشاء البرومبت", (dialog, which) -> {
            String userDescription = input.getText().toString().trim();
            if (userDescription.isEmpty()) {
                Toast.makeText(context, "اكتب وصف التطبيق أولًا", Toast.LENGTH_SHORT).show();
                return;
            }
            String finalPrompt = buildFinalPrompt(styleIndex, userDescription);
            showFinalPrompt(context, finalPrompt);
        });

        builder.setNegativeButton("إلغاء", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        UiStyle.styleDialog(dialog);
    }

    // دمج النمط مع وصف المستخدم
    private static String buildFinalPrompt(int styleIndex, String userDescription) {
        String stylePrompt = STYLE_PROMPTS[styleIndex];
        return stylePrompt + "\n\n" + userDescription;
    }

    // عرض البرومبت النهائي مع زر النسخ
    private static void showFinalPrompt(Context context, String prompt) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("البرومبت الجاهز");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 16));

        final EditText promptView = new EditText(context);
        promptView.setText(prompt);
        promptView.setTextColor(0xFFE0E0E0);
        promptView.setBackground(UiStyle.rounded(context, 0xFF0D1117, 12, 0xFF30363D, 1));
        promptView.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        promptView.setMinLines(8);
        promptView.setMaxLines(15);
        promptView.setGravity(Gravity.TOP);
        promptView.setTypeface(Typeface.MONOSPACE);
        promptView.setTextSize(12);
        promptView.setFocusable(false);
        promptView.setFocusableInTouchMode(false);
        promptView.setKeyListener(null); // منع التعديل

        layout.addView(promptView);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("نسخ", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("prompt", prompt);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "تم نسخ البرومبت", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("إغلاق", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        UiStyle.styleDialog(dialog);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}