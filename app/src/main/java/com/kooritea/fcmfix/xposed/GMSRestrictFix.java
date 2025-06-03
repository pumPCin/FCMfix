package com.kooritea.fcmfix.xposed;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class GMSRestrictFix extends XposedModule {
    public GMSRestrictFix(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        super(loadPackageParam);
        try {
            this.startHookRegisterGmsRestrictObserver();
        } catch (Throwable e) {
            printLog("h00k error registerGmsRestrictObserver:" + e.getMessage());
        }
        try {
            this.startHookUpdateGmsRestrict();
        } catch (Throwable e) {
            printLog("h00k error updateGmsRestrict:" + e.getMessage());
        }
        try {
            this.startHookIsGoogleRestricInfoOn();
        } catch (Throwable e) {
            printLog("h00k error isGoogleRestricInfoOn:" + e.getMessage());
        }
    }

    private void startHookRegisterGmsRestrictObserver() {
        XposedHelpers.findAndHookMethod("com.android.server.hans.scene.OplusBgSceneManager", loadPackageParam.classLoader, "registerGmsRestrictObserver", XC_MethodReplacement.DO_NOTHING);
    }

    private void startHookUpdateGmsRestrict() {
        XposedHelpers.findAndHookMethod("com.android.server.hans.scene.OplusBgSceneManager", loadPackageParam.classLoader, "updateGmsRestrict", XC_MethodReplacement.DO_NOTHING);
    }

    private void startHookIsGoogleRestricInfoOn() {
        XposedHelpers.findAndHookMethod("com.android.server.am.OplusAppStartupManager$OplusStartupStrategy", loadPackageParam.classLoader, "isGoogleRestricInfoOn", int.class, XC_MethodReplacement.returnConstant(false));
    }

    private void startHookIsGmsApp() {
        XposedHelpers.findAndHookMethod("com.android.server.hans.OplusHansDBConfig", loadPackageParam.classLoader, "isGmsApp", int.class, XC_MethodReplacement.returnConstant(false));
    }
}
