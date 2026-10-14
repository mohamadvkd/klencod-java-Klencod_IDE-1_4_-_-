package com.flutteride.codweva;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FindReplaceBar extends LinearLayout {

	// ============================================================
	// ألوان الواجهة
	// ============================================================
	private static final int COLOR_BACKGROUND = 0xFF0D1117;
	private static final int COLOR_SURFACE = 0xFF161B22;
	private static final int COLOR_SURFACE_ALT = 0xFF21262D;
	private static final int COLOR_PRIMARY = 0xFF1F6FEB;
	private static final int COLOR_PRIMARY_LIGHT = 0xFF58A6FF;
	private static final int COLOR_TEXT_PRIMARY = 0xFFE6EDF3;
	private static final int COLOR_TEXT_SECONDARY = 0xFF8B949E;
	private static final int COLOR_BORDER = 0xFF30363D;
	private static final int COLOR_ACCENT = 0xFFD29922;

	// ============================================================
	// ألوان التمييز في المحرر
	// ============================================================
	// ✅ النتائج العادية: أزرق شفاف (مرئي لكن هادئ)
	private static final int HIGHLIGHT_COLOR_NORMAL = 0x5558A6FF;
	// ✅ النتيجة الحالية: برتقالي واضح + بارز
	private static final int HIGHLIGHT_COLOR_CURRENT = 0xFFFFA657;

	// ============================================================
	// الحقول
	// ============================================================
	private EditText etFind, etReplace;
	private Button btnFindPrev, btnFindNext, btnReplace, btnReplaceAll, btnClose;
	private TextView tvResults;
	private EditText editor;
	private Context context;

	private final List<Integer> matchPositions = new ArrayList<>();
	private int currentMatchIndex = -1;
	private String lastFindText = "";

	// ✅ تتبع الـ spans لإزالتها لاحقاً
	private final List<BackgroundColorSpan> appliedSpans = new ArrayList<>();

	// ✅ Debounce للبحث
	private final Handler debounceHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingSearch;

	// ============================================================
	// البناء
	// ============================================================
	public FindReplaceBar(Context context) {
		super(context);
		this.context = context;
		init();
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	private void init() {
		setOrientation(VERTICAL);
		setBackground(UiStyle.rounded(context, COLOR_SURFACE, 12, COLOR_BORDER, 1));
		setPadding(dp(12), dp(10), dp(12), dp(10));

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			setElevation(dp(8));
		}

		// ============================================================
		// صف البحث: [بحث] [السابق ▲] [التالي ▼] [العدّاد] [إغلاق ✖]
		// ============================================================
		LinearLayout findRow = new LinearLayout(context);
		findRow.setOrientation(HORIZONTAL);
		findRow.setGravity(Gravity.CENTER_VERTICAL);

		etFind = createStyledEditText("ابحث في الملف…");
		etFind.setLayoutParams(new LayoutParams(0, dp(40), 1));
		etFind.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		findRow.addView(etFind);

		btnFindPrev = createStyledButton("▲", false);
		btnFindPrev.setTextSize(14);
		btnFindPrev.setLayoutParams(new LayoutParams(dp(42), dp(40)));
		((LayoutParams) btnFindPrev.getLayoutParams()).leftMargin = dp(4);
		findRow.addView(btnFindPrev);

		btnFindNext = createStyledButton("▼", false);
		btnFindNext.setTextSize(14);
		btnFindNext.setLayoutParams(new LayoutParams(dp(42), dp(40)));
		((LayoutParams) btnFindNext.getLayoutParams()).leftMargin = dp(4);
		findRow.addView(btnFindNext);

		addView(findRow);

		// ============================================================
		// صف الاستبدال: [استبدال بـ...] [استبدال] [الكل]
		// ============================================================
		LinearLayout replaceRow = new LinearLayout(context);
		replaceRow.setOrientation(HORIZONTAL);
		replaceRow.setGravity(Gravity.CENTER_VERTICAL);
		replaceRow.setPadding(0, dp(8), 0, 0);

		etReplace = createStyledEditText("استبدال بـ…");
		etReplace.setLayoutParams(new LayoutParams(0, dp(40), 1));
		replaceRow.addView(etReplace);

		btnReplace = createStyledButton("استبدال", false);
		btnReplace.setTextSize(12);
		btnReplace.setLayoutParams(new LayoutParams(dp(74), dp(40)));
		((LayoutParams) btnReplace.getLayoutParams()).leftMargin = dp(4);
		replaceRow.addView(btnReplace);

		btnReplaceAll = createStyledButton("الكل", false);
		btnReplaceAll.setTextSize(12);
		btnReplaceAll.setLayoutParams(new LayoutParams(dp(54), dp(40)));
		((LayoutParams) btnReplaceAll.getLayoutParams()).leftMargin = dp(4);
		replaceRow.addView(btnReplaceAll);

		addView(replaceRow);

		// ============================================================
		// صف النتائج: [▶ 3 / 12]                    [إغلاق ✖]
		// ============================================================
		LinearLayout bottomRow = new LinearLayout(context);
		bottomRow.setOrientation(HORIZONTAL);
		bottomRow.setGravity(Gravity.CENTER_VERTICAL);
		bottomRow.setPadding(0, dp(8), 0, 0);

		tvResults = new TextView(context);
		tvResults.setText("اكتب كلمة للبحث…");
		tvResults.setTextColor(COLOR_TEXT_SECONDARY);
		tvResults.setTextSize(14);
		tvResults.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
		tvResults.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
		bottomRow.addView(tvResults);

		btnClose = createStyledButton("إغلاق ✖", false);
		btnClose.setTextSize(12);
		btnClose.setTextColor(COLOR_TEXT_PRIMARY);
		btnClose.setLayoutParams(new LayoutParams(dp(88), dp(36)));
		bottomRow.addView(btnClose);

		addView(bottomRow);

		setupListeners();
	}

	private EditText createStyledEditText(String hint) {
		EditText et = new EditText(context);
		et.setHint(hint);
		et.setHintTextColor(COLOR_TEXT_SECONDARY);
		et.setTextColor(COLOR_TEXT_PRIMARY);
		et.setTextSize(13);
		et.setPadding(dp(12), 0, dp(12), 0);
		et.setSingleLine(true);

		GradientDrawable gd = new GradientDrawable();
		gd.setColor(COLOR_BACKGROUND);
		gd.setCornerRadius(dp(10));
		gd.setStroke(dp(1), COLOR_BORDER);
		et.setBackground(gd);

		return et;
	}

	private Button createStyledButton(String text, boolean isPrimary) {
		Button btn = new Button(context);
		btn.setText(text);
		btn.setTextColor(Color.WHITE);
		btn.setAllCaps(false);
		btn.setPadding(0, 0, 0, 0);

		GradientDrawable gd = new GradientDrawable();
		gd.setColor(isPrimary ? COLOR_PRIMARY : COLOR_SURFACE_ALT);
		gd.setCornerRadius(dp(10));
		if (!isPrimary) {
			gd.setStroke(dp(1), COLOR_BORDER);
		}
		btn.setBackground(gd);

		return btn;
	}

	public void setEditor(EditText editorRef) {
		this.editor = editorRef;
	}

	// ============================================================
	// المستمعون
	// ============================================================
	private void setupListeners() {
		// ✅ بحث تلقائي مع debounce — لا يعمل إلا إذا كان المؤشر في etFind
		etFind.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				final String findText = s.toString();
				if (findText.equals(lastFindText)) return;
				lastFindText = findText;

				if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);

				pendingSearch = new Runnable() {
					@Override
					public void run() {
						if (etFind != null && etFind.hasFocus()) {
							findAllMatchesCaseInsensitive(false);
						}
					}
				};
				debounceHandler.postDelayed(pendingSearch, 200);
			}

			@Override
			public void afterTextChanged(Editable s) {}
		});

		btnFindPrev.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { goToPreviousMatch(); }
		});

		btnFindNext.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { goToNextMatch(); }
		});

		btnReplace.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { replaceCurrent(); }
		});

		btnReplaceAll.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { replaceAll(); }
		});

		btnClose.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { hide(); }
		});

		etFind.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH
						|| actionId == EditorInfo.IME_ACTION_NEXT
						|| actionId == EditorInfo.IME_ACTION_DONE) {
					if (pendingSearch != null) {
						debounceHandler.removeCallbacks(pendingSearch);
						pendingSearch = null;
					}
					findAllMatchesCaseInsensitive(true);
					goToNextMatch();
					return true;
				}
				return false;
			}
		});
	}

	// ============================================================
	// البحث
	// ============================================================

	private void findAllMatchesCaseInsensitive(boolean force) {
		matchPositions.clear();
		currentMatchIndex = -1;

		String findText = etFind.getText().toString();
		if (editor == null || findText.isEmpty()) {
			clearHighlights();
			tvResults.setText("اكتب كلمة للبحث…");
			tvResults.setTextColor(COLOR_TEXT_SECONDARY);
			return;
		}

		CharSequence contentSeq = editor.getText();
		if (contentSeq == null) {
			clearHighlights();
			tvResults.setText("اكتب كلمة للبحث…");
			tvResults.setTextColor(COLOR_TEXT_SECONDARY);
			return;
		}
		String content = contentSeq.toString();
		String contentLower = content.toLowerCase(Locale.getDefault());
		String findTextLower = findText.toLowerCase(Locale.getDefault());

		int index = 0;
		while (index >= 0) {
			index = contentLower.indexOf(findTextLower, index);
			if (index >= 0) {
				matchPositions.add(index);
				index += findText.length();
			}
		}

		if (matchPositions.isEmpty()) {
			tvResults.setText("لا نتائج");
			tvResults.setTextColor(COLOR_ACCENT);
			clearHighlights();
		} else {
			currentMatchIndex = 0;
			applyHighlights();
			updateResultLabel();
			// ✅ لا نلمس التركيز — نُعيده إلى etFind
			restoreFindFocus();
		}
	}

	/**
	 * ✅ طبّق الـ spans على النص الحالي دون استبداله
	 * لا نستدعي editor.setText() أبداً — هذا يمنع فقدان المؤشر
	 */
	private void applyHighlights() {
		if (editor == null) return;
		CharSequence seq = editor.getText();
		if (!(seq instanceof Spannable)) return;

		Spannable spannable = (Spannable) seq;

		// إزالة التلوين القديم
		removeAppliedSpans(spannable);

		String findText = etFind.getText().toString();
		if (findText.isEmpty()) return;
		int findLen = findText.length();
		int contentLen = spannable.length();

		for (int i = 0; i < matchPositions.size(); i++) {
			int start = matchPositions.get(i);
			int end = start + findLen;
			if (start < 0 || end > contentLen) continue;

			// ✅ النتيجة الحالية: برتقالي بارز
			//    باقي النتائج: أزرق شفاف
			int color = (i == currentMatchIndex)
					? HIGHLIGHT_COLOR_CURRENT
					: HIGHLIGHT_COLOR_NORMAL;

			BackgroundColorSpan span = new BackgroundColorSpan(color);
			spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			appliedSpans.add(span);
		}

		// ✅ لا نلمس التركيز — لا setText، لا setSelection
	}

	private void removeAppliedSpans(Spannable spannable) {
		for (BackgroundColorSpan span : appliedSpans) {
			try {
				spannable.removeSpan(span);
			} catch (Exception ignored) {}
		}
		appliedSpans.clear();
	}

	private void clearHighlights() {
		if (editor == null) return;
		CharSequence seq = editor.getText();
		if (!(seq instanceof Spannable)) return;
		Spannable spannable = (Spannable) seq;

		// ✅ نزيل كل BackgroundColorSpan من نوعنا
		BackgroundColorSpan[] spans = spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
		for (BackgroundColorSpan span : spans) {
			spannable.removeSpan(span);
		}
		appliedSpans.clear();
	}

	// ============================================================
	// التنقل
	// ============================================================

	private void goToNextMatch() {
		if (matchPositions.isEmpty()) {
			findAllMatchesCaseInsensitive(true);
			if (matchPositions.isEmpty()) return;
		}

		currentMatchIndex++;
		if (currentMatchIndex >= matchPositions.size()) {
			currentMatchIndex = 0;
		}

		applyHighlights();
		scrollToCurrentMatch();
		updateResultLabel();
		restoreFindFocus();
	}

	private void goToPreviousMatch() {
		if (matchPositions.isEmpty()) {
			findAllMatchesCaseInsensitive(true);
			if (matchPositions.isEmpty()) return;
		}

		currentMatchIndex--;
		if (currentMatchIndex < 0) {
			currentMatchIndex = matchPositions.size() - 1;
		}

		applyHighlights();
		scrollToCurrentMatch();
		updateResultLabel();
		restoreFindFocus();
	}

	/**
	 * ✅ تحديث العدّاد بشكل بارز وواضح
	 */
	private void updateResultLabel() {
		if (matchPositions.isEmpty()) {
			tvResults.setText("لا نتائج");
			tvResults.setTextColor(COLOR_ACCENT);
		} else {
			tvResults.setText("▶ " + (currentMatchIndex + 1) + " / " + matchPositions.size());
			// ✅ لون مختلف إذا كانت النتيجة الأولى/الأخيرة
			tvResults.setTextColor(COLOR_PRIMARY_LIGHT);
		}
	}

	/**
	 * ✅ تمرير المحرر إلى المطابقة الحالية
	 * لا نستدعي editor.requestFocus() — يبقى التركيز في etFind
	 */
	private void scrollToCurrentMatch() {
		if (editor == null || matchPositions.isEmpty()
				|| currentMatchIndex < 0 || currentMatchIndex >= matchPositions.size()) return;

		String findText = etFind.getText().toString();
		if (findText.isEmpty()) return;

		final int start = matchPositions.get(currentMatchIndex);
		final int end = start + findText.length();

		// ✅ نُحدّد الـ selection في المحرر (بدون requestFocus)
		try {
			editor.setSelection(start, end);
		} catch (Exception ignored) {}

		// ✅ التمرير بعد تأخير بسيط لضمان اكتمال الـ layout
		editor.postDelayed(new Runnable() {
			@Override
			public void run() {
				try {
					android.text.Layout layout = editor.getLayout();
					if (layout == null) return;
					int line = layout.getLineForOffset(start);
					int y = layout.getLineTop(line);
					// نضع النتيجة في الثلث العلوي من الشاشة
					int viewport = Math.max(1, editor.getHeight());
					int targetY = Math.max(0, y - viewport / 3);
					editor.scrollTo(0, targetY);
				} catch (Exception ignored) {}
			}
		}, 30);
	}

	/**
	 * ✅ إعادة التركيز إلى مربع البحث — يمنع القفز إلى المحرر
	 */
	private void restoreFindFocus() {
		if (etFind == null) return;
		etFind.post(new Runnable() {
			@Override
			public void run() {
				if (etFind != null && !etFind.hasFocus()) {
					etFind.requestFocus();
				}
				try {
					int len = etFind.getText().length();
					etFind.setSelection(len);
				} catch (Exception ignored) {}
			}
		});
	}

	// ============================================================
	// الاستبدال
	// ============================================================

	private void replaceCurrent() {
		if (editor == null || matchPositions.isEmpty()
				|| currentMatchIndex < 0 || currentMatchIndex >= matchPositions.size()) return;

		String findText = etFind.getText().toString();
		String replaceText = etReplace.getText().toString();
		if (findText.isEmpty()) return;

		int start = matchPositions.get(currentMatchIndex);
		int end = start + findText.length();

		CharSequence contentSeq = editor.getText();
		if (contentSeq == null || end > contentSeq.length()) return;

		String currentContent = contentSeq.toString();
		String foundText = currentContent.substring(start, end);
		if (!foundText.equalsIgnoreCase(findText)) return;

		Editable editable = editor.getText();
		if (editable == null) return;

		if (editable instanceof Spannable) {
			removeAppliedSpans((Spannable) editable);
		}

		editable.replace(start, end, replaceText);

		// إعادة البحث بعد الاستبدال
		findAllMatchesCaseInsensitive(true);

		if (!matchPositions.isEmpty()) {
			if (currentMatchIndex >= matchPositions.size()) {
				currentMatchIndex = 0;
			}
			applyHighlights();
			scrollToCurrentMatch();
			updateResultLabel();
		}
		restoreFindFocus();
	}

	/**
	 * ✅ استبدال الكل — يدوياً بدون regex
	 */
	private void replaceAll() {
		if (editor == null) return;

		String findText = etFind.getText().toString();
		String replaceText = etReplace.getText().toString();
		if (findText.isEmpty()) return;

		CharSequence contentSeq = editor.getText();
		if (contentSeq == null) return;
		String content = contentSeq.toString();
		String contentLower = content.toLowerCase(Locale.getDefault());
		String findTextLower = findText.toLowerCase(Locale.getDefault());

		int count = 0;
		int index = 0;
		while (index >= 0) {
			index = contentLower.indexOf(findTextLower, index);
			if (index >= 0) {
				count++;
				index += findText.length();
			}
		}

		if (count == 0) {
			tvResults.setText("لا نتائج");
			tvResults.setTextColor(COLOR_ACCENT);
			return;
		}

		Editable editable = editor.getText();
		if (editable instanceof Spannable) {
			removeAppliedSpans((Spannable) editable);
		}
		if (editable == null) return;

		StringBuilder result = new StringBuilder(content.length());
		int cursor = 0;
		index = 0;
		while (index >= 0) {
			index = contentLower.indexOf(findTextLower, index);
			if (index >= 0) {
				result.append(content, cursor, index);
				result.append(replaceText);
				cursor = index + findText.length();
				index = cursor;
			}
		}
		result.append(content, cursor, content.length());

		editor.setText(result.toString());
		try {
			editor.setSelection(editor.getText().length());
		} catch (Exception ignored) {}

		tvResults.setText("تم استبدال " + count);
		tvResults.setTextColor(COLOR_PRIMARY_LIGHT);
		matchPositions.clear();
		currentMatchIndex = -1;
		appliedSpans.clear();

		restoreFindFocus();
	}

	// ============================================================
	// الإظهار والإخفاء
	// ============================================================

	public void show() {
		setVisibility(VISIBLE);
		etFind.requestFocus();
		etFind.setText("");
		etFind.setHint("ابحث في الملف…");
		lastFindText = "";
		matchPositions.clear();
		currentMatchIndex = -1;
		clearHighlights();
		tvResults.setText("اكتب كلمة للبحث…");
		tvResults.setTextColor(COLOR_TEXT_SECONDARY);
	}

	public void hide() {
		if (pendingSearch != null) {
			debounceHandler.removeCallbacks(pendingSearch);
			pendingSearch = null;
		}
		clearHighlights();
		setVisibility(GONE);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (pendingSearch != null) {
			debounceHandler.removeCallbacks(pendingSearch);
			pendingSearch = null;
		}
		clearHighlights();
	}
}