package com.flutteride.codweva;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** تخزين محلي لحالة محرر المشروع فقط، منفصل عن المصادقة والبناء. */
public final class EditorStateStore {
    private static final String PREFS_NAME = "klencod_editor_state";
    private static final String FAVORITES_PREFIX = "favorites_";
    private static final String TABS_PREFIX = "tabs_";
    private static final String ACTIVE_PREFIX = "active_";

    private final SharedPreferences prefs;

    public EditorStateStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> getFavorites(String projectKey) {
        Set<String> stored = prefs.getStringSet(FAVORITES_PREFIX + projectKey, Collections.<String>emptySet());
        return new HashSet<>(stored);
    }

    public void setFavorites(String projectKey, Set<String> favorites) {
        prefs.edit().putStringSet(FAVORITES_PREFIX + projectKey, new HashSet<>(favorites)).apply();
    }

    public List<String> getRecentTabs(String projectKey) {
        String encoded = prefs.getString(TABS_PREFIX + projectKey, "");
        ArrayList<String> result = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return result;
        String[] values = encoded.split("\\n");
        for (String value : values) {
            if (!value.trim().isEmpty()) result.add(value);
        }
        return result;
    }

    public void setRecentTabs(String projectKey, List<String> tabs) {
        StringBuilder builder = new StringBuilder();
        for (String tab : tabs) {
            if (tab != null && !tab.isEmpty()) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(tab);
            }
        }
        prefs.edit().putString(TABS_PREFIX + projectKey, builder.toString()).apply();
    }

    public String getActiveTab(String projectKey) {
        return prefs.getString(ACTIVE_PREFIX + projectKey, "");
    }

    public void setActiveTab(String projectKey, String path) {
        prefs.edit().putString(ACTIVE_PREFIX + projectKey, path == null ? "" : path).apply();
    }

    public void clearProjectState(String projectKey) {
        prefs.edit().remove(FAVORITES_PREFIX + projectKey)
                .remove(TABS_PREFIX + projectKey)
                .remove(ACTIVE_PREFIX + projectKey)
                .apply();
    }
}
