package com.siweina.misoul;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 把设置页的配置交给被 hook 的小爱进程。
 *
 * <p>LSPosed 的 getRemotePreferences 在本机读不到值，因此用 ContentResolver.call。
 */
public final class PersonaProvider extends ContentProvider {

    public static final String AUTHORITY = "io.github.siweina.misoul.config";
    public static final String METHOD = "persona";
    /** 小爱进程回传工具调用：method=usage, arg=工具名。 */
    public static final String METHOD_USAGE = "usage";
    /** 小爱进程记录成功流程：extras.entry =「应用|末步目标|Base64(流程原文)」。 */
    public static final String METHOD_FLOW_PUT = "flow_put";
    /** prefs 里存「工具名|次数|最后调用毫秒」，一行一条。 */
    public static final String KEY_USAGE = "usage";
    /** hook 最近一次来取配置的时间（毫秒），用来判断模块是否真的活着。 */
    public static final String KEY_HOOK_SEEN = "hook_last_seen";
    private static volatile long lastSeenWrite;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_USAGE.equals(method)) {
            recordUsage(arg != null ? arg : (extras == null ? null : extras.getString("tool")));
            return null;
        }
        if (METHOD_FLOW_PUT.equals(method)) {
            recordFlow(extras == null ? null : extras.getString("entry"));
            Bundle ok = new Bundle();
            ok.putBoolean("ok", true);
            return ok;
        }
        if (!METHOD.equals(method)) return null;
        touchHookSeen();
        SharedPreferences prefs = getContext().getSharedPreferences(PersonaHook.PREFS, 0);
        Bundle bundle = new Bundle();
        bundle.putBoolean(PersonaHook.KEY_ENABLED, prefs.getBoolean(PersonaHook.KEY_ENABLED, false));
        bundle.putBoolean(PersonaHook.KEY_VIP_UNLOCK,
                prefs.getBoolean(PersonaHook.KEY_VIP_UNLOCK, false));
        bundle.putBoolean(PersonaHook.KEY_FLOW_PLAN,
                prefs.getBoolean(PersonaHook.KEY_FLOW_PLAN, false));
        bundle.putString(PersonaHook.KEY_FLOW_STORE,
                prefs.getString(PersonaHook.KEY_FLOW_STORE, ""));
        bundle.putBoolean(PersonaHook.KEY_FIX_PROMPT, prefs.getBoolean(PersonaHook.KEY_FIX_PROMPT, true));
        bundle.putString(PersonaHook.KEY_NAME, prefs.getString(PersonaHook.KEY_NAME, PersonaHook.DEFAULT_NAME));
        bundle.putString(PersonaHook.KEY_SOUL, prefs.getString(PersonaHook.KEY_SOUL, ""));
        bundle.putBoolean(PersonaHook.KEY_PROFILE_ENABLED,
                prefs.getBoolean(PersonaHook.KEY_PROFILE_ENABLED, true));
        bundle.putString(PersonaHook.KEY_PROFILE_NAME,
                prefs.getString(PersonaHook.KEY_PROFILE_NAME, PersonaHook.DEFAULT_PROFILE_NAME));
        bundle.putString(PersonaHook.KEY_PROFILE_SIGNATURE,
                prefs.getString(PersonaHook.KEY_PROFILE_SIGNATURE, PersonaHook.DEFAULT_PROFILE_SIGNATURE));
        bundle.putString(PersonaHook.KEY_PROFILE_WELCOME,
                prefs.getString(PersonaHook.KEY_PROFILE_WELCOME, PersonaHook.DEFAULT_PROFILE_WELCOME));
        bundle.putString(PersonaHook.KEY_TEXT_MAP, prefs.getString(PersonaHook.KEY_TEXT_MAP, ""));
        bundle.putString(PersonaHook.KEY_STYLE_CONTRACT,
                prefs.getString(PersonaHook.KEY_STYLE_CONTRACT, PersonaHook.DEFAULT_STYLE_CONTRACT));
        bundle.putBoolean(PersonaHook.KEY_TOOL_TRIM,
                prefs.getBoolean(PersonaHook.KEY_TOOL_TRIM, true));
        bundle.putBoolean(PersonaHook.KEY_TOOL_FILTER,
                prefs.getBoolean(PersonaHook.KEY_TOOL_FILTER, false));
        bundle.putString(PersonaHook.KEY_TOOL_KEEP,
                prefs.getString(PersonaHook.KEY_TOOL_KEEP, ""));
        bundle.putString(PersonaHook.KEY_TOOL_GROUPS, groupPrefixes());
        bundle.putString(PersonaHook.KEY_TOOL_DEFAULTS,
                readAsset("tool_overrides.default.txt"));
        bundle.putString(PersonaHook.KEY_RULES_TEXT,
                prefs.getString(PersonaHook.KEY_RULES_TEXT, ""));
        bundle.putBoolean(PersonaHook.KEY_TOOL_DEEP,
                prefs.getBoolean(PersonaHook.KEY_TOOL_DEEP, false));
        bundle.putString(PersonaHook.KEY_TOOL_OVERRIDES,
                prefs.getString(PersonaHook.KEY_TOOL_OVERRIDES, ""));
        return bundle;
    }

    /** 记下 hook 最近一次来取配置的时间（节流到 20 秒一次，避免频繁写盘）。 */
    private void touchHookSeen() {
        long now = System.currentTimeMillis();
        if (now - lastSeenWrite < 20_000L) return;
        lastSeenWrite = now;
        try {
            getContext().getSharedPreferences(PersonaHook.PREFS, 0)
                    .edit().putLong(KEY_HOOK_SEEN, now).apply();
        } catch (Throwable ignored) {
            // 写失败不影响取配置
        }
    }
    /** 把一次工具调用累加进 prefs；单条上限 300 个工具，避免无限增长。 */
    private void recordUsage(String tool) {
        if (tool == null || tool.isEmpty()) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PersonaHook.PREFS, 0);
        String raw = prefs.getString(KEY_USAGE, "");
        long now = System.currentTimeMillis();

        java.util.LinkedHashMap<String, long[]> map = new java.util.LinkedHashMap<>();
        if (raw != null) {
            for (String line : raw.split("\n")) {
                String entry = line.trim();
                if (entry.isEmpty()) continue;
                String[] parts = entry.split("\\|");
                if (parts.length < 2) continue;
                try {
                    long count = Long.parseLong(parts[1]);
                    long last = parts.length > 2 ? Long.parseLong(parts[2]) : 0L;
                    map.put(parts[0], new long[]{count, last});
                } catch (NumberFormatException ignored) {
                    // 坏行直接丢掉
                }
            }
        }
        long[] current = map.get(tool);
        if (current == null) current = new long[]{0L, 0L};
        current[0] += 1;
        current[1] = now;
        map.put(tool, current);

        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (java.util.Map.Entry<String, long[]> entry : map.entrySet()) {
            if (kept++ >= 300) break;
            out.append(entry.getKey()).append('|')
                    .append(entry.getValue()[0]).append('|')
                    .append(entry.getValue()[1]).append('\n');
        }
        prefs.edit().putString(KEY_USAGE, out.toString()).apply();
    }

    /** 记录一条成功流程；同应用+目标覆盖旧的，总数上限 50 条。 */
    private void recordFlow(String entry) {
        if (entry == null || entry.isEmpty()) return;
        String[] parts = entry.split("\\|", 3);
        if (parts.length < 3) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PersonaHook.PREFS, 0);
        String raw = prefs.getString(PersonaHook.KEY_FLOW_STORE, "");
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (raw != null) {
            for (String line : raw.split("\n")) {
                String lineEntry = line.trim();
                if (lineEntry.isEmpty()) continue;
                String[] lineParts = lineEntry.split("\\|", 3);
                if (lineParts.length < 3) continue;
                map.put(lineParts[0] + "|" + lineParts[1], lineEntry);
            }
        }
        map.put(parts[0] + "|" + parts[1], entry);
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (String lineEntry : map.values()) {
            if (kept++ >= 50) break;
            out.append(lineEntry).append('\n');
        }
        prefs.edit().putString(PersonaHook.KEY_FLOW_STORE, out.toString()).apply();
    }

    /** 从 assets/tool_groups.txt 取出所有类别前缀，供 hook 判断「未知类别默认保留」。 */
    private String groupPrefixes() {
        StringBuilder builder = new StringBuilder();
        for (String line : readAsset("tool_groups.txt").split("\n")) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            int bar = line.indexOf('|');
            if (bar <= 0) continue;
            if (builder.length() > 0) builder.append(',');
            builder.append(line, 0, bar);
        }
        return builder.toString();
    }

    private String readAsset(String name) {
        try (java.io.InputStream in = getContext().getAssets().open(name);
             java.io.BufferedReader reader =
                     new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
            return builder.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
