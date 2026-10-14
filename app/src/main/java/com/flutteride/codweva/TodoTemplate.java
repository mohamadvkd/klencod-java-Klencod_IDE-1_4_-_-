package com.flutteride.codweva;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * ✅ قالب "مدير المهام" — To-Do List احترافي.
 * يحتوي على:
 *   - lib/main.dart
 *   - pubspec.yaml (مع shared_preferences)
 *   - .github/workflows/main.yml
 *   - android/... كامل
 */
public final class TodoTemplate {

    private TodoTemplate() { }

    public static boolean write(File projectDir, String packageName,
                                 String appName, String apiLevel) {
        try {
            String packagePath = packageName.replace('.', '/');

            // المسارات
            File libDir = new File(projectDir, "lib");
            File androidDir = new File(projectDir, "android");
            File appDir = new File(androidDir, "app");
            File srcDir = new File(appDir, "src");
            File mainDir = new File(srcDir, "main");
            File resDir = new File(mainDir, "res");
            File valuesDir = new File(mainDir, "res/values");
            File javaPackageDir = new File(mainDir, "java/" + packagePath);
            File kotlinDir = new File(mainDir, "kotlin/" + packagePath);
            File githubDir = new File(projectDir, ".github");
            File workflowsDir = new File(githubDir, "workflows");

            // إنشاء المجلدات
            if (!libDir.mkdirs()) return false;
            if (!javaPackageDir.mkdirs()) return false;
            if (!resDir.mkdirs()) return false;
            if (!valuesDir.mkdirs()) return false;
            if (!kotlinDir.mkdirs()) return false;
            if (!workflowsDir.mkdirs()) return false;

            // اسم snake_case للمشروع (يستخدمه pubspec)
            String appNameSnakeCase = toSnakeCase(appName);

            // 1) pubspec.yaml (مع shared_preferences)
            writeFile(new File(projectDir, "pubspec.yaml"),
                    buildPubspecYaml(appNameSnakeCase));

            // 2) lib/main.dart (تطبيق To-Do)
            writeFile(new File(libDir, "main.dart"), buildMainDart(appName));

            // 3) .gitignore
            writeFile(new File(projectDir, ".gitignore"), buildGitignore());

            // 4) .github/workflows/main.yml
            writeFile(new File(workflowsDir, "main.yml"), buildWorkflowYml());

            // 5) android/build.gradle
            writeFile(new File(androidDir, "build.gradle"), buildRootBuildGradle());

            // 6) android/app/build.gradle
            writeFile(new File(appDir, "build.gradle"),
                    buildAppBuildGradle(packageName, apiLevel));

            // 7) android/settings.gradle
            writeFile(new File(androidDir, "settings.gradle"), buildSettingsGradle());

            // 8) android/gradle.properties
            writeFile(new File(androidDir, "gradle.properties"), buildGradleProperties());

            // 9) android/local.properties (فارغ)
            writeFile(new File(androidDir, "local.properties"), "");

            // 10) android/app/src/main/res/values/styles.xml
            writeFile(new File(valuesDir, "styles.xml"), buildStylesXml());

            // 11) android/app/src/main/AndroidManifest.xml
            writeFile(new File(mainDir, "AndroidManifest.xml"),
                    buildAndroidManifest(packageName, appName));

            // 12) MainActivity.kt
            writeFile(new File(kotlinDir, "MainActivity.kt"),
                    buildMainActivityKt(packageName));

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================
    // الأدوات
    // ============================================================

    private static String toSnakeCase(String value) {
        if (value == null || value.isEmpty()) return "flutter_app";
        String result = value.toLowerCase()
                .replaceAll(" ", "_")
                .replaceAll("[^a-z0-9_]", "");
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) {
            result = "flutter_app";
        }
        return result;
    }

    private static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
            writer.flush();
        } finally {
            writer.close();
        }
    }

    // ============================================================
    // ملف pubspec.yaml
    // ============================================================
    private static String buildPubspecYaml(String appNameSnakeCase) {
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(appNameSnakeCase).append("\n");
        sb.append("description: A professional task manager built with Flutter.\n");
        sb.append("publish_to: 'none'\n");
        sb.append("version: 1.0.0+1\n");
        sb.append("\n");
        sb.append("environment:\n");
        sb.append("  sdk: '>=2.17.0 <4.0.0'\n");
        sb.append("\n");
        sb.append("dependencies:\n");
        sb.append("  flutter:\n");
        sb.append("    sdk: flutter\n");
        sb.append("  shared_preferences: ^2.2.2\n");
        sb.append("\n");
        sb.append("flutter:\n");
        sb.append("  uses-material-design: true\n");
        return sb.toString();
    }

    // ============================================================
    // ملف lib/main.dart (تطبيق To-Do)
    // ============================================================
    private static String buildMainDart(String appName) {
        // استبدال __APP_TITLE__ باسم التطبيق (مع تنظيف المحارف الخطرة)
        String safeTitle = appName == null ? "مهامي" : appName.replace("'", "\\'");
        StringBuilder sb = new StringBuilder();

        sb.append("import 'dart:convert';\n");
        sb.append("import 'package:flutter/material.dart';\n");
        sb.append("import 'package:flutter/services.dart';\n");
        sb.append("import 'package:shared_preferences/shared_preferences.dart';\n");
        sb.append("\n");
        sb.append("void main() async {\n");
        sb.append("  WidgetsFlutterBinding.ensureInitialized();\n");
        sb.append("  runApp(const TaskMasterApp());\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("class AppTheme {\n");
        sb.append("  static const Color background = Color(0xFF0D1117);\n");
        sb.append("  static const Color surface = Color(0xFF161B22);\n");
        sb.append("  static const Color surfaceAlt = Color(0xFF21262D);\n");
        sb.append("  static const Color border = Color(0xFF30363D);\n");
        sb.append("  static const Color primary = Color(0xFF1F6FEB);\n");
        sb.append("  static const Color primaryLight = Color(0xFF58A6FF);\n");
        sb.append("  static const Color success = Color(0xFF2EA043);\n");
        sb.append("  static const Color danger = Color(0xFFCF222E);\n");
        sb.append("  static const Color warning = Color(0xFFD29922);\n");
        sb.append("  static const Color textPrimary = Color(0xFFE6EDF3);\n");
        sb.append("  static const Color textMuted = Color(0xFF8B949E);\n");
        sb.append("\n");
        sb.append("  static ThemeData get dark => ThemeData(\n");
        sb.append("        useMaterial3: true,\n");
        sb.append("        brightness: Brightness.dark,\n");
        sb.append("        scaffoldBackgroundColor: background,\n");
        sb.append("        colorScheme: const ColorScheme.dark(\n");
        sb.append("          primary: primary,\n");
        sb.append("          secondary: primaryLight,\n");
        sb.append("          surface: surface,\n");
        sb.append("          background: background,\n");
        sb.append("          error: danger,\n");
        sb.append("          onPrimary: Colors.white,\n");
        sb.append("          onSurface: textPrimary,\n");
        sb.append("        ),\n");
        sb.append("        appBarTheme: const AppBarTheme(\n");
        sb.append("          backgroundColor: background,\n");
        sb.append("          elevation: 0,\n");
        sb.append("          centerTitle: true,\n");
        sb.append("          titleTextStyle: TextStyle(\n");
        sb.append("            color: textPrimary,\n");
        sb.append("            fontSize: 20,\n");
        sb.append("            fontWeight: FontWeight.bold,\n");
        sb.append("          ),\n");
        sb.append("        ),\n");
        sb.append("        floatingActionButtonTheme: const FloatingActionButtonThemeData(\n");
        sb.append("          backgroundColor: primary,\n");
        sb.append("          foregroundColor: Colors.white,\n");
        sb.append("        ),\n");
        sb.append("        inputDecorationTheme: InputDecorationTheme(\n");
        sb.append("          filled: true,\n");
        sb.append("          fillColor: surfaceAlt,\n");
        sb.append("          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),\n");
        sb.append("          border: OutlineInputBorder(\n");
        sb.append("            borderRadius: BorderRadius.circular(14),\n");
        sb.append("            borderSide: const BorderSide(color: border),\n");
        sb.append("          ),\n");
        sb.append("          enabledBorder: OutlineInputBorder(\n");
        sb.append("            borderRadius: BorderRadius.circular(14),\n");
        sb.append("            borderSide: const BorderSide(color: border),\n");
        sb.append("          ),\n");
        sb.append("          focusedBorder: OutlineInputBorder(\n");
        sb.append("            borderRadius: BorderRadius.circular(14),\n");
        sb.append("            borderSide: const BorderSide(color: primaryLight, width: 1.5),\n");
        sb.append("          ),\n");
        sb.append("        ),\n");
        sb.append("      );\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("class Task {\n");
        sb.append("  final String id;\n");
        sb.append("  String title;\n");
        sb.append("  bool done;\n");
        sb.append("  final DateTime createdAt;\n");
        sb.append("\n");
        sb.append("  Task({\n");
        sb.append("    required this.id,\n");
        sb.append("    required this.title,\n");
        sb.append("    this.done = false,\n");
        sb.append("    required this.createdAt,\n");
        sb.append("  });\n");
        sb.append("\n");
        sb.append("  Map<String, dynamic> toJson() => {\n");
        sb.append("        'id': id,\n");
        sb.append("        'title': title,\n");
        sb.append("        'done': done,\n");
        sb.append("        'createdAt': createdAt.toIso8601String(),\n");
        sb.append("      };\n");
        sb.append("\n");
        sb.append("  factory Task.fromJson(Map<String, dynamic> json) => Task(\n");
        sb.append("        id: json['id'] as String,\n");
        sb.append("        title: json['title'] as String,\n");
        sb.append("        done: json['done'] as bool? ?? false,\n");
        sb.append("        createdAt: DateTime.parse(json['createdAt'] as String),\n");
        sb.append("      );\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("class TaskMasterApp extends StatelessWidget {\n");
        sb.append("  const TaskMasterApp({super.key});\n");
        sb.append("\n");
        sb.append("  @override\n");
        sb.append("  Widget build(BuildContext context) {\n");
        sb.append("    return MaterialApp(\n");
        sb.append("      title: '").append(safeTitle).append("',\n");
        sb.append("      debugShowCheckedModeBanner: false,\n");
        sb.append("      theme: AppTheme.dark,\n");
        sb.append("      home: const TaskHomePage(),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("enum TaskFilter { all, active, completed }\n");
        sb.append("\n");
        sb.append("class TaskHomePage extends StatefulWidget {\n");
        sb.append("  const TaskHomePage({super.key});\n");
        sb.append("\n");
        sb.append("  @override\n");
        sb.append("  State<TaskHomePage> createState() => _TaskHomePageState();\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("class _TaskHomePageState extends State<TaskHomePage>\n");
        sb.append("    with SingleTickerProviderStateMixin {\n");
        sb.append("  static const String _storageKey = 'klencod_tasks_v1';\n");
        sb.append("\n");
        sb.append("  final List<Task> _tasks = [];\n");
        sb.append("  final TextEditingController _searchController = TextEditingController();\n");
        sb.append("  TaskFilter _filter = TaskFilter.all;\n");
        sb.append("  String _searchQuery = '';\n");
        sb.append("  bool _loading = true;\n");
        sb.append("  late AnimationController _fadeController;\n");
        sb.append("\n");
        sb.append("  @override\n");
        sb.append("  void initState() {\n");
        sb.append("    super.initState();\n");
        sb.append("    _fadeController = AnimationController(\n");
        sb.append("      vsync: this,\n");
        sb.append("      duration: const Duration(milliseconds: 300),\n");
        sb.append("    );\n");
        sb.append("    _loadTasks();\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  @override\n");
        sb.append("  void dispose() {\n");
        sb.append("    _fadeController.dispose();\n");
        sb.append("    _searchController.dispose();\n");
        sb.append("    super.dispose();\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Future<void> _loadTasks() async {\n");
        sb.append("    try {\n");
        sb.append("      final prefs = await SharedPreferences.getInstance();\n");
        sb.append("      final data = prefs.getString(_storageKey);\n");
        sb.append("      if (data != null && data.isNotEmpty) {\n");
        sb.append("        final list = jsonDecode(data) as List<dynamic>;\n");
        sb.append("        _tasks.clear();\n");
        sb.append("        _tasks.addAll(list.map((e) => Task.fromJson(e as Map<String, dynamic>)));\n");
        sb.append("      }\n");
        sb.append("    } catch (_) {}\n");
        sb.append("    setState(() => _loading = false);\n");
        sb.append("    _fadeController.forward();\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Future<void> _saveTasks() async {\n");
        sb.append("    try {\n");
        sb.append("      final prefs = await SharedPreferences.getInstance();\n");
        sb.append("      final data = jsonEncode(_tasks.map((t) => t.toJson()).toList());\n");
        sb.append("      await prefs.setString(_storageKey, data);\n");
        sb.append("    } catch (_) {}\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  void _addTask(String title) {\n");
        sb.append("    final trimmed = title.trim();\n");
        sb.append("    if (trimmed.isEmpty) return;\n");
        sb.append("    setState(() {\n");
        sb.append("      _tasks.insert(\n");
        sb.append("        0,\n");
        sb.append("        Task(\n");
        sb.append("          id: DateTime.now().microsecondsSinceEpoch.toString(),\n");
        sb.append("          title: trimmed,\n");
        sb.append("          createdAt: DateTime.now(),\n");
        sb.append("        ),\n");
        sb.append("      );\n");
        sb.append("    });\n");
        sb.append("    _saveTasks();\n");
        sb.append("    HapticFeedback.lightImpact();\n");
        sb.append("    _showSnack('تمت إضافة المهمة', success: true);\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  void _toggleTask(Task task) {\n");
        sb.append("    setState(() => task.done = !task.done);\n");
        sb.append("    _saveTasks();\n");
        sb.append("    HapticFeedback.selectionClick();\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  void _deleteTask(Task task) {\n");
        sb.append("    final index = _tasks.indexOf(task);\n");
        sb.append("    if (index == -1) return;\n");
        sb.append("    setState(() => _tasks.removeAt(index));\n");
        sb.append("    _saveTasks();\n");
        sb.append("    _showSnack(\n");
        sb.append("      'تم حذف المهمة',\n");
        sb.append("      actionLabel: 'تراجع',\n");
        sb.append("      onAction: () {\n");
        sb.append("        setState(() => _tasks.insert(index, task));\n");
        sb.append("        _saveTasks();\n");
        sb.append("      },\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  void _clearCompleted() {\n");
        sb.append("    final completed = _tasks.where((t) => t.done).toList();\n");
        sb.append("    if (completed.isEmpty) {\n");
        sb.append("      _showSnack('لا توجد مهام مكتملة');\n");
        sb.append("      return;\n");
        sb.append("    }\n");
        sb.append("    showDialog(\n");
        sb.append("      context: context,\n");
        sb.append("      builder: (ctx) => _buildConfirmDialog(\n");
        sb.append("        ctx,\n");
        sb.append("        title: 'حذف المهام المكتملة',\n");
        sb.append("        message: 'سيتم حذف ${completed.length} مهمة مكتملة. لا يمكن التراجع.',\n");
        sb.append("        onConfirm: () {\n");
        sb.append("          setState(() => _tasks.removeWhere((t) => t.done));\n");
        sb.append("          _saveTasks();\n");
        sb.append("          Navigator.pop(ctx);\n");
        sb.append("          _showSnack('تم حذف ${completed.length} مهمة', success: true);\n");
        sb.append("        },\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  void _showSnack(String message,\n");
        sb.append("      {String? actionLabel, VoidCallback? onAction, bool success = false}) {\n");
        sb.append("    if (!mounted) return;\n");
        sb.append("    ScaffoldMessenger.of(context).clearSnackBars();\n");
        sb.append("    ScaffoldMessenger.of(context).showSnackBar(\n");
        sb.append("      SnackBar(\n");
        sb.append("        content: Row(\n");
        sb.append("          children: [\n");
        sb.append("            Icon(\n");
        sb.append("              success ? Icons.check_circle_rounded : Icons.info_outline_rounded,\n");
        sb.append("              color: success ? AppTheme.success : AppTheme.textPrimary,\n");
        sb.append("              size: 20,\n");
        sb.append("            ),\n");
        sb.append("            const SizedBox(width: 10),\n");
        sb.append("            Expanded(\n");
        sb.append("              child: Text(\n");
        sb.append("                message,\n");
        sb.append("                style: const TextStyle(color: AppTheme.textPrimary),\n");
        sb.append("              ),\n");
        sb.append("            ),\n");
        sb.append("          ],\n");
        sb.append("        ),\n");
        sb.append("        backgroundColor: AppTheme.surfaceAlt,\n");
        sb.append("        behavior: SnackBarBehavior.floating,\n");
        sb.append("        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),\n");
        sb.append("        margin: const EdgeInsets.all(16),\n");
        sb.append("        duration: const Duration(seconds: 3),\n");
        sb.append("        action: actionLabel != null\n");
        sb.append("            ? SnackBarAction(\n");
        sb.append("                label: actionLabel,\n");
        sb.append("                textColor: AppTheme.primaryLight,\n");
        sb.append("                onPressed: onAction ?? () {},\n");
        sb.append("              )\n");
        sb.append("            : null,\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _buildConfirmDialog(\n");
        sb.append("    BuildContext ctx, {\n");
        sb.append("    required String title,\n");
        sb.append("    required String message,\n");
        sb.append("    required VoidCallback onConfirm,\n");
        sb.append("  }) {\n");
        sb.append("    return AlertDialog(\n");
        sb.append("      backgroundColor: AppTheme.surface,\n");
        sb.append("      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),\n");
        sb.append("      title: Text(title, style: const TextStyle(color: AppTheme.textPrimary)),\n");
        sb.append("      content: Text(message, style: const TextStyle(color: AppTheme.textMuted)),\n");
        sb.append("      actions: [\n");
        sb.append("        TextButton(\n");
        sb.append("          onPressed: () => Navigator.pop(ctx),\n");
        sb.append("          child: const Text('إلغاء',\n");
        sb.append("              style: TextStyle(color: AppTheme.textMuted)),\n");
        sb.append("        ),\n");
        sb.append("        TextButton(\n");
        sb.append("          onPressed: onConfirm,\n");
        sb.append("          child: const Text('تأكيد',\n");
        sb.append("              style: TextStyle(\n");
        sb.append("                  color: AppTheme.danger, fontWeight: FontWeight.bold)),\n");
        sb.append("        ),\n");
        sb.append("      ],\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  void _openAddDialog() {\n");
        sb.append("    final controller = TextEditingController();\n");
        sb.append("    showDialog(\n");
        sb.append("      context: context,\n");
        sb.append("      builder: (ctx) => AlertDialog(\n");
        sb.append("        backgroundColor: AppTheme.surface,\n");
        sb.append("        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),\n");
        sb.append("        title: Row(\n");
        sb.append("          children: const [\n");
        sb.append("            Icon(Icons.add_task_rounded, color: AppTheme.primaryLight),\n");
        sb.append("            SizedBox(width: 10),\n");
        sb.append("            Text('مهمة جديدة',\n");
        sb.append("                style: TextStyle(color: AppTheme.textPrimary)),\n");
        sb.append("          ],\n");
        sb.append("        ),\n");
        sb.append("        content: TextField(\n");
        sb.append("          controller: controller,\n");
        sb.append("          autofocus: true,\n");
        sb.append("          textInputAction: TextInputAction.done,\n");
        sb.append("          onSubmitted: (v) {\n");
        sb.append("            _addTask(v);\n");
        sb.append("            Navigator.pop(ctx);\n");
        sb.append("          },\n");
        sb.append("          decoration: const InputDecoration(\n");
        sb.append("            hintText: 'اكتب المهمة...',\n");
        sb.append("            prefixIcon: Icon(Icons.edit_rounded),\n");
        sb.append("          ),\n");
        sb.append("          style: const TextStyle(color: AppTheme.textPrimary),\n");
        sb.append("        ),\n");
        sb.append("        actions: [\n");
        sb.append("          TextButton(\n");
        sb.append("            onPressed: () => Navigator.pop(ctx),\n");
        sb.append("            child: const Text('إلغاء',\n");
        sb.append("                style: TextStyle(color: AppTheme.textMuted)),\n");
        sb.append("          ),\n");
        sb.append("          FilledButton(\n");
        sb.append("            onPressed: () {\n");
        sb.append("              _addTask(controller.text);\n");
        sb.append("              Navigator.pop(ctx);\n");
        sb.append("            },\n");
        sb.append("            style: FilledButton.styleFrom(backgroundColor: AppTheme.primary),\n");
        sb.append("            child: const Text('إضافة'),\n");
        sb.append("          ),\n");
        sb.append("        ],\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  List<Task> get _visibleTasks {\n");
        sb.append("    Iterable<Task> result = _tasks;\n");
        sb.append("    if (_filter == TaskFilter.active) {\n");
        sb.append("      result = result.where((t) => !t.done);\n");
        sb.append("    } else if (_filter == TaskFilter.completed) {\n");
        sb.append("      result = result.where((t) => t.done);\n");
        sb.append("    }\n");
        sb.append("    if (_searchQuery.isNotEmpty) {\n");
        sb.append("      final q = _searchQuery.toLowerCase();\n");
        sb.append("      result = result.where((t) => t.title.toLowerCase().contains(q));\n");
        sb.append("    }\n");
        sb.append("    return result.toList();\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  @override\n");
        sb.append("  Widget build(BuildContext context) {\n");
        sb.append("    if (_loading) {\n");
        sb.append("      return const Scaffold(\n");
        sb.append("        body: Center(\n");
        sb.append("          child: CircularProgressIndicator(color: AppTheme.primaryLight),\n");
        sb.append("        ),\n");
        sb.append("      );\n");
        sb.append("    }\n");
        sb.append("\n");
        sb.append("    final visible = _visibleTasks;\n");
        sb.append("    final total = _tasks.length;\n");
        sb.append("    final completed = _tasks.where((t) => t.done).length;\n");
        sb.append("    final active = total - completed;\n");
        sb.append("    final progress = total == 0 ? 0.0 : completed / total;\n");
        sb.append("\n");
        sb.append("    return Scaffold(\n");
        sb.append("      appBar: AppBar(\n");
        sb.append("        title: const Text('").append(safeTitle).append("'),\n");
        sb.append("        actions: [\n");
        sb.append("          if (completed > 0)\n");
        sb.append("            IconButton(\n");
        sb.append("              tooltip: 'حذف المكتملة',\n");
        sb.append("              icon: const Icon(Icons.delete_sweep_rounded),\n");
        sb.append("              onPressed: _clearCompleted,\n");
        sb.append("            ),\n");
        sb.append("        ],\n");
        sb.append("      ),\n");
        sb.append("      floatingActionButton: FloatingActionButton.extended(\n");
        sb.append("        onPressed: _openAddDialog,\n");
        sb.append("        icon: const Icon(Icons.add_rounded),\n");
        sb.append("        label: const Text('مهمة جديدة',\n");
        sb.append("            style: TextStyle(fontWeight: FontWeight.bold)),\n");
        sb.append("      ),\n");
        sb.append("      body: Column(\n");
        sb.append("        children: [\n");
        sb.append("          _buildHeader(total, completed, active, progress),\n");
        sb.append("          _buildSearchBar(),\n");
        sb.append("          _buildFilters(total, active, completed),\n");
        sb.append("          Expanded(\n");
        sb.append("            child: visible.isEmpty\n");
        sb.append("                ? _buildEmptyState()\n");
        sb.append("                : _buildTaskList(visible),\n");
        sb.append("          ),\n");
        sb.append("        ],\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _buildHeader(int total, int completed, int active, double progress) {\n");
        sb.append("    return Padding(\n");
        sb.append("      padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),\n");
        sb.append("      child: Container(\n");
        sb.append("        padding: const EdgeInsets.all(18),\n");
        sb.append("        decoration: BoxDecoration(\n");
        sb.append("          gradient: const LinearGradient(\n");
        sb.append("            colors: [Color(0xFF1F6FEB), Color(0xFF0D419D)],\n");
        sb.append("            begin: Alignment.topLeft,\n");
        sb.append("            end: Alignment.bottomRight,\n");
        sb.append("          ),\n");
        sb.append("          borderRadius: BorderRadius.circular(20),\n");
        sb.append("          boxShadow: [\n");
        sb.append("            BoxShadow(\n");
        sb.append("              color: AppTheme.primary.withOpacity(0.3),\n");
        sb.append("              blurRadius: 16,\n");
        sb.append("              offset: const Offset(0, 6),\n");
        sb.append("            ),\n");
        sb.append("          ],\n");
        sb.append("        ),\n");
        sb.append("        child: Column(\n");
        sb.append("          crossAxisAlignment: CrossAxisAlignment.start,\n");
        sb.append("          children: [\n");
        sb.append("            Row(\n");
        sb.append("              children: [\n");
        sb.append("                const Icon(Icons.task_alt_rounded,\n");
        sb.append("                    color: Colors.white, size: 28),\n");
        sb.append("                const SizedBox(width: 10),\n");
        sb.append("                Text(\n");
        sb.append("                  total == 0 ? 'لا توجد مهام بعد' : 'مهامك',\n");
        sb.append("                  style: const TextStyle(\n");
        sb.append("                    color: Colors.white,\n");
        sb.append("                    fontSize: 18,\n");
        sb.append("                    fontWeight: FontWeight.bold,\n");
        sb.append("                  ),\n");
        sb.append("                ),\n");
        sb.append("              ],\n");
        sb.append("            ),\n");
        sb.append("            const SizedBox(height: 14),\n");
        sb.append("            Row(\n");
        sb.append("              mainAxisAlignment: MainAxisAlignment.spaceBetween,\n");
        sb.append("              children: [\n");
        sb.append("                _statChip('الإجمالي', total, Colors.white),\n");
        sb.append("                _statChip('نشطة', active, AppTheme.warning),\n");
        sb.append("                _statChip('مكتملة', completed, AppTheme.success),\n");
        sb.append("              ],\n");
        sb.append("            ),\n");
        sb.append("            const SizedBox(height: 16),\n");
        sb.append("            ClipRRect(\n");
        sb.append("              borderRadius: BorderRadius.circular(8),\n");
        sb.append("              child: TweenAnimationBuilder<double>(\n");
        sb.append("                tween: Tween(begin: 0, end: progress),\n");
        sb.append("                duration: const Duration(milliseconds: 600),\n");
        sb.append("                builder: (_, value, __) => LinearProgressIndicator(\n");
        sb.append("                  value: value,\n");
        sb.append("                  minHeight: 8,\n");
        sb.append("                  backgroundColor: Colors.white24,\n");
        sb.append("                  valueColor:\n");
        sb.append("                      const AlwaysStoppedAnimation<Color>(Colors.white),\n");
        sb.append("                ),\n");
        sb.append("              ),\n");
        sb.append("            ),\n");
        sb.append("            const SizedBox(height: 6),\n");
        sb.append("            Text(\n");
        sb.append("              '${(progress * 100).toStringAsFixed(0)}% إنجاز',\n");
        sb.append("              style: const TextStyle(color: Colors.white70, fontSize: 12),\n");
        sb.append("            ),\n");
        sb.append("          ],\n");
        sb.append("        ),\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _statChip(String label, int value, Color color) {\n");
        sb.append("    return Container(\n");
        sb.append("      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),\n");
        sb.append("      decoration: BoxDecoration(\n");
        sb.append("        color: Colors.white.withOpacity(0.15),\n");
        sb.append("        borderRadius: BorderRadius.circular(10),\n");
        sb.append("      ),\n");
        sb.append("      child: Column(\n");
        sb.append("        children: [\n");
        sb.append("          Text(\n");
        sb.append("            '$value',\n");
        sb.append("            style: TextStyle(\n");
        sb.append("              color: color,\n");
        sb.append("              fontSize: 18,\n");
        sb.append("              fontWeight: FontWeight.bold,\n");
        sb.append("            ),\n");
        sb.append("          ),\n");
        sb.append("          Text(\n");
        sb.append("            label,\n");
        sb.append("            style: const TextStyle(color: Colors.white70, fontSize: 11),\n");
        sb.append("          ),\n");
        sb.append("        ],\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _buildSearchBar() {\n");
        sb.append("    return Padding(\n");
        sb.append("      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),\n");
        sb.append("      child: TextField(\n");
        sb.append("        controller: _searchController,\n");
        sb.append("        onChanged: (v) => setState(() => _searchQuery = v),\n");
        sb.append("        style: const TextStyle(color: AppTheme.textPrimary),\n");
        sb.append("        decoration: InputDecoration(\n");
        sb.append("          hintText: 'بحث في المهام...',\n");
        sb.append("          prefixIcon: const Icon(Icons.search_rounded,\n");
        sb.append("              color: AppTheme.textMuted),\n");
        sb.append("          suffixIcon: _searchQuery.isNotEmpty\n");
        sb.append("              ? IconButton(\n");
        sb.append("                  icon: const Icon(Icons.clear_rounded,\n");
        sb.append("                      color: AppTheme.textMuted),\n");
        sb.append("                  onPressed: () {\n");
        sb.append("                    _searchController.clear();\n");
        sb.append("                    setState(() => _searchQuery = '');\n");
        sb.append("                  },\n");
        sb.append("                )\n");
        sb.append("              : null,\n");
        sb.append("        ),\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _buildFilters(int total, int active, int completed) {\n");
        sb.append("    return Padding(\n");
        sb.append("      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),\n");
        sb.append("      child: Row(\n");
        sb.append("        children: [\n");
        sb.append("          _filterChip('الكل', TaskFilter.all, total),\n");
        sb.append("          const SizedBox(width: 8),\n");
        sb.append("          _filterChip('نشطة', TaskFilter.active, active),\n");
        sb.append("          const SizedBox(width: 8),\n");
        sb.append("          _filterChip('مكتملة', TaskFilter.completed, completed),\n");
        sb.append("        ],\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _filterChip(String label, TaskFilter filter, int count) {\n");
        sb.append("    final selected = _filter == filter;\n");
        sb.append("    return Expanded(\n");
        sb.append("      child: GestureDetector(\n");
        sb.append("        onTap: () => setState(() => _filter = filter),\n");
        sb.append("        child: AnimatedContainer(\n");
        sb.append("          duration: const Duration(milliseconds: 200),\n");
        sb.append("          padding: const EdgeInsets.symmetric(vertical: 10),\n");
        sb.append("          decoration: BoxDecoration(\n");
        sb.append("            color: selected ? AppTheme.primary : AppTheme.surfaceAlt,\n");
        sb.append("            borderRadius: BorderRadius.circular(12),\n");
        sb.append("            border: Border.all(\n");
        sb.append("              color: selected ? AppTheme.primaryLight : AppTheme.border,\n");
        sb.append("            ),\n");
        sb.append("          ),\n");
        sb.append("          child: Column(\n");
        sb.append("            children: [\n");
        sb.append("              Text(\n");
        sb.append("                '$count',\n");
        sb.append("                style: TextStyle(\n");
        sb.append("                  color: selected ? Colors.white : AppTheme.textPrimary,\n");
        sb.append("                  fontSize: 16,\n");
        sb.append("                  fontWeight: FontWeight.bold,\n");
        sb.append("                ),\n");
        sb.append("              ),\n");
        sb.append("              Text(\n");
        sb.append("                label,\n");
        sb.append("                style: TextStyle(\n");
        sb.append("                  color: selected ? Colors.white70 : AppTheme.textMuted,\n");
        sb.append("                  fontSize: 11,\n");
        sb.append("                ),\n");
        sb.append("              ),\n");
        sb.append("            ],\n");
        sb.append("          ),\n");
        sb.append("        ),\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _buildTaskList(List<Task> visible) {\n");
        sb.append("    return FadeTransition(\n");
        sb.append("      opacity: _fadeController,\n");
        sb.append("      child: ListView.builder(\n");
        sb.append("        padding: const EdgeInsets.fromLTRB(12, 8, 12, 100),\n");
        sb.append("        itemCount: visible.length,\n");
        sb.append("        itemBuilder: (context, index) {\n");
        sb.append("          final task = visible[index];\n");
        sb.append("          return _buildTaskItem(task);\n");
        sb.append("        },\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _buildTaskItem(Task task) {\n");
        sb.append("    return Dismissible(\n");
        sb.append("      key: ValueKey(task.id),\n");
        sb.append("      direction: DismissDirection.endToStart,\n");
        sb.append("      background: Container(\n");
        sb.append("        margin: const EdgeInsets.symmetric(vertical: 4),\n");
        sb.append("        padding: const EdgeInsets.symmetric(horizontal: 20),\n");
        sb.append("        decoration: BoxDecoration(\n");
        sb.append("          color: AppTheme.danger,\n");
        sb.append("          borderRadius: BorderRadius.circular(14),\n");
        sb.append("        ),\n");
        sb.append("        alignment: Alignment.centerRight,\n");
        sb.append("        child: const Icon(Icons.delete_rounded,\n");
        sb.append("            color: Colors.white, size: 26),\n");
        sb.append("      ),\n");
        sb.append("      confirmDismiss: (_) async {\n");
        sb.append("        _deleteTask(task);\n");
        sb.append("        return false;\n");
        sb.append("      },\n");
        sb.append("      child: AnimatedContainer(\n");
        sb.append("        duration: const Duration(milliseconds: 250),\n");
        sb.append("        margin: const EdgeInsets.symmetric(vertical: 4),\n");
        sb.append("        decoration: BoxDecoration(\n");
        sb.append("          color: AppTheme.surface,\n");
        sb.append("          borderRadius: BorderRadius.circular(14),\n");
        sb.append("          border: Border.all(\n");
        sb.append("            color: task.done\n");
        sb.append("                ? AppTheme.success.withOpacity(0.4)\n");
        sb.append("                : AppTheme.border,\n");
        sb.append("          ),\n");
        sb.append("        ),\n");
        sb.append("        child: Material(\n");
        sb.append("          color: Colors.transparent,\n");
        sb.append("          child: InkWell(\n");
        sb.append("            borderRadius: BorderRadius.circular(14),\n");
        sb.append("            onTap: () => _toggleTask(task),\n");
        sb.append("            child: Padding(\n");
        sb.append("              padding: const EdgeInsets.all(14),\n");
        sb.append("              child: Row(\n");
        sb.append("                children: [\n");
        sb.append("                  AnimatedContainer(\n");
        sb.append("                    duration: const Duration(milliseconds: 250),\n");
        sb.append("                    width: 26,\n");
        sb.append("                    height: 26,\n");
        sb.append("                    decoration: BoxDecoration(\n");
        sb.append("                      color: task.done\n");
        sb.append("                          ? AppTheme.success\n");
        sb.append("                          : Colors.transparent,\n");
        sb.append("                      borderRadius: BorderRadius.circular(8),\n");
        sb.append("                      border: Border.all(\n");
        sb.append("                        color: task.done\n");
        sb.append("                            ? AppTheme.success\n");
        sb.append("                            : AppTheme.textMuted,\n");
        sb.append("                        width: 2,\n");
        sb.append("                      ),\n");
        sb.append("                    ),\n");
        sb.append("                    child: task.done\n");
        sb.append("                        ? const Icon(Icons.check_rounded,\n");
        sb.append("                            color: Colors.white, size: 18)\n");
        sb.append("                        : null,\n");
        sb.append("                  ),\n");
        sb.append("                  const SizedBox(width: 14),\n");
        sb.append("                  Expanded(\n");
        sb.append("                    child: Column(\n");
        sb.append("                      crossAxisAlignment: CrossAxisAlignment.start,\n");
        sb.append("                      children: [\n");
        sb.append("                        Text(\n");
        sb.append("                          task.title,\n");
        sb.append("                          style: TextStyle(\n");
        sb.append("                            color: task.done\n");
        sb.append("                                ? AppTheme.textMuted\n");
        sb.append("                                : AppTheme.textPrimary,\n");
        sb.append("                            fontSize: 15,\n");
        sb.append("                            decoration: task.done\n");
        sb.append("                                ? TextDecoration.lineThrough\n");
        sb.append("                                : null,\n");
        sb.append("                            decorationColor: AppTheme.textMuted,\n");
        sb.append("                          ),\n");
        sb.append("                        ),\n");
        sb.append("                        const SizedBox(height: 4),\n");
        sb.append("                        Text(\n");
        sb.append("                          _formatDate(task.createdAt),\n");
        sb.append("                          style: const TextStyle(\n");
        sb.append("                            color: AppTheme.textMuted,\n");
        sb.append("                            fontSize: 11,\n");
        sb.append("                          ),\n");
        sb.append("                        ),\n");
        sb.append("                      ],\n");
        sb.append("                    ),\n");
        sb.append("                  ),\n");
        sb.append("                  IconButton(\n");
        sb.append("                    tooltip: 'حذف',\n");
        sb.append("                    icon: const Icon(Icons.delete_outline_rounded,\n");
        sb.append("                        color: AppTheme.textMuted),\n");
        sb.append("                    onPressed: () => _deleteTask(task),\n");
        sb.append("                  ),\n");
        sb.append("                ],\n");
        sb.append("              ),\n");
        sb.append("            ),\n");
        sb.append("          ),\n");
        sb.append("        ),\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  String _formatDate(DateTime date) {\n");
        sb.append("    final now = DateTime.now();\n");
        sb.append("    final diff = now.difference(date);\n");
        sb.append("    if (diff.inMinutes < 1) return 'الآن';\n");
        sb.append("    if (diff.inHours < 1) return 'منذ ${diff.inMinutes} دقيقة';\n");
        sb.append("    if (diff.inDays < 1) return 'منذ ${diff.inHours} ساعة';\n");
        sb.append("    if (diff.inDays == 1) return 'أمس';\n");
        sb.append("    return 'منذ ${diff.inDays} يوم';\n");
        sb.append("  }\n");
        sb.append("\n");
        sb.append("  Widget _buildEmptyState() {\n");
        sb.append("    final String message;\n");
        sb.append("    final IconData icon;\n");
        sb.append("    if (_searchQuery.isNotEmpty) {\n");
        sb.append("      message = 'لا توجد نتائج للبحث';\n");
        sb.append("      icon = Icons.search_off_rounded;\n");
        sb.append("    } else if (_filter == TaskFilter.completed) {\n");
        sb.append("      message = 'لا توجد مهام مكتملة بعد';\n");
        sb.append("      icon = Icons.check_circle_outline_rounded;\n");
        sb.append("    } else if (_filter == TaskFilter.active) {\n");
        sb.append("      message = 'كل المهام مكتملة!';\n");
        sb.append("      icon = Icons.celebration_rounded;\n");
        sb.append("    } else {\n");
        sb.append("      message = 'ابدأ بإضافة مهمتك الأولى';\n");
        sb.append("      icon = Icons.playlist_add_rounded;\n");
        sb.append("    }\n");
        sb.append("\n");
        sb.append("    return Center(\n");
        sb.append("      child: Column(\n");
        sb.append("        mainAxisAlignment: MainAxisAlignment.center,\n");
        sb.append("        children: [\n");
        sb.append("          Container(\n");
        sb.append("            padding: const EdgeInsets.all(24),\n");
        sb.append("            decoration: BoxDecoration(\n");
        sb.append("              color: AppTheme.surfaceAlt,\n");
        sb.append("              shape: BoxShape.circle,\n");
        sb.append("              border: Border.all(color: AppTheme.border),\n");
        sb.append("            ),\n");
        sb.append("            child: Icon(icon, size: 56, color: AppTheme.primaryLight),\n");
        sb.append("          ),\n");
        sb.append("          const SizedBox(height: 20),\n");
        sb.append("          Text(\n");
        sb.append("            message,\n");
        sb.append("            style: const TextStyle(\n");
        sb.append("              color: AppTheme.textMuted,\n");
        sb.append("              fontSize: 15,\n");
        sb.append("            ),\n");
        sb.append("          ),\n");
        sb.append("          const SizedBox(height: 6),\n");
        sb.append("          const Text(\n");
        sb.append("            'استخدم الزر أدناه للبدء',\n");
        sb.append("            style: TextStyle(color: AppTheme.textMuted, fontSize: 12),\n");
        sb.append("          ),\n");
        sb.append("        ],\n");
        sb.append("      ),\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ============================================================
    // ملف .gitignore
    // ============================================================
    private static String buildGitignore() {
        return "*.iml\n" +
                ".dart_tool/\n" +
                ".idea/\n" +
                ".packages\n" +
                ".pub/\n" +
                "build/\n" +
                "*.swp\n" +
                ".flutter-plugins\n" +
                ".flutter-plugins-dependencies\n";
    }

    // ============================================================
    // ملف .github/workflows/main.yml
    // ============================================================
    private static String buildWorkflowYml() {
        StringBuilder sb = new StringBuilder();
        sb.append("name: Build Flutter APK\n");
        sb.append("\n");
        sb.append("on:\n");
        sb.append("  workflow_dispatch:\n");
        sb.append("\n");
        sb.append("jobs:\n");
        sb.append("  build:\n");
        sb.append("    runs-on: ubuntu-latest\n");
        sb.append("    steps:\n");
        sb.append("      - uses: actions/checkout@v4\n");
        sb.append("\n");
        sb.append("      - name: Setup Java 11\n");
        sb.append("        uses: actions/setup-java@v4\n");
        sb.append("        with:\n");
        sb.append("          distribution: temurin\n");
        sb.append("          java-version: '11'\n");
        sb.append("          cache: gradle\n");
        sb.append("\n");
        sb.append("      - name: Setup Flutter\n");
        sb.append("        uses: subosito/flutter-action@v2\n");
        sb.append("        with:\n");
        sb.append("          flutter-version: '3.16.0'\n");
        sb.append("\n");
        sb.append("      - name: Get dependencies\n");
        sb.append("        run: flutter pub get\n");
        sb.append("\n");
        sb.append("      - name: Build APK\n");
        sb.append("        run: flutter build apk --release\n");
        sb.append("\n");
        sb.append("      - name: Upload APK\n");
        sb.append("        uses: actions/upload-artifact@v4\n");
        sb.append("        with:\n");
        sb.append("          name: app-release\n");
        sb.append("          path: build/app/outputs/flutter-apk/app-release.apk\n");
        sb.append("          retention-days: 7\n");
        return sb.toString();
    }

    // ============================================================
    // android/build.gradle
    // ============================================================
    private static String buildRootBuildGradle() {
        return "buildscript {\n" +
                "    ext.kotlin_version = '1.7.10'\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "    }\n" +
                "    dependencies {\n" +
                "        classpath 'com.android.tools.build:gradle:7.3.0'\n" +
                "        classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version\"\n" +
                "    }\n" +
                "}\n\n" +
                "allprojects {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "    }\n" +
                "}\n\n" +
                "rootProject.buildDir = '../build'\n" +
                "subprojects {\n" +
                "    project.buildDir = \"${rootProject.buildDir}/${project.name}\"\n" +
                "}\n" +
                "subprojects {\n" +
                "    project.evaluationDependsOn(':app')\n" +
                "}\n\n" +
                "tasks.register(\"clean\", Delete) {\n" +
                "    delete rootProject.buildDir\n" +
                "}\n";
    }

    // ============================================================
    // android/app/build.gradle
    // ============================================================
    private static String buildAppBuildGradle(String packageName, String apiLevel) {
        return "def localProperties = new Properties()\n" +
                "def localPropertiesFile = rootProject.file('local.properties')\n" +
                "if (localPropertiesFile.exists()) {\n" +
                "    localPropertiesFile.withReader('UTF-8') { reader ->\n" +
                "        localProperties.load(reader)\n" +
                "    }\n" +
                "}\n\n" +
                "def flutterRoot = localProperties.getProperty('flutter.sdk')\n" +
                "if (flutterRoot == null) {\n" +
                "    throw new GradleException(\"Flutter SDK not found.\")\n" +
                "}\n\n" +
                "apply plugin: 'com.android.application'\n" +
                "apply plugin: 'kotlin-android'\n" +
                "apply from: \"$flutterRoot/packages/flutter_tools/gradle/flutter.gradle\"\n\n" +
                "android {\n" +
                "    compileSdkVersion " + apiLevel + "\n" +
                "    namespace \"" + packageName + "\"\n\n" +
                "    defaultConfig {\n" +
                "        applicationId \"" + packageName + "\"\n" +
                "        minSdkVersion 21\n" +
                "        targetSdkVersion " + apiLevel + "\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "    }\n" +
                "    buildTypes {\n" +
                "        release {\n" +
                "            signingConfig signingConfigs.debug\n" +
                "        }\n" +
                "    }\n" +
                "}\n\n" +
                "flutter {\n" +
                "    source '../..'\n" +
                "}\n\n" +
                "dependencies {\n" +
                "    implementation \"org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version\"\n" +
                "}\n";
    }

    // ============================================================
    // android/settings.gradle
    // ============================================================
    private static String buildSettingsGradle() {
        return "include ':app'\n\n" +
                "def localPropertiesFile = new File(rootProject.projectDir, \"local.properties\")\n" +
                "def properties = new Properties()\n\n" +
                "assert localPropertiesFile.exists()\n" +
                "localPropertiesFile.withReader(\"UTF-8\") { reader -> properties.load(reader) }\n\n" +
                "def flutterSdkPath = properties.getProperty(\"flutter.sdk\")\n" +
                "assert flutterSdkPath != null, \"flutter.sdk not set in local.properties\"\n" +
                "apply from: \"$flutterSdkPath/packages/flutter_tools/gradle/app_plugin_loader.gradle\"\n";
    }

    // ============================================================
    // android/gradle.properties
    // ============================================================
    private static String buildGradleProperties() {
        return "org.gradle.jvmargs=-Xmx1536M\n" +
                "android.useAndroidX=true\n" +
                "android.enableJetifier=true\n";
    }

    // ============================================================
    // android/app/src/main/res/values/styles.xml
    // ============================================================
    private static String buildStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <style name=\"NormalTheme\" parent=\"android:Theme.Material.Light.NoActionBar\">\n" +
                "        <item name=\"android:windowBackground\">@android:color/white</item>\n" +
                "    </style>\n" +
                "</resources>\n";
    }

    // ============================================================
    // android/app/src/main/AndroidManifest.xml (مع NormalTheme)
    // ============================================================
    private static String buildAndroidManifest(String packageName, String appName) {
        String safeLabel = appName == null ? "App" : appName.replace("\"", "\\\"");
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"" + packageName + "\">\n\n" +
                "    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>\n" +
                "    <uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\"/>\n\n" +
                "    <application\n" +
                "        android:requestLegacyExternalStorage=\"true\"\n" +
                "        android:label=\"" + safeLabel + "\"\n" +
                "        android:icon=\"@drawable/ic_launcher\"\n" +
                "        android:theme=\"@style/NormalTheme\">\n" +
                "        <activity\n" +
                "            android:name=\".MainActivity\"\n" +
                "            android:exported=\"true\"\n" +
                "            android:launchMode=\"singleTop\"\n" +
                "            android:configChanges=\"orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode\"\n" +
                "            android:hardwareAccelerated=\"true\"\n" +
                "            android:windowSoftInputMode=\"adjustResize\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\"/>\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "        <meta-data\n" +
                "            android:name=\"flutterEmbedding\"\n" +
                "            android:value=\"2\"/>\n" +
                "    </application>\n" +
                "</manifest>\n";
    }

    // ============================================================
    // android/.../MainActivity.kt
    // ============================================================
    private static String buildMainActivityKt(String packageName) {
        return "package " + packageName + "\n\n" +
                "import io.flutter.embedding.android.FlutterActivity\n\n" +
                "class MainActivity: FlutterActivity() {\n" +
                "}\n";
    }
}
