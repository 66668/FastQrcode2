package com.ruijia.file.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.ruijia.file.FileAidlInterface;
import com.ruijia.file.MyApplication;
import com.ruijia.file.listener.OnServiceAndActListener;
import com.ruijia.file.utils.CheckUtils;

import java.io.File;

/**
 * 服务端
 * <p>
 * 链路层与测试b通讯的service（单向通讯，链路层是客户端--->测试b是服务端）
 */
public class QRXmitService extends Service {
    private String filepath;
    private OnServiceAndActListener listener;
    private Handler handler;

    /**
     * 客户端开启连接后，自动执行
     */
    public QRXmitService() {
        handler = new Handler();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new FileAIDLServiceBinder();
    }

    //---------------------------------------------AIDL接口实现--------------------------------------------

    public class FileAIDLServiceBinder extends FileAidlInterface.Stub {
        
        //act与service交互使用
        public QRXmitService geSerVice() {
            return QRXmitService.this;
        }

        //拿到客户端信息，测试b使用该数据。
        @Override
        public boolean QRRecv(String filePath) throws RemoteException {
            filepath = filePath;
            File file = new File(filepath);
            if (file == null || file.length() <= 0) {
                return false;
            } else {
                Log.d("SJY", "测试bAPP拿到传输文件 file=" + filepath);
                startToAct();
                return true;
            }
        }
    }

    //===========================================================================================================================
    //=================================以下为同一进程下，act与service的交互：包括service回调act,act回调service===================================
    //===========================================================================================================================

    /**
     *
     */
    private void startToAct() {
//        if (checkActAlive() && isActFrontShow()) {
        if (listener != null) {
            listener.onQrRecv(filepath);
        }
//        } else {
//            Log.d("SJY", "MainAct不在前台，正在开启");
//            startApp();
//        }
    }

    /**
     * 设置回调
     *
     * @param listener
     */
    public void setListener(OnServiceAndActListener listener) {
        this.listener = listener;
    }

    private boolean checkActAlive() {
        return CheckUtils.isActivityAlive(MyApplication.getInstance(), "com.ruijia.file", "MainActivity");
    }

    private boolean isActFrontShow() {
        return CheckUtils.isActFrontShow(MyApplication.getInstance(), "com.ruijia.file.MainActivity");
    }

    /**
     * 由service调起app的act界面
     * 由于intent传值 不能传大数据，所以使用接口回调方式。
     */
    private void startApp() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                //启动应用，参数为需要自动启动的应用的包名
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.ruijia.qrcode");
                startActivity(launchIntent);
            }
        });
    }

}