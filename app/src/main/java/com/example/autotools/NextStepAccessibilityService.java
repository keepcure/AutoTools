package com.example.autotools;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

public class NextStepAccessibilityService extends AccessibilityService {
    private static final String PREFS = "auto_tools";
    private static final String TARGET_PACKAGE = "target_package";
    private static final long CLICK_COOLDOWN_MS = 900L;
    private long lastClickTime;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getSource() == null) {
            return;
        }
        String targetPackage = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(TARGET_PACKAGE, "").trim();
        String currentPackage = event.getPackageName() == null
                ? ""
                : event.getPackageName().toString();
        if (currentPackage.equals(getPackageName())
                || (!targetPackage.isEmpty() && !targetPackage.equals(currentPackage))) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS) {
            return;
        }
        AccessibilityNodeInfo candidate = findNextStep(event.getSource());
        if (candidate != null && clickCandidate(candidate)) {
            lastClickTime = now;
        }
    }

    private AccessibilityNodeInfo findNextStep(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        String text = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null
                ? ""
                : node.getContentDescription().toString();
        if (isNextStep(text) || isNextStep(description)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNextStep(node.getChild(i));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private boolean isNextStep(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("下一步")
                || normalized.equals("继续")
                || normalized.equals("next")
                || normalized.equals("continue")
                || normalized.equals("next step");
    }

    private boolean clickCandidate(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo clickable = node;
        while (clickable != null && !clickable.isClickable()) {
            clickable = clickable.getParent();
        }
        return clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    @Override
    public void onInterrupt() {
        // Required by AccessibilityService.
    }
}
