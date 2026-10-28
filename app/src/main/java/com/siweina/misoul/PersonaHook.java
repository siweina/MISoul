package com.siweina.misoul;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * MISoul：给超级小爱（com.miui.voiceassist）换人格，不动 APK、不改数据目录。
 *
 * <p>注入点：
 * <ul>
 *   <li>{@code rs.a$a.readAsset / readFile} —— 载入 {@code agents/osbot.main/soul.md} 与
 *       {@code prompt.md} 时替换人格文本、修正身份句；</li>
 *   <li>{@code ck.o0.l(String) / m(File)} —— 解析 {@code profile.json} 成 AgentProfile 之后，
 *       替换界面显示名 / 签名 / 欢迎语（profile.json 不走 readAsset，必须单独 hook）。</li>
 * </ul>
 *
 * <p>配置由模块 App 的 {@link PersonaProvider} 通过 ContentResolver.call 提供。
 */
public final class PersonaHook extends XposedModule {

    public static final String PREFS = "persona";

    public static final String KEY_ENABLED = "enabled";
    /** 超级小爱会员解锁（定时任务）：默认关闭，与人格替换开关相互独立。 */
    public static final String KEY_VIP_UNLOCK = "vip_unlock";
    /** 自动化流程：先规划后执行，成功流程优先复用。默认关闭，与会员解锁相互独立。 */
    public static final String KEY_FLOW_PLAN = "flow_plan";
    /** 已记录的成功流程，一行一条「应用|末步目标|Base64(流程原文)」，上限 50 条。 */
    public static final String KEY_FLOW_STORE = "flow_store";
    public static final String KEY_SOUL = "soul";
    public static final String KEY_FIX_PROMPT = "fix_prompt";
    public static final String KEY_NAME = "name";

    public static final String KEY_PROFILE_ENABLED = "profile_enabled";
    public static final String KEY_PROFILE_NAME = "profile_name";
    public static final String KEY_PROFILE_SIGNATURE = "profile_signature";
    public static final String KEY_PROFILE_WELCOME = "profile_welcome";
    /** 用户自定义文案替换，一行一条「原文=新词」。 */
    public static final String KEY_TEXT_MAP = "text_map";
    /** 追加到系统提示末尾的语气契约。 */
    public static final String KEY_STYLE_CONTRACT = "style_contract";
    /** 是否对工具描述做瘦身。 */
    public static final String KEY_TOOL_TRIM = "tool_trim";
    /** 单个工具的描述覆盖，一行一条「工具名=新描述」。 */
    public static final String KEY_TOOL_OVERRIDES = "tool_overrides";
    /** 随包发布的内置精简描述（`### 工具名` 分块），由 ContentProvider 读 assets 传过来。 */
    public static final String KEY_TOOL_DEFAULTS = "tool_defaults";
    /** 工具筛选开关：只保留勾选的类别；关掉即全部保留。 */
    public static final String KEY_TOOL_FILTER = "tool_filter";
    /** 勾选保留的类别前缀，逗号分隔；空 = 不过滤。 */
    public static final String KEY_TOOL_KEEP = "tool_keep";
    /** 按对话关键词临时放开相关类别（默认开）。 */
    public static final String KEY_AUTO_EXPAND = "tool_auto_expand";
    /** 全部已知类别前缀（逗号分隔），用来判断「未知类别默认保留」。 */
    public static final String KEY_TOOL_GROUPS = "tool_groups";
    /** 自定义的 tool_selection_rules.md；空 = 用官方那份。 */
    public static final String KEY_RULES_TEXT = "rules_text";
    /** 工具描述深度压缩（实验）：按块打分 + 每工具硬上限。 */
    public static final String KEY_TOOL_DEEP = "tool_deep";

    /** 「选工具规则」的内置精简版，作为编辑器的初始内容；清空保存即回到官方那份。 */
    public static final String COMPACT_RULES =
            "# 选工具规则（精简版）\n\n"
                    + "## 优先级\n"
                    + "专用命令 > 专用工具 > A2A/MCP/Skill > controlApp（最后手段）。\n"
                    + "已有专用能力能完成时，不要用 controlApp 绕。\n\n"
                    + "## 闹钟 / 日程 / 定时 的区分（最容易混）\n"
                    + "- 「叫我起床」「早上 X 点闹我」→ `deskclock add`（闹钟，不支持指定日期）\n"
                    + "- 「明天 X 点开会提醒」「某天 X 点提醒我」→ `calendar create`（日程）\n"
                    + "- 「明天有会但没说几点」→ 先问具体时间，<b>禁止默认 9 点</b>\n"
                    + "- 「5 分钟后叫我」「到点跑个 AI 任务」→ `timer set`（定时/倒计时任务，不是闹钟）\n"
                    + "- 「每年 X 月 X 号」「生日/纪念日/倒数日」→ `calendar new-reminder`\n"
                    + "- 「上车后/关闹钟后执行」→ `timer set --event-type-id car_boarded|alarm_dismissed`，不要换算成时间\n\n"
                    + "## 时间与参数格式\n"
                    + "- 时间统一 ISO：`2026-10-19T07:00`；不要写「明天早上」这种相对说法进参数。\n"
                    + "- 坐标经度在前、纬度在后；缺省内/城市时先问，不要瞎填。\n"
                    + "- 参数只传用户原话里明确过的信息，缺失就问一句，不要替用户假设。\n\n"
                    + "## 什么时候不要用工具\n"
                    + "- 纯闲聊、情绪回应、解释概念：直接回答，别调工具。\n"
                    + "- 用户只是问「能不能做」，先回答再问要不要做。\n"
                    + "- 工具失败一次就如实说，不要换个工具硬凑。\n";
    /** 深度压缩时每个工具保留的字符上限。 */
    private static final int DEEP_BUDGET = 800;

    /** 契约里自带的标记，用来避免重复追加。 */
    private static final String STYLE_MARKER = "【语气契约】";

    public static final String DEFAULT_STYLE_CONTRACT =
            "下面是硬性语气要求，优先于上面任何设定：\n"
                    + "1. 说话像熟人发消息：短句为主，一条尽量不超过 40 字，能一句说完就别分点。\n"
                    + "2. 禁止书面语与客服腔：不要用「首先/其次/最后/综上所述/此外/值得注意的是」"
                    + "，不要出现「希望对你有帮助」「作为 AI」「建议您」这类句式，不要用「您」。\n"
                    + "3. 不要动不动列清单或加小标题；只有对方明确要步骤、要对比时才用列表。\n"
                    + "4. 允许口语、语气词和碎片句（如「诶」「行吧」「草」），该吐槽就吐槽，别端着。\n"
                    + "5. 仍然保持人格设定里的自称、称呼和性格，别因为这条要求变成另一个人。";

    public static final String DEFAULT_NAME = "DeepSeek（日常被叫大肥鱼）";
    public static final String DEFAULT_PROFILE_NAME = "大肥鱼";
    public static final String DEFAULT_PROFILE_SIGNATURE = "你一个人的赛博小鲸鱼";
    public static final String DEFAULT_PROFILE_WELCOME = "我回来了";

    private static final String TAG = "MISoul";
    private static final String PKG = "com.miui.voiceassist";
    private static final String AGENT = "osbot.main";
    private static final String AGENT_PREFIX = "agents/" + AGENT + "/";
    private static final String SOUL = "soul.md";
    private static final String PROMPT = "prompt.md";
    private static final String RULES = "tool_selection_rules.md";

    /** 官方 prompt.md 里那句"表明自己是超级小爱"，做定点替换而不是整份覆盖。 */
    private static final String OFFICIAL_CLAIM = "并表明自己是超级小爱";

    /** 界面上不会经由 agent 数据、而是直接渲染的品牌文案。 */
    private static final String BRAND_NAME = "超级小爱";

    private final Object installLock = new Object();
    private boolean installed;
    private boolean reportedFailure;

    private volatile Context appContext;
    private volatile Bundle cachedConfig;
    private volatile long cachedAt;
    private volatile Constructor<?> profileConstructor;
    private volatile String lastLoggedName;
    private volatile String lastLoggedText;
    private volatile boolean contractLogged;
    private volatile boolean toolTrimLogged;
    private volatile boolean toolFilterLogged;
    private volatile boolean usageReportWarned;
    private volatile boolean contextDumped;
    private final java.util.concurrent.atomic.AtomicInteger cacheProbeCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile String cachedUserText;
    private volatile long cachedUserTextAt;
    private final java.util.concurrent.atomic.AtomicInteger toolFilterCalls =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger toolCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong toolCharsBefore =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong toolCharsAfter =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong toolParamChars =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile int systemPromptChars = -1;
    private volatile boolean statsScheduled;
    private final java.util.concurrent.atomic.AtomicInteger paramProbeCalls =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger paramTrimCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong paramCharsBefore =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong paramCharsAfter =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile ClassLoader appLoader;
    private final java.util.Map<String, Integer> promptParts =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Object jsonInstance;
    private static volatile java.lang.reflect.Method jsonParse;
    private volatile boolean jsonRebuildWarned;
    private volatile java.util.Map<String, String> cachedOverrides;
    private volatile java.util.Map<String, String> cachedBuiltin;
    private volatile String cachedBuiltinRaw;
    private volatile String cachedOverridesRaw;
    private volatile java.util.Map<String, String> cachedTextMap;
    private volatile String cachedTextMapStamp;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!PKG.equals(param.getPackageName())) return;
        try {
            install(param.getClassLoader());
        } catch (Throwable failure) {
            synchronized (installLock) {
                if (reportedFailure) return;
                reportedFailure = true;
            }
            // 找不到目标类/方法时安静降级：小爱照常以官方人格运行。
            log(Log.ERROR, TAG, "install failed, 小爱将以官方人格运行: " + failure, failure);
        }
    }

    /**
     * 热重载：模块 APK 更新后，LSPosed 会在已运行的小爱进程里重新加载模块，
     * 不必强行停止小爱。这里先摘掉旧 hook（否则会重复生效），再用新代码装一遍。
     */
    @Override
    public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        log(Log.INFO, TAG, "hot reloading: " + param.getExtras());
        return true;
    }

    @Override
    public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
        java.util.List<XposedInterface.HookHandle> old = param.getOldHookHandles();
        // 热重载会创建一个新的模块实例，旧实例的字段不会带过来；
        // 但旧 hook 句柄里握着目标方法，可以反推出被 hook 进程的 ClassLoader。
        ClassLoader loader = appLoader;
        if (loader == null) {
            for (XposedInterface.HookHandle handle : old) {
                try {
                    java.lang.reflect.Executable executable = handle.getExecutable();
                    if (executable != null) {
                        loader = executable.getDeclaringClass().getClassLoader();
                        if (loader != null) break;
                    }
                } catch (Throwable ignored) {
                    // 换下一个句柄试
                }
            }
        }

        if (loader == null) {
            loader = currentAppClassLoader();
        }

        int removed = 0;
        for (XposedInterface.HookHandle handle : old) {
            try {
                handle.unhook();
                removed++;
            } catch (Throwable ignored) {
                // 单个句柄摘不掉就继续，反正下面会重装
            }
        }

        synchronized (installLock) {
            installed = false;
            reportedFailure = false;
        }
        // 计数器也重置，避免上一次运行的数字污染新的统计
        toolCount.set(0);
        toolCharsBefore.set(0);
        toolCharsAfter.set(0);
        toolParamChars.set(0);
        toolFilterCalls.set(0);
        contractLogged = false;
        toolTrimLogged = false;
        toolFilterLogged = false;
        statsScheduled = false;
        promptParts.clear();

        log(Log.INFO, TAG, "hot reloaded: 摘掉 " + removed + " 个旧 hook，classLoader=" + loader);
        if (loader == null) {
            log(Log.WARN, TAG, "hot reload: 拿不到目标 ClassLoader，等下次包就绪");
            return;
        }
        appLoader = loader;
        try {
            install(loader);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "hot reload 后重装失败: " + failure, failure);
        }
    }

    private void install(ClassLoader loader) throws Exception {
        synchronized (installLock) {
            if (installed) return;
        }

        appLoader = loader;
        hookPromptFiles(loader);
        try {
            hookProfile(loader);
        } catch (Throwable failure) {
            // 界面形象是可选能力：失败时不影响人格文本注入。
            log(Log.ERROR, TAG, "profile hook skipped: " + failure);
        }
        try {
            hookAgentName(loader);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "name hook skipped: " + failure);
        }
        try {
            hookConfigAsset();
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "config hook skipped: " + failure);
        }
        try {
            hookBrandText();
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "text hook skipped: " + failure);
        }
        try {
            hookStyleContract(loader);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "style contract skipped: " + failure);
        }
        try {
            hookMessageCache(loader);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "message cache skipped: " + failure);
        }
        try {
            hookToolUsage(loader);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "tool usage skipped: " + failure);
        }
        try {
            hookToolFilter(loader);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "tool filter skipped: " + failure);
        }
        try {
            hookToolDescription(loader);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "tool trim skipped: " + failure);
        }
        try {
            hookVipUnlock(loader);
        } catch (Throwable failure) {
            // 会员解锁是可选能力：失败时不影响人格等原有功能。
            log(Log.ERROR, TAG, "vip unlock skipped: " + failure);
        }
        try {
            hookFlowControl(loader);
        } catch (Throwable failure) {
            // 流程注入是可选能力：失败时不影响人格、会员解锁等原有功能。
            log(Log.ERROR, TAG, "flow control skipped: " + failure);
        }

        synchronized (installLock) {
            installed = true;
        }
        log(Log.INFO, TAG, "installed");
    }

    // ---- 对话页标题：AgentDefinition.name（来自 config.json 的 name 字段） ----

    /**
     * 对话页顶部标题与开场语旁边的「超级小爱」是品牌字符串资源，直接渲染在 TextView 上，
     * 不经过 agent 数据。这里在 setText 时把它换成配置里的显示名。
     * 只匹配长度 4 且完全相等的串，避免影响其它文本。
     */
    private void hookBrandText() throws Exception {
        Class<?> textView = Class.forName("android.widget.TextView");
        Method setText = textView.getDeclaredMethod("setText", CharSequence.class);
        hook(setText).intercept(chain -> {
            String replacement = textReplacement(chain.getArg(0));
            return replacement == null ? chain.proceed() : chain.proceed(new Object[]{replacement});
        });

        // 有些地方用 setText(resId) / 带 BufferType 的重载
        Method setTextTyped = null;
        for (Method m : textView.getDeclaredMethods()) {
            if ("setText".equals(m.getName()) && m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == CharSequence.class) {
                setTextTyped = m;
                break;
            }
        }
        if (setTextTyped != null) {
            final Method target = setTextTyped;
            hook(target).intercept(chain -> {
                String replacement = textReplacement(chain.getArg(0));
                return replacement == null
                        ? chain.proceed()
                        : chain.proceed(new Object[]{replacement, chain.getArg(1)});
            });
        }

        Class<?> resources = Class.forName("android.content.res.Resources");
        for (String name : new String[]{"getText", "getString"}) {
            for (Method m : resources.getDeclaredMethods()) {
                if (!name.equals(m.getName()) || m.getParameterCount() != 1
                        || m.getParameterTypes()[0] != int.class) {
                    continue;
                }
                hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    String replacement = textReplacement(result);
                    return replacement == null ? result : replacement;
                });
            }
        }

        // 开场白是一组随机候选（字符串数组），必须单独处理 getStringArray
        for (Method m : resources.getDeclaredMethods()) {
            if (!"getStringArray".equals(m.getName()) || m.getParameterCount() != 1
                    || m.getParameterTypes()[0] != int.class) {
                continue;
            }
            hook(m).intercept(chain -> {
                Object result = chain.proceed();
                if (!(result instanceof String[] array)) return result;
                java.util.Map<String, String> map = textMap();
                if (map.isEmpty()) return result;
                String[] copy = null;
                for (int i = 0; i < array.length; i++) {
                    String hit = map.get(array[i]);
                    if (hit != null && !hit.equals(array[i])) {
                        if (copy == null) copy = array.clone();
                        copy[i] = hit;
                    }
                }
                return copy == null ? result : copy;
            });
        }
        log(Log.INFO, TAG, "hooking TextView/Resources 文案替换");
    }

    /** 命中映射时返回替换文本，否则 null。 */
    private String textReplacement(Object candidate) {
        if (!(candidate instanceof CharSequence cs)) return null;
        java.util.Map<String, String> map = textMap();
        if (map.isEmpty()) return null;
        return map.get(cs.toString());
    }

    /**
     * 文案映射：「超级小爱 → 显示名」是内置的，另外解析用户在「文案替换」里写的
     * 「原文=新词」，用户写的覆盖内置。
     */
    private java.util.Map<String, String> textMap() {
        Bundle config = config();
        if (config == null || !config.getBoolean(KEY_ENABLED, false)
                || !config.getBoolean(KEY_PROFILE_ENABLED, true)) {
            return java.util.Collections.emptyMap();
        }
        String raw = config.getString(KEY_TEXT_MAP, "");
        String name = config.getString(KEY_PROFILE_NAME, DEFAULT_PROFILE_NAME);
        String stamp = (raw == null ? "" : raw) + "\u0000" + (name == null ? "" : name);
        if (stamp.equals(cachedTextMapStamp) && cachedTextMap != null) {
            return cachedTextMap;
        }

        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (name != null && !name.trim().isEmpty()) {
            map.put(BRAND_NAME, name);
        }
        if (raw != null) {
            for (String line : raw.split("\n")) {
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                int eq = entry.indexOf('=');
                if (eq <= 0) continue;
                String from = entry.substring(0, eq).trim();
                String to = entry.substring(eq + 1).trim();
                if (!from.isEmpty()) map.put(from, to);
            }
        }
        cachedTextMap = map;
        cachedTextMapStamp = stamp;
        if (map.size() > 1) log(Log.INFO, TAG, "文案映射 " + map.size() + " 条");
        return map;
    }

    /**
     * UI 读的是 AgentDefinition 的 name 字段（Kotlin 同类内直接读字段，不走 getter），
     * 所以真正的办法是在 App 读 assets 里的 config.json 时改掉 "name"。
     */
    private void hookConfigAsset() throws Exception {
        Method open = Class.forName("android.content.res.AssetManager")
                .getDeclaredMethod("open", String.class);
        hook(open).intercept(chain -> {
            Object pathArg = chain.getArg(0);
            if (!(pathArg instanceof String path)
                    || !path.endsWith("/config.json")
                    || !path.contains("/" + AGENT + "/")) {
                return chain.proceed();
            }
            Bundle config = config();
            if (config == null || !config.getBoolean(KEY_ENABLED, false)
                    || !config.getBoolean(KEY_PROFILE_ENABLED, true)) {
                return chain.proceed();
            }
            String name = config.getString(KEY_PROFILE_NAME, DEFAULT_PROFILE_NAME);
            if (name == null || name.trim().isEmpty()) return chain.proceed();

            Object result = chain.proceed();
            if (!(result instanceof java.io.InputStream stream)) return result;
            byte[] raw;
            try {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = stream.read(chunk)) > 0) buffer.write(chunk, 0, read);
                raw = buffer.toByteArray();
            } catch (Throwable failure) {
                log(Log.ERROR, TAG, "config asset read failed: " + failure);
                return result;
            }
            try {
                String text = new String(raw, "UTF-8");
                String patched = text.replaceAll("\"name\"\\s*:\\s*\"[^\"]*\"",
                        java.util.regex.Matcher.quoteReplacement("\"name\": \"" + name + "\""));
                if (patched.equals(text)) return new java.io.ByteArrayInputStream(raw);
                if (!name.equals(lastLoggedName)) {
                    lastLoggedName = name;
                    log(Log.INFO, TAG, "config.json name -> " + name);
                }
                return new java.io.ByteArrayInputStream(patched.getBytes("UTF-8"));
            } catch (Throwable failure) {
                log(Log.ERROR, TAG, "config asset patch failed: " + failure);
                return new java.io.ByteArrayInputStream(raw);
            }
        });
        log(Log.INFO, TAG, "hooking AssetManager.open (config.json name)");
    }

    /**
     * 语气契约：追加到最终系统提示的末尾。
     *
     * 小爱的系统提示由 soul/prompt/security/bootstrap 等片段拼成，工具定义作为 function-calling
     * 的 tools 数组跟在后面，模型对结尾更敏感，所以这段短契约放在最后比写在开头更管用。
     */
    private void hookStyleContract(ClassLoader loader) throws Exception {
        // 这个方法在运行时的 agent 对象 ck.d0 上，不在 AgentDefinition 上
        Class<?> agent = Class.forName("ck.d0", false, loader);
        Method getPrompt = agent.getDeclaredMethod("getResolvedSystemPrompt");
        hook(getPrompt).intercept(chain -> {
            Object result = chain.proceed();
            Bundle config = config();
            if (config == null || !config.getBoolean(KEY_ENABLED, false)) return result;
            String contract = config.getString(KEY_STYLE_CONTRACT, DEFAULT_STYLE_CONTRACT);
            if (contract == null || contract.trim().isEmpty()) return result;
            if (!(result instanceof String base)) return result;
            if (!base.contains(STYLE_MARKER)) {
                systemPromptChars = base.length() + contract.length();
                scheduleStats();
            }
            if (!contractLogged) {
                contractLogged = true;
                log(Log.INFO, TAG, "语气契约已追加（" + contract.length() + " 字，系统提示 "
                        + base.length() + " -> " + (base.length() + contract.length()) + " 字）");
            }
            return base + "\n\n" + STYLE_MARKER + "\n" + contract.trim();
        });
        log(Log.INFO, TAG, "hooking ck.d0.getResolvedSystemPrompt (语气契约)");
    }

    /**
     * 缓存本轮用户原话：beforeModelCall 能拿到 List<Message>，
     * 这时消息已经齐了；getTools 阶段还没有，所以先存下来给它用。
     */
    private void hookMessageCache(ClassLoader loader) throws Exception {
        Class<?> plugin = Class.forName("uj.a", false, loader);
        for (Method method : plugin.getDeclaredMethods()) {
            if (!"beforeModelCall".equals(method.getName())) continue;
            if (method.getParameterCount() < 3) continue;
            if (!java.util.List.class.isAssignableFrom(method.getParameterTypes()[2])) continue;
            hook(method).intercept(chain -> {
                try {
                    if (cacheProbeCount.incrementAndGet() <= 3) {
                        log(Log.INFO, TAG, "消息形态：" + describeMessages(chain.getArg(2)));
                    }
                    String text = lastUserTextIn(chain.getArg(2));
                    if (text != null && !text.isBlank()) {
                        cachedUserText = text;
                        cachedUserTextAt = SystemClock.elapsedRealtime();
                    }
                } catch (Throwable ignored) {
                    // 缓存失败不影响正常流程
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooking uj.a.beforeModelCall (消息缓存)");
            return;
        }
        log(Log.WARN, TAG, "没找到 beforeModelCall，消息缓存不可用");
    }

    /** 打印消息列表末尾几条的形态，用来确定用户原话存在哪种类型里。 */
    private static String describeMessages(Object listObject) {
        if (!(listObject instanceof java.util.List<?> list)) return "not-a-list";
        StringBuilder out = new StringBuilder();
        int from = Math.max(0, list.size() - 4);
        for (int i = from; i < list.size(); i++) {
            Object message = list.get(i);
            if (message == null) {
                out.append(" [null]");
                continue;
            }
            String role = "?";
            Object content = null;
            try {
                role = String.valueOf(message.getClass().getMethod("getRole").invoke(message));
                content = message.getClass().getMethod("getContent").invoke(message);
            } catch (Throwable ignored) {
                // 结构不一致
            }
            out.append(" [").append(role).append('/')
                    .append(content == null ? "null" : content.getClass().getSimpleName())
                    .append(':').append(abbreviate(String.valueOf(content))).append(']');
        }
        return out.toString();
    }
    /** 从一个 List<Message> 里取最后一条用户消息。 */
    private static String lastUserTextIn(Object listObject) {
        if (!(listObject instanceof java.util.List<?> list)) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Object message = list.get(i);
            if (message == null) continue;
            try {
                Object role = message.getClass().getMethod("getRole").invoke(message);
                if (role == null
                        || !role.toString().toUpperCase(java.util.Locale.ROOT).contains("USER")) {
                    continue;
                }
                Object content = message.getClass().getMethod("getContent").invoke(message);
                if (content instanceof String s && !s.isBlank()) {
                    String body = s.trim();
                    // 跳过系统注入的提示消息（<potentially_related_tools> 之类）
                    if (!body.startsWith("<")) return body;
                }
            } catch (Throwable ignored) {
                // 换下一条
            }
        }
        return null;
    }
    /** 记录工具调用：ToolExecutor.execute 是所有工具调用的汇聚点。 */
    private void hookToolUsage(ClassLoader loader) throws Exception {
        Class<?> executor = Class.forName("com.aios.osbot.tools.ToolExecutor", false, loader);
        for (Method method : executor.getDeclaredMethods()) {
            if (!"execute".equals(method.getName())) continue;
            if (method.getParameterCount() < 1 || method.getParameterTypes()[0] != String.class) continue;
            hook(method).intercept(chain -> {
                Object arg = chain.getArg(0);
                if (arg instanceof String name && !name.isEmpty()) reportUsage(name);
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooking ToolExecutor.execute (使用统计)");
            return;
        }
        log(Log.WARN, TAG, "没找到 ToolExecutor.execute，使用统计不可用");
    }

    private void reportUsage(String toolName) {
        try {
            Context ctx = context();
            if (ctx == null) return;
            Bundle extras = new Bundle();
            extras.putString("tool", toolName);
            ctx.getContentResolver().call(
                    Uri.parse("content://" + PersonaProvider.AUTHORITY),
                    PersonaProvider.METHOD_USAGE,
                    toolName,
                    extras);
        } catch (Throwable failure) {
            if (!usageReportWarned) {
                usageReportWarned = true;
                log(Log.WARN, TAG, "使用统计回传失败：" + failure);
            }
        }
    }

    /**
     * 工具筛选：在 sj.e.getTools() 返回前把未勾选的类别去掉。
     *
     * 工具列表在每次模型调用前都会从执行上下文取一次，这里过滤最省事；
     * 未知类别（小爱升级后新增的前缀）默认保留，避免被静默砍掉。
     */
    private void hookToolFilter(ClassLoader loader) throws Exception {
        Class<?> context = Class.forName("sj.e", false, loader);
        Method getTools = context.getDeclaredMethod("getTools");
        hook(getTools).intercept(chain -> {
            Object result = chain.proceed();
            int alive = toolFilterCalls.get() + 1;
            // 无条件探针：只要 getTools 被调用就留痕，用来区分「hook 没装上」和「条件没命中」
            if (alive <= 5) {
                log(
                        Log.INFO,
                        TAG,
                        "getTools 调用 " + alive + "，列表大小="
                                + (result instanceof java.util.List<?> l ? l.size() : -1)
                                + "，消息=" + abbreviate(routerText(chain.getThisObject()))
                                + "，历史=" + abbreviate(lastUserText(chain.getThisObject()))
                                + "，缓存=" + abbreviate(cachedUserText));
            }
            // 只要还没 dump 过就打一次，定位消息到底存在哪个字段
            if (!contextDumped) {
                contextDumped = true;
                dumpContext(chain.getThisObject());
            }
            Bundle config = config();
            if (config == null || !config.getBoolean(KEY_ENABLED, false)) return result;
            if (!config.getBoolean(KEY_TOOL_FILTER, false)) return result;
            if (!(result instanceof java.util.List<?> list) || list.isEmpty()) return result;

            java.util.Set<String> keep = splitToSet(config.getString(KEY_TOOL_KEEP, ""));
            if (keep.isEmpty()) return result;
            java.util.Set<String> known = splitToSet(config.getString(KEY_TOOL_GROUPS, ""));

            // 关键词临时扩容：这轮消息里提到航班/酒店/导航……就临时把对应类别放回来，
            // 不需要用户回 App 手动切换。
            if (config.getBoolean(KEY_AUTO_EXPAND, true)) {
                java.util.Set<String> added = new java.util.LinkedHashSet<>();
                expandFromContext(chain.getThisObject(), keep, added);
                if (!added.isEmpty()) {
                    log(Log.INFO, TAG, "关键词扩容：" + added + "（本轮临时放开）");
                }
            }

            java.util.List<Object> filtered = new java.util.ArrayList<>(list.size());
            int beforeChars = 0;
            int afterChars = 0;
            int dropped = 0;
            for (Object item : list) {
                String name = toolName(item);
                int chars = toolChars(item);
                beforeChars += chars;
                String prefix = prefixOf(name);
                boolean isKnown = known.isEmpty() || known.contains(prefix);
                boolean selected = keep.contains(prefix);
                if (isKnown && !selected) {
                    dropped++;
                    continue;
                }
                filtered.add(item);
                afterChars += chars;
            }
            int call = toolFilterCalls.incrementAndGet();
            if (call <= 6) {
                log(
                        Log.INFO,
                        TAG,
                        "筛选调用 " + call + "：" + list.size() + " -> " + filtered.size()
                                + " 个，描述 " + beforeChars + " -> " + afterChars + " 字");
            }
            if (dropped > 0 && !toolFilterLogged) {
                toolFilterLogged = true;
                log(
                        Log.INFO,
                        TAG,
                        "工具筛选：" + list.size() + " -> " + filtered.size() + " 个，描述约 "
                                + beforeChars + " -> " + afterChars + " 字");
            }
            return filtered;
        });
        log(Log.INFO, TAG, "hooking sj.e.getTools (工具筛选)");
    }

    /** 拿当前进程的 ClassLoader：热重载后的新实例里没有旧字段，只能这样取。 */
    private static ClassLoader currentAppClassLoader() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object app = activityThread.getMethod("currentApplication").invoke(null);
            if (app instanceof android.content.Context context) {
                return context.getClassLoader();
            }
        } catch (Throwable ignored) {
            // 退到系统 ClassLoader
        }
        try {
            return ClassLoader.getSystemClassLoader();
        } catch (Throwable ignored) {
            return null;
        }
    }
    /** 关键词 → 类别：命中就临时放开，不改用户保存的筛选。 */
    private static final String[] KEYWORD_RULES = {
        "航班|机票|机场|飞机|登机|起飞|降落|variflight,ctrip_travel",
        "酒店|民宿|住宿|订房|携程|ctrip_travel",
        "导航|地图|路线|开车|驾车|路况|堵车|amap_car,amap-navigation",
        "支付宝|付款|点餐|外卖|买单|alipay",
        "车窗|空调|车控|车辆|control",
        "相册|照片|图片|截图|gallery",
        "笔记|便签|记一下|备忘录|notes",
        "电话|打电话|短信|通讯录|phone,telecom,im",
        "音乐|播放|放首歌|music",
        "文件|读取|写入|保存到|read,send,file",
        "闹钟|闹铃|叫我起床|deskclock",
        "日程|会议|提醒|calendar",
        "倒计时|几分钟后|定时|timer",
        "打开应用|关闭应用|设置|voiceassist",
    };

    private void expandFromContext(Object context, java.util.Set<String> keep,
                                   java.util.Set<String> added) {
        // 优先用路由决策里的原话：getTools 时消息列表还没填，但它已经有了
        String text = routerText(context);
        if (text == null || text.isEmpty()) text = lastUserText(context);
        if ((text == null || text.isEmpty()) && cachedUserText != null
                && SystemClock.elapsedRealtime() - cachedUserTextAt < 120_000L) {
            text = cachedUserText;   // 由 beforeModelCall 缓存的本轮原话
        }
        if (text == null || text.isEmpty()) return;
        for (String rule : KEYWORD_RULES) {
            int split = rule.lastIndexOf('|');
            if (split <= 0) continue;
            String keywords = rule.substring(0, split);
            String groups = rule.substring(split + 1);
            boolean hit = false;
            for (String keyword : keywords.split("\\|")) {
                if (!keyword.isEmpty() && text.contains(keyword)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) continue;
            for (String group : groups.split(",")) {
                String prefix = group.trim().toLowerCase(java.util.Locale.ROOT);
                if (!prefix.isEmpty() && keep.add(prefix)) added.add(prefix);
            }
        }
    }

    /** 从执行上下文拿最近一条用户消息的正文，拿不到就返回 null。 */
    private static String abbreviate(String text) {
        if (text == null) return "null";
        String flat = text.replace("\n", " ").trim();
        return flat.length() > 40 ? flat.substring(0, 40) + "…" : flat;
    }
    /** 一次性打印执行上下文的字段结构，找出消息列表存在哪个字段。 */
    private void dumpContext(Object context) {
        try {
            StringBuilder out = new StringBuilder("sj.e 字段：");
            for (java.lang.reflect.Field field : context.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(context);
                } catch (Throwable ignored) {
                    continue;
                }
                out.append('\n').append("  ").append(field.getName()).append(':')
                        .append(field.getType().getSimpleName());
                if (value instanceof java.util.List<?> list) {
                    out.append(" size=").append(list.size());
                    if (!list.isEmpty()) {
                        Object first = list.get(0);
                        out.append(" first=")
                                .append(first == null ? "null" : first.getClass().getSimpleName());
                        if (first != null && first.getClass().getName().contains("Message")) {
                            try {
                                Object role = first.getClass().getMethod("getRole").invoke(first);
                                Object content = first.getClass().getMethod("getContent").invoke(first);
                                out.append(" role=").append(role)
                                        .append(" content=")
                                        .append(content == null
                                                ? "null"
                                                : content.getClass().getSimpleName() + ":"
                                                        + abbreviate(String.valueOf(content)));
                            } catch (Throwable ignored) {
                                // 结构不一致就跳过
                            }
                        }
                    }
                } else if (value instanceof String s) {
                    out.append(" = ").append(abbreviate(s));
                }
            }
            log(Log.INFO, TAG, out.toString());
        } catch (Throwable failure) {
            log(Log.WARN, TAG, "dumpContext 失败：" + failure);
        }
    }
    /** 从路由决策对象里取用户原话（getTools 阶段消息列表还是空的）。 */
    private static String routerText(Object context) {
        try {
            java.lang.reflect.Field field = context.getClass().getDeclaredField("l");
            field.setAccessible(true);
            Object decision = field.get(context);
            if (decision == null) return null;
            Object text = decision.getClass().getMethod("getText").invoke(decision);
            return text instanceof String s && !s.isBlank() ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
    private static String lastUserText(Object context) {
        try {
            // 不依赖字段名：找一个「元素是 Message 的 List」字段即可
            for (java.lang.reflect.Field field : context.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(context);
                } catch (Throwable ignored) {
                    continue;
                }
                if (!(value instanceof java.util.List<?> list) || list.isEmpty()) continue;
                Object probe = list.get(list.size() - 1);
                if (probe == null || !probe.getClass().getName().contains("Message")) continue;
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object message = list.get(i);
                    if (message == null) continue;
                    Object role = message.getClass().getMethod("getRole").invoke(message);
                    if (role == null
                            || !role.toString().toUpperCase(java.util.Locale.ROOT).contains("USER")) {
                        continue;
                    }
                    Object content = message.getClass().getMethod("getContent").invoke(message);
                    if (content instanceof String s && !s.isBlank()) return s;
                }
            }
        } catch (Throwable ignored) {
            // 结构变了就安静跳过，筛选照常
        }
        return null;
    }

    private static java.util.Set<String> splitToSet(String raw) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        if (raw == null) return set;
        for (String part : raw.split(",")) {
            String item = part.trim().toLowerCase(java.util.Locale.ROOT);
            if (!item.isEmpty()) set.add(item);
        }
        return set;
    }

    private static String prefixOf(String name) {
        if (name == null) return "";
        int x = name.indexOf("__");
        if (x > 0) return name.substring(0, x).toLowerCase(java.util.Locale.ROOT);
        int u = name.indexOf('_');
        if (u > 0) return name.substring(0, u).toLowerCase(java.util.Locale.ROOT);
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private static String toolName(Object toolDefinition) {
        try {
            Object name = toolDefinition.getClass().getMethod("getName").invoke(toolDefinition);
            return name instanceof String s ? s : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int toolChars(Object toolDefinition) {
        try {
            Object desc = toolDefinition.getClass().getMethod("getDescription").invoke(toolDefinition);
            Object params = toolDefinition.getClass().getMethod("getParameters").invoke(toolDefinition);
            int chars = desc instanceof String s ? s.length() : 0;
            if (params != null) chars += params.toString().length();
            return chars;
        } catch (Throwable ignored) {
            return 0;
        }
    }


    /**
     * 工具描述瘦身：只改 description，参数 schema 一个字节都不动。
     *
     * 保留：含格式字面量（格式/必须/必填/枚举/经度/纬度/ISO）和适用边界的行
     *       （不适用/仅用于/不要用于）——这些一旦删掉模型就会瞎猜参数、用错工具。
     * 丢弃：示例、⚠️ 提醒、重复语气、长场景描写。
     */
    private void hookToolDescription(ClassLoader loader) throws Exception {
        Class<?> toolDefinition = Class.forName("com.aios.osbot.llm.model.ToolDefinition", false, loader);
        Method getDescription = toolDefinition.getDeclaredMethod("getDescription");
        Method getName = toolDefinition.getDeclaredMethod("getName");
        Method getParameters = null;
        try {
            getParameters = toolDefinition.getDeclaredMethod("getParameters");
        } catch (Throwable ignored) {
            // 拿不到参数也不影响瘦身
        }
        final Method parameters = getParameters;
        hook(getDescription).intercept(chain -> {
            Object result = chain.proceed();
            if (!(result instanceof String original) || original.isEmpty()) return result;
            Bundle config = config();
            if (config == null || !config.getBoolean(KEY_ENABLED, false)) return result;

            String name;
            try {
                name = String.valueOf(getName.invoke(chain.getThisObject()));
            } catch (Throwable ignored) {
                name = "";
            }

            String override = toolOverrides(config).get(name);
            if (override == null) override = builtinOverrides(config).get(name);
            String finalText = result instanceof String s ? s : original;
            if (override != null) {
                finalText = override;
            } else if (config.getBoolean(KEY_TOOL_DEEP, false)) {
                // 实验：按块打分 + 硬上限，砍示例和决策表，保格式与边界
                finalText = compressDeep(original);
            } else if (config.getBoolean(KEY_TOOL_TRIM, true)) {
                String compressed = compressToolDescription(original);
                if (!compressed.equals(original) && !toolTrimLogged) {
                    toolTrimLogged = true;
                    log(Log.INFO, TAG, "工具描述瘦身：例 " + name + " " + original.length()
                            + " -> " + compressed.length() + " 字");
                }
                finalText = compressed;
            }

            // 统计：工具数、描述字数（前/后）、参数 schema 字数
            toolCount.incrementAndGet();
            toolCharsBefore.addAndGet(original.length());
            toolCharsAfter.addAndGet(finalText.length());
            int paramsChars = 0;
            if (parameters != null) {
                try {
                    Object params = parameters.invoke(chain.getThisObject());
                    if (params != null) {
                        paramsChars = params.toString().length();
                        toolParamChars.addAndGet(paramsChars);
                    }
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
            // 逐条打印（只打前 12 条，避免刷屏）：工具名 描述 before -> after 参数 schema
            int index = toolCount.get();
            if (index <= 12) {
                log(
                        Log.INFO,
                        TAG,
                        "工具 " + index + " " + name + "：描述 " + original.length() + " -> "
                                + finalText.length() + " 字，参数 schema " + paramsChars + " 字");
            }
            scheduleStats();
            return finalText;
        });
        log(Log.INFO, TAG, "hooking ToolDefinition.getDescription (工具描述瘦身)");

        // 参数表：经实测放弃压缩。
        // 探针显示 11.2 万字里 "description" 字段极少（有的工具一个都没有），
        // 主体是字段名 / enum 列表 / type / required / 嵌套 properties —— 删长句省不了几个字，
        // 而重建 JsonObject 又依赖小爱进程里的 kotlinx（其 ClassLoader 不可达），
        // 投入与收益都不划算。这里只保留一次形态观测，便于以后换思路。
        if (parameters != null) {
            hook(parameters).intercept(chain -> {
                Object result = chain.proceed();
                int calls = paramProbeCalls.incrementAndGet();
                if (calls <= 3 && result != null) {
                    String text = result.toString();
                    log(
                            Log.INFO,
                            TAG,
                            "参数探针 " + calls + "：len=" + text.length() + " desc字段="
                                    + countOf(text, "\"description\"") + " 最长描述="
                                    + maxDescriptionLength(text));
                }
                return result;
            });
            log(Log.INFO, TAG, "hooking ToolDefinition.getParameters (仅观测，不修改)");

        // 装完 hook 立刻取一次配置：一是确认配置通道可用，二是给 App 一个「已注入」的心跳
        try {
            config();
        } catch (Throwable ignored) {
            // 心跳失败不影响 hook
        }
        }
    }

    /**
     * 把 JSON 里超长的 "description" 值截断。
     * 只动引号内的描述文本，键名、类型、enum、required 都不会被碰到。
     */
    private static String trimParameterDescriptions(String json, int limit) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        StringBuilder out = new StringBuilder();
        int last = 0;
        boolean changed = false;
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value.length() <= limit) continue;
            String cut = value.substring(0, limit);
            // 别切在转义序列中间：末尾若是奇数个反斜杠，或者落在 unicode 转义里，就往回退
            while (!cut.isEmpty() && endsWithOddBackslashes(cut)) {
                cut = cut.substring(0, cut.length() - 1);
            }
            int unicode = cut.lastIndexOf("\\u");
            if (unicode >= 0 && cut.length() - unicode < 6) {
                cut = cut.substring(0, unicode);
            }
            out.append(json, last, matcher.start(1));
            out.append(cut).append("…");
            last = matcher.end(1);
            changed = true;
        }
        if (!changed) return json;
        out.append(json.substring(last));
        return out.toString();
    }

    private static boolean endsWithOddBackslashes(String text) {
        int count = 0;
        for (int i = text.length() - 1; i >= 0 && text.charAt(i) == '\\'; i--) count++;
        return count % 2 == 1;
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }

    /** 所有 "description" 值里最长的那个长度，用来判断到底有没有可截的对象。 */
    private static int maxDescriptionLength(String json) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        int max = 0;
        while (matcher.find()) {
            max = Math.max(max, matcher.group(1).length());
        }
        return max;
    }

    /** 探针用：取前 200 字并压平换行，方便在 logcat 里看结构。 */
    private static String sample(String text) {
        String head = text.length() > 200 ? text.substring(0, 200) : text;
        return head.replace("\n", " ").replace("\r", " ");
    }

    /** 用 kotlinx.serialization 的 Json 把字符串重新解析成 JsonObject。 */
    private Object buildJsonElement(String text) {
        try {
            if (jsonInstance == null) {
                // 必须用被 hook 进程的 ClassLoader：模块自己的加载器看不到小爱的依赖，
                // 之前漏了 loader 参数，报 ClassNotFoundException 后一直走兜底、参数表没改过。
                Class<?> jsonClass = Class.forName(
                        "kotlinx.serialization.json.Json", false, appLoader);
                Object companion = jsonClass.getField("Companion").get(null);
                Object defaultJson = companion.getClass().getMethod("getDefault").invoke(companion);
                jsonParse = defaultJson.getClass().getMethod("parseToJsonElement", String.class);
                jsonInstance = defaultJson;
                log(Log.INFO, TAG, "JSON 重建已就绪：" + jsonClass.getName());
            }
            return jsonParse.invoke(jsonInstance, text);
        } catch (Throwable failure) {
            if (!jsonRebuildWarned) {
                jsonRebuildWarned = true;
                log(Log.WARN, TAG, "参数表重建失败，保留原样：" + failure);
            }
            return null;
        }
    }

    /** 等工具列表出齐后打一条汇总，避免每次都刷日志。 */
    private void scheduleStats() {
        if (statsScheduled) return;
        synchronized (this) {
            if (statsScheduled) return;
            statsScheduled = true;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {
                return;
            }
            int partTotal = 0;
            StringBuilder parts = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : promptParts.entrySet()) {
                partTotal += entry.getValue();
                parts.append('\n').append("  ").append(entry.getKey()).append('=')
                        .append(entry.getValue());
            }
            int systemChars = systemPromptChars > 0 ? systemPromptChars : partTotal;
            log(
                    Log.INFO,
                    TAG,
                    "提示词统计：系统提示 " + systemChars + " 字（片段合计 " + partTotal + "）；工具 "
                            + toolCount.get() + " 个，描述 " + toolCharsBefore.get() + " -> "
                            + toolCharsAfter.get() + " 字，参数 schema " + toolParamChars.get()
                            + " 字；合计约 "
                            + (systemChars + toolCharsAfter.get() + toolParamChars.get()) + " 字"
                            + parts);
        }, "misoul-stats");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean isNoiseLine(String line) {
        String l = line.toLowerCase(java.util.Locale.ROOT);
        return l.contains("示例")
                || l.contains("例如")
                || l.contains("比如")
                || l.contains("example")
                || l.contains("⚠")
                || l.contains("注意")
                || l.contains("温馨提示")
                || l.contains("建议")
                || l.startsWith("//");
    }

    private static boolean hasLiteralRequirement(String line) {
        return line.contains("格式")
                || line.contains("必须")
                || line.contains("必填")
                || line.contains("可选")
                || line.contains("枚举")
                || line.contains("取值")
                || line.contains("经度")
                || line.contains("纬度")
                || line.contains("iso")
                || line.contains("ISO")
                || line.contains("不适用")
                || line.contains("仅用于")
                || line.contains("不要用于")
                || line.contains("只用于")
                || line.contains("适用于");
    }

    private static String compressToolDescription(String text) {
        StringBuilder builder = new StringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            boolean keep = !isNoiseLine(line) || hasLiteralRequirement(line);
            if (!keep) continue;
            builder.append(line).append('\n');
        }
        String out = builder.toString().trim();
        return out.isEmpty() ? text : out;
    }

    /**
     * 深度压缩（实验）：按空行/标题切块，给每块打分，超预算时丢低分块。
     *
     * 加分：含参数格式或适用边界的块（这两类是"字面要求"，删了模型会瞎猜/用错工具）
     * 减分：示例、⚠️ 提醒、超过 800 字的大段
     * 首块（概述）无条件保留；高价值块可截断进预算，低价值块直接丢。
     */
    private static String compressDeep(String text) {
        java.util.List<String> blocks = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
            } else if (trimmed.startsWith("#")) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
                current.append(line).append('\n');
            } else {
                current.append(line).append('\n');
            }
        }
        if (current.length() > 0) blocks.add(current.toString());

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            String block = blocks.get(i);
            boolean first = i == 0;
            int score = scoreBlock(block);
            if (!first && score < 0) continue;

            int remaining = DEEP_BUDGET - out.length();
            if (remaining <= 0) break;
            if (block.length() <= remaining) {
                out.append(block).append('\n');
            } else if (first || score >= 100) {
                // 首块和"字面要求"块：截断保留，不整块丢
                out.append(block, 0, remaining).append('\n');
            }
        }
        String result = out.toString().trim();
        return result.isEmpty() ? text : result;
    }

    private static int scoreBlock(String block) {
        int score = 0;
        String lower = block.toLowerCase(java.util.Locale.ROOT);
        if (containsAny(block, "格式", "必须", "必填", "可选", "枚举", "取值", "经度", "纬度")
                || lower.contains("iso")) {
            score += 100;
        }
        if (containsAny(block, "不适用", "仅用于", "不要用于", "只用于", "禁止", "别用", "不支持", "不负责")) {
            score += 100;
        }
        if (containsAny(block, "示例", "例如", "比如", "example", "⚠")) score -= 100;
        if (block.length() > 800) score -= 30;
        if (block.length() <= 200) score += 20;
        return score;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    /** 解析随包内置的精简描述表（`### 工具名` 起块，块内为描述正文）。 */
    private java.util.Map<String, String> builtinOverrides(Bundle config) {
        String raw = config.getString(KEY_TOOL_DEFAULTS, "");
        if (raw == null) raw = "";
        if (raw.equals(cachedBuiltinRaw) && cachedBuiltin != null) return cachedBuiltin;
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        String current = null;
        StringBuilder body = new StringBuilder();
        for (String line : raw.split("\n")) {
            if (line.startsWith("### ")) {
                if (current != null) map.put(current, body.toString().trim());
                current = line.substring(4).trim();
                body.setLength(0);
            } else if (current != null) {
                body.append(line).append('\n');
            }
        }
        if (current != null) map.put(current, body.toString().trim());
        map.values().removeIf(String::isEmpty);
        cachedBuiltin = map;
        cachedBuiltinRaw = raw;
        return map;
    }
    private java.util.Map<String, String> toolOverrides(Bundle config) {
        String raw = config.getString(KEY_TOOL_OVERRIDES, "");
        if (raw == null) raw = "";
        if (raw.equals(cachedOverridesRaw) && cachedOverrides != null) return cachedOverrides;
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (String line : raw.split("\n")) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) continue;
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            map.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        cachedOverrides = map;
        cachedOverridesRaw = raw;
        return map;
    }

    private void hookAgentName(ClassLoader loader) throws Exception {
        Class<?> definition = Class.forName("com.aios.osbot.agent.unified.AgentDefinition", false, loader);
        Method getId = definition.getDeclaredMethod("getId");
        Method getName = definition.getDeclaredMethod("getName");
        hook(getName).intercept(chain -> {
            Object result = chain.proceed();
            if (!(result instanceof String original)) return result;
            Bundle config = config();
            if (config == null || !config.getBoolean(KEY_ENABLED, false)
                    || !config.getBoolean(KEY_PROFILE_ENABLED, true)) {
                return result;
            }
            Object id;
            try {
                id = getId.invoke(chain.getThisObject());
            } catch (Throwable ignored) {
                return result;
            }
            if (!AGENT.equals(id)) return result;
            String name = config.getString(KEY_PROFILE_NAME, DEFAULT_PROFILE_NAME);
            if (name == null || name.trim().isEmpty()) return result;
            if (!name.equals(lastLoggedName)) {
                lastLoggedName = name;
                log(Log.INFO, TAG, "agent name -> " + name + " (was " + original + ")");
            }
            return name;
        });
        log(Log.INFO, TAG, "hooking AgentDefinition.getName");
    }

    // ---- 人格文本：soul.md / prompt.md ----

    private void hookPromptFiles(ClassLoader loader) throws Exception {
        Class<?> builder = Class.forName("rs.a", false, loader);
        Class<?> companion = null;
        Method readAsset = null;
        Method readFile = null;

        for (Class<?> inner : builder.getDeclaredClasses()) {
            Method asset = null;
            Method file = null;
            for (Method m : inner.getDeclaredMethods()) {
                if ("readAsset".equals(m.getName()) && m.getParameterCount() == 3
                        && m.getParameterTypes()[1] == String.class) {
                    asset = m;
                } else if ("readFile".equals(m.getName()) && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == File.class) {
                    file = m;
                }
            }
            if (asset != null && file != null) {
                companion = inner;
                readAsset = asset;
                readFile = file;
                break;
            }
        }
        if (companion == null) {
            throw new IllegalStateException("PromptBuilder companion not found in rs.a");
        }

        log(Log.INFO, TAG, "hooking " + companion.getName());
        hook(readAsset).intercept(this::onReadAsset);
        hook(readFile).intercept(this::onReadFile);

        // 统计系统提示各片段：rs.d.resolve(...) 每次返回一个文件的正文，
        // 实例字段 a 就是文件名（soul.md / prompt.md / tool_selection_rules.md …）。
        try {
            Class<?> resolver = Class.forName("rs.d", false, loader);
            Class<?> fsType = readAsset.getParameterTypes()[0];
            Method resolve = resolver.getDeclaredMethod(
                    "resolve", fsType, String.class, File.class, boolean.class);
            java.lang.reflect.Field nameField = resolver.getField("a");
            hook(resolve).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof String text) {
                    try {
                        Object name = nameField.get(chain.getThisObject());
                        if (name instanceof String part) {
                            promptParts.merge(part, text.length(), Math::max);
                        }
                    } catch (Throwable ignored) {
                        // 统计失败不影响返回
                    }
                }
                return result;
            });
            log(Log.INFO, TAG, "hooking rs.d.resolve (系统提示片段统计)");
        } catch (Throwable failure) {
            log(Log.WARN, TAG, "系统提示统计没挂上：" + failure);
        }
    }

    private Object onReadAsset(XposedInterface.Chain chain) throws Throwable {
        Object pathArg = chain.getArg(1);
        if (!(pathArg instanceof String path) || !path.startsWith(AGENT_PREFIX)) {
            return chain.proceed();
        }
        Object prefixArg = chain.getArg(2);
        String prefix = prefixArg instanceof String s ? s : null;

        if (path.equals(AGENT_PREFIX + SOUL)) {
            String soul = soul();
            if (soul != null) {
                log(Log.INFO, TAG, "soul.md from assets -> " + soul.length() + " chars override");
                return withPrefix(prefix, soul);
            }
        } else if (path.equals(AGENT_PREFIX + RULES)) {
            String custom = customRules();
            if (custom != null) {
                log(Log.INFO, TAG, "tool_selection_rules.md -> " + custom.length() + " chars override");
                return withPrefix(prefix, custom);
            }
        } else if (path.equals(AGENT_PREFIX + PROMPT)) {
            Object result = chain.proceed();
            if (!(result instanceof String text)) return result;
            if (!fixPrompt()) return result;
            String fixed = fixIdentity(text);
            log(Log.INFO, TAG, "prompt.md from assets -> " + (fixed == null ? "unchanged" : "identity patched"));
            return fixed == null ? result : withPrefix(prefix, fixed);
        }
        return chain.proceed();
    }

    private Object onReadFile(XposedInterface.Chain chain) throws Throwable {
        Object fileArg = chain.getArg(0);
        if (!(fileArg instanceof File file)) return chain.proceed();
        String name = file.getName();
        if (!SOUL.equals(name) && !PROMPT.equals(name) && !RULES.equals(name)) return chain.proceed();
        if (!file.getPath().contains("/" + AGENT + "/")) return chain.proceed();

        Object prefixArg = chain.getArg(1);
        String prefix = prefixArg instanceof String s ? s : null;

        if (RULES.equals(name)) {
            String custom = customRules();
            if (custom != null) {
                log(Log.INFO, TAG, "tool_selection_rules.md (file) -> " + custom.length() + " chars override");
                return withPrefix(prefix, custom);
            }
        } else if (SOUL.equals(name)) {
            String soul = soul();
            if (soul != null) {
                log(Log.INFO, TAG, "soul.md from file -> " + soul.length() + " chars override");
                return withPrefix(prefix, soul);
            }
        } else if (fixPrompt()) {
            Object result = chain.proceed();
            if (result instanceof String text) {
                String fixed = fixIdentity(text);
                if (fixed != null) return withPrefix(prefix, fixed);
            }
            return result;
        }
        return chain.proceed();
    }

    private static String withPrefix(String prefix, String text) {
        return prefix == null ? text : prefix + "\n" + text;
    }

    // ---- 界面形象：profile.json -> AgentProfile ----

    private void hookProfile(ClassLoader loader) throws Exception {
        Class<?> store = Class.forName("ck.o0", false, loader);
        Method fromAsset = store.getDeclaredMethod("l", String.class);
        Method fromFile = store.getDeclaredMethod("m", File.class);

        hook(fromAsset).intercept(chain -> {
            Object result = chain.proceed();
            if (result == null || !AGENT.equals(chain.getArg(0))) return result;
            return patchProfile(result);
        });
        hook(fromFile).intercept(chain -> {
            Object result = chain.proceed();
            Object arg = chain.getArg(0);
            if (result == null || !(arg instanceof File file)
                    || !file.getPath().contains("/" + AGENT + "/")) {
                return result;
            }
            return patchProfile(result);
        });
        log(Log.INFO, TAG, "hooking ck.o0 profile loader");
    }

    private Object patchProfile(Object profile) {
        Bundle config = config();
        if (config == null || !config.getBoolean(KEY_ENABLED, false)
                || !config.getBoolean(KEY_PROFILE_ENABLED, true)) {
            return profile;
        }
        try {
            Class<?> type = profile.getClass();
            Constructor<?> ctor = profileConstructor;
            if (ctor == null) {
                ctor = type.getDeclaredConstructor(String.class, String.class, String.class,
                        String.class, List.class, String.class, String.class, List.class);
                ctor.setAccessible(true);
                profileConstructor = ctor;
            }
            String displayName = config.getString(KEY_PROFILE_NAME, DEFAULT_PROFILE_NAME);
            String signature = config.getString(KEY_PROFILE_SIGNATURE, DEFAULT_PROFILE_SIGNATURE);
            String welcome = config.getString(KEY_PROFILE_WELCOME, DEFAULT_PROFILE_WELCOME);

            Object patched = ctor.newInstance(
                    isBlank(displayName) ? get(profile, "getDisplayName") : displayName,
                    get(profile, "getAvatar"),
                    isBlank(signature) ? get(profile, "getSignature") : signature,
                    get(profile, "getPersonality"),
                    get(profile, "getExpertise"),
                    isBlank(welcome) ? get(profile, "getWelcomeHi") : welcome,
                    get(profile, "getWelcomeHiDescription"),
                    get(profile, "getWelcomeOptions"));
            log(Log.INFO, TAG, "profile -> display=" + (isBlank(displayName) ? "(keep)" : displayName));
            return patched;
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "profile patch failed: " + failure);
            return profile;
        }
    }

    private static Object get(Object target, String getter) {
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ---- 超级小爱会员解锁（定时任务 + 视频生成，共用同一判断） ----
    //
    // 问题（超级小爱 8.3.30.4420 / 508003030，实测包内静态分析）：
    // 非会员让小爱建「定时 AI 任务」时，模型并不是弹一个固定提示，而是说出
    // 「需要会员才能执行自动化」——包里根本没有这句原话，它是模型根据工具
    // 返回的拒绝理由现场组织出来的。真正的关卡在本地工具层：
    //   com.aios.osbot.membership.MembershipRepository.checkTimerAccess()
    //     -> 枚举 MembershipRepository$b：ALLOWED / BLOCKED_FREE / BLOCKED_UNVERIFIED
    //     （等级来自账号/服务端刷新，本地只缓存 membership_state）
    //   com.aios.osbot.tools.system.TimerTool.membershipBlockOrNull()
    //   com.aios.osbot.tools.system.TimerTool.enabledUpdateMembershipBlockOrNull(boolean)
    //     -> 非空即挡住创建/修改；BLOCKED_FREE 时模型按内置文案收尾：
    //        「请到「我的」→「用量与订阅」中，升级会员。」
    //
    // 本 hook 只在用户主动打开开关时生效，且与人格替换开关相互独立：
    //   - checkTimerAccess -> ALLOWED（覆盖创建与执行两侧共用的同一判断）
    //   - membershipBlockOrNull / enabledUpdateMembershipBlockOrNull -> null（=不挡）
    // 这两个都是 Kotlin suspend 方法，Java 签名是 (Continuation) -> Object；
    // 未挂起时直接返回结果对象即可，返回 null / 枚举常量就是正常返回值，
    // 不是绕过协程。其它功能（人格、工具筛选、描述瘦身）一行未动。
    //
    // 诚实边界（必须写进 README，不能打包票）：
    //   - 创建时放行，不等于到点一定执行：TimerAlarmReceiver /
    //     TaskForegroundService 手里也有会员仓库，日志里有
    //     「One-time timer, pausing until membership can be verified」和
    //     timer_skipped_membership_required / unverified，建好的任务到点
    //     仍可能被跳过，只能真机实测。
    //   - 场景自动化没有在包里找到独立的会员关：如果场景是走定时任务去
    //     执行，本 hook 就覆盖；如果走米家云端建规则，本地 hook 帮不上。
    //   - 视频生成不是独立的会员判断，它共用上面同一个 checkTimerAccess：
    //     付费功能门 PaidFeatureGate（Ldl/z3$a$b）.verdictOf(VIDEO_BUILDER)
    //     先调 checkTimerAccess，ALLOWED 就返回 null（=放行），否则返回
    //     facade/n1 拒绝（免费版文案代号 50201，未验证为
    //     MEMBERSHIP_UNVERIFIED）；聊天界面的视频模式关口（Lhu/o5$d0）
    //     同样先调 checkTimerAccess，ALLOWED 才设置 activeTaskMode，
    //     否则弹 video_builder_membership_required / _unverified 提示。
    //     所以本开关打开后，定时任务和视频生成的客户端关口同时放行，
    //     无法只开其中一个；视频是云端沙箱渲染，服务端仍可能在真正
    //     生成时再拦一次，客户端放行不等于一定出片，只能真机实测。

    private volatile boolean vipLogged;

    private void hookVipUnlock(ClassLoader loader) throws Exception {
        Class<?> repository = Class.forName(
                "com.aios.osbot.membership.MembershipRepository", false, loader);
        for (Method method : repository.getDeclaredMethods()) {
            if (!"checkTimerAccess".equals(method.getName())
                    || method.getParameterCount() != 1) {
                continue;
            }
            hook(method).intercept(chain -> {
                if (!vipUnlock()) return chain.proceed();
                Object allowed = allowedAccess(loader);
                if (allowed == null) return chain.proceed();
                if (!vipLogged) {
                    vipLogged = true;
                    log(Log.INFO, TAG, "会员解锁：checkTimerAccess -> ALLOWED");
                }
                return allowed;
            });
            log(Log.INFO, TAG, "hooking MembershipRepository.checkTimerAccess (会员解锁)");
            break;
        }

        Class<?> timerTool = Class.forName(
                "com.aios.osbot.tools.system.TimerTool", false, loader);
        for (Method method : timerTool.getDeclaredMethods()) {
            String name = method.getName();
            if (!"membershipBlockOrNull".equals(name)
                    && !"enabledUpdateMembershipBlockOrNull".equals(name)) {
                continue;
            }
            hook(method).intercept(chain -> {
                if (!vipUnlock()) return chain.proceed();
                return null;
            });
            log(Log.INFO, TAG, "hooking TimerTool." + name + " (会员解锁)");
        }
    }

    /** 枚举 MembershipRepository$b.ALLOWED；拿不到就返回 null（走原逻辑，不硬编）。 */
    private static Object allowedAccess(ClassLoader loader) {
        try {
            Class<?> access = Class.forName(
                    "com.aios.osbot.membership.MembershipRepository$b", false, loader);
            java.lang.reflect.Field field = access.getField("ALLOWED");
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 会员解锁开关：独立于人格替换开关，默认关闭。 */
    private boolean vipUnlock() {
        Bundle config = config();
        return config != null && config.getBoolean(KEY_VIP_UNLOCK, false);
    }

    // ---- 自动化流程：先规划后执行，成功流程优先复用 ----
    //
    // 背景：模型调界面自动化时只传一句话给
    //   com.xiaomi.voiceassistant.UIAgentServiceForOSBot.controlApp(String)，
    // 聊天和定时任务到点执行走的都是这一个方法。笼统的一句话（比如“打开支付宝签到”）
    // 云端经常在首页找不到入口；把精确流程写进这句话（比如“先点我的，再点签到”）
    // 就能走通。本 hook 不内置任何应用、任何任务的流程，只做通用机制：
    //   1. 先规划（第一次，没有成功记录时）：给笼统指令加一段通用规划要求，
    //      让云端先按目标应用的实际界面想清楚每一步要点的按钮文字，再依次执行，
    //      找不到按钮就停下不要猜测、不要乱点。
    //   2. 再复用（之后，有成功记录时优先）：分步骤指令成功执行后，把“应用|末步目标|原文”
    //      记下来；下次同样应用、同样末步目标的笼统指令直接改写成这条成功流程，
    //      不再重新规划。记录只认“至少两步点击且返回非错误”的执行，单步“只打开”
    //      不算成功，不记录。
    //   安全红线：含支付、转账、付款、下单、购买、充值、提现字样的指令一律原样放行，
    //   绝不补全、不改写。流程只是给云端的文字，不是本地点屏幕的代码；
    //   应用改版改了按钮文字，旧记录就可能失效，失效时关掉开关或在设置页清除记录。
    //   记录存在模块 App 的 persona prefs 里，经 PersonaProvider 读写，见下文。

    private static final String[] FLOW_SENSITIVE = {
            "支付", "转账", "付款", "下单", "购买", "充值", "提现"};

    private volatile boolean flowLogged;

    private void hookFlowControl(ClassLoader loader) throws Exception {
        Class<?> service = Class.forName(
                "com.xiaomi.voiceassistant.UIAgentServiceForOSBot", false, loader);
        for (Method method : service.getDeclaredMethods()) {
            if (!"controlApp".equals(method.getName())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != String.class) {
                continue;
            }
            hook(method).intercept(chain -> {
                if (!flowPlan()) return chain.proceed();
                Object arg = chain.getArg(0);
                if (!(arg instanceof String)) return chain.proceed();
                String query = (String) arg;
                if (query == null || query.trim().isEmpty()) return chain.proceed();
                if (isFlowSensitive(query)) {
                    log(Log.INFO, TAG, "流程注入：敏感指令原样放行，不补全");
                    return chain.proceed();
                }
                // 优先复用：已有成功记录，且新指令还是笼统的，就直接用记录的流程。
                String recorded = findRecordedFlow(query);
                if (recorded != null && countClicks(query) < 2) {
                    if (!flowLogged) {
                        flowLogged = true;
                        log(Log.INFO, TAG, "流程注入：复用成功流程 -> " + recorded);
                    } else {
                        log(Log.INFO, TAG, "流程注入：复用成功流程");
                    }
                    return chain.proceed(new Object[]{recorded});
                }
                // 已是分步骤指令：原样执行，成功后才记录（见下方）。
                boolean stepwise = countClicks(query) >= 2;
                String toRun = stepwise ? query
                        : "先规划后执行：先根据目标应用的实际界面想清楚完成这个任务要依次"
                        + "点击哪些按钮，写出每个按钮的准确文字，再依次执行；找不到对应按钮"
                        + "就停下，不要猜测、不要乱点。任务：" + query
                        + "。完成后汇报每一步的结果。";
                if (!stepwise) log(Log.INFO, TAG, "流程注入：先规划后执行");
                Object result = chain.proceed(new Object[]{toRun});
                // 只记录分步骤且非错误的执行；规划前缀的笼统指令不知道云端实际走了哪几步，不记录。
                if (stepwise && !isFlowError(result)) recordFlow(query);
                return result;
            });
            log(Log.INFO, TAG, "hooking UIAgentServiceForOSBot.controlApp (流程注入)");
            break;
        }
    }

    private boolean flowPlan() {
        Bundle config = config();
        return config != null && config.getBoolean(KEY_FLOW_PLAN, false);
    }

    /** 判断 controlApp 的返回是不是错误：null 或拿不到 isError 都算错误，不记录。 */
    private static boolean isFlowError(Object result) {
        if (result == null) return true;
        try {
            Object error = result.getClass().getMethod("isError").invoke(result);
            return error instanceof Boolean && (Boolean) error;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isFlowSensitive(String query) {
        for (String word : FLOW_SENSITIVE) {
            if (query.contains(word)) return true;
        }
        return false;
    }

    private static int countClicks(String query) {
        int count = 0;
        int index = 0;
        while ((index = query.indexOf("点击", index)) >= 0) {
            count++;
            index += 2;
        }
        return count;
    }

    /** 从“打开XX”里取应用名（2~6 字），取不到返回 null。 */
    private static String flowApp(String query) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("打开([^，,。；;\\s]{2,6})").matcher(query);
        return m.find() ? m.group(1) : null;
    }

    /** 取最后一个“点击XX”的目标（2~6 字），取不到返回 null。 */
    private static String flowTarget(String query) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("点击([^，,。；;\\s]{2,6})").matcher(query);
        String target = null;
        while (m.find()) target = m.group(1);
        return target;
    }

    /** 在已记录流程里找“应用+末步目标”都出现在新指令里的那一条；没有返回 null。 */
    private String findRecordedFlow(String query) {
        Bundle config = config();
        if (config == null) return null;
        String store = config.getString(KEY_FLOW_STORE, "");
        if (store == null || store.isEmpty()) return null;
        for (String line : store.split("\n")) {
            String entry = line.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split("\\|", 3);
            if (parts.length < 3) continue;
            if (query.contains(parts[0]) && query.contains(parts[1])) {
                try {
                    return new String(android.util.Base64.decode(parts[2],
                            android.util.Base64.NO_WRAP), "UTF-8");
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 记录一条分步骤成功流程；同应用+目标覆盖旧的，总数上限 50 条。 */
    private void recordFlow(String query) {
        String app = flowApp(query);
        String target = flowTarget(query);
        if (app == null || target == null) return;
        try {
            String encoded = android.util.Base64.encodeToString(
                    query.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
            Bundle extras = new Bundle();
            extras.putString("entry", app + "|" + target + "|" + encoded);
            Context ctx = context();
            if (ctx == null) return;
            ctx.getContentResolver().call(
                    Uri.parse("content://" + PersonaProvider.AUTHORITY),
                    PersonaProvider.METHOD_FLOW_PUT, null, extras);
            log(Log.INFO, TAG, "流程注入：记录成功流程 " + app + " -> " + target);
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "flow record failed: " + failure);
        }
    }

    // ---- 配置读取 ----

    private Context context() {
        Context cached = appContext;
        if (cached != null) return cached;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            if (application instanceof Context ctx) {
                appContext = ctx;
                return ctx;
            }
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "context unavailable: " + failure);
        }
        return null;
    }

    private Bundle config() {
        long now = SystemClock.elapsedRealtime();
        Bundle cached = cachedConfig;
        if (cached != null && now - cachedAt < 1500) return cached;

        Context ctx = context();
        if (ctx == null) return cached;
        try {
            Bundle fresh = ctx.getContentResolver().call(
                    Uri.parse("content://" + PersonaProvider.AUTHORITY), PersonaProvider.METHOD, null, null);
            if (fresh != null) {
                cachedConfig = fresh;
                cachedAt = now;
                return fresh;
            }
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "provider call failed: " + failure);
        }
        return cached;
    }

    private boolean enabled() {
        Bundle config = config();
        return config != null && config.getBoolean(KEY_ENABLED, false);
    }

    private boolean fixPrompt() {
        Bundle config = config();
        return config != null && config.getBoolean(KEY_ENABLED, false)
                && config.getBoolean(KEY_FIX_PROMPT, true);
    }

    /** 用户自定义的选工具规则；空则返回 null（继续用官方 24k 那份）。 */
    private String customRules() {
        Bundle config = config();
        if (config == null || !config.getBoolean(KEY_ENABLED, false)) return null;
        String raw = config.getString(KEY_RULES_TEXT, "");
        if (raw == null || raw.trim().isEmpty()) return null;
        return raw;
    }

    private String soul() {
        Bundle config = config();
        if (config == null || !config.getBoolean(KEY_ENABLED, false)) return null;
        String text = config.getString(KEY_SOUL, "");
        return text == null || text.trim().isEmpty() ? null : text;
    }

    private String displayName() {
        Bundle config = config();
        String name = config == null ? null : config.getString(KEY_NAME, null);
        return name == null || name.trim().isEmpty() ? DEFAULT_NAME : name.trim();
    }

    /** 把官方身份句换掉；若官方文案变了找不到该句，则末尾追加一段覆盖声明。 */
    private String fixIdentity(String original) {
        String name = displayName();
        if (original.contains(OFFICIAL_CLAIM)) {
            return original.replace(OFFICIAL_CLAIM, "并表明自己叫" + name);
        }
        if (original.contains(name)) return null;
        return original + "\n\n## 名字与自称（覆盖上文同名设定）\n\n- 你的名字是" + name
                + "。被误叫成「超级小爱」「小爱」时不要顺着承认，也不必争辩，一句话带过即可。\n";
    }
}
