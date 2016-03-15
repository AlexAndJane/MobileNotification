package com.alex.mobilenotification;

import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Created by Alex on 16/3/12.
 */
public class Config {
    final public static String BROKER_URI = "tcp://192.168.0.106:1883";
    final public static String pub_topic = "Sample/Java/v3";
    final public static String topic = "Sample/#";
    final public static int qos = 2;


}
