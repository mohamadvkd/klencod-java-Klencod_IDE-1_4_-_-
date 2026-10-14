package com.flutteride.codweva;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * نظام التصميم المشترك للواجهة. لا يُستخدم لتلوين نصوص البرمجة داخل المحرر.
 */
public final class UiStyle {
    public static final int BACKGROUND = 0xFF0D1117;
    public static final int SURFACE = 0xFF161B22;
    public static final int SURFACE_ALT = 0xFF21262D;
    public static final int PRIMARY = 0xFF1F6FEB;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int MUTED = TEXT;
    public static final int BORDER = 0xFF30363D;
    public static final int ACCENT = 0xFF58A6FF;

    private UiStyle() { }

    // ============================================================
    // أدوات أساسية
    // ============================================================

    public static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(Context context, int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    public static void applySurface(View view, Context context, int color, int radiusDp, boolean border) {
        if (view == null) return;
        view.setBackground(rounded(context, color, radiusDp, BORDER, border ? 1 : 0));
    }

    // ============================================================
    // نصوص
    // ============================================================

    public static TextView title(Context context, String text, float size) {
        TextView view = text(context, text, size);
        view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    public static TextView text(Context context, String value, float size) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextColor(TEXT);
        view.setTextSize(size);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView sectionLabel(Context context, String value) {
        TextView view = text(context, value, 12);
        view.setTypeface(null, Typeface.BOLD);
        view.setLetterSpacing(0.02f);
        return view;
    }

    // ============================================================
    // حقول الإدخال
    // ============================================================

    public static EditText field(Context context, String hint) {
        EditText field = new EditText(context);
        field.setHint(hint == null ? "" : hint);
        field.setHintTextColor(TEXT);
        field.setTextColor(TEXT);
        field.setTextSize(14);
        field.setSingleLine(true);
        field.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        field.setBackground(rounded(context, SURFACE, 14, BORDER, 1));
        return field;
    }

    // ============================================================
    // فواصل ومسافات
    // ============================================================

    public static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(BORDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(context, 1)));
        return divider;
    }

    public static View spacer(Context context, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(context, heightDp)));
        return spacer;
    }

    // ============================================================
    // أزرار
    // ============================================================

    public static Button button(Context context, String text, boolean primary) {
        Button button = new Button(context);
        button.setText(text);
        applyButton(button, context, primary);
        return button;
    }

    public static void applyButton(Button button, Context context, boolean primary) {
        if (button == null) return;
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 44));
        button.setMinimumHeight(dp(context, 44));
        button.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));
        button.setBackground(rounded(context, primary ? PRIMARY : SURFACE_ALT, 14,
                primary ? ACCENT : BORDER, 1));
    }

    /** زر إجراء أساسي (أزرق) — للاستخدام في الإجراءات الرئيسية */
    public static Button primaryButton(Context context, String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight(dp(context, 44));
        btn.setMinimumHeight(dp(context, 44));
        btn.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        btn.setBackground(rounded(context, PRIMARY, 12, ACCENT, 1));
        return btn;
    }

    /** زر إجراء خفيف (شفاف مع حدود) — للاستخدام الثانوي */
    public static Button ghostButton(Context context, String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(TEXT);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight(dp(context, 44));
        btn.setMinimumHeight(dp(context, 44));
        btn.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        btn.setBackground(rounded(context, 0x00000000, 12, BORDER, 1));
        return btn;
    }

    // ============================================================
    // أيقونات مرسومة (بدون إيموجي)
    // ============================================================

    /** دائرة ملونة بحرف أول — لمشروع أو عنصر */
    public static TextView circleIcon(Context context, String letter, int color) {
        TextView view = new TextView(context);
        view.setText(letter == null ? "?" : letter);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(20);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        view.setBackground(gd);
        int size = dp(context, 48);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }

    /** مربع بزوايا دائرية + رمز Unicode بسيط */
    public static TextView squareIcon(Context context, String symbol, int color) {
        TextView view = new TextView(context);
        view.setText(symbol == null ? "" : symbol);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(16);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(context, 12));
        gd.setColor(color);
        view.setBackground(gd);
        int size = dp(context, 40);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }

    /** زر دائري نصي — للاستخدام في AppBar (بحث، إعدادات) */
    public static TextView circleTextButton(Context context, String symbol, int bgColor) {
        TextView view = new TextView(context);
        view.setText(symbol == null ? "" : symbol);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(14);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(bgColor);
        view.setBackground(gd);
        int size = dp(context, 40);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    // ============================================================
    // بطاقات
    // ============================================================

    /** بطاقة مشروع أنيقة */
    public static LinearLayout projectCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        card.setBackground(rounded(context, SURFACE, 16, BORDER, 1));
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    // ============================================================
    // الحوارات
    // ============================================================

    public static void styleDialog(final AlertDialog dialog) {
        if (dialog == null) return;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(rounded(dialog.getContext(), BACKGROUND, 18, BORDER, 1));
        }

        int titleId = dialog.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(TEXT);
            title.setTypeface(null, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(TEXT);
        styleButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), TEXT);
        styleButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), TEXT);
        styleButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), TEXT);

        final ListView list = dialog.getListView();
        if (list != null) {
            list.setBackgroundColor(BACKGROUND);
            list.setDivider(new ColorDrawable(BORDER));
            list.setDividerHeight(dp(dialog.getContext(), 1));
            list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(View parent, View child) { styleTextTree(child, TEXT); }
                @Override public void onChildViewRemoved(View parent, View child) { }
            });
            for (int i = 0; i < list.getChildCount(); i++) styleTextTree(list.getChildAt(i), TEXT);
            list.post(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < list.getChildCount(); i++) styleTextTree(list.getChildAt(i), TEXT);
                }
            });
        }
        if (dialog.getWindow() != null) dialog.getWindow().getDecorView().post(new Runnable() {
            @Override public void run() {
                styleTextTree(dialog.getWindow().getDecorView(), TEXT);
                styleButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), TEXT);
                styleButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), TEXT);
                styleButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), TEXT);
            }
        });
    }

    private static void styleButton(Button button, int color) {
        if (button != null) {
            button.setTextColor(color);
            button.setAllCaps(false);
        }
    }

    public static void styleDialog(final android.app.AlertDialog dialog) {
        if (dialog == null) return;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(rounded(dialog.getContext(), SURFACE, 18, BORDER, 1));
        }

        int titleId = dialog.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(TEXT);
            title.setTypeface(null, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(TEXT);
        styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE), TEXT);
        styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE), TEXT);
        styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL), TEXT);

        final ListView list = dialog.getListView();
        if (list != null) {
            list.setBackgroundColor(SURFACE);
            list.setDivider(new ColorDrawable(BORDER));
            list.setDividerHeight(dp(dialog.getContext(), 1));
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().getDecorView().post(new Runnable() {
                @Override public void run() {
                    styleTextTree(dialog.getWindow().getDecorView(), TEXT);
                    styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE), TEXT);
                    styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE), TEXT);
                    styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL), TEXT);
                }
            });
        }
    }

    private static void styleTextTree(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(color);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleTextTree(group.getChildAt(i), color);
        }
    }

    // ============================================================
    // Spinner والمحولات
    // ============================================================

    public static <T> ArrayAdapter<T> spinnerAdapter(final android.content.Context context, T[] values) {
        return new ArrayAdapter<T>(context, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(14);
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(14);
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 14));
                view.setBackgroundColor(SURFACE);
                return view;
            }
        };
    }

    public static ArrayAdapter<String> dialogListAdapter(android.content.Context context, String[] values) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                styleDialogRow(row);
                return row;
            }
        };
    }

    public static ArrayAdapter<String> dialogChoiceAdapter(android.content.Context context, String[] values) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                styleDialogRow(row);
                return row;
            }
        };
    }

    private static void styleDialogRow(TextView row) {
        row.setTextColor(TEXT);
        row.setTextSize(15);
        row.setBackgroundColor(SURFACE);
        row.setPadding(24, 18, 24, 18);
        row.setSingleLine(false);
    }

    public static void styleSpinner(Spinner spinner) {
        if (spinner != null) {
            spinner.setBackground(rounded(spinner.getContext(), SURFACE, 12, BORDER, 1));
            spinner.setPadding(dp(spinner.getContext(), 10), 0, dp(spinner.getContext(), 10), 0);
        }
    }
}