package com.example.dexcenter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

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
            // Hook vào View.onFinishInflate để thiết lập điểm neo căn giữa
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
                        final Context context = taskBarView.getContext();

                        XposedBridge.log("[DEX_DEBUG] Hooked TaskBarView constructor/inflation.");

                        int containerId = taskBarView.getResources().getIdentifier("container", "id", TARGET_PKG);
                        int leftPanelId = taskBarView.getResources().getIdentifier("left_panel", "id", TARGET_PKG);
                        int appdockContainerId = taskBarView.getResources().getIdentifier("appdock_container", "id", TARGET_PKG);

                        final ViewGroup container = taskBarView.findViewById(containerId);
                        final View leftPanel = taskBarView.findViewById(leftPanelId);
                        final View appdockContainer = taskBarView.findViewById(appdockContainerId);

                        if (container instanceof RelativeLayout && leftPanel != null && appdockContainer != null) {
                            XposedBridge.log("[DEX_DEBUG] Found RelativeLayout container and target panels.");

                            container.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // 1. Tạo một anchor View vô hình (invisible) làm điểm neo ở chính giữa container
                                        View anchor = new View(context);
                                        int anchorId = View.generateViewId();
                                        anchor.setId(anchorId);
                                        anchor.setVisibility(View.INVISIBLE);

                                        RelativeLayout.LayoutParams anchorParams = new RelativeLayout.LayoutParams(0, 0);
                                        anchorParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                                        anchorParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                                        anchor.setLayoutParams(anchorParams);

                                        // Thêm anchor vào container
                                        container.addView(anchor);

                                        // 2. Chỉnh sửa LayoutParams của leftPanel (giữ nguyên kiểu RelativeLayout.LayoutParams)
                                        RelativeLayout.LayoutParams newLeftParams = (RelativeLayout.LayoutParams) leftPanel.getLayoutParams();
                                        // Xoá căn lề trái mặc định
                                        newLeftParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
                                        // Căn lề phải của leftPanel nằm sát bên TRÁI của điểm neo anchor
                                        newLeftParams.addRule(RelativeLayout.LEFT_OF, anchorId);
                                        leftPanel.setLayoutParams(newLeftParams);

                                        // 3. Chỉnh sửa LayoutParams của appdockContainer (giữ nguyên kiểu RelativeLayout.LayoutParams)
                                        RelativeLayout.LayoutParams newDockParams = (RelativeLayout.LayoutParams) appdockContainer.getLayoutParams();
                                        // Xoá các luật cũ nếu có liên quan đến căn lề trái
                                        newDockParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
                                        // Căn lề trái của appdockContainer nằm sát bên PHẢI của điểm neo anchor
                                        newDockParams.addRule(RelativeLayout.RIGHT_OF, anchorId);
                                        appdockContainer.setLayoutParams(newDockParams);

                                        // Yêu cầu vẽ lại layout
                                        container.requestLayout();

                                        XposedBridge.log("[DEX_DEBUG] Successfully centered taskbar via RelativeLayout anchor without ClassCastException!");
                                    } catch (Exception e) {
                                        XposedBridge.log("[DEX_DEBUG] Anchor setup failed: " + e.getMessage());
                                    }
                                }
                            });
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log("[DEX_DEBUG] Error setting up anchor hook: " + e.getMessage());
        }
    }
}
