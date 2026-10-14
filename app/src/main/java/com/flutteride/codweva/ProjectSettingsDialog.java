package com.flutteride.codweva;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ProjectSettingsDialog extends Dialog {
	
	// لوحة ألوان الواجهة
	private static final int COLOR_BACKGROUND = 0xFF0D1117;
	private static final int COLOR_SURFACE = 0xFF161B22;
	private static final int COLOR_PRIMARY = 0xFF1F6FEB;
	private static final int COLOR_TEXT_PRIMARY = 0xFFE6EDF3;
	private static final int COLOR_TEXT_SECONDARY = COLOR_TEXT_PRIMARY;
	private static final int COLOR_TEXT_BLUE = COLOR_TEXT_PRIMARY;
	
	private Context context;
	private File projectDir;
	private SharedPreferences prefs;
	private String projectName;
	private Runnable onSettingsChanged;
	
	private TextView tvAppName, tvPackageName, tvApiLevel;
	private Button btnChangeIcon, btnClose;
	private ImageView ivIconPreview;
	private LinearLayout btnChangeAppName, btnChangePackageName, btnChangeApi;
	
	private static final int REQUEST_PICK_ICON = 200;
	private File currentIconFile;
	
	public ProjectSettingsDialog(Context context, File projectDir, Runnable onSettingsChanged) {
		super(context);
		this.context = context;
		this.projectDir = projectDir;
		this.onSettingsChanged = onSettingsChanged;
		prefs = context.getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE);
		projectName = projectDir.getName();
	}
	
	private int dp(int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(createLayout());
		
		Window window = getWindow();
		if (window != null) {
			window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.CENTER);
			
			// خلفية شفافة للنافذة للسماح بظهور الحواف المنحنية للحاوية
			window.setBackgroundDrawableResource(android.R.color.transparent);
			
			// تظليل الخلفية
			window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			window.setDimAmount(0.7f);
		}
		
		setupViews();
		loadCurrentSettings();
	}
	
	private View createLayout() {
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(20), dp(24), dp(20), dp(24));
		
		GradientDrawable rootBg = new GradientDrawable();
		rootBg.setColor(COLOR_BACKGROUND);
		rootBg.setCornerRadius(dp(16));
		rootBg.setStroke(dp(1), 0xFF30363D);
		root.setBackground(rootBg);
		
		// العنوان
		TextView title = new TextView(context);
		title.setText("إعدادات المشروع");
		title.setTextSize(18);
		title.setTextColor(COLOR_TEXT_PRIMARY);
		title.setTypeface(null, android.graphics.Typeface.BOLD);
		title.setGravity(Gravity.CENTER);
		title.setPadding(0, 0, 0, dp(24));
		root.addView(title);
		
		// حاوية الأيقونة
		LinearLayout iconContainer = new LinearLayout(context);
		iconContainer.setOrientation(LinearLayout.HORIZONTAL);
		iconContainer.setGravity(Gravity.CENTER_VERTICAL);
		iconContainer.setPadding(dp(16), dp(16), dp(16), dp(16));
		
		GradientDrawable itemBg = new GradientDrawable();
		itemBg.setColor(COLOR_SURFACE);
		itemBg.setCornerRadius(dp(12));
		iconContainer.setBackground(itemBg);
		
		LinearLayout iconTextInfo = new LinearLayout(context);
		iconTextInfo.setOrientation(LinearLayout.VERTICAL);
		iconTextInfo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		
		TextView tvIconLabel = new TextView(context);
		tvIconLabel.setText("أيقونة التطبيق");
		tvIconLabel.setTextSize(14);
		tvIconLabel.setTextColor(COLOR_TEXT_PRIMARY);
		iconTextInfo.addView(tvIconLabel);
		
		TextView tvIconHint = new TextView(context);
		tvIconHint.setText("ستظهر في متجر التطبيقات");
		tvIconHint.setTextSize(11);
		tvIconHint.setTextColor(COLOR_TEXT_SECONDARY);
		iconTextInfo.addView(tvIconHint);
		
		iconContainer.addView(iconTextInfo);
		
		ivIconPreview = new ImageView(context);
		int previewSize = dp(48);
		LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(previewSize, previewSize);
		ivParams.rightMargin = dp(12);
		ivIconPreview.setLayoutParams(ivParams);
		
		// حواف منحنية للمعاينة
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			ivIconPreview.setClipToOutline(true);
			ivIconPreview.setOutlineProvider(new android.view.ViewOutlineProvider() {
				@Override
				public void getOutline(View view, android.graphics.Outline outline) {
					outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
				}
			});
		}
		iconContainer.addView(ivIconPreview);
		
		btnChangeIcon = createStyledButton("تغيير", false);
		btnChangeIcon.setTextSize(12);
		btnChangeIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(60), dp(36)));
		btnChangeIcon.setOnClickListener(v -> pickImageFromGallery());
		iconContainer.addView(btnChangeIcon);
		
		root.addView(iconContainer);
		
		// قائمة الإعدادات
		LinearLayout settingsList = new LinearLayout(context);
		settingsList.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
		LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		listParams.topMargin = dp(16);
		listParams.bottomMargin = dp(24);
		settingsList.setLayoutParams(listParams);
		
		GradientDrawable listBg = new GradientDrawable();
		listBg.setColor(COLOR_SURFACE);
		listBg.setCornerRadius(dp(12));
		settingsList.setBackground(listBg);
		
		btnChangeAppName = createSettingItem("اسم التطبيق", "تغيير اسم التطبيق المعروض");
		settingsList.addView(btnChangeAppName);
		settingsList.addView(createSeparator());
		
		btnChangePackageName = createSettingItem("اسم الحزمة", "تغيير المعرف الفريد (متقدم)");
		settingsList.addView(btnChangePackageName);
		settingsList.addView(createSeparator());
		
		btnChangeApi = createSettingItem("إصدار API", "تغيير Target SDK Level");
		settingsList.addView(btnChangeApi);
		
		root.addView(settingsList);
		
		// زر إغلاق
		btnClose = createStyledButton("إغلاق", true);
		btnClose.setLayoutParams(new LinearLayout.LayoutParams(
		LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
		btnClose.setOnClickListener(v -> dismiss());
		root.addView(btnClose);
		
		return root;
	}
	
	private LinearLayout createSettingItem(String title, String subtitle) {
		LinearLayout item = new LinearLayout(context);
		item.setOrientation(LinearLayout.VERTICAL);
		item.setPadding(dp(16), dp(16), dp(16), dp(16));
		item.setClickable(true);
		item.setFocusable(true);
		
		TextView tvTitle = new TextView(context);
		tvTitle.setText(title);
		tvTitle.setTextSize(14);
		tvTitle.setTextColor(COLOR_TEXT_PRIMARY);
		item.addView(tvTitle);
		
		TextView tvSubtitle = new TextView(context);
		tvSubtitle.setText(subtitle);
		tvSubtitle.setTextSize(11);
		tvSubtitle.setTextColor(COLOR_TEXT_SECONDARY);
		item.addView(tvSubtitle);
		
		return item;
	}
	
	private View createSeparator() {
		View separator = new View(context);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
		LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
		params.leftMargin = dp(16);
		params.rightMargin = dp(16);
		separator.setLayoutParams(params);
		separator.setBackgroundColor(0xFF30363D);
		return separator;
	}
	
	private Button createStyledButton(String text, boolean isPrimary) {
		Button btn = new Button(context);
		btn.setText(text);
		btn.setTextColor(Color.WHITE);
		btn.setAllCaps(false);
		btn.setTextSize(14);
		
		GradientDrawable gd = new GradientDrawable();
		gd.setColor(isPrimary ? COLOR_PRIMARY : 0xFF21262D);
		gd.setCornerRadius(dp(8));
		if (!isPrimary) {
			gd.setStroke(dp(1), 0xFF30363D);
		}
		btn.setBackground(gd);
		
		return btn;
	}
	
	private void setupViews() {
		btnChangeAppName.setOnClickListener(v -> showChangeAppNameDialog());
		btnChangePackageName.setOnClickListener(v -> showChangePackageNameDialog());
		btnChangeApi.setOnClickListener(v -> showChangeApiDialog());
	}
	
	private void loadCurrentSettings() {
		loadIconPreview();
	}
	
	private void loadIconPreview() {
		File iconFile = new File(projectDir, "android/app/src/main/res/drawable/ic_launcher.png");
		if (iconFile.exists()) {
			currentIconFile = iconFile;
			ivIconPreview.setImageURI(Uri.fromFile(iconFile));
			} else {
			ivIconPreview.setImageResource(android.R.drawable.sym_def_app_icon);
		}
	}
	
	private void pickImageFromGallery() {
		Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/*");
		if (context instanceof Activity) {
			((Activity) context).startActivityForResult(intent, REQUEST_PICK_ICON);
		}
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_PICK_ICON && resultCode == Activity.RESULT_OK && data != null) {
			Uri selectedImageUri = data.getData();
			if (selectedImageUri != null) {
				saveIconToProject(selectedImageUri);
			}
		}
	}
	
	private void saveIconToProject(Uri imageUri) {
		try {
			File drawableDir = new File(projectDir, "android/app/src/main/res/drawable");
			if (!drawableDir.exists()) {
				drawableDir.mkdirs();
			}
			
			File iconFile = new File(drawableDir, "ic_launcher.png");
			InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
			FileOutputStream outputStream = new FileOutputStream(iconFile);
			
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			
			inputStream.close();
			outputStream.close();
			
			currentIconFile = iconFile;
			ivIconPreview.setImageURI(imageUri);
			Toast.makeText(context, "تم تغيير أيقونة التطبيق", Toast.LENGTH_SHORT).show();
			
			if (onSettingsChanged != null) {
				onSettingsChanged.run();
			}
			
			} catch (Exception e) {
			Toast.makeText(context, "خطأ في حفظ الأيقونة: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}
	
	private void showChangeAppNameDialog() {
		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
		builder.setTitle("تغيير اسم التطبيق");
		
		final EditText input = new EditText(context);
		input.setHint("اسم التطبيق الجديد");
		input.setText(prefs.getString("name_" + projectName, ""));
		input.setPadding(dp(16), dp(12), dp(16), dp(12));
		builder.setView(input);
		
		builder.setPositiveButton("حفظ", (dialog, which) -> {
			String newName = input.getText().toString().trim();
			if (!newName.isEmpty()) {
				prefs.edit().putString("name_" + projectName, newName).apply();
				updateAndroidManifestLabel(newName);
				Toast.makeText(context, "تم تغيير اسم التطبيق", Toast.LENGTH_SHORT).show();
				if (onSettingsChanged != null) {
					onSettingsChanged.run();
				}
			}
		});
		builder.setNegativeButton("إلغاء", null);
		android.app.AlertDialog dialog = builder.create();
		dialog.show();
		UiStyle.styleDialog(dialog);
	}
	
	private void updateAndroidManifestLabel(String newName) {
		File manifestFile = new File(projectDir, "android/app/src/main/AndroidManifest.xml");
		if (manifestFile.exists()) {
			try {
				java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(manifestFile));
				StringBuilder content = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.contains("android:label=")) {
						line = line.replaceAll("android:label=\"[^\"]*\"", "android:label=\"" + newName + "\"");
					}
					content.append(line).append("\n");
				}
				reader.close();
				
				java.io.FileWriter writer = new java.io.FileWriter(manifestFile);
				writer.write(content.toString());
				writer.close();
				
				} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void showChangePackageNameDialog() {
		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
		builder.setTitle("تغيير اسم الحزمة");
		builder.setMessage("تحذير: تغيير اسم الحزمة قد يتطلب تعديلات إضافية.");
		
		final EditText input = new EditText(context);
		input.setHint("com.example.newpackage");
		input.setText(prefs.getString("package_" + projectName, ""));
		input.setPadding(dp(16), dp(12), dp(16), dp(12));
		builder.setView(input);
		
		builder.setPositiveButton("تغيير", (dialog, which) -> {
			String newPackage = input.getText().toString().trim();
			if (!newPackage.isEmpty() && isValidPackageName(newPackage)) {
				prefs.edit().putString("package_" + projectName, newPackage).apply();
				Toast.makeText(context, "تم تغيير اسم الحزمة", Toast.LENGTH_LONG).show();
				} else {
				Toast.makeText(context, "اسم حزمة غير صالح", Toast.LENGTH_SHORT).show();
			}
		});
		builder.setNegativeButton("إلغاء", null);
		android.app.AlertDialog dialog = builder.create();
		dialog.show();
		UiStyle.styleDialog(dialog);
	}
	
	private void showChangeApiDialog() {
		final String[] apiLevels = {"21", "24", "28", "31", "33"};
		final String[] apiNames = {"Android 5.0 (API 21)", "Android 7.0 (API 24)", "Android 9 (API 28)", "Android 12 (API 31)", "Android 13 (API 33)"};
		
		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
		builder.setTitle("اختر Target API");
		
		int currentApi = Integer.parseInt(prefs.getString("api_" + projectName, "31"));
		int selectedIndex = 0;
		for (int i = 0; i < apiLevels.length; i++) {
			if (Integer.parseInt(apiLevels[i]) == currentApi) {
				selectedIndex = i;
				break;
			}
		}
		
		builder.setSingleChoiceItems(UiStyle.dialogChoiceAdapter(context, apiNames), selectedIndex, (dialog, which) -> {
			String newApi = apiLevels[which];
			prefs.edit().putString("api_" + projectName, newApi).apply();
			updateBuildGradleApi(newApi);
			Toast.makeText(context, "تم تغيير Target API إلى " + newApi, Toast.LENGTH_SHORT).show();
			dialog.dismiss();
		});
		
		builder.setNegativeButton("إلغاء", null);
		android.app.AlertDialog dialog = builder.create();
		dialog.show();
		UiStyle.styleDialog(dialog);
	}
	
	private void updateBuildGradleApi(String apiLevel) {
		File buildGradle = new File(projectDir, "android/app/build.gradle");
		if (buildGradle.exists()) {
			try {
				java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(buildGradle));
				StringBuilder content = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.contains("compileSdkVersion")) {
						line = "    compileSdkVersion " + apiLevel;
						} else if (line.contains("targetSdkVersion")) {
						line = "        targetSdkVersion " + apiLevel;
					}
					content.append(line).append("\n");
				}
				reader.close();
				
				java.io.FileWriter writer = new java.io.FileWriter(buildGradle);
				writer.write(content.toString());
				writer.close();
				
				} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean isValidPackageName(String packageName) {
		if (packageName == null || packageName.isEmpty()) return false;
		String[] parts = packageName.split("\\.");
		if (parts.length < 2) return false;
		for (String part : parts) {
			if (part.isEmpty()) return false;
			if (!part.matches("^[a-z][a-z0-9_]*$")) return false;
		}
		return true;
	}
}
