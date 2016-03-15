package com.alex.mobilenotification.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.alex.mobilenotification.Config;
import com.alex.mobilenotification.MainActivity;
import com.alex.mobilenotification.R;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.sql.Timestamp;

/**
 * 后台Service用于接收消息
 */
public class MessagingService extends Service implements MqttCallback {
    private static final String TAG = "MessagingService";
    MqttClient mMqttClient;
    String deviceId;
    boolean threadDisable;
    int count;

    public MessagingService() {

    }

    /**
     * 获取设备imei作为MQTT客户端的ClientId
     * @return
     */
    private String getClientId()
    {
        Log.i(TAG, "MessagingService getClientId()");
//        TelephonyManager mTm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        TelephonyManager mTm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String imei = mTm.getDeviceId();
        String imsi = mTm.getSubscriberId();
//        String mtype = Build.MODEL; // 手机型号
//        String mtyb = Build.BRAND;//手机品牌
        Log.i("text", "手机IMEI号: " + imei + " 手机IESI号: " + imsi);

        deviceId = imei;
        if (null == deviceId) {
            deviceId = MqttAsyncClient.generateClientId();
        }

        return deviceId;
    }

    public void onCreate()
    {
        Log.d(TAG, "onCreate() executed mMqttClient = " + mMqttClient);
        super.onCreate();
//        count();
        if (null == mMqttClient) {
            subscriber();
        } else if (null != mMqttClient && !mMqttClient.isConnected()) {
            Log.d(TAG, "onCreate() executed mMqttClient.isConnected() = " + mMqttClient.isConnected());
            subscriber();
        }

    }

    public void onDestroy()
    {
        Log.d(TAG, "onDestroy() executed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() executed mMqttClient.isConnected() = " + mMqttClient.isConnected());
        if (!mMqttClient.isConnected()) {
            Log.d(TAG, "onStartCommand() executed 重新建立连接");
            subscriber();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 测试Service在后台是否在运行
     */
    private void count()
    {
        threadDisable = false;

        /** 创建一个线程，每秒计数器加一，并在控制台进行Log输出 */
        new Thread(new Runnable() {
            public void run() {
                while (!threadDisable) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {

                    }
                    count++;

                    if (count == 1000000) {
                        threadDisable = true;
                    }

                    if (count % 10 == 0) {
                        Log.d(TAG, "count % 10 == 0 and count = " + count);
                        //pushNotification();
                    }
                    Log.d(TAG, "Count is" + count);
                }
            }
        }).start();
    }

    /**
     * 生成通知
     * @param title 通知标题
     * @param message 通知信息
     */
    private void pushNotification(String title, String message)
    {
        Log.d(TAG, "pushNotification is done title = " + title + " and message = " + message);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setSmallIcon(R.drawable.notification_icon);
        mBuilder.setVibrate(new long[]{1000, 1000});
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mBuilder.setSound(alarmSound);
        mBuilder.setContentTitle(title);
        mBuilder.setContentText(message);

        //增加通知点击事件，传值给目标Activity
        Intent resultIntent = new Intent(this, MainActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString("title", title);
        bundle.putString("message", message);
        resultIntent.putExtras(bundle);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);

        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // notificationID allows you to update the notification later on.
        int notificationID = (int) System.currentTimeMillis();
        mNotificationManager.notify(notificationID, mBuilder.build());

    }

    /**
     * 订阅消息
     */
    private void subscriber() {
        Log.d(TAG, "subscribe.........");
        try {
            mMqttClient = new MqttClient(Config.BROKER_URI, getClientId(),
                    null);
            MqttConnectOptions conOptions = new MqttConnectOptions();
            conOptions.setCleanSession(false);
            mMqttClient.setCallback(this);
            mMqttClient.connect(conOptions);
            mMqttClient.subscribe(Config.topic, Config.qos);

        } catch (MqttException e) {
            e.printStackTrace();
            Log.d(TAG, "Subscribe 建立连接失败");
            String title = "连接丢失";
            String message = "连接丢失，请点击右上角Subscribe重新连接！";
            pushNotification(title, message);
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        //对subscriber连接失败时的处理
        Log.d(TAG, "MessagingService connectionLost");
        String title = "连接丢失";
        String message = "连接丢失，请点击右上角Subscribe重新连接！";
        pushNotification(title, message);
    }

    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        String time = new Timestamp(System.currentTimeMillis()).toString();
//        String message = "Time:\t" +time +
//                "  Topic:\t" + s +
//                "  Message:\t" + new String(mqttMessage.getPayload()) +
//                "  QoS:\t" + mqttMessage.getQos();

        String message = "Time:" +time + "" + new String(mqttMessage.getPayload());
        System.out.println(message);
        pushNotification("Message Title", message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d(TAG, "publisher deliveryComplete");
    }
}
