package com.kooritea.fcmfix.xposed;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.kooritea.fcmfix.util.XposedUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Timer;
import java.util.TimerTask;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class ReconnectManagerFix extends XposedModule {

    private Class<?> GcmChimeraService;
    private String GcmChimeraServiceLogMethodName;
    private Boolean startHookFlag = false;


    public ReconnectManagerFix(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        super(loadPackageParam);
        this.addButton();
        this.startHookGcmServiceStart();
    }

    @Override
    protected void onCanReadConfig() throws Throwable {
        if(startHookFlag){
            this.checkVersion();
            onUpdateConfig();
        }else {
            startHookFlag = true;
        }

    }

    private void startHookGcmServiceStart() {
        this.GcmChimeraService = XposedHelpers.findClass("com.google.android.gms.gcm.GcmChimeraService", loadPackageParam.classLoader);
        try{
            for(Method method : this.GcmChimeraService.getMethods()){
                if(method.getParameterTypes().length == 2){
                    if(method.getParameterTypes()[0] == String.class && method.getParameterTypes()[1] == Object[].class){
                        this.GcmChimeraServiceLogMethodName = method.getName();
                        break;
                    }
                }
            }
            XposedHelpers.findAndHookMethod(this.GcmChimeraService, "onCreate", new XC_MethodHook() {
                @SuppressLint("UnspecifiedRegisterReceiverFlag")
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction("com.kooritea.fcmfix.log");
                    if (Build.VERSION.SDK_INT >= 34) {
                        context.registerReceiver(logBroadcastReceive, intentFilter, Context.RECEIVER_EXPORTED);
                    } else {
                        context.registerReceiver(logBroadcastReceive, intentFilter);
                    }
                    if(startHookFlag){
                        checkVersion();
                    }else {
                        startHookFlag = true;
                    }
                }
            });
            XposedHelpers.findAndHookMethod(this.GcmChimeraService, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    context.unregisterReceiver(logBroadcastReceive);
                }
            });
        }catch (Throwable e){
            XposedBridge.log("GcmChimeraService h00k failed");
        }
    }

    public static final String configVersion = "v3";
    private void checkVersion() throws Throwable {
        final SharedPreferences sharedPreferences = context.getSharedPreferences("fcmfix_config", Context.MODE_PRIVATE);
        String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        long versionCode = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        if(versionCode < 213916046){
            printLog("Old GMS version. Please use fcmfix 0.4.1 version and disable the reconnection repair function.");
            return;
        }
        if (!sharedPreferences.getBoolean("isInit", false) || !sharedPreferences.getString("config_version", "").equals(configVersion)) {
            printLog("fcmfix_config init", true);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isInit", true);
            editor.putBoolean("enable", false);
            editor.putLong("heartbeatInterval", 0L);
            editor.putLong("reconnInterval", 0L);
            editor.putString("gms_version", versionName);
            editor.putLong("gms_version_code", versionCode);
            editor.putString("config_version", configVersion);
            editor.putString("timer_class", "");
            editor.putString("timer_settimeout_method", "");
            editor.putString("timer_alarm_type_property", "");
            editor.apply();
            printLog("Updating h00k location", true);
            findAndUpdateHookTarget(sharedPreferences);
            return;
        }
        if (!sharedPreferences.getString("gms_version", "").equals(versionName) ) {
            printLog("GMS updated: " + sharedPreferences.getString("gms_version", "") + "(" + sharedPreferences.getLong("gms_version_code", 0) + ")" + "->" + versionName + "(" +versionCode + ")", true);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("gms_version", versionName);
            editor.putLong("gms_version_code", versionCode);
            editor.putBoolean("enable", false);
            editor.apply();
            printLog("Updating h00k location", true);
            findAndUpdateHookTarget(sharedPreferences);
            return;
        }
        if (!sharedPreferences.getBoolean("enable", false)) {
            printLog("In file configuration current flag is <false>, fcmfix exits", true);
            return;
        }
        startHook();
    }

    protected void startHook() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences("fcmfix_config", Context.MODE_PRIVATE);
        printLog("timer_class: "+ sharedPreferences.getString("timer_class", ""), true);
        printLog("timer_alarm_type_property: "+ sharedPreferences.getString("timer_alarm_type_property", ""), true);
        printLog("timer_settimeout_method: "+ sharedPreferences.getString("timer_settimeout_method", ""), true);
        final Class<?> timerClazz = XposedHelpers.findClass(sharedPreferences.getString("timer_class", ""), loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(timerClazz, "toString", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) {
                String alarmType = (String) XposedUtils.getObjectFieldByPath(param.thisObject,  sharedPreferences.getString("timer_alarm_type_property", ""));
                if("GCM_HB_ALARM".equals(alarmType) || "GCM_CONN_ALARM".equals(alarmType)){
                    long hinterval = sharedPreferences.getLong("heartbeatInterval", 0L);
                    long cinterval = sharedPreferences.getLong("reconnInterval", 0L);
                    if((hinterval > 1000) || (cinterval > 1000)){
                        param.setResult(param.getResult() + "[fcmfix locked]");
                    }
                }
            }
        });
        XposedHelpers.findAndHookMethod(timerClazz, sharedPreferences.getString("timer_settimeout_method", ""), long.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) {
                String alarmType = (String) XposedUtils.getObjectFieldByPath(param.thisObject,  sharedPreferences.getString("timer_alarm_type_property", ""));
                if ("GCM_HB_ALARM".equals(alarmType)) {
                    long interval = sharedPreferences.getLong("heartbeatInterval", 0L);
                    if(interval > 1000){
                        param.args[0] = interval;
                    }
                }
                if ("GCM_CONN_ALARM".equals(alarmType)) {
                    long interval = sharedPreferences.getLong("reconnInterval", 0L);
                    if(interval > 1000){
                        param.args[0] = interval;
                    }
                }
            }

            @Override
            protected void afterHookedMethod(final MethodHookParam param) {
                String alarmType = (String) XposedUtils.getObjectFieldByPath(param.thisObject,  sharedPreferences.getString("timer_alarm_type_property", ""));
                if ("GCM_HB_ALARM".equals(alarmType) || "GCM_CONN_ALARM".equals(alarmType)) {
                    Field maxField = null;
                    long maxFieldValue = 0L;
                    for(Field field : timerClazz.getDeclaredFields()){
                        if(field.getType() == long.class){
                            long fieldValue = (long)XposedHelpers.getObjectField(param.thisObject,field.getName());
                            if(maxField == null || fieldValue > maxFieldValue){
                                maxField = field;
                                maxFieldValue = fieldValue;
                            }
                        }
                    }
                    final Timer timer = new Timer("ReconnectManagerFix");
                    final Field finalMaxField = maxField;
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            long nextConnectionTime = XposedHelpers.getLongField(param.thisObject, finalMaxField.getName());
                            if (nextConnectionTime != 0 && nextConnectionTime - SystemClock.elapsedRealtime() < -60000) {
                                context.sendBroadcast(new Intent("com.google.android.intent.action.GCM_RECONNECT"));
                                printLog("Send broadcast GCM_RECONNECT", true);
                            }
                            timer.cancel();
                        }
                    }, (long) param.args[0] + 5000);
                }
            }
        });
    }

    private final BroadcastReceiver logBroadcastReceive = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.kooritea.fcmfix.log".equals(action)) {
                try{
                    XposedHelpers.callStaticMethod(GcmChimeraService,GcmChimeraServiceLogMethodName , new Class<?>[]{String.class, Object[].class}, "[fcmfix] " + intent.getStringExtra("text"), null);
                }catch (Throwable e){
                    XposedBridge.log("Failed output log to FCM： " + "[fcmfix] " + intent.getStringExtra("text"));
                }
            }
        }
    };

    private void findAndUpdateHookTarget(final SharedPreferences sharedPreferences){
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        try{
            Class<?> heartbeatChimeraAlarm =  XposedHelpers.findClass("com.google.android.gms.gcm.connection.HeartbeatChimeraAlarm",loadPackageParam.classLoader);
            Class<?> timerClass = heartbeatChimeraAlarm.getConstructors()[0].getParameterTypes()[3];
            if (timerClass.getDeclaredMethods().length == 0) {
                timerClass = timerClass.getSuperclass();
            }
            editor.putString("timer_class", timerClass.getName());
            for(Method method : timerClass.getDeclaredMethods()){
                if(method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == long.class && Modifier.isFinal(method.getModifiers()) && Modifier.isPublic(method.getModifiers())){
                    editor.putString("timer_settimeout_method", method.getName());
                    break;
                }
            }
            for(final Field timerClassField : timerClass.getDeclaredFields()){
                if(Modifier.isFinal(timerClassField.getModifiers()) && Modifier.isPublic(timerClassField.getModifiers())){
                    final Class<?> alarmClass = timerClassField.getType();
                    final Boolean[] isFinish = {false};
                    Constructor alarmClassConstructor = null;
                    for (Constructor constructor: alarmClass.getConstructors()) {
                        Class[] pts = constructor.getParameterTypes();
                        if (alarmClassConstructor == null || pts.length > alarmClassConstructor.getParameterCount()) {
                            if (pts[0] == Context.class && pts[1] == int.class && pts[2] == String.class)
                                alarmClassConstructor = constructor;
                        }
                    }
                    if(alarmClassConstructor == null) throw new Throwable("Constructor not found");
                    XposedBridge.hookMethod(alarmClassConstructor, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) {
                            if(!isFinish[0]){
                                for(Field field : alarmClass.getDeclaredFields()){
                                    if(field.getType() == String.class && Modifier.isFinal(field.getModifiers()) && Modifier.isPrivate(field.getModifiers())){
                                        if(param.args[2] != null && XposedHelpers.getObjectField(param.thisObject, field.getName()) == param.args[2]){
                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            editor.putString("timer_alarm_type_property", timerClassField.getName() + "." + field.getName());
                                            editor.putBoolean("enable", true);
                                            editor.apply();
                                            isFinish[0] = true;
                                            printLog("Update h00k position successfully", true);
                                            sendNotification("Autoupdate configuration file succeeded");
                                            startHook();
                                            return;
                                        }
                                    }
                                }
                                printLog("Automatically searching for h00k points failed: target method not found", true);
                            }
                        }
                    });
                    break;
                }
            }
        }catch (Throwable e){
            editor.putBoolean("enable", false);
            printLog("Autofind h00k point failed"+e.getMessage(), true);
            this.sendNotification("Autoupdate configuration file failed", "Failed to find h00k point, disable reconnection repair and fix heartbeat functions.");
            e.printStackTrace();
        }
        editor.apply();
    }

    private void addButton(){
        XposedHelpers.findAndHookMethod("com.google.android.gms.gcm.GcmChimeraDiagnostics", loadPackageParam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @SuppressLint("SetTextI18n")
            @Override
            protected void afterHookedMethod(final MethodHookParam param) {
                ViewGroup viewGroup = ((Window)XposedHelpers.callMethod(param.thisObject, "getWindow")).getDecorView().findViewById(android.R.id.content);
                LinearLayout linearLayout = (LinearLayout)viewGroup.getChildAt(0);
                LinearLayout linearLayout2 = (LinearLayout)linearLayout.getChildAt(0);

                Button reConnectButton = new Button((ContextWrapper)param.thisObject);
                reConnectButton.setText("RECONNECT");
                reConnectButton.setOnClickListener(view -> {
                    context.sendBroadcast(new Intent("com.google.android.intent.action.GCM_RECONNECT"));
                    printLog("Send broadcast GCM_RECONNECT", true);
                });
                linearLayout2.addView(reConnectButton);

                Button openFcmFixButton = new Button((ContextWrapper)param.thisObject);
                openFcmFixButton.setText("Open fcmfix");
                openFcmFixButton.setOnClickListener(view -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setPackage("com.kooritea.fcmfix");
                    intent.setComponent(new ComponentName("com.kooritea.fcmfix","com.kooritea.fcmfix.MainActivity"));
                    context.startActivity(intent);
                });
                linearLayout2.addView(openFcmFixButton);
            }
        });
    }
}
