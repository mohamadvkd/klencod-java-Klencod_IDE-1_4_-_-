package com.flutteride.codweva;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorView extends ScrollView {

	private EditText editor;
	private TextView lineNumbers;
	private LinearLayout container;
	private Context context;
	private SharedPreferences prefs;
	private boolean pinchZoomEnabled = false;
	private boolean pinchTracking = false;
	private float pinchStartDistance = 0f;
	private float pinchStartFontSize = 15f;
	private float pinchFontSize = 15f;
	private String currentText = "";
	private String currentLanguage = "dart";
	private boolean isHighlighting = false;
	private boolean isUpdatingLineNumbers = false;
	private TextWatcher textWatcher;
	private final Set<Integer> errorLines = new HashSet<>();
	private final Set<Integer> externalErrorLines = new HashSet<>();
	private int lastScrollY = 0;

	private static final String UNDO_KEY_DEFAULT = "__default__";
	private String currentFileKey = UNDO_KEY_DEFAULT;
	private final Map<String, ArrayList<String>> undoStacksByFile = new HashMap<>();
	private final Map<String, ArrayList<String>> redoStacksByFile = new HashMap<>();
	private boolean isUndoRedo = false;
	private final int maxStackSize = 100;

	private PopupWindow completionPopup;
	private ListView completionList;
	private CompletionAdapter completionAdapter;
	private List<DartCompletions.Item> currentItems = new ArrayList<>();
	private int currentSuggestionStart = -1;
	private int currentSuggestionEnd = -1;
	private boolean isShowingPopup = false;
	private boolean suppressAutoComplete = false;
	private DartCompletions completions;

	private final Handler completionHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingCompletion = null;
	private static final long COMPLETION_DELAY_MS = 120L;

	// ✅ منع الإظهار بعد الإدراج لمدة قصيرة
	private long suppressUntilMs = 0L;
	private static final long SUPPRESS_AFTER_INSERT_MS = 300L;

	// ✅ تخطي التغييرات البرمجية
	private String lastInsertedText = null;

	private static final Set<String> DART_KEYWORDS = new HashSet<>(Arrays.asList(
	"abstract", "as", "assert", "async", "await", "break", "case", "catch",
	"class", "const", "continue", "covariant", "default", "deferred", "do",
	"dynamic", "else", "enum", "export", "extends", "extension", "external",
	"factory", "false", "final", "finally", "for", "Function", "get", "hide",
	"if", "implements", "import", "in", "interface", "is", "late", "library",
	"mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
	"return", "set", "show", "static", "super", "switch", "sync", "this",
	"throw", "true", "try", "typedef", "var", "void", "while", "with", "yield"
	));

	private static final Set<String> FLUTTER_FUNCTIONS = new HashSet<>(Arrays.asList(
	"build", "initState", "dispose", "setState", "didChangeDependencies",
	"didUpdateWidget", "reassemble", "deactivate", "activate", "createState"
	));

	private static final Set<String> FLUTTER_WIDGETS = new HashSet<>(Arrays.asList(
	"Container", "Column", "Row", "Stack", "ListView", "GridView", "Scaffold",
	"AppBar", "Text", "Button", "Icon", "Image", "TextField", "MaterialApp",
	"Center", "Padding", "Margin", "Expanded", "Flexible", "Positioned", "Align",
	"SizedBox", "SingleChildScrollView", "SafeArea", "GestureDetector", "InkWell"
	));

	private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
	"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
	));

	private static final Set<String> KOTLIN_KEYWORDS = new HashSet<>(Arrays.asList(
	"as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while", "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import", "init", "param", "property", "receiver", "set", "setparam", "where", "actual", "abstract", "annotation", "companion", "const", "crossinline", "data", "enum", "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit", "noinline", "open", "operator", "out", "override", "private", "protected", "public", "reified", "sealed", "suspend", "tailrec", "vararg"
	));

	private static final Set<String> PYTHON_KEYWORDS = new HashSet<>(Arrays.asList(
	"and", "as", "assert", "async", "await", "break", "case", "class", "continue", "def", "del", "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "match", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield"
	));

	private static final Set<String> JS_KEYWORDS = new HashSet<>(Arrays.asList(
	"abstract", "arguments", "await", "boolean", "break", "byte", "case", "catch",
	"char", "class", "const", "continue", "debugger", "default", "delete", "do",
	"double", "else", "enum", "eval", "export", "extends", "false", "final", "finally",
	"float", "for", "function", "goto", "if", "implements", "import", "in", "instanceof",
	"int", "interface", "let", "long", "native", "new", "null", "package", "private",
	"protected", "public", "return", "short", "static", "super", "switch", "synchronized",
	"this", "throw", "throws", "transient", "true", "try", "typeof", "var", "void",
	"volatile", "while", "with", "yield"
	));

	private static final Set<String> SQL_KEYWORDS = new HashSet<>(Arrays.asList(
		"select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "alter", "drop", "table", "database", "join", "inner", "left", "right", "outer", "on", "as", "and", "or", "not", "null", "is", "like", "between", "group", "by", "order", "having", "limit", "distinct", "count", "sum", "avg", "min", "max", "primary", "key", "foreign", "references", "index", "view", "case", "when", "then", "else", "end"
	));

	private static final Set<String> HTML_TAGS = new HashSet<>(Arrays.asList(
	"html", "head", "title", "body", "div", "span", "p", "a", "img", "ul", "ol", "li",
	"table", "tr", "td", "th", "form", "input", "button", "select", "option", "textarea",
	"script", "style", "link", "meta", "nav", "header", "footer", "section", "article",
	"aside", "main", "figure", "figcaption", "h1", "h2", "h3", "h4", "h5", "h6", "br", "hr"
	));

	private static final Set<String> CSS_PROPERTIES = new HashSet<>(Arrays.asList(
		"color", "background", "margin", "padding", "border", "width", "height", "display",
		"position", "top", "left", "right", "bottom", "font", "text", "align", "justify",
		"flex", "grid", "opacity", "z-index", "overflow", "float", "clear", "cursor",
		"transition", "transform", "animation", "box-shadow", "border-radius"
	));

	private static final Set<String> YAML_KEYWORDS = new HashSet<>(Arrays.asList(
		"true", "false", "yes", "no", "on", "off", "null", "~"
	));

	private static final Set<String> XML_KEYWORDS = new HashSet<>(Arrays.asList(
		"xml", "version", "encoding", "stylesheet", "include", "import"
	));

	private static final Set<String> MARKDOWN_KEYWORDS = new HashSet<>(Arrays.asList(
		"#", "##", "###", "####", "#####", "######", "**", "__", "*", "_", "~~", "`", "```",
		"-", "+", "*", "1.", ">", "---", "***", "___", "[", "]", "(", ")", "!", "|"
	));

	private static final int COLOR_KEYWORD = 0xFF58A6FF;
	private static final int COLOR_FUNCTION = 0xFF79C0FF;
	private static final int COLOR_STRING = 0xFF4A9EFF;
	private static final int COLOR_NUMBER = 0xFF6A8CFF;
	private static final int COLOR_OPERATOR = 0xFF1F6FEB;
	private static final int COLOR_TAG = 0xFF58A6FF;
	private static final int COLOR_COMMENT = 0xFF8B949E;
	private static final int COLOR_DEFAULT = 0xFFE6EDF3;
	private static final int COLOR_BACKGROUND = 0xFF0D1117;
	private static final int COLOR_LINE_NUMBER = 0xFF484F58;

	private static final int POPUP_BG = 0xFF161B22;
	private static final int POPUP_BORDER = 0xFF30363D;
	private static final int POPUP_ITEM_BG = 0xFF0D1117;
	private static final int POPUP_TEXT = 0xFFE6EDF3;
	private static final int POPUP_MUTED = 0xFF8B949E;
	private static final int POPUP_KIND_W = 0xFF58A6FF;
	private static final int POPUP_KIND_P = 0xFF7EE787;
	private static final int POPUP_KIND_M = 0xFFD2A8FF;
	private static final int POPUP_KIND_T = 0xFFFFA657;
	private static final int POPUP_KIND_K = 0xFFFF7B72;
	private static final int POPUP_KIND_I = 0xFF79C0FF;
	private static final int POPUP_KIND_E = 0xFFF0883E;
	private static final int POPUP_SNIPPET_BADGE = 0xFF7EE787;

	private static final Pattern PATTERN_STRING = Pattern.compile("\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"|'([^'\\\\]*(\\\\.[^'\\\\]*)*)'");
	private static final Pattern PATTERN_NUMBER = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
	private static final Pattern PATTERN_SINGLE_LINE_COMMENT = Pattern.compile("//.*$|#.*$", Pattern.MULTILINE);
	private static final Pattern PATTERN_MULTI_LINE_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
	private static final Pattern PATTERN_OPERATOR = Pattern.compile("[=<>!+\\-*/%&|^~?:]|==|!=|<=|>=|&&|\\|\\|");
	private static final Pattern PATTERN_HTML_TAG = Pattern.compile("<[/]?[a-zA-Z][a-zA-Z0-9]*[^>]*>");
	private static final Pattern PATTERN_CSS_SELECTOR = Pattern.compile("[.#][a-zA-Z][a-zA-Z0-9_-]*");
	private static final Pattern PATTERN_MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE);
	private static final Pattern PATTERN_FUNCTION_CALL = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\()");
	private static final Pattern PATTERN_ANNOTATION = Pattern.compile("@[A-Za-z_][A-Za-z0-9_.]*");
	private static final Pattern PATTERN_JSON_PROPERTY = Pattern.compile("\\\"[^\\\"]+\\\"(?=\\s*:)");
	private static final Pattern PATTERN_CONFIG_KEY = Pattern.compile("(?m)^\\s*[A-Za-z_][A-Za-z0-9_.-]*(?=\\s*[:=])");
	private static final Pattern PATTERN_WORD = Pattern.compile("\\b\\w+\\b");

	private static final int DEFAULT_FONT_SIZE = 13;
	private static final int MIN_FONT_SIZE = 8;
	private static final int MAX_FONT_SIZE = 32;
	private static final int LINE_NUMBER_MIN_WIDTH = 32;
	private static final int EDITOR_PADDING_HORIZONTAL = 6;
	private static final int EDITOR_PADDING_VERTICAL = 6;
	private static final int LINE_NUMBER_FONT_SIZE = 10;
	private static final int LINE_SPACING_MULT = 1;

	public CodeEditorView(Context context) {
		super(context);
		this.context = context;
		init();
	}

	public CodeEditorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		init();
	}

	public CodeEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		this.context = context;
		init();
	}

	private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

	private void init() {
		prefs = context.getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE);

		new Thread(new Runnable() {
			@Override public void run() {
				try {
					completions = DartCompletions.get(context);
					completionHandler.post(new Runnable() {
						@Override public void run() {
							if (editor != null && editor.hasFocus()) {
								scheduleCheckForAutoComplete();
							}
						}
					});
				} catch (Exception ignored) {}
			}
		}, "dart-completions-loader").start();

		container = new LinearLayout(context);
		container.setOrientation(LinearLayout.HORIZONTAL);
		container.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
		container.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		container.setBackgroundColor(COLOR_BACKGROUND);

		container.setClipToOutline(true);
		container.setOutlineProvider(new ViewOutlineProvider() {
			@Override
			public void getOutline(View view, Outline outline) {
				outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
			}
		});

		lineNumbers = new TextView(context);
		lineNumbers.setBackgroundColor(COLOR_BACKGROUND);
		lineNumbers.setTextColor(COLOR_LINE_NUMBER);
		lineNumbers.setTypeface(Typeface.MONOSPACE);
		lineNumbers.setGravity(Gravity.TOP | Gravity.LEFT);
		lineNumbers.setPadding(dp(6), dp(EDITOR_PADDING_VERTICAL), dp(3), dp(EDITOR_PADDING_VERTICAL));
		lineNumbers.setMinimumWidth(dp(LINE_NUMBER_MIN_WIDTH));
		lineNumbers.setLineSpacing(0, LINE_SPACING_MULT);
		lineNumbers.setIncludeFontPadding(false);
		lineNumbers.setElegantTextHeight(false);
		lineNumbers.setTextSize(LINE_NUMBER_FONT_SIZE);

		editor = new EditText(context);
		editor.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT));
		editor.setBackgroundColor(COLOR_BACKGROUND);
		editor.setTextColor(COLOR_DEFAULT);
		editor.setTypeface(Typeface.MONOSPACE);
		editor.setGravity(Gravity.TOP | Gravity.LEFT);
		editor.setTextDirection(View.TEXT_DIRECTION_LTR);
		editor.setPadding(dp(EDITOR_PADDING_HORIZONTAL), dp(EDITOR_PADDING_VERTICAL),
			dp(EDITOR_PADDING_HORIZONTAL), dp(EDITOR_PADDING_VERTICAL));
		editor.setHorizontallyScrolling(false);
		editor.setVerticalScrollBarEnabled(false);
		editor.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
		editor.setLineSpacing(0, LINE_SPACING_MULT);
		editor.setIncludeFontPadding(false);
		editor.setElegantTextHeight(false);

		editor.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, android.view.MotionEvent event) {
				if (!pinchZoomEnabled) return false;

				int action = event.getActionMasked();
				if (action == android.view.MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
					pinchStartDistance = pinchDistance(event);
					pinchStartFontSize = getFontSize();
					pinchFontSize = pinchStartFontSize;
					pinchTracking = pinchStartDistance > 0f;
					if (pinchTracking) v.getParent().requestDisallowInterceptTouchEvent(true);
					return pinchTracking;
				}

				if (pinchTracking && action == android.view.MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
					v.getParent().requestDisallowInterceptTouchEvent(true);
					float currentDistance = pinchDistance(event);
					if (currentDistance > 0f && pinchStartDistance > 0f) {
						float target = pinchStartFontSize + (currentDistance - pinchStartDistance) * 0.035f;
						target = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, target));
						pinchFontSize += (target - pinchFontSize) * 0.30f;
						setFontSizePreview(pinchFontSize);
					}
					return true;
				}

				if (pinchTracking && (action == android.view.MotionEvent.ACTION_POINTER_UP
						|| action == android.view.MotionEvent.ACTION_UP
						|| action == android.view.MotionEvent.ACTION_CANCEL)) {
					boolean consumed = pinchTracking;
					if (action != android.view.MotionEvent.ACTION_POINTER_UP) {
						setFontSizeInternal(Math.round(pinchFontSize), true);
						pinchTracking = false;
						pinchStartDistance = 0f;
						v.getParent().requestDisallowInterceptTouchEvent(false);
					}
					return consumed;
				}
				return false;
			}
		});

		editor.setOnScrollChangeListener(new View.OnScrollChangeListener() {
			@Override
			public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
				lastScrollY = scrollY;
			}
		});

		container.addView(lineNumbers);
		container.addView(editor);
		addView(container);
		setFillViewport(true);

		initAutoComplete();

		textWatcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String current = s.toString();

				// ✅ إذا كان التغيير نتج عن insertSuggestion، تجاهله
				if (lastInsertedText != null && lastInsertedText.equals(current)) {
					lastInsertedText = null;
					// حدّث الحالة الأساسية فقط
					currentText = current;
					updateLineNumbers();
					updateErrorMarkers();
					if (!isUndoRedo) {
						pushToUndoStack(current);
					}
					return;
				}

				currentText = current;
				updateLineNumbers();
				updateErrorMarkers();
				highlightSyntax();
				if (!isUndoRedo) {
					pushToUndoStack(current);
				}
				scheduleCheckForAutoComplete();
			}

			@Override
			public void afterTextChanged(Editable s) {}
		};
		editor.addTextChangedListener(textWatcher);

		pushToUndoStack(editor.getText().toString());
		applySettings();

		editor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				updateLineNumbers();
				editor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
			}
		});
	}

	private void initAutoComplete() {
		completionList = new ListView(context);
		completionList.setBackgroundColor(POPUP_BG);
		completionList.setDivider(null);
		completionList.setDividerHeight(0);
		completionList.setPadding(dp(2), dp(4), dp(2), dp(4));

		GradientDrawable listDrawable = new GradientDrawable();
		listDrawable.setColor(POPUP_BG);
		listDrawable.setCornerRadius(dp(10));
		completionList.setBackground(listDrawable);

		completionList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < 0 || position >= currentItems.size()) return;
				insertSuggestion(currentItems.get(position));
			}
		});

		completionPopup = new PopupWindow(completionList,
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT, false);
		GradientDrawable popupDrawable = new GradientDrawable();
		popupDrawable.setColor(POPUP_BG);
		popupDrawable.setCornerRadius(dp(10));
		popupDrawable.setStroke(dp(1), POPUP_BORDER);
		completionPopup.setBackgroundDrawable(popupDrawable);
		completionPopup.setOutsideTouchable(true);
		completionPopup.setFocusable(false);
		completionPopup.setClippingEnabled(true);
		completionPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
		completionPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
			@Override
			public void onDismiss() {
				isShowingPopup = false;
			}
		});
	}

	/**
	 * ✅ إخفاء الاقتراحات + إلغاء أي جدولة معلّقة
	 */
	private void hideSuggestions() {
		if (completionHandler != null && pendingCompletion != null) {
			completionHandler.removeCallbacks(pendingCompletion);
			pendingCompletion = null;
		}
		if (completionPopup != null && completionPopup.isShowing()) completionPopup.dismiss();
		isShowingPopup = false;
		currentItems = new ArrayList<>();
		currentSuggestionStart = -1;
		currentSuggestionEnd = -1;
	}

	private void scheduleCheckForAutoComplete() {
		if (completionHandler == null) return;

		// ✅ فحص فترة المنع بعد الإدراج
		if (System.currentTimeMillis() < suppressUntilMs) return;

		if (pendingCompletion != null) {
			completionHandler.removeCallbacks(pendingCompletion);
		}
		pendingCompletion = new Runnable() {
			@Override
			public void run() {
				pendingCompletion = null;
				checkForAutoCompleteNow();
			}
		};
		completionHandler.postDelayed(pendingCompletion, COMPLETION_DELAY_MS);
	}

	private void checkForAutoCompleteNow() {
		if (suppressAutoComplete || isUndoRedo || editor == null) return;
		if (System.currentTimeMillis() < suppressUntilMs) return;

		String text = editor.getText().toString();
		int cursorPos = editor.getSelectionStart();
		if (cursorPos <= 0 || cursorPos > text.length()) {
			hideSuggestions();
			return;
		}

		int start = cursorPos - 1;
		while (start > 0) {
			char c = text.charAt(start - 1);
			if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$') {
				start--;
			} else break;
		}
		String prefix = text.substring(start, cursorPos);
		if (prefix.length() < 1) {
			hideSuggestions();
			return;
		}

		if (completions == null) {
			try { completions = DartCompletions.get(context); } catch (Exception ignored) {}
		}

		CompletionContext.AnalysisResult ctx = null;
		try {
			ctx = CompletionContext.analyze(text, cursorPos);
		} catch (Exception ignored) {}

		List<DartCompletions.Item> items = null;

		if (ctx != null && ctx.insideImport) {
			if (completions != null && completions.isLoaded()) {
				items = new ArrayList<>();
				List<DartCompletions.Item> all = completions.byPrefix(prefix, 30);
				for (DartCompletions.Item item : all) {
					if (item.kind == DartCompletions.ItemKind.IMPORT) {
						items.add(item);
					}
				}
			}
			if (items == null || items.isEmpty()) {
				items = itemsFromFallback(prefix);
			}
		} else if (ctx != null && ctx.useFilteredMode && !ctx.customSuggestions.isEmpty()) {
			items = itemsFromStrings(ctx.customSuggestions, prefix);
		}

		if (items == null && completions != null && completions.isLoaded()) {
			String widgetCtx = (ctx != null) ? ctx.currentWidget : null;
			items = completions.byPrefixWithVariants(prefix, widgetCtx, 20);
		}

		if (items == null || items.isEmpty()) {
			items = itemsFromFallback(prefix);
		}

		if (items != null && !items.isEmpty()) {
			showSuggestions(items, start, cursorPos);
		} else {
			hideSuggestions();
		}
	}

	private List<DartCompletions.Item> itemsFromStrings(List<String> strings, String prefix) {
		List<DartCompletions.Item> items = new ArrayList<>();
		if (strings == null) return items;
		String lowerPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.US);

		for (String s : strings) {
			if (s == null) continue;
			String nameForMatch = s;
			if (nameForMatch.startsWith("MainAxisAlignment.")
					|| nameForMatch.startsWith("CrossAxisAlignment.")
					|| nameForMatch.startsWith("TextAlign.")
					|| nameForMatch.startsWith("FontWeight.")
					|| nameForMatch.startsWith("BoxFit.")
					|| nameForMatch.startsWith("Alignment.")
					|| nameForMatch.startsWith("TextOverflow.")
					|| nameForMatch.startsWith("MainAxisSize.")) {
				int dot = nameForMatch.lastIndexOf('.');
				if (dot >= 0) nameForMatch = nameForMatch.substring(dot + 1);
			}
			if (!nameForMatch.toLowerCase(Locale.US).startsWith(lowerPrefix)) continue;

			DartCompletions.ItemKind kind = DartCompletions.ItemKind.WIDGET;
			if (s.contains(".") && (s.startsWith("MainAxis")
					|| s.startsWith("CrossAxis")
					|| s.startsWith("TextAlign.")
					|| s.startsWith("FontWeight.")
					|| s.startsWith("BoxFit.")
					|| s.startsWith("Alignment.")
					|| s.startsWith("TextOverflow."))) {
				kind = DartCompletions.ItemKind.ENUM_VALUE;
			}

			items.add(new DartCompletions.Item(
					s, "", "", kind, "context",
					"", "", "", 10,
					null, null));
		}

		Collections.sort(items, new Comparator<DartCompletions.Item>() {
			@Override
			public int compare(DartCompletions.Item a, DartCompletions.Item b) {
				return Integer.compare(a.name.length(), b.name.length());
			}
		});
		return items;
	}

	private List<DartCompletions.Item> itemsFromFallback(String prefix) {
		List<DartCompletions.Item> items = new ArrayList<>();
		String lower = prefix.toLowerCase(Locale.US);
		LinkedHashSet<String> names = new LinkedHashSet<>();

		for (String k : DART_KEYWORDS) {
			if (k.toLowerCase(Locale.US).startsWith(lower)) names.add(k);
		}
		if ("dart".equals(currentLanguage)) {
			for (String k : FLUTTER_FUNCTIONS) {
				if (k.toLowerCase(Locale.US).startsWith(lower)) names.add(k);
			}
			for (String k : FLUTTER_WIDGETS) {
				if (k.toLowerCase(Locale.US).startsWith(lower)) names.add(k);
			}
		}
		extractLocalWords(prefix, names);

		for (String name : names) {
			items.add(new DartCompletions.Item(
					name, "", "",
					DartCompletions.ItemKind.WIDGET,
					"local", "", "", "",
					5, null, null));
			if (items.size() >= 12) break;
		}
		return items;
	}

	private void extractLocalWords(String prefix, Set<String> out) {
		String text = editor.getText().toString();
		String lower = prefix.toLowerCase(Locale.US);

		Matcher m1 = Pattern.compile("\\b([a-z][a-zA-Z0-9_]*)\\s*=").matcher(text);
		while (m1.find()) {
			String name = m1.group(1);
			if (name.toLowerCase(Locale.US).startsWith(lower)) out.add(name);
		}
		Matcher m2 = Pattern.compile("\\b([a-z][a-zA-Z0-9_]*)\\s*\\(").matcher(text);
		while (m2.find()) {
			String name = m2.group(1);
			if (name.toLowerCase(Locale.US).startsWith(lower)) out.add(name);
		}
		Matcher m3 = Pattern.compile("\\bclass\\s+([A-Z][a-zA-Z0-9_]*)").matcher(text);
		while (m3.find()) {
			String name = m3.group(1);
			if (name.toLowerCase(Locale.US).startsWith(lower)) out.add(name);
		}
	}

	private void showSuggestions(List<DartCompletions.Item> items, int start, int end) {
		if (items == null || items.isEmpty()) {
			hideSuggestions();
			return;
		}

		if (items.size() > 15) items = items.subList(0, 15);

		currentItems = items;
		currentSuggestionStart = start;
		currentSuggestionEnd = end;

		completionAdapter = new CompletionAdapter(context, items);
		completionList.setAdapter(completionAdapter);

		int[] location = new int[2];
		editor.getLocationInWindow(location);
		android.text.Layout layout = editor.getLayout();
		int selection = Math.max(0, Math.min(editor.getSelectionStart(), editor.length()));
		int line = layout == null ? 0 : layout.getLineForOffset(selection);
		int cursorX = location[0] + editor.getPaddingLeft() + dp(8);
		int cursorY = location[1] + editor.getPaddingTop() + dp(4);
		if (layout != null) {
			cursorX += Math.round(layout.getPrimaryHorizontal(selection));
			cursorY = location[1] + layout.getLineBottom(line) - editor.getScrollY() + dp(4);
		}

		int popupWidth = dp(320);
		Rect visibleFrame = new Rect();
		editor.getWindowVisibleDisplayFrame(visibleFrame);
		int availableWidth = visibleFrame.width() - dp(16);
		popupWidth = Math.min(popupWidth, availableWidth);

		int itemHeight = dp(52);
		int popupHeight = Math.min(items.size() * itemHeight + dp(10),
				Math.max(dp(150), visibleFrame.height() / 2));

		int minX = visibleFrame.left + dp(6);
		int maxX = Math.max(minX, visibleFrame.right - popupWidth - dp(6));
		cursorX = Math.max(minX, Math.min(cursorX, maxX));

		int minY = visibleFrame.top + dp(6);
		int maxY = Math.max(minY, visibleFrame.bottom - popupHeight - dp(6));
		if (cursorY > maxY) {
			int above = (layout == null) ? maxY
					: location[1] + layout.getLineTop(line) - editor.getScrollY() - popupHeight - dp(6);
			cursorY = (above >= minY) ? above : maxY;
		}
		cursorY = Math.max(minY, Math.min(cursorY, maxY));

		try {
			if (completionPopup.isShowing()) {
				completionPopup.update(cursorX, cursorY, popupWidth, popupHeight);
					completionAdapter.notifyDataSetChanged();
			} else {
				completionPopup.setWidth(popupWidth);
				completionPopup.setHeight(popupHeight);
				completionPopup.showAtLocation(editor, Gravity.TOP | Gravity.LEFT, cursorX, cursorY);
				isShowingPopup = true;
			}
		} catch (Exception e) {
			hideSuggestions();
		}
	}

	private void insertSuggestion(DartCompletions.Item item) {
		if (item == null) return;
		if (currentSuggestionStart < 0 || currentSuggestionEnd < 0) {
			hideSuggestions();
			return;
		}

		// ✅ إلغاء أي جدولة معلّقة قبل الإدراج
		if (pendingCompletion != null) {
			completionHandler.removeCallbacks(pendingCompletion);
			pendingCompletion = null;
		}

		String text = editor.getText().toString();
		int start = Math.max(0, Math.min(currentSuggestionStart, text.length()));
		int end = Math.max(start, Math.min(currentSuggestionEnd, text.length()));

		String insertName = item.isSnippetVariant ? extractBaseName(item.name) : item.name;
		boolean useSnippet = item.isSnippetVariant && item.hasSnippet();

		String resultingText;

		if (useSnippet) {
			DartSnippets.InsertionResult result = DartSnippets.insert(text, start, end, item.snippet);
			resultingText = result.newText;
			try {
				suppressAutoComplete = true;
				isUndoRedo = true;
				lastInsertedText = resultingText;  // ✅ تخطي onTextChanged
				editor.setText(resultingText);
				editor.setSelection(Math.max(0,
						Math.min(result.newCursorPosition, editor.getText().length())));
				currentText = resultingText;
				highlightSyntax();
			} finally {
				suppressAutoComplete = false;
				isUndoRedo = false;
			}
			pushToUndoStack(resultingText);
		} else {
			resultingText = text.substring(0, start) + insertName + text.substring(end);
			int newCursor = start + insertName.length();

			try {
				suppressAutoComplete = true;
				isUndoRedo = true;
				lastInsertedText = resultingText;  // ✅ تخطي onTextChanged
				editor.setText(resultingText);
				editor.setSelection(newCursor);
				currentText = resultingText;
				highlightSyntax();
			} finally {
				suppressAutoComplete = false;
				isUndoRedo = false;
			}
			pushToUndoStack(resultingText);
		}

		// ✅ إخفاء Popup + إلغاء أي جدولة
		hideSuggestions();

		// ✅ منع إعادة الإظهار التلقائي لفترة قصيرة
		suppressUntilMs = System.currentTimeMillis() + SUPPRESS_AFTER_INSERT_MS;
	}

	private String extractBaseName(String name) {
		if (name == null) return "";
		int idx = name.indexOf('(');
		return idx > 0 ? name.substring(0, idx).trim() : name;
	}

	// ============================================================
	// CompletionAdapter
	// ============================================================
	private static final class CompletionAdapter extends BaseAdapter {
		private final Context ctx;
		private final List<DartCompletions.Item> items;

		CompletionAdapter(Context ctx, List<DartCompletions.Item> items) {
			this.ctx = ctx;
			this.items = items;
		}

		@Override public int getCount() { return items.size(); }
		@Override public Object getItem(int position) { return items.get(position); }
		@Override public long getItemId(int position) { return position; }

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LinearLayout row;
			if (convertView instanceof LinearLayout) {
				row = (LinearLayout) convertView;
			} else {
				row = buildRow();
			}

			DartCompletions.Item item = items.get(position);

			TextView kindView = (TextView) row.getChildAt(0);
			kindView.setText(item.kind.symbol);
			kindView.setBackground(buildKindBg(colorForKind(item.kind)));

			LinearLayout textCol = (LinearLayout) row.getChildAt(1);
			TextView nameView = (TextView) textCol.getChildAt(0);
			TextView descView = (TextView) textCol.getChildAt(1);

			nameView.setText(item.name);

			String desc = item.description;
			if (desc == null || desc.isEmpty()) {
				if (!item.className.isEmpty()) desc = item.className;
				else if (!item.category.isEmpty()) desc = item.category;
			}

			if (item.isSnippetVariant) {
				descView.setText("SNIPPET — " + (desc == null ? "" : desc));
				descView.setTextColor(POPUP_SNIPPET_BADGE);
			} else {
				descView.setText(desc == null ? "" : desc);
				descView.setTextColor(POPUP_MUTED);
			}

			return row;
		}

		private LinearLayout buildRow() {
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(dp(10), dp(6), dp(10), dp(6));
			row.setMinimumHeight(dp(48));
			row.setBackgroundColor(POPUP_ITEM_BG);

			TextView kind = new TextView(ctx);
			kind.setTextSize(11);
			kind.setTypeface(Typeface.DEFAULT_BOLD);
			kind.setGravity(Gravity.CENTER);
			kind.setTextColor(0xFFFFFFFF);
			int size = dp(28);
			LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(size, size);
			kp.setMarginEnd(dp(10));
			row.addView(kind, kp);

			LinearLayout textCol = new LinearLayout(ctx);
			textCol.setOrientation(LinearLayout.VERTICAL);
			row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

			TextView name = new TextView(ctx);
			name.setTextSize(13);
			name.setTextColor(POPUP_TEXT);
			name.setTypeface(Typeface.MONOSPACE);
			name.setSingleLine(true);
			name.setEllipsize(android.text.TextUtils.TruncateAt.END);
			textCol.addView(name);

			TextView desc = new TextView(ctx);
			desc.setTextSize(10);
			desc.setTextColor(POPUP_MUTED);
			desc.setSingleLine(true);
			desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
			LinearLayout.LayoutParams dParams = new LinearLayout.LayoutParams(-1, -2);
			dParams.topMargin = dp(2);
			textCol.addView(desc, dParams);

			return row;
		}

		private GradientDrawable buildKindBg(int color) {
			GradientDrawable gd = new GradientDrawable();
			gd.setShape(GradientDrawable.RECTANGLE);
			gd.setColor(color);
			gd.setCornerRadius(dp(6));
			return gd;
		}

		private int colorForKind(DartCompletions.ItemKind kind) {
			switch (kind) {
				case WIDGET: return POPUP_KIND_W;
				case PROPERTY: return POPUP_KIND_P;
				case METHOD: return POPUP_KIND_M;
				case TYPE: return POPUP_KIND_T;
				case KEYWORD: return POPUP_KIND_K;
				case IMPORT: return POPUP_KIND_I;
				case ENUM_VALUE: return POPUP_KIND_E;
				default: return POPUP_KIND_W;
			}
		}

		private int dp(int value) {
			return Math.round(value * ctx.getResources().getDisplayMetrics().density);
		}
	}

	public void setLanguage(String filePath) {
		hideSuggestions();
		if (filePath == null) {
			currentLanguage = "dart";
			return;
		}

		String ext = "";
		int lastDot = filePath.lastIndexOf('.');
		if (lastDot > 0) ext = filePath.substring(lastDot + 1).toLowerCase();

		switch (ext) {
			case "dart": currentLanguage = "dart"; break;
			case "java": currentLanguage = "java"; break;
			case "kt":
			case "kts": currentLanguage = "kotlin"; break;
			case "py":
			case "pyw": currentLanguage = "python"; break;
			case "sql": currentLanguage = "sql"; break;
			case "sh":
			case "bash": currentLanguage = "shell"; break;
			case "toml":
			case "properties": currentLanguage = "config"; break;
			case "js": currentLanguage = "javascript"; break;
			case "json": currentLanguage = "json"; break;
			case "html":
			case "htm": currentLanguage = "html"; break;
			case "css": currentLanguage = "css"; break;
			case "yaml":
			case "yml": currentLanguage = "yaml"; break;
			case "xml": currentLanguage = "xml"; break;
			case "md": currentLanguage = "markdown"; break;
			case "txt": currentLanguage = "text"; break;
			default: currentLanguage = "text"; break;
		}

		refreshHighlighting();
	}

	private Set<String> getCurrentKeywords() {
		switch (currentLanguage) {
			case "json": return Collections.emptySet();
			case "java": return JAVA_KEYWORDS;
			case "kotlin": return KOTLIN_KEYWORDS;
			case "python": return PYTHON_KEYWORDS;
			case "sql": return SQL_KEYWORDS;
			case "shell": return Collections.emptySet();
			case "config": return Collections.emptySet();
			case "text": return Collections.emptySet();
			case "javascript": return JS_KEYWORDS;
			case "html": return HTML_TAGS;
			case "css": return CSS_PROPERTIES;
			case "yaml": return YAML_KEYWORDS;
			case "xml": return XML_KEYWORDS;
			case "markdown": return MARKDOWN_KEYWORDS;
			default: return DART_KEYWORDS;
		}
	}

	private void updateLineNumberWidth(int lineCount) {
		if (lineNumbers == null) return;
		String widestNumber = String.valueOf(Math.max(1, lineCount));
		int measured = Math.round(lineNumbers.getPaint().measureText(widestNumber));
		int padding = lineNumbers.getPaddingLeft() + lineNumbers.getPaddingRight();
		int minimum = Math.max(LINE_NUMBER_MIN_WIDTH, measured + padding + 8);
		lineNumbers.setMinimumWidth(dp(minimum));
	}

	private void updateLineNumbers() {
		if (isUpdatingLineNumbers || editor == null || lineNumbers == null) return;
		isUpdatingLineNumbers = true;
		try {
			android.text.Layout layout = editor.getLayout();
			if (layout == null) return;
			String text = editor.getText().toString();
			int lineCount = Math.max(1, layout.getLineCount());
			SpannableStringBuilder numbers = new SpannableStringBuilder();
			int currentLineNumber = 1;

			for (int i = 0; i < lineCount; i++) {
				int start = layout.getLineStart(i);
				boolean logicalLineStart = i == 0 || (start > 0 && start - 1 < text.length() && text.charAt(start - 1) == '\n');
				if (i > 0) numbers.append("\n");
				int markerStart = numbers.length();
				if (logicalLineStart) {
					boolean hasError = hasAnyError(currentLineNumber);
					numbers.append(hasError ? "●" : " ");
					numbers.append(String.valueOf(currentLineNumber));
					if (hasError) {
						try {
							numbers.setSpan(new ForegroundColorSpan(0xFFFF6B6B), markerStart, markerStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						} catch (Exception ignored) {}
					}
					currentLineNumber++;
				} else {
					numbers.append(" ");
				}
			}

			updateLineNumberWidth(currentLineNumber - 1);
			lineNumbers.setText(numbers);
		} finally {
			isUpdatingLineNumbers = false;
		}
	}

	private void applySettings() {
		int fontSize = prefs.getInt("editor_font_size", DEFAULT_FONT_SIZE);
		pinchZoomEnabled = prefs.getBoolean("editor_pinch_zoom", false);
		editor.setTextSize(fontSize);
		lineNumbers.setTextSize(Math.max(8, fontSize - 3));

		boolean wordWrap = prefs.getBoolean("editor_word_wrap", true);
		editor.setHorizontallyScrolling(!wordWrap);

		post(new Runnable() {
			@Override
			public void run() {
				updateLineNumbers();
			}
		});
	}

	public void setText(String text) {
		if (text == null) text = "";
		hideSuggestions();
		suppressAutoComplete = true;
		isHighlighting = true;
		currentText = text;
		editor.setText(text);
		isHighlighting = false;
		suppressAutoComplete = false;
		updateLineNumbers();
		highlightSyntax();
		pushToUndoStack(text);
	}

	public void setTextWithLanguage(String text, String filePath) {
		setLanguage(filePath);
		switchUndoContext(filePath);
		hideSuggestions();
		setText(text);
	}

	private void switchUndoContext(String filePath) {
		String newKey = (filePath == null || filePath.isEmpty()) ? UNDO_KEY_DEFAULT : filePath;
		if (newKey.equals(currentFileKey)) return;
		currentFileKey = newKey;
		if (!undoStacksByFile.containsKey(currentFileKey)) {
			undoStacksByFile.put(currentFileKey, new ArrayList<String>());
		}
		if (!redoStacksByFile.containsKey(currentFileKey)) {
			redoStacksByFile.put(currentFileKey, new ArrayList<String>());
		}
	}

	private ArrayList<String> currentUndoStack() {
		ArrayList<String> stack = undoStacksByFile.get(currentFileKey);
		if (stack == null) {
			stack = new ArrayList<>();
			undoStacksByFile.put(currentFileKey, stack);
		}
		return stack;
	}

	private ArrayList<String> currentRedoStack() {
		ArrayList<String> stack = redoStacksByFile.get(currentFileKey);
		if (stack == null) {
			stack = new ArrayList<>();
			redoStacksByFile.put(currentFileKey, stack);
		}
		return stack;
	}

	public String getText() { return editor.getText().toString(); }

	private float pinchDistance(android.view.MotionEvent event) {
		if (event.getPointerCount() < 2) return 0f;
		float dx = event.getX(0) - event.getX(1);
		float dy = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	private int clampFontSize(int size) {
		return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
	}

	private void setFontSizePreview(float size) {
		size = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
		editor.setTextSize(size);
		lineNumbers.setTextSize(Math.max(8, size - 3));
		updateLineNumbers();
	}

	private void setFontSizeInternal(int size, boolean persist) {
		size = clampFontSize(size);
		if (persist) prefs.edit().putInt("editor_font_size", size).apply();
		editor.setTextSize(size);
		lineNumbers.setTextSize(Math.max(8, size - 3));
		updateLineNumbers();
	}

	public void setFontSize(int size) { setFontSizeInternal(size, true); }
	public int getFontSize() { return prefs.getInt("editor_font_size", DEFAULT_FONT_SIZE); }

	public void setPinchZoomEnabled(boolean enabled) {
		pinchZoomEnabled = enabled;
		prefs.edit().putBoolean("editor_pinch_zoom", enabled).apply();
	}

	public boolean isPinchZoomEnabled() { return pinchZoomEnabled; }

	public void setWordWrap(boolean wrap) {
		prefs.edit().putBoolean("editor_word_wrap", wrap).apply();
		editor.setHorizontallyScrolling(!wrap);
		updateLineNumbers();
	}

	public boolean isWordWrap() { return prefs.getBoolean("editor_word_wrap", true); }

	private void safeSetSpanOnEditable(Editable editable, int start, int end, int color, int totalLen) {
		if (editable == null) return;
		if (start < 0) start = 0;
		if (end > totalLen) end = totalLen;
		if (start >= end) return;
		try {
			editable.setSpan(new ForegroundColorSpan(color), start, end,
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		} catch (Exception ignored) {}
	}

	private void highlightSyntax() {
		if (isHighlighting) return;
		String text = currentText;
		if (text == null || text.isEmpty()) return;
		isHighlighting = true;

		int selectionStart = editor.getSelectionStart();
		int selectionEnd = editor.getSelectionEnd();

		Editable editable = editor.getText();
		if (editable == null) {
			isHighlighting = false;
			return;
		}

		int totalLen = editable.length();

		try {
			ForegroundColorSpan[] existingSpans = editable.getSpans(0, totalLen, ForegroundColorSpan.class);
			for (ForegroundColorSpan span : existingSpans) {
				try { editable.removeSpan(span); } catch (Exception ignored) {}
			}

			Matcher singleLineMatcher = PATTERN_SINGLE_LINE_COMMENT.matcher(text);
			while (singleLineMatcher.find()) {
				safeSetSpanOnEditable(editable, singleLineMatcher.start(), singleLineMatcher.end(), COLOR_COMMENT, totalLen);
			}

			Matcher multiLineMatcher = PATTERN_MULTI_LINE_COMMENT.matcher(text);
			while (multiLineMatcher.find()) {
				safeSetSpanOnEditable(editable, multiLineMatcher.start(), multiLineMatcher.end(), COLOR_COMMENT, totalLen);
			}

			Matcher stringMatcher = PATTERN_STRING.matcher(text);
			while (stringMatcher.find()) {
				safeSetSpanOnEditable(editable, stringMatcher.start(), stringMatcher.end(), COLOR_STRING, totalLen);
			}

			Matcher numberMatcher = PATTERN_NUMBER.matcher(text);
			while (numberMatcher.find()) {
				safeSetSpanOnEditable(editable, numberMatcher.start(), numberMatcher.end(), COLOR_NUMBER, totalLen);
			}

			Matcher operatorMatcher = PATTERN_OPERATOR.matcher(text);
			while (operatorMatcher.find()) {
				safeSetSpanOnEditable(editable, operatorMatcher.start(), operatorMatcher.end(), COLOR_OPERATOR, totalLen);
			}

			if (currentLanguage.equals("html")) {
				Matcher tagMatcher = PATTERN_HTML_TAG.matcher(text);
				while (tagMatcher.find()) {
					safeSetSpanOnEditable(editable, tagMatcher.start(), tagMatcher.end(), COLOR_TAG, totalLen);
				}
			} else if (currentLanguage.equals("css")) {
				Matcher selectorMatcher = PATTERN_CSS_SELECTOR.matcher(text);
				while (selectorMatcher.find()) {
					safeSetSpanOnEditable(editable, selectorMatcher.start(), selectorMatcher.end(), COLOR_TAG, totalLen);
				}
			} else if (currentLanguage.equals("markdown")) {
				Matcher headingMatcher = PATTERN_MARKDOWN_HEADING.matcher(text);
				while (headingMatcher.find()) {
					safeSetSpanOnEditable(editable, headingMatcher.start(), headingMatcher.end(), COLOR_TAG, totalLen);
				}
			}

			if (currentLanguage.equals("json")) {
				Matcher propertyMatcher = PATTERN_JSON_PROPERTY.matcher(text);
				while (propertyMatcher.find()) {
					safeSetSpanOnEditable(editable, propertyMatcher.start(), propertyMatcher.end(), COLOR_FUNCTION, totalLen);
				}
			}
			if (currentLanguage.equals("yaml") || currentLanguage.equals("config")) {
				Matcher keyMatcher = PATTERN_CONFIG_KEY.matcher(text);
				while (keyMatcher.find()) {
					safeSetSpanOnEditable(editable, keyMatcher.start(), keyMatcher.end(), COLOR_FUNCTION, totalLen);
				}
			}
			if (currentLanguage.equals("java") || currentLanguage.equals("kotlin")) {
				Matcher annotationMatcher = PATTERN_ANNOTATION.matcher(text);
				while (annotationMatcher.find()) {
					safeSetSpanOnEditable(editable, annotationMatcher.start(), annotationMatcher.end(), COLOR_TAG, totalLen);
				}
			}
			if (currentLanguage.equals("dart") || currentLanguage.equals("java")
					|| currentLanguage.equals("kotlin") || currentLanguage.equals("python")
					|| currentLanguage.equals("javascript") || currentLanguage.equals("sql")) {
				Matcher functionMatcher = PATTERN_FUNCTION_CALL.matcher(text);
				while (functionMatcher.find()) {
					int start = functionMatcher.start();
					int end = functionMatcher.end();
					if (start >= end) continue;
					ForegroundColorSpan[] spans = editable.getSpans(start, end, ForegroundColorSpan.class);
					boolean protectedSpan = false;
					for (ForegroundColorSpan span : spans) {
						int color = span.getForegroundColor();
						if (color == COLOR_COMMENT || color == COLOR_STRING) { protectedSpan = true; break; }
					}
					if (!protectedSpan) safeSetSpanOnEditable(editable, start, end, COLOR_FUNCTION, totalLen);
				}
			}

			Set<String> keywords = getCurrentKeywords();
			Matcher wordMatcher = PATTERN_WORD.matcher(text);
			while (wordMatcher.find()) {
				String word = wordMatcher.group();
				String keywordWord = currentLanguage.equals("sql") ? word.toLowerCase() : word;
				int start = wordMatcher.start();
				int end = wordMatcher.end();
				if (start >= end) continue;

				ForegroundColorSpan[] colorSpans = editable.getSpans(start, end, ForegroundColorSpan.class);
				if (colorSpans.length > 0 && (colorSpans[0].getForegroundColor() == COLOR_COMMENT
						|| colorSpans[0].getForegroundColor() == COLOR_STRING)) {
					continue;
				}

				if (keywords.contains(keywordWord)) {
					safeSetSpanOnEditable(editable, start, end, COLOR_KEYWORD, totalLen);
				} else if (FLUTTER_WIDGETS.contains(word) || word.matches("[A-Z][a-zA-Z0-9]*")) {
					safeSetSpanOnEditable(editable, start, end, COLOR_FUNCTION, totalLen);
				} else if (word.matches("[a-z][a-zA-Z0-9]*\\(")) {
					safeSetSpanOnEditable(editable, start, end, COLOR_FUNCTION, totalLen);
				}
			}

			if (selectionStart >= 0 && selectionStart <= totalLen) {
				int selEnd = (selectionEnd >= 0 && selectionEnd <= totalLen) ? selectionEnd : selectionStart;
				try {
					editor.setSelection(Math.min(selectionStart, selEnd), Math.max(selectionStart, selEnd));
				} catch (Exception ignored) {}
			}

		} catch (Exception e) {
			// لا setText عند الفشل
		}

		isHighlighting = false;
	}

	public void refreshHighlighting() {
		highlightSyntax();
		updateErrorMarkers();
	}

	public void setExternalErrorLines(Set<Integer> lines) {
		externalErrorLines.clear();
		if (lines != null) externalErrorLines.addAll(lines);
		updateLineNumbers();
	}

	private boolean hasAnyError(int lineNumber) {
		return errorLines.contains(lineNumber) || externalErrorLines.contains(lineNumber);
	}

	public void goToLine(final int lineNumber) { goToPosition(lineNumber, 0); }

	public void goToPosition(final int lineNumber, final int columnNumber) {
		if (editor == null || lineNumber < 1) return;
		post(new Runnable() {
			@Override public void run() {
				android.text.Layout layout = editor.getLayout();
				if (layout == null) return;
				int line = Math.min(lineNumber - 1, Math.max(0, layout.getLineCount() - 1));
				int lineStart = layout.getLineStart(line);
				int lineEnd = layout.getLineEnd(line);
				int offset = Math.max(lineStart, Math.min(lineStart + Math.max(0, columnNumber), lineEnd));
				editor.requestFocus();
				editor.setSelection(offset);
				int viewport = Math.max(1, CodeEditorView.this.getHeight());
				int targetY = Math.max(0, editor.getTop() + layout.getLineTop(line) - viewport / 3);
				CodeEditorView.this.scrollTo(0, targetY);
				lastScrollY = targetY;
			}
		});
	}

	private void updateErrorMarkers() {
		if (editor == null) return;
		errorLines.clear();
		String text = editor.getText().toString();
		ArrayList<Character> stack = new ArrayList<>();
		ArrayList<Integer> stackLines = new ArrayList<>();
		boolean single = false;
		boolean dbl = false;
		boolean escaped = false;
		boolean lineComment = false;
		int line = 1;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') { line++; lineComment = false; continue; }
			if (lineComment) continue;
			if (escaped) { escaped = false; continue; }
			if ((single || dbl) && c == '\\') { escaped = true; continue; }
			if (!single && !dbl && c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') { lineComment = true; i++; continue; }
			if (c == '\'' && !dbl) { single = !single; continue; }
			if (c == '"' && !single) { dbl = !dbl; continue; }
			if (single || dbl) continue;
			if (c == '(' || c == '[' || c == '{') { stack.add(c); stackLines.add(line); }
			else if (c == ')' || c == ']' || c == '}') {
				char expected = c == ')' ? '(' : (c == ']' ? '[' : '{');
				if (stack.isEmpty() || stack.get(stack.size() - 1) != expected) errorLines.add(line);
				else { stack.remove(stack.size() - 1); stackLines.remove(stackLines.size() - 1); }
			}
		}
		if (single || dbl) errorLines.add(line);
		for (Integer openLine : stackLines) errorLines.add(openLine);
		updateLineNumbers();
	}

	public void setEditable(boolean editable) { editor.setEnabled(editable); }
	public void requestEditorFocus() { editor.requestFocus(); }

	public void clear() {
		editor.setText("");
		currentText = "";
		ArrayList<String> undo = currentUndoStack();
		ArrayList<String> redo = currentRedoStack();
		undo.clear();
		redo.clear();
		pushToUndoStack("");
		if (completionPopup != null && completionPopup.isShowing()) {
			completionPopup.dismiss();
			isShowingPopup = false;
		}
	}

	public EditText getEditor() { return editor; }
	public int getVerticalScrollPosition() { return getScrollY(); }

	public void restoreVerticalScrollPosition(final int scrollY) {
		post(new Runnable() {
			@Override public void run() {
				int restored = Math.max(0, scrollY);
				CodeEditorView.this.scrollTo(0, restored);
				lastScrollY = restored;
			}
		});
	}

	public void zoomIn() {
		int currentSize = getFontSize();
		if (currentSize < MAX_FONT_SIZE) setFontSize(currentSize + 2);
	}

	public void zoomOut() {
		int currentSize = getFontSize();
		if (currentSize > MIN_FONT_SIZE) setFontSize(currentSize - 2);
	}

	public void zoomReset() { setFontSize(DEFAULT_FONT_SIZE); }

	public void pushToUndoStack(String text) {
		if (isUndoRedo) return;
		ArrayList<String> undo = currentUndoStack();
		ArrayList<String> redo = currentRedoStack();
		if (!undo.isEmpty() && text != null && text.equals(undo.get(undo.size() - 1))) return;
		if (undo.size() > maxStackSize) undo.remove(0);
		undo.add(text == null ? "" : text);
		redo.clear();
	}

	public void undo() {
		ArrayList<String> undo = currentUndoStack();
		ArrayList<String> redo = currentRedoStack();
		if (undo.size() <= 1) return;

		int savedSelectionStart = editor.getSelectionStart();
		int savedSelectionEnd = editor.getSelectionEnd();
		int savedScrollY = getScrollY();

		isUndoRedo = true;
		String current = undo.remove(undo.size() - 1);
		redo.add(current);
		String previous = undo.get(undo.size() - 1);

		if (previous.equals(editor.getText().toString())) {
			isUndoRedo = false;
			return;
		}

		lastInsertedText = previous; // تجاهل هذا التغيير
		editor.setText(previous);
		currentText = previous;

		int newLen = previous.length();
		int selStart = Math.max(0, Math.min(savedSelectionStart, newLen));
		int selEnd = Math.max(0, Math.min(savedSelectionEnd, newLen));
		try {
			if (selStart != selEnd) editor.setSelection(selStart, selEnd);
			else editor.setSelection(selStart);
		} catch (Exception ignored) {}

		final int restoreScroll = savedScrollY;
		CodeEditorView.this.post(new Runnable() {
			@Override public void run() {
				CodeEditorView.this.scrollTo(0, restoreScroll);
				lastScrollY = restoreScroll;
			}
		});

		isUndoRedo = false;
	}

	public void redo() {
		ArrayList<String> undo = currentUndoStack();
		ArrayList<String> redo = currentRedoStack();
		if (redo.isEmpty()) return;

		int savedSelectionStart = editor.getSelectionStart();
		int savedSelectionEnd = editor.getSelectionEnd();
		int savedScrollY = getScrollY();

		isUndoRedo = true;
		String next = redo.remove(redo.size() - 1);
		if (next.equals(editor.getText().toString())) {
			isUndoRedo = false;
			return;
		}

		undo.add(next);
		lastInsertedText = next; // تجاهل هذا التغيير
		editor.setText(next);
		currentText = next;

		int newLen = next.length();
		int selStart = Math.max(0, Math.min(savedSelectionStart, newLen));
		int selEnd = Math.max(0, Math.min(savedSelectionEnd, newLen));
		try {
			if (selStart != selEnd) editor.setSelection(selStart, selEnd);
			else editor.setSelection(selStart);
		} catch (Exception ignored) {}

		final int restoreScroll = savedScrollY;
		CodeEditorView.this.post(new Runnable() {
			@Override public void run() {
				CodeEditorView.this.scrollTo(0, restoreScroll);
				lastScrollY = restoreScroll;
			}
		});

		isUndoRedo = false;
	}

	public boolean canUndo() { return currentUndoStack().size() > 1; }
	public boolean canRedo() { return !currentRedoStack().isEmpty(); }
}