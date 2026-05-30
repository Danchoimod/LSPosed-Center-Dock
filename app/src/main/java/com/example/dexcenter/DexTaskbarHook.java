package com.example.dexcenter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class DexTaskbarHook implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.sec.android.dexsystemui";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PKG) && !lpparam.packageName.equals("com.android.systemui")) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.view.View",
                lpparam.classLoader,
                "onFinishInflate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String className = param.thisObject.getClass().getName();
                        
                        if (!className.equals("com.sec.android.dexsystemui.components.taskbar.ui.views.TaskBarView")) {
                            return;
                        }

                        final ViewGroup taskBarView = (ViewGroup) param.thisObject;

                        XposedBridge.log("[DEX_DEBUG] Hooked TaskBarView. Setting up translation centering listener...");

                        int containerId = taskBarView.getResources().getIdentifier("container", "id", TARGET_PKG);
                        int leftPanelId = taskBarView.getResources().getIdentifier("left_panel", "id", TARGET_PKG);
                        int appdockContainerId = taskBarView.getResources().getIdentifier("appdock_container", "id", TARGET_PKG);

                        final ViewGroup container = taskBarView.findViewById(containerId);
                        final View leftPanel = taskBarView.findViewById(leftPanelId);
                        final View appdockContainer = taskBarView.findViewById(appdockContainerId);

                        if (container != null && leftPanel != null && appdockContainer != null) {
                            
                            // Lắng nghe sự thay đổi kích thước của container (thanh Taskbar) và appdock
                            View.OnLayoutChangeListener layoutListener = new View.OnLayoutChangeListener() {
                                @Override
                                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                    try {
                                        int containerWidth = container.getWidth();
                                        int leftPanelWidth = leftPanel.getWidth();
                                        int appdockWidth = appdockContainer.getWidth();

                                        if (containerWidth <= 0 || leftPanelWidth <= 0) {
                                            return;
                                        }

                                        // Chiều rộng của cả cụm nút và dock ứng dụng
                                        int totalClusterWidth = leftPanelWidth + appdockWidth;

                                        // Vị trí bắt đầu của cụm để căn giữa
                                        int targetStart = (containerWidth - totalClusterWidth) / 2;

                                        // Vị trí bắt đầu thực tế hiện tại (left_panel mặc định căn trái, bắt đầu từ 0)
                                        int currentStart = leftPanel.getLeft();

                                        // Tính toán khoảng dịch chuyển cần thiết (Translation X)
                                        float translationX = targetStart - currentStart;

                                        // Áp dụng dịch chuyển ngang cho cả 2 view để gom chúng ra giữa
                                        leftPanel.setTranslationX(translationX);
                                        appdockContainer.setTranslationX(translationX);

                                    } catch (Exception e) {
                                        XposedBridge.log("[DEX_DEBUG] Translation calc failed: " + e.getMessage());
                                    }
                                }
                            };

                            // Gán listener theo dõi thay đổi bố cục để tự động căn giữa động khi thêm/bớt app
                            container.addOnLayoutChangeListener(layoutListener);
                            leftPanel.addOnLayoutChangeListener(layoutListener);
                            appdockContainer.addOnLayoutChangeListener(layoutListener);

                            XposedBridge.log("[DEX_DEBUG] Translation layout listener registered successfully!");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log("[DEX_DEBUG] Error setting up translation hook: " + e.getMessage());
        }
    }
}
