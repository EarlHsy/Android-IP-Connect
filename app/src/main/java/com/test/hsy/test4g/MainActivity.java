package com.test.hsy.test4g;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    //region UI相关
    private TextView receive;
    private TextView send;
    private TextView networkInfo;
    private Button asServer;
    private Button asClient;
    private Button sendData;
    private EditText IP;
    private EditText PORT;
    private EditText data;
    //endregion

    private NetworkService networkService;
    private NetworkHandler networkHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        networkHandler = new NetworkHandler(this);
        initUI();
    }

    private void initUI() {
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        receive = (TextView) findViewById(R.id.receive);
        send = (TextView) findViewById(R.id.send);
        networkInfo = (TextView) findViewById(R.id.networkInfo);
        asServer = (Button) findViewById(R.id.asServer);
        asClient = (Button) findViewById(R.id.asClient);
        sendData = (Button) findViewById(R.id.sendData);
        IP = (EditText) findViewById(R.id.IP);
        PORT = (EditText) findViewById(R.id.PORT);
        data = (EditText) findViewById(R.id.data);

        receive.setMovementMethod(ScrollingMovementMethod.getInstance());
        send.setMovementMethod(ScrollingMovementMethod.getInstance());
        networkInfo.setMovementMethod(ScrollingMovementMethod.getInstance());

        asServer.setOnClickListener(buttonClickListeners);
        asClient.setOnClickListener(buttonClickListeners);
        sendData.setOnClickListener(buttonClickListeners);
    }

    @Override
    public void onResume() {
        super.onResume();
        startService(NetworkService.class, networkConnection, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(networkConnection);
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
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private View.OnClickListener buttonClickListeners = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            //region Listeners
            switch (v.getId()) {
                case R.id.asServer:
                    if (networkService != null) {
                        if (NetworkService.SERVICE_CONNECTED
                                && networkService.getMode() == NetworkService.MODE_NULL) {
                            networkInfo.setText("");
                            networkService.initAsServer(Integer.parseInt(PORT.getText().toString()));
                        }
                    } else {
                        Log.d("Main", "NetworkService is null.");
                        networkInfo.append("Network Error! \n");
                    }
                    break;
                case R.id.asClient:
                    if (networkService != null) {
                        if (NetworkService.SERVICE_CONNECTED
                                && networkService.getMode() == NetworkService.MODE_NULL) {
//                            if (NetworkService != null) {
//                                Message msg = new Message();
//                                msg.obj = IP.getText().toString();
//                                NetworkService.setServiceHandler.sendMessage(msg);//把数据包发送给发送客户端子线程，让其发送出去
//                            }
                            networkService.initAsClient(IP.getText().toString(), Integer.parseInt(PORT.getText().toString()));
                        }
                    } else {
                        Log.d("Main", "NetworkService is null.");
                    }
                    break;
                case R.id.sendData:
                    if (networkService != null) {
                        Message msg = new Message();
                        msg.obj = data.getText().toString();
//                        NetworkService.setServiceHandler.sendMessage(msg);
                    } else {
                        Log.d("Main", "NetworkService is null.");
                    }
                    break;
            }
            //endregions
        }
    };

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!NetworkService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection networkConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            networkService = ((NetworkService.serviceBinder) arg1).getService();
            networkService.setHandler(networkHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            networkService = null;
        }
    };

    private static class NetworkHandler extends Handler {
        private final String TAG = "NetworkHandler";
        private final WeakReference<MainActivity> mActivity;

        private NetworkHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NetworkService.MESSAGE_FROM_NetworkService_Receive:
                    mActivity.get().receive.append(msg.obj.toString() + '\n');
                    break;
                case NetworkService.MESSAGE_FROM_NetworkService_NetworkInfo:
                {
                    mActivity.get().networkInfo.append(msg.obj.toString() + '\n');
                    break;
                }
            }
        }
    }
}
