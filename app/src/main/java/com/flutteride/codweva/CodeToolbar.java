package com.flutteride.codweva;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * شريط الأدوات السريع للمحرر — v2.0
 *
 * ✅ التحسينات:
 *   - يظهر فقط عند فتح لوحة المفاتيح
 *   - ارتفاع أنحف (34dp بدل 40dp)
 *   - أزرار أصغر وأخف
 *   - مراقبة تغييرات المحرر
 */
public class CodeToolbar extends HorizontalScrollView {

    private LinearLayout container;
    private Context context;
    private EditText editor;
    private SharedPreferences prefs;

    // ألوان متناسقة مع المحرر
    private static final int BG_COLOR = 0xFF0D1117;
    private static final int BTN_COLOR = 0xFF161B22;
    private static final int BTN_PRESSED_COLOR = 0xFF1F6FEB;
    private static final int BTN_TEXT_COLOR = 0xFFE6EDF3;
    private static final int SEPARATOR_COLOR = 0xFF30363D;
    private static final int CORNER_RADIUS = 6;

    // ✅ الأبعاد الجديدة (أصغر)
    private static final int BTN_HEIGHT_DP = 32;
    private static final int BTN_WIDTH_DP = 40;
    private static final int BAR_PADDING_VERTICAL_DP = 4;

    public CodeToolbar(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public CodeToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public CodeToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    private void init() {
        prefs = context.getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE);

        setBackgroundColor(BG_COLOR);
        setHorizontalScrollBarEnabled(false);

        // خلفية ذات زوايا مستديرة
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), CORNER_RADIUS);
            }
        });

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(4), dp(BAR_PADDING_VERTICAL_DP), dp(4), dp(BAR_PADDING_VERTICAL_DP));

        addView(container);

        createAllButtons();
    }

    private void createAllButtons() {
        // الأقواس
        addSmallButton("(");
        addSmallButton(")");
        addSmallButton("{");
        addSmallButton("}");
        addSmallButton("[");
        addSmallButton("]");
        addSmallSeparator();

        // العمليات
        addSmallButton("=");
        addSmallButton("+");
        addSmallButton("-");
        addSmallButton("*");
        addSmallButton("/");
        addSmallSeparator();

        // النصوص والفواصل
        addSmallButton("\"");
        addSmallButton("'");
        addSmallButton(";");
        addSmallButton(",");
        addSmallButton(".");
        addSmallSeparator();

        // منطق
        addSmallButton("!");
        addSmallButton("?");
        addSmallButton("&&");
        addSmallButton("||");
        addSmallSeparator();

        // تحكم
        addSmallButton("↵");
        addSmallButton("⇥");
        addSmallSeparator();

        // تراجع/إعادة
        addSmallButton("↩");
        addSmallButton("↪");
        addSmallSeparator();

        // تكبير/تصغير
        addSmallButton("➕");
        addSmallButton("➖");
        addSmallSeparator();

        // بحث
        addSmallButton("🔍");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addSmallButton(String symbol) {
        Button button = new Button(context);
        button.setText(symbol);
        button.setTextSize(11);
        button.setTypeface(Typeface.MONOSPACE);

        // ✅ أبعاد أصغر
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setHeight(dp(BTN_HEIGHT_DP));
        button.setWidth(dp(BTN_WIDTH_DP));

        // خلفية مستديرة
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(BTN_COLOR);
        drawable.setCornerRadius(dp(CORNER_RADIUS));
        button.setBackground(drawable);

        button.setTextColor(BTN_TEXT_COLOR);
        button.setAllCaps(false);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // تأثير الضغط
                GradientDrawable pressedDrawable = new GradientDrawable();
                pressedDrawable.setColor(BTN_PRESSED_COLOR);
                pressedDrawable.setCornerRadius(dp(CORNER_RADIUS));
                button.setBackground(pressedDrawable);
                button.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        GradientDrawable originalDrawable = new GradientDrawable();
                        originalDrawable.setColor(BTN_COLOR);
                        originalDrawable.setCornerRadius(dp(CORNER_RADIUS));
                        button.setBackground(originalDrawable);
                    }
                }, 120);
                insertSymbol(symbol);
            }
        });

        // ✅ هامش صغير بين الأزرار
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(BTN_WIDTH_DP), dp(BTN_HEIGHT_DP));
        params.setMargins(dp(1), 0, dp(1), 0);
        container.addView(button, params);
    }

    private void addSmallSeparator() {
        View separator = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(1), dp(16));
        params.setMargins(dp(3), 0, dp(3), 0);
        separator.setLayoutParams(params);
        separator.setBackgroundColor(SEPARATOR_COLOR);
        container.addView(separator);
    }

    public void setEditor(EditText editorRef) {
        this.editor = editorRef;
    }

    private void insertSymbol(String symbol) {
        if (editor == null) return;

        // معالجة أزرار التحكم
        if (symbol.equals("↩")) {
            if (editor.getContext() instanceof MainActivity) {
                ((MainActivity) editor.getContext()).performUndo();
            }
            return;
        } else if (symbol.equals("↪")) {
            if (editor.getContext() instanceof MainActivity) {
                ((MainActivity) editor.getContext()).performRedo();
            }
            return;
        } else if (symbol.equals("➕")) {
            if (editor.getContext() instanceof MainActivity) {
                ((MainActivity) editor.getContext()).performZoomIn();
            }
            return;
        } else if (symbol.equals("➖")) {
            if (editor.getContext() instanceof MainActivity) {
                ((MainActivity) editor.getContext()).performZoomOut();
            }
            return;
        } else if (symbol.equals("🔍")) {
            if (editor.getContext() instanceof MainActivity) {
                ((MainActivity) editor.getContext()).showFindBar();
            }
            return;
        }

        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();

        if (start < 0 || end < 0) {
            int newPosition = editor.getText().length();
            editor.getText().insert(newPosition, symbol);
            editor.setSelection(newPosition + symbol.length());
            return;
        }

        Editable editable = editor.getText();

        if (symbol.equals("{") || symbol.equals("(") || symbol.equals("[") || symbol.equals("<")) {
            String closeSymbol = "";
            if (symbol.equals("{")) closeSymbol = "}";
            else if (symbol.equals("(")) closeSymbol = ")";
            else if (symbol.equals("[")) closeSymbol = "]";
            else if (symbol.equals("<")) closeSymbol = ">";

            editable.replace(start, end, symbol + closeSymbol);
            editor.setSelection(start + 1);
        } else if (symbol.equals("\"") || symbol.equals("'")) {
            editable.replace(start, end, symbol + symbol);
            editor.setSelection(start + 1);
        } else if (symbol.equals("↵")) {
            editable.replace(start, end, "\n");
            editor.setSelection(start + 1);
        } else if (symbol.equals("⇥")) {
            editable.replace(start, end, "    ");
            editor.setSelection(start + 4);
        } else {
            editable.replace(start, end, symbol);
            editor.setSelection(start + symbol.length());
        }
    }
}