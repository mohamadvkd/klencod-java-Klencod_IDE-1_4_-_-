package com.flutteride.codweva;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AiSettingsActivity extends AppCompatActivity {
    private static final int BG = 0xFF0D1117;
    private static final int SURFACE = 0xFF161B22;
    private static final int PRIMARY = 0xFF1F6FEB;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int MUTED = TEXT;

    private Spinner providerSpinner;
    private EditText keyInput;
    private EditText modelInput;
    private TextView status;
    private AiKeyStore keyStore;
    private AiSettings settings;
    private final List<String> providerIds = Arrays.asList(
            AiSettings.OPENAI, AiSettings.GEMINI, AiSettings.OPENROUTER);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyStore = new AiKeyStore(this);
        settings = new AiSettings(this);
        setTitle("إعدادات الذكاء الاصطناعي");
        buildLayout();
    }

    private void buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(UiStyle.rounded(this, SURFACE, 18, 0xFF30363D, 1));
        TextView title = UiStyle.title(this, "إعدادات الذكاء الاصطناعي", 22);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView note = label("أدخل مفتاحك الشخصي. لا يُرسل إلى خادم البناء ولا يظهر في السجلات.", 13, TEXT);
        note.setPadding(0, dp(8), 0, 0);
        header.addView(note, new LinearLayout.LayoutParams(-1, -2));
        root.addView(header, params(-1, -2, 0, 0, 0, 20));

        root.addView(label("المزود", 13, TEXT));
        providerSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = UiStyle.spinnerAdapter(this,
                new String[]{"OpenAI", "Gemini", "OpenRouter"});
        providerSpinner.setAdapter(adapter);
        UiStyle.styleSpinner(providerSpinner);
        int selected = providerIds.indexOf(settings.getDefaultProvider());
        providerSpinner.setSelection(selected < 0 ? 0 : selected);
        root.addView(providerSpinner, params(-1, -2, 0, 0, 0, 14));

        root.addView(label("مفتاح API", 13, TEXT));
        keyInput = UiStyle.field(this, "أدخل المفتاح هنا");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(keyInput, params(-1, -2, 0, 0, 0, 14));

        root.addView(label("اسم النموذج", 13, TEXT));
        modelInput = UiStyle.field(this, "مثال: gpt-4o-mini أو gemini-2.0-flash");
        root.addView(modelInput, params(-1, -2, 0, 0, 0, 16));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        Button save = button("حفظ", PRIMARY);
        Button test = button("اختبار الاتصال", SURFACE);
        buttons.addView(save, params(0, -2, 1, 0, 0, 0));
        buttons.addView(test, params(0, -2, 1, 8, 0, 0));
        root.addView(buttons, params(-1, -2, 0, 0, 0, 16));

        status = label("", 13, TEXT);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackground(UiStyle.rounded(this, SURFACE, 12, 0xFF30363D, 1));
        root.addView(status, params(-1, -2, 0, 0, 0, 16));

        Button clear = button("حذف مفتاح المزود الحالي", SURFACE);
        root.addView(clear, params(-1, -2, 0, 0, 0, 12));
        clear.setOnClickListener(v -> {
            String id = currentProvider();
            keyStore.clear(id);
            keyInput.setText("");
            status.setText("تم حذف مفتاح " + displayName(id));
        });

        providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadProvider(providerIds.get(position));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        save.setOnClickListener(v -> saveCurrent());
        test.setOnClickListener(v -> testCurrent());
        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadProvider(String providerId) {
        String key = keyStore.get(providerId);
        keyInput.setText(key == null ? "" : key);
        modelInput.setText(settings.getModel(providerId));
        status.setText("جاهز: " + displayName(providerId));
    }

    private void saveCurrent() {
        if (!keyStore.isAvailable()) {
            status.setText("تعذر تهيئة التخزين المشفر، لم يُحفظ المفتاح");
            return;
        }
        String id = currentProvider();
        String key = keyInput.getText().toString().trim();
        String model = modelInput.getText().toString().trim();
        if (key.isEmpty() || model.isEmpty()) {
            status.setText("أدخل المفتاح واسم النموذج");
            return;
        }
        keyStore.save(id, key);
        settings.setModel(id, model);
        settings.setDefaultProvider(id);
        status.setText("تم حفظ إعدادات " + displayName(id) + " بأمان");
    }

    private void testCurrent() {
        saveCurrent();
        String id = currentProvider();
        String key = keyStore.get(id);
        String model = modelInput.getText().toString().trim();
        if (key == null || key.isEmpty() || model.isEmpty()) return;
        status.setText("جارٍ اختبار الاتصال...");
        new Thread(() -> {
            try {
                AiProvider provider = AiProviderFactory.create(this, id);
                List<AiMessage> messages = new ArrayList<>();
                messages.add(AiMessage.user("أجب بكلمة واحدة فقط: متصل"));
                AiResponse response = provider.send(messages, model, 0d, 32);
                runOnUiThread(() -> status.setText("نجح الاتصال بـ " + displayName(id) + ": " + response.text.trim()));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("فشل الاختبار: " + safeError(e)));
            }
        }).start();
    }

    private String currentProvider() {
        int position = providerSpinner == null ? 0 : providerSpinner.getSelectedItemPosition();
        return providerIds.get(Math.max(0, Math.min(position, providerIds.size() - 1)));
    }

    private String displayName(String id) {
        return AiSettings.GEMINI.equals(id) ? "Gemini" : AiSettings.OPENROUTER.equals(id) ? "OpenRouter" : "OpenAI";
    }

    // ✅ التعديل الوحيد: عرض الخطأ الحقيقي الكامل مع إخفاء المفاتيح الحساسة.
    private String safeError(Exception e) {
        if (e == null) return "Unknown error";

        StringBuilder sb = new StringBuilder();

        // 1) نوع الاستثناء
        sb.append("Type: ").append(e.getClass().getName()).append("\n\n");

        // 2) الرسالة الأصلية
        String msg = e.getMessage();
        sb.append("Message: ");
        sb.append(msg == null || msg.trim().isEmpty() ? "(no message)" : msg);

        // 3) سلسلة الأسباب (حتى 5 مستويات)
        Throwable cause = e.getCause();
        int depth = 1;
        while (cause != null && depth <= 5) {
            sb.append("\n\nCaused by [").append(depth).append("]: ")
              .append(cause.getClass().getName());
            sb.append("\nMessage: ");
            String causeMsg = cause.getMessage();
            sb.append(causeMsg == null || causeMsg.trim().isEmpty() ? "(no message)" : causeMsg);
            cause = cause.getCause();
            depth++;
        }

        // 4) Stack trace مختصر (أول 5 أسطر)
        StackTraceElement[] stack = e.getStackTrace();
        if (stack != null && stack.length > 0) {
            sb.append("\n\nStack trace (top 5):");
            int count = Math.min(5, stack.length);
            for (int i = 0; i < count; i++) {
                sb.append("\n  at ").append(stack[i].toString());
            }
        }

        // 5) إخفاء المفاتيح والرموز الحساسة إن ظهرت في الرسالة
        String result = sb.toString();
        result = result.replaceAll("(?i)(key|token|authorization|bearer|api[_-]?key)\\s*[:=]\\s*\\S+", "$1: [hidden]");
        result = result.replaceAll("(?i)(sk-)[A-Za-z0-9_\\-]{10,}", "$1[hidden]");
        result = result.replaceAll("(?i)(ghp_|gho_|ghu_|ghs_|ghr_)[A-Za-z0-9]{10,}", "$1[hidden]");

        return result;
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
        button.setBackground(UiStyle.rounded(this, color, 12, color == PRIMARY ? 0xFF58A6FF : 0xFF30363D, 1));
        return button;
    }

    private LinearLayout.LayoutParams params(int w, int h, float weight, int left, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(dp(left), dp(top), dp(left), dp(bottom));
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}