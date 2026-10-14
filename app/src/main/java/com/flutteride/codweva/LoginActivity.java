package com.flutteride.codweva;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class LoginActivity extends Activity {

    private static final int BG = Color.rgb(10, 14, 20);
    private static final int SURFACE = Color.rgb(20, 27, 36);
    private static final int ALT = Color.rgb(27, 36, 47);
    private static final int BORDER = Color.rgb(48, 62, 77);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(157, 171, 187);
    private static final int BLUE = Color.rgb(55, 145, 255);
    private static final String DEVICE_CODE_CHANNEL = "github-device-code";
    private static final int DEVICE_CODE_NOTIFICATION = 7710;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 7711;

    private LinearLayout root;
    private TextView status;
    private TextView code;
    private Button connect;
    private SecureStore secureStore;
    private String verificationUri = GitHubConfig.VERIFICATION_URL;
    private GitHubApi.DeviceCode pendingDeviceCode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        secureStore = new SecureStore(this);
        buildUi();
        refreshConnection();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri data = intent.getData();
        if (data != null && "flutteride".equals(data.getScheme())) {
            String codeValue = data.getQueryParameter("code");
            if (codeValue != null && !codeValue.isEmpty()) {
                Toast.makeText(this, "تم استلام الرمز، جارٍ التحقق...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(18));
        root.setBackgroundColor(BG);

        TextView eyebrow = text("KLENCOD  /  CONNECT", 11, BLUE, true);
        eyebrow.setLetterSpacing(0.08f);
        root.addView(eyebrow);
        TextView title = text("اربط مساحة العمل بـ GitHub", 27, TEXT, true);
        root.addView(title, params(0, 6, 0, 0));
        root.addView(text("تفويض رسمي من دون خادم وسيط", 13, MUTED, false), params(0, 3, 0, 20));

        LinearLayout explanation = card();
        explanation.addView(text("كيف يعمل الربط؟", 17, TEXT, true));
        TextView body = text("يتم طلب رمز مؤقت من GitHub. سيتم ملء الرمز تلقائياً في صفحة التحقق. أنت فقط تضغط Continue ثم توافق. لا يتم حفظ كلمة المرور.", 14, MUTED, false);
        body.setLineSpacing(0, 1.15f);
        explanation.addView(body, params(0, 8, 0, 0));
        root.addView(explanation, params(0, 0, 0, 14));

        status = text("جارٍ التحقق من الحالة...", 14, MUTED, false);
        root.addView(status, params(2, 0, 0, 10));

        code = text("", 30, TEXT, true);
        code.setGravity(Gravity.CENTER);
        code.setLetterSpacing(0.14f);
        code.setBackground(round(ALT, 20, BORDER));
        root.addView(code, params(0, 0, 0, 10, 74));

        Button copy = button("نسخ رمز التحقق", ALT, TEXT);
        root.addView(copy, params(0, 0, 0, 8, 48));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String value = code.getText().toString();
                if (value.trim().isEmpty()) return;
                ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText("GitHub code", value));
                Toast.makeText(LoginActivity.this, "تم نسخ الرمز", Toast.LENGTH_SHORT).show();
            }
        });

        Button open = button("فتح صفحة التحقق داخل التطبيق", BLUE, Color.WHITE);
        root.addView(open, params(0, 0, 0, 8, 50));
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openVerificationPage(); }
        });

        connect = button("بدء تسجيل الدخول", BLUE, Color.WHITE);
        root.addView(connect, params(0, 0, 0, 8, 50));
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { beginLogin(); }
        });

        Button close = button("إغلاق", Color.TRANSPARENT, MUTED);
        root.addView(close, params(0, 4, 0, 0, 44));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        setContentView(root);
    }

    private void refreshConnection() {
        final String token = secureStore.readGitHubToken();

        if (!token.isEmpty()) {
            String savedUser = secureStore.readGitHubUsername();
            showAuthenticatedState(savedUser);
            enterAppSoon();
            return;
        }

        status.setText("لم يتم ربط حساب GitHub");
        connect.setText("بدء تسجيل الدخول");
    }

    private void beginLogin() {
        if (!secureStore.readGitHubToken().isEmpty()) {
            secureStore.clearGitHubToken();
            refreshConnection();
            return;
        }
        connect.setEnabled(false);
        connect.setText("جارٍ طلب رمز مؤقت...");
        GitHubApi.requestDeviceCode(new GitHubApi.Callback<GitHubApi.DeviceCode>() {
            @Override public void onSuccess(final GitHubApi.DeviceCode device) {
                pendingDeviceCode = device;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connect.setEnabled(true);
                        connect.setText("انتظر موافقة GitHub");
                        code.setText(device.userCode);
                        verificationUri = device.verificationUri == null || device.verificationUri.trim().isEmpty() ? GitHubConfig.VERIFICATION_URL : device.verificationUri;
                        status.setText("سيتم فتح GitHub، والرمز جاهز، اضغط Continue ثم Authorize");
                        showDeviceCodeNotification(device);
                        openVerificationPage();
                    }
                });
                GitHubApi.pollAccessToken(device, new GitHubApi.Callback<String>() {
                    @Override public void onSuccess(final String token) {
                        if (!secureStore.saveGitHubToken(token)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() { showError("تعذر حفظ التفويض بأمان"); }
                            });
                            return;
                        }
                        GitHubApi.getUser(token, new GitHubApi.Callback<String>() {
                            @Override public void onSuccess(final String user) {
                                secureStore.saveGitHubUsername(user);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        clearDeviceCodeNotification();
                                        connect.setEnabled(true);
                                        showAuthenticatedState(user);
                                        enterAppSoon();
                                    }
                                });
                            }

                            @Override public void onError(String message) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() { showError("تم التفويض لكن تعذر قراءة الحساب"); }
                                });
                            }
                        });
                    }

                    @Override public void onError(final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                connect.setEnabled(true);
                                connect.setText("بدء تسجيل الدخول");
                                showError(message);
                            }
                        });
                    }
                });
            }

            @Override public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connect.setEnabled(true);
                        connect.setText("بدء تسجيل الدخول");
                        showError(message);
                    }
                });
            }
        });
    }

    /**
     * يفتح صفحة التحقق داخل التطبيق، مع تمرير الرمز إلى WebView لملئه تلقائياً.
     */
    private void openVerificationPage() {
        try {
            // انسخ الرمز احتياطياً
            if (pendingDeviceCode != null && pendingDeviceCode.userCode != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", pendingDeviceCode.userCode));
            }

            Intent intent = new Intent(this, WebViewAuthActivity.class);
            intent.putExtra(WebViewAuthActivity.EXTRA_URL, verificationUri);
            if (pendingDeviceCode != null) {
                intent.putExtra(WebViewAuthActivity.EXTRA_USER_CODE, pendingDeviceCode.userCode);
            }
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "تعذر فتح صفحة التحقق داخل التطبيق", Toast.LENGTH_LONG).show();
        }
    }

    private void showDeviceCodeNotification(final GitHubApi.DeviceCode device) {
        pendingDeviceCode = device;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        postDeviceCodeNotification(device);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingDeviceCode != null) {
            postDeviceCodeNotification(pendingDeviceCode);
        }
    }

    private void postDeviceCodeNotification(GitHubApi.DeviceCode device) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(DEVICE_CODE_CHANNEL, "تسجيل الدخول إلى GitHub", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("رمز تسجيل الدخول المؤقت إلى GitHub");
            manager.createNotificationChannel(channel);
        }
        Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(device.verificationUri == null || device.verificationUri.trim().isEmpty() ? GitHubConfig.VERIFICATION_URL : device.verificationUri));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, DEVICE_CODE_NOTIFICATION, openIntent, flags);
        String message = "الرمز: " + device.userCode + "\nسيتم فتح GitHub تلقائياً. اضغط Continue ثم Authorize.";
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, DEVICE_CODE_CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("رمز تسجيل الدخول إلى GitHub")
                .setContentText("الرمز: " + device.userCode)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pending)
                .setAutoCancel(false)
                .setOngoing(false)
                .setOnlyAlertOnce(true);
        manager.notify(DEVICE_CODE_NOTIFICATION, builder.build());
    }

    private void clearDeviceCodeNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(DEVICE_CODE_NOTIFICATION);
        pendingDeviceCode = null;
    }

    private void showAuthenticatedState(String user) {
        status.setText("تم الربط بالحساب: " + user);
        code.setText("");
        connect.setEnabled(true);
        connect.setText("دخول إلى التطبيق");
        connect.setTextColor(Color.WHITE);
        connect.setBackground(round(BLUE, 13, BORDER));
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { enterApp(); }
        });
    }

    private void enterAppSoon() {
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) enterApp();
            }
        }, 500L);
    }

    private void enterApp() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // ✅ التعديل الوحيد: عرض الخطأ الحقيقي الكامل بدون تفسير أو احتمالات.
    private void showError(String message) {
        String fullText = message == null ? "Unknown error" : message;

        // عرض النص الكامل داخل ScrollView
        TextView content = new TextView(this);
        content.setText(fullText);
        content.setTextColor(Color.rgb(230, 237, 243));
        content.setTextSize(12);
        content.setTypeface(Typeface.MONOSPACE);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.setTextIsSelectable(true);
        content.setGravity(Gravity.START);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(13, 17, 23));
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(-1, -2));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.55)));

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this)
                .setTitle("تفاصيل الخطأ")
                .setView(scroll)
                .setPositiveButton("نسخ", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("error", fullText));
                    Toast.makeText(this, "تم نسخ التفاصيل", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("إعادة المحاولة", (d, w) -> {
                    if (connect != null) {
                        connect.setEnabled(true);
                        connect.setText("بدء تسجيل الدخول");
                    }
                    beginLogin();
                })
                .setNegativeButton("إغلاق", null);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // تطبيق النمط العام على النافذة
        if (dialog.getWindow() != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.rgb(13, 17, 23));
            gd.setCornerRadius(dp(16));
            gd.setStroke(dp(1), Color.rgb(48, 62, 77));
            dialog.getWindow().setBackgroundDrawable(gd);
        }
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(round(SURFACE, 22, BORDER));
        box.setElevation(dp(3));
        return box;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private Button button(String value, int background, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(13);
        b.setTextColor(color);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(background, 15, background == Color.TRANSPARENT ? Color.TRANSPARENT : BORDER));
        return b;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams params(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private LinearLayout.LayoutParams params(int left, int top, int right, int bottom, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(height));
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}