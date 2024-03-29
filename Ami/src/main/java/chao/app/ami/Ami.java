package chao.app.ami;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import chao.app.ami.base.AmiHandlerThread;
import chao.app.ami.launcher.drawer.DrawerManager;
import chao.app.ami.plugin.plugins.frame.FrameManager;
import chao.app.ami.proxy.ProxyManager;
import chao.app.ami.text.TextManager;
import chao.app.ami.utils.Util;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author chao.qin
 * @since 2017/7/27
 *
 * 调试工具
 *
 */

public class Ami {

    public static final int DEBUG_MODE_ENABLED = 1;

    public static final int DEBUG_MODE_DISABLED = -1;

    public static final int DEBUG_MODE_UNSET = 0;

    private static final String TAG = "AMI";
    private static Application mApp;
    private static Ami mInstance;

    private static AmiHandlerThread sHandlerThread;

    private static int mDebugMode = DEBUG_MODE_UNSET;

    public static final int LIFECYCLE_LEVEL_FULL = 3;
    public static final int LIFECYCLE_LEVEL_SIMPLE = 2;
    public static final int LIFECYCLE_LEVEL_CREATE = 1;
    public static final int LIFECYCLE_LEVEL_NONE = 0;

    private static int mLifecycle = LIFECYCLE_LEVEL_NONE;

    private Ami() {
    }

    private static boolean isDebugMode(Application app) {
        if (mDebugMode == DEBUG_MODE_UNSET) {
            boolean enabled = Util.isApkDebugable(app);
            mDebugMode = enabled ? DEBUG_MODE_ENABLED: DEBUG_MODE_DISABLED;
        }
        return mDebugMode == DEBUG_MODE_ENABLED;
    }

    public static void setDebugMode(int debugMode) {
        mDebugMode = debugMode;
    }

    public static void init(Application app) {
        mApp = app;
        if (!isDebugMode(app)) {
            return;
        }
        if (mInstance != null) {
            return;
        }

        //只在主进程初始化
        if (!Util.isMainProcess(app)) {
            return;
        }

        mInstance = new Ami();
//        ProxyManager.init(app);
//        TextManager.init();
        FrameManager.init();
        DrawerManager.get();
//        MonitorManager.init(app);

        sHandlerThread = new AmiHandlerThread();
        sHandlerThread.start();
    }

    public static boolean inited() {
        return mInstance != null;
    }

    /**
     *
     * @param drawerId 抽屉配置文件Id, 必须是R.raw.xxxx
     */
    public static void setDrawerId(int drawerId) {
        if (!isDebugMode(mApp)) {
            return;
        }
        if (!Util.isMainProcess(getApp())) {
            return;
        }
        DrawerManager.get().setDrawerId(drawerId);
    }

    public static Application getApp() {
        if (mApp == null) {
            return reflectApplication();
        }
        return mApp;
    }

    public static Application reflectApplication() {
        if (mApp == null) {
            synchronized (Ami.class) {
                if (mApp == null) {
                    try {
                        Class<?> ActivityThread = Class.forName("android.app.ActivityThread");

                        Method method = ActivityThread.getMethod("currentActivityThread");
                        Object currentActivityThread = method.invoke(ActivityThread);//获取currentActivityThread 对象

                        Method method2 = currentActivityThread.getClass().getMethod("getApplication");
                        mApp =(Application) method2.invoke(currentActivityThread);//获取 Context对象

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return mApp;
    }

    public static void setLifecycleLevel(int level) {
        mLifecycle = level;
    }

    public static void log(Object log) {
        log(TAG, " >>> " + String.valueOf(log));
    }

    public static void log(String log, Object... args) {
        log(String.format(log, args));
    }

    public static void log() {
        log(TAG, "");
    }

    public static void deepLog(Object object) {
        if(object == null) {
            return;
        }
        Class clazz = object.getClass();
        Field[] fields = clazz.getDeclaredFields();
        StringBuilder builder = new StringBuilder();
        builder.append(clazz.getSimpleName()).append("{");
        try {
            for (Field field: fields) {
                field.setAccessible(true);
                appendLog(object, field, builder);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        builder.append("}");
        Ami.log(builder.toString());
    }

    private static void appendLog(Object parent, Field field, StringBuilder logs) throws IllegalAccessException {

        Object obj = field.get(parent);
        logs.append(field.getName()).append(":");
        if (obj == null) {
            logs.append("null").append(", ");
        } else if (obj instanceof Number) {
            logs.append(obj).append(", ");
        } else if (obj instanceof String) {
            logs.append("\"").append(obj).append("\"").append(", ");
        } else if (obj.getClass() == Object.class) {
        } else if (obj.getClass().isArray()) {
            Object array[] = (Object[]) obj;
            logs.append(Arrays.toString(array)).append(", ");
        } else {
            appendLog(obj, field, logs);
        }
    }


    public static void log(String tag, String log) {

        StackTraceElement[] traces = Thread.currentThread().getStackTrace();
        String className = null;
        String method = null;
        for (StackTraceElement element: traces) {
            String name = element.getClassName();
            if (name.contains("dalvik") || name.contains("java.lang")) {
                continue;
            }
            if (!name.contains(Ami.class.getName())) {
                className = element.getClassName();
                className = className.substring(className.lastIndexOf(".") + 1);
                method = element.getMethodName();
                break;
            }

        }
        Log.d(tag, className + "." + method + "() " + log);
    }

    @Deprecated
    public static void lifecycle(String tag, String log, int level) {
        if (mLifecycle == LIFECYCLE_LEVEL_NONE) {
            return;
        }
        if (level > mLifecycle) {
            return;
        }
        log(tag, log);
    }

    public static AmiHandlerThread getHandlerThread() {
        if (!inited()) {
            return null;
        }
        return sHandlerThread;
    }

    public static Handler getHandler(){
        if (!inited()) {
            return null;
        }
        return sHandlerThread.getHandler();
    }
}
