package com.flutteride.codweva;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DartCompletions — محمّل موحّد لقاموس Dart/Flutter.
 *
 * يُحمّل 9 ملفات JSON من assets/dart_completions/ ويبني فهرساً داخلياً.
 *
 * ✅ v2: يدعم "variants" — عرض نسختين لكل widget ذي snippet مفيد:
 *    - النسخة 1: الاسم فقط (مثل Column)
 *    - النسخة 2: الاسم + ( … ) مع snippet (مثل Column( … ))
 */
public final class DartCompletions {

    private static final String TAG = "DartCompletions";
    private static final String ASSETS_DIR = "dart_completions";

    private static DartCompletions instance;

    // ============================================================
    // نماذج البيانات
    // ============================================================
    public static final class Item {
        public final String name;
        public final String description;
        public final String snippet;
        public final ItemKind kind;
        public final String category;
        public final String signature;
        public final String returnType;
        public final String className;
        public final int priority;
        public final List<String> applicableTo;
        public final List<String> enumValues;

        // ✅ جديد: هل هذه نسخة "snippet" (تُفك عند الاختيار)؟
        public final boolean isSnippetVariant;

        Item(String name, String description, String snippet, ItemKind kind,
             String category, String signature, String returnType,
             String className, int priority,
             List<String> applicableTo, List<String> enumValues) {
            this(name, description, snippet, kind, category, signature, returnType,
                 className, priority, applicableTo, enumValues, false);
        }

        Item(String name, String description, String snippet, ItemKind kind,
             String category, String signature, String returnType,
             String className, int priority,
             List<String> applicableTo, List<String> enumValues,
             boolean isSnippetVariant) {
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
            this.snippet = snippet == null ? "" : snippet;
            this.kind = kind;
            this.category = category == null ? "" : category;
            this.signature = signature == null ? "" : signature;
            this.returnType = returnType == null ? "" : returnType;
            this.className = className == null ? "" : className;
            this.priority = priority;
            this.applicableTo = applicableTo == null ? Collections.<String>emptyList() : applicableTo;
            this.enumValues = enumValues == null ? Collections.<String>emptyList() : enumValues;
            this.isSnippetVariant = isSnippetVariant;
        }

        public boolean appliesTo(String widgetName) {
            if (widgetName == null || widgetName.isEmpty()) return true;
            if (applicableTo.isEmpty()) return true;
            return applicableTo.contains(widgetName);
        }

        public boolean hasSnippet() {
            return snippet != null && !snippet.isEmpty();
        }

        /** ✅ نسخة مع snippet مفعّل (isSnippetVariant = true). */
        public Item asSnippetVariant(String displayName) {
            return new Item(
                    displayName,
                    description + " — إدراج كامل",
                    snippet,
                    kind,
                    category,
                    signature,
                    returnType,
                    className,
                    priority,
                    applicableTo,
                    enumValues,
                    true
            );
        }

        /** ✅ نسخة بدون snippet (isSnippetVariant = false). */
        public Item asPlainVariant() {
            return new Item(
                    name,
                    description,
                    "", // لا snippet
                    kind,
                    category,
                    signature,
                    returnType,
                    className,
                    priority,
                    applicableTo,
                    enumValues,
                    false
            );
        }

        @Override
        public String toString() {
            return kind + ":" + name + (isSnippetVariant ? " [snippet]" : "");
        }
    }

    public enum ItemKind {
        WIDGET("W"),
        PROPERTY("P"),
        METHOD("M"),
        TYPE("T"),
        KEYWORD("K"),
        IMPORT("I"),
        ENUM_VALUE("E");

        public final String symbol;
        ItemKind(String symbol) { this.symbol = symbol; }
    }

    // ============================================================
    // البيانات المُحمَّلة
    // ============================================================
    private final Map<String, List<Item>> byFirstChar = new HashMap<>();
    private final List<Item> allItems = new ArrayList<>();
    private boolean loaded = false;

    private final Map<String, Item> widgetsByName = new HashMap<>();
    private final Map<String, List<Item>> propertiesByWidget = new HashMap<>();
    private final Map<String, Item> enumsByName = new HashMap<>();

    // ============================================================
    // Singleton
    // ============================================================
    private DartCompletions() {}

    public static synchronized DartCompletions get(Context context) {
        if (instance == null) {
            instance = new DartCompletions();
            instance.load(context.getApplicationContext());
        }
        return instance;
    }

    // ============================================================
    // التحميل
    // ============================================================
    private void load(Context context) {
        if (loaded) return;
        long start = System.currentTimeMillis();

        try {
            loadWidgetsFile(context, "widgets_part1.json");
            loadWidgetsFile(context, "widgets_part2.json");
            loadWidgetsFile(context, "widgets_part3.json");
            loadPropertiesFile(context);
            loadMethodsFile(context);
            loadTypesFile(context);
            loadKeywordsFile(context);
            loadImportsFile(context);
            loadSnippetsFile(context);

            buildFirstCharIndex();

            loaded = true;
            long elapsed = System.currentTimeMillis() - start;
            Log.i(TAG, "تم تحميل " + allItems.size() + " عنصر في " + elapsed + "ms");
        } catch (Exception e) {
            Log.e(TAG, "فشل تحميل القاموس: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // قراءة JSON مع دعم التعليقات
    // ============================================================
    private JSONObject readJson(Context context, String fileName) throws Exception {
        InputStream is = context.getAssets().open(ASSETS_DIR + "/" + fileName);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) continue;
            String cleaned = stripTrailingComment(line);
            sb.append(cleaned).append('\n');
        }
        reader.close();
        is.close();

        String json = sb.toString();
        if (json.trim().isEmpty()) {
            Log.w(TAG, "ملف فارغ: " + fileName);
            return new JSONObject();
        }

        return new JSONObject(json);
    }

    private String stripTrailingComment(String line) {
        if (line == null) return "";
        int commentIdx = -1;
        boolean insideString = false;
        boolean escaped = false;
        char stringQuote = 0;

        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);

            if (escaped) { escaped = false; continue; }
            if (c == '\\' && insideString) { escaped = true; continue; }

            if (c == '"' || c == '\'') {
                if (!insideString) { insideString = true; stringQuote = c; }
                else if (c == stringQuote) { insideString = false; }
                continue;
            }

            if (!insideString && c == '/' && line.charAt(i + 1) == '/') {
                commentIdx = i;
                break;
            }
        }

        if (commentIdx < 0) return line;
        if (commentIdx > 0 && line.charAt(commentIdx - 1) == ':') return line;

        return line.substring(0, commentIdx).trim();
    }

    // ============================================================
    // محمّلات الملفات
    // ============================================================
    private void loadWidgetsFile(Context context, String fileName) throws Exception {
        JSONObject json = readJson(context, fileName);
        JSONArray widgets = json.optJSONArray("widgets");
        if (widgets == null) return;

        for (int i = 0; i < widgets.length(); i++) {
            JSONObject w = widgets.optJSONObject(i);
            if (w == null) continue;
            String name = w.optString("name", "");
            if (name.isEmpty()) continue;

            Item item = new Item(
                    name,
                    w.optString("description", ""),
                    w.optString("snippet", ""),
                    ItemKind.WIDGET,
                    w.optString("category", ""),
                    "", "", "",
                    w.optInt("priority", 5),
                    null, null
            );
            addItem(item);
            // حفظ الأول فقط (أعلى priority) إذا تكرر
            if (!widgetsByName.containsKey(name)) {
                widgetsByName.put(name, item);
            }
        }
    }

    private void loadPropertiesFile(Context context) throws Exception {
        JSONObject json = readJson(context, "properties.json");
        JSONArray props = json.optJSONArray("properties");
        if (props == null) return;

        Map<String, Item> merged = new HashMap<>();

        for (int i = 0; i < props.length(); i++) {
            JSONObject p = props.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("name", "");
            if (name.isEmpty()) continue;

            List<String> applicableTo = readStringArray(p.optJSONArray("applicable_to"));

            Item existing = merged.get(name);
            if (existing != null) {
                Set<String> combined = new HashSet<>(existing.applicableTo);
                combined.addAll(applicableTo);
                List<String> mergedList = new ArrayList<>(combined);
                Item updated = new Item(
                        name,
                        existing.description.isEmpty() ? p.optString("description", "") : existing.description,
                        existing.snippet,
                        ItemKind.PROPERTY,
                        existing.category.isEmpty() ? p.optString("type", "") : existing.category,
                        "", p.optString("type", ""), "",
                        existing.priority,
                        mergedList, null
                );
                merged.put(name, updated);
            } else {
                Item item = new Item(
                        name,
                        p.optString("description", ""),
                        "", ItemKind.PROPERTY,
                        p.optString("type", ""),
                        "", p.optString("type", ""), "",
                        5, applicableTo, null
                );
                merged.put(name, item);
            }
        }

        for (Item item : merged.values()) {
            addItem(item);
            for (String widget : item.applicableTo) {
                propertiesByWidget
                        .computeIfAbsent(widget, k -> new ArrayList<>())
                        .add(item);
            }
        }
    }

    private void loadMethodsFile(Context context) throws Exception {
        JSONObject json = readJson(context, "methods.json");
        JSONArray methods = json.optJSONArray("methods");
        if (methods == null) return;

        for (int i = 0; i < methods.length(); i++) {
            JSONObject m = methods.optJSONObject(i);
            if (m == null) continue;
            String name = m.optString("name", "");
            if (name.isEmpty()) continue;

            Item item = new Item(
                    name,
                    m.optString("description", ""),
                    "", ItemKind.METHOD,
                    m.optString("category", ""),
                    m.optString("signature", ""),
                    m.optString("return", ""),
                    m.optString("class", ""),
                    5, null, null
            );
            addItem(item);
        }
    }

    private void loadTypesFile(Context context) throws Exception {
        JSONObject json = readJson(context, "types.json");

        JSONArray types = json.optJSONArray("types");
        if (types != null) {
            for (int i = 0; i < types.length(); i++) {
                JSONObject t = types.optJSONObject(i);
                if (t == null) continue;
                String name = t.optString("name", "");
                if (name.isEmpty()) continue;

                Item item = new Item(
                        name, t.optString("description", ""), "",
                        ItemKind.TYPE, t.optString("category", ""),
                        "", "", "", 6, null, null
                );
                addItem(item);
            }
        }

        JSONArray enums = json.optJSONArray("enums");
        if (enums != null) {
            for (int i = 0; i < enums.length(); i++) {
                JSONObject e = enums.optJSONObject(i);
                if (e == null) continue;
                String name = e.optString("name", "");
                if (name.isEmpty()) continue;

                List<String> values = readStringArray(e.optJSONArray("values"));
                Item enumType = new Item(
                        name, "قيم: " + values.size(), "",
                        ItemKind.TYPE, "enum",
                        "", "", "", 5, null, values
                );
                addItem(enumType);
                enumsByName.put(name, enumType);
            }
        }
    }

    private void loadKeywordsFile(Context context) throws Exception {
        JSONObject json = readJson(context, "keywords.json");
        JSONArray keywords = json.optJSONArray("keywords");
        if (keywords == null) return;

        for (int i = 0; i < keywords.length(); i++) {
            JSONObject k = keywords.optJSONObject(i);
            if (k == null) continue;
            String name = k.optString("name", "");
            if (name.isEmpty()) continue;

            Item item = new Item(
                    name, k.optString("description", ""), "",
                    ItemKind.KEYWORD, k.optString("category", ""),
                    "", "", "", 4, null, null
            );
            addItem(item);
        }
    }

    private void loadImportsFile(Context context) throws Exception {
        JSONObject json = readJson(context, "imports.json");
        JSONArray imports = json.optJSONArray("imports");
        if (imports == null) return;

        for (int i = 0; i < imports.length(); i++) {
            JSONObject imp = imports.optJSONObject(i);
            if (imp == null) continue;
            String path = imp.optString("path", "");
            if (path.isEmpty()) continue;

            Item item = new Item(
                    path, imp.optString("description", ""),
                    "import '" + path + "';",
                    ItemKind.IMPORT, imp.optString("category", ""),
                    "", "", "", imp.optInt("priority", 5), null, null
            );
            addItem(item);
        }
    }

    private void loadSnippetsFile(Context context) throws Exception {
        JSONObject json = readJson(context, "snippets.json");
        JSONArray snippets = json.optJSONArray("snippets");
        if (snippets == null) return;

        for (int i = 0; i < snippets.length(); i++) {
            JSONObject s = snippets.optJSONObject(i);
            if (s == null) continue;
            String prefix = s.optString("prefix", "");
            if (prefix.isEmpty()) continue;

            Item item = new Item(
                    prefix, s.optString("description", ""),
                    s.optString("body", ""),
                    ItemKind.WIDGET, s.optString("category", ""),
                    "", "", "", 7, null, null
            );
            addItem(item);
        }
    }

    // ============================================================
    // الفهرسة
    // ============================================================
    private void addItem(Item item) {
        if (item == null || item.name.isEmpty()) return;
        allItems.add(item);
    }

    private void buildFirstCharIndex() {
        byFirstChar.clear();
        for (Item item : allItems) {
            String key = firstChar(item.name);
            byFirstChar.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        for (List<Item> list : byFirstChar.values()) {
            Collections.sort(list, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return Integer.compare(b.priority, a.priority);
                }
            });
        }
    }

    private String firstChar(String s) {
        if (s == null || s.isEmpty()) return "_";
        char c = s.charAt(0);
        if (c >= 'A' && c <= 'Z') return String.valueOf(Character.toLowerCase(c));
        if (c >= 'a' && c <= 'z') return String.valueOf(c);
        if (c >= '0' && c <= '9') return "#";
        return "_";
    }

    // ============================================================
    // البحث العام
    // ============================================================
    public List<Item> byPrefix(String prefix, int limit) {
        return byPrefix(prefix, limit, null);
    }

    public List<Item> byPrefixAndWidget(String prefix, String widgetName, int limit) {
        return byPrefix(prefix, limit, widgetName);
    }

    private List<Item> byPrefix(String prefix, int limit, String widgetContext) {
        if (prefix == null) prefix = "";
        String lower = prefix.toLowerCase(Locale.US);
        List<Item> results = new ArrayList<>();

        if (lower.isEmpty()) {
            List<Item> sorted = new ArrayList<>(allItems);
            Collections.sort(sorted, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return Integer.compare(b.priority, a.priority);
                }
            });
            for (Item item : sorted) {
                if (results.size() >= limit) break;
                if (!matchesContext(item, widgetContext)) continue;
                results.add(item);
            }
            return results;
        }

        String firstChar = lower.substring(0, 1);
        List<Item> bucket = byFirstChar.get(firstChar);
        if (bucket == null) return results;

        for (Item item : bucket) {
            if (results.size() >= limit) break;
            if (!item.name.toLowerCase(Locale.US).startsWith(lower)) continue;
            if (!matchesContext(item, widgetContext)) continue;
            results.add(item);
        }

        Collections.sort(results, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int lenCmp = Integer.compare(a.name.length(), b.name.length());
                if (lenCmp != 0) return lenCmp;
                return Integer.compare(b.priority, a.priority);
            }
        });

        return results;
    }

    private boolean matchesContext(Item item, String widgetContext) {
        if (item == null) return false;
        if (item.kind != ItemKind.PROPERTY) return true;
        if (widgetContext == null || widgetContext.isEmpty()) return true;
        return item.appliesTo(widgetContext);
    }

    // ============================================================
    // ✅ الإصدار الجديد: byPrefixWithVariants
    // ============================================================

    /**
     * مثل byPrefixAndWidget، لكن:
     *   - لكل widget له snippet مفيد، يُعيد نسختين:
     *     1. اسم فقط (بدون snippet).
     *     2. اسم + ( … ) مع snippet.
     */
    public List<Item> byPrefixWithVariants(String prefix, String widgetContext, int limit) {
        List<Item> base = byPrefix(prefix, limit, widgetContext);
        return expandVariants(base, limit);
    }

    /**
     * ✅ يُوسّع القائمة: كل widget ذو snippet مفيد → نسختان.
     */
    private List<Item> expandVariants(List<Item> base, int limit) {
        if (base == null || base.isEmpty()) return base;

        List<Item> expanded = new ArrayList<>();
        for (Item item : base) {
            if (item.kind == ItemKind.WIDGET && isUsefulSnippet(item.snippet)) {
                // نسخة 1: اسم فقط
                expanded.add(item.asPlainVariant());
                // نسخة 2: snippet
                expanded.add(item.asSnippetVariant(item.name + "( … )"));
            } else {
                // نسخة واحدة عادية
                expanded.add(item);
            }
            if (expanded.size() >= limit) break;
        }

        // إعادة الترتيب: الأولوية للنسخة الأولى (بدون snippet)
        Collections.sort(expanded, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                // 1. Alphabetical
                String aBase = baseName(a.name);
                String bBase = baseName(b.name);
                int cmp = aBase.compareToIgnoreCase(bBase);
                if (cmp != 0) return cmp;
                // 2. النسخة بدون snippet أولاً
                if (a.isSnippetVariant != b.isSnippetVariant) {
                    return a.isSnippetVariant ? 1 : -1;
                }
                return 0;
            }
        });

        if (expanded.size() > limit) {
            expanded = new ArrayList<>(expanded.subList(0, limit));
        }

        return expanded;
    }

    /**
     * استخراج الاسم الأساسي (بدون "( … )").
     */
    private String baseName(String name) {
        if (name == null) return "";
        int idx = name.indexOf('(');
        return idx > 0 ? name.substring(0, idx).trim() : name;
    }

    /**
     * ✅ القاعدة: snippet "مفيد" إذا:
     *   - يحتوي على \n
     *   - وطوله > 40 حرف
     */
    public static boolean isUsefulSnippet(String snippet) {
        if (snippet == null || snippet.isEmpty()) return false;
        if (!snippet.contains("\n")) return false;
        return snippet.length() > 40;
    }

    // ============================================================
    // بحث خاص
    // ============================================================
    public List<Item> propertiesFor(String widgetName) {
        if (widgetName == null) return Collections.emptyList();
        List<Item> props = propertiesByWidget.get(widgetName);
        return props == null ? Collections.<Item>emptyList() : props;
    }

    public List<String> enumValues(String enumName) {
        if (enumName == null) return Collections.emptyList();
        Item enumItem = enumsByName.get(enumName);
        if (enumItem == null) return Collections.emptyList();
        return enumItem.enumValues;
    }

    public Item widget(String name) {
        if (name == null) return null;
        return widgetsByName.get(name);
    }

    // ============================================================
    // إحصائيات
    // ============================================================
    public int totalItems() { return allItems.size(); }
    public boolean isLoaded() { return loaded; }

    public String stats() {
        return "DartCompletions{" +
                "total=" + allItems.size() +
                ", widgets=" + widgetsByName.size() +
                ", properties=" + propertiesByWidget.size() +
                ", enums=" + enumsByName.size() +
                "}";
    }

    // ============================================================
    // أدوات
    // ============================================================
    private static List<String> readStringArray(JSONArray array) {
        if (array == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String s = array.optString(i, "");
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }
}