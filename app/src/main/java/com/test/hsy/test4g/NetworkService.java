package com.test.hsy.test4g;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

/**
 * Created by hsy on 18-7-24.
 */

public class NetworkService extends Service {
    public static final String TAG = "NetworkService";

    public static final int MODE_NULL = -1;
    public static final int MODE_SERVER = 0;
    public static final int MODE_CLIENT = 1;
    public final static int MESSAGE_FROM_NetworkService_Receive = 1;
    public final static int MESSAGE_FROM_NetworkService_NetworkInfo = 2;
    private int mode = MODE_NULL;

    private Context context;
    private IBinder binder;
    private Handler serviceHandler;
    //    public Handler setServiceHandler;
    public Handler sendHandler;
    public static boolean SERVICE_CONNECTED = false;
    public static boolean COMMUNICATION_CONNECTED = false;

    private NetworkInfo networkInfo;
    private String ServerIP = "";//服务器端IP
    private String LocalIP = "";//本机IP
    private int PORT = 9876;
    private Socket socket;

    @Override
    public void onCreate() {
        super.onCreate();
        binder = new serviceBinder();
        context = this;
//        setServiceHandler = new Handler() {
//            public void handleMessage(Message msg) {
//                if (mode == MODE_CLIENT)
//                    LocalIP = (String) msg.obj;
//            }
//        };
        SERVICE_CONNECTED = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SERVICE_CONNECTED = false;
    }

    private void checkNetwork() {

        Log.d(TAG, "initNetwork:" + " started.");
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        networkInfo = connectivityManager.getActiveNetworkInfo();
        if (null != networkInfo) {
            switch (networkInfo.getType()) {
                case ConnectivityManager.TYPE_MOBILE:
                    Log.d(TAG, "initNetwork:" + " Mobile.");
                    try {
                        if (networkInfo.isAvailable() && networkInfo.isConnected()) {
                            //Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
                            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                                NetworkInterface intf = en.nextElement();
                                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                                    InetAddress inetAddress = enumIpAddr.nextElement();
                                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                        LocalIP = inetAddress.getHostAddress();
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "initNetwork:" + " Exception.");
                        e.printStackTrace();
                    }
                    break;
                case ConnectivityManager.TYPE_WIFI:
                    Log.d(TAG, "initNetwork:" + " Wifi.");
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
//                            ServerIP = formatIpAddress(wifiManager.getDhcpInfo().serverAddress);//转换IP地址
                    LocalIP = formatIpAddress(wifiManager.getDhcpInfo().ipAddress);
                    break;
            }

        }
    }

    public boolean initAsServer(final int targetPORT) {
        checkNetwork();
        mode = MODE_SERVER;
        this.PORT = targetPORT;
        this.ServerIP = this.LocalIP;
        socket = new Socket();
        updateNetworkInfo();
        try {
            final ServerSocket server = new ServerSocket(PORT);
            Thread serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Log.d(TAG, "initAsServer:" + "serverThread is running.");
                            serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Waiting connect...").sendToTarget();
                            socket = server.accept();
                            if (socket != null) {
                                Log.d(TAG, "initAsServer:" + "socket connection is ready.");
                                serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo,
                                        "Connection is ready. Client IP: ").sendToTarget();

                            }
                            while (!socket.isClosed() && socket.isConnected()) {
                                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                                Message msg = (Message) objectInputStream.readObject();
                                serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_Receive, msg).sendToTarget();
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "initAsServer:" + e);
                            serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo,
                                    "Connection failed. ").sendToTarget();
                        }
                    }
                }
            });
            serverThread.start();
        } catch (Exception e) {
            Log.d(TAG, "initAsServer:" + e);
            e.printStackTrace();
            disConnect();
        }
        return true;
    }

    public boolean initAsClient(String targetIP, final int targetPORT) {
        checkNetwork();
        mode = MODE_CLIENT;
        this.ServerIP = targetIP;
        this.PORT = targetPORT;
        final int timeout = 3000;
        socket = new Socket();
        updateNetworkInfo();
        Thread connectThread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Try to connect server...").sendToTarget();
                            socket.connect(new InetSocketAddress(ServerIP, PORT), timeout);

                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.d(TAG, "initAsClient:" + e);
                            serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Connect server Exception: " + e).sendToTarget();
                            disConnect();
                        }
                    }
                }
        );
        try {
            connectThread.start();
            connectThread.join(timeout + 1000);
            if (!socket.isClosed() && socket.isConnected()) {
                COMMUNICATION_CONNECTED = true;
                serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Connection is ready.").sendToTarget();
                Log.d(TAG, "initAsClient:" + "connection is ready.");
                runSendThread();
            } else {
                Log.d(TAG, "initAsClient:" + "connection is not ready.");
                serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Connect server Error. ").sendToTarget();
                disConnect();
            }
        } catch (Exception e) {
            Log.d(TAG, "initAsClient:" + "connectThread failed");
            e.printStackTrace();
            disConnect();
        }
        return COMMUNICATION_CONNECTED;
    }

    private void runSendThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //建立消息循环的步骤
                Looper.prepare();//1、初始化Looper
                sendHandler = new Handler() {//2、绑定handler到CustomThread实例的Looper对象
                    public void handleMessage(Message msg) {//3、定义处理消息的方法
                        if (!socket.isClosed() && socket.isConnected())
                            try {
//                                sendPackage(msg.obj);
                                ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
                                os.writeObject(msg);
                                socket.getOutputStream().flush();
                            } catch (Exception e) {
                                Log.e("runSendClient", "sendPackage(wifiPackage)");
//                                    e.printStackTrace();
                            }

                    }
                };
                Looper.loop();//4、启动消息循环
            }
        }).start();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    if (!socket.isClosed() && socket.isConnected())
//                        sendPackage(wifiPackage);
//                } catch (Exception e) {
//                    Log.d("runSendClient", "sendPackage(wifiPackage)");
////                    e.printStackTrace();
//                }
//            }
//        }).start();
    }

    public void runReceiveThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!socket.isClosed() && socket.isConnected()) {
                        ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                        Message msg = (Message) objectInputStream.readObject();
                        serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_Receive, msg).sendToTarget();

//                        receiveMessage_package();
                    }
                } catch (Exception e) {
                    COMMUNICATION_CONNECTED = false;
                    Log.e("runReceiveThread", "receiveMessage");
//                    context.sendBroadcast(new Intent(ACTION_FROM_WIFISERVICE_SERVER_CONNECTION_LOST));
//                    e.printStackTrace();
                }

            }
        }).start();
    }

    public int getMode() {
        return mode;
    }

    public void updateNetworkInfo() {
        String modeName = "Null";
        switch (mode) {
            case MODE_CLIENT:
                modeName = "Client";
                break;
            case MODE_SERVER:
                modeName = "Server";
                break;
            case MODE_NULL:
                modeName = "Null";
                break;
        }
        serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Mode : " + modeName).sendToTarget();
        serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Network Type : " + networkInfo.getTypeName()).sendToTarget();
        serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Local IP : " + LocalIP).sendToTarget();
        serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "Server IP : " + ServerIP).sendToTarget();
        serviceHandler.obtainMessage(MESSAGE_FROM_NetworkService_NetworkInfo, "PORT : " + PORT).sendToTarget();
    }

    private void disConnect() {
        mode = MODE_NULL;
        COMMUNICATION_CONNECTED = false;
        socket = null;
    }

    //转换IP地址的函数
    private String formatIpAddress(int ipAddress) {
        return (ipAddress & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                (ipAddress >> 24 & 0xFF);
    }

    public class serviceBinder extends Binder {
        public NetworkService getService() {
            return NetworkService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setHandler(Handler serviceHandler) {
        this.serviceHandler = serviceHandler;
    }
}
