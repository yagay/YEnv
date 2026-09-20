package com.yagay.yenv.ui;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.yenv.YEnvApp;
import com.yagay.yenv.data.AppConfig;
import com.yagay.yenv.data.ConfigRepository;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import io.github.libxposed.service.XposedService;

public final class AppDetailActivity extends AppCompatActivity implements YEnvApp.ServiceObserver {
    private String packageName;
    private AppConfig config;
    private LinearLayout root;
    private TextView summary;
    private TextView scopeState;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private Spinner nightMode, orientation, screenshots, keepScreen, locationMode;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("package");
        if (packageName == null) { finish(); return; }
        config = ConfigRepository.get(packageName);
        YEnvApp.addObserver(this);
        buildUi();
    }

    @Override protected void onDestroy() {
        YEnvApp.removeObserver(this);
        super.onDestroy();
    }

    private void buildUi() {
        fields.clear();
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        root.setPadding(p, p, p, dp(40));
        scroll.addView(root);
        setContentView(scroll);

        PackageManager pm = getPackageManager();
        String name = packageName;
        try { name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))); } catch (Throwable ignored) {}
        setTitle(name);

        TextView header = text(name, 24, true);
        root.addView(header);
        root.addView(text(packageName, 13, false));
        summary = text("", 13, false);
        scopeState = text("", 13, false);
        root.addView(summary);
        root.addView(scopeState);
        refreshStatus();

        LinearLayout actions = horizontal();
        Button scope = button("加入 LSPosed 作用域");
        scope.setOnClickListener(v -> requestScope());
        Button save = button("保存");
        save.setOnClickListener(v -> save());
        actions.addView(scope, weight());
        actions.addView(save, weight());
        root.addView(actions);

        section("快速模板");
        LinearLayout presets = horizontal();
        Button tablet = button("平板"); tablet.setOnClickListener(v -> applyPreset("tablet"));
        Button compact = button("紧凑"); compact.setOnClickListener(v -> applyPreset("compact"));
        Button uk = button("英国环境"); uk.setOnClickListener(v -> applyPreset("uk"));
        presets.addView(tablet, weight()); presets.addView(compact, weight()); presets.addView(uk, weight());
        root.addView(presets);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        Configuration cf = getResources().getConfiguration();

        section("显示与分辨率");
        addField("densityDpi", "DPI / densityDpi", String.valueOf(dm.densityDpi), val(config.densityDpi), "例如 420；留空=默认");
        addField("widthPixels", "虚拟宽度 px", String.valueOf(dm.widthPixels), val(config.widthPixels), "例如 1080");
        addField("heightPixels", "虚拟高度 px", String.valueOf(dm.heightPixels), val(config.heightPixels), "例如 2400");
        addField("smallestWidthDp", "最小宽度 dp", String.valueOf(cf.smallestScreenWidthDp), val(config.smallestWidthDp), "600 常用于平板布局");
        addField("screenWidthDp", "screenWidthDp", String.valueOf(cf.screenWidthDp), val(config.screenWidthDp), "可独立覆盖");
        addField("screenHeightDp", "screenHeightDp", String.valueOf(cf.screenHeightDp), val(config.screenHeightDp), "可独立覆盖");
        addField("fontScale", "字体缩放", String.valueOf(cf.fontScale), val(config.fontScale), "1.0=100%  0.9=90%");
        addField("xdpi", "xDpi", String.valueOf(dm.xdpi), val(config.xdpi), "留空=真实值");
        addField("ydpi", "yDpi", String.valueOf(dm.ydpi), val(config.ydpi), "留空=真实值");
        addField("refreshRate", "刷新率 Hz", defaultRefreshRate(), val(config.refreshRate), "例如 60 / 90 / 120；仅覆盖 App 读取值");

        section("语言、地区与时间");
        addField("localeTag", "Locale / 应用语言", Locale.getDefault().toLanguageTag(), val(config.localeTag), "BCP-47，例如 zh-CN / en-GB");
        addField("timeZoneId", "时区", TimeZone.getDefault().getID(), val(config.timeZoneId), "例如 Europe/London / Asia/Shanghai");
        nightMode = addSpinner("深色模式", "系统当前=" + nightText(cf), new String[]{"默认", "浅色", "深色"}, nightIndex(config.nightMode));

        section("窗口与方向");
        orientation = addSpinner("屏幕方向", defaultOrientation(), new String[]{"默认", "竖屏", "横屏", "全传感器", "锁定当前"}, orientationIndex(config.orientation));
        screenshots = addSpinner("截图 / FLAG_SECURE", "应用默认", new String[]{"默认", "强制允许截图", "强制禁止截图"}, boolIndex(config.allowScreenshots));
        keepScreen = addSpinner("保持屏幕常亮", "应用默认", new String[]{"默认", "强制常亮", "不强制常亮"}, boolIndex(config.keepScreenOn));
        addField("userAgent", "WebView User-Agent", "WebView 默认", val(config.userAgent), "只覆盖 WebSettings.getDefaultUserAgent");

        section("定位与模拟");
        locationMode = addSpinner("定位模式", "真实系统定位", new String[]{"默认/真实", "固定位置", "随机半径"}, locationIndex(config.locationMode));
        addField("latitude", "纬度", "真实系统定位", val(config.latitude), "-90..90");
        addField("longitude", "经度", "真实系统定位", val(config.longitude), "-180..180");
        addField("altitude", "海拔 m", "Location 原值", val(config.altitude), "可留空");
        addField("accuracy", "精度 m", "Location 原值", val(config.accuracy), "例如 5");
        addField("speed", "速度 m/s", "Location 原值", val(config.speed), "例如 1.4");
        addField("bearing", "方向 °", "Location 原值", val(config.bearing), "0..360");
        addField("randomRadiusMeters", "随机半径 m", "0", val(config.randomRadiusMeters), "随机模式使用");
        addField("locationUpdateIntervalMs", "随机更新间隔 ms", "5000", val(config.locationUpdateIntervalMs), "最小建议 1000");

        section("应用原始信息 / 诊断");
        root.addView(text(buildRawInfo(), 13, false));

        Button reset = button("恢复这个应用全部默认");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("恢复全部默认？")
                .setMessage("将删除 YEnv 对此应用的全部覆盖。应用之后直接读取真实系统/自身默认值。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (d, w) -> {
                    if (ConfigRepository.reset(packageName)) { config = new AppConfig(); buildUi(); toast("已恢复默认"); }
                    else toast("LSPosed 服务不可用，未写入");
                }).show());
        root.addView(reset);
    }

    private void addField(String key, String label, String defaultValue, String current, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(8));
        TextView labelView = text(label, 15, true);
        box.addView(labelView);
        TextView def = text("默认/真实：" + defaultValue, 12, false);
        box.addView(def);
        LinearLayout row = horizontal();
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setText(current == null ? "" : current);
        row.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button clear = button("恢复");
        clear.setOnClickListener(v -> edit.setText(""));
        row.addView(clear);
        box.addView(row);
        fields.put(key, edit);
        root.addView(box);
    }

    private Spinner addSpinner(String label, String defaultValue, String[] options, int selected) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0, dp(6), 0, dp(8));
        box.addView(text(label, 15, true)); box.addView(text("默认/真实：" + defaultValue, 12, false));
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        s.setSelection(Math.max(0, Math.min(selected, options.length - 1)));
        box.addView(s); root.addView(box); return s;
    }

    private void save() {
        try {
            AppConfig c = new AppConfig();
            c.densityDpi = intField("densityDpi");
            c.widthPixels = intField("widthPixels");
            c.heightPixels = intField("heightPixels");
            c.smallestWidthDp = intField("smallestWidthDp");
            c.screenWidthDp = intField("screenWidthDp");
            c.screenHeightDp = intField("screenHeightDp");
            c.fontScale = floatField("fontScale");
            c.xdpi = floatField("xdpi"); c.ydpi = floatField("ydpi"); c.refreshRate = floatField("refreshRate");
            c.localeTag = stringField("localeTag"); c.timeZoneId = stringField("timeZoneId");
            c.nightMode = switch (nightMode.getSelectedItemPosition()) { case 1 -> "light"; case 2 -> "dark"; default -> null; };
            c.orientation = switch (orientation.getSelectedItemPosition()) { case 1 -> "portrait"; case 2 -> "landscape"; case 3 -> "sensor"; case 4 -> "locked"; default -> null; };
            c.allowScreenshots = triBool(screenshots.getSelectedItemPosition());
            c.keepScreenOn = triBool(keepScreen.getSelectedItemPosition());
            c.userAgent = stringField("userAgent");
            c.locationMode = switch (locationMode.getSelectedItemPosition()) { case 1 -> "fixed"; case 2 -> "random"; default -> null; };
            c.latitude = doubleField("latitude"); c.longitude = doubleField("longitude"); c.altitude = doubleField("altitude");
            c.accuracy = floatField("accuracy"); c.speed = floatField("speed"); c.bearing = floatField("bearing"); c.randomRadiusMeters = floatField("randomRadiusMeters");
            c.locationUpdateIntervalMs = intField("locationUpdateIntervalMs");
            validate(c);
            saveBaselineIfNeeded();
            if (!ConfigRepository.save(packageName, c)) { toast("LSPosed 服务不可用，未写入"); return; }
            config = c; refreshStatus(); toast("已保存；已在作用域中的目标 App 重启进程后完整生效，运行时读取项可即时更新");
        } catch (IllegalArgumentException e) { toast(e.getMessage()); }
    }

    private void validate(AppConfig c) {
        if (c.densityDpi != null && (c.densityDpi < 72 || c.densityDpi > 1000)) throw new IllegalArgumentException("DPI 建议范围：72–1000");
        if (c.widthPixels != null && c.widthPixels < 100) throw new IllegalArgumentException("虚拟宽度过小");
        if (c.heightPixels != null && c.heightPixels < 100) throw new IllegalArgumentException("虚拟高度过小");
        if (c.latitude != null && (c.latitude < -90 || c.latitude > 90)) throw new IllegalArgumentException("纬度必须在 -90..90");
        if (c.longitude != null && (c.longitude < -180 || c.longitude > 180)) throw new IllegalArgumentException("经度必须在 -180..180");
        if (c.locationMode != null && (c.latitude == null || c.longitude == null)) throw new IllegalArgumentException("启用定位模拟需要填写经纬度");
        if (c.localeTag != null && Locale.forLanguageTag(c.localeTag).getLanguage().isBlank()) throw new IllegalArgumentException("Locale 格式无效，例如 en-GB");
    }

    private void requestScope() {
        if (YEnvApp.getService() == null) { toast("LSPosed 服务未连接"); return; }
        ConfigRepository.requestScope(packageName, new XposedService.OnScopeEventListener() {
            @Override public void onScopeRequestApproved(String pkg) { runOnUiThread(() -> { refreshStatus(); toast("已加入作用域"); }); }
            @Override public void onScopeRequestDenied(String pkg) { runOnUiThread(() -> toast("作用域请求被拒绝")); }
            @Override public void onScopeRequestFailed(String pkg, String message) { runOnUiThread(() -> toast("作用域请求失败：" + message)); }
        });
    }

    private void applyPreset(String preset) {
        if ("tablet".equals(preset)) {
            fields.get("densityDpi").setText("320"); fields.get("smallestWidthDp").setText("600"); fields.get("fontScale").setText("1.0");
        } else if ("compact".equals(preset)) {
            fields.get("densityDpi").setText("380"); fields.get("fontScale").setText("0.95");
        } else if ("uk".equals(preset)) {
            fields.get("localeTag").setText("en-GB"); fields.get("timeZoneId").setText("Europe/London");
        }
        toast("模板已填入；检查后点击保存");
    }

    private void refreshStatus() {
        if (summary != null) summary.setText("YEnv 覆盖项：" + config.overrideCount() + " · 未设置的字段全部返回真实值");
        if (scopeState != null) scopeState.setText("LSPosed：" + (YEnvApp.getService() == null ? "未连接" : (ConfigRepository.isInScope(packageName) ? "已在作用域" : "未在作用域")));
    }

    private void saveBaselineIfNeeded() {
        SharedPreferences p = YEnvApp.remotePrefs();
        if (p == null) return;
        String key = "baseline." + packageName;
        if (!p.contains(key)) p.edit().putString(key, buildBaseline()).commit();
    }

    private String buildBaseline() {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics(); Configuration cf = getResources().getConfiguration();
            JSONObject j = new JSONObject();
            j.put("capturedAt", System.currentTimeMillis()); j.put("densityDpi", dm.densityDpi); j.put("widthPixels", dm.widthPixels); j.put("heightPixels", dm.heightPixels);
            j.put("fontScale", cf.fontScale); j.put("smallestWidthDp", cf.smallestScreenWidthDp); j.put("screenWidthDp", cf.screenWidthDp); j.put("screenHeightDp", cf.screenHeightDp);
            j.put("locale", Locale.getDefault().toLanguageTag()); j.put("timezone", TimeZone.getDefault().getID());
            return j.toString();
        } catch (Throwable t) { return "{}"; }
    }

    private String buildRawInfo() {
        StringBuilder b = new StringBuilder();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
            ApplicationInfo ai = pi.applicationInfo;
            b.append("版本：").append(pi.versionName).append(" (").append(pi.getLongVersionCode()).append(")\n");
            if (ai != null) b.append("UID：").append(ai.uid).append("\nminSdk：").append(ai.minSdkVersion).append("  targetSdk：").append(ai.targetSdkVersion).append('\n');
            b.append("请求权限：").append(pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length).append('\n');
            ActivityInfo launch = launchActivityInfo();
            if (launch != null) b.append("启动 Activity：").append(launch.name).append("\nManifest orientation：").append(launch.screenOrientation).append('\n');
        } catch (Throwable t) { b.append("应用信息读取失败：").append(t.getClass().getSimpleName()).append('\n'); }
        DisplayMetrics dm = getResources().getDisplayMetrics(); Configuration cf = getResources().getConfiguration();
        b.append("\n系统显示：").append(dm.widthPixels).append('×').append(dm.heightPixels).append("  ").append(dm.densityDpi).append("dpi\n");
        b.append("Configuration：").append(cf.screenWidthDp).append('×').append(cf.screenHeightDp).append("dp  sw=").append(cf.smallestScreenWidthDp).append("dp\n");
        b.append("Locale：").append(Locale.getDefault().toLanguageTag()).append("\nTimezone：").append(TimeZone.getDefault().getID()).append('\n');
        SharedPreferences p = YEnvApp.remotePrefs();
        if (p != null && p.contains("baseline." + packageName)) b.append("\n首次修改前快照：\n").append(p.getString("baseline." + packageName, ""));
        return b.toString();
    }

    private ActivityInfo launchActivityInfo() {
        try {
            var i = getPackageManager().getLaunchIntentForPackage(packageName);
            if (i == null || i.getComponent() == null) return null;
            return getPackageManager().getActivityInfo(i.getComponent(), 0);
        } catch (Throwable t) { return null; }
    }

    private String defaultOrientation() { ActivityInfo a = launchActivityInfo(); return a == null ? "未声明/无启动 Activity" : String.valueOf(a.screenOrientation); }
    private String defaultRefreshRate() { try { return String.valueOf(getDisplay().getRefreshRate()); } catch (Throwable t) { return "系统值"; } }
    private static String nightText(Configuration c) { int n = c.uiMode & Configuration.UI_MODE_NIGHT_MASK; return n == Configuration.UI_MODE_NIGHT_YES ? "深色" : n == Configuration.UI_MODE_NIGHT_NO ? "浅色" : "未指定"; }

    private Integer intField(String k) { String s = stringField(k); return s == null ? null : Integer.valueOf(s); }
    private Float floatField(String k) { String s = stringField(k); return s == null ? null : Float.valueOf(s); }
    private Double doubleField(String k) { String s = stringField(k); return s == null ? null : Double.valueOf(s); }
    private String stringField(String k) { String s = fields.get(k).getText().toString().trim(); return s.isEmpty() ? null : s; }
    private static Boolean triBool(int pos) { return pos == 0 ? null : pos == 1; }
    private static String val(Object o) { return o == null ? null : String.valueOf(o); }
    private static int nightIndex(String s) { return "light".equals(s) ? 1 : "dark".equals(s) ? 2 : 0; }
    private static int orientationIndex(String s) { return "portrait".equals(s) ? 1 : "landscape".equals(s) ? 2 : "sensor".equals(s) ? 3 : "locked".equals(s) ? 4 : 0; }
    private static int boolIndex(Boolean b) { return b == null ? 0 : b ? 1 : 2; }
    private static int locationIndex(String s) { return "fixed".equals(s) ? 1 : "random".equals(s) ? 2 : 0; }

    private void section(String s) { TextView v = text(s, 19, true); v.setPadding(0, dp(18), 0, dp(4)); root.addView(v); }
    private TextView text(String s, int sp, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); return b; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override public void onServiceChanged() { runOnUiThread(this::refreshStatus); }
}
