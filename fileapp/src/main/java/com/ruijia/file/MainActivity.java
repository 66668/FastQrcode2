package com.ruijia.file;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import com.ruijia.file.listener.OnServiceAndActListener;
import com.ruijia.file.service.QRXmitService;

public class MainActivity extends AppCompatActivity {
    TextView tv_path;

    //service相关
    ServiceConnection conn;
    QRXmitService myService = null;
    QRXmitService.FileAIDLServiceBinder myBinder = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_path = findViewById(R.id.tv_path);
        tv_path.setText("获取路径 file=null");
        initService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (conn != null) {
            unbindService(conn);
        }
    }

    //===============================act绑定service===============================
    private void initService() {
        conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                myBinder = (QRXmitService.FileAIDLServiceBinder) service;
                myService = myBinder.geSerVice();
                //绑定监听
                myService.setListener(myListener);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        //act绑定service
        Intent intent = new Intent(MainActivity.this, QRXmitService.class);
        bindService(intent, conn, BIND_AUTO_CREATE);//开启服务
    }

    /**
     * service的回调
     */
    private OnServiceAndActListener myListener = new OnServiceAndActListener() {
        @Override
        public void onQrRecv(String selectPath) {
            Log.d("SJY", "MianAct获取路径 file=" + selectPath);
            if (TextUtils.isEmpty(selectPath)) {
                tv_path.setText("已获取路径 ,但格式不正确无法转成文件");
            } else {
                tv_path.setText("获取路径 file=" + selectPath);
            }

            //拿到路径后，测试B软件实现其他业务需要，需要开发方自己实现自己的业务即可。
        }
    };

}
