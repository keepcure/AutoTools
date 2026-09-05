package com.example.autotools;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "auto_tools";
    private static final String TARGET_PACKAGE = "target_package";
    private EditText packageInput;
    private TextView serviceStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (packageInput != null) {
            packageInput.setText(getPreferences(MODE_PRIVATE).getString(TARGET_PACKAGE, ""));
        }
        updateServiceStatus();
    }

    private void buildView() {
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("自动下一步");
        title.setTextSize(26);
        title.setTextColor(0xff12304a);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(this);
        description.setText("在您授权后，自动识别目标游戏中的“下一步”或“继续”按钮并点击。建议填写目标应用包名，留空则匹配所有第三方应用。\n\n请只对您有权操作的应用启用自动化。");
        description.setTextSize(16);
        description.setPadding(0, padding / 2, 0, padding / 2);
        root.addView(description, new LinearLayout.LayoutParams(-1, -2));

        packageInput = new EditText(this);
        packageInput.setHint("目标应用包名，例如 com.example.game");
        packageInput.setSingleLine(true);
        root.addView(packageInput, new LinearLayout.LayoutParams(-1, -2));

        Button saveButton = new Button(this);
        saveButton.setText("保存目标应用");
        saveButton.setOnClickListener(view -> saveTargetPackage());
        root.addView(saveButton, new LinearLayout.LayoutParams(-1, -2));

        Button settingsButton = new Button(this);
        settingsButton.setText("打开无障碍设置");
        settingsButton.setOnClickListener(view -> {
            saveTargetPackage();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        root.addView(settingsButton, new LinearLayout.LayoutParams(-1, -2));

        serviceStatus = new TextView(this);
        serviceStatus.setTextSize(15);
        serviceStatus.setPadding(0, padding / 2, 0, 0);
        root.addView(serviceStatus, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void saveTargetPackage() {
        getPreferences(MODE_PRIVATE).edit()
                .putString(TARGET_PACKAGE, packageInput.getText().toString().trim())
                .apply();
        serviceStatus.setText("目标应用设置已保存");
    }

    private void updateServiceStatus() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean enabled = false;
        if (manager != null) {
            List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo service : services) {
                if (getPackageName().equals(service.getResolveInfo().serviceInfo.packageName)) {
                    enabled = true;
                    break;
                }
            }
        }
        serviceStatus.setText(enabled ? "服务状态：已开启" : "服务状态：未开启，请先打开无障碍设置");
    }
}
