package com.alex.mobilenotification;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.alex.mobilenotification.services.MessagingService;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.sql.Timestamp;


public class MainActivity extends AppCompatActivity implements MqttCallback {

    private final String TAG = "MainActivity.class";
    private MqttAndroidClient client;

    private TextView titleTV;
    private TextView messageTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "发送消息", Snackbar.LENGTH_LONG)
                        .setAction("Action", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                publisher();
                            }
                        }).show();
            }
        });



        //启动Service
        Intent startIntent = new Intent(this, MessagingService.class);
        startService(startIntent);
    }

    public void onStart()
    {
        Log.d(TAG, "onStart() executed");
        super.onStart();
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        String title = "";
        String message = "";
        if (null != bundle) {
            title = bundle.getString("title");
            message = bundle.getString("message");
            titleTV = (TextView)findViewById(R.id.title);
            titleTV.setText(title);
            messageTV = (TextView)findViewById(R.id.message);
            messageTV.setText(message);
        }
        Log.d(TAG, "onStart() title = " + title + " and message = " + message);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.subscribe) {
            //启动Service
            Intent startIntent = new Intent(this, MessagingService.class);
            startService(startIntent);
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * 获取IMEI号，IESI号，手机型号
     */
    private String getInfo() {
        Log.i(TAG, "MainActivity getInfo()");
        TelephonyManager mTm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String imei = mTm.getDeviceId();
        String imsi = mTm.getSubscriberId();
        String mtype = Build.MODEL; // 手机型号
        String mtyb = Build.BRAND;//手机品牌

        Log.i("text", "手机IMEI号: " + imei + " 手机IESI号: " + imsi + "手机型号: " + mtype + "手机品牌: " + mtyb);
        return imei;
    }

    /**
     * 发布消息
     */
    private void publisher() {
        Log.i(TAG, "MQTT Start");
        MemoryPersistence memPer = new MemoryPersistence();
        String clientId = getInfo() + "_publisher";

        client = new MqttAndroidClient(this, Config.BROKER_URI, clientId, memPer);
        client.setCallback(this);

        try {
            client.connect(null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken mqttToken) {
                    Log.i(TAG, "Client connected");
                    Log.i(TAG, "Topics=" + mqttToken.getTopics());

                    MqttMessage message = new MqttMessage("Hello, I am Android Mqtt Client.".getBytes());
                    message.setQos(Config.qos);
                    message.setRetained(false);

                    try {
                        client.publish(Config.pub_topic, message);
                        Log.i(TAG, "Message published");
                        client.disconnect();
                        Log.i(TAG, "client disconnected");
                    } catch (MqttPersistenceException e) {
                        e.printStackTrace();
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onFailure(IMqttToken arg0, Throwable arg1) {
                    Log.i(TAG, "Client connection failed: " + arg1.getMessage());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        //对于publisher没有什么用，似乎，因为发送之前建立连接，发送完了关闭连接
        Log.d(TAG, "in MainActivity connectionLost");
    }

    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        String time = new Timestamp(System.currentTimeMillis()).toString();
        String message = "Time:\t" +time +
                "  Topic:\t" + s +
                "  Message:\t" + new String(mqttMessage.getPayload()) +
                "  QoS:\t" + mqttMessage.getQos();
        Log.d(TAG, message);

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d(TAG, "publisher deliveryComplete");
    }
}
