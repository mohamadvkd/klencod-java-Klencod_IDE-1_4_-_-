package com.flutteride.codweva;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

	// ============================================================
	// ثوابت الألوان
	// ============================================================
	private static final int COLOR_BACKGROUND = 0xFF0D1117;
	private static final int COLOR_SURFACE = 0xFF161B22;
	private static final int COLOR_SURFACE_ALT = 0xFF21262D;
	private static final int COLOR_PRIMARY = 0xFF1F6FEB;
	private static final int COLOR_TEXT_PRIMARY = 0xFFE6EDF3;
	private static final int COLOR_TEXT_SECONDARY = COLOR_TEXT_PRIMARY;
	private static final int COLOR_TEXT_BLUE = COLOR_TEXT_PRIMARY;
	private static final int COLOR_ARROW = 0xFF8B949E;
	private static final int COLOR_BORDER = 0xFF30363D;

	private static final int STEP_COLOR_SUCCESS = 0xFF2EA043;
	private static final int STEP_COLOR_FAILURE = 0xFFCF222E;
	private static final int STEP_COLOR_RUNNING = 0xFFD29922;
	private static final int STEP_COLOR_QUEUED = 0xFF6E7681;

	private static final int LOG_COLOR_ERROR = 0xFFCF222E;
	private static final int LOG_COLOR_NORMAL = 0xFFE6EDF3;
	private static final int LOG_COLOR_CONTEXT = 0xFF8B949E;
	private static final int LOG_COLOR_LINK = 0xFF58A6FF;
	private static final int LOG_COLOR_SUCCESS = 0xFF3FB950;
	private static final int LOG_COLOR_WARNING = 0xFFD29922;

	private static final int DEFAULT_VISIBLE_LOGS = 100;
	private static final int LOGS_BATCH_SIZE = 100;

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	// ============================================================
	// ثوابت الشاشات
	// ============================================================
	private static final int SCREEN_PROJECTS_LIST = 0;
	private static final int SCREEN_CREATE_PROJECT = 1;
	private static final int SCREEN_EDITOR = 2;
	private static final int REQUEST_IMPORT_FILE = 100;
	private static final int REQUEST_IMPORT_PROJECT_ZIP = 101;
	private static final int REQUEST_PICK_ICON = 200;
	private static final int REQUEST_PICK_ICON_CREATE = 201;

	// ============================================================
	// الحقول
	// ============================================================
	private int currentScreen = SCREEN_PROJECTS_LIST;
	private SharedPreferences prefs;
	private File projectsBaseDir;
	private File currentProjectDir;
	private File currentOpenFile;
	private boolean reloadFileAfterAi = false;
	private EditorStateStore editorStateStore;
	private final List<EditorTabState> openTabs = new ArrayList<>();
	private int activeTabIndex = -1;
	private LinearLayout tabStrip;
	private HorizontalScrollView tabScrollView;
	private Button btnFavoriteCurrent;
	private Set<String> favoritePaths = new HashSet<>();
	private Uri selectedIconUri;
	private ImageView ivIconPreview;
	private TextView tvIconSelected;
	private Button btnSelectIcon;

	private Button btnToolbarSave;

	private android.widget.RadioGroup repoTypeGroup;

	private LinearLayout layoutProjectsList;
	private ListView lvProjects;
	private List<File> projectsList = new ArrayList<>();

	private LinearLayout layoutCreateProject;
	private EditText etAppName;
	private EditText etPackageName;
	private Spinner spinnerApi;

	private DrawerLayout drawerLayout;
	private LinearLayout layoutEditor;
	private Toolbar toolbar;
	private ActionBarDrawerToggle drawerToggle;
	private CodeEditorView codeEditorView;
	private CodeToolbar codeToolbar;
	private FindReplaceBar findReplaceBar;
	private ProjectSettingsDialog settingsDialog;
	private TextView tvProjectInfo;
	private RecyclerView rvFileTree;
	private FileTreeAdapter fileTreeAdapter;
	private LinearLayoutManager fileTreeLayoutManager;
	private int treeScrollPosition = 0;
	private int treeScrollOffset = 0;
	private LinearLayout drawerContent;
	private TextView tvCurrentFilePath;

	private FrameLayout viewerContainer;
	private View activeViewer;
	private FileKind activeViewerKind = null;
	private ImageViewer activeImageViewer;
	private VideoViewer activeVideoViewer;
	private AudioViewer activeAudioViewer;
	private UnsupportedViewer activeUnsupportedViewer;

	private LinearLayout logPanel;
	private TextView tvLogs;
	private RecyclerView rvSteps;
	private StepsAdapter stepsAdapter;
	private final List<UiStep> uiSteps = new ArrayList<>();
	private TextView tvBuildTimer;
	private TextView tvUploadCounter;
	private LinearLayout uploadCounterBox;
	private TextView tvErrorSummary;
	private final List<BuildError> buildErrors = new ArrayList<>();
	private View logDragHandle;
	private TextView dragIcon;
	private boolean isLogPanelExpanded = false;
	private int logPanelCollapsedHeight;
	private int logPanelExpandedHeight;
	private float startY = 0;
	private int startHeight = 0;
	private android.os.Handler timerHandler;
	private Runnable timerRunnable;

	private long buildStartedAtMs = 0L;
	private boolean buildInProgress = false;

	private boolean isEditorVisible = false;
	private List<File> expandedDirs = new ArrayList<>();
	private FlutterBuildApi buildApi;
	private File selectedFileForDialog = null;

	private final Map<String, Bitmap> projectIconCache = new HashMap<>();

	private boolean lastKeyboardVisible = false;

	// ============================================================
	// فئات داخلية
	// ============================================================
	private static final class BuildError {
		final String rawMessage;
		final String path;
		final int line;
		final int column;
		final boolean warning;

		BuildError(String rawMessage, String path, int line, int column, boolean warning) {
			this.rawMessage = rawMessage;
			this.path = path;
			this.line = line;
			this.column = column;
			this.warning = warning;
		}
	}

	private static final class UiStep {
		String name;
		String status;
		String conclusion;
		long startedAtMs;
		long durationMs;
		long jobId;

		final List<FlutterBuildApi.BuildLogLine> allLogs = new ArrayList<>();
		final List<FlutterBuildApi.BuildLogLine> errorLogs = new ArrayList<>();
		final List<FlutterBuildApi.BuildLogLine> annotations = new ArrayList<>();
		boolean logsExpanded = false;
		int visibleCount = DEFAULT_VISIBLE_LOGS;
		boolean logsFetched = false;
		boolean showFromEnd = false;

		UiStep(String name, String status, String conclusion, long startedAtMs, long durationMs, long jobId) {
			this.name = name == null ? "" : name;
			this.status = status == null ? "" : status;
			this.conclusion = conclusion == null ? "" : conclusion;
			this.startedAtMs = startedAtMs;
			this.durationMs = durationMs;
			this.jobId = jobId;
			this.showFromEnd = isFailure();
		}

		boolean isRunning() { return "in_progress".equalsIgnoreCase(status); }
		boolean isSuccess() { return "completed".equalsIgnoreCase(status) && "success".equalsIgnoreCase(conclusion); }
		boolean isFailure() {
			return "completed".equalsIgnoreCase(status)
					&& ("failure".equalsIgnoreCase(conclusion) || "cancelled".equalsIgnoreCase(conclusion));
		}
		boolean isQueued() { return !isRunning() && !isSuccess() && !isFailure(); }
	}

	// ============================================================
	// onCreate
	// ============================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!AuthManager.getInstance(this).isLoggedIn()) {
			goToLoginActivity();
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(COLOR_BACKGROUND);
			getWindow().setNavigationBarColor(COLOR_BACKGROUND);
			getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(COLOR_BACKGROUND));
		}

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

		prefs = getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE);
		editorStateStore = new EditorStateStore(this);
		projectsBaseDir = new File(getFilesDir(), "projects");
		if (!projectsBaseDir.exists()) {
			projectsBaseDir.mkdirs();
		}

		buildApi = new FlutterBuildApi(this);

		int savedLogMode = prefs.getInt("log_mode", 0);
		buildApi.setLogMode(savedLogMode);

		timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());

		buildAllScreens();
		setupKeyboardVisibilityListener();
		showScreen(SCREEN_PROJECTS_LIST);
		loadProjectsList();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopTimerUpdates();
		releaseCurrentViewer();
		for (Bitmap bmp : projectIconCache.values()) {
			if (bmp != null && !bmp.isRecycled()) bmp.recycle();
		}
		projectIconCache.clear();
	}

	private void goToLoginActivity() {
		Intent intent = new Intent(this, LoginActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		finish();
	}

	private void setupKeyboardVisibilityListener() {
		final View rootView = getWindow().getDecorView().getRootView();
		rootView.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						Rect r = new Rect();
						rootView.getWindowVisibleDisplayFrame(r);
						int screenHeight = rootView.getRootView().getHeight();
						int keypadHeight = screenHeight - r.bottom;

						boolean keyboardVisible = keypadHeight > screenHeight * 0.15;

						if (keyboardVisible == lastKeyboardVisible) return;
						lastKeyboardVisible = keyboardVisible;

						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								boolean isTextEditor = (activeViewerKind == FileKind.TEXT
										|| activeViewerKind == FileKind.UNKNOWN
										|| activeViewerKind == null);

								if (keyboardVisible) {
									if (codeToolbar != null) {
										codeToolbar.setVisibility(isTextEditor ? View.VISIBLE : View.GONE);
									}
									if (logPanel != null) {
										logPanel.setVisibility(View.GONE);
									}
								} else {
									if (codeToolbar != null) {
										codeToolbar.setVisibility(View.GONE);
									}
									if (logPanel != null) {
										logPanel.setVisibility(View.VISIBLE);

										ViewGroup.LayoutParams params = logPanel.getLayoutParams();
										params.height = isLogPanelExpanded
												? logPanelExpandedHeight
												: logPanelCollapsedHeight;
										logPanel.setLayoutParams(params);

										updateDragIcon();
									}
								}
							}
						});
					}
				}
		);
	}

	// ============================================================
	// إدارة العارضات
	// ============================================================
	private void releaseCurrentViewer() {
		try {
			if (activeVideoViewer != null) {
				activeVideoViewer.release();
				activeVideoViewer = null;
			}
			if (activeAudioViewer != null) {
				activeAudioViewer.release();
				activeAudioViewer = null;
			}
			if (activeImageViewer != null) {
				activeImageViewer.release();
				activeImageViewer = null;
			}
			activeUnsupportedViewer = null;
			activeViewer = null;
			activeViewerKind = null;
		} catch (Exception ignored) {}
	}

	private void showFileViewer(File file) {
		if (file == null || !file.isFile() || viewerContainer == null) return;

		releaseCurrentViewer();

		FileKind kind = FileKind.detect(file.getName());
		View newViewer = null;

		try {
			switch (kind) {
				case IMAGE:
					activeImageViewer = new ImageViewer(this);
					activeImageViewer.loadImage(file.getAbsolutePath());
					newViewer = activeImageViewer;
					break;

				case VIDEO:
					activeVideoViewer = new VideoViewer(this);
					activeVideoViewer.loadVideo(file.getAbsolutePath());
					newViewer = activeVideoViewer;
					break;

				case AUDIO:
					activeAudioViewer = new AudioViewer(this);
					activeAudioViewer.loadAudio(file.getAbsolutePath());
					newViewer = activeAudioViewer;
					break;

				case PDF:
				case APK:
				case ARCHIVE:
				case BINARY:
					activeUnsupportedViewer = new UnsupportedViewer(this);
					activeUnsupportedViewer.loadFile(file.getAbsolutePath(), kind);
					newViewer = activeUnsupportedViewer;
					break;

				case TEXT:
				case UNKNOWN:
				default:
					if (codeEditorView == null) {
						codeEditorView = new CodeEditorView(this);
					}
					newViewer = codeEditorView;
					break;
			}
		} catch (Exception e) {
			addLog("تعذر عرض الملف: " + e.getMessage());
			activeUnsupportedViewer = new UnsupportedViewer(this);
			activeUnsupportedViewer.loadFile(file.getAbsolutePath(), kind);
			newViewer = activeUnsupportedViewer;
		}

		if (newViewer == null) return;

		viewerContainer.removeAllViews();
		viewerContainer.addView(newViewer, new FrameLayout.LayoutParams(-1, -1));

		activeViewer = newViewer;
		activeViewerKind = kind;

		boolean isText = (kind == FileKind.TEXT || kind == FileKind.UNKNOWN);
		if (codeToolbar != null) {
			codeToolbar.setVisibility(isText ? (lastKeyboardVisible ? View.VISIBLE : View.GONE) : View.GONE);
			if (isText && codeEditorView != null) {
				codeToolbar.setEditor(codeEditorView.getEditor());
			}
		}

		if (!isText && findReplaceBar != null && findReplaceBar.getVisibility() == View.VISIBLE) {
			findReplaceBar.hide();
		}
	}

	// ============================================================
	// أيقونات المشاريع
	// ============================================================
	private File findProjectIcon(File projectDir) {
		if (projectDir == null || !projectDir.isDirectory()) return null;

		File drawable = new File(projectDir, "android/app/src/main/res/drawable/ic_launcher.png");
		if (drawable.isFile()) return drawable;

		String[] fallbacks = {
				"android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
				"android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
				"android/app/src/main/res/mipmap-xhdpi/ic_launcher.png",
				"android/app/src/main/res/mipmap-hdpi/ic_launcher.png",
				"android/app/src/main/res/mipmap-mdpi/ic_launcher.png",
				"android/app/src/main/res/mipmap/ic_launcher.png",
		};
		for (String path : fallbacks) {
			File f = new File(projectDir, path);
			if (f.isFile()) return f;
		}
		return null;
	}

	private Bitmap getProjectIconCached(File projectDir) {
		if (projectDir == null) return null;
		String key = projectDir.getAbsolutePath();

		if (projectIconCache.containsKey(key)) {
			Bitmap cached = projectIconCache.get(key);
			if (cached != null && !cached.isRecycled()) return cached;
			return null;
		}

		File iconFile = findProjectIcon(projectDir);
		if (iconFile == null || !iconFile.exists()) {
			projectIconCache.put(key, null);
			return null;
		}

		try {
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(iconFile.getAbsolutePath(), opts);

			int targetPx = dp(96);
			int sample = 1;
			int maxDim = Math.max(opts.outWidth, opts.outHeight);
			while (maxDim / (sample * 2) >= targetPx) sample *= 2;

			opts.inJustDecodeBounds = false;
			opts.inSampleSize = sample;
			opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

			Bitmap bmp = BitmapFactory.decodeFile(iconFile.getAbsolutePath(), opts);
			projectIconCache.put(key, bmp);
			return bmp;
		} catch (Exception e) {
			projectIconCache.put(key, null);
			return null;
		}
	}

	private void invalidateProjectIconCache(File projectDir) {
		if (projectDir == null) return;
		String key = projectDir.getAbsolutePath();
		Bitmap old = projectIconCache.remove(key);
		if (old != null && !old.isRecycled()) {
			old.recycle();
		}
	}

	private void loadProjectIconAsync(final File projectDir, final FrameLayout container) {
		if (projectDir == null || container == null) return;

		new Thread(new Runnable() {
			@Override public void run() {
				final Bitmap bmp = getProjectIconCached(projectDir);
				if (bmp == null || bmp.isRecycled()) return;

				container.post(new Runnable() {
					@Override public void run() {
						if (isFinishing() || bmp.isRecycled()) return;
						if (container.getChildCount() == 0) return;

						container.removeAllViews();
						ImageView iconView = new ImageView(MainActivity.this);
						iconView.setImageBitmap(bmp);
						iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
						iconView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
						iconView.setClipToOutline(true);
						iconView.setOutlineProvider(new android.view.ViewOutlineProvider() {
							@Override public void getOutline(View view, android.graphics.Outline outline) {
								outline.setOval(0, 0, view.getWidth(), view.getHeight());
							}
						});
						container.addView(iconView);
					}
				});
			}
		}, "project-icon-loader").start();
	}

	// ============================================================
	// السجل النصي
	// ============================================================
	public void addLog(final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (message == null) return;
				final BuildError parsedError = parseBuildError(message);
				if (parsedError != null) registerBuildError(parsedError);
				String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
				String logEntry = "[" + timestamp + "] " + message + "\n";
				if (tvLogs != null) {
					if (parsedError == null) {
						tvLogs.append(logEntry);
					} else {
						SpannableStringBuilder clickableEntry = new SpannableStringBuilder(logEntry);
						clickableEntry.setSpan(new ForegroundColorSpan(COLOR_TEXT_PRIMARY), 0, clickableEntry.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						clickableEntry.setSpan(new UnderlineSpan(), 0, clickableEntry.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						clickableEntry.setSpan(new ClickableSpan() {
							@Override public void onClick(View widget) { openBuildError(parsedError); }
							@Override public void updateDrawState(android.text.TextPaint ds) {
								ds.setColor(COLOR_TEXT_PRIMARY);
								ds.setUnderlineText(true);
							}
						}, 0, clickableEntry.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						tvLogs.append(clickableEntry);
					}
				}
			}
		});
	}

	// ============================================================
	// تحليل أخطاء البناء
	// ============================================================
	private static final Pattern BUILD_LOCATION_PATTERN = Pattern.compile("(?m)([^\\r\\n]*?\\.(?:dart|java|kt|kts|py|js|ts|xml|yaml|yml|json)):(\\d+)(?::(\\d+))?(?=\\D|$)");

	private BuildError parseBuildError(String message) {
		if (message == null || message.trim().isEmpty()) return null;
		String lower = message.toLowerCase(Locale.US);
		boolean warning = lower.contains("warning") || lower.contains("warn:") || lower.contains("تحذير");
		Matcher matcher = BUILD_LOCATION_PATTERN.matcher(message);
		if (!matcher.find()) return null;
		String path = cleanBuildPath(matcher.group(1));
		int line;
		int column = 0;
		try {
			line = Integer.parseInt(matcher.group(2));
			if (matcher.group(3) != null) column = Integer.parseInt(matcher.group(3));
		} catch (NumberFormatException e) { return null; }
		return new BuildError(message, path, Math.max(1, line), Math.max(0, column), warning);
	}

	private String cleanBuildPath(String rawPath) {
		String path = rawPath == null ? "" : rawPath.trim().replace('\\', '/');
		if (path.startsWith("file://")) path = path.substring(7);
		while (path.startsWith("./")) path = path.substring(2);
		String[] projectRoots = {"lib/", "test/", "android/", "ios/", "web/", "linux/", "macos/", "windows/", "app/src/"};
		int best = -1;
		for (String root : projectRoots) {
			int index = path.lastIndexOf(root);
			if (index > best) best = index;
		}
		if (best > 0 && !new File(path).exists()) path = path.substring(best);
		return path;
	}

	private void registerBuildError(BuildError error) {
		for (BuildError existing : buildErrors) {
			if (existing.path.equals(error.path) && existing.line == error.line && existing.column == error.column && existing.warning == error.warning) {
				refreshExternalErrorMarkersForCurrentFile();
				return;
			}
		}
		buildErrors.add(error);
		if (tvErrorSummary != null) tvErrorSummary.setText("أخطاء البناء: " + buildErrors.size());
		refreshExternalErrorMarkersForCurrentFile();
	}

	private void clearBuildErrors() {
		buildErrors.clear();
		if (tvErrorSummary != null) tvErrorSummary.setText("أخطاء البناء: 0");
		refreshExternalErrorMarkersForCurrentFile();
	}

	private File resolveBuildFile(String rawPath) {
		if (currentProjectDir == null || rawPath == null) return null;
		String path = rawPath.replace('\\', '/');
		if (path.startsWith("file://")) path = path.substring(7);
		while (path.startsWith("./")) path = path.substring(2);
		File direct = new File(path);
		if (direct.isAbsolute() && direct.exists()) return direct;
		String stripped = stripGitHubRunnerPrefix(path);
		if (!stripped.equals(path)) {
			File f = new File(currentProjectDir, stripped);
			if (f.exists()) return f;
		}
		String projectPrefix = currentProjectDir.getName() + "/";
		if (path.startsWith(projectPrefix)) path = path.substring(projectPrefix.length());
		File candidate = new File(currentProjectDir, path);
		if (candidate.exists()) return candidate;
		String tail = extractAfterLastKnownRoot(path);
		if (tail != null && !tail.equals(path)) {
			File tailCandidate = new File(currentProjectDir, tail);
			if (tailCandidate.exists()) return tailCandidate;
		}
		File byExtension = findByKnownRoot(path);
		if (byExtension != null) return byExtension;
		return null;
	}

	private String stripGitHubRunnerPrefix(String path) {
		if (path == null) return "";
		String marker = "/home/runner/work/";
		int idx = path.indexOf(marker);
		if (idx >= 0) {
			String after = path.substring(idx + marker.length());
			String[] parts = after.split("/", 3);
			if (parts.length >= 3) return parts[2];
		}
		return path;
	}

	private String extractAfterLastKnownRoot(String path) {
		if (path == null) return null;
		String[] roots = {"/lib/", "/android/", "/test/", "/web/", "/ios/",
				"/macos/", "/windows/", "/linux/", "/pubspec.yaml"};
		int bestIdx = -1;
		for (String r : roots) {
			int i = path.indexOf(r);
			if (i > bestIdx) { bestIdx = i; }
		}
		if (bestIdx >= 0) return path.substring(bestIdx + 1);
		return null;
	}

	private File findByKnownRoot(String path) {
		if (path == null || currentProjectDir == null) return null;
		String[] knownRoots = {
				"lib/", "android/app/src/main/", "android/app/", "android/",
				"test/", "web/", "ios/Runner/", "ios/",
				"pubspec.yaml", "analysis_options.yaml"
		};
		for (String root : knownRoots) {
			int idx = path.indexOf(root);
			if (idx >= 0) {
				String sub = path.substring(idx);
				File f = new File(currentProjectDir, sub);
				if (f.exists()) return f;
			}
		}
		return null;
	}

	private File findNearbySourceFile(String path) {
		if (path == null || currentProjectDir == null) return null;
		String lower = path.toLowerCase(Locale.US);
		if (lower.endsWith("androidmanifest.xml")) {
			File f = new File(currentProjectDir, "android/app/src/main/AndroidManifest.xml");
			if (f.exists()) return f;
		}
		if (lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts")) {
			File f1 = new File(currentProjectDir, "android/app/build.gradle");
			if (f1.exists()) return f1;
			File f2 = new File(currentProjectDir, "android/build.gradle");
			if (f2.exists()) return f2;
		}
		if (lower.endsWith("styles.xml") || lower.endsWith("colors.xml")
				|| lower.endsWith("strings.xml") || lower.endsWith("themes.xml")) {
			String fileName = path.substring(path.lastIndexOf('/') + 1);
			File f = new File(currentProjectDir, "android/app/src/main/res/values/" + fileName);
			if (f.exists()) return f;
		}
		if (lower.contains("ic_launcher") || lower.contains("drawable")) {
			File resDrawable = new File(currentProjectDir, "android/app/src/main/res/drawable");
			if (resDrawable.exists() && resDrawable.isDirectory()) {
				File[] children = resDrawable.listFiles();
				if (children != null && children.length > 0) return resDrawable;
			}
		}
		if (lower.endsWith("pubspec.yaml")) {
			File f = new File(currentProjectDir, "pubspec.yaml");
			if (f.exists()) return f;
		}
		if (lower.endsWith("main.dart")) {
			File f = new File(currentProjectDir, "lib/main.dart");
			if (f.exists()) return f;
		}
		return null;
	}

	private String getFileNameFromPath(String path) {
		if (path == null) return "";
		int slash = path.lastIndexOf('/');
		if (slash >= 0 && slash < path.length() - 1) return path.substring(slash + 1);
		return path;
	}

	private void refreshExternalErrorMarkersForCurrentFile() {
		if (codeEditorView == null) return;
		if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) return;
		Set<Integer> lines = new HashSet<>();
		if (currentOpenFile != null) {
			for (BuildError error : buildErrors) {
				File errorFile = resolveBuildFile(error.path);
				if (sameFile(errorFile, currentOpenFile)) lines.add(error.line);
			}
		}
		codeEditorView.setExternalErrorLines(lines);
	}

	private boolean sameFile(File first, File second) {
		if (first == null || second == null) return false;
		try {
			return first.getCanonicalFile().equals(second.getCanonicalFile());
		} catch (IOException e) {
			return first.getAbsolutePath().equals(second.getAbsolutePath());
		}
	}

	private void openBuildError(final BuildError error) {
		if (error == null) return;
		File file = resolveBuildFile(error.path);
		if (file == null) file = findNearbySourceFile(error.path);
		if (file == null) {
			String fileName = error.path;
			int slash = fileName.lastIndexOf('/');
			if (slash >= 0) fileName = fileName.substring(slash + 1);
			if (fileName.isEmpty()) fileName = error.path;
			StringBuilder msg = new StringBuilder();
			msg.append("الملف غير موجود في مشروعك\n\n");
			msg.append("الاسم: ").append(fileName).append("\n");
			msg.append("السطر: ").append(error.line);
			if (error.column > 0) msg.append(":").append(error.column);
			msg.append("\n\nقد يكون ملفاً مؤقتاً أثناء البناء على GitHub");
			new AlertDialog.Builder(MainActivity.this)
					.setTitle("تعذّر فتح الملف")
					.setMessage(msg.toString())
					.setPositiveButton("نسخ المسار", new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface d, int which) {
							android.content.ClipboardManager cm = (android.content.ClipboardManager)
									getSystemService(Context.CLIPBOARD_SERVICE);
							cm.setPrimaryClip(android.content.ClipData.newPlainText("path", error.path));
							Toast.makeText(MainActivity.this, "تم نسخ المسار", Toast.LENGTH_SHORT).show();
						}
					})
					.setNegativeButton("إغلاق", null)
					.show();
			return;
		}
		openFileInEditor(file);
		if (file.isDirectory()) {
			Toast.makeText(this, "تم فتح المجلد: " + file.getName(), Toast.LENGTH_SHORT).show();
			return;
		}
		if (activeViewerKind == FileKind.TEXT || activeViewerKind == FileKind.UNKNOWN) {
			final File targetFile = file;
			codeEditorView.post(new Runnable() {
				@Override public void run() { codeEditorView.goToPosition(error.line, error.column); }
			});
			String note;
			if (targetFile.getName().equalsIgnoreCase(getFileNameFromPath(error.path))) {
				note = "السطر " + error.line + (error.column > 0 ? ":" + error.column : "");
			} else {
				note = "تم فتح ملف قريب: " + targetFile.getName();
			}
			Toast.makeText(this, note, Toast.LENGTH_SHORT).show();
		}
	}

	// ============================================================
	// بناء الشاشات
	// ============================================================
	private void buildAllScreens() {
		buildProjectsListScreen();
		buildCreateProjectScreen();
		buildEditorScreen();
	}

	private void buildProjectsListScreen() {
		layoutProjectsList = new LinearLayout(this);
		layoutProjectsList.setOrientation(LinearLayout.VERTICAL);
		layoutProjectsList.setBackgroundColor(COLOR_BACKGROUND);
		layoutProjectsList.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

		LinearLayout appBar = new LinearLayout(this);
		appBar.setOrientation(LinearLayout.HORIZONTAL);
		appBar.setGravity(Gravity.CENTER_VERTICAL);
		appBar.setPadding(dp(16), dp(14), dp(16), dp(14));
		appBar.setBackgroundColor(COLOR_BACKGROUND);

		final TextView brand = new TextView(this);
		brand.setText("KlencodIDE");
		brand.setTextColor(COLOR_TEXT_PRIMARY);
		brand.setTextSize(24);
		brand.setTypeface(null, android.graphics.Typeface.BOLD);
		brand.setLetterSpacing(0.02f);
		brand.setAlpha(0f);
		brand.setTranslationY(-40f);
		brand.setScaleX(0.85f);
		brand.setScaleY(0.85f);
		appBar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));

		layoutProjectsList.addView(appBar, new LinearLayout.LayoutParams(-1, -2));

		brand.post(new Runnable() {
			@Override public void run() {
				playBrandAnimation(brand);
			}
		});

		LinearLayout quickActions = new LinearLayout(this);
		quickActions.setOrientation(LinearLayout.VERTICAL);
		quickActions.setPadding(dp(16), dp(4), dp(16), dp(12));

		LinearLayout actionsRow1 = new LinearLayout(this);
		actionsRow1.setOrientation(LinearLayout.HORIZONTAL);

		Button newProjectBtn = UiStyle.primaryButton(this, "+ مشروع جديد");
		newProjectBtn.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { showScreen(SCREEN_CREATE_PROJECT); }
		});
		LinearLayout.LayoutParams npParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
		npParams.setMarginEnd(dp(6));
		actionsRow1.addView(newProjectBtn, npParams);

		Button templatesBtn = UiStyle.ghostButton(this, "القوالب الجاهزة");
		templatesBtn.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, TemplateStoreActivity.class);
				startActivity(intent);
			}
		});
		LinearLayout.LayoutParams tpParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
		tpParams.setMarginStart(dp(6));
		actionsRow1.addView(templatesBtn, tpParams);

		quickActions.addView(actionsRow1, new LinearLayout.LayoutParams(-1, -2));

		LinearLayout actionsRow2 = new LinearLayout(this);
		actionsRow2.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams row2Params = new LinearLayout.LayoutParams(-1, -2);
		row2Params.topMargin = dp(8);
		actionsRow2.setLayoutParams(row2Params);

		Button importZipBtn = UiStyle.ghostButton(this, "استيراد مشروع من ZIP");
		importZipBtn.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { openProjectZipImport(); }
		});
		actionsRow2.addView(importZipBtn, new LinearLayout.LayoutParams(-1, dp(46)));

		quickActions.addView(actionsRow2);

		layoutProjectsList.addView(quickActions, new LinearLayout.LayoutParams(-1, -2));

		LinearLayout sectionHeader = new LinearLayout(this);
		sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
		sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
		sectionHeader.setPadding(dp(16), dp(6), dp(16), dp(8));

		TextView sectionTitle = new TextView(this);
		sectionTitle.setText("مشاريعك");
		sectionTitle.setTextColor(COLOR_TEXT_PRIMARY);
		sectionTitle.setTextSize(15);
		sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
		sectionHeader.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView projectCount = new TextView(this);
		projectCount.setText("");
		projectCount.setTextColor(COLOR_TEXT_SECONDARY);
		projectCount.setTextSize(12);
		projectCount.setTag("project_count");
		sectionHeader.addView(projectCount);

		layoutProjectsList.addView(sectionHeader, new LinearLayout.LayoutParams(-1, -2));

		FrameLayout listContainer = new FrameLayout(this);
		LinearLayout.LayoutParams listContainerParams =
				new LinearLayout.LayoutParams(-1, 0, 1f);
		layoutProjectsList.addView(listContainer, listContainerParams);

		LinearLayout emptyState = new LinearLayout(this);
		emptyState.setOrientation(LinearLayout.VERTICAL);
		emptyState.setGravity(Gravity.CENTER);
		emptyState.setPadding(dp(24), dp(40), dp(24), dp(40));
		emptyState.setVisibility(View.GONE);

		TextView emptyShape = new TextView(this);
		emptyShape.setGravity(Gravity.CENTER);
		GradientDrawable emptyShapeBg = new GradientDrawable();
		emptyShapeBg.setCornerRadius(dp(24));
		emptyShapeBg.setColor(COLOR_SURFACE_ALT);
		emptyShapeBg.setStroke(dp(1), COLOR_BORDER);
		emptyShape.setBackground(emptyShapeBg);
		LinearLayout.LayoutParams eshParams = new LinearLayout.LayoutParams(dp(96), dp(96));
		eshParams.gravity = Gravity.CENTER_HORIZONTAL;
		emptyState.addView(emptyShape, eshParams);

		TextView emptyTitle = new TextView(this);
		emptyTitle.setText("لا توجد مشاريع بعد");
		emptyTitle.setTextColor(COLOR_TEXT_PRIMARY);
		emptyTitle.setTextSize(16);
		emptyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
		emptyTitle.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(-2, -2);
		etParams.topMargin = dp(16);
		emptyState.addView(emptyTitle, etParams);

		TextView emptySub = new TextView(this);
		emptySub.setText("ابدأ بإنشاء مشروع Flutter الأول");
		emptySub.setTextColor(COLOR_TEXT_SECONDARY);
		emptySub.setTextSize(13);
		emptySub.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams esParams = new LinearLayout.LayoutParams(-2, -2);
		esParams.topMargin = dp(6);
		emptyState.addView(emptySub, esParams);

		Button emptyNewBtn = UiStyle.primaryButton(this, "+ إنشاء مشروع");
		emptyNewBtn.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { showScreen(SCREEN_CREATE_PROJECT); }
		});
		LinearLayout.LayoutParams enParams = new LinearLayout.LayoutParams(-2, dp(44));
		enParams.topMargin = dp(24);
		enParams.gravity = Gravity.CENTER;
		emptyState.addView(emptyNewBtn, enParams);

		listContainer.addView(emptyState, new FrameLayout.LayoutParams(-1, -1));

		lvProjects = new ListView(this);
		lvProjects.setBackgroundColor(COLOR_BACKGROUND);
		lvProjects.setDivider(null);
		lvProjects.setDividerHeight(dp(8));
		lvProjects.setVerticalScrollBarEnabled(false);
		lvProjects.setPadding(dp(12), dp(4), dp(12), dp(8));
		lvProjects.setClipToPadding(false);
		lvProjects.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
		lvProjects.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				openProject(projectsList.get(position));
			}
		});
		lvProjects.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				deleteProject(projectsList.get(position));
				return true;
			}
		});
		lvProjects.setEmptyView(emptyState);

		listContainer.addView(lvProjects);

		LinearLayout bottomNav = new LinearLayout(this);
		bottomNav.setOrientation(LinearLayout.HORIZONTAL);
		bottomNav.setGravity(Gravity.CENTER);
		bottomNav.setPadding(dp(8), dp(6), dp(8), dp(6));
		bottomNav.setBackgroundColor(COLOR_SURFACE);

		bottomNav.addView(buildBottomNavItem("مشاريع", true, new View.OnClickListener() {
			@Override public void onClick(View v) {
				// نحن هنا
			}
		}), new LinearLayout.LayoutParams(0, -2, 1f));

		bottomNav.addView(buildBottomNavItem("المكتبة", false, new View.OnClickListener() {
			@Override public void onClick(View v) {
				startActivity(new Intent(MainActivity.this, BuiltAppsActivity.class));
			}
		}), new LinearLayout.LayoutParams(0, -2, 1f));

		bottomNav.addView(buildBottomNavItem("AI", false, new View.OnClickListener() {
			@Override public void onClick(View v) {
				startActivity(new Intent(MainActivity.this, AiSettingsActivity.class));
			}
		}), new LinearLayout.LayoutParams(0, -2, 1f));

		bottomNav.addView(buildBottomNavItem("إعدادات", false, new View.OnClickListener() {
			@Override public void onClick(View v) {
				showSettingsDialog();
			}
		}), new LinearLayout.LayoutParams(0, -2, 1f));

		View divider = new View(this);
		divider.setBackgroundColor(COLOR_BORDER);
		layoutProjectsList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
		layoutProjectsList.addView(bottomNav, new LinearLayout.LayoutParams(-1, -2));
	}

	private void playBrandAnimation(final TextView brand) {
		if (brand == null) return;

		ObjectAnimator fadeIn = ObjectAnimator.ofFloat(brand, "alpha", 0f, 1f);
		fadeIn.setDuration(500);
		fadeIn.setInterpolator(new DecelerateInterpolator());

		ObjectAnimator slideDown = ObjectAnimator.ofFloat(brand, "translationY", -40f, 0f);
		slideDown.setDuration(600);
		slideDown.setInterpolator(new DecelerateInterpolator());

		ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(brand, "scaleX", 0.85f, 1f);
		ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(brand, "scaleY", 0.85f, 1f);
		scaleUpX.setDuration(600);
		scaleUpY.setDuration(600);
		scaleUpX.setInterpolator(new OvershootInterpolator(1.4f));
		scaleUpY.setInterpolator(new OvershootInterpolator(1.4f));

		AnimatorSet phase1 = new AnimatorSet();
		phase1.playTogether(fadeIn, slideDown, scaleUpX, scaleUpY);
		phase1.setStartDelay(80);

		ObjectAnimator pulseX = ObjectAnimator.ofFloat(brand, "scaleX", 1f, 1.04f, 1f);
		ObjectAnimator pulseY = ObjectAnimator.ofFloat(brand, "scaleY", 1f, 1.04f, 1f);
		pulseX.setDuration(400);
		pulseY.setDuration(400);
		pulseX.setInterpolator(new AccelerateDecelerateInterpolator());
		pulseY.setInterpolator(new AccelerateDecelerateInterpolator());

		AnimatorSet phase2 = new AnimatorSet();
		phase2.playTogether(pulseX, pulseY);

		AnimatorSet full = new AnimatorSet();
		full.playSequentially(phase1, phase2);
		full.start();
	}

	private LinearLayout buildBottomNavItem(String label, boolean active,
	                                          View.OnClickListener onClick) {
		LinearLayout item = new LinearLayout(this);
		item.setOrientation(LinearLayout.VERTICAL);
		item.setGravity(Gravity.CENTER);
		item.setPadding(dp(4), dp(8), dp(4), dp(6));
		item.setClickable(true);
		item.setFocusable(true);
		item.setOnClickListener(onClick);

		View indicator = new View(this);
		if (active) {
			indicator.setBackgroundColor(COLOR_PRIMARY);
		} else {
			indicator.setBackgroundColor(0x00000000);
		}
		LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(28), dp(3));
		ip.gravity = Gravity.CENTER_HORIZONTAL;
		ip.bottomMargin = dp(6);
		item.addView(indicator, ip);

		TextView text = new TextView(this);
		text.setText(label);
		text.setTextSize(13);
		text.setTextColor(active ? COLOR_PRIMARY : COLOR_TEXT_SECONDARY);
		text.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
		text.setGravity(Gravity.CENTER);
		item.addView(text);

		return item;
	}

	private void buildCreateProjectScreen() {
		layoutCreateProject = new LinearLayout(this);
		layoutCreateProject.setOrientation(LinearLayout.VERTICAL);
		layoutCreateProject.setPadding(dp(22), dp(22), dp(22), dp(24));
		layoutCreateProject.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
		layoutCreateProject.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT));
		layoutCreateProject.setBackgroundColor(COLOR_BACKGROUND);

		TextView tvCreateTitle = new TextView(this);
		tvCreateTitle.setText("إنشاء مشروع Flutter جديد");
		tvCreateTitle.setTextSize(22);
		tvCreateTitle.setTextColor(COLOR_TEXT_PRIMARY);
		tvCreateTitle.setPadding(0, 0, 0, 32);
		layoutCreateProject.addView(tvCreateTitle);

		TextView tvAppName = new TextView(this);
		tvAppName.setText("اسم التطبيق:");
		tvAppName.setTextSize(14);
		tvAppName.setTextColor(COLOR_TEXT_PRIMARY);
		tvAppName.setPadding(0, 0, 0, 8);
		layoutCreateProject.addView(tvAppName);

		etAppName = new EditText(this);
		etAppName.setHint("مثلاً: My App");
		etAppName.setHintTextColor(COLOR_TEXT_SECONDARY);
		etAppName.setTextColor(COLOR_TEXT_PRIMARY);
		etAppName.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		etAppName.setPadding(16, 14, 16, 14);
		etAppName.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		layoutCreateProject.addView(etAppName);

		TextView tvRepoInfo = new TextView(this);
		tvRepoInfo.setText("ملاحظة: سيُستخدم اسم التطبيق كاسم المستودع على GitHub. إذا تكرر، سيُضاف رقم تلقائياً.");
		tvRepoInfo.setTextSize(11);
		tvRepoInfo.setTextColor(COLOR_TEXT_SECONDARY);
		tvRepoInfo.setPadding(0, dp(6), 0, 0);
		tvRepoInfo.setLineSpacing(0, 1.1f);
		layoutCreateProject.addView(tvRepoInfo);

		View spacer1 = new View(this);
		spacer1.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 16));
		layoutCreateProject.addView(spacer1);

		TextView tvPackageName = new TextView(this);
		tvPackageName.setText("اسم الحزمة:");
		tvPackageName.setTextSize(14);
		tvPackageName.setTextColor(COLOR_TEXT_PRIMARY);
		tvPackageName.setPadding(0, 0, 0, 8);
		layoutCreateProject.addView(tvPackageName);

		etPackageName = new EditText(this);
		etPackageName.setHint("مثلاً: com.example.myapp");
		etPackageName.setHintTextColor(COLOR_TEXT_SECONDARY);
		etPackageName.setTextColor(COLOR_TEXT_PRIMARY);
		etPackageName.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		etPackageName.setPadding(16, 14, 16, 14);
		etPackageName.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		layoutCreateProject.addView(etPackageName);

		View spacer2 = new View(this);
		spacer2.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 16));
		layoutCreateProject.addView(spacer2);

		TextView tvApi = new TextView(this);
		tvApi.setText("Target API:");
		tvApi.setTextSize(14);
		tvApi.setTextColor(COLOR_TEXT_PRIMARY);
		tvApi.setPadding(0, 0, 0, 8);
		layoutCreateProject.addView(tvApi);

		spinnerApi = new Spinner(this);
		String[] apiLevels = {"21 (Android 5.0)", "24 (Android 7.0)", "28 (Android 9)", "31 (Android 12)", "33 (Android 13)"};
		ArrayAdapter<String> adapter = UiStyle.spinnerAdapter(this, apiLevels);
		spinnerApi.setAdapter(adapter);
		spinnerApi.setSelection(4);
		spinnerApi.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		spinnerApi.setPadding(16, 12, 16, 12);
		UiStyle.styleSpinner(spinnerApi);
		layoutCreateProject.addView(spinnerApi);

		View spacerRepo = new View(this);
		spacerRepo.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 16));
		layoutCreateProject.addView(spacerRepo);

		TextView tvRepoType = new TextView(this);
		tvRepoType.setText("نوع المستودع على GitHub:");
		tvRepoType.setTextSize(14);
		tvRepoType.setTextColor(COLOR_TEXT_PRIMARY);
		tvRepoType.setPadding(0, 0, 0, 8);
		layoutCreateProject.addView(tvRepoType);

		repoTypeGroup = new android.widget.RadioGroup(this);
		repoTypeGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
		repoTypeGroup.setPadding(dp(4), dp(4), dp(4), dp(4));

		android.widget.RadioButton rbPublic = new android.widget.RadioButton(this);
		rbPublic.setText("عام (Public) - موصى به");
		rbPublic.setTextColor(COLOR_TEXT_PRIMARY);
		rbPublic.setTextSize(13);
		rbPublic.setId(android.R.id.button1);
		repoTypeGroup.addView(rbPublic);

		TextView tvPublicInfo = new TextView(this);
		tvPublicInfo.setText("بناء مجاني غير محدود\nمناسب للمشاريع التعليمية\nالكود مرئي للجميع");
		tvPublicInfo.setTextSize(11);
		tvPublicInfo.setTextColor(COLOR_TEXT_SECONDARY);
		tvPublicInfo.setPadding(dp(30), 0, 0, dp(10));
		tvPublicInfo.setLineSpacing(0, 1.15f);
		repoTypeGroup.addView(tvPublicInfo);

		android.widget.RadioButton rbPrivate = new android.widget.RadioButton(this);
		rbPrivate.setText("خاص (Private)");
		rbPrivate.setTextColor(COLOR_TEXT_PRIMARY);
		rbPrivate.setTextSize(13);
		rbPrivate.setId(android.R.id.button2);
		repoTypeGroup.addView(rbPrivate);

		TextView tvPrivateInfo = new TextView(this);
		tvPrivateInfo.setText("كودك خاص تماماً\n2,000 دقيقة بناء مجاناً/شهر\nبعدها قد تحتاج دفع");
		tvPrivateInfo.setTextSize(11);
		tvPrivateInfo.setTextColor(COLOR_TEXT_SECONDARY);
		tvPrivateInfo.setPadding(dp(30), 0, 0, dp(10));
		tvPrivateInfo.setLineSpacing(0, 1.15f);
		repoTypeGroup.addView(tvPrivateInfo);

		repoTypeGroup.check(android.R.id.button1);
		layoutCreateProject.addView(repoTypeGroup, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		View spacer3 = new View(this);
		spacer3.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 16));
		layoutCreateProject.addView(spacer3);

		TextView tvIcon = new TextView(this);
		tvIcon.setText("أيقونة التطبيق:");
		tvIcon.setTextSize(14);
		tvIcon.setTextColor(COLOR_TEXT_PRIMARY);
		tvIcon.setPadding(0, 0, 0, 8);
		layoutCreateProject.addView(tvIcon);

		LinearLayout iconRow = new LinearLayout(this);
		iconRow.setOrientation(LinearLayout.HORIZONTAL);
		iconRow.setGravity(Gravity.CENTER_VERTICAL);
		iconRow.setPadding(0, 0, 0, 16);

		btnSelectIcon = new Button(this);
		btnSelectIcon.setText("اختيار أيقونة");
		btnSelectIcon.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		btnSelectIcon.setMinHeight(dp(48));
		btnSelectIcon.setTextColor(COLOR_TEXT_PRIMARY);
		btnSelectIcon.setAllCaps(false);
		btnSelectIcon.setPadding(16, 12, 16, 12);
		btnSelectIcon.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { pickIconForCreateProject(); }
		});
		iconRow.addView(btnSelectIcon);

		ivIconPreview = new ImageView(this);
		ivIconPreview.setLayoutParams(new LinearLayout.LayoutParams(60, 60));
		ivIconPreview.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		ivIconPreview.setPadding(4, 4, 4, 4);
		ivIconPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
		ivIconPreview.setVisibility(View.GONE);
		iconRow.addView(ivIconPreview);

		tvIconSelected = new TextView(this);
		tvIconSelected.setText("لم يتم اختيار أيقونة (سيتم استخدام الافتراضية)");
		tvIconSelected.setTextSize(11);
		tvIconSelected.setTextColor(COLOR_TEXT_SECONDARY);
		tvIconSelected.setPadding(16, 0, 0, 0);
		tvIconSelected.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		iconRow.addView(tvIconSelected);

		layoutCreateProject.addView(iconRow);

		View spacer4 = new View(this);
		spacer4.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 32));
		layoutCreateProject.addView(spacer4);

		LinearLayout createButtonRow = new LinearLayout(this);
		createButtonRow.setOrientation(LinearLayout.HORIZONTAL);
		createButtonRow.setGravity(Gravity.CENTER);
		createButtonRow.setPadding(0, 0, 0, 0);

		Button btnCancel = new Button(this);
		btnCancel.setText("إلغاء");
		btnCancel.setBackground(UiStyle.rounded(this, COLOR_SURFACE_ALT, 12, 0xFF30363D, 1));
		btnCancel.setMinHeight(dp(48));
		btnCancel.setTextColor(COLOR_TEXT_PRIMARY);
		btnCancel.setAllCaps(false);
		btnCancel.setPadding(16, 14, 16, 14);
		android.widget.LinearLayout.LayoutParams cancelParams = new android.widget.LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		cancelParams.setMargins(0, 0, 8, 0);
		btnCancel.setLayoutParams(cancelParams);
		btnCancel.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { showScreen(SCREEN_PROJECTS_LIST); }
		});
		createButtonRow.addView(btnCancel);

		Button btnCreate = new Button(this);
		btnCreate.setText("إنشاء");
		btnCreate.setBackground(UiStyle.rounded(this, COLOR_PRIMARY, 12, 0xFF58A6FF, 1));
		btnCreate.setMinHeight(dp(48));
		btnCreate.setTextColor(COLOR_TEXT_PRIMARY);
		btnCreate.setAllCaps(false);
		btnCreate.setPadding(16, 14, 16, 14);
		android.widget.LinearLayout.LayoutParams createParams = new android.widget.LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		createParams.setMargins(8, 0, 0, 0);
		btnCreate.setLayoutParams(createParams);
		btnCreate.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { createProject(); }
		});
		createButtonRow.addView(btnCreate);

		layoutCreateProject.addView(createButtonRow);
	}

	private void pickIconForCreateProject() {
		Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/*");
		startActivityForResult(intent, REQUEST_PICK_ICON_CREATE);
	}

	private Uri cropImageToSquare(Uri imageUri) {
		try {
			InputStream inputStream = getContentResolver().openInputStream(imageUri);
			Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
			inputStream.close();
			int width = originalBitmap.getWidth();
			int height = originalBitmap.getHeight();
			int size = Math.min(width, height);
			int left = (width - size) / 2;
			int top = (height - size) / 2;
			Bitmap squaredBitmap = Bitmap.createBitmap(originalBitmap, left, top, size, size);
			Bitmap resizedBitmap = Bitmap.createScaledBitmap(squaredBitmap, 512, 512, true);
			File tempFile = new File(getCacheDir(), "temp_icon_" + System.currentTimeMillis() + ".png");
			FileOutputStream fos = new FileOutputStream(tempFile);
			resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
			fos.close();
			squaredBitmap.recycle();
			resizedBitmap.recycle();
			originalBitmap.recycle();
			return Uri.fromFile(tempFile);
		} catch (Exception e) {
			addLog("خطأ في قص الصورة: " + e.getMessage());
			return imageUri;
		}
	}

	private void buildTabStrip() {
		tabScrollView = new HorizontalScrollView(this);
		tabScrollView.setHorizontalScrollBarEnabled(false);
		tabScrollView.setBackgroundColor(COLOR_BACKGROUND);
		tabScrollView.setPadding(dp(4), dp(2), dp(4), dp(2));
		tabScrollView.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
		tabStrip = new LinearLayout(this);
		tabStrip.setOrientation(LinearLayout.HORIZONTAL);
		tabStrip.setGravity(Gravity.CENTER_VERTICAL);
		tabStrip.setPadding(dp(4), dp(2), dp(4), dp(2));
		tabStrip.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
		tabScrollView.addView(tabStrip, new HorizontalScrollView.LayoutParams(
				HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.MATCH_PARENT));
		layoutEditor.addView(tabScrollView);
	}

	private void renderTabStrip() {
		if (tabStrip == null) return;
		tabStrip.removeAllViews();
		for (int i = 0; i < openTabs.size(); i++) {
			final int index = i;
			final EditorTabState state = openTabs.get(i);
			File file = new File(state.absolutePath);
			LinearLayout tab = new LinearLayout(this);
			tab.setOrientation(LinearLayout.HORIZONTAL);
			tab.setGravity(Gravity.CENTER_VERTICAL);
			tab.setPadding(dp(6), 0, dp(2), 0);
			tab.setBackground(UiStyle.rounded(this, index == activeTabIndex ? COLOR_PRIMARY : COLOR_SURFACE_ALT, 8, 0xFF30363D, 1));

			TextView label = new TextView(this);
			label.setText(file.getName() + (state.dirty ? " •" : ""));
			label.setTextColor(COLOR_TEXT_PRIMARY);
			label.setTextSize(11);
			label.setSingleLine(true);
			label.setMaxWidth(dp(90));
			label.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
			label.setPadding(0, 0, dp(4), 0);
			tab.addView(label, new LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.MATCH_PARENT));

			Button close = new Button(this);
			close.setText("×");
			close.setTextColor(COLOR_TEXT_PRIMARY);
			close.setTextSize(14);
			close.setAllCaps(false);
			close.setMinWidth(0);
			close.setMinimumWidth(0);
			close.setMinHeight(0);
			close.setMinimumHeight(0);
			close.setPadding(0, 0, 0, 0);
			close.setBackground(UiStyle.rounded(this, index == activeTabIndex ? COLOR_PRIMARY : COLOR_SURFACE_ALT, 6, 0xFF30363D, 0));
			close.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { closeTab(index); }
			});
			tab.addView(close, new LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.MATCH_PARENT));

			tab.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { switchToTab(index); }
			});
			tab.setOnLongClickListener(new View.OnLongClickListener() {
				@Override public boolean onLongClick(View v) { toggleFavorite(new File(state.absolutePath)); return true; }
			});

			tabStrip.addView(tab, new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.MATCH_PARENT));
		}
		updateFavoriteButton();
		updateSaveButtonVisibility();
		if (tabScrollView != null && activeTabIndex >= 0) {
			tabScrollView.post(new Runnable() {
				@Override public void run() {
					if (activeTabIndex < tabStrip.getChildCount()) tabScrollView.smoothScrollTo(tabStrip.getChildAt(activeTabIndex).getLeft(), 0);
				}
			});
		}
	}

	private void showOpenFileDialog() {
		if (currentProjectDir == null) return;
		try {
			final List<String> files = new ProjectFileService(currentProjectDir).listFiles();
			if (files.isEmpty()) { Toast.makeText(this, "لا توجد ملفات قابلة للفتح", Toast.LENGTH_SHORT).show(); return; }
			AlertDialog dialog = new AlertDialog.Builder(this).setTitle("فتح ملف في تبويب")
					.setAdapter(UiStyle.dialogListAdapter(this, files.toArray(new String[0])), new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface d, int which) { openFileInEditor(new File(currentProjectDir, files.get(which))); }
					}).setNegativeButton("إلغاء", null).create();
			dialog.show();
			styleDialog(dialog);
		} catch (Exception e) { Toast.makeText(this, "تعذر قراءة ملفات المشروع", Toast.LENGTH_SHORT).show(); }
	}

	private void showGoToLineDialog() {
		if (codeEditorView == null || currentOpenFile == null) return;
		if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) {
			Toast.makeText(this, "الانتقال إلى سطر متاح للملفات النصية فقط", Toast.LENGTH_SHORT).show();
			return;
		}
		final EditText input = new EditText(this);
		input.setHint("رقم السطر");
		input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		input.setTextColor(COLOR_TEXT_PRIMARY);
		input.setHintTextColor(COLOR_TEXT_SECONDARY);
		input.setPadding(dp(20), dp(12), dp(20), dp(12));
		AlertDialog dialog = new AlertDialog.Builder(this).setTitle("الانتقال إلى رقم سطر").setView(input)
				.setPositiveButton("انتقال", null).setNegativeButton("إلغاء", null).create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface d) {
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						try {
							int line = Integer.parseInt(input.getText().toString().trim());
							if (line < 1) throw new NumberFormatException();
							codeEditorView.goToLine(line);
							dialog.dismiss();
						} catch (NumberFormatException e) { input.setError("أدخل رقم سطر صحيح"); }
					}
				});
			}
		});
		dialog.show();
		styleDialog(dialog);
	}

	private void showFavoritesDialog() {
		if (currentProjectDir == null) return;
		final List<String> favorites = new ArrayList<>();
		for (String path : favoritePaths) if (new File(currentProjectDir, path).isFile()) favorites.add(path);
		Collections.sort(favorites);
		if (favorites.isEmpty()) { Toast.makeText(this, "لا توجد ملفات مفضلة", Toast.LENGTH_SHORT).show(); return; }
		AlertDialog dialog = new AlertDialog.Builder(this).setTitle("الملفات المفضلة")
				.setAdapter(UiStyle.dialogListAdapter(this, favorites.toArray(new String[0])), new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int which) { openFileInEditor(new File(currentProjectDir, favorites.get(which))); }
				}).setNegativeButton("إغلاق", null).create();
		dialog.show();
		styleDialog(dialog);
	}

	private void toggleFavoriteCurrent() {
		if (currentOpenFile != null) toggleFavorite(currentOpenFile);
	}

	private void toggleFavorite(File file) {
		if (file == null || currentProjectDir == null) return;
		String path = getRelativePath(file);
		if (favoritePaths.contains(path)) {
			favoritePaths.remove(path);
			Toast.makeText(this, "أزيل من المفضلة", Toast.LENGTH_SHORT).show();
		} else {
			favoritePaths.add(path);
			Toast.makeText(this, "أضيف إلى المفضلة", Toast.LENGTH_SHORT).show();
		}
		saveEditorProjectState();
		updateFavoriteButton();
		renderTabStrip();
	}

	private void updateFavoriteButton() {
		if (btnFavoriteCurrent != null) btnFavoriteCurrent.setText(currentOpenFile != null && favoritePaths.contains(getRelativePath(currentOpenFile)) ? "★ مفضل" : "☆ مفضل");
	}

	private void formatCurrentDart() {
		if (currentOpenFile == null || !currentOpenFile.getName().toLowerCase(Locale.US).endsWith(".dart")) {
			Toast.makeText(this, "التنسيق متاح لملفات Dart فقط", Toast.LENGTH_SHORT).show();
			return;
		}
		if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) return;
		String formatted = formatDartSource(codeEditorView.getText());
		codeEditorView.setText(formatted);
		updateActiveTabFromEditor();
		saveCurrentFile();
		Toast.makeText(this, "تم تنسيق ملف Dart", Toast.LENGTH_SHORT).show();
	}

	private String formatDartSource(String source) {
		if (source == null || source.isEmpty()) return "";
		String[] lines = source.replace("\r", "").split("\n", -1);
		StringBuilder result = new StringBuilder();
		int indent = 0;
		for (String original : lines) {
			String line = original.trim();
			if (line.isEmpty()) { result.append('\n'); continue; }
			if (line.startsWith("}") || line.startsWith(")") || line.startsWith("]")) indent = Math.max(0, indent - 1);
			for (int i = 0; i < indent; i++) result.append("  ");
			result.append(line).append('\n');
			int opens = countBracesOutsideStrings(line, '{');
			int closes = countBracesOutsideStrings(line, '}');
			indent = Math.max(0, indent + opens - closes);
		}
		if (result.length() > 0) result.setLength(result.length() - 1);
		return result.toString();
	}

	private int countBracesOutsideStrings(String line, char target) {
		boolean single = false, dbl = false, escaped = false;
		int count = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (escaped) { escaped = false; continue; }
			if (c == '\\' && (single || dbl)) { escaped = true; continue; }
			if (c == '\'' && !dbl) single = !single;
			else if (c == '"' && !single) dbl = !dbl;
			else if (!single && !dbl && c == target) count++;
		}
		return count;
	}

	private void showProjectSearchDialog() {
		if (currentProjectDir == null || !currentProjectDir.isDirectory()) return;
		final LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(16), dp(4), dp(16), dp(4));
		root.setBackgroundColor(COLOR_BACKGROUND);
		final EditText query = new EditText(this);
		query.setHint("النص المطلوب البحث عنه");
		query.setTextColor(COLOR_TEXT_PRIMARY);
		query.setHintTextColor(COLOR_TEXT_SECONDARY);
		root.addView(query, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
		final EditText replacement = new EditText(this);
		replacement.setHint("النص البديل (اختياري)");
		replacement.setTextColor(COLOR_TEXT_PRIMARY);
		replacement.setHintTextColor(COLOR_TEXT_SECONDARY);
		root.addView(replacement, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
		final TextView resultCount = new TextView(this);
		resultCount.setTextColor(COLOR_TEXT_BLUE);
		resultCount.setPadding(0, dp(6), 0, dp(6));
		root.addView(resultCount);
		final ListView results = new ListView(this);
		results.setBackgroundColor(COLOR_SURFACE);
		results.setDivider(null);
		root.addView(results, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)));
		final ArrayList<String> resultPaths = new ArrayList<>();
		final ArrayList<Integer> resultLines = new ArrayList<>();
		final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("بحث واستبدال في المشروع")
				.setView(root).setNegativeButton("إغلاق", null).create();
		LinearLayout buttons = new LinearLayout(this);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setGravity(Gravity.CENTER);
		Button search = addDialogButton("بحث");
		Button replace = addDialogButton("استبدال الكل");
		buttons.addView(search, new LinearLayout.LayoutParams(0, dp(42), 1f));
		buttons.addView(replace, new LinearLayout.LayoutParams(0, dp(42), 1f));
		root.addView(buttons, 2);
		search.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { runProjectSearch(query.getText().toString(), resultCount, results, resultPaths, resultLines); }
		});
		replace.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				String find = query.getText().toString();
				if (find.trim().isEmpty()) { query.setError("أدخل نص البحث أولاً"); return; }
				AlertDialog confirm = new AlertDialog.Builder(MainActivity.this).setTitle("تأكيد الاستبدال")
						.setMessage("سيتم استبدال النص في كل ملفات المشروع المطابقة. هل تريد المتابعة؟")
						.setPositiveButton("استبدال", new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface d, int which) {
								replaceInProject(find, replacement.getText().toString(), resultCount, results, resultPaths, resultLines);
							}
						}).setNegativeButton("إلغاء", null).create();
				confirm.show();
				styleDialog(confirm);
			}
		});
		results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < resultPaths.size()) {
					openFileInEditor(new File(currentProjectDir, resultPaths.get(position)));
					if (activeViewerKind == FileKind.TEXT || activeViewerKind == FileKind.UNKNOWN) {
						codeEditorView.goToLine(resultLines.get(position));
					}
					dialog.dismiss();
				}
			}
		});
		dialog.show();
		styleDialog(dialog);
	}

	private Button addDialogButton(String title) {
		Button button = new Button(this);
		button.setText(title);
		button.setTextColor(COLOR_TEXT_BLUE);
		button.setAllCaps(false);
		button.setMinHeight(0);
		button.setMinimumHeight(0);
		button.setBackgroundColor(COLOR_SURFACE);
		return button;
	}

	private void runProjectSearch(String query, TextView resultCount, ListView results, ArrayList<String> resultPaths, ArrayList<Integer> resultLines) {
		resultPaths.clear();
		resultLines.clear();
		ArrayList<String> labels = new ArrayList<>();
		if (query == null || query.isEmpty()) { resultCount.setText("أدخل نصًا للبحث"); results.setAdapter(null); return; }
		try {
			ProjectFileService service = new ProjectFileService(currentProjectDir);
			Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
			for (String relative : service.listFiles()) {
				if (!FileKind.isTextLike(relative)) continue;
				String content;
				try { content = service.readFile(relative); } catch (Exception e) { continue; }
				String[] lines = content.replace("\r", "").split("\n", -1);
				for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
					Matcher matcher = pattern.matcher(lines[lineIndex]);
					if (matcher.find()) {
						resultPaths.add(relative);
						resultLines.add(lineIndex + 1);
						labels.add(relative + ":" + (lineIndex + 1) + "  " + lines[lineIndex].trim());
					}
				}
			}
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
				@Override public View getView(int position, View convertView, ViewGroup parent) {
					TextView text = (TextView) super.getView(position, convertView, parent);
					text.setTextColor(COLOR_TEXT_PRIMARY); text.setTextSize(11); text.setPadding(dp(8), dp(8), dp(8), dp(8));
					return text;
				}
			};
			results.setAdapter(adapter);
			resultCount.setText(labels.size() + " نتيجة في المشروع");
		} catch (Exception e) { resultCount.setText("تعذر البحث: " + e.getMessage()); results.setAdapter(null); }
	}

	private void replaceInProject(String query, String replacement, TextView resultCount, ListView results, ArrayList<String> resultPaths, ArrayList<Integer> resultLines) {
		int changedFiles = 0;
		int replacements = 0;
		try {
			ProjectFileService service = new ProjectFileService(currentProjectDir);
			Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
			for (String relative : service.listFiles()) {
				if (!FileKind.isTextLike(relative)) continue;
				String content;
				try { content = service.readFile(relative); } catch (Exception e) { continue; }
				Matcher matcher = pattern.matcher(content);
				int count = 0;
				while (matcher.find()) count++;
				if (count > 0) {
					service.writeFile(relative, matcher.replaceAll(Matcher.quoteReplacement(replacement)));
					changedFiles++; replacements += count;
					syncOpenTabFromDisk(new File(currentProjectDir, relative));
				}
			}
			resultCount.setText("تم استبدال " + replacements + " تطابق في " + changedFiles + " ملف");
			runProjectSearch(query, resultCount, results, resultPaths, resultLines);
		} catch (Exception e) { resultCount.setText("تعذر الاستبدال: " + e.getMessage()); }
	}

	private void syncOpenTabFromDisk(File file) {
		EditorTabState state = findTab(file.getAbsolutePath());
		if (state == null) return;
		try {
			String content = new ProjectFileService(currentProjectDir).readFile(getRelativePath(file));
			state.content = content; state.dirty = false; state.loaded = true;
			if (currentOpenFile != null && currentOpenFile.getAbsolutePath().equals(file.getAbsolutePath())) {
				if (activeViewerKind == FileKind.TEXT || activeViewerKind == FileKind.UNKNOWN) {
					codeEditorView.setTextWithLanguage(content, file.getAbsolutePath());
				}
			}
			renderTabStrip();
		} catch (Exception ignored) { }
	}

	// ============================================================
	// بناء شاشة المحرر
	// ============================================================
	private void buildEditorScreen() {
		drawerLayout = new DrawerLayout(this);
		drawerLayout.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT));

		LinearLayout mainContent = new LinearLayout(this);
		mainContent.setOrientation(LinearLayout.VERTICAL);
		mainContent.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT));
		mainContent.setBackgroundColor(COLOR_BACKGROUND);

		layoutEditor = new LinearLayout(this);
		layoutEditor.setOrientation(LinearLayout.VERTICAL);
		layoutEditor.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				0, 1f));
		layoutEditor.setBackgroundColor(COLOR_BACKGROUND);

		toolbar = new Toolbar(this);
		toolbar.setBackgroundColor(COLOR_SURFACE);
		toolbar.setElevation(dp(3));
		toolbar.setTitleTextColor(COLOR_TEXT_PRIMARY);
		toolbar.setTitle("Klencod IDE");
		toolbar.setMinimumHeight(0);
		toolbar.setPadding(0, 0, 0, 0);
		toolbar.setTitleMargin(0, 0, 0, 0);
		toolbar.setContentInsetsRelative(dp(8), dp(8));
		toolbar.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

		btnToolbarSave = new Button(this);
		btnToolbarSave.setText("حفظ");
		btnToolbarSave.setTextColor(COLOR_TEXT_PRIMARY);
		btnToolbarSave.setTextSize(13);
		btnToolbarSave.setAllCaps(false);
		btnToolbarSave.setMinWidth(0);
		btnToolbarSave.setMinimumWidth(0);
		btnToolbarSave.setMinHeight(0);
		btnToolbarSave.setMinimumHeight(0);
		btnToolbarSave.setPadding(dp(16), 0, dp(16), 0);
		btnToolbarSave.setBackground(UiStyle.rounded(this, COLOR_PRIMARY, 10, 0xFF58A6FF, 1));
		btnToolbarSave.setVisibility(View.GONE);
		btnToolbarSave.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				saveAllOpenTabs();
				Toast.makeText(MainActivity.this, "تم حفظ جميع التعديلات", Toast.LENGTH_SHORT).show();
			}
		});

		Toolbar.LayoutParams saveParams = new Toolbar.LayoutParams(
				Toolbar.LayoutParams.WRAP_CONTENT,
				dp(36));
		saveParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
		saveParams.rightMargin = dp(8);
		toolbar.addView(btnToolbarSave, saveParams);

		layoutEditor.addView(toolbar);

		tvProjectInfo = new TextView(this);
		tvProjectInfo.setTextSize(11);
		tvProjectInfo.setTextColor(COLOR_TEXT_BLUE);
		tvProjectInfo.setBackgroundColor(COLOR_SURFACE_ALT);
		tvProjectInfo.setPadding(dp(12), dp(3), dp(12), dp(3));
		tvProjectInfo.setVisibility(View.GONE);

		tvCurrentFilePath = new TextView(this);
		tvCurrentFilePath.setTextSize(11);
		tvCurrentFilePath.setTextColor(COLOR_TEXT_SECONDARY);
		tvCurrentFilePath.setBackgroundColor(COLOR_SURFACE_ALT);
		tvCurrentFilePath.setPadding(dp(12), dp(2), dp(12), dp(2));
		tvCurrentFilePath.setVisibility(View.GONE);

		buildTabStrip();

		viewerContainer = new FrameLayout(this);
		viewerContainer.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				0, 1f));
		viewerContainer.setBackgroundColor(COLOR_BACKGROUND);
		layoutEditor.addView(viewerContainer);

		codeEditorView = new CodeEditorView(this);
		codeEditorView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

		codeToolbar = new CodeToolbar(this);
		codeToolbar.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		codeToolbar.setVisibility(View.GONE);
		layoutEditor.addView(codeToolbar);

		codeToolbar.setEditor(codeEditorView.getEditor());

		mainContent.addView(layoutEditor);

		buildLogPanel();
		mainContent.addView(logPanel);

		drawerLayout.addView(mainContent);

		drawerContent = new LinearLayout(this);
		drawerContent.setOrientation(LinearLayout.VERTICAL);
		drawerContent.setBackgroundColor(COLOR_SURFACE);
		drawerContent.setLayoutParams(new DrawerLayout.LayoutParams(
				Math.min(dp(340), (int) (getResources().getDisplayMetrics().widthPixels * 0.88f)),
				ViewGroup.LayoutParams.MATCH_PARENT,
				Gravity.START));

		LinearLayout drawerInner = new LinearLayout(this);
		drawerInner.setOrientation(LinearLayout.VERTICAL);
		drawerInner.setPadding(0, 0, 0, dp(10));
		drawerInner.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT));

		TextView tvDrawerTitle = new TextView(this);
		tvDrawerTitle.setText("هيكل المشروع");
		tvDrawerTitle.setTextColor(COLOR_TEXT_PRIMARY);
		tvDrawerTitle.setTextSize(14);
		tvDrawerTitle.setPadding(dp(20), dp(14), dp(20), dp(14));
		tvDrawerTitle.setBackgroundColor(COLOR_SURFACE_ALT);
		drawerInner.addView(tvDrawerTitle, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		rvFileTree = new RecyclerView(this);
		rvFileTree.setBackgroundColor(COLOR_SURFACE);
		rvFileTree.setPadding(dp(4), dp(6), dp(4), dp(6));
		rvFileTree.setClipToPadding(false);
		rvFileTree.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				0, 1f));
		rvFileTree.setItemAnimator(new DefaultItemAnimator());
		rvFileTree.setHasFixedSize(false);
		rvFileTree.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
		rvFileTree.setTextDirection(View.TEXT_DIRECTION_LTR);

		fileTreeLayoutManager = new LinearLayoutManager(this);
		rvFileTree.setLayoutManager(fileTreeLayoutManager);

		fileTreeAdapter = new FileTreeAdapter(
				new OnFileTreeListener() {
					@Override public void onFileClick(File file, boolean isDirectory) {
						if (isDirectory) {
							toggleDirectory(file);
						} else {
							openFileInEditor(file);
							drawerLayout.closeDrawer(Gravity.START);
						}
					}
					@Override public void onFileLongClick(File file) {
						selectedFileForDialog = file;
						showFileOptionsDialog();
					}
				},
				COLOR_TEXT_PRIMARY,
				dp(1),
				COLOR_ARROW,
				R.drawable.ic_tree_folder,
				R.drawable.ic_tree_folder_open,
				R.drawable.ic_tree_file
		);
		rvFileTree.setAdapter(fileTreeAdapter);

		drawerInner.addView(rvFileTree);

		LinearLayout buttonsContainer = new LinearLayout(this);
		buttonsContainer.setOrientation(LinearLayout.VERTICAL);
		buttonsContainer.setPadding(dp(8), dp(8), dp(8), dp(8));
		buttonsContainer.setBackgroundColor(COLOR_SURFACE);
		buttonsContainer.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		LinearLayout row1 = new LinearLayout(this);
		row1.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams row1Params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		row1.setLayoutParams(row1Params);

		Button btnProjectSettings = createDrawerButton("إعدادات المشروع", false);
		btnProjectSettings.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				drawerLayout.closeDrawer(Gravity.START);
				showProjectSettings();
			}
		});
		LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
		p1.setMargins(0, 0, dp(4), 0);
		row1.addView(btnProjectSettings, p1);

		Button btnAiAssistant = createDrawerButton("الذكاء الاصطناعي", true);
		btnAiAssistant.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				drawerLayout.closeDrawer(Gravity.START);
				openAiChat();
			}
		});
		LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
		p2.setMargins(dp(4), 0, 0, 0);
		row1.addView(btnAiAssistant, p2);

		buttonsContainer.addView(row1);

		LinearLayout row2 = new LinearLayout(this);
		row2.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams row2Params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		row2Params.topMargin = dp(8);
		row2.setLayoutParams(row2Params);

		Button btnExportZip = createDrawerButton("تصدير ZIP", false);
		btnExportZip.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { exportProjectAsZip(); }
		});
		LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(46), 1f);
		p3.setMargins(0, 0, dp(4), 0);
		row2.addView(btnExportZip, p3);

		Button btnBuildApk = createDrawerButton("بناء APK", false);
		btnBuildApk.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { buildApk(); }
		});
		LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(0, dp(46), 1f);
		p4.setMargins(dp(4), 0, 0, 0);
		row2.addView(btnBuildApk, p4);

		buttonsContainer.addView(row2);

		LinearLayout row3 = new LinearLayout(this);
		row3.setOrientation(LinearLayout.HORIZONTAL);
		row3.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams row3Params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		row3Params.topMargin = dp(8);
		row3.setLayoutParams(row3Params);

		Button btnBackToProjects = createDrawerButton("كل المشاريع", false);
		btnBackToProjects.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				saveAllOpenTabs();
				drawerLayout.closeDrawer(Gravity.START);
				showScreen(SCREEN_PROJECTS_LIST);
				loadProjectsList();
			}
		});
		LinearLayout.LayoutParams p5 = new LinearLayout.LayoutParams(0, dp(46), 1f);
		p5.setMargins(dp(60), 0, dp(60), 0);
		row3.addView(btnBackToProjects, p5);

		buttonsContainer.addView(row3);

		drawerInner.addView(buttonsContainer, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		drawerContent.addView(drawerInner);

		drawerLayout.addView(drawerContent);

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
				android.R.string.ok, android.R.string.cancel);
		drawerLayout.addDrawerListener(drawerToggle);
		drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
			@Override public void onDrawerOpened(View drawerView) { restoreTreeScrollState(); }
			@Override public void onDrawerClosed(View drawerView) { saveTreeScrollState(); }
		});

		setupDraggableLogPanel();
	}

	private void updateSaveButtonVisibility() {
		if (btnToolbarSave == null) return;
		boolean hasOpenFile = currentOpenFile != null && !openTabs.isEmpty();
		boolean isText = (activeViewerKind == FileKind.TEXT
				|| activeViewerKind == FileKind.UNKNOWN
				|| activeViewerKind == null);
		btnToolbarSave.setVisibility((hasOpenFile && isText) ? View.VISIBLE : View.GONE);
	}

	private void buildLogPanel() {
		logPanelCollapsedHeight = dp(42);
		logPanelExpandedHeight = Math.max(logPanelCollapsedHeight, getResources().getDisplayMetrics().heightPixels - dp(54));
		logPanel = new LinearLayout(this);
		logPanel.setOrientation(LinearLayout.VERTICAL);
		logPanel.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 16, 0xFF30363D, 1));
		logPanel.setClipToOutline(true);
		logPanel.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				logPanelCollapsedHeight));

		LinearLayout dragHandleContainer = new LinearLayout(this);
		dragHandleContainer.setOrientation(LinearLayout.VERTICAL);
		dragHandleContainer.setBackgroundColor(COLOR_SURFACE_ALT);
		dragHandleContainer.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
		dragHandleContainer.setGravity(Gravity.CENTER);
		dragHandleContainer.setPadding(0, dp(4), 0, dp(4));

		dragIcon = new TextView(this);
		dragIcon.setText("▲");
		dragIcon.setTextColor(COLOR_TEXT_SECONDARY);
		dragIcon.setTextSize(14);
		dragIcon.setGravity(Gravity.CENTER);
		dragHandleContainer.addView(dragIcon);
		logDragHandle = dragHandleContainer;

		logPanel.addView(logDragHandle);

		LinearLayout logHeader = new LinearLayout(this);
		logHeader.setOrientation(LinearLayout.HORIZONTAL);
		logHeader.setPadding(dp(12), dp(8), dp(12), dp(8));
		logHeader.setBackground(UiStyle.rounded(this, COLOR_SURFACE_ALT, 14, 0xFF30363D, 1));
		logHeader.setGravity(Gravity.CENTER_VERTICAL);

		TextView tvLogTitle = new TextView(this);
		tvLogTitle.setText("سجل البناء");
		tvLogTitle.setTextColor(COLOR_TEXT_PRIMARY);
		tvLogTitle.setTextSize(11);
		tvLogTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		logHeader.addView(tvLogTitle);

		tvBuildTimer = new TextView(this);
		tvBuildTimer.setText("");
		tvBuildTimer.setTextColor(COLOR_TEXT_PRIMARY);
		tvBuildTimer.setTextSize(11);
		tvBuildTimer.setTypeface(android.graphics.Typeface.MONOSPACE);
		tvBuildTimer.setPadding(dp(6), 0, dp(6), 0);
		logHeader.addView(tvBuildTimer);

		tvErrorSummary = new TextView(this);
		tvErrorSummary.setText("أخطاء البناء: 0");
		tvErrorSummary.setTextColor(COLOR_TEXT_PRIMARY);
		tvErrorSummary.setTextSize(10);
		tvErrorSummary.setPadding(dp(8), 0, dp(8), 0);
		logHeader.addView(tvErrorSummary);

		Button btnOpenGithub = new Button(this);
		btnOpenGithub.setText("GitHub");
		btnOpenGithub.setTextSize(10);
		btnOpenGithub.setPadding(dp(10), dp(5), dp(10), dp(5));
		btnOpenGithub.setMinHeight(dp(34));
		btnOpenGithub.setBackground(UiStyle.rounded(this, COLOR_SURFACE_ALT, 9, 0xFF30363D, 1));
		btnOpenGithub.setTextColor(COLOR_TEXT_PRIMARY);
		btnOpenGithub.setAllCaps(false);
		btnOpenGithub.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { openBuildOnGitHub(); }
		});
		logHeader.addView(btnOpenGithub);

		Button btnClearLogs = new Button(this);
		btnClearLogs.setText("مسح");
		btnClearLogs.setTextSize(10);
		btnClearLogs.setPadding(dp(12), dp(5), dp(12), dp(5));
		btnClearLogs.setMinHeight(dp(34));
		btnClearLogs.setBackground(UiStyle.rounded(this, COLOR_SURFACE_ALT, 9, 0xFF30363D, 1));
		btnClearLogs.setTextColor(COLOR_TEXT_PRIMARY);
		btnClearLogs.setAllCaps(false);
		btnClearLogs.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { clearLogPanel(); }
		});
		logHeader.addView(btnClearLogs);

		Button btnCopyLogs = new Button(this);
		btnCopyLogs.setText("نسخ");
		btnCopyLogs.setTextSize(10);
		btnCopyLogs.setPadding(dp(12), dp(5), dp(12), dp(5));
		btnCopyLogs.setMinHeight(dp(34));
		btnCopyLogs.setBackground(UiStyle.rounded(this, COLOR_SURFACE_ALT, 9, 0xFF30363D, 1));
		btnCopyLogs.setTextColor(COLOR_TEXT_PRIMARY);
		btnCopyLogs.setAllCaps(false);
		btnCopyLogs.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				String text = buildStepsText();
				android.content.ClipboardManager clipboard =
						(android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				android.content.ClipData clip = android.content.ClipData.newPlainText("steps", text);
				clipboard.setPrimaryClip(clip);
				Toast.makeText(MainActivity.this, "تم نسخ قائمة الخطوات", Toast.LENGTH_SHORT).show();
			}
		});
		logHeader.addView(btnCopyLogs);

		logPanel.addView(logHeader);

		uploadCounterBox = new LinearLayout(this);
		uploadCounterBox.setOrientation(LinearLayout.HORIZONTAL);
		uploadCounterBox.setPadding(dp(14), dp(10), dp(14), dp(10));
		uploadCounterBox.setGravity(Gravity.CENTER_VERTICAL);
		uploadCounterBox.setBackgroundColor(COLOR_BACKGROUND);
		uploadCounterBox.setVisibility(View.GONE);

		tvUploadCounter = new TextView(this);
		tvUploadCounter.setText("");
		tvUploadCounter.setTextColor(COLOR_TEXT_PRIMARY);
		tvUploadCounter.setTextSize(13);
		tvUploadCounter.setTypeface(android.graphics.Typeface.MONOSPACE);
		uploadCounterBox.addView(tvUploadCounter);

		logPanel.addView(uploadCounterBox, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		rvSteps = new RecyclerView(this);
		rvSteps.setBackgroundColor(COLOR_BACKGROUND);
		rvSteps.setPadding(0, dp(6), 0, dp(6));
		rvSteps.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
		rvSteps.setLayoutManager(new LinearLayoutManager(this));
		rvSteps.setItemAnimator(new DefaultItemAnimator());
		rvSteps.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
		rvSteps.setTextDirection(View.TEXT_DIRECTION_LTR);
		stepsAdapter = new StepsAdapter();
		rvSteps.setAdapter(stepsAdapter);
		logPanel.addView(rvSteps);

		tvLogs = new TextView(this);
		tvLogs.setTextColor(COLOR_TEXT_PRIMARY);
		tvLogs.setTextSize(10);
		tvLogs.setTypeface(android.graphics.Typeface.MONOSPACE);
		tvLogs.setPadding(dp(12), dp(8), dp(12), dp(8));
		tvLogs.setVisibility(View.GONE);
		tvLogs.setMovementMethod(LinkMovementMethod.getInstance());
		tvLogs.setHighlightColor(0x553F8CFF);
		logPanel.addView(tvLogs, new LinearLayout.LayoutParams(0, 0));
	}

	private Button createDrawerButton(String title, boolean primary) {
		Button button = new Button(this);
		button.setText(title);
		button.setTextColor(COLOR_TEXT_PRIMARY);
		button.setTextSize(12);
		button.setAllCaps(false);
		button.setMinHeight(0);
		button.setMinimumHeight(0);
		button.setMinWidth(0);
		button.setMinimumWidth(0);
		button.setPadding(dp(6), 0, dp(6), 0);
		button.setBackground(UiStyle.rounded(this,
				primary ? COLOR_PRIMARY : COLOR_SURFACE_ALT,
				10,
				primary ? 0xFF58A6FF : 0xFF30363D,
				1));
		return button;
	}

	// ============================================================
	// فتح GitHub
	// ============================================================
	private void openBuildOnGitHub() {
		if (currentProjectDir == null || !currentProjectDir.isDirectory()) {
			Toast.makeText(this, "افتح مشروعاً أولاً", Toast.LENGTH_SHORT).show();
			return;
		}
		String savedRepoName = prefs.getString("repo_" + currentProjectDir.getName(), "");
		if (savedRepoName.isEmpty()) { showNotBuiltYetDialog(); return; }
		String username = AuthManager.getInstance(this).getGithubUsername();
		if (username == null || username.trim().isEmpty()) { fetchUsernameAndOpen(savedRepoName); return; }
		showGitHubDialog(username, savedRepoName);
	}

	private void showNotBuiltYetDialog() {
		new AlertDialog.Builder(this)
				.setTitle("المشروع لم يُبنَ بعد")
				.setMessage("لم يتم بناء هذا المشروع على GitHub بعد.\n\n"
						+ "يمكنك:\n"
						+ "• بناء APK الآن (لإنشاء المستودع)\n"
						+ "• فتح صفحة GitHub المتوقعة (قد لا تعمل)")
				.setPositiveButton("بناء APK", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int w) { buildApk(); }
				})
				.setNeutralButton("فتح الرابط المتوقع", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int w) { openGitHubWithGuessedName(); }
				})
				.setNegativeButton("إلغاء", null)
				.show();
	}

	private void openGitHubWithGuessedName() {
		if (currentProjectDir == null) return;
		String appName = prefs.getString("name_" + currentProjectDir.getName(), currentProjectDir.getName());
		final String guessedRepoName = repositoryNameFor(appName);
		String username = AuthManager.getInstance(this).getGithubUsername();
		if (username == null || username.trim().isEmpty()) { fetchUsernameAndOpen(guessedRepoName); return; }
		showGitHubDialog(username, guessedRepoName);
	}

	private void fetchUsernameAndOpen(final String repoName) {
		String token = new SecureStore(this).readGitHubToken();
		if (token == null || token.trim().isEmpty()) {
			Toast.makeText(this, "لم يتم الربط بحساب GitHub. أعد تسجيل الدخول", Toast.LENGTH_LONG).show();
			return;
		}
		Toast.makeText(this, "جارٍ التعرف على حسابك...", Toast.LENGTH_SHORT).show();
		GitHubApi.getUser(token, new GitHubApi.Callback<String>() {
			@Override public void onSuccess(final String username) {
				if (username == null || username.trim().isEmpty()) {
					runOnUiThread(new Runnable() {
						@Override public void run() {
							Toast.makeText(MainActivity.this, "تعذر التعرف على اسم الحساب", Toast.LENGTH_LONG).show();
						}
					});
					return;
				}
				new SecureStore(MainActivity.this).saveGitHubUsername(username);
				AuthManager.getInstance(MainActivity.this).saveGithubUsername(username);
				runOnUiThread(new Runnable() {
					@Override public void run() { showGitHubDialog(username, repoName); }
				});
			}
			@Override public void onError(final String message) {
				runOnUiThread(new Runnable() {
					@Override public void run() {
						Toast.makeText(MainActivity.this, "تعذر التعرف على حسابك:\n" + message, Toast.LENGTH_LONG).show();
					}
				});
			}
		});
	}

	private void showGitHubDialog(final String username, final String repoName) {
		final String url = "https://github.com/" + username + "/" + repoName + "/actions";
		new AlertDialog.Builder(this)
				.setTitle("فتح GitHub Actions")
				.setMessage("سيتم فتح صفحة البناء على GitHub:\n\n" + url
						+ "\n\nملاحظة: إذا كان المستودع لم يُنشأ بعد، ستحتاج بناء المشروع أولاً.")
				.setPositiveButton("فتح", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int w) {
						try {
							Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
							startActivity(intent);
						} catch (Exception e) {
							Toast.makeText(MainActivity.this, "تعذر فتح الرابط: " + e.getMessage(), Toast.LENGTH_SHORT).show();
						}
					}
				})
				.setNegativeButton("إلغاء", null)
				.show();
	}

	private String repositoryNameFor(String raw) {
		if (raw == null) return "flutter-app";
		String name = raw.trim();
		if (name.isEmpty()) return "flutter-app";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			boolean isArabic = (c >= '\u0600' && c <= '\u06FF')
					|| (c >= '\u0750' && c <= '\u077F')
					|| (c >= '\uFB50' && c <= '\uFDFF')
					|| (c >= '\uFE70' && c <= '\uFEFF');
			boolean isLatinDigit = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
					|| (c >= '0' && c <= '9');
			boolean isSafe = c == '-' || c == '_' || c == '.';
			if (isArabic || isLatinDigit || isSafe) sb.append(c);
			else if (c == ' ') sb.append('-');
		}
		String result = sb.toString();
		while (result.startsWith("-") || result.startsWith(".")) result = result.substring(1);
		while (result.endsWith("-") || result.endsWith(".")) result = result.substring(0, result.length() - 1);
		if (result.isEmpty()) result = "flutter-app";
		return result.length() > 90 ? result.substring(0, 90) : result;
	}

	private void openAiChat() {
		if (currentProjectDir == null || !currentProjectDir.isDirectory()) {
			Toast.makeText(this, "افتح مشروعًا أولًا", Toast.LENGTH_SHORT).show();
			return;
		}
		saveAllOpenTabs();
		reloadFileAfterAi = true;
		Intent intent = new Intent(this, AiChatActivity.class);
		intent.putExtra(AiChatActivity.EXTRA_PROJECT_DIR, currentProjectDir.getAbsolutePath());
		if (currentOpenFile != null) {
			try {
				String rootPath = currentProjectDir.getCanonicalPath();
				String filePath = currentOpenFile.getCanonicalPath();
				String relative = filePath.startsWith(rootPath) ? filePath.substring(rootPath.length()) : filePath;
				while (relative.startsWith(File.separator)) relative = relative.substring(1);
				intent.putExtra(AiChatActivity.EXTRA_CURRENT_FILE, relative.replace(File.separatorChar, '/'));
			} catch (Exception ignored) { }
		}
		startActivity(intent);
	}

	private void showProjectSettings() {
		if (currentProjectDir == null) {
			Toast.makeText(this, "لا يوجد مشروع مفتوح", Toast.LENGTH_SHORT).show();
			return;
		}
		settingsDialog = new ProjectSettingsDialog(this, currentProjectDir, new Runnable() {
			@Override public void run() {
				String folderName = currentProjectDir.getName();
				String appName = prefs.getString("name_" + folderName, folderName);
				String packageName = prefs.getString("package_" + folderName, "");
				String apiLevel = prefs.getString("api_" + folderName, "");
				tvProjectInfo.setText("المشروع: " + appName + " | الحزمة: " + packageName + " | API: " + apiLevel);
				invalidateProjectIconCache(currentProjectDir);
			}
		});
		settingsDialog.show();
	}

	private int getLogPanelMaxHeight() {
		int parentHeight = 0;
		if (logPanel != null && logPanel.getParent() instanceof View) {
			parentHeight = ((View) logPanel.getParent()).getHeight();
		}
		if (parentHeight <= 0) parentHeight = getResources().getDisplayMetrics().heightPixels;
		return Math.max(logPanelCollapsedHeight, parentHeight - dp(44));
	}

	private void setupDraggableLogPanel() {
		logPanelExpandedHeight = getLogPanelMaxHeight();
		logDragHandle.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						startY = event.getRawY();
						startHeight = logPanel.getLayoutParams().height;
						if (startHeight <= 0) startHeight = logPanelCollapsedHeight;
						logPanelExpandedHeight = getLogPanelMaxHeight();
						return true;
					case MotionEvent.ACTION_MOVE:
						float deltaY = event.getRawY() - startY;
						int newHeight = (int) (startHeight - deltaY);
						int maxHeight = getLogPanelMaxHeight();
						if (newHeight < logPanelCollapsedHeight) { newHeight = logPanelCollapsedHeight; }
						else if (newHeight > maxHeight) { newHeight = maxHeight; }
						logPanelExpandedHeight = maxHeight;
						ViewGroup.LayoutParams layoutParams = logPanel.getLayoutParams();
						layoutParams.height = newHeight;
						logPanel.setLayoutParams(layoutParams);
						isLogPanelExpanded = (newHeight > logPanelCollapsedHeight + 20);
						updateDragIcon();
						return true;
					case MotionEvent.ACTION_UP:
						int currentHeight = logPanel.getLayoutParams().height;
						logPanelExpandedHeight = getLogPanelMaxHeight();
						int middleHeight = (logPanelCollapsedHeight + logPanelExpandedHeight) / 2;
						int targetHeight;
						if (currentHeight >= middleHeight) { targetHeight = logPanelExpandedHeight; isLogPanelExpanded = true; }
						else { targetHeight = logPanelCollapsedHeight; isLogPanelExpanded = false; }
						ViewGroup.LayoutParams finalParams = logPanel.getLayoutParams();
						finalParams.height = targetHeight;
						logPanel.setLayoutParams(finalParams);
						updateDragIcon();
						return true;
				}
				return false;
			}
		});
	}

	private void updateDragIcon() {
		if (dragIcon != null) {
			if (isLogPanelExpanded) dragIcon.setText("▼");
			else dragIcon.setText("▲");
		}
	}

	private void showSettingsDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("الإعدادات");
		String[] options = {
				"سياسة الخصوصية",
				"تواصل مع المطور",
				"حول التطبيق",
				"إعدادات المحرر",
				"إدارة الذاكرة المؤقتة",
				"تسجيل الخروج"
		};
		builder.setAdapter(UiStyle.dialogListAdapter(this, options), new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				switch (which) {
					case 0: showPrivacyPolicy(); break;
					case 1: showContactInfo(); break;
					case 2: showAboutApp(); break;
					case 3: showEditorSettings(); break;
					case 4: showCacheManager(); break;
					case 5: confirmLogout(); break;
				}
			}
		});
		builder.setNegativeButton("إغلاق", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void confirmLogout() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("تسجيل الخروج");
		builder.setMessage(
				"هل أنت متأكد أنك تريد تسجيل الخروج؟\n\n"
				+ "ملاحظات:\n"
				+ "• سيتم حذف مفتاح GitHub المحفوظ من هذا الجهاز\n"
				+ "• ستبقى مشاريعك وملفاتك كما هي\n"
				+ "• ستحتاج تسجيل الدخول من جديد للبناء على GitHub\n"
				+ "• لن يتم حذف أي شيء من حساب GitHub الخاص بك"
		);
		builder.setPositiveButton("تسجيل الخروج", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				performLogout();
			}
		});
		builder.setNegativeButton("إلغاء", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void performLogout() {
		try {
			new SecureStore(this).clearGitHubToken();
			AuthManager.getInstance(this).logout();

			for (Bitmap bmp : projectIconCache.values()) {
				if (bmp != null && !bmp.isRecycled()) bmp.recycle();
			}
			projectIconCache.clear();

			Toast.makeText(this, "تم تسجيل الخروج بنجاح", Toast.LENGTH_SHORT).show();

			Intent intent = new Intent(this, LoginActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(intent);
			finish();

		} catch (Exception e) {
			Toast.makeText(this, "خطأ أثناء تسجيل الخروج: " + e.getMessage(),
					Toast.LENGTH_LONG).show();
		}
	}

	private void showCacheManager() {
		final long apkIconsSize = getDirSize(new File(getCacheDir(), "apk_icons"));
		final long templateIconsSize = getDirSize(new File(getCacheDir(), "template_icons"));
		final long templatePreviewsSize = getDirSize(new File(getCacheDir(), "template_previews"));
		final long showcaseSize = getDirSize(new File(getCacheDir(), "template_showcase"));
		final long totalSize = apkIconsSize + templateIconsSize + templatePreviewsSize + showcaseSize;

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(20), dp(16), dp(20), dp(16));
		root.setBackgroundColor(COLOR_BACKGROUND);

		TextView title = new TextView(this);
		title.setText("إدارة الذاكرة المؤقتة");
		title.setTextSize(17);
		title.setTextColor(COLOR_TEXT_PRIMARY);
		title.setTypeface(null, android.graphics.Typeface.BOLD);
		root.addView(title);

		TextView desc = new TextView(this);
		desc.setText("التطبيق يخزّن أيقونات ومعاينات لتسريع الفتح. يمكنك حذفها لتحرير مساحة، وسيُعاد إنشاؤها عند الحاجة.");
		desc.setTextSize(12);
		desc.setTextColor(COLOR_TEXT_SECONDARY);
		desc.setPadding(0, dp(8), 0, dp(16));
		desc.setLineSpacing(0, 1.15f);
		root.addView(desc);

		TextView totalView = new TextView(this);
		totalView.setText("الإجمالي: " + formatBytes(totalSize));
		totalView.setTextSize(15);
		totalView.setTextColor(COLOR_PRIMARY);
		totalView.setTypeface(null, android.graphics.Typeface.BOLD);
		totalView.setPadding(0, 0, 0, dp(12));
		root.addView(totalView);

		root.addView(buildCacheRow("أيقونات التطبيقات المبنية", apkIconsSize));
		root.addView(buildCacheRow("أيقونات القوالب", templateIconsSize));
		root.addView(buildCacheRow("معاينات القوالب", templatePreviewsSize));
		root.addView(buildCacheRow("ملفات مؤقتة للقوالب", showcaseSize));

		View spacer = new View(this);
		spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(12)));
		root.addView(spacer);

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(root)
				.setPositiveButton("حذف الكل", null)
				.setNegativeButton("إغلاق", null)
				.create();

		dialog.setOnShowListener(d -> {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
				confirmClearCache(dialog, totalSize);
			});
		});

		dialog.show();
		styleDialog(dialog);
	}

	private LinearLayout buildCacheRow(String label, long size) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setPadding(0, dp(8), 0, dp(8));

		TextView labelView = new TextView(this);
		labelView.setText(label);
		labelView.setTextSize(13);
		labelView.setTextColor(COLOR_TEXT_PRIMARY);
		row.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView sizeView = new TextView(this);
		sizeView.setText(formatBytes(size));
		sizeView.setTextSize(13);
		sizeView.setTextColor(size > 0 ? COLOR_PRIMARY : COLOR_TEXT_SECONDARY);
		sizeView.setTypeface(android.graphics.Typeface.MONOSPACE);
		row.addView(sizeView);

		return row;
	}

	private void confirmClearCache(final AlertDialog parentDialog, final long size) {
		String sizeText = formatBytes(size);
		new AlertDialog.Builder(this)
				.setTitle("تأكيد الحذف")
				.setMessage("سيتم حذف " + sizeText + " من الذاكرة المؤقتة.\n\n"
						+ "الأيقونات والمعاينات ستُعاد إنشاؤها عند فتح التطبيقات والقوالب مرة أخرى.\n\n"
						+ "لا يتم حذف أي مشاريع أو ملفات أو APK.")
				.setPositiveButton("حذف", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int which) {
						performClearCache();
						if (parentDialog != null && parentDialog.isShowing()) parentDialog.dismiss();
						Toast.makeText(MainActivity.this,
								"تم حذف الذاكرة المؤقتة (" + sizeText + ")",
								Toast.LENGTH_LONG).show();
					}
				})
				.setNegativeButton("إلغاء", null)
				.show();
	}

	private void performClearCache() {
		try {
			for (Bitmap bmp : projectIconCache.values()) {
				if (bmp != null && !bmp.isRecycled()) bmp.recycle();
			}
			projectIconCache.clear();

			deleteRecursive(new File(getCacheDir(), "apk_icons"));
			deleteRecursive(new File(getCacheDir(), "template_icons"));
			deleteRecursive(new File(getCacheDir(), "template_previews"));
			deleteRecursive(new File(getCacheDir(), "template_showcase"));
		} catch (Exception e) {
			Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private long getDirSize(File dir) {
		if (dir == null || !dir.exists()) return 0;
		if (dir.isFile()) return dir.length();
		long size = 0;
		File[] children = dir.listFiles();
		if (children != null) {
			for (File child : children) size += getDirSize(child);
		}
		return size;
	}

	private String formatBytes(long bytes) {
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
		if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
		return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
	}

	// ============================================================
	// سياسة الخصوصية
	// ============================================================
	private void showPrivacyPolicy() {
		String privacyText = buildPrivacyPolicyText();

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("سياسة الخصوصية");

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(16), dp(12), dp(16), dp(12));
		root.setBackgroundColor(COLOR_BACKGROUND);

		TextView textView = new TextView(this);
		textView.setText(privacyText);
		textView.setTextColor(COLOR_TEXT_PRIMARY);
		textView.setTextSize(13);
		textView.setLineSpacing(0, 1.15f);
		textView.setTextIsSelectable(true);
		textView.setPadding(dp(12), dp(12), dp(12), dp(12));
		textView.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, COLOR_BORDER, 1));

		ScrollView scrollView = new ScrollView(this);
		scrollView.setBackgroundColor(COLOR_BACKGROUND);
		scrollView.addView(textView, new ScrollView.LayoutParams(-1, -2));
		root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

		builder.setView(root);
		builder.setPositiveButton("موافق", null);
		builder.setNeutralButton("نسخ النص", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				android.content.ClipboardManager cm = (android.content.ClipboardManager)
						getSystemService(Context.CLIPBOARD_SERVICE);
				cm.setPrimaryClip(android.content.ClipData.newPlainText("privacy", buildPrivacyPolicyText()));
				Toast.makeText(MainActivity.this, "تم نسخ سياسة الخصوصية", Toast.LENGTH_SHORT).show();
			}
		});

		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);

		if (dialog.getWindow() != null) {
			int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95);
			int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
			dialog.getWindow().setLayout(width, height);
		}
	}

	private String buildPrivacyPolicyText() {
		StringBuilder sb = new StringBuilder();

		sb.append("سياسة الخصوصية — Klencod IDE\n");
		sb.append("آخر تحديث: 2026\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("مقدمة وقبول\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("نحن في Klencod IDE نحترم خصوصيتك ونأخذها على محمل الجدية. توضّح هذه السياسة بالتفصيل:\n");
		sb.append("• ما البيانات التي نجمعها (وما لا نجمعها)\n");
		sb.append("• كيف نستخدمها ونحميها\n");
		sb.append("• مع من نشاركها (إن لزم)\n");
		sb.append("• حقوقك الكاملة تجاه بياناتك\n\n");
		sb.append("باستخدامك للتطبيق، أنت توافق على هذه السياسة. إذا لم توافق على أي جزء منها، يرجى عدم استخدام التطبيق.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("١. البيانات التي نجمعها\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("التطبيق مصمّم ليعمل محليًا بالكامل. لا نجمع أيًا من البيانات التالية:\n");
		sb.append("• الاسم أو البريد الإلكتروني\n");
		sb.append("• رقم الهاتف أو العنوان\n");
		sb.append("• جهات الاتصال أو الصور أو الملفات الشخصية\n");
		sb.append("• الموقع الجغرافي\n");
		sb.append("• معلومات الجهاز الحساسة (IMEI, MAC)\n");
		sb.append("• سجل الاستخدام أو التحليلات (Analytics)\n\n");
		sb.append("كل بياناتك تُخزَّن على جهازك فقط ولا تُرسَل إلى خوادمنا لأننا لا نملك خوادم. المشروع بالكامل يعمل بدون اتصال (Offline-First).\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٢. البيانات التي لا نجمعها أبدًا\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("نؤكد بشكل قاطع أننا لا نجمع ولا نحتفظ بـ:\n");
		sb.append("• كلمات المرور\n");
		sb.append("• بيانات بطاقات الائتمان\n");
		sb.append("• سجل التصفح\n");
		sb.append("• تفاصيل الاستخدام\n");
		sb.append("• إحصائيات فتح/إغلاق التطبيق\n\n");
		sb.append("أي معلومة تراها في التطبيق (مشاريع، ملفات، إعدادات) محفوظة في مساحة التخزين الداخلية للتطبيق على جهازك.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٣. البيانات المرسلة لأطراف ثالثة\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("ملاحظة مهمة: على الرغم من أننا لا نجمع بياناتك، إلا أن بعض الميزات تتطلب إرسال بيانات لأطراف ثالثة تختارها أنت بنفسك.\n\n");
		sb.append("أ) GitHub (لبناء APK)\n\n");
		sb.append("عند استخدامك ميزة \"بناء APK\":\n");
		sb.append("• تُرفَع ملفات مشروعك إلى مستودع GitHub خاص بك\n");
		sb.append("• يُشغَّل GitHub Actions لبناء التطبيق\n");
		sb.append("• البيانات تخضع لسياسة خصوصية GitHub:\n");
		sb.append("  https://docs.github.com/site-policy/privacy-policies\n\n");
		sb.append("ب) مزودو الذكاء الاصطناعي (AI)\n\n");
		sb.append("عند استخدامك مساعد AI، يُرسَل:\n");
		sb.append("• نص طلبك (Prompt)\n");
		sb.append("• محتوى الملفات المرفقة أو المفتوحة\n");
		sb.append("• سياق المحادثة\n\n");
		sb.append("إلى المزود الذي اخترته:\n");
		sb.append("• OpenAI (https://openai.com/privacy)\n");
		sb.append("• Google Gemini (https://policies.google.com/privacy)\n");
		sb.append("• OpenRouter (https://openrouter.ai/privacy)\n\n");
		sb.append("لا ترسل أي معلومات شخصية أو حساسة إلى مساعد AI. راجع سياسة الخصوصية لكل مزود.\n\n");
		sb.append("ج) الروابط الخارجية\n\n");
		sb.append("التطبيق قد يحتوي روابط لمواقع خارجية. لا نتحمل مسؤولية محتوى أو سياسات هذه المواقع. راجع سياساتها قبل الاستخدام.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٤. تخزين البيانات وأمنها\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("أ) ما يُخزَّن محليًا على جهازك:\n");
		sb.append("• المشاريع والملفات: في مساحة التطبيق الخاصة\n");
		sb.append("• الإعدادات: SharedPreferences عادية\n");
		sb.append("• المفاتيح الحساسة (GitHub Token، API Keys): مشفّرة باستخدام Android Keystore + AES-256-GCM\n");
		sb.append("• المحادثات مع AI: مشفّرة ضمن مساحة التطبيق\n\n");
		sb.append("ب) ما لا يُخزَّن على الإطلاق:\n");
		sb.append("• كلمات مرور GitHub (نستخدم Device Flow فقط)\n");
		sb.append("• بيانات شخصية\n");
		sb.append("• أي شيء على خوادمنا (لا نملك خوادم)\n\n");
		sb.append("ج) أمن التخزين:\n");
		sb.append("• المفاتيح مشفّرة على مستوى النظام\n");
		sb.append("• لا يمكن لتطبيق آخر قراءتها\n");
		sb.append("• عند إلغاء التطبيق، تُحذف كل البيانات تلقائيًا\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٥. الذكاء الاصطناعي\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("قسم مهم.\n\n");
		sb.append("أ) كيف يعمل:\n");
		sb.append("• أنت تختار المزود (OpenAI/Gemini/OpenRouter)\n");
		sb.append("• أنت تُدخل مفتاح API الخاص بك\n");
		sb.append("• الطلبات تُرسَل مباشرة من جهازك للمزود\n");
		sb.append("• نحن لا نرى أو نحتفظ بأي شيء\n\n");
		sb.append("ب) مسؤوليتك:\n");
		sb.append("• لا ترسل أسرارًا (كلمات مرور، مفاتيح، رموز)\n");
		sb.append("• لا ترسل بيانات شخصية للآخرين\n");
		sb.append("• راجع سياسة المزود قبل الاستخدام\n\n");
		sb.append("ج) اقتراحات AI:\n");
		sb.append("• قد يقدّم AI اقتراحات خاطئة أو مضللة\n");
		sb.append("• راجع كل تعديل قبل تطبيقه\n");
		sb.append("• نحن غير مسؤولين عن اقتراحات AI\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٦. GitHub Actions\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("عند استخدام ميزة البناء:\n\n");
		sb.append("أ) ما يحدث:\n");
		sb.append("• ربط آمن بحسابك عبر OAuth Device Flow\n");
		sb.append("• لا نحتفظ بكلمة مرور GitHub أبدًا\n");
		sb.append("• الـ Token محفوظ مشفّرًا على جهازك\n");
		sb.append("• ملفات المشروع تُرفَع لمستودعك الخاص\n\n");
		sb.append("ب) ما لا نفعله:\n");
		sb.append("• لا نرى ملفاتك أو مشاريعك\n");
		sb.append("• لا نصل لمستودعاتك الأخرى\n");
		sb.append("• لا نشارك أي شيء مع طرف ثالث\n\n");
		sb.append("ج) تحذير:\n");
		sb.append("• البناء العام (Public) يعني أن الكود مرئي للجميع\n");
		sb.append("• إذا كان مشروعك يحتوي أسرارًا، استخدم Private Repo\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٧. القوالب وحقوق الملكية\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("يوفر التطبيق مجموعة من القوالب الجاهزة لتسهيل إنشاء مشاريع Flutter. القوالب مصممة ومطوّرة بواسطة Klencod IDE، وهي مُتاحة للمستخدم وفق الشروط التالية.\n\n");

		sb.append("أ) الملكية:\n");
		sb.append("• جميع القوالب المتوفرة داخل التطبيق ملك حصري لـ Klencod IDE.\n");
		sb.append("• القوالب محمية بحقوق النشر © 2026 Klencod IDE.\n");
		sb.append("• لا يجوز المطالبة بملكيتها أو نسبتها لمطور آخر.\n\n");

		sb.append("ب) الاستخدام المسموح:\n");
		sb.append("• استخدام القوالب لإنشاء مشاريع شخصية.\n");
		sb.append("• استخدام القوالب لإنشاء مشاريع تجارية.\n");
		sb.append("• تعديل القالب أو إضافة خصائص وميزات جديدة عليه.\n");
		sb.append("• نشر المشروع الناتج على متاجر التطبيقات كمنتجك الخاص بعد التعديل.\n");
		sb.append("• الاحتفاظ بمشروعك ومشاركته بحرية بعد إجراء التعديلات.\n\n");

		sb.append("ج) الاستخدام غير المسموح:\n");
		sb.append("• نشر القالب كما هو تمامًا في أي متجر تطبيقات.\n");
		sb.append("• إعادة بيع القالب نفسه بشكل منفصل.\n");
		sb.append("• توزيع القالب أو مشاركته كملف مستقل.\n");
		sb.append("• نسب القالب إلى مطور آخر غير Klencod IDE.\n");
		sb.append("• المطالبة بأنك المؤلف الأصلي للقالب.\n");
		sb.append("• استخدام القالب في منتج لا يحتوي على أي تعديل جوهري عليه.\n\n");

		sb.append("د) شرط التعديل الجوهري:\n");
		sb.append("عند استخدام أي قالب من قوالب Klencod IDE، يجب إضافة تعديل جوهري واحد على الأقل، لتفادي التشابه الكامل مع القالب الأصلي. هذا الشرط ينطبق على كل استخدام، سواء كان شخصيًا أو تجاريًا.\n\n");
		sb.append("التعديل الجوهري يشمل:\n");
		sb.append("• إضافة شاشة أو صفحة جديدة.\n");
		sb.append("• إضافة خاصية أو ميزة جديدة.\n");
		sb.append("• تغيير التصميم أو الألوان بشكل معتبر.\n");
		sb.append("• إضافة وظيفة جديدة أو دمج خدمة خارجية.\n");
		sb.append("• تغيير بنية المشروع بشكل يُميّزه عن القالب الأصلي.\n\n");
		sb.append("تعديل بسيط كتغيير الاسم أو الأيقونة فقط لا يُعتبر تعديلًا جوهريًا.\n\n");

		sb.append("هـ) الإفصاح عن المصدر:\n");
		sb.append("لا يُشترط الإفصاح عن مصدر القالب الأصلي في مشروعك، لكنه موضع تقدير. الشرط الأساسي هو عدم نشر القالب كما هو أو نسبته لمطور آخر.\n\n");

		sb.append("و) العواقب:\n");
		sb.append("في حال ثبوت مخالفة شروط استخدام القوالب، يحتفظ مطوّر التطبيق بالحق في اتخاذ الإجراءات القانونية المناسبة.\n\n");

		sb.append("ز) الإبلاغ عن انتهاكات:\n");
		sb.append("إذا لاحظت استخدامًا مخالفًا لقوالب Klencod IDE، يُرجى الإبلاغ عبر البريد الإلكتروني:\n");
		sb.append("mm2007mm19h@gmail.com\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٨. حقوق الملكية الفكرية للتطبيق\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("أ) ملكية التطبيق:\n");
		sb.append("• التطبيق (الاسم، الشعار، الكود) ملك للمطوّر.\n");
		sb.append("• محمي بحقوق النشر © 2026 Klencod IDE.\n");
		sb.append("• لا يجوز نسخ التطبيق أو إعادة توزيعه دون إذن مسبق.\n\n");
		sb.append("ب) ملكية مشاريعك:\n");
		sb.append("• كل مشروع تنشئه أنت ملكك بالكامل.\n");
		sb.append("• لا نطالب بأي حقوق على مشاريعك.\n");
		sb.append("• يمكنك استخدامها تجاريًا كما تشاء.\n\n");
		sb.append("ج) البيانات المدمجة:\n");
		sb.append("• قوائم الاقتراحات التلقائية (Dart/Flutter) للاستخدام الشخصي فقط.\n");
		sb.append("• القوالب الجاهزة تخضع للشروط الواردة في القسم السابع.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("٩. حقوقك\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("لديك الحق الكامل في:\n");
		sb.append("• الوصول لبياناتك في أي وقت (موجودة على جهازك).\n");
		sb.append("• تعديل أي ملف يدويًا من داخل التطبيق.\n");
		sb.append("• حذف أي مشروع في أي وقت.\n");
		sb.append("• حذف كل البيانات بإلغاء تثبيت التطبيق.\n");
		sb.append("• سحب المفاتيح (GitHub، AI) من الإعدادات.\n");
		sb.append("• تصدير مشاريعك كملفات ZIP.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("١٠. حدود المسؤولية\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("التطبيق يُقدَّم \"كما هو\" (As-Is) دون أي ضمانات صريحة أو ضمنية.\n\n");
		sb.append("أ) لا نتحمل مسؤولية:\n");
		sb.append("• أي أخطاء في المشاريع التي تنشئها.\n");
		sb.append("• أي خسارة بيانات (احتفظ بنسخ احتياطية).\n");
		sb.append("• أي توقف أو فشل في GitHub Actions أو AI.\n");
		sb.append("• أي أضرار مباشرة أو غير مباشرة من استخدام التطبيق.\n");
		sb.append("• اقتراحات الذكاء الاصطناعي (راجعها قبل التطبيق).\n");
		sb.append("• أي محتوى أو كود تضعه في مشاريعك.\n\n");
		sb.append("ب) أنت المسؤول عن:\n");
		sb.append("• مشاريعك وملفاتك ومحتواها.\n");
		sb.append("• مراجعة أي كود قبل استخدامه.\n");
		sb.append("• تحديث نسخ احتياطية.\n");
		sb.append("• الالتزام بسياسات GitHub ومزودي AI.\n");
		sb.append("• احترام شروط استخدام القوالب الواردة في القسم السابع.\n\n");
		sb.append("ج) الضمان الوحيد:\n");
		sb.append("نحن نُقدّم التطبيق بأفضل جهد ممكن، دون وعد بنتائج محددة.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("١١. حذف البيانات\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("يمكنك حذف بياناتك عبر:\n");
		sb.append("• حذف مشروع فردي: الضغط الطويل > حذف.\n");
		sb.append("• حذف محادثة AI: زر \"مسح\".\n");
		sb.append("• حذف مفتاح: الإعدادات > حذف مفتاح.\n");
		sb.append("• حذف كل شيء: إلغاء تثبيت التطبيق.\n\n");
		sb.append("عند إلغاء التثبيت:\n");
		sb.append("• كل الملفات والمشاريع تُحذَف.\n");
		sb.append("• المفاتيح المشفّرة تُحذَف.\n");
		sb.append("• المحادثات تُحذَف.\n");
		sb.append("• لا يبقى أي أثر.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("١٢. الأذونات المطلوبة\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("نطلب فقط الأذونات الضرورية للعمل:\n");
		sb.append("• INTERNET: للاتصال بـ GitHub و AI.\n");
		sb.append("• ACCESS_NETWORK_STATE: لمعرفة حالة الشبكة.\n");
		sb.append("• POST_NOTIFICATIONS: لإشعارات البناء.\n");
		sb.append("• READ/WRITE_EXTERNAL_STORAGE: لتصدير ZIP (اختياري).\n");
		sb.append("• REQUEST_INSTALL_PACKAGES: لتثبيت APK (اختياري).\n\n");
		sb.append("لا نطلب أي إذن آخر. يمكنك رفض أي إذن اختياري وسيعمل التطبيق بميزات محدودة.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("١٣. مجاني، بلا إعلانات\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("التطبيق مجاني بالكامل، ولا نعرض أي إعلانات.\n\n");
		sb.append("أ) ما لا يوجد:\n");
		sb.append("• لا مشتريات داخلية.\n");
		sb.append("• لا اشتراكات.\n");
		sb.append("• لا إعلانات.\n");
		sb.append("• لا تتبع لأغراض تسويقية.\n\n");
		sb.append("ب) التكاليف الخارجية (اختيارية):\n");
		sb.append("• GitHub Actions الخاص (Private): يدفعها المستخدم مباشرة لـ GitHub.\n");
		sb.append("• مزودو AI: يدفعها المستخدم مباشرة للمزود.\n\n");
		sb.append("ج) لا نشارك بياناتك مع أي مُعلن أو مسوّق.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("١٤. تغييرات السياسة\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("قد نحدّث هذه السياسة من وقت لآخر. سنُعلمك عبر:\n");
		sb.append("• إشعار داخل التطبيق عند أول تشغيل بعد التحديث.\n");
		sb.append("• تحديث التاريخ في الأعلى.\n\n");
		sb.append("استمرارك في استخدام التطبيق بعد التحديث يعني موافقتك على النسخة الجديدة.\n\n\n");

		// ═══════════════════════════════════════════════════════════════
		sb.append("١٥. تواصل معنا\n\n");
		// ═══════════════════════════════════════════════════════════════
		sb.append("لأي استفسار حول الخصوصية أو شروط استخدام القوالب:\n\n");
		sb.append("البريد الإلكتروني: mm2007mm19h@gmail.com\n\n");
		sb.append("سنرد في أقرب وقت ممكن.\n\n\n");

		sb.append("شكرًا لثقتك واستخدامك Klencod IDE\n");

		return sb.toString();
	}

	private void showContactInfo() {
		String contactText = "للتواصل مع المطور:\n\n" +
				"البريد الإلكتروني:\n" +
				"mm2007mm19h@gmail.com\n\n" +
				"تيليجرام:\n" +
				"https://t.me/Klencod\n\n" +
				"إذا واجهتك مشكلة:\n" +
				"سيتم الرد عليك في أقرب وقت ممكن.\n\n" +
				"شكرًا لك على استخدام التطبيق!";

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("تواصل مع المطور");

		final TextView textView = new TextView(this);
		textView.setText(contactText);
		textView.setTextColor(COLOR_TEXT_PRIMARY);
		textView.setPadding(dp(24), dp(20), dp(24), dp(20));
		textView.setTextSize(14);
		textView.setLineSpacing(0, 1.2f);
		textView.setTextIsSelectable(true);
		Linkify.addLinks(textView, Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);
		textView.setMovementMethod(LinkMovementMethod.getInstance());

		ScrollView scrollView = new ScrollView(this);
		scrollView.setBackgroundColor(COLOR_BACKGROUND);
		scrollView.addView(textView);
		builder.setView(scrollView);

		builder.setPositiveButton("موافق", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void showAboutApp() {
		String version = "1.4";
		try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
		catch (PackageManager.NameNotFoundException e) { e.printStackTrace(); }
		String aboutText = "Klencod IDE\n\n" +
				"الإصدار: " + version + "\n\n" +
				"تطبيق لإنشاء وتحرير وبناء مشاريع Flutter مباشرة من هاتفك.";
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("حول التطبيق");
		TextView textView = new TextView(this);
		textView.setText(aboutText);
		textView.setTextColor(COLOR_TEXT_PRIMARY);
		textView.setPadding(32, 24, 32, 24);
		textView.setTextSize(14);
		textView.setMovementMethod(new ScrollingMovementMethod());
		ScrollView scrollView = new ScrollView(this);
		scrollView.addView(textView);
		builder.setView(scrollView);
		builder.setPositiveButton("موافق", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void showEditorSettings() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("إعدادات المحرر");
		int currentSize = codeEditorView != null ? codeEditorView.getFontSize() : 13;
		boolean wordWrap = codeEditorView != null ? codeEditorView.isWordWrap() : true;
		boolean pinchZoom = codeEditorView != null ? codeEditorView.isPinchZoomEnabled() : false;
		String[] options = {
				"حجم الخط: " + currentSize,
				"التفاف النص: " + (wordWrap ? "مفعل" : "غير مفعل"),
				"تكبير وتصغير المحرر بإصبعين: " + (pinchZoom ? "مفعل" : "غير مفعل")
		};
		builder.setAdapter(UiStyle.dialogListAdapter(this, options), new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				switch (which) {
					case 0: showFontSizeDialog(); break;
					case 1: toggleWordWrap(); break;
					case 2: togglePinchZoom(); break;
				}
			}
		});
		builder.setNegativeButton("إغلاق", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		UiStyle.styleDialog(dialog);
	}

	private void showFontSizeDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("حجم الخط");
		int currentSize = codeEditorView != null ? codeEditorView.getFontSize() : 13;
		String[] options = {"صغير (10)", "افتراضي (13)", "كبير (18)", "كبير جداً (24)"};
		int[] sizes = {10, 13, 18, 24};
		int selectedIndex = 1;
		for (int i = 0; i < sizes.length; i++) { if (sizes[i] == currentSize) { selectedIndex = i; break; } }
		builder.setSingleChoiceItems(UiStyle.dialogChoiceAdapter(this, options), selectedIndex, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				if (codeEditorView != null) {
					codeEditorView.setFontSize(sizes[which]);
					Toast.makeText(MainActivity.this, "تم تغيير حجم الخط إلى " + sizes[which], Toast.LENGTH_SHORT).show();
					dialog.dismiss();
				}
			}
		});
		builder.setNegativeButton("إلغاء", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		UiStyle.styleDialog(dialog);
	}

	private void toggleWordWrap() {
		if (codeEditorView != null) {
			boolean newState = !codeEditorView.isWordWrap();
			codeEditorView.setWordWrap(newState);
			Toast.makeText(this, "التفاف النص: " + (newState ? "مفعل" : "غير مفعل"), Toast.LENGTH_SHORT).show();
		}
	}

	private void togglePinchZoom() {
		if (codeEditorView != null) {
			boolean newState = !codeEditorView.isPinchZoomEnabled();
			codeEditorView.setPinchZoomEnabled(newState);
			Toast.makeText(this, "تكبير وتصغير المحرر بإصبعين: " + (newState ? "مفعل" : "غير مفعل"), Toast.LENGTH_SHORT).show();
		}
	}

	private void showFileOptionsDialog() {
		if (selectedFileForDialog == null) return;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("خيارات: " + selectedFileForDialog.getName());
		String[] options = {"ملف جديد", "مجلد جديد", "استيراد ملف", "إعادة تسمية", "حذف"};
		builder.setAdapter(UiStyle.dialogListAdapter(this, options), new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				switch (which) {
					case 0: showCreateFileDialog(); break;
					case 1: showCreateFolderDialog(); break;
					case 2: openFileImport(); break;
					case 3: showRenameDialog(); break;
					case 4: deleteSelectedFile(); break;
				}
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void showCreateFileDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("ملف جديد");
		final EditText input = new EditText(this);
		input.setHint("اسم الملف (مثلاً: home.dart)");
		input.setHintTextColor(COLOR_TEXT_PRIMARY);
		input.setTextColor(COLOR_TEXT_PRIMARY);
		input.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		input.setPadding(dp(14), dp(12), dp(14), dp(12));
		builder.setView(input);
		builder.setPositiveButton("إنشاء", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				String fileName = input.getText().toString().trim();
				if (fileName.isEmpty()) { Toast.makeText(MainActivity.this, "الرجاء إدخال اسم الملف", Toast.LENGTH_SHORT).show(); return; }
				File targetDir = selectedFileForDialog != null && selectedFileForDialog.isDirectory()
						? selectedFileForDialog : selectedFileForDialog.getParentFile();
				File newFile = new File(targetDir, fileName);
				try {
					if (newFile.createNewFile()) {
						refreshFileTree();
						openFileInEditor(newFile);
						addLog("تم إنشاء ملف: " + fileName);
					} else { Toast.makeText(MainActivity.this, "الملف موجود بالفعل", Toast.LENGTH_SHORT).show(); }
				} catch (IOException e) { addLog("خطأ: " + e.getMessage()); }
			}
		});
		builder.setNegativeButton("إلغاء", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void showCreateFolderDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("مجلد جديد");
		final EditText input = new EditText(this);
		input.setHint("اسم المجلد");
		input.setHintTextColor(COLOR_TEXT_PRIMARY);
		input.setTextColor(COLOR_TEXT_PRIMARY);
		input.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		input.setPadding(dp(14), dp(12), dp(14), dp(12));
		builder.setView(input);
		builder.setPositiveButton("إنشاء", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				String folderName = input.getText().toString().trim();
				if (folderName.isEmpty()) { Toast.makeText(MainActivity.this, "الرجاء إدخال اسم المجلد", Toast.LENGTH_SHORT).show(); return; }
				File targetDir = selectedFileForDialog != null && selectedFileForDialog.isDirectory()
						? selectedFileForDialog : selectedFileForDialog.getParentFile();
				File newFolder = new File(targetDir, folderName);
				if (newFolder.mkdirs()) {
					refreshFileTree();
					addLog("تم إنشاء مجلد: " + folderName);
				} else { Toast.makeText(MainActivity.this, "المجلد موجود بالفعل", Toast.LENGTH_SHORT).show(); }
			}
		});
		builder.setNegativeButton("إلغاء", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void showRenameDialog() {
		if (selectedFileForDialog == null || selectedFileForDialog.getParentFile() == null) return;
		if (currentProjectDir != null && selectedFileForDialog.equals(currentProjectDir)) {
			Toast.makeText(this, "لا يمكن إعادة تسمية مجلد المشروع من هنا", Toast.LENGTH_SHORT).show();
			return;
		}
		final EditText input = new EditText(this);
		input.setText(selectedFileForDialog.getName());
		input.setSelectAllOnFocus(true);
		input.setSingleLine(true);
		input.setTextColor(COLOR_TEXT_PRIMARY);
		input.setHintTextColor(COLOR_TEXT_SECONDARY);
		input.setPadding(dp(14), dp(12), dp(14), dp(12));
		input.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("إعادة تسمية").setView(input)
				.setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface d) {
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						String newName = input.getText().toString().trim();
						if (newName.isEmpty() || newName.contains("/") || newName.contains("\\")) {
							input.setError("أدخل اسمًا صالحًا");
							return;
						}
						File target = new File(selectedFileForDialog.getParentFile(), newName);
						if (target.exists()) { input.setError("يوجد ملف أو مجلد بهذا الاسم"); return; }
						File oldFile = selectedFileForDialog;
						if (!oldFile.renameTo(target)) {
							Toast.makeText(MainActivity.this, "تعذرت إعادة التسمية", Toast.LENGTH_SHORT).show();
							return;
						}
						updateOpenPathsAfterRename(oldFile, target);
						selectedFileForDialog = target;
						refreshFileTree();
						renderTabStrip();
						saveEditorProjectState();
						addLog("تمت إعادة التسمية إلى: " + newName);
						dialog.dismiss();
					}
				});
			}
		});
		dialog.show();
		styleDialog(dialog);
		input.requestFocus();
	}

	private void updateOpenPathsAfterRename(File oldFile, File newFile) {
		String oldPath = oldFile.getAbsolutePath();
		String newPath = newFile.getAbsolutePath();
		for (int i = 0; i < openTabs.size(); i++) {
			EditorTabState state = openTabs.get(i);
			if (state.absolutePath.equals(oldPath) || state.absolutePath.startsWith(oldPath + File.separator)) {
				String suffix = state.absolutePath.substring(oldPath.length());
				EditorTabState replacement = new EditorTabState(newPath + suffix);
				replacement.content = state.content;
				replacement.selectionStart = state.selectionStart;
				replacement.selectionEnd = state.selectionEnd;
				replacement.scrollY = state.scrollY;
				replacement.loaded = state.loaded;
				replacement.dirty = state.dirty;
				openTabs.set(i, replacement);
			}
		}
		if (currentOpenFile != null) {
			String currentPath = currentOpenFile.getAbsolutePath();
			if (currentPath.equals(oldPath) || currentPath.startsWith(oldPath + File.separator)) {
				currentOpenFile = new File(newPath + currentPath.substring(oldPath.length()));
			}
		}
		if (currentProjectDir != null) {
			String oldRelative = getRelativePath(oldFile).replace(File.separatorChar, '/');
			String newRelative = getRelativePath(newFile).replace(File.separatorChar, '/');
			Set<String> updatedFavorites = new HashSet<>();
			for (String favorite : favoritePaths) {
				if (favorite.equals(oldRelative) || favorite.startsWith(oldRelative + "/")) {
					updatedFavorites.add(newRelative + favorite.substring(oldRelative.length()));
				} else { updatedFavorites.add(favorite); }
			}
			favoritePaths = updatedFavorites;
		}
	}

	private void openProjectZipImport() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed"});
		startActivityForResult(intent, REQUEST_IMPORT_PROJECT_ZIP);
	}

	private void openFileImport() {
		File targetDir = selectedFileForDialog != null && selectedFileForDialog.isDirectory()
				? selectedFileForDialog : selectedFileForDialog.getParentFile();
		prefs.edit().putString("import_target", targetDir.getAbsolutePath()).apply();
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		startActivityForResult(intent, REQUEST_IMPORT_FILE);
	}

	private void deleteSelectedFile() {
		if (selectedFileForDialog == null) return;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("حذف");
		builder.setMessage("هل أنت متأكد من حذف \"" + selectedFileForDialog.getName() + "\"؟");
		builder.setPositiveButton("نعم", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				String name = selectedFileForDialog.getName();
				if (currentOpenFile != null && currentOpenFile.equals(selectedFileForDialog)) {
					currentOpenFile = null;
					releaseCurrentViewer();
					viewerContainer.removeAllViews();
					tvCurrentFilePath.setText("");
					updateSaveButtonVisibility();
				}
				deleteRecursive(selectedFileForDialog);
				refreshFileTree();
				addLog("تم حذف: " + name);
			}
		});
		builder.setNegativeButton("إلغاء", null);
		AlertDialog dialog = builder.create();
		dialog.show();
		styleDialog(dialog);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_PICK_ICON_CREATE && resultCode == RESULT_OK && data != null) {
			Uri originalUri = data.getData();
			if (originalUri != null) {
				selectedIconUri = cropImageToSquare(originalUri);
				String fileName = getFileNameFromUri(selectedIconUri);
				tvIconSelected.setText("تم اختيار: " + (fileName != null ? fileName : "صورة"));
				tvIconSelected.setTextColor(COLOR_PRIMARY);
				ivIconPreview.setVisibility(View.VISIBLE);
				ivIconPreview.setImageURI(selectedIconUri);
			}
		} else if (requestCode == REQUEST_PICK_ICON && settingsDialog != null) {
			settingsDialog.onActivityResult(requestCode, resultCode, data);
		} else if (requestCode == REQUEST_IMPORT_PROJECT_ZIP && resultCode == RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) importProjectFromZip(uri);
		} else if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) {
				String targetPath = prefs.getString("import_target", "");
				if (targetPath.isEmpty()) return;
				String fileName = getFileNameFromUri(uri);
				if (fileName == null) fileName = "imported_file";
				File targetFile = new File(targetPath, fileName);
				try {
					InputStream inputStream = getContentResolver().openInputStream(uri);
					FileOutputStream outputStream = new FileOutputStream(targetFile);
					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = inputStream.read(buffer)) != -1) {
						outputStream.write(buffer, 0, bytesRead);
					}
					inputStream.close();
					outputStream.close();
					refreshFileTree();
					addLog("تم استيراد: " + fileName);
				} catch (IOException e) { addLog("خطأ في الاستيراد: " + e.getMessage()); }
			}
		}
	}

	private String getFileNameFromUri(Uri uri) {
		String fileName = null;
		if (uri.getScheme() != null && uri.getScheme().equals("content")) {
			Cursor cursor = getContentResolver().query(uri, null, null, null, null);
			if (cursor != null) {
				int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				cursor.moveToFirst();
				if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
				cursor.close();
			}
		}
		if (fileName == null) {
			fileName = uri.getPath();
			if (fileName != null) {
				int cut = fileName.lastIndexOf('/');
				if (cut != -1) fileName = fileName.substring(cut + 1);
			}
		}
		return fileName;
	}

	private void copyDefaultIcon(File projectDir) {
		try {
			File drawableDir = new File(projectDir, "android/app/src/main/res/drawable");
			if (!drawableDir.exists() && !drawableDir.mkdirs()) return;
			File targetFile = new File(drawableDir, "ic_launcher.png");
			if (targetFile.exists()) return;
			Bitmap defaultIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
			if (defaultIcon == null) {
				defaultIcon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
				android.graphics.Canvas canvas = new android.graphics.Canvas(defaultIcon);
				canvas.drawColor(0xFF1F6FEB);
			}
			Bitmap resized = Bitmap.createScaledBitmap(defaultIcon, 512, 512, true);
			FileOutputStream fos = new FileOutputStream(targetFile);
			resized.compress(Bitmap.CompressFormat.PNG, 100, fos);
			fos.flush();
			fos.close();
			if (resized != defaultIcon) resized.recycle();
			if (defaultIcon != null) defaultIcon.recycle();
		} catch (Exception e) { addLog("تعذر إنشاء أيقونة افتراضية: " + e.getMessage()); }
	}

	private void saveIconToProject(Uri imageUri, File projectDir) {
		try {
			File drawableDir = new File(projectDir, "android/app/src/main/res/drawable");
			if (!drawableDir.exists() && !drawableDir.mkdirs()) {
				addLog("تعذر إنشاء مجلد الأيقونة");
				return;
			}
			InputStream inputStream = getContentResolver().openInputStream(imageUri);
			if (inputStream == null) { addLog("تعذر فتح الصورة"); copyDefaultIcon(projectDir); return; }
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
			inputStream.close();
			if (originalBitmap == null) { addLog("تعذر قراءة الصورة"); copyDefaultIcon(projectDir); return; }
			int width = originalBitmap.getWidth();
			int height = originalBitmap.getHeight();
			int size = Math.min(width, height);
			int left = (width - size) / 2;
			int top = (height - size) / 2;
			Bitmap squaredBitmap = Bitmap.createBitmap(originalBitmap, left, top, size, size);
			Bitmap resizedBitmap = Bitmap.createScaledBitmap(squaredBitmap, 512, 512, true);
			File iconFile = new File(drawableDir, "ic_launcher.png");
			FileOutputStream outputStream = new FileOutputStream(iconFile);
			resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
			outputStream.flush();
			outputStream.close();
			if (resizedBitmap != squaredBitmap) resizedBitmap.recycle();
			if (squaredBitmap != originalBitmap) squaredBitmap.recycle();
			originalBitmap.recycle();
			addLog("تم حفظ أيقونة التطبيق");
		} catch (Exception e) {
			addLog("خطأ في حفظ الأيقونة: " + e.getMessage());
			copyDefaultIcon(projectDir);
		}
	}

	private void showScreen(int screen) {
		currentScreen = screen;
		if (layoutProjectsList.getParent() != null)
			((ViewGroup) layoutProjectsList.getParent()).removeView(layoutProjectsList);
		if (layoutCreateProject.getParent() != null)
			((ViewGroup) layoutCreateProject.getParent()).removeView(layoutCreateProject);
		if (drawerLayout != null && drawerLayout.getParent() != null)
			((ViewGroup) drawerLayout.getParent()).removeView(drawerLayout);

		LinearLayout rootLayout = new LinearLayout(this);
		rootLayout.setOrientation(LinearLayout.VERTICAL);
		rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

		switch (screen) {
			case SCREEN_PROJECTS_LIST:
				rootLayout.addView(layoutProjectsList);
				restartBrandAnimationIfPossible();
				break;
			case SCREEN_CREATE_PROJECT: rootLayout.addView(layoutCreateProject); break;
			case SCREEN_EDITOR: rootLayout.addView(drawerLayout); isEditorVisible = true; break;
		}
		setContentView(rootLayout);
	}

	private void restartBrandAnimationIfPossible() {
		if (layoutProjectsList == null) return;
		layoutProjectsList.post(new Runnable() {
			@Override public void run() {
				TextView brand = findBrandTextView(layoutProjectsList);
				if (brand != null) {
					brand.setAlpha(0f);
					brand.setTranslationY(-40f);
					brand.setScaleX(0.85f);
					brand.setScaleY(0.85f);
					playBrandAnimation(brand);
				}
			}
		});
	}

	private TextView findBrandTextView(ViewGroup root) {
		for (int i = 0; i < root.getChildCount(); i++) {
			View child = root.getChildAt(i);
			if (child instanceof TextView) {
				CharSequence text = ((TextView) child).getText();
				if (text != null && "KlencodIDE".contentEquals(text)) {
					return (TextView) child;
				}
			}
			if (child instanceof ViewGroup) {
				TextView found = findBrandTextView((ViewGroup) child);
				if (found != null) return found;
			}
		}
		return null;
	}

	@Override
	public void onBackPressed() {
		if (currentScreen == SCREEN_EDITOR) {
			if (drawerLayout.isDrawerOpen(Gravity.START)) {
				drawerLayout.closeDrawer(Gravity.START);
				return;
			}
			saveAllOpenTabs();
			showScreen(SCREEN_PROJECTS_LIST);
			loadProjectsList();
			isEditorVisible = false;
		} else if (currentScreen == SCREEN_CREATE_PROJECT) {
			showScreen(SCREEN_PROJECTS_LIST);
			loadProjectsList();
		} else {
			super.onBackPressed();
		}
	}

	private void loadProjectsList() {
		projectsList.clear();
		File[] dirs = projectsBaseDir.listFiles();
		if (dirs != null) {
			for (File dir : dirs) {
				if (dir.isDirectory()) projectsList.add(dir);
			}
		}
		Collections.sort(projectsList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

		if (layoutProjectsList != null) {
			TextView count = layoutProjectsList.findViewWithTag("project_count");
			if (count != null) {
				count.setText(projectsList.size() + " مشروع");
			}
		}

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, android.R.id.text1) {

			@Override public int getCount() { return projectsList.size(); }
			@Override public String getItem(int position) { return null; }

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				File project = projectsList.get(position);

				LinearLayout card = UiStyle.projectCard(MainActivity.this);
				card.setClickable(false);
				card.setFocusable(false);

				android.widget.AbsListView.LayoutParams cardParams =
						new android.widget.AbsListView.LayoutParams(-1, -2);
				card.setLayoutParams(cardParams);
				card.setPadding(dp(16), dp(14), dp(16), dp(14));

				String appName = prefs.getString("name_" + project.getName(), project.getName());
				String firstLetter = appName.isEmpty() ? "?"
						: appName.substring(0, 1).toUpperCase(Locale.US);

				final FrameLayout iconContainer = new FrameLayout(MainActivity.this);
				int iconSize = dp(48);
				LinearLayout.LayoutParams iconParams =
						new LinearLayout.LayoutParams(iconSize, iconSize);
				iconContainer.setLayoutParams(iconParams);

				int color = pickProjectColor(project.getName());
				TextView fallback = UiStyle.circleIcon(MainActivity.this, firstLetter, color);
				fallback.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
				iconContainer.addView(fallback);

				card.addView(iconContainer);

				LinearLayout textCol = new LinearLayout(MainActivity.this);
				textCol.setOrientation(LinearLayout.VERTICAL);
				LinearLayout.LayoutParams tcParams = new LinearLayout.LayoutParams(0, -2, 1f);
				tcParams.setMarginStart(dp(14));
				card.addView(textCol, tcParams);

				TextView title = new TextView(MainActivity.this);
				title.setText(appName);
				title.setTextColor(COLOR_TEXT_PRIMARY);
				title.setTextSize(15);
				title.setTypeface(null, android.graphics.Typeface.BOLD);
				title.setSingleLine(true);
				title.setEllipsize(android.text.TextUtils.TruncateAt.END);
				textCol.addView(title);

				String packageName = prefs.getString("package_" + project.getName(), "");
				TextView subtitle = new TextView(MainActivity.this);
				subtitle.setText(packageName.isEmpty() ? "بدون حزمة" : packageName);
				subtitle.setTextColor(COLOR_TEXT_SECONDARY);
				subtitle.setTextSize(11);
				subtitle.setSingleLine(true);
				subtitle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
				LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
				subParams.topMargin = dp(2);
				textCol.addView(subtitle, subParams);

				TextView dateView = new TextView(MainActivity.this);
				dateView.setText(formatRelativeTime(project.lastModified()));
				dateView.setTextColor(COLOR_TEXT_SECONDARY);
				dateView.setTextSize(10);
				LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
				dateParams.topMargin = dp(4);
				textCol.addView(dateView, dateParams);

				TextView arrow = new TextView(MainActivity.this);
				arrow.setText("\u203A");
				arrow.setTextColor(COLOR_ARROW);
				arrow.setTextSize(22);
				arrow.setGravity(Gravity.CENTER);
				LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(24), dp(24));
				arrowParams.setMarginStart(dp(6));
				card.addView(arrow, arrowParams);

				loadProjectIconAsync(project, iconContainer);

				return card;
			}
		};
		lvProjects.setAdapter(adapter);
	}

	private int pickProjectColor(String projectKey) {
		int[] palette = {
				0xFF1F6FEB, 0xFF2EA043, 0xFFD29922, 0xFFCF222E,
				0xFF8957E5, 0xFFDB61A2, 0xFF0EA5E9, 0xFFF97316,
		};
		if (projectKey == null) return palette[0];
		int hash = Math.abs(projectKey.hashCode());
		return palette[hash % palette.length];
	}

	private String formatRelativeTime(long timestampMs) {
		long diff = System.currentTimeMillis() - timestampMs;
		long minutes = diff / (60 * 1000);
		long hours = diff / (60 * 60 * 1000);
		long days = diff / (24 * 60 * 60 * 1000);

		if (minutes < 1) return "الآن";
		if (minutes < 60) return "منذ " + minutes + " دقيقة";
		if (hours < 24) return "منذ " + hours + " ساعة";
		if (days < 7) return "منذ " + days + " يوم";
		if (days < 30) return "منذ " + (days / 7) + " أسبوع";
		if (days < 365) return "منذ " + (days / 30) + " شهر";
		return "منذ " + (days / 365) + " سنة";
	}

	private boolean isValidPackageName(String packageName) {
		if (packageName == null || packageName.isEmpty()) return false;
		String[] parts = packageName.split("\\.");
		if (parts.length < 3) return false;
		for (String part : parts) {
			if (part.isEmpty()) return false;
			if (Character.isDigit(part.charAt(0))) return false;
			for (int i = 0; i < part.length(); i++) {
				char c = part.charAt(i);
				if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
						|| (c >= '0' && c <= '9') || c == '_')) return false;
			}
		}
		return true;
	}

	private void createProject() {
		String appName = etAppName.getText().toString().trim();
		String packageName = etPackageName.getText().toString().trim();
		String selectedApi = spinnerApi.getSelectedItem().toString();
		String apiLevel = selectedApi.split(" ")[0];

		if (appName.isEmpty()) { Toast.makeText(this, "الرجاء إدخال اسم التطبيق", Toast.LENGTH_SHORT).show(); return; }
		if (packageName.isEmpty()) { Toast.makeText(this, "الرجاء إدخال اسم الحزمة", Toast.LENGTH_SHORT).show(); return; }
		if (!isValidPackageName(packageName)) { Toast.makeText(this, "اسم الحزمة غير صحيح\nمثال: com.example.myapp", Toast.LENGTH_LONG).show(); return; }

		String uniqueAppName = findUniqueAppName(appName);
		String folderName = "project_" + System.currentTimeMillis();
		currentProjectDir = new File(projectsBaseDir, folderName);
		if (!currentProjectDir.mkdirs()) { Toast.makeText(this, "خطأ في إنشاء مجلد المشروع", Toast.LENGTH_LONG).show(); return; }

		boolean isPublicRepo = true;
		if (repoTypeGroup != null) {
			int checkedId = repoTypeGroup.getCheckedRadioButtonId();
			isPublicRepo = (checkedId == android.R.id.button1);
		}

		prefs.edit()
				.putString("name_" + folderName, uniqueAppName)
				.putString("package_" + folderName, packageName)
				.putString("api_" + folderName, apiLevel)
				.putBoolean("repo_public_" + folderName, isPublicRepo)
				.apply();

		boolean ok = TemplateManager.writeTemplate(
				TemplateManager.DEFAULT_TEMPLATE,
				currentProjectDir,
				packageName,
				uniqueAppName,
				apiLevel
		);
		if (!ok) {
			Toast.makeText(this, "خطأ في إنشاء هيكل المشروع", Toast.LENGTH_LONG).show();
			deleteRecursive(currentProjectDir);
			return;
		}

		if (selectedIconUri != null) {
			saveIconToProject(selectedIconUri, currentProjectDir);
		} else {
			copyDefaultIcon(currentProjectDir);
		}

		invalidateProjectIconCache(currentProjectDir);

		if (!uniqueAppName.equals(appName)) {
			Toast.makeText(this,
					"الاسم \"" + appName + "\" كان مستخدماً، تم استخدام \"" + uniqueAppName + "\"",
					Toast.LENGTH_LONG).show();
		}

		addLog("تم إنشاء مشروع: " + uniqueAppName);
		openProject(currentProjectDir);
	}

	private String findUniqueAppName(String baseName) {
		if (baseName == null || baseName.trim().isEmpty()) return baseName == null ? "" : baseName;
		String trimmed = baseName.trim();
		Set<String> existingNames = new HashSet<>();
		File[] dirs = projectsBaseDir.listFiles();
		if (dirs != null) {
			for (File dir : dirs) {
				if (!dir.isDirectory()) continue;
				String name = prefs.getString("name_" + dir.getName(), "");
				if (!name.isEmpty()) existingNames.add(name);
			}
		}
		if (!existingNames.contains(trimmed)) return trimmed;
		int suffix = 1;
		while (suffix <= 999) {
			String candidate = trimmed + "_" + suffix;
			if (!existingNames.contains(candidate)) return candidate;
			suffix++;
		}
		return trimmed;
	}

	private void showRepoTypeDialogForImport(final Uri uri) {
		final LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(20), dp(16), dp(20), dp(16));
		root.setBackgroundColor(COLOR_BACKGROUND);

		TextView title = new TextView(this);
		title.setText("نوع المستودع على GitHub:");
		title.setTextSize(15);
		title.setTextColor(COLOR_TEXT_PRIMARY);
		title.setTypeface(null, android.graphics.Typeface.BOLD);
		title.setPadding(0, 0, 0, dp(12));
		root.addView(title);

		final android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
		radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
		radioGroup.setPadding(dp(4), dp(4), dp(4), dp(4));

		android.widget.RadioButton rbPublic = new android.widget.RadioButton(this);
		rbPublic.setText("عام (Public) - موصى به");
		rbPublic.setTextColor(COLOR_TEXT_PRIMARY);
		rbPublic.setTextSize(13);
		rbPublic.setId(android.R.id.button1);
		radioGroup.addView(rbPublic);

		android.widget.RadioButton rbPrivate = new android.widget.RadioButton(this);
		rbPrivate.setText("خاص (Private)");
		rbPrivate.setTextColor(COLOR_TEXT_PRIMARY);
		rbPrivate.setTextSize(13);
		rbPrivate.setId(android.R.id.button2);
		radioGroup.addView(rbPrivate);

		radioGroup.check(android.R.id.button1);
		root.addView(radioGroup);

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("استيراد مشروع من ZIP")
				.setView(root)
				.setPositiveButton("استيراد", null)
				.setNegativeButton("إلغاء", null)
				.create();

		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface d) {
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						boolean isPublicRepo = (radioGroup.getCheckedRadioButtonId() == android.R.id.button1);
						dialog.dismiss();
						performImportProjectFromZip(uri, isPublicRepo);
					}
				});
			}
		});

		dialog.show();
		styleDialog(dialog);
	}

	private void importProjectFromZip(Uri uri) {
		showRepoTypeDialogForImport(uri);
	}

	private void performImportProjectFromZip(Uri uri, boolean isPublicRepo) {
		File tempDir = new File(projectsBaseDir, ".zip_import_" + System.currentTimeMillis());
		File targetDir = null;
		try {
			if (!tempDir.mkdirs()) throw new IOException("تعذر إنشاء مجلد الاستيراد المؤقت");
			InputStream input = getContentResolver().openInputStream(uri);
			if (input == null) throw new IOException("تعذر قراءة ملف ZIP");
			extractZipSafely(input, tempDir);
			input.close();

			File sourceRoot = findImportedProjectRoot(tempDir);
			if (sourceRoot == null || !sourceRoot.exists()) throw new IOException("الأرشيف فارغ");

			String zipName = getFileNameFromUri(uri);
			String projectName = sanitizeImportedProjectName(zipName);
			targetDir = nextAvailableProjectDir(projectName);
			copyDirectory(sourceRoot, targetDir);
			deleteRecursive(tempDir);

			ensureWorkflowExists(targetDir);

			String folderName = targetDir.getName();
			prefs.edit()
					.putString("name_" + folderName, projectName)
					.putBoolean("repo_public_" + folderName, isPublicRepo)
					.apply();

			loadProjectsList();
			addLog("تم استيراد المشروع: " + targetDir.getName() + " (" + (isPublicRepo ? "عام" : "خاص") + ")");
			Toast.makeText(this, "تم استيراد المشروع بنجاح", Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			if (targetDir != null && targetDir.exists()) deleteRecursive(targetDir);
			if (tempDir.exists()) deleteRecursive(tempDir);
			addLog("خطأ في استيراد المشروع: " + e.getMessage());
			Toast.makeText(this, "تعذر استيراد المشروع: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void extractZipSafely(InputStream input, File destination) throws IOException {
		ZipInputStream zipInput = new ZipInputStream(input);
		String rootPath = destination.getCanonicalPath();
		long totalBytes = 0;
		byte[] buffer = new byte[8192];
		ZipEntry entry;
		try {
			while ((entry = zipInput.getNextEntry()) != null) {
				String entryName = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
				while (entryName.startsWith("./")) entryName = entryName.substring(2);
				if (entryName.length() == 0 || entryName.startsWith("/") || !isSafeZipPath(entryName)) {
					throw new IOException("مسار غير آمن داخل الأرشيف");
				}
				if (entryName.startsWith("__MACOSX/") || entryName.endsWith("/.DS_Store") || entryName.equals(".DS_Store")) {
					zipInput.closeEntry();
					continue;
				}
				File output = new File(destination, entryName);
				String outputPath = output.getCanonicalPath();
				if (!outputPath.equals(rootPath) && !outputPath.startsWith(rootPath + File.separator)) {
					throw new IOException("مسار خارج مجلد المشروع");
				}
				if (entry.isDirectory()) {
					if (!output.exists() && !output.mkdirs()) throw new IOException("تعذر إنشاء مجلد داخل الأرشيف");
				} else {
					File parent = output.getParentFile();
					if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("تعذر إنشاء مجلد الملف");
					FileOutputStream fileOutput = new FileOutputStream(output);
					try {
						int count;
						while ((count = zipInput.read(buffer)) != -1) {
							totalBytes += count;
							if (totalBytes > 300L * 1024L * 1024L) throw new IOException("الأرشيف أكبر من الحد المسموح");
							fileOutput.write(buffer, 0, count);
						}
					} finally { fileOutput.close(); }
				}
				zipInput.closeEntry();
			}
		} finally { zipInput.close(); }
	}

	private boolean isSafeZipPath(String path) {
		String[] parts = path.split("/");
		for (String part : parts) if (part.equals("..")) return false;
		return true;
	}

	private File findImportedProjectRoot(File extractionDir) {
		File[] children = extractionDir.listFiles();
		if (children == null || children.length == 0) return null;
		ArrayList<File> meaningful = new ArrayList<>();
		for (File child : children) {
			if (!child.getName().equals("__MACOSX") && !child.getName().equals(".DS_Store")) meaningful.add(child);
		}
		if (meaningful.size() == 1 && meaningful.get(0).isDirectory()) return meaningful.get(0);
		return extractionDir;
	}

	private String sanitizeImportedProjectName(String zipName) {
		String name = zipName == null ? "ImportedProject" : zipName;
		if (name.toLowerCase(Locale.US).endsWith(".zip")) name = name.substring(0, name.length() - 4);
		name = name.trim().replaceAll("[^\\p{L}\\p{N}_-]", "_");
		while (name.startsWith(".")) name = name.substring(1);
		if (name.length() == 0) name = "ImportedProject";
		return name;
	}

	private File nextAvailableProjectDir(String projectName) {
		File candidate = new File(projectsBaseDir, projectName);
		int suffix = 2;
		while (candidate.exists()) {
			candidate = new File(projectsBaseDir, projectName + "_" + suffix);
			suffix++;
		}
		return candidate;
	}

	private void copyDirectory(File source, File destination) throws IOException {
		if (source.isDirectory()) {
			if (!destination.exists() && !destination.mkdirs()) throw new IOException("تعذر إنشاء مجلد المشروع");
			File[] children = source.listFiles();
			if (children == null) return;
			for (File child : children) copyDirectory(child, new File(destination, child.getName()));
			return;
		}
		File parent = destination.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("تعذر إنشاء مجلد الملف");
		FileInputStream input = new FileInputStream(source);
		FileOutputStream output = new FileOutputStream(destination);
		try {
			byte[] buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
		} finally {
			try { input.close(); } finally { output.close(); }
		}
	}

	private void ensureWorkflowExists(File projectDir) {
		if (projectDir == null || !projectDir.isDirectory()) return;
		try {
			File githubDir = new File(projectDir, ".github");
			File workflowsDir = new File(githubDir, "workflows");
			File workflowFile = new File(workflowsDir, "main.yml");
			if (!workflowFile.exists()) {
				if (!workflowsDir.exists() && !workflowsDir.mkdirs()) {
					addLog("تعذر إنشاء مجلد .github/workflows");
					return;
				}
				writeDefaultWorkflow(workflowFile);
				addLog("تم إنشاء ملف البناء main.yml تلقائياً");
				return;
			}
			String content = readFileText(workflowFile);
			if (hasWorkflowDispatch(content)) {
				addLog("المشروع يحتوي على main.yml جاهز، لن نعدّله");
				return;
			}
			String updated = injectWorkflowDispatch(content);
			if (updated != null && !updated.equals(content)) {
				writeTextFile(workflowFile, updated);
				addLog("تم تحديث main.yml بإضافة workflow_dispatch");
			} else {
				writeDefaultWorkflow(workflowFile);
				addLog("لم نتمكن من تحليل main.yml، تم استبداله بالنسخة الافتراضية");
			}
		} catch (Exception e) {
			addLog("تعذر التحقق من workflow: " + e.getMessage());
		}
	}

	private boolean hasWorkflowDispatch(String content) {
		if (content == null || content.isEmpty()) return false;
		Pattern p = Pattern.compile("^\\s*workflow_dispatch\\s*:", Pattern.MULTILINE);
		return p.matcher(content).find();
	}

	private String injectWorkflowDispatch(String content) {
		if (content == null || content.isEmpty()) return null;
		try {
			String[] lines = content.split("\n", -1);
			StringBuilder result = new StringBuilder();
			boolean onFound = false;
			boolean dispatchInjected = false;
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				result.append(line);
				if (!onFound && line.matches("^on\\s*:\\s*$")) {
					onFound = true;
					result.append("\n  workflow_dispatch:");
					dispatchInjected = true;
				} else if (!onFound && line.matches("^on\\s*:\\s*\\[.*\\]\\s*$")) {
					String inner = line.replaceAll("^on\\s*:\\s*\\[|\\]\\s*$", "").trim();
					result.setLength(result.length() - line.length());
					result.append("on:");
					if (!inner.isEmpty()) {
						for (String item : inner.split(",")) {
							result.append("\n  ").append(item.trim()).append(":");
						}
					}
					result.append("\n  workflow_dispatch:");
					onFound = true;
					dispatchInjected = true;
				} else if (!onFound && line.matches("^on\\s*:\\s*\\S+.*$")) {
					String value = line.replaceFirst("^on\\s*:\\s*", "").trim();
					result.setLength(result.length() - line.length());
					result.append("on:");
					if (!value.isEmpty()) result.append("\n  ").append(value).append(":");
					result.append("\n  workflow_dispatch:");
					onFound = true;
					dispatchInjected = true;
				}
				if (i < lines.length - 1) result.append("\n");
			}
			return dispatchInjected ? result.toString() : null;
		} catch (Exception e) { return null; }
	}

	private void writeDefaultWorkflow(File workflowFile) throws IOException {
		String yml =
				"name: Build Flutter APK\n" +
						"\n" +
						"on:\n" +
						"  workflow_dispatch:\n" +
						"\n" +
						"jobs:\n" +
						"  build:\n" +
						"    runs-on: ubuntu-latest\n" +
						"    steps:\n" +
						"      - uses: actions/checkout@v4\n" +
						"\n" +
						"      - name: Setup Java 11\n" +
						"        uses: actions/setup-java@v4\n" +
						"        with:\n" +
						"          distribution: temurin\n" +
						"          java-version: '11'\n" +
						"\n" +
						"      - name: Setup Flutter\n" +
						"        uses: subosito/flutter-action@v2\n" +
						"        with:\n" +
						"          flutter-version: '3.16.0'\n" +
						"\n" +
						"      - name: Get dependencies\n" +
						"        run: flutter pub get\n" +
						"\n" +
						"      - name: Build APK\n" +
						"        run: flutter build apk --release\n" +
						"\n" +
						"      - name: Upload APK\n" +
						"        uses: actions/upload-artifact@v4\n" +
						"        with:\n" +
						"          name: app-release\n" +
						"          path: build/app/outputs/flutter-apk/app-release.apk\n" +
						"          retention-days: 7\n";
		writeTextFile(workflowFile, yml);
	}

	private void writeTextFile(File file, String content) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) parent.mkdirs();
		FileWriter writer = new FileWriter(file);
		try {
			writer.write(content);
			writer.flush();
		} finally { writer.close(); }
	}

	private void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			File[] children = fileOrDirectory.listFiles();
			if (children != null) for (File child : children) deleteRecursive(child);
		}
		fileOrDirectory.delete();
	}

	private void reloadFileFromDisk(File file) {
		if (file == null || !file.exists()) return;
		try {
			EditorTabState state = findTab(file.getAbsolutePath());
			FileKind kind = FileKind.detect(file.getName());
			if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
				String content = readFileText(file);
				if (state == null) { state = new EditorTabState(file.getAbsolutePath()); openTabs.add(state); }
				state.content = content; state.loaded = true; state.dirty = false;
				currentOpenFile = file;
				activeTabIndex = openTabs.indexOf(state);
				showFileViewer(file);
				if (codeEditorView != null && (activeViewerKind == FileKind.TEXT || activeViewerKind == FileKind.UNKNOWN)) {
					codeEditorView.setTextWithLanguage(content, file.getAbsolutePath());
				}
				refreshExternalErrorMarkersForCurrentFile();
				tvCurrentFilePath.setText("الملف: " + file.getName() + " - " + getRelativePath(file));
				renderTabStrip();
				saveEditorProjectState();
			} else {
				currentOpenFile = file;
				showFileViewer(file);
				tvCurrentFilePath.setText("الملف: " + file.getName() + " - " + getRelativePath(file));
			}
		} catch (IOException e) {
			addLog("خطأ في إعادة تحميل الملف بعد AI: " + e.getMessage());
		}
	}

	private void openFileInEditor(File file) {
		if (file == null || !file.isFile()) return;

		saveCurrentFile();

		EditorTabState state = findTab(file.getAbsolutePath());
		FileKind kind = FileKind.detect(file.getName());

		try {
			if (state == null) {
				state = new EditorTabState(file.getAbsolutePath());
				if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
					state.content = readFileText(file);
				} else {
					state.content = "";
				}
				state.loaded = true;
				state.dirty = false;
				openTabs.add(state);
			}
			activeTabIndex = openTabs.indexOf(state);
			currentOpenFile = file;

			showFileViewer(file);

			if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
				if (codeEditorView != null) {
					codeEditorView.setTextWithLanguage(state.content, file.getAbsolutePath());
					int start = Math.min(state.selectionStart, state.content.length());
					int end = Math.min(state.selectionEnd, state.content.length());
					if (start <= end) codeEditorView.getEditor().setSelection(start, end);
					codeEditorView.restoreVerticalScrollPosition(state.scrollY);
				}
			}

			refreshExternalErrorMarkersForCurrentFile();
			tvCurrentFilePath.setText("الملف: " + file.getName() + " - " + getRelativePath(file));
			updateFavoriteButton();
			renderTabStrip();
			saveEditorProjectState();
		} catch (IOException e) { addLog("خطأ في فتح الملف: " + e.getMessage()); }
	}

	private String readFileText(File file) throws IOException {
		StringBuilder builder = new StringBuilder();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null) builder.append(line).append("\n");
			if (builder.length() > 0) builder.setLength(builder.length() - 1);
			return builder.toString();
		} finally { if (reader != null) reader.close(); }
	}

	private void saveCurrentFile() {
		if (currentOpenFile == null || !currentOpenFile.exists() || codeEditorView == null) return;
		if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN
				&& activeViewerKind != null) return;
		updateActiveTabFromEditor();
		EditorTabState state = findTab(currentOpenFile.getAbsolutePath());
		String code = state == null ? codeEditorView.getText() : state.content;
		FileWriter writer = null;
		try {
			writer = new FileWriter(currentOpenFile);
			writer.write(code);
			writer.flush();
			if (state != null) state.dirty = false;
		} catch (IOException ignored) {
		} finally { if (writer != null) try { writer.close(); } catch (IOException ignored) { } }
		saveEditorProjectState();
		renderTabStrip();
	}

	private String getRelativePath(File file) {
		if (currentProjectDir == null) return file.getName();
		String projectPath = currentProjectDir.getAbsolutePath();
		String filePath = file.getAbsolutePath();
		return filePath.startsWith(projectPath) ? filePath.substring(projectPath.length() + 1) : filePath;
	}

	private String editorProjectKey() {
		if (currentProjectDir == null) return "none";
		return Integer.toHexString(currentProjectDir.getAbsolutePath().hashCode());
	}

	private EditorTabState findTab(String absolutePath) {
		if (absolutePath == null) return null;
		for (EditorTabState state : openTabs) if (absolutePath.equals(state.absolutePath)) return state;
		return null;
	}

	private void updateActiveTabFromEditor() {
		if (activeTabIndex < 0 || activeTabIndex >= openTabs.size() || codeEditorView == null) return;
		if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN
				&& activeViewerKind != null) return;
		EditorTabState state = openTabs.get(activeTabIndex);
		String text = codeEditorView.getText();
		if (!text.equals(state.content)) state.dirty = true;
		state.content = text;
		state.selectionStart = Math.max(0, codeEditorView.getEditor().getSelectionStart());
		state.selectionEnd = Math.max(0, codeEditorView.getEditor().getSelectionEnd());
		state.scrollY = codeEditorView.getVerticalScrollPosition();
		state.loaded = true;
	}

	private void saveEditorProjectState() {
		if (editorStateStore == null || currentProjectDir == null) return;
		ArrayList<String> paths = new ArrayList<>();
		for (EditorTabState state : openTabs) paths.add(getRelativePath(new File(state.absolutePath)));
		editorStateStore.setRecentTabs(editorProjectKey(), paths);
		editorStateStore.setFavorites(editorProjectKey(), favoritePaths);
		String activePath = currentOpenFile == null ? "" : getRelativePath(currentOpenFile);
		editorStateStore.setActiveTab(editorProjectKey(), activePath);
	}

	private void switchToTab(int index) {
		if (index < 0 || index >= openTabs.size()) return;
		if (activeTabIndex == index) return;
		updateActiveTabFromEditor();
		activeTabIndex = index;
		EditorTabState state = openTabs.get(index);
		File file = new File(state.absolutePath);
		currentOpenFile = file;

		FileKind kind = FileKind.detect(file.getName());
		showFileViewer(file);

		if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
			if (codeEditorView != null) {
				codeEditorView.setTextWithLanguage(state.content, state.absolutePath);
				int start = Math.min(state.selectionStart, codeEditorView.getText().length());
				int end = Math.min(state.selectionEnd, codeEditorView.getText().length());
				if (start <= end) codeEditorView.getEditor().setSelection(start, end);
				codeEditorView.restoreVerticalScrollPosition(state.scrollY);
			}
		}

		refreshExternalErrorMarkersForCurrentFile();
		tvCurrentFilePath.setText("الملف: " + currentOpenFile.getName() + " - " + getRelativePath(currentOpenFile));
		updateFavoriteButton();
		renderTabStrip();
		saveEditorProjectState();
	}

	private void closeTab(int index) {
		if (index < 0 || index >= openTabs.size()) return;
		boolean wasActive = (index == activeTabIndex);
		if (wasActive) updateActiveTabFromEditor();
		openTabs.remove(index);

		if (openTabs.isEmpty()) {
			activeTabIndex = -1;
			currentOpenFile = null;
			releaseCurrentViewer();
			if (viewerContainer != null) viewerContainer.removeAllViews();
			if (codeEditorView != null) codeEditorView.clear();
			tvCurrentFilePath.setText("لا يوجد ملف مفتوح");
		} else {
			if (index < activeTabIndex) activeTabIndex--;
			if (wasActive || activeTabIndex < 0 || activeTabIndex >= openTabs.size()) {
				activeTabIndex = Math.min(index, openTabs.size() - 1);
				EditorTabState state = openTabs.get(activeTabIndex);
				File file = new File(state.absolutePath);
				currentOpenFile = file;
				FileKind kind = FileKind.detect(file.getName());
				showFileViewer(file);
				if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
					if (codeEditorView != null) {
						codeEditorView.setTextWithLanguage(state.content, state.absolutePath);
						codeEditorView.restoreVerticalScrollPosition(state.scrollY);
					}
				}
				tvCurrentFilePath.setText("الملف: " + currentOpenFile.getName() + " - " + getRelativePath(currentOpenFile));
			}
		}
		refreshExternalErrorMarkersForCurrentFile();
		renderTabStrip();
		saveEditorProjectState();
	}

	private void saveAllOpenTabs() {
		updateActiveTabFromEditor();
		for (EditorTabState state : openTabs) {
			if (!state.loaded) continue;
			File file = new File(state.absolutePath);
			if (!FileKind.isTextLike(file.getName())) continue;
			FileWriter writer = null;
			try {
				writer = new FileWriter(file);
				writer.write(state.content);
				writer.flush();
				state.dirty = false;
			} catch (IOException e) {
				addLog("تعذر حفظ التبويب: " + file.getName());
			} finally {
				if (writer != null) try { writer.close(); } catch (IOException ignored) { }
			}
		}
		saveEditorProjectState();
		renderTabStrip();
	}

	private void saveAllOpenTabsSilently() {
		if (currentProjectDir == null || openTabs.isEmpty()) return;
		updateActiveTabFromEditor();

		final List<String> paths = new ArrayList<>();
		final List<String> contents = new ArrayList<>();
		for (EditorTabState state : openTabs) {
			if (!state.loaded) continue;
			File file = new File(state.absolutePath);
			if (!FileKind.isTextLike(file.getName())) continue;
			paths.add(state.absolutePath);
			contents.add(state.content == null ? "" : state.content);
		}
		if (paths.isEmpty()) return;

		for (EditorTabState state : openTabs) state.dirty = false;
		saveEditorProjectState();

		new Thread(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < paths.size(); i++) {
					FileWriter writer = null;
					try {
						writer = new FileWriter(new File(paths.get(i)));
						writer.write(contents.get(i));
						writer.flush();
					} catch (IOException ignored) {
					} finally {
						if (writer != null) try { writer.close(); } catch (IOException ignored) { }
					}
				}
			}
		}, "auto-save").start();
	}

	private void closeAllTabs() {
		if (openTabs.isEmpty()) { Toast.makeText(this, "لا توجد تبويبات مفتوحة", Toast.LENGTH_SHORT).show(); return; }
		updateActiveTabFromEditor();
		boolean hasDirtyTabs = false;
		for (EditorTabState state : openTabs) { if (state.dirty) { hasDirtyTabs = true; break; } }
		if (!hasDirtyTabs) { clearAllTabsAfterConfirmation(); return; }
		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("تبويبات غير محفوظة")
				.setMessage("توجد ملفات معدلة. هل تريد حفظها قبل إغلاق جميع التبويبات؟")
				.setPositiveButton("حفظ وإغلاق", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int which) { saveAllOpenTabs(); clearAllTabsAfterConfirmation(); }
				})
				.setNeutralButton("إغلاق دون حفظ", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int which) { clearAllTabsAfterConfirmation(); }
				})
				.setNegativeButton("إلغاء", null)
				.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void clearAllTabsAfterConfirmation() {
		openTabs.clear();
		activeTabIndex = -1;
		currentOpenFile = null;
		releaseCurrentViewer();
		if (viewerContainer != null) viewerContainer.removeAllViews();
		if (codeEditorView != null) codeEditorView.clear();
		tvCurrentFilePath.setText("لا يوجد ملف مفتوح");
		refreshExternalErrorMarkersForCurrentFile();
		renderTabStrip();
		saveEditorProjectState();
		Toast.makeText(this, "تم إغلاق جميع التبويبات", Toast.LENGTH_SHORT).show();
	}

	private void openProject(File projectDir) {
		currentProjectDir = projectDir;
		openTabs.clear();
		activeTabIndex = -1;
		currentOpenFile = null;
		releaseCurrentViewer();
		favoritePaths = editorStateStore == null ? new HashSet<String>() : editorStateStore.getFavorites(editorProjectKey());
		String folderName = projectDir.getName();
		String appName = prefs.getString("name_" + folderName, folderName);
		String packageName = prefs.getString("package_" + folderName, "");
		String apiLevel = prefs.getString("api_" + folderName, "");
		tvProjectInfo.setText("المشروع: " + appName + " | الحزمة: " + packageName + " | API: " + apiLevel);
		expandedDirs.clear();
		List<String> recentTabs = editorStateStore == null ? new ArrayList<String>() : editorStateStore.getRecentTabs(editorProjectKey());
		if (recentTabs.isEmpty()) {
			File mainDart = new File(new File(projectDir, "lib"), "main.dart");
			if (mainDart.exists()) openFileInEditor(mainDart);
		} else {
			for (String relative : recentTabs) {
				File file = new File(projectDir, relative);
				if (file.isFile()) openFileInEditor(file);
			}
			String activePath = editorStateStore.getActiveTab(editorProjectKey());
			EditorTabState activeState = findTab(new File(projectDir, activePath).getAbsolutePath());
			if (activeState != null) switchToTab(openTabs.indexOf(activeState));
		}
		refreshFileTree();
		renderTabStrip();
		showScreen(SCREEN_EDITOR);
		addLog("تم فتح المشروع: " + appName);
	}

	private void deleteProject(final File projectDir) {
		final String name = prefs.getString("name_" + projectDir.getName(), "");
		AlertDialog confirm = new AlertDialog.Builder(this)
				.setTitle("تأكيد الحذف")
				.setMessage("هل أنت متأكد أنك تريد حذف المشروع \"" + name + "\"؟")
				.setPositiveButton("حذف", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						invalidateProjectIconCache(projectDir);
						deleteRecursive(projectDir);
						prefs.edit()
								.remove("name_" + projectDir.getName())
								.remove("package_" + projectDir.getName())
								.remove("api_" + projectDir.getName())
								.remove("repo_public_" + projectDir.getName())
								.apply();
						loadProjectsList();
						addLog("تم حذف المشروع: " + name);
						Toast.makeText(MainActivity.this, "تم حذف المشروع", Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton("إلغاء", null)
				.create();
		confirm.show();
		styleDialog(confirm);
	}

	private void toggleDirectory(File dir) {
		if (expandedDirs.contains(dir)) expandedDirs.remove(dir); else expandedDirs.add(dir);
		refreshFileTree();
	}

	private void saveTreeScrollState() {
		if (rvFileTree != null && fileTreeLayoutManager != null) {
			treeScrollPosition = fileTreeLayoutManager.findFirstVisibleItemPosition();
			View firstChild = fileTreeLayoutManager.findViewByPosition(treeScrollPosition);
			treeScrollOffset = firstChild == null ? 0 : firstChild.getTop();
		}
	}

	private void restoreTreeScrollState() {
		if (rvFileTree == null || fileTreeLayoutManager == null) return;
		rvFileTree.post(new Runnable() {
			@Override public void run() {
				if (fileTreeAdapter != null && fileTreeAdapter.getItemCount() > 0) {
					int safe = Math.min(treeScrollPosition, fileTreeAdapter.getItemCount() - 1);
					fileTreeLayoutManager.scrollToPositionWithOffset(safe, treeScrollOffset);
				}
			}
		});
	}

	private void refreshFileTree() {
		if (currentProjectDir == null || !currentProjectDir.exists() || rvFileTree == null) return;
		if (fileTreeAdapter == null || fileTreeLayoutManager == null) return;
		final List<FileNode> newNodes = new ArrayList<>();
		collectAllFilesAsTree(currentProjectDir, newNodes);
		final int firstVisible = fileTreeLayoutManager.findFirstVisibleItemPosition();
		final View firstChild = fileTreeLayoutManager.findViewByPosition(firstVisible);
		final int topOffset = firstChild == null ? 0 : firstChild.getTop();
		fileTreeAdapter.submitList(newNodes);
		rvFileTree.post(new Runnable() {
			@Override public void run() {
				if (firstVisible >= 0 && firstVisible < fileTreeAdapter.getItemCount()) {
					fileTreeLayoutManager.scrollToPositionWithOffset(firstVisible, topOffset);
				}
			}
		});
	}

	private void collectAllFilesAsTree(File dir, List<FileNode> result) {
		List<File> dirs = new ArrayList<>();
		List<File> files = new ArrayList<>();
		File[] children = dir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.getName().equals(".gitignore")) continue;
				if (child.isDirectory()) dirs.add(child); else files.add(child);
			}
		}
		Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		List<File> allItems = new ArrayList<>();
		allItems.addAll(dirs);
		allItems.addAll(files);
		for (File item : allItems) addFileNode(item, result, 0);
	}

	private void addFileNode(File item, List<FileNode> result, int depth) {
		boolean isExpanded = item.isDirectory() && expandedDirs.contains(item);
		result.add(new FileNode(item, depth, isExpanded));
		if (isExpanded) {
			List<File> subDirs = new ArrayList<>();
			List<File> subFiles = new ArrayList<>();
			File[] children = item.listFiles();
			if (children != null) {
				for (File child : children) {
					if (child.getName().equals(".gitignore")) continue;
					if (child.isDirectory()) subDirs.add(child); else subFiles.add(child);
				}
			}
			Collections.sort(subDirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
			Collections.sort(subFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
			List<File> allSub = new ArrayList<>();
			allSub.addAll(subDirs);
			allSub.addAll(subFiles);
			for (File child : allSub) addFileNode(child, result, depth + 1);
		}
	}

	private void exportProjectAsZip() {
		try {
			if (currentProjectDir == null || !currentProjectDir.exists()) {
				Toast.makeText(this, "لا يوجد مشروع مفتوح", Toast.LENGTH_SHORT).show();
				return;
			}
			saveAllOpenTabs();
			if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
			File downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
			if (downloadsDir == null) downloadsDir = new File(getFilesDir(), "exports");
			if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
				Toast.makeText(this, "تعذر إنشاء مجلد التصدير", Toast.LENGTH_LONG).show();
				return;
			}
			String appName = prefs.getString("name_" + currentProjectDir.getName(), "flutter_project");
			String safeName = appName.replaceAll("[^A-Za-z0-9._\\-]+", "_");
			if (safeName.isEmpty()) safeName = "flutter_project";
			File zipFile = new File(downloadsDir, safeName + "_" + System.currentTimeMillis() + ".zip");
			FileOutputStream fos = new FileOutputStream(zipFile);
			ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos));
			addFilesToZip(currentProjectDir, currentProjectDir.getAbsolutePath(), zos);
			zos.flush();
			zos.close();
			fos.close();
			addLog("تم تصدير ZIP: " + zipFile.getName());
			final File finalZip = zipFile;
			new AlertDialog.Builder(this)
					.setTitle("تم تصدير ZIP")
					.setMessage("تم الحفظ في:\n" + finalZip.getAbsolutePath()
							+ "\n\nيمكنك فتحه أو مشاركته.")
					.setPositiveButton("فتح", new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface d, int w) { openZipFile(finalZip); }
					})
					.setNeutralButton("مشاركة", new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface d, int w) { shareZipFile(finalZip); }
					})
					.setNegativeButton("إغلاق", null)
					.show();
		} catch (Throwable t) {
			StringBuilder err = new StringBuilder();
			err.append("خطأ: ").append(t.getClass().getSimpleName());
			if (t.getMessage() != null) err.append("\n").append(t.getMessage());
			addLog(err.toString());
			Toast.makeText(this, err.toString(), Toast.LENGTH_LONG).show();
		}
	}

	private void addFilesToZip(File source, String basePath, ZipOutputStream zos) throws IOException {
		File[] files = source.listFiles();
		if (files == null) return;
		byte[] buffer = new byte[4096];
		for (File file : files) {
			if (file.getName().equals(".gitignore")) continue;
			String entryPath = file.getAbsolutePath().substring(basePath.length() + 1);
			if (file.isDirectory()) {
				zos.putNextEntry(new ZipEntry(entryPath + "/"));
				zos.closeEntry();
				addFilesToZip(file, basePath, zos);
			} else {
				FileInputStream fis = new FileInputStream(file);
				zos.putNextEntry(new ZipEntry(entryPath));
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) zos.write(buffer, 0, bytesRead);
				zos.closeEntry();
				fis.close();
			}
		}
	}

	private void openZipFile(File zipFile) {
		if (zipFile == null || !zipFile.exists()) return;
		try {
			Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", zipFile);
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(uri, "application/zip");
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivity(Intent.createChooser(intent, "فتح ZIP"));
		} catch (Exception e) {
			Toast.makeText(this, "تعذر فتح الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void shareZipFile(File zipFile) {
		if (zipFile == null || !zipFile.exists()) return;
		try {
			Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", zipFile);
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("application/zip");
			intent.putExtra(Intent.EXTRA_STREAM, uri);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivity(Intent.createChooser(intent, "مشاركة ZIP"));
		} catch (Exception e) {
			Toast.makeText(this, "تعذر مشاركة الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void buildApk() {
		if (currentProjectDir == null || !currentProjectDir.exists()) {
			Toast.makeText(this, "لا يوجد مشروع مفتوح", Toast.LENGTH_SHORT).show();
			return;
		}
		saveAllOpenTabs();
		clearBuildErrors();
		resetStepsState();
		drawerLayout.closeDrawer(Gravity.START);

		buildStartedAtMs = System.currentTimeMillis();
		buildInProgress = true;
		startTimerUpdates();
		uiSteps.clear();
		stepsAdapter.notifyDataSetChanged();
		if (uploadCounterBox != null) uploadCounterBox.setVisibility(View.GONE);
		addLog("بدء عملية بناء APK عبر GitHub Actions...");

		buildApi.buildApkDirect(currentProjectDir, getCacheDir(), new FlutterBuildApi.BuildCallback() {
			@Override public void onProgress(final String message) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (message != null && (message.startsWith("الاتصال") || message.startsWith("تم العثور")
								|| message.startsWith("لم يوجد") || message.startsWith("تم إنشاء")
								|| message.startsWith("اكتمل") || message.startsWith("تم تشغيل")
								|| message.startsWith("حالة GitHub") || message.startsWith("فشل")
								|| message.startsWith("اكتمل البناء") || message.startsWith("=====")
								|| message.startsWith("جارٍ تجربة الاسم") || message.startsWith("الاسم")
								|| message.startsWith("جارٍ إنشاؤه"))) {
							addLog(message);
						}
					}
				});
			}

			@Override public void onSuccess(final File apkFile) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						buildInProgress = false;
						stopTimerUpdates();
						addLog("━━━━━━━━━━━━━━━━━━━━━━");
						addLog("تم بناء APK بنجاح");
						addLog("المسار: " + apkFile.getAbsolutePath());
						try {
							File savedApk = new BuiltAppsStore(MainActivity.this).save(apkFile, currentProjectDir.getName());
							addLog("تم حفظ نسخة في مكتبة التطبيقات: " + savedApk.getName());
						} catch (Exception e) {
							addLog("تعذر حفظ نسخة في المكتبة: " + e.getMessage());
						}
						addLog("جاري التثبيت...");
						buildApi.installApk(apkFile);
					}
				});
			}

			@Override public void onError(final String error) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						buildInProgress = false;
						stopTimerUpdates();
						if (error != null && error.startsWith(FlutterBuildApi.GITHUB_QUOTA_PREFIX)) {
							String githubMessage = error.substring(FlutterBuildApi.GITHUB_QUOTA_PREFIX.length()).trim();
							showGithubQuotaDialog(githubMessage);
							return;
						}
						if (error != null && error.startsWith(FlutterBuildApi.ANNOTATIONS_PREFIX)) {
							String rawAnnotations = error.substring(FlutterBuildApi.ANNOTATIONS_PREFIX.length());
							applyAnnotations(rawAnnotations);
							addLog("━━━━━━━━━━━━━━━━━━━━━━");
							addLog("فشل البناء - راجع التفاصيل أعلاه");
							return;
						}
						addLog("━━━━━━━━━━━━━━━━━━━━━━");
						addLog("فشل البناء: " + error);
					}
				});
			}

			@Override public void onStepsUpdate(final List<GitHubApi.Step> steps) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() { applySteps(steps); }
				});
			}

			@Override public void onUploadProgress(final int current, final int total) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (uploadCounterBox != null && tvUploadCounter != null) {
							uploadCounterBox.setVisibility(View.VISIBLE);
							tvUploadCounter.setText("تم رفع (" + current + "/" + total + " ملف)");
						}
					}
				});
			}

			@Override public void onStepLogs(final long jobId, final List<FlutterBuildApi.BuildLogLine> allLogs,
			                                final List<FlutterBuildApi.BuildLogLine> errorLogs) {
				runOnUiThread(new Runnable() {
					@Override public void run() { applyStepLogs(jobId, allLogs, errorLogs); }
				});
			}

			@Override public void onRepositoryReady(final String repoName) {
				if (repoName == null || repoName.trim().isEmpty()) return;
				if (currentProjectDir == null) return;
				prefs.edit().putString("repo_" + currentProjectDir.getName(), repoName.trim()).apply();
			}
		});
	}

	private void showGithubQuotaDialog(String githubMessage) {
		final String finalMessage;
		if (githubMessage == null || githubMessage.trim().isEmpty()) {
			finalMessage = "The job was not started because recent account payments have failed "
					+ "or your spending limit needs to be increased.";
		} else { finalMessage = githubMessage; }

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(20), dp(16), dp(20), dp(16));
		root.setBackgroundColor(COLOR_BACKGROUND);

		TextView intro = new TextView(this);
		intro.setText("GitHub أعاد الرسالة الرسمية التالية:");
		intro.setTextSize(13);
		intro.setTextColor(COLOR_TEXT_PRIMARY);
		root.addView(intro, new LinearLayout.LayoutParams(-1, -2));

		TextView msgView = new TextView(this);
		msgView.setText(finalMessage);
		msgView.setTextSize(12);
		msgView.setTypeface(android.graphics.Typeface.MONOSPACE);
		msgView.setTextColor(COLOR_TEXT_PRIMARY);
		msgView.setPadding(dp(14), dp(14), dp(14), dp(14));
		msgView.setBackground(UiStyle.rounded(this, COLOR_SURFACE, 12, 0xFF30363D, 1));
		msgView.setTextIsSelectable(true);
		LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(-1, -2);
		msgParams.topMargin = dp(12);
		root.addView(msgView, msgParams);

		TextView hint = new TextView(this);
		hint.setText("\nهذا يعني:\n"
				+ "• رصيد GitHub Actions المجاني انتهى\n"
				+ "• أو مشكلة في طريقة الدفع\n\n"
				+ "الكود سليم - المشكلة في إعدادات GitHub");
		hint.setTextSize(12);
		hint.setTextColor(COLOR_TEXT_PRIMARY);
		hint.setLineSpacing(0, 1.3f);
		root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("لم يبدأ البناء على GitHub")
				.setView(root)
				.setPositiveButton("فتح الفواتير", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int w) { openBillingPage(); }
				})
				.setNeutralButton("إعدادات المستودع", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface d, int w) { openRepoSettingsPage(); }
				})
				.setNegativeButton("إغلاق", null)
				.create();
		dialog.show();
		styleDialog(dialog);
	}

	private void openBillingPage() {
		try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/billing"))); }
		catch (Exception e) { Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show(); }
	}

	private void openRepoSettingsPage() {
		String username = AuthManager.getInstance(this).getGithubUsername();
		String folderName = currentProjectDir != null ? currentProjectDir.getName() : "";
		String repoName = prefs.getString("repo_" + folderName, "");
		if (repoName.isEmpty()) repoName = prefs.getString("name_" + folderName, "");
		if (username == null || username.trim().isEmpty() || repoName == null || repoName.trim().isEmpty()) {
			openBillingPage();
			return;
		}
		try {
			String url = "https://github.com/" + username + "/" + repoName + "/settings";
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (Exception e) { Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show(); }
	}

	private void resetStepsState() {
		uiSteps.clear();
		if (stepsAdapter != null) stepsAdapter.notifyDataSetChanged();
		if (tvBuildTimer != null) tvBuildTimer.setText("");
		if (uploadCounterBox != null) uploadCounterBox.setVisibility(View.GONE);
	}

	private void applySteps(List<GitHubApi.Step> steps) {
		if (steps == null || steps.isEmpty()) return;
		uiSteps.clear();
		for (GitHubApi.Step step : steps) {
			UiStep ui = new UiStep(step.name, step.status, step.conclusion, step.startedAtMs, step.durationMs(), 0L);
			uiSteps.add(ui);
		}
		if (stepsAdapter != null) stepsAdapter.notifyDataSetChanged();
		if (uploadCounterBox != null) uploadCounterBox.setVisibility(View.GONE);
	}

	private void applyAnnotations(String rawAnnotations) {
		if (rawAnnotations == null || rawAnnotations.trim().isEmpty()) return;
		String[] parts = rawAnnotations.split(FlutterBuildApi.ANNOTATIONS_SEPARATOR);
		List<FlutterBuildApi.BuildLogLine> parsed = new ArrayList<>();
		for (String part : parts) {
			if (part == null || part.trim().isEmpty()) continue;
			String message = part.trim();
			int logType = FlutterBuildApi.classifyAnnotationStatic(message);
			parsed.add(new FlutterBuildApi.BuildLogLine(message, null, 0, 0, logType));
		}
		if (parsed.isEmpty()) return;
		if (tvLogs != null) {
			tvLogs.append("\n");
			tvLogs.append("━━━━━━━━━━━━━━━━━━━━━━\n");
			tvLogs.append("رسائل GitHub الرسمية:\n\n");
			for (FlutterBuildApi.BuildLogLine line : parsed) {
				String prefix = "";
				if (line.logType == FlutterBuildApi.LOG_TYPE_ERROR) prefix = "[خطأ] ";
				else if (line.logType == FlutterBuildApi.LOG_TYPE_WARNING) prefix = "[تحذير] ";
				tvLogs.append(prefix);
				tvLogs.append(line.text);
				tvLogs.append("\n\n");
			}
		}
		List<UiStep> targets = new ArrayList<>();
		for (UiStep step : uiSteps) if (step.isFailure()) targets.add(step);
		if (targets.isEmpty() && !uiSteps.isEmpty()) targets.add(uiSteps.get(uiSteps.size() - 1));
		for (UiStep step : targets) {
			step.annotations.clear();
			step.annotations.addAll(parsed);
			step.logsFetched = true;
			if (step.isFailure()) step.logsExpanded = true;
		}
		if (stepsAdapter != null) stepsAdapter.notifyDataSetChanged();
	}

	private void applyStepLogs(long jobId, List<FlutterBuildApi.BuildLogLine> allLogs,
	                            List<FlutterBuildApi.BuildLogLine> errorLogs) {
		if (allLogs == null) allLogs = new ArrayList<>();
		if (errorLogs == null) errorLogs = new ArrayList<>();
		List<UiStep> targets = new ArrayList<>();
		for (UiStep step : uiSteps) if (step.isFailure()) targets.add(step);
		if (targets.isEmpty()) {
			UiStep buildStep = null;
			for (UiStep step : uiSteps) {
				if (step.name.toLowerCase(Locale.US).contains("build")) { buildStep = step; break; }
			}
			if (buildStep != null) targets.add(buildStep);
			else if (!uiSteps.isEmpty()) targets.add(uiSteps.get(uiSteps.size() - 1));
		}
		for (UiStep step : targets) {
			step.allLogs.clear();
			step.allLogs.addAll(allLogs);
			step.errorLogs.clear();
			step.errorLogs.addAll(errorLogs);
			step.logsFetched = true;
		}
		for (FlutterBuildApi.BuildLogLine line : errorLogs) {
			if (line.logType == FlutterBuildApi.LOG_TYPE_ERROR && line.filePath != null && line.line > 0) {
				BuildError be = new BuildError(line.text, cleanBuildPath(line.filePath),
						Math.max(1, line.line), Math.max(0, line.column), false);
				boolean exists = false;
				for (BuildError existing : buildErrors) {
					if (existing.path.equals(be.path) && existing.line == be.line) { exists = true; break; }
				}
				if (!exists) buildErrors.add(be);
			}
		}
		if (tvErrorSummary != null) tvErrorSummary.setText("أخطاء البناء: " + buildErrors.size());
		refreshExternalErrorMarkersForCurrentFile();
		if (stepsAdapter != null) stepsAdapter.notifyDataSetChanged();
	}

	private void startTimerUpdates() {
		if (timerHandler == null) return;
		stopTimerUpdates();
		timerRunnable = new Runnable() {
			@Override public void run() {
				if (!buildInProgress) return;
				long elapsed = (System.currentTimeMillis() - buildStartedAtMs) / 1000L;
				long minutes = elapsed / 60L;
				long seconds = elapsed % 60L;
				if (tvBuildTimer != null) tvBuildTimer.setText(minutes + ":" + String.format(Locale.US, "%02d", seconds));
				if (stepsAdapter != null) stepsAdapter.notifyDataSetChanged();
				timerHandler.postDelayed(this, 1000L);
			}
		};
		timerHandler.post(timerRunnable);
	}

	private void stopTimerUpdates() {
		if (timerHandler != null && timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
		timerRunnable = null;
	}

	private void clearLogPanel() {
		if (tvLogs != null) tvLogs.setText("");
		uiSteps.clear();
		if (stepsAdapter != null) stepsAdapter.notifyDataSetChanged();
		if (tvBuildTimer != null) tvBuildTimer.setText("");
		if (uploadCounterBox != null) uploadCounterBox.setVisibility(View.GONE);
		clearBuildErrors();
		buildInProgress = false;
		stopTimerUpdates();
	}

	private String buildStepsText() {
		StringBuilder sb = new StringBuilder();
		boolean logsAlreadyShown = false;
		boolean annotationsAlreadyShown = false;
		sb.append("═══════════════════════════════════\n");
		sb.append("  ملخص البناء - Klencod IDE\n");
		sb.append("═══════════════════════════════════\n\n");
		for (UiStep step : uiSteps) {
			String marker;
			if (step.isSuccess()) marker = "[OK]";
			else if (step.isFailure()) marker = "[X]";
			else if (step.isRunning()) marker = "[..]";
			else marker = "[ ]";
			sb.append(marker).append("  ").append(step.name).append("  ")
					.append(formatDuration(step)).append("\n");
		}
		for (UiStep step : uiSteps) {
			if (step.annotations.isEmpty()) continue;
			sb.append("\n");
			sb.append("═══════════════════════════════════\n");
			sb.append("  رسائل GitHub الرسمية\n");
			sb.append("═══════════════════════════════════\n\n");
			for (FlutterBuildApi.BuildLogLine line : step.annotations) {
				String prefix = "";
				if (line.logType == FlutterBuildApi.LOG_TYPE_ERROR) prefix = "[خطأ] ";
				else if (line.logType == FlutterBuildApi.LOG_TYPE_WARNING) prefix = "[تحذير] ";
				sb.append(prefix).append(line.text).append("\n\n");
			}
			annotationsAlreadyShown = true;
			break;
		}
		for (UiStep step : uiSteps) {
			if (!step.isFailure()) continue;
			List<FlutterBuildApi.BuildLogLine> logs = step.logsExpanded ? step.allLogs : step.errorLogs;
			if (logs.isEmpty()) continue;
			sb.append("═══════════════════════════════════\n");
			sb.append("  سجل ").append(step.name).append("\n");
			sb.append("═══════════════════════════════════\n\n");
			for (FlutterBuildApi.BuildLogLine line : logs) sb.append("     ").append(line.text).append("\n");
			sb.append("\n");
			logsAlreadyShown = true;
			break;
		}
		if (!logsAlreadyShown && !annotationsAlreadyShown && tvLogs != null) {
			sb.append("═══════════════════════════════════\n");
			sb.append("  السجل النصي\n");
			sb.append("═══════════════════════════════════\n\n");
			sb.append(tvLogs.getText().toString());
		}
		return sb.toString();
	}

	private String formatDuration(UiStep step) {
		long ms;
		if (step.isRunning()) ms = System.currentTimeMillis() - step.startedAtMs;
		else ms = step.durationMs;
		if (ms <= 0) return "";
		long s = ms / 1000L;
		long m = s / 60L;
		long sec = s % 60L;
		if (m > 0) return m + "m " + sec + "s";
		return sec + "s";
	}

	private final class StepsAdapter extends RecyclerView.Adapter<StepsAdapter.StepVH> {
		StepsAdapter() { }

		@NonNull
		@Override
		public StepVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LinearLayout row = new LinearLayout(MainActivity.this);
			row.setOrientation(LinearLayout.VERTICAL);
			row.setPadding(dp(12), dp(6), dp(12), dp(6));
			row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

			LinearLayout main = new LinearLayout(MainActivity.this);
			main.setOrientation(LinearLayout.HORIZONTAL);
			main.setGravity(Gravity.CENTER_VERTICAL);

			TextView circle = new TextView(MainActivity.this);
			circle.setTextSize(11);
			circle.setGravity(Gravity.CENTER);
			circle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
			circle.setTextColor(0xFFFFFFFF);
			LinearLayout.LayoutParams circleP = new LinearLayout.LayoutParams(dp(20), dp(20));
			circleP.setMargins(0, 0, dp(10), 0);
			main.addView(circle, circleP);

			TextView name = new TextView(MainActivity.this);
			name.setTextSize(12);
			name.setTextColor(COLOR_TEXT_PRIMARY);
			name.setSingleLine(true);
			name.setEllipsize(android.text.TextUtils.TruncateAt.END);
			LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			nameP.setMargins(0, 0, dp(10), 0);
			main.addView(name, nameP);

			TextView duration = new TextView(MainActivity.this);
			duration.setTextSize(11);
			duration.setTextColor(COLOR_TEXT_SECONDARY);
			duration.setTypeface(android.graphics.Typeface.MONOSPACE);
			main.addView(duration);

			row.addView(main);

			LinearLayout logsBox = new LinearLayout(MainActivity.this);
			logsBox.setOrientation(LinearLayout.VERTICAL);
			logsBox.setPadding(dp(30), dp(6), dp(8), dp(6));
			logsBox.setVisibility(View.GONE);
			row.addView(logsBox);

			return new StepVH(row, circle, name, duration, logsBox);
		}

		@Override
		public void onBindViewHolder(@NonNull final StepVH holder, int position) {
			final UiStep step = uiSteps.get(position);
			holder.name.setText(step.name);
			holder.duration.setText(formatDuration(step));

			GradientDrawable circleBg = new GradientDrawable();
			circleBg.setShape(GradientDrawable.OVAL);
			if (step.isSuccess()) {
				circleBg.setColor(STEP_COLOR_SUCCESS);
				holder.circle.setText("+");
			} else if (step.isFailure()) {
				circleBg.setColor(STEP_COLOR_FAILURE);
				holder.circle.setText("X");
			} else if (step.isRunning()) {
				circleBg.setColor(STEP_COLOR_RUNNING);
				holder.circle.setText("");
			} else {
				circleBg.setColor(STEP_COLOR_QUEUED);
				holder.circle.setText("");
			}
			holder.circle.setBackground(circleBg);

			holder.logsBox.removeAllViews();

			if (!step.logsExpanded) {
				if (!step.annotations.isEmpty()) {
					displayAnnotations(holder.logsBox, step);
					holder.logsBox.setVisibility(View.VISIBLE);
				} else {
					holder.logsBox.setVisibility(View.GONE);
				}
				if (step.logsFetched && !step.allLogs.isEmpty()) {
					addExpandButton(holder.logsBox, step, holder.getAdapterPosition());
				}
				return;
			}

			List<FlutterBuildApi.BuildLogLine> logsToShow = step.allLogs;

			if (!step.annotations.isEmpty()) {
				displayAnnotations(holder.logsBox, step);
				View spacer = new View(MainActivity.this);
				spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(8)));
				holder.logsBox.addView(spacer);
			}

			if (logsToShow.isEmpty() && step.annotations.isEmpty()) {
				holder.logsBox.setVisibility(View.GONE);
				return;
			}
			holder.logsBox.setVisibility(View.VISIBLE);
			if (logsToShow.isEmpty()) {
				addCollapseButton(holder.logsBox, step, holder.getAdapterPosition());
				return;
			}

			int total = logsToShow.size();
			int toShow = Math.min(step.visibleCount, total);
			int startIndex = 0;
			if (step.showFromEnd && total > step.visibleCount) {
				startIndex = total - step.visibleCount;
				toShow = total;
			}
			if (startIndex > 0 || toShow < total) {
				int shownStart = startIndex + 1;
				int shownEnd = startIndex + (toShow - startIndex);
				if (startIndex == 0 && toShow < total) shownEnd = toShow;
				TextView indicator = new TextView(MainActivity.this);
				indicator.setText("- عرض " + shownStart + " إلى " + shownEnd + " من " + total + " -");
				indicator.setTextSize(10);
				indicator.setGravity(Gravity.CENTER);
				indicator.setTextColor(LOG_COLOR_LINK);
				indicator.setPadding(dp(8), dp(6), dp(8), dp(6));
				holder.logsBox.addView(indicator);
			}

			for (int i = startIndex; i < toShow; i++) {
				final FlutterBuildApi.BuildLogLine line = logsToShow.get(i);
				TextView lineView = new TextView(MainActivity.this);
				lineView.setTextSize(10);
				lineView.setTypeface(android.graphics.Typeface.MONOSPACE);
				String prefix = getLogLinePrefix(line);
				lineView.setText(prefix + line.text);
				lineView.setPadding(dp(4), dp(1), dp(4), dp(1));
				lineView.setTextIsSelectable(true);
				int color = getLogLineColor(line);
				if (step.isFailure() && color == LOG_COLOR_NORMAL) color = LOG_COLOR_ERROR;
				lineView.setTextColor(color);
				if (line.filePath != null && line.line > 0) {
					lineView.setPaintFlags(lineView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
					lineView.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) {
							BuildError be = new BuildError(line.text, cleanBuildPath(line.filePath),
									Math.max(1, line.line), Math.max(0, line.column), false);
							openBuildError(be);
						}
					});
				}
				holder.logsBox.addView(lineView);
			}

			if (toShow < total) {
				final int remaining = total - toShow;
				final int toAdd = Math.min(LOGS_BATCH_SIZE, remaining);
				Button moreBtn = new Button(MainActivity.this);
				moreBtn.setText("عرض " + toAdd + " سطر إضافي");
				moreBtn.setTextSize(10);
				moreBtn.setAllCaps(false);
				moreBtn.setTextColor(LOG_COLOR_LINK);
				moreBtn.setBackground(UiStyle.rounded(MainActivity.this, COLOR_SURFACE_ALT, 8, 0xFF30363D, 1));
				moreBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
				LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(34));
				p.setMargins(0, dp(4), 0, dp(4));
				moreBtn.setLayoutParams(p);
				moreBtn.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						step.visibleCount += toAdd;
						step.showFromEnd = false;
						notifyItemChanged(holder.getAdapterPosition());
					}
				});
				holder.logsBox.addView(moreBtn);
				if (remaining > toAdd) {
					Button allBtn = new Button(MainActivity.this);
					allBtn.setText("عرض الكل (" + total + " سطر)");
					allBtn.setTextSize(10);
					allBtn.setAllCaps(false);
					allBtn.setTextColor(LOG_COLOR_LINK);
					allBtn.setBackground(UiStyle.rounded(MainActivity.this, COLOR_SURFACE_ALT, 8, 0xFF30363D, 1));
					allBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
					LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(-1, dp(34));
					p2.setMargins(0, 0, 0, dp(4));
					allBtn.setLayoutParams(p2);
					allBtn.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) {
							step.visibleCount = total;
							step.showFromEnd = false;
							notifyItemChanged(holder.getAdapterPosition());
						}
					});
					holder.logsBox.addView(allBtn);
				}
			}
			addCollapseButton(holder.logsBox, step, holder.getAdapterPosition());
		}

		private void displayAnnotations(LinearLayout parent, UiStep step) {
			if (step.annotations.isEmpty()) return;
			LinearLayout annBox = new LinearLayout(MainActivity.this);
			annBox.setOrientation(LinearLayout.VERTICAL);
			annBox.setPadding(dp(8), dp(6), dp(8), dp(10));
			annBox.setBackground(UiStyle.rounded(MainActivity.this, COLOR_SURFACE_ALT, 10, 0xFF30363D, 1));
			TextView annTitle = new TextView(MainActivity.this);
			annTitle.setText("رسائل GitHub الرسمية:");
			annTitle.setTextSize(11);
			annTitle.setTextColor(LOG_COLOR_LINK);
			annTitle.setPadding(dp(4), dp(2), dp(4), dp(6));
			annTitle.setTypeface(null, android.graphics.Typeface.BOLD);
			annBox.addView(annTitle);
			for (FlutterBuildApi.BuildLogLine line : step.annotations) {
				TextView annLine = new TextView(MainActivity.this);
				annLine.setTextSize(11);
				annLine.setTypeface(android.graphics.Typeface.MONOSPACE);
				annLine.setTextIsSelectable(true);
				annLine.setPadding(dp(6), dp(3), dp(6), dp(3));
				String prefix = "";
				int color;
				switch (line.logType) {
					case FlutterBuildApi.LOG_TYPE_ERROR: prefix = "[خطأ] "; color = LOG_COLOR_ERROR; break;
					case FlutterBuildApi.LOG_TYPE_WARNING: prefix = "[تحذير] "; color = LOG_COLOR_WARNING; break;
					default: color = LOG_COLOR_NORMAL;
				}
				annLine.setText(prefix + line.text);
				annLine.setTextColor(color);
				annBox.addView(annLine);
			}
			parent.addView(annBox);
		}

		private void addExpandButton(LinearLayout parent, final UiStep step, final int position) {
			Button expandBtn = new Button(MainActivity.this);
			expandBtn.setText("عرض كل السجل (" + step.allLogs.size() + " سطر)");
			expandBtn.setTextSize(10);
			expandBtn.setAllCaps(false);
			expandBtn.setTextColor(LOG_COLOR_LINK);
			expandBtn.setBackground(UiStyle.rounded(MainActivity.this, COLOR_SURFACE_ALT, 8, 0xFF30363D, 1));
			expandBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(34));
			p.setMargins(0, dp(4), 0, dp(2));
			expandBtn.setLayoutParams(p);
			expandBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					step.logsExpanded = true;
					if (step.isFailure() && step.allLogs.size() > DEFAULT_VISIBLE_LOGS) step.showFromEnd = true;
					notifyItemChanged(position);
				}
			});
			parent.addView(expandBtn);
		}

		private void addCollapseButton(LinearLayout parent, final UiStep step, final int position) {
			Button collapseBtn = new Button(MainActivity.this);
			collapseBtn.setText("طوي السجل");
			collapseBtn.setTextSize(10);
			collapseBtn.setAllCaps(false);
			collapseBtn.setTextColor(LOG_COLOR_LINK);
			collapseBtn.setBackground(UiStyle.rounded(MainActivity.this, COLOR_SURFACE_ALT, 8, 0xFF30363D, 1));
			collapseBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(34));
			p.setMargins(0, dp(2), 0, dp(2));
			collapseBtn.setLayoutParams(p);
			collapseBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					step.logsExpanded = false;
					notifyItemChanged(position);
				}
			});
			parent.addView(collapseBtn);
		}

		@Override public int getItemCount() { return uiSteps.size(); }

		private boolean isContextLine(String text) {
			if (text == null) return false;
			String lower = text.toLowerCase(Locale.US);
			return lower.startsWith("at ") || lower.startsWith("    at ")
					|| lower.startsWith("> run with") || lower.startsWith("> get more help")
					|| lower.contains("daemon") || lower.contains("starting a gradle daemon");
		}

		private int getLogLineColor(FlutterBuildApi.BuildLogLine line) {
			if (line == null) return LOG_COLOR_NORMAL;
			switch (line.logType) {
				case FlutterBuildApi.LOG_TYPE_ERROR: return LOG_COLOR_ERROR;
				case FlutterBuildApi.LOG_TYPE_WARNING: return LOG_COLOR_WARNING;
				case FlutterBuildApi.LOG_TYPE_SUCCESS: return LOG_COLOR_SUCCESS;
				default:
					if (isContextLine(line.text)) return LOG_COLOR_CONTEXT;
					return LOG_COLOR_NORMAL;
			}
		}

		private String getLogLinePrefix(FlutterBuildApi.BuildLogLine line) {
			if (line == null) return "";
			switch (line.logType) {
				case FlutterBuildApi.LOG_TYPE_ERROR: return "[خطأ] ";
				case FlutterBuildApi.LOG_TYPE_WARNING: return "[تحذير] ";
				case FlutterBuildApi.LOG_TYPE_SUCCESS: return "[OK] ";
				default: return "";
			}
		}

		final class StepVH extends RecyclerView.ViewHolder {
			final TextView circle;
			final TextView name;
			final TextView duration;
			final LinearLayout logsBox;
			StepVH(@NonNull View itemView, TextView circle, TextView name, TextView duration, LinearLayout logsBox) {
				super(itemView);
				this.circle = circle;
				this.name = name;
				this.duration = duration;
				this.logsBox = logsBox;
			}
		}
	}

	public void performUndo() { if (codeEditorView != null) codeEditorView.undo(); }
	public void performRedo() { if (codeEditorView != null) codeEditorView.redo(); }
	public void performZoomIn() { if (codeEditorView != null) codeEditorView.zoomIn(); }
	public void performZoomOut() { if (codeEditorView != null) codeEditorView.zoomOut(); }

	public void showFindBar() {
		if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) {
			Toast.makeText(this, "البحث متاح للملفات النصية فقط", Toast.LENGTH_SHORT).show();
			return;
		}
		if (findReplaceBar == null) {
			findReplaceBar = new FindReplaceBar(this);
			findReplaceBar.setEditor(codeEditorView.getEditor());
			findReplaceBar.setVisibility(View.GONE);
			int toolbarIndex = layoutEditor.indexOfChild(codeToolbar);
			layoutEditor.addView(findReplaceBar, toolbarIndex + 1);
		}
		findReplaceBar.show();
	}

	@Override protected void onPause() {
		super.onPause();
		if (activeVideoViewer != null) {
			try { activeVideoViewer.release(); } catch (Exception ignored) {}
		}
		if (activeAudioViewer != null) {
			try { activeAudioViewer.release(); } catch (Exception ignored) {}
		}
		if (isEditorVisible && currentProjectDir != null) {
			saveAllOpenTabsSilently();
		}
	}

	@Override protected void onStop() {
		super.onStop();
		if (isEditorVisible && currentProjectDir != null) {
			saveAllOpenTabsSilently();
		}
	}

	@Override protected void onResume() {
		super.onResume();
		if (reloadFileAfterAi && isEditorVisible && currentOpenFile != null) {
			reloadFileAfterAi = false;
			reloadFileFromDisk(currentOpenFile);
		}
		if (currentScreen == SCREEN_PROJECTS_LIST) loadProjectsList();
	}

	@Override protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		if (drawerToggle != null) drawerToggle.syncState();
	}

	public static class FileNode {
		public final File file;
		public final int depth;
		public final boolean isExpanded;
		public FileNode(File file, int depth, boolean isExpanded) {
			this.file = file;
			this.depth = depth;
			this.isExpanded = isExpanded;
		}
	}

	public interface OnFileTreeListener {
		void onFileClick(File file, boolean isDirectory);
		void onFileLongClick(File file);
	}

	public static class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.VH> {
		private final List<FileNode> nodes = new ArrayList<>();
		private final OnFileTreeListener listener;
		private final int colorTextPrimary;
		private final int dpUnit;
		private final int colorArrow;
		private final int iconFolder;
		private final int iconFolderOpen;
		private final int iconFile;

		public FileTreeAdapter(OnFileTreeListener listener, int colorTextPrimary,
		                       int dpUnit, int colorArrow, int iconFolder,
		                       int iconFolderOpen, int iconFile) {
			this.listener = listener;
			this.colorTextPrimary = colorTextPrimary;
			this.dpUnit = dpUnit;
			this.colorArrow = colorArrow;
			this.iconFolder = iconFolder;
			this.iconFolderOpen = iconFolderOpen;
			this.iconFile = iconFile;
			setHasStableIds(true);
		}

		@Override public long getItemId(int position) {
			return nodes.get(position).file.getAbsolutePath().hashCode();
		}

		@NonNull
		@Override
		public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LinearLayout row = new LinearLayout(parent.getContext());
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setTextDirection(View.TEXT_DIRECTION_LTR);
			row.setMinimumHeight(dpUnit * 36);

			TextView arrow = new TextView(parent.getContext());
			arrow.setTextColor(colorArrow);
			arrow.setTextSize(10);
			arrow.setGravity(Gravity.CENTER);
			row.addView(arrow, new LinearLayout.LayoutParams(dpUnit * 16, dpUnit * 24));

			ImageView icon = new ImageView(parent.getContext());
			LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpUnit * 20, dpUnit * 20);
			iconParams.setMargins(0, 0, dpUnit * 6, 0);
			row.addView(icon, iconParams);

			TextView text = new TextView(parent.getContext());
			text.setTextColor(colorTextPrimary);
			text.setTextSize(13);
			text.setSingleLine(true);
			text.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
			text.setTextDirection(View.TEXT_DIRECTION_LTR);
			text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
			row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

			row.setBackgroundColor(0x00000000);
			return new VH(row, arrow, icon, text);
		}

		@Override
		public void onBindViewHolder(@NonNull VH holder, int position) {
			FileNode node = nodes.get(position);
			int indentPx = dpUnit * (8 + node.depth * 18);
			holder.row.setPadding(indentPx, 0, dpUnit * 8, 0);
			if (node.file.isDirectory()) {
				holder.arrow.setVisibility(View.VISIBLE);
				holder.arrow.setText(node.isExpanded ? "▼" : "▶");
			} else { holder.arrow.setVisibility(View.INVISIBLE); }
			if (node.file.isDirectory()) {
				holder.icon.setImageResource(node.isExpanded ? iconFolderOpen : iconFolder);
			} else { holder.icon.setImageResource(iconFile); }
			holder.text.setText(node.file.getName());
			holder.row.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (listener != null) listener.onFileClick(node.file, node.file.isDirectory());
				}
			});
			holder.row.setOnLongClickListener(new View.OnLongClickListener() {
				@Override public boolean onLongClick(View v) {
					if (listener != null) listener.onFileLongClick(node.file);
					return true;
				}
			});
		}

		@Override public int getItemCount() { return nodes.size(); }

		public void submitList(List<FileNode> newNodes) {
			DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new FileTreeDiff(this.nodes, newNodes));
			this.nodes.clear();
			this.nodes.addAll(newNodes);
			diff.dispatchUpdatesTo(this);
		}

		public static class VH extends RecyclerView.ViewHolder {
			final LinearLayout row;
			final TextView arrow;
			final ImageView icon;
			final TextView text;
			VH(@NonNull View itemView, TextView arrow, ImageView icon, TextView text) {
				super(itemView);
				this.row = (LinearLayout) itemView;
				this.arrow = arrow;
				this.icon = icon;
				this.text = text;
			}
		}

		static class FileTreeDiff extends DiffUtil.Callback {
			private final List<FileNode> oldList;
			private final List<FileNode> newList;
			FileTreeDiff(List<FileNode> oldList, List<FileNode> newList) {
				this.oldList = new ArrayList<>(oldList);
				this.newList = new ArrayList<>(newList);
			}
			@Override public int getOldListSize() { return oldList.size(); }
			@Override public int getNewListSize() { return newList.size(); }
			@Override public boolean areItemsTheSame(int oldPos, int newPos) {
				return oldList.get(oldPos).file.getAbsolutePath()
						.equals(newList.get(newPos).file.getAbsolutePath());
			}
			@Override public boolean areContentsTheSame(int oldPos, int newPos) {
				FileNode a = oldList.get(oldPos);
				FileNode b = newList.get(newPos);
				return a.depth == b.depth && a.isExpanded == b.isExpanded;
			}
		}
	}

	private void styleDialog(AlertDialog dialog) {
		Window window = dialog.getWindow();
		if (window != null) {
			GradientDrawable gd = new GradientDrawable();
			gd.setColor(COLOR_BACKGROUND);
			gd.setCornerRadius(dp(12));
			gd.setStroke(dp(1), 0xFF30363D);
			window.setBackgroundDrawable(gd);
		}
		int titleViewId = dialog.getContext().getResources().getIdentifier("android:id/alertTitle", null, null);
		if (titleViewId != 0) {
			TextView titleView = dialog.findViewById(titleViewId);
			if (titleView != null) {
				titleView.setTextColor(COLOR_TEXT_PRIMARY);
				titleView.setTypeface(null, android.graphics.Typeface.BOLD);
			}
		}
		int messageViewId = dialog.getContext().getResources().getIdentifier("android:id/message", null, null);
		if (messageViewId != 0) {
			TextView messageView = dialog.findViewById(messageViewId);
			if (messageView != null) messageView.setTextColor(COLOR_TEXT_SECONDARY);
		}
		Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		if (btnPos != null) btnPos.setTextColor(COLOR_TEXT_BLUE);
		Button btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
		if (btnNeg != null) btnNeg.setTextColor(COLOR_TEXT_SECONDARY);
		ListView listView = dialog.getListView();
		if (listView != null) {
			listView.setBackgroundColor(COLOR_BACKGROUND);
			listView.setDivider(new android.graphics.drawable.ColorDrawable(0xFF30363D));
			listView.setDividerHeight(dp(1));
		}
		UiStyle.styleDialog(dialog);
	}
}