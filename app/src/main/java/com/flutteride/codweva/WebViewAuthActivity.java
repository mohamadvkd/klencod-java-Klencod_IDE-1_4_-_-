package com.flutteride.codweva;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * نشاط داخلي لفتح صفحة GitHub داخل التطبيق، مع ملء رمز التحقق تلقائياً.
 * GitHub يستخدم حقول OTP منفصلة، لذا نوزع الرمز حرفاً حرفاً عبر JavaScript.
 */
public final class WebViewAuthActivity extends Activity {

    public static final String EXTRA_URL = "auth_url";
    public static final String EXTRA_USER_CODE = "auth_user_code";

    private WebView webView;
    private TextView statusView;
    private String userCode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        userCode = getIntent().getStringExtra(EXTRA_USER_CODE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 14, 20));

        // شريط علوي
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        topBar.setBackgroundColor(Color.rgb(20, 27, 36));

        Button close = new Button(this);
        close.setText("إغلاق");
        close.setTextColor(Color.WHITE);
        close.setAllCaps(false);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(close);

        TextView title = new TextView(this);
        title.setText("تسجيل الدخول إلى GitHub");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setPadding(dp(16), 0, 0, 0);
        topBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("جارٍ تحميل صفحة GitHub، والرمز سيُملأ تلقائياً...");
        statusView.setTextColor(Color.rgb(157, 171, 187));
        statusView.setTextSize(12);
        statusView.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (statusView != null) {
                    statusView.setText("إذا لم يُملأ الرمز تلقائياً، الصقه من الحافظة.");
                }
                if (userCode != null && !userCode.isEmpty()) {
                    fillOtpFields(view, userCode);
                }
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.trim().isEmpty()) {
            url = GitHubConfig.VERIFICATION_URL;
        }
        webView.loadUrl(url);
    }

    /**
     * يوزع رمز OTP على مربعات GitHub المنفصلة.
     * GitHub يستخدم حقول OTP بحرف واحد لكل مربع، لذا نوزع الرمز حرفاً حرفاً.
     */
    private void fillOtpFields(WebView view, String code) {
        final String cleanCode = code.replace("-", "").trim();
        if (cleanCode.isEmpty()) return;

        String js = "(function() {" +
                "var code = '" + cleanCode + "';" +
                "var inputs = document.querySelectorAll('input');" +
                "var otpInputs = [];" +
                "for (var i = 0; i < inputs.length; i++) {" +
                "  var inp = inputs[i];" +
                "  var isOtp = (inp.maxLength === 1) || " +
                "              (inp.inputMode === 'numeric') || " +
                "              (inp.type === 'tel') || " +
                "              (inp.getAttribute('autocomplete') === 'one-time-code');" +
                "  if (isOtp && !inp.disabled && !inp.readOnly) {" +
                "    otpInputs.push(inp);" +
                "  }" +
                "}" +
                "if (otpInputs.length >= code.length) {" +
                "  for (var j = 0; j < code.length; j++) {" +
                "    otpInputs[j].value = code[j];" +
                "    otpInputs[j].dispatchEvent(new Event('input', { bubbles: true }));" +
                "    otpInputs[j].dispatchEvent(new Event('change', { bubbles: true }));" +
                "    otpInputs[j].dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));" +
                "  }" +
                "  otpInputs[code.length - 1].focus();" +
                "}" +
                "})();";

        view.evaluateJavascript(js, null);
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("flutteride://")) {
            if (statusView != null) {
                statusView.setText("تم استلام التفويض. جارٍ العودة...");
            }
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}