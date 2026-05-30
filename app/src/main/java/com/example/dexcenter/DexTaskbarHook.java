package com.example.dexcenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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

                        XposedBridge.log("[DEX_DEBUG] Hooked TaskBarView onFinishInflate.");

                        int containerId = taskBarView.getResources().getIdentifier("container", "id", TARGET_PKG);
                        int leftPanelId = taskBarView.getResources().getIdentifier("left_panel", "id", TARGET_PKG);
                        int appdockContainerId = taskBarView.getResources().getIdentifier("appdock_container", "id", TARGET_PKG);
                        int dividerId = taskBarView.getResources().getIdentifier("pinned_apps_divider", "id", TARGET_PKG);

                        final ViewGroup container = taskBarView.findViewById(containerId);
                        final View leftPanel = taskBarView.findViewById(leftPanelId);
                        final View appdockContainer = taskBarView.findViewById(appdockContainerId);
                        final View divider = taskBarView.findViewById(dividerId);

                        if (container instanceof RelativeLayout && leftPanel != null && appdockContainer != null) {
                            XposedBridge.log("[DEX_DEBUG] Found RelativeLayout container and target panels.");

                            View.OnLayoutChangeListener centerListener = new View.OnLayoutChangeListener() {
                                private boolean scheduledDump = false;

                                @Override
                                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                    try {
                                        int containerW = container.getWidth();
                                        int navW = leftPanel.getWidth();
                                        int dockW = appdockContainer.getVisibility() != View.GONE ? appdockContainer.getWidth() : 0;

                                        if (containerW <= 0 || navW <= 0) {
                                            return;
                                        }

                                        // Schedule a hierarchy dump 3 seconds after the first valid layout pass
                                        if (!scheduledDump && dockW > 0) {
                                            scheduledDump = true;
                                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        XposedBridge.log("[DEX_DEBUG] --- DUMPING APPDOCK HIERARCHY (DELAYED 3S) ---");
                                                        dumpViewHierarchy(appdockContainer, "");
                                                    } catch (Exception e) {
                                                        XposedBridge.log("[DEX_DEBUG] Delayed dump error: " + e.getMessage());
                                                    }
                                                }
                                            }, 3000);
                                        }

                                        // Find CellLayoutTaskbar inside appdockContainer
                                        View cellLayout = findCellLayout(appdockContainer);
                                        if (cellLayout == null) {
                                            return;
                                        }

                                        // Find pinned and recent containers
                                        View pinnedContainer = null;
                                        View recentContainer = null;
                                        if (cellLayout instanceof ViewGroup) {
                                            ViewGroup vg = (ViewGroup) cellLayout;
                                            if (vg.getChildCount() > 0) {
                                                pinnedContainer = vg.getChildAt(0);
                                            }
                                            if (vg.getChildCount() > 1) {
                                                recentContainer = vg.getChildAt(1);
                                            }
                                        }

                                        // Measure pinned apps visible right edge
                                        int pinnedVisibleRight = 0;
                                        if (pinnedContainer instanceof ViewGroup && pinnedContainer.getVisibility() == View.VISIBLE) {
                                            ViewGroup vg = (ViewGroup) pinnedContainer;
                                            for (int i = 0; i < vg.getChildCount(); i++) {
                                                View child = vg.getChildAt(i);
                                                if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0) {
                                                    pinnedVisibleRight = Math.max(pinnedVisibleRight, child.getRight());
                                                }
                                            }
                                        }

                                        // Measure recent apps visible bounds
                                        int recentVisibleLeft = -1;
                                        int recentVisibleRight = 0;
                                        if (recentContainer instanceof ViewGroup && recentContainer.getVisibility() == View.VISIBLE) {
                                            ViewGroup vg = (ViewGroup) recentContainer;
                                            for (int i = 0; i < vg.getChildCount(); i++) {
                                                View child = vg.getChildAt(i);
                                                if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0) {
                                                    int cLeft = child.getLeft();
                                                    int cRight = child.getRight();
                                                    if (recentVisibleLeft == -1 || cLeft < recentVisibleLeft) {
                                                        recentVisibleLeft = cLeft;
                                                    }
                                                    recentVisibleRight = Math.max(recentVisibleRight, cRight);
                                                }
                                            }
                                        }

                                        // Gap between pinned and recent apps
                                        int gap = 14; // default gap in px

                                        // Position of recent container
                                        float recentTargetLeft = 0;
                                        if (pinnedVisibleRight > 0) {
                                            recentTargetLeft = pinnedVisibleRight + gap;
                                            if (divider != null && divider.getVisibility() == View.VISIBLE) {
                                                recentTargetLeft += divider.getWidth() + gap;
                                            }
                                        }

                                        // Apply translation to recent container if it has visible apps
                                        if (recentContainer != null && recentContainer.getVisibility() == View.VISIBLE && recentVisibleLeft != -1) {
                                            float recentTranslation = recentTargetLeft - (recentContainer.getLeft() + recentVisibleLeft);
                                            recentContainer.setTranslationX(recentTranslation);
                                        } else if (recentContainer != null) {
                                            recentContainer.setTranslationX(0);
                                        }

                                        // Calculate total visible width of the dock
                                        float visibleDockW = 0;
                                        if (recentContainer != null && recentContainer.getVisibility() == View.VISIBLE && recentVisibleRight > 0) {
                                            visibleDockW = recentTargetLeft + (recentVisibleRight - recentVisibleLeft);
                                        } else if (pinnedVisibleRight > 0) {
                                            visibleDockW = pinnedVisibleRight;
                                        }

                                        // Calculate cluster start
                                        float clusterW = navW + visibleDockW;
                                        float clusterStart = (containerW - clusterW) / 2.0f;

                                        // navTranslation centers the leftPanel at clusterStart
                                        float navTranslation = clusterStart - leftPanel.getLeft();
                                        
                                        // dockTranslation positions appdockContainer so that its visible start is at clusterStart + navW
                                        float dockTranslation = 0;
                                        if (visibleDockW > 0) {
                                            dockTranslation = (clusterStart + navW) - appdockContainer.getLeft();
                                        } else {
                                            // If no apps are visible, center the nav panel alone
                                            clusterStart = (containerW - navW) / 2.0f;
                                            navTranslation = clusterStart - leftPanel.getLeft();
                                            dockTranslation = 0;
                                        }

                                        leftPanel.setTranslationX(navTranslation);
                                        appdockContainer.setTranslationX(dockTranslation);

                                        // Apply translation to divider if visible and there are pinned apps
                                        if (divider != null && divider.getVisibility() == View.VISIBLE) {
                                            if (pinnedVisibleRight > 0) {
                                                float dividerTargetLeft = pinnedVisibleRight + gap;
                                                // dividerTargetLeft is relative to appdockContainer's visual start
                                                float dividerVisualLeft = (clusterStart + navW) + dividerTargetLeft;
                                                float dividerTranslation = dividerVisualLeft - divider.getLeft();
                                                divider.setTranslationX(dividerTranslation);
                                            } else {
                                                divider.setTranslationX(0);
                                            }
                                        }

                                        XposedBridge.log(String.format(
                                            "[DEX_DEBUG] center: cW=%d navW=%d visDockW=%.1f start=%.1f navTx=%.1f dockTx=%.1f",
                                            containerW, navW, visibleDockW, clusterStart, navTranslation, dockTranslation
                                        ));
                                    } catch (Exception e) {
                                        XposedBridge.log("[DEX_DEBUG] Center logic error: " + e.getMessage());
                                    }
                                }
                            };

                            // Listen to layout changes of all three views to keep them aligned
                            container.addOnLayoutChangeListener(centerListener);
                            leftPanel.addOnLayoutChangeListener(centerListener);
                            appdockContainer.addOnLayoutChangeListener(centerListener);

                            XposedBridge.log("[DEX_DEBUG] Listeners registered. Nav + Dock will be centered and flush.");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log("[DEX_DEBUG] Error setting up layout hook: " + e.getMessage());
        }
    }

    private View findCellLayout(View view) {
        if (view.getClass().getName().contains("CellLayoutTaskbar")) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = findCellLayout(vg.getChildAt(i));
                if (child != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private void dumpViewHierarchy(View view, String indent) {
        try {
            String info = String.format("%s%s id=%d vis=%d w=%d h=%d L=%d R=%d", 
                indent, 
                view.getClass().getName(), 
                view.getId(), 
                view.getVisibility(), 
                view.getWidth(), 
                view.getHeight(),
                view.getLeft(),
                view.getRight()
            );
            XposedBridge.log("[DEX_DEBUG] " + info);
            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    dumpViewHierarchy(vg.getChildAt(i), indent + "  ");
                }
            }
        } catch (Exception e) {
            XposedBridge.log("[DEX_DEBUG] Hierarchy dump error: " + e.getMessage());
        }
    }
}
