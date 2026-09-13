package com.example.autotools;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.WindowManager;

import java.util.Locale;

public class NextStepAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoTools";
    private static final String PREFS = "auto_tools";
    private static final String TARGET_PACKAGE = "target_package";
    private static final String DEFAULT_TARGET_PACKAGE = "com.leniu.dpcqln.vivo";
    private static final float NEXT_BUTTON_X_RATIO = 0.667f;
    private static final float NEXT_BUTTON_Y_RATIO = 0.9302f;
    private static final float FAST_FORWARD_X_RATIO = 0.926f;
    private static final float FAST_FORWARD_Y_RATIO = 0.864f;
    private static final long DIAGNOSTIC_COOLDOWN_MS = 3000L;
    private long lastDiagnosticTime;
    private final BroadcastReceiver clickTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            Log.i(TAG, "received click tick broadcast: " + intent);
            if (!AutoClickForegroundService.ACTION_TICK.equals(intent.getAction())
                    || !isTargetGameForeground()) {
                return;
            }
            boolean nextButton = intent.getBooleanExtra(
                    AutoClickForegroundService.EXTRA_NEXT_BUTTON, true);
            int action = intent.getIntExtra(AutoClickForegroundService.EXTRA_ACTION, nextButton ? 0 : 1);
            if (action == AutoClickForegroundService.ACTION_NEXT) {
                performCoordinateGesture(NEXT_BUTTON_X_RATIO, NEXT_BUTTON_Y_RATIO, "next");
            } else {
                performCoordinateGesture(FAST_FORWARD_X_RATIO, FAST_FORWARD_Y_RATIO, "fast-forward");
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        registerClickTickReceiver();
        Intent serviceIntent = new Intent(this, AutoClickForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.i(TAG, "service connected; foreground click service started");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        String targetPackage = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE).trim();
        if (targetPackage.isEmpty()) {
            targetPackage = DEFAULT_TARGET_PACKAGE;
        }
        String currentPackage = event.getPackageName() == null
                ? ""
                : event.getPackageName().toString();
        Log.d(TAG, "event type=" + event.getEventType()
            + ", package=" + currentPackage
            + ", hasSource=" + (event.getSource() != null));
        if (currentPackage.equals(getPackageName())
                || (!targetPackage.isEmpty() && !targetPackage.equals(currentPackage))) {
            // System and game-space events can arrive while the target game remains visible.
            return;
        }
        if (event.getSource() == null) {
            return;
        }
        logWindowShape(event.getSource(), currentPackage);
        AccessibilityNodeInfo candidate = findNextStep(event.getSource());
        if (candidate != null && clickCandidate(candidate)) {
            Log.i(TAG, "accessible next-step click");
        }
    }

    private boolean isTargetGameForeground() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) {
            return false;
        }
        String targetPackage = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE).trim();
        if (targetPackage.isEmpty()) {
            targetPackage = DEFAULT_TARGET_PACKAGE;
        }
        return targetPackage.equals(root.getPackageName().toString());
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

    private int countNodes(AccessibilityNodeInfo node) {
        if (node == null) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += countNodes(node.getChild(i));
        }
        return count;
    }

    private void registerClickTickReceiver() {
        IntentFilter filter = new IntentFilter(AutoClickForegroundService.ACTION_TICK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(clickTickReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(clickTickReceiver, filter);
        }
    }

    private void performCoordinateGesture(float xRatio, float yRatio, String actionName) {
        Log.i(TAG, "performing " + actionName + " gesture at xRatio=" + xRatio + ", yRatio=" + yRatio);
        DisplayMetrics realMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }
        windowManager.getDefaultDisplay().getRealMetrics(realMetrics);
        int width = realMetrics.widthPixels;
        int height = realMetrics.heightPixels;
        Path path = new Path();
        path.moveTo(width * xRatio, height * yRatio);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 80);
        boolean accepted = dispatchGesture(
                new GestureDescription.Builder().addStroke(stroke).build(),
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        Log.d(TAG, actionName + " gesture completed");
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        Log.w(TAG, actionName + " gesture cancelled");
                        Log.d(TAG, actionName + " gesture will be retried on the next tick");
                    }
                },
                null);
        Log.i(TAG, "width: " + width + ",height: " + height
                + "," + actionName + " click at x=" + (int) (width * xRatio)
                + ", y=" + (int) (height * yRatio)
                + ", accepted=" + accepted);
    }

    private void logWindowShape(AccessibilityNodeInfo root, String packageName) {
        long now = System.currentTimeMillis();
        if (now - lastDiagnosticTime < DIAGNOSTIC_COOLDOWN_MS) {
            return;
        }
        lastDiagnosticTime = now;
        StringBuilder classes = new StringBuilder();
        int nodeCount = appendClassNames(root, classes, 0);
        Log.i(TAG, "package=" + packageName
                + ", nodes=" + nodeCount
                + ", classes=" + classes
                + ", renderingSurface=" + hasRenderingSurface(classes.toString())
                + ", likelyEngine=" + guessEngine(classes.toString(), nodeCount));
    }

    private int appendClassNames(AccessibilityNodeInfo node, StringBuilder classes, int nodeCount) {
        if (node == null) {
            return nodeCount;
        }
        nodeCount++;
        String className = node.getClassName() == null ? "" : node.getClassName().toString();
        if (classes.indexOf(className) < 0 && classes.length() < 600) {
            if (classes.length() > 0) {
                classes.append('|');
            }
            classes.append(className);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            nodeCount = appendClassNames(node.getChild(i), classes, nodeCount);
        }
        return nodeCount;
    }

    private boolean hasRenderingSurface(String classes) {
        return classes.contains("SurfaceView")
                || classes.contains("TextureView")
                || classes.contains("GLSurfaceView")
                || classes.contains("UnityPlayer");
    }

    private String guessEngine(String classes, int nodeCount) {
        if (classes.contains("UnityPlayer")) {
            return "Unity";
        }
        if (classes.contains("FlutterView")) {
            return "Flutter";
        }
        if (classes.contains("Cocos2dxGLSurfaceView")) {
            return "Cocos2d-x";
        }
        if (classes.contains("SurfaceView") || classes.contains("GLSurfaceView")
                || classes.contains("TextureView")) {
            return "自绘引擎或OpenGL/Canvas";
        }
        return nodeCount <= 2 ? "无障碍节点过少" : "原生或可访问UI";
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
        Log.i(TAG, "accessibility interrupted; foreground click service remains active");
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(clickTickReceiver);
        stopService(new Intent(this, AutoClickForegroundService.class));
        Log.i(TAG, "accessibility service destroyed; foreground click service stopped");
        super.onDestroy();
    }
}
