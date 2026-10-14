package com.flutteride.codweva;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CompletionContext — محلل سياقي يحدد ما يجب اقتراحه بناءً على موضع المؤشر.
 *
 * 28 قاعدة موزعة على 6 فئات:
 *   1) داخل Widgets       (8 قواعد)
 *   2) بعد Properties     (8 قواعد)
 *   3) Enums              (4 قواعد)
 *   4) Import             (2 قواعد)
 *   5) Class/State        (3 قواعد)
 *   6) Dart عام           (3 قواعد)
 */
public final class CompletionContext {

    // ============================================================
    // الثوابت
    // ============================================================
    /** أقصى عدد أحرف نقرأها قبل المؤشر للتحليل (حماية للأداء). */
    private static final int LOOKBACK_LIMIT = 800;

    // ============================================================
    // القواعد: خصائص Widgets الشهيرة
    // ============================================================
    /** قواعد: داخل widget معين → اقترح خصائصه. */
    private static final List<WidgetRule> WIDGET_RULES = new ArrayList<>();
    static {
        WIDGET_RULES.add(new WidgetRule("Column", new String[]{
                "children", "mainAxisAlignment", "crossAxisAlignment",
                "mainAxisSize", "textDirection", "verticalDirection"
        }));
        WIDGET_RULES.add(new WidgetRule("Row", new String[]{
                "children", "mainAxisAlignment", "crossAxisAlignment",
                "mainAxisSize", "textDirection", "verticalDirection"
        }));
        WIDGET_RULES.add(new WidgetRule("Container", new String[]{
                "child", "padding", "margin", "color", "width", "height",
                "alignment", "decoration", "constraints", "transform", "clipBehavior"
        }));
        WIDGET_RULES.add(new WidgetRule("Text", new String[]{
                "style", "textAlign", "maxLines", "overflow", "softWrap",
                "textDirection", "textScaleFactor"
        }));
        WIDGET_RULES.add(new WidgetRule("Scaffold", new String[]{
                "appBar", "body", "floatingActionButton", "drawer", "endDrawer",
                "bottomNavigationBar", "bottomSheet", "backgroundColor",
                "resizeToAvoidBottomInset", "extendBody"
        }));
        WIDGET_RULES.add(new WidgetRule("AppBar", new String[]{
                "title", "actions", "leading", "centerTitle", "elevation",
                "toolbarHeight", "flexibleSpace", "bottom", "backgroundColor"
        }));
        WIDGET_RULES.add(new WidgetRule("ListView", new String[]{
                "children", "scrollDirection", "padding", "physics",
                "shrinkWrap", "reverse", "controller", "primary"
        }));
        WIDGET_RULES.add(new WidgetRule("Stack", new String[]{
                "children", "alignment", "fit", "clipBehavior"
        }));
    }

    // ============================================================
    // القواعد: بعد property → قيم محددة
    // ============================================================
    /** بعد "mainAxisAlignment:" → قيم MainAxisAlignment. */
    private static final PropertyValuesRule MAIN_AXIS_ALIGNMENT = new PropertyValuesRule(
            "mainAxisAlignment",
            new String[]{
                    "MainAxisAlignment.start", "MainAxisAlignment.center",
                    "MainAxisAlignment.end", "MainAxisAlignment.spaceBetween",
                    "MainAxisAlignment.spaceAround", "MainAxisAlignment.spaceEvenly"
            });

    private static final PropertyValuesRule CROSS_AXIS_ALIGNMENT = new PropertyValuesRule(
            "crossAxisAlignment",
            new String[]{
                    "CrossAxisAlignment.start", "CrossAxisAlignment.center",
                    "CrossAxisAlignment.end", "CrossAxisAlignment.stretch",
                    "CrossAxisAlignment.baseline"
            });

    private static final PropertyValuesRule MAIN_AXIS_SIZE = new PropertyValuesRule(
            "mainAxisSize",
            new String[]{"MainAxisSize.min", "MainAxisSize.max"});

    private static final PropertyValuesRule TEXT_ALIGN = new PropertyValuesRule(
            "textAlign",
            new String[]{
                    "TextAlign.left", "TextAlign.right", "TextAlign.center",
                    "TextAlign.justify", "TextAlign.start", "TextAlign.end"
            });

    private static final PropertyValuesRule TEXT_OVERFLOW = new PropertyValuesRule(
            "overflow",
            new String[]{
                    "TextOverflow.clip", "TextOverflow.fade",
                    "TextOverflow.ellipsis", "TextOverflow.visible"
            });

    private static final PropertyValuesRule FONT_WEIGHT = new PropertyValuesRule(
            "fontWeight",
            new String[]{
                    "FontWeight.w100", "FontWeight.w200", "FontWeight.w300",
                    "FontWeight.w400", "FontWeight.w500", "FontWeight.w600",
                    "FontWeight.w700", "FontWeight.w800", "FontWeight.w900",
                    "FontWeight.bold", "FontWeight.normal"
            });

    private static final PropertyValuesRule BOX_FIT = new PropertyValuesRule(
            "fit",
            new String[]{
                    "BoxFit.fill", "BoxFit.contain", "BoxFit.cover",
                    "BoxFit.fitWidth", "BoxFit.fitHeight", "BoxFit.none",
                    "BoxFit.scaleDown"
            });

    private static final PropertyValuesRule ALIGNMENT = new PropertyValuesRule(
            "alignment",
            new String[]{
                    "Alignment.topLeft", "Alignment.topCenter", "Alignment.topRight",
                    "Alignment.centerLeft", "Alignment.center", "Alignment.centerRight",
                    "Alignment.bottomLeft", "Alignment.bottomCenter", "Alignment.bottomRight"
            });

    // القائمة الكاملة للـ property rules
    private static final List<PropertyValuesRule> PROPERTY_RULES = new ArrayList<>();
    static {
        PROPERTY_RULES.add(MAIN_AXIS_ALIGNMENT);
        PROPERTY_RULES.add(CROSS_AXIS_ALIGNMENT);
        PROPERTY_RULES.add(MAIN_AXIS_SIZE);
        PROPERTY_RULES.add(TEXT_ALIGN);
        PROPERTY_RULES.add(TEXT_OVERFLOW);
        PROPERTY_RULES.add(FONT_WEIGHT);
        PROPERTY_RULES.add(BOX_FIT);
        PROPERTY_RULES.add(ALIGNMENT);
    }

    // ============================================================
    // القواعد: Enums الشائعة
    // ============================================================
    private static final List<EnumRule> ENUM_RULES = new ArrayList<>();
    static {
        ENUM_RULES.add(new EnumRule("MainAxisAlignment"));
        ENUM_RULES.add(new EnumRule("CrossAxisAlignment"));
        ENUM_RULES.add(new EnumRule("TextAlign"));
        ENUM_RULES.add(new EnumRule("FontWeight"));
    }

    // ============================================================
    // القواعد: بعد properties نوعية → اقتراح باني
    // ============================================================
    /** بعد "style:" → TextStyle(. */
    private static final String[] AFTER_STYLE_VALUES = {"TextStyle("};

    /** بعد "decoration:" → BoxDecoration( أو InputDecoration(. */
    private static final String[] AFTER_DECORATION_VALUES = {
            "BoxDecoration(", "InputDecoration("
    };

    /** بعد "padding:" أو "margin:" → EdgeInsets. */
    private static final String[] AFTER_INSETS_VALUES = {
            "EdgeInsets.all(",
            "EdgeInsets.symmetric(",
            "EdgeInsets.only(",
            "EdgeInsets.fromLTRB(",
            "EdgeInsets.zero"
    };

    /** بعد "borderRadius:" → BorderRadius. */
    private static final String[] AFTER_BORDER_RADIUS = {
            "BorderRadius.circular(",
            "BorderRadius.all(",
            "BorderRadius.only(",
            "BorderRadius.vertical(",
            "BorderRadius.horizontal("
    };

    /** بعد "color:" → Colors. */
    private static final String[] AFTER_COLOR = {"Colors."};

    /** بعد "controller:" → TextEditingController أو ScrollController أو PageController. */
    private static final String[] AFTER_CONTROLLER = {
            "TextEditingController(",
            "ScrollController(",
            "PageController(",
            "AnimationController("
    };

    /** بعد "physics:" → ScrollPhysics. */
    private static final String[] AFTER_PHYSICS = {
            "BouncingScrollPhysics(",
            "ClampingScrollPhysics(",
            "AlwaysScrollableScrollPhysics(",
            "NeverScrollableScrollPhysics("
    };

    // ============================================================
    // القواعد: import
    // ============================================================
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "import\\s+['\"]([^'\"]*)$");

    // ============================================================
    // القواعد: class/state
    // ============================================================
    private static final Pattern STATE_METHOD_PATTERN = Pattern.compile(
            "class\\s+\\w+\\s+extends\\s+State<");

    // ============================================================
    // نتيجة التحليل
    // ============================================================
    public static final class AnalysisResult {
        /** الكلمة الحالية قبل المؤشر. */
        public String prefix = "";

        /** widget السياقي (إن وُجد). */
        public String currentWidget = null;

        /** property الحالية بعدها المؤشر (إن وُجدت). */
        public String afterProperty = null;

        /** هل المؤشر داخل import ' '. */
        public boolean insideImport = false;

        /** داخل State class. */
        public boolean insideState = false;

        /** اقتراحات مُخصصة مبنية على القاعدة. */
        public final List<String> customSuggestions = new ArrayList<>();

        /** هل يجب تضييق القائمة من DartCompletions؟ */
        public boolean useFilteredMode = false;
    }

    private CompletionContext() {}

    // ============================================================
    // نقطة الدخول الرئيسية
    // ============================================================

    /**
     * يحلّل السياق الكامل حول موضع المؤشر.
     */
    public static AnalysisResult analyze(String text, int cursorPos) {
        AnalysisResult r = new AnalysisResult();
        if (text == null || cursorPos < 0 || cursorPos > text.length()) return r;

        // 1) استخراج البادئة (الكلمة الحالية)
        r.prefix = extractPrefix(text, cursorPos);

        // 2) احصل على قسم التحليل (آخر 800 حرف قبل المؤشر)
        int start = Math.max(0, cursorPos - LOOKBACK_LIMIT);
        String lookback = text.substring(start, cursorPos);

        // 3) ترتيب القواعد (الأهم أولاً)

        // 3.1) import
        Matcher importMatcher = IMPORT_PATTERN.matcher(lookback);
        if (importMatcher.find()) {
            r.insideImport = true;
            r.useFilteredMode = true;
            return r;
        }

        // 3.2) State class
        if (STATE_METHOD_PATTERN.matcher(lookback).find()) {
            r.insideState = true;
        }

        // 3.3) property → قيم
        // ابحث عن "prop:" بعد آخر سطر
        int lastNewline = lookback.lastIndexOf('\n');
        String lastLine = lastNewline >= 0
                ? lookback.substring(lastNewline + 1)
                : lookback;

        // هل المؤشر بعد "prop:"؟ (بدون قيم؟)
        String afterProp = extractAfterProperty(lastLine);
        if (afterProp != null && r.prefix.isEmpty()) {
            r.afterProperty = afterProp;
            r.useFilteredMode = true;
            addPropertyValues(r.customSuggestions, afterProp);
            return r;
        }

        // هل المؤشر داخل قيمة (بعد prop: ثم بدأ بكتابة prefix)؟
        String insideValueProp = extractAfterPropertyWithPrefix(lastLine, r.prefix);
        if (insideValueProp != null) {
            r.afterProperty = insideValueProp;
            r.useFilteredMode = true;
            addPropertyValues(r.customSuggestions, insideValueProp);
            return r;
        }

        // 3.4) widget context
        String widget = findEnclosingWidget(lookback, cursorPos - start);
        if (widget != null) {
            r.currentWidget = widget;
            // اقتراح خصائص هذا الـ widget
            for (WidgetRule rule : WIDGET_RULES) {
                if (rule.widgetName.equals(widget)) {
                    Collections.addAll(r.customSuggestions, rule.properties);
                    break;
                }
            }
        }

        return r;
    }

    // ============================================================
    // استخراج البادئة
    // ============================================================
    private static String extractPrefix(String text, int cursorPos) {
        if (cursorPos <= 0) return "";
        int start = cursorPos - 1;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$') {
                start--;
            } else break;
        }
        return text.substring(start, cursorPos);
    }

    // ============================================================
    // استخراج "prop:" بعد آخر سطر
    // ============================================================
    private static String extractAfterProperty(String lastLine) {
        if (lastLine == null) return null;
        String trimmed = lastLine.trim();
        // النمط: "prop:" في نهاية السطر
        Matcher m = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*$")
                .matcher(trimmed);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String extractAfterPropertyWithPrefix(String lastLine, String prefix) {
        if (lastLine == null || prefix == null || prefix.isEmpty()) return null;
        // النمط: "prop: pre" حيث pre هو prefix الحالي
        String trimmed = lastLine.trim();
        // أوقف قبل الـ prefix
        if (!trimmed.endsWith(prefix)) return null;
        String before = trimmed.substring(0, trimmed.length() - prefix.length());
        Matcher m = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*$")
                .matcher(before);
        if (m.find()) return m.group(1);
        return null;
    }

    // ============================================================
    // استخراج widget السياقي
    // ============================================================

    /**
     * يحاول إيجاد آخر widget مفتوح (بدون قوس إغلاق) قبل المؤشر.
     * يفحص قائمة الـ widgets الشهيرة، ويعيد أول تطابق.
     */
    private static String findEnclosingWidget(String lookback, int cursorOffsetInLookback) {
        // للأداء: نبحث فقط آخر 400 حرف
        int start = Math.max(0, lookback.length() - 400);
        String region = lookback.substring(start);

        // جرّب كل widget rule، وخذ الأقرب
        String best = null;
        int bestPos = -1;

        for (WidgetRule rule : WIDGET_RULES) {
            String needle = rule.widgetName + "(";
            int pos = region.lastIndexOf(needle);
            if (pos < 0) continue;
            // هل القوس ما زال مفتوحاً؟
            if (isBracketOpen(region, pos + needle.length() - 1)) {
                if (pos > bestPos) {
                    best = rule.widgetName;
                    bestPos = pos;
                }
            }
        }
        return best;
    }

    /**
     * هل القوس عند موضع معين ما زال مفتوحاً؟
     */
    private static boolean isBracketOpen(String text, int openPos) {
        if (openPos < 0 || openPos >= text.length()) return false;
        if (text.charAt(openPos) != '(') return false;

        int depth = 1;
        for (int i = openPos + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return false;
            }
        }
        return true;
    }

    // ============================================================
    // إضافة قيم property
    // ============================================================
    private static void addPropertyValues(List<String> out, String property) {
        if (property == null) return;

        // ابحث في PROPERTY_RULES
        for (PropertyValuesRule rule : PROPERTY_RULES) {
            if (rule.property.equals(property)) {
                Collections.addAll(out, rule.values);
                return;
            }
        }

        // القواعد الخاصة (بعد property نوعية)
        switch (property) {
            case "style":
                Collections.addAll(out, AFTER_STYLE_VALUES);
                break;
            case "decoration":
                Collections.addAll(out, AFTER_DECORATION_VALUES);
                break;
            case "padding":
            case "margin":
            case "contentPadding":
                Collections.addAll(out, AFTER_INSETS_VALUES);
                break;
            case "borderRadius":
                Collections.addAll(out, AFTER_BORDER_RADIUS);
                break;
            case "color":
            case "backgroundColor":
            case "foregroundColor":
                Collections.addAll(out, AFTER_COLOR);
                break;
            case "controller":
                Collections.addAll(out, AFTER_CONTROLLER);
                break;
            case "physics":
                Collections.addAll(out, AFTER_PHYSICS);
                break;
            default:
                break;
        }
    }

    // ============================================================
    // أدوات ثابتة للاستخدام الخارجي
    // ============================================================

    /**
     * يحاول استخراج widget الاسم من بادئة معينة إذا كانت البادئة تساوي اسم widget.
     * مثلاً: إذا prefix = "Text" والمؤشر بعده، فهذا يعني المستخدم بدأ بكتابة Text.
     */
    public static boolean isWidgetName(String prefix) {
        if (prefix == null) return false;
        for (WidgetRule rule : WIDGET_RULES) {
            if (rule.widgetName.equals(prefix)) return true;
        }
        return false;
    }

    /**
     * اقتراحات إضافية لسياقات خاصة (State methods، إلخ).
     */
    public static List<String> getStateMethodSuggestions() {
        List<String> out = new ArrayList<>();
        out.add("initState()");
        out.add("dispose()");
        out.add("didChangeDependencies()");
        out.add("build(BuildContext context)");
        out.add("setState(() { })");
        out.add("@override");
        return out;
    }

    // ============================================================
    // الفئات الداخلية
    // ============================================================
    private static final class WidgetRule {
        final String widgetName;
        final String[] properties;
        WidgetRule(String widgetName, String[] properties) {
            this.widgetName = widgetName;
            this.properties = properties;
        }
    }

    private static final class PropertyValuesRule {
        final String property;
        final String[] values;
        PropertyValuesRule(String property, String[] values) {
            this.property = property;
            this.values = values;
        }
    }

    private static final class EnumRule {
        final String enumName;
        EnumRule(String enumName) { this.enumName = enumName; }
    }
}