package com.flutteride.codweva;

/** حالة ملف مفتوح داخل محرر التبويبات. */
public final class EditorTabState {
    public final String absolutePath;
    public String content = "";
    public int selectionStart = 0;
    public int selectionEnd = 0;
    public int scrollY = 0;
    public boolean loaded = false;
    public boolean dirty = false;

    public EditorTabState(String absolutePath) {
        this.absolutePath = absolutePath;
    }
}
