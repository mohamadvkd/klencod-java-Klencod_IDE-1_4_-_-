package com.flutteride.codweva;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** شاشة محادثة AI محفوظة لكل مشروع، مع وضعين: مساعد ذكي / وكيل كامل. */
public final class AiChatActivity extends AppCompatActivity {
    public static final String EXTRA_PROJECT_DIR = "project_dir";
    public static final String EXTRA_CURRENT_FILE = "current_file";

    private static final int BG = 0xFF0D1117;
    private static final int SURFACE = 0xFF161B22;
    private static final int SURFACE_ALT = 0xFF21262D;
    private static final int PRIMARY = 0xFF1F6FEB;
    private static final int PRIMARY_DARK = 0xFF123E73;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int MUTED = 0xFF8B949E;
    private static final int USER_BUBBLE = 0xFF123E73;
    private static final int AI_BUBBLE = 0xFF161B22;
    private static final int BORDER = 0xFF30363D;
    private static final int SUCCESS = 0xFF2EA043;
    private static final int STEP_BG = 0xFF1A1F27;

    /** الحد الأقصى لجولات الوكيل. */
    private static final int MAX_AGENTIC_ROUNDS = 15;

    private LinearLayout messageList;
    private ScrollView messageScroll;
    private EditText input;
    private TextView contextView;
    private TextView progressView;
    private Button sendButton;
    private Button undoButton;
    private Button btnModeSmart;
    private Button btnModeAgentic;
    private boolean isSending = false;
    private volatile boolean cancelRequested = false;
    private volatile Thread requestThread;
    private List<AgentChangeStore.PreviousFile> lastAppliedChange;
    private AiSettings settings;
    private AiKeyStore keyStore;
    private AiModeStore modeStore;
    private AiConversationStore conversationStore;
    private File projectDir;
    private String currentFile;
    private String contextText = "";
    private final List<AiMessage> conversation = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AiSettings(this);
        keyStore = new AiKeyStore(this);
        modeStore = new AiModeStore(this);
        conversationStore = new AiConversationStore(this);
        String projectPath = getIntent().getStringExtra(EXTRA_PROJECT_DIR);
        currentFile = getIntent().getStringExtra(EXTRA_CURRENT_FILE);
        if (projectPath != null && !projectPath.isEmpty()) projectDir = new File(projectPath);
        setTitle("مساعد AI");
        if (projectDir != null) conversation.addAll(conversationStore.load(projectDir.getAbsolutePath()));
        buildLayout();
        if (conversation.isEmpty()) {
            String welcome = "مرحبًا. أنا مساعدك داخل KlencodIDE. أرى مشروعك كاملاً، ويمكنني قراءة أي ملف واقتراح التعديلات. لن أكتب أو أحذف أي شيء قبل موافقتك.";
            conversation.add(AiMessage.assistant(welcome));
            conversationStore.save(projectPath(), conversation);
        }
        renderConversation();
        updateContextLine();
        refreshModeButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateContextLine();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistConversation();
    }

    // ============================================================
    // البناء
    // ============================================================
    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // 1) الترويسة
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.setBackgroundColor(SURFACE);
        header.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(56)));

        Button menuButton = new Button(this);
        menuButton.setText("≡");
        menuButton.setTextColor(TEXT);
        menuButton.setTextSize(20);
        menuButton.setAllCaps(false);
        menuButton.setMinWidth(0);
        menuButton.setMinimumWidth(0);
        menuButton.setMinHeight(0);
        menuButton.setMinimumHeight(0);
        menuButton.setPadding(0, 0, 0, 0);
        menuButton.setBackground(UiStyle.rounded(this, 0x00000000, 10, 0x00000000, 0));
        menuButton.setOnClickListener(v -> showMenuDialog());
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("مساعد AI");
        title.setTextColor(TEXT);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        Button settingsButton = new Button(this);
        settingsButton.setText("إعدادات");
        settingsButton.setTextColor(TEXT);
        settingsButton.setTextSize(12);
        settingsButton.setAllCaps(false);
        settingsButton.setMinWidth(0);
        settingsButton.setMinimumWidth(0);
        settingsButton.setMinHeight(0);
        settingsButton.setMinimumHeight(0);
        settingsButton.setPadding(dp(12), 0, dp(12), 0);
        settingsButton.setBackground(UiStyle.rounded(this, SURFACE_ALT, 10, BORDER, 1));
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, AiSettingsActivity.class)));
        header.addView(settingsButton, new LinearLayout.LayoutParams(-2, dp(38)));

        root.addView(header);

        // 2) شريط اختيار الوضع
        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        modeBar.setBackgroundColor(SURFACE);
        modeBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("الوضع:");
        modeLabel.setTextColor(MUTED);
        modeLabel.setTextSize(11);
        modeLabel.setPadding(0, 0, dp(8), 0);
        modeBar.addView(modeLabel);

        btnModeSmart = new Button(this);
        btnModeSmart.setText("مساعد ذكي");
        btnModeSmart.setTextSize(11);
        btnModeSmart.setAllCaps(false);
        btnModeSmart.setMinWidth(0);
        btnModeSmart.setMinimumWidth(0);
        btnModeSmart.setMinHeight(0);
        btnModeSmart.setMinimumHeight(0);
        btnModeSmart.setPadding(dp(12), 0, dp(12), 0);
        btnModeSmart.setOnClickListener(v -> selectMode(AiMode.SMART));
        LinearLayout.LayoutParams smartParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        smartParams.setMarginEnd(dp(4));
        modeBar.addView(btnModeSmart, smartParams);

        btnModeAgentic = new Button(this);
        btnModeAgentic.setText("وكيل كامل");
        btnModeAgentic.setTextSize(11);
        btnModeAgentic.setAllCaps(false);
        btnModeAgentic.setMinWidth(0);
        btnModeAgentic.setMinimumWidth(0);
        btnModeAgentic.setMinHeight(0);
        btnModeAgentic.setMinimumHeight(0);
        btnModeAgentic.setPadding(dp(12), 0, dp(12), 0);
        btnModeAgentic.setOnClickListener(v -> selectMode(AiMode.AGENTIC));
        LinearLayout.LayoutParams agenticParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        agenticParams.setMarginStart(dp(4));
        modeBar.addView(btnModeAgentic, agenticParams);

        root.addView(modeBar, new LinearLayout.LayoutParams(-1, -2));

        // 3) سطر السياق
        contextView = new TextView(this);
        contextView.setText("");
        contextView.setTextColor(MUTED);
        contextView.setTextSize(11);
        contextView.setMaxLines(1);
        contextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        contextView.setPadding(dp(14), dp(4), dp(14), dp(4));
        contextView.setBackgroundColor(BG);
        root.addView(contextView, new LinearLayout.LayoutParams(-1, -2));

        // 4) التحذير
        LinearLayout warningRow = new LinearLayout(this);
        warningRow.setOrientation(LinearLayout.HORIZONTAL);
        warningRow.setGravity(Gravity.CENTER_VERTICAL);
        warningRow.setPadding(dp(14), dp(6), dp(14), dp(6));
        warningRow.setBackgroundColor(BG);

        TextView warningIcon = new TextView(this);
        warningIcon.setText("!");
        warningIcon.setTextColor(0xFFD29922);
        warningIcon.setTextSize(11);
        warningIcon.setTypeface(null, android.graphics.Typeface.BOLD);
        warningIcon.setGravity(Gravity.CENTER);
        GradientDrawable warnBg = new GradientDrawable();
        warnBg.setShape(GradientDrawable.OVAL);
        warnBg.setColor(0xFF2A2110);
        warnBg.setStroke(dp(1), 0xFF6B5318);
        warningIcon.setBackground(warnBg);
        warningRow.addView(warningIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView warningText = new TextView(this);
        warningText.setText("راجع التعديلات قبل تطبيقها — قد يخطئ AI.");
        warningText.setTextColor(MUTED);
        warningText.setTextSize(11);
        warningText.setPadding(dp(8), 0, 0, 0);
        warningText.setMaxLines(1);
        warningText.setEllipsize(TextUtils.TruncateAt.END);
        warningRow.addView(warningText, new LinearLayout.LayoutParams(0, -2, 1f));

        root.addView(warningRow, new LinearLayout.LayoutParams(-1, -2));

        // 5) شريط الحالة
        progressView = new TextView(this);
        progressView.setText("");
        progressView.setTextColor(PRIMARY);
        progressView.setTextSize(11);
        progressView.setVisibility(View.GONE);
        progressView.setPadding(dp(14), dp(4), dp(14), dp(4));
        root.addView(progressView, new LinearLayout.LayoutParams(-1, -2));

        // 6) قائمة الرسائل
        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setBackgroundColor(BG);

        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(8), dp(10), dp(8), dp(14));
        messageScroll.addView(messageList, new ScrollView.LayoutParams(-1, -2));

        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 7) زر التراجع
        undoButton = new Button(this);
        undoButton.setText("تراجع عن آخر تعديل");
        undoButton.setTextColor(TEXT);
        undoButton.setTextSize(12);
        undoButton.setAllCaps(false);
        undoButton.setMinHeight(0);
        undoButton.setMinimumHeight(0);
        undoButton.setBackground(UiStyle.rounded(this, SURFACE_ALT, 10, BORDER, 1));
        undoButton.setVisibility(View.GONE);
        undoButton.setOnClickListener(v -> undoLastChange());
        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(-1, dp(38));
        undoParams.setMargins(dp(14), 0, dp(14), dp(6));
        root.addView(undoButton, undoParams);

        // 8) صف الإدخال
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8), dp(6), dp(8), dp(8));
        composer.setBackgroundColor(SURFACE);
        composer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        Button attachButton = new Button(this);
        attachButton.setText("إرفاق");
        attachButton.setTextColor(TEXT);
        attachButton.setTextSize(11);
        attachButton.setAllCaps(false);
        attachButton.setMinWidth(0);
        attachButton.setMinimumWidth(0);
        attachButton.setMinHeight(0);
        attachButton.setMinimumHeight(0);
        attachButton.setPadding(dp(10), 0, dp(10), 0);
        attachButton.setBackground(UiStyle.rounded(this, SURFACE_ALT, 10, BORDER, 1));
        attachButton.setOnClickListener(v -> showFilePicker());
        composer.addView(attachButton, new LinearLayout.LayoutParams(-2, dp(40)));

        input = new EditText(this);
        input.setHint("اكتب رسالتك…");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(13);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setMaxLines(3);
        input.setMinLines(1);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(UiStyle.rounded(this, SURFACE_ALT, 18, BORDER, 1));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, -2, 1f);
        inputParams.setMargins(dp(6), 0, dp(6), 0);
        composer.addView(input, inputParams);

        sendButton = new Button(this);
        sendButton.setText("إرسال");
        sendButton.setTextColor(Color.WHITE);
        sendButton.setTextSize(12);
        sendButton.setAllCaps(false);
        sendButton.setMinWidth(0);
        sendButton.setMinimumWidth(0);
        sendButton.setMinHeight(0);
        sendButton.setMinimumHeight(0);
        sendButton.setPadding(dp(14), 0, dp(14), 0);
        sendButton.setBackground(UiStyle.rounded(this, PRIMARY, 12, 0xFF58A6FF, 1));
        sendButton.setOnClickListener(v -> {
            if (isSending) stopCurrentRequest();
            else sendMessage();
        });
        composer.addView(sendButton, new LinearLayout.LayoutParams(-2, dp(40)));

        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    // ============================================================
    // اختيار الوضع
    // ============================================================
    private void selectMode(AiMode mode) {
        if (mode == AiMode.AGENTIC && !modeStore.isAgenticAvailable()) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("وكيل كامل — غير متاح")
                    .setMessage("هذا الوضع غير متاح حالياً.")
                    .setPositiveButton("حسناً", null)
                    .create();
            dialog.show();
            UiStyle.styleDialog(dialog);
            return;
        }
        modeStore.setMode(mode);
        refreshModeButtons();
        updateContextLine();
        Toast.makeText(this,
                "الوضع الحالي: " + mode.displayName,
                Toast.LENGTH_SHORT).show();
    }

    private void refreshModeButtons() {
        AiMode current = modeStore.getMode();
        boolean smartActive = current == AiMode.SMART;
        boolean agenticActive = current == AiMode.AGENTIC;

        btnModeSmart.setBackground(UiStyle.rounded(this,
                smartActive ? PRIMARY : SURFACE_ALT,
                10, smartActive ? 0xFF58A6FF : BORDER, 1));
        btnModeSmart.setTextColor(TEXT);

        btnModeAgentic.setBackground(UiStyle.rounded(this,
                agenticActive ? PRIMARY : SURFACE_ALT,
                10, agenticActive ? 0xFF58A6FF : BORDER, 1));
        btnModeAgentic.setTextColor(agenticActive ? TEXT : MUTED);
    }

    // ============================================================
    // قائمة ⚙
    // ============================================================
    private void showMenuDialog() {
        String[] options = {"محادثة جديدة", "فتح ملف للسياق"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("خيارات")
                .setAdapter(UiStyle.dialogListAdapter(this, options), (d, which) -> {
                    switch (which) {
                        case 0: confirmClearConversation(); break;
                        case 1: showFilePicker(); break;
                    }
                })
                .setNegativeButton("إغلاق", null)
                .create();
        dialog.show();
        UiStyle.styleDialog(dialog);
    }

    // ============================================================
    // سطر السياق
    // ============================================================
    private void updateContextLine() {
        if (contextView == null) return;
        StringBuilder sb = new StringBuilder();
        String provider = settings.getDefaultProvider();
        String model = settings.getModel(provider);
        sb.append(displayName(provider));
        if (model != null && !model.isEmpty()) sb.append(" · ").append(model);
        if (currentFile != null && !currentFile.isEmpty()) sb.append("  |  ").append(currentFile);
        AiMode mode = modeStore.getMode();
        sb.append("  |  ").append(mode.displayName);
        contextView.setText(sb.toString());
    }

    // ============================================================
    // الإرسال — نقطة الدخول
    // ============================================================
    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        String providerId = settings.getDefaultProvider();
        String key = keyStore.get(providerId);
        if (key == null || key.trim().isEmpty()) {
            Toast.makeText(this, "أدخل مفتاح " + displayName(providerId) + " من الإعدادات أولًا", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AiSettingsActivity.class));
            return;
        }
        input.setText("");
        conversation.add(AiMessage.user(text));
        persistConversation();
        appendUser(text);
        isSending = true;
        cancelRequested = false;
        sendButton.setEnabled(true);
        sendButton.setText("إيقاف");

        final String model = settings.getModel(providerId);
        final String userMessage = text;
        final AiMode mode = modeStore.getMode();

        requestThread = new Thread(() -> {
            try {
                if (mode == AiMode.AGENTIC) {
                    runAgenticLoop(providerId, model, userMessage);
                } else {
                    runSmartMode(providerId, model, userMessage);
                }
            } catch (Exception e) {
                if (cancelRequested || Thread.currentThread().isInterrupted()) return;
                runOnUiThread(() -> {
                    appendAssistantPlain("تعذر الاتصال: " + safeError(e));
                    finishSending();
                });
            }
        });
        requestThread.start();
    }

    // ============================================================
    // الوضع الذكي (بدون أدوات)
    // ============================================================
    private void runSmartMode(String providerId, String model, String userMessage) throws Exception {
        runOnUiThread(() -> {
            progressView.setText("جارٍ تحليل المشروع وإعداد الرد…");
            progressView.setVisibility(View.VISIBLE);
        });

        String systemPrompt = buildSystemPrompt(userMessage);
        List<AiMessage> requestMessages = new ArrayList<>();
        requestMessages.add(AiMessage.system(systemPrompt));
        requestMessages.addAll(conversation);

        AiProvider provider = AiProviderFactory.create(this, providerId);
        AiResponse response = provider.send(requestMessages, model, settings.getTemperature(), settings.getMaxTokens());
        if (cancelRequested || Thread.currentThread().isInterrupted()) return;

        String cleanText = AgentEngine.removeActionBlocks(response.text);
        List<AgentEngine.AgentAction> parsedActions = AgentEngine.parseActions(response.text);
        final List<AgentEngine.AgentAction> actions = parsedActions;

        runOnUiThread(() -> {
            if (cancelRequested) return;
            conversation.add(AiMessage.assistant(cleanText));
            persistConversation();
            if (actions.isEmpty()) {
                appendAssistantPlain(cleanText);
            } else {
                appendAssistantWithActions(cleanText, actions.size());
                showActionsDialog(actions);
            }
            finishSending();
        });
    }

    // ============================================================
    // الوضع الكامل (Agentic Loop)
    // ============================================================
    private void runAgenticLoop(String providerId, String model, String userMessage) {
        runOnUiThread(() -> {
            progressView.setText("الوكيل الكامل يعمل… الجولة 1");
            progressView.setVisibility(View.VISIBLE);
        });

        AgentToolExecutor executor = new AgentToolExecutor(projectDir);

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(buildAgenticSystemPrompt(userMessage)));
        messages.addAll(conversation);

        AiProvider provider = AiProviderFactory.create(this, providerId);
        List<AgentTool.ToolCall> dummy = new ArrayList<>();

        int round = 0;
        StringBuilder finalText = new StringBuilder();
        boolean reachedFinalAnswer = false;

        while (round < MAX_AGENTIC_ROUNDS && !cancelRequested) {
            round++;
            final int currentRound = round;

            runOnUiThread(() -> progressView.setText(
                    "الوكيل الكامل — الجولة " + currentRound + "/" + MAX_AGENTIC_ROUNDS));

            AiResponse response;
            try {
                response = provider.sendWithTools(
                        messages, model,
                        settings.getTemperature(),
                        settings.getMaxTokens(),
                        dummy);
            } catch (Exception e) {
                final String errMsg = safeError(e);
                runOnUiThread(() -> {
                    appendAssistantPlain("تعذر الاتصال بالوكيل: " + errMsg);
                    finishSending();
                });
                return;
            }

            if (cancelRequested) return;

            // لا tool calls → الوكيل انتهى برد نصي
            if (!response.hasToolCalls()) {
                finalText.append(response.text);
                reachedFinalAnswer = true;
                break;
            }

            // ✅ نسخ محلية — لتجاوز قيد الـ lambda
            final AiResponse responseForRaw = response;
            final List<AiMessage> messagesRef = messages;
            final List<AiMessage> conversationRef = conversation;

            runOnUiThread(() -> {
                if (responseForRaw.rawAssistantMessage instanceof org.json.JSONObject) {
                    AiMessage raw = AiMessage.assistantWithToolCalls(
                            (org.json.JSONObject) responseForRaw.rawAssistantMessage);
                    conversationRef.add(raw);
                    messagesRef.add(raw);
                }
            });

            // ✅ نُنفّذ كل أداة
            for (AgentTool.ToolCall call : response.toolCalls) {
                if (cancelRequested) return;

                final AgentTool.ToolCall callForUi = call;
                runOnUiThread(() -> appendStepBubble(callForUi));

                AgentTool.ToolResult result = executor.execute(call);

                AiMessage toolMsg;
                if (AiSettings.GEMINI.equals(providerId)) {
                    toolMsg = AiMessage.tool(call.name, result.content);
                } else {
                    toolMsg = AiMessage.tool(call.id, result.content);
                }
                messages.add(toolMsg);

                final AgentTool.ToolResult resultForUi = result;
                runOnUiThread(() -> appendStepResultBubble(resultForUi));
            }
        }

        // ✅ نُعالج الرد النهائي
        final String text = finalText.toString();
        String cleanText = AgentEngine.removeActionBlocks(text);
        List<AgentEngine.AgentAction> parsedActions = AgentEngine.parseActions(text);
        final List<AgentEngine.AgentAction> actions = parsedActions;
        final boolean hitLimit = (round >= MAX_AGENTIC_ROUNDS && !reachedFinalAnswer);
        final boolean finalAnswerReached = reachedFinalAnswer;   // ✅ نسخة final
        final String finalCleanText = cleanText;

        runOnUiThread(() -> {
            if (cancelRequested) return;

            if (!finalCleanText.isEmpty()) {
                conversation.add(AiMessage.assistant(finalCleanText));
                persistConversation();
            }

            if (!actions.isEmpty()) {
                appendAssistantWithActions(finalCleanText, actions.size());
                showActionsDialog(actions);
            } else if (finalAnswerReached) {
                appendAssistantPlain(finalCleanText.isEmpty()
                        ? "انتهى الوكيل دون رد نصي."
                        : finalCleanText);
            } else if (hitLimit) {
                appendLimitReachedBubble();
            } else {
                appendAssistantPlain(finalCleanText.isEmpty()
                        ? "انتهى الوكيل."
                        : finalCleanText);
            }

            finishSending();
        });
    }

    private String buildAgenticSystemPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("أنت وكيل برمجي داخل KlencodIDE. أنت خبير في Flutter وDart.\n\n");

        prompt.append("لديك أدوات يمكنك استدعاؤها:\n");
        prompt.append("• read_file(path): يقرأ ملفاً قبل تعديله.\n");
        prompt.append("• list_files(): يعرض كل ملفات المشروع.\n");
        prompt.append("• get_file_info(path): حجم + أسطر ملف.\n");
        prompt.append("• search(query, extension?): يبحث عن نص في المشروع.\n\n");

        prompt.append("آلية العمل:\n");
        prompt.append("1. عندما تحتاج رؤية ملف، استدعِ read_file.\n");
        prompt.append("2. إذا احتجت البحث، استخدم search.\n");
        prompt.append("3. بعد الفهم الكافي، أرسل تعديلاتك عبر وسوم <klencod_action>.\n");
        prompt.append("4. لا تكتب أي تعديل خارج الوسوم.\n\n");

        prompt.append(AgentEngine.buildInstruction()).append("\n\n");

        if (projectDir != null && projectDir.isDirectory()) {
            try {
                ProjectContextBuilder.Context ctx = ProjectContextBuilder.build(
                        projectDir, currentFile, userMessage, contextText);
                if (ctx != null && ctx.text != null && !ctx.text.isEmpty()) {
                    prompt.append(ctx.text);
                }
            } catch (Exception e) {
                prompt.append("\n[تعذر بناء سياق المشروع: ").append(e.getMessage()).append("]\n");
            }
        }

        return prompt.toString();
    }

    // ============================================================
    // system prompt للوضع الذكي
    // ============================================================
    private String buildSystemPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("أنت مساعد برمجي داخل KlencodIDE. أنت خبير في Flutter وDart. ");
        prompt.append("تستطيع قراءة أي ملف في المشروع واقتراح تعديلات دقيقة عليه. ");
        prompt.append("لا تفترض أنك عدّلت أي ملف، ولا تطلب تغييرات في إعدادات الخادم أو البناء.\n\n");
        prompt.append(AgentEngine.buildInstruction()).append("\n\n");

        if (projectDir != null && projectDir.isDirectory()) {
            try {
                ProjectContextBuilder.Context ctx = ProjectContextBuilder.build(
                        projectDir, currentFile, userMessage, contextText);
                if (ctx != null && ctx.text != null && !ctx.text.isEmpty()) {
                    prompt.append(ctx.text);
                }
            } catch (Exception e) {
                prompt.append("\n[تعذر بناء سياق المشروع: ").append(e.getMessage()).append("]\n");
            }
        }
        return prompt.toString();
    }

    // ============================================================
    // فقاعات الخطوات (للوكيل الكامل)
    // ============================================================
    private void appendStepBubble(AgentTool.ToolCall call) {
        if (messageList == null) return;

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        row.setPadding(dp(4), dp(2), dp(4), dp(2));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(10), dp(6), dp(10), dp(6));
        bubble.setBackground(UiStyle.rounded(this, STEP_BG, 10, BORDER, 1));

        TextView line = new TextView(this);
        line.setTextColor(TEXT);
        line.setTextSize(11);
        line.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.setText("→ " + formatToolCall(call));
        bubble.addView(line, params(-1, -2, 0, 0));

        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);
        row.addView(bubble, new LinearLayout.LayoutParams(width, -2));
        messageList.addView(row, new LinearLayout.LayoutParams(-1, -2));
        scrollToBottom();
    }

    private void appendStepResultBubble(AgentTool.ToolResult result) {
        if (messageList == null) return;

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        row.setPadding(dp(20), dp(0), dp(4), dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(10), dp(4), dp(10), dp(4));
        bubble.setBackground(UiStyle.rounded(this, 0x00000000, 8, 0x00000000, 0));

        String summary = summarizeToolResult(result);
        TextView line = new TextView(this);
        line.setTextColor(MUTED);
        line.setTextSize(10);
        line.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.setText("← " + summary);
        bubble.addView(line, params(-1, -2, 0, 0));

        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);
        row.addView(bubble, new LinearLayout.LayoutParams(width, -2));
        messageList.addView(row, new LinearLayout.LayoutParams(-1, -2));
        scrollToBottom();
    }

    private void appendLimitReachedBubble() {
        if (messageList == null) return;

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackground(UiStyle.rounded(this, STEP_BG, 10, 0xFFD29922, 1));

        TextView line = new TextView(this);
        line.setTextColor(TEXT);
        line.setTextSize(12);
        line.setText("وصل الوكيل إلى الحد الأقصى من الجولات (" + MAX_AGENTIC_ROUNDS + ").");
        bubble.addView(line, params(-1, -2, 0, 0));

        Button continueBtn = new Button(this);
        continueBtn.setText("اطلب المزيد");
        continueBtn.setTextColor(TEXT);
        continueBtn.setTextSize(11);
        continueBtn.setAllCaps(false);
        continueBtn.setMinHeight(0);
        continueBtn.setMinimumHeight(0);
        continueBtn.setBackground(UiStyle.rounded(this, SURFACE_ALT, 8, BORDER, 1));
        continueBtn.setOnClickListener(v ->
                Toast.makeText(this, "اكتب طلبك التالي، وسيستمر الوكيل من حيث توقف.", Toast.LENGTH_LONG).show());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-1, dp(34));
        btnParams.topMargin = dp(6);
        bubble.addView(continueBtn, btnParams);

        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);
        row.addView(bubble, new LinearLayout.LayoutParams(width, -2));
        messageList.addView(row, new LinearLayout.LayoutParams(-1, -2));
        scrollToBottom();
    }

    private String formatToolCall(AgentTool.ToolCall call) {
        switch (call.name) {
            case AgentTool.TOOL_READ_FILE:
                return "قراءة " + call.getString("path");
            case AgentTool.TOOL_LIST_FILES:
                return "سرد ملفات المشروع";
            case AgentTool.TOOL_FILE_INFO:
                return "معلومات " + call.getString("path");
            case AgentTool.TOOL_SEARCH:
                String q = call.getString("query");
                String ext = call.getOptionalString("extension");
                return "بحث عن \"" + q + "\"" + (ext != null ? " في *." + ext : "");
            default:
                return call.name;
        }
    }

    private String summarizeToolResult(AgentTool.ToolResult result) {
        if (result == null) return "";
        if (result.isError) {
            return "خطأ: " + shorten(result.content, 100);
        }
        String content = result.content == null ? "" : result.content;
        int newlineIdx = content.indexOf('\n');
        String firstLine = newlineIdx > 0 ? content.substring(0, newlineIdx) : content;
        if (firstLine.length() > 120) firstLine = firstLine.substring(0, 120) + "…";
        return firstLine;
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    // ============================================================
    // إدارة الطلب
    // ============================================================
    private void stopCurrentRequest() {
        if (!isSending) return;
        cancelRequested = true;
        Thread worker = requestThread;
        if (worker != null) worker.interrupt();
        finishSending();
        progressView.setText("تم إيقاف الطلب");
        progressView.setVisibility(View.VISIBLE);
    }

    private void finishSending() {
        isSending = false;
        requestThread = null;
        sendButton.setEnabled(true);
        sendButton.setText("إرسال");
        if (progressView != null) progressView.setVisibility(View.GONE);
    }

    // ============================================================
    // الإرفاق اليدوي
    // ============================================================
    private void showFilePicker() {
        if (projectDir == null || !projectDir.isDirectory()) {
            Toast.makeText(this, "افتح مشروعًا أولًا لإرفاق ملف", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            List<String> files = new ProjectFileService(projectDir).listFiles();
            if (files.isEmpty()) {
                Toast.makeText(this, "لا توجد ملفات قابلة للإرفاق", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] choices = files.toArray(new String[0]);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("اختر ملفًا لإرفاقه يدوياً")
                    .setAdapter(UiStyle.dialogListAdapter(this, choices), (d, which) -> attachFile(choices[which]))
                    .setNegativeButton("إلغاء", null)
                    .create();
            dialog.show();
            UiStyle.styleDialog(dialog);
        } catch (Exception e) {
            Toast.makeText(this, safeError(e), Toast.LENGTH_SHORT).show();
        }
    }

    private void attachFile(String relativePath) {
        if (projectDir == null) return;
        try {
            String content = new ProjectFileService(projectDir).readFile(relativePath);
            contextText = "\n--- " + relativePath + " ---\n" + content + "\n--- نهاية " + relativePath + " ---\n";
            currentFile = relativePath;
            updateContextLine();
            Toast.makeText(this, "تم إرفاق " + relativePath, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, safeError(e), Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // حوار الموافقة على التعديلات
    // ============================================================
    private void showActionsDialog(List<AgentEngine.AgentAction> actions) {
        if (projectDir == null || actions == null || actions.isEmpty()) return;
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView title = label("التغييرات المقترحة — راجعها قبل التطبيق", 13, PRIMARY);
        title.setPadding(0, 0, 0, dp(8));
        preview.addView(title, params(-1, -2, 0, 0));
        TextView diff = label(buildDiffPreview(actions), 12, TEXT);
        diff.setTypeface(android.graphics.Typeface.MONOSPACE);
        diff.setTextIsSelectable(true);
        diff.setGravity(Gravity.LEFT);
        ScrollView diffScroll = new ScrollView(this);
        diffScroll.setBackground(round(SURFACE_ALT, 10, 0xFF30363D, 1));
        diffScroll.addView(diff, new ScrollView.LayoutParams(-1, -2));
        preview.addView(diffScroll, params(-1, dp(360), 0, 0));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مراجعة تغييرات الملفات")
                .setView(preview)
                .setNegativeButton("رفض", null)
                .setPositiveButton("تطبيق التغييرات", (d, which) -> applyActions(actions))
                .create();
        dialog.show();
        UiStyle.styleDialog(dialog);
    }

    private String buildDiffPreview(List<AgentEngine.AgentAction> actions) {
        StringBuilder out = new StringBuilder();
        ProjectFileService files = new ProjectFileService(projectDir);
        for (AgentEngine.AgentAction action : actions) {
            String oldText = "";
            try { if (files.exists(action.path)) oldText = files.readFile(action.path); } catch (Exception ignored) { }
            String newText = action.isDelete ? "" : (action.content == null ? "" : action.content);
            out.append(action.isDelete ? "حذف " : "تعديل ").append(action.path).append("\n");
            String[] oldLines = oldText.split("\\n", -1);
            String[] newLines = newText.split("\\n", -1);
            int count = Math.max(oldLines.length, newLines.length);
            for (int i = 0; i < count; i++) {
                String oldLine = i < oldLines.length ? oldLines[i] : null;
                String newLine = i < newLines.length ? newLines[i] : null;
                if (oldLine != null && newLine != null && oldLine.equals(newLine)) out.append("  ").append(oldLine).append("\n");
                else {
                    if (oldLine != null && !oldLine.isEmpty()) out.append("- ").append(oldLine).append("\n");
                    if (newLine != null && !newLine.isEmpty()) out.append("+ ").append(newLine).append("\n");
                }
            }
            out.append("\n");
            if (out.length() > 30000) { out.append("… تم اختصار العرض …\n"); break; }
        }
        return out.toString();
    }

    private void applyActions(List<AgentEngine.AgentAction> actions) {
        new Thread(() -> {
            try {
                ProjectFileService files = new ProjectFileService(projectDir);
                List<AgentChangeStore.PreviousFile> previous = AgentChangeStore.capture(files, actions);
                for (AgentEngine.AgentAction action : actions) AgentEngine.apply(files, action);
                lastAppliedChange = previous;
                runOnUiThread(() -> {
                    undoButton.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "تم تطبيق التغييرات بعد موافقتك", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل تطبيق التغييرات: " + safeError(e), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void undoLastChange() {
        final List<AgentChangeStore.PreviousFile> previous = lastAppliedChange;
        if (previous == null || previous.isEmpty()) return;
        lastAppliedChange = null;
        new Thread(() -> {
            try {
                AgentChangeStore.restore(new ProjectFileService(projectDir), previous);
                runOnUiThread(() -> {
                    undoButton.setVisibility(View.GONE);
                    Toast.makeText(this, "تم التراجع عن آخر تعديل", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل التراجع: " + safeError(e), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmClearConversation() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("محادثة جديدة")
                .setMessage("سيتم حذف محادثة هذا المشروع فقط. هل تريد المتابعة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("متابعة", (d, which) -> {
                    conversation.clear();
                    conversationStore.clear(projectPath());
                    String welcome = "بدأت محادثة جديدة. أرى مشروعك كاملاً — اكتب طلبك.";
                    conversation.add(AiMessage.assistant(welcome));
                    persistConversation();
                    renderConversation();
                }).create();
        dialog.show();
        UiStyle.styleDialog(dialog);
    }

    // ============================================================
    // رسم الرسائل
    // ============================================================
    private void renderConversation() {
        if (messageList == null) return;
        messageList.removeAllViews();
        for (AiMessage message : conversation) {
            if ("user".equals(message.role)) appendUser(message.content);
            else if ("assistant".equals(message.role)) appendAssistantPlain(message.content);
        }
        scrollToBottom();
    }

    private void appendUser(String text) { addMessageBubble("أنت", text, true, 0); }

    private void appendAssistantPlain(String text) {
        addMessageBubble("مساعد AI", text == null ? "" : text, false, 0);
    }

    private void appendAssistantWithActions(String text, int actionCount) {
        addMessageBubble("مساعد AI", text == null ? "" : text, false, actionCount);
    }

    private void addMessageBubble(String sender, String text, boolean user, int actionCount) {
        if (messageList == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackground(UiStyle.rounded(this, user ? USER_BUBBLE : AI_BUBBLE, 14, user ? 0xFF1F6FEB : BORDER, 1));
        TextView senderView = label(sender, 10, MUTED);
        senderView.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView body = label(text, 14, TEXT);
        body.setTextIsSelectable(true);
        body.setGravity(Gravity.RIGHT);
        bubble.addView(senderView, params(-1, -2, 0, 0));
        bubble.addView(body, params(-1, -2, 0, 4));

        if (!user) {
            if (actionCount > 0) {
                TextView hint = label(
                        actionCount == 1
                                ? "تعديل واحد مقترح — راجعه في الحوار."
                                : actionCount + " تعديلات مقترحة — راجعها في الحوار.",
                        10, PRIMARY);
                hint.setPadding(0, dp(4), 0, 0);
                bubble.addView(hint, params(-1, -2, 0, 0));
            } else {
                TextView hint = label("قراءة فقط — لا توجد تعديلات.", 10, MUTED);
                hint.setPadding(0, dp(4), 0, 0);
                bubble.addView(hint, params(-1, -2, 0, 0));
            }
        }

        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        row.addView(bubble, new LinearLayout.LayoutParams(width, -2));
        messageList.addView(row, new LinearLayout.LayoutParams(-1, -2));
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (messageScroll != null) messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void persistConversation() {
        if (conversationStore != null) conversationStore.save(projectPath(), conversation);
    }

    private String projectPath() { return projectDir == null ? "" : projectDir.getAbsolutePath(); }

    private String displayName(String id) {
        return AiSettings.GEMINI.equals(id) ? "Gemini" : AiSettings.OPENROUTER.equals(id) ? "OpenRouter" : "OpenAI";
    }

    private String safeError(Exception e) {
        if (e == null) return "Unknown error";
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(e.getClass().getName()).append("\n\n");
        String msg = e.getMessage();
        sb.append("Message: ");
        sb.append(msg == null || msg.trim().isEmpty() ? "(no message)" : msg);
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
        StackTraceElement[] stack = e.getStackTrace();
        if (stack != null && stack.length > 0) {
            sb.append("\n\nStack trace (top 5):");
            int count = Math.min(5, stack.length);
            for (int i = 0; i < count; i++) {
                sb.append("\n  at ").append(stack[i].toString());
            }
        }
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

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height, float weight, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(margin), dp(margin), dp(margin), dp(margin));
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}