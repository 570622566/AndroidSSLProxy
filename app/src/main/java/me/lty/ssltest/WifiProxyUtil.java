package me.lty.ssltest;

import android.content.Context;
import android.net.ProxyInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class WifiProxyUtil {

    private static WifiProxyUtil mInstance;
    private ProxyInfo mInfo;
    private WifiManager mWifiManager;

    private WifiProxyUtil(Context context) {
        Context applicationContext = context.getApplicationContext();
        mWifiManager = (WifiManager) applicationContext.getSystemService(Context.WIFI_SERVICE);
    }

    public static WifiProxyUtil getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new WifiProxyUtil(context);
        }
        return mInstance;
    }

    /**
     * 设置公共成员常量值
     *
     * @param obj
     * @param value
     * @param name
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    private static void setEnumField(Object obj, String value, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException,
            IllegalAccessException {

        Field f = obj.getClass().getField(name);
        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
    }


    /**
     * getField只能获取类的public 字段.
     *
     * @param obj
     * @param name
     * @return
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    private static Object getFieldObject(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException,
            IllegalAccessException {
        Field f = obj.getClass().getField(name);
        Object out = f.get(obj);
        return out;
    }

    /**
     * @param obj
     * @param name
     * @return
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    private static Object getDeclaredFieldObject(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException,
            IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    /**
     * @param obj
     * @param name
     * @param object
     */
    private static void setDeclaredFildObject(Object obj, String name, Object object) {
        Field f = null;
        try {
            f = obj.getClass().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        f.setAccessible(true);
        try {
            f.set(obj, object);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前的Wifi连接
     *
     * @return
     */
    private WifiConfiguration getCurrentWifiConfiguration() {
        if (!mWifiManager.isWifiEnabled())
            return null;
        List<WifiConfiguration> configurationList = mWifiManager.getConfiguredNetworks();
        WifiConfiguration configuration = null;
        int cur = mWifiManager.getConnectionInfo().getNetworkId();
        // Log.d("当前wifi连接信息",mWifiManager.getConnectionInfo().toString());
        for (int i = 0; i < configurationList.size(); ++i) {
            WifiConfiguration wifiConfiguration = configurationList.get(i);
            if (wifiConfiguration.networkId == cur)
                configuration = wifiConfiguration;
        }
        return configuration;
    }

    // API 17 可以用
    // 其它可以用的版本需要再测试和处理
    // @exclList 那些不走代理， 没有传null,多个数据以逗号隔开
    public void setWifiProxySettingsFor17And(String host, int port, String exclList) {

        WifiConfiguration config;

        config = getCurrentWifiConfiguration();
        if (config == null) return;

        try {
            //get the link properties from the wifi configuration
            Object linkProperties = getFieldObject(config, "linkProperties");
            if (null == linkProperties) return;

            //获取类 LinkProperties的setHttpProxy方法
            Class<?> proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
            Class<?>[] setHttpProxyParams = new Class[1];
            setHttpProxyParams[0] = proxyPropertiesClass;
            Class<?> lpClass = Class.forName("android.net.LinkProperties");

            Method setHttpProxy = lpClass.getDeclaredMethod("setHttpProxy", setHttpProxyParams);
            setHttpProxy.setAccessible(true);


            // 获取类 ProxyProperties的构造函数
            Constructor<?> proxyPropertiesCtor = proxyPropertiesClass.getConstructor(
                    String.class,
                    int.class,
                    String.class
            );
            // 实例化类ProxyProperties
            Object proxySettings = proxyPropertiesCtor.newInstance(host, port, exclList);


            //pass the new object to setHttpProxy

            Object[] params = new Object[1];
            params[0] = proxySettings;
            setHttpProxy.invoke(linkProperties, params);
            setEnumField(config, "STATIC", "proxySettings");

            //save the settings
            mWifiManager.updateNetwork(config);
            mWifiManager.disconnect();
            mWifiManager.reconnect();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mWifiManager = null;
        }
    }

    // 取消代理设置
    public void unsetWifiProxySettingsFor17And() {
        WifiConfiguration config = getCurrentWifiConfiguration();
        if (null == config) {
            return;
        }

        try {
            //get the link properties from the wifi configuration
            Object linkProperties = getFieldObject(config, "linkProperties");
            if (null == linkProperties) return;
            //get the setHttpProxy method for LinkProperties

            Class<?> proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
            Class<?>[] setHttpProxyParams = new Class[1];
            setHttpProxyParams[0] = proxyPropertiesClass;

            Class<?> lpClass = Class.forName("android.net.LinkProperties");
            Method setHttpProxy = lpClass.getDeclaredMethod("setHttpProxy", setHttpProxyParams);
            setHttpProxy.setAccessible(true);

            //pass null as the proxy
            Object[] params = new Object[1];

            params[0] = null;
            setHttpProxy.invoke(linkProperties, params);
            setEnumField(config, "NONE", "proxySettings");

            //save the config
            mWifiManager.updateNetwork(config);

            mWifiManager.disconnect();
            mWifiManager.reconnect();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mWifiManager = null;
        }
    }

    /**
     * API 21设置代理
     * android.net.IpConfiguration.ProxySettings
     * {@hide}
     */
    public final void setHttpProxySystemProperty(String host, String port, String exclList) {
        WifiConfiguration config = getCurrentWifiConfiguration();

        if (exclList != null) {
            exclList = exclList.replace(",", "|");
        }

        Log.d("代理信息：", "setHttpProxySystemProperty :" + host + ":" + port + " - " + exclList);

        if (host != null) {
            String syshost = System.setProperty("http.proxyHost", host);
            String syshost1 = System.setProperty("https.proxyHost", host);
        } else {
            System.clearProperty("http.proxyHost");
            System.clearProperty("https.proxyHost");
        }
        if (port != null) {
            System.setProperty("http.proxyPort", port);
            System.setProperty("https.proxyPort", port);
        } else {
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyPort");
        }
        if (exclList != null) {
            System.setProperty("http.nonProxyHosts", exclList);
            System.setProperty("https.nonProxyHosts", exclList);
        } else {
            System.clearProperty("http.nonProxyHosts");
            System.clearProperty("https.nonProxyHosts");
        }
       /* if (!Uri.EMPTY.equals(pacFileUrl)) {
            ProxySelector.setDefault(new PacProxySelector());
        } else {
            ProxySelector.setDefault(sDefaultProxySelector);
        }*/


        mWifiManager.updateNetwork(config);

        mWifiManager.disconnect();
        mWifiManager.reconnect();
    }


    /**
     * 设置代理信息 exclList是添加不用代理的网址用的
     */
    public void setHttpPorxySetting(String host, int port, List<String> exclList)
            throws Exception {
        WifiConfiguration config = getCurrentWifiConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mInfo = ProxyInfo.buildDirectProxy(host, port);
        }
        if (config != null) {
            Class clazz = Class.forName("android.net.wifi.WifiConfiguration");
            Class parmars = Class.forName("android.net.ProxyInfo");
            Method method = clazz.getMethod("setHttpProxy", parmars);
            method.invoke(config, mInfo);
            Object mIpConfiguration = getDeclaredFieldObject(config, "mIpConfiguration");

            setEnumField(mIpConfiguration, "STATIC", "proxySettings");
            setDeclaredFildObject(config, "mIpConfiguration", mIpConfiguration);
            //save the settings
            mWifiManager.updateNetwork(config);
            mWifiManager.disconnect();
            mWifiManager.reconnect();
        }

    }

    /**
     * 取消代理设置
     */
    public void unSetHttpProxy() throws Exception {
        WifiConfiguration configuration = getCurrentWifiConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mInfo = ProxyInfo.buildDirectProxy(null, 0);
        }
        if (configuration != null) {
            Class clazz = Class.forName("android.net.wifi.WifiConfiguration");
            Class parmars = Class.forName("android.net.ProxyInfo");
            Method method = clazz.getMethod("setHttpProxy", parmars);
            method.invoke(configuration, mInfo);

            Object mIpConfiguration = getDeclaredFieldObject(configuration, "mIpConfiguration");
            setEnumField(mIpConfiguration, "NONE", "proxySettings");
            setDeclaredFildObject(configuration, "mIpConfiguration", mIpConfiguration);

            //save the settings
            mWifiManager.updateNetwork(configuration);
            mWifiManager.disconnect();
            mWifiManager.reconnect();
        }
    }
}