package com.ruijia.qrcode.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.ruijia.qrcode.Constants;
import com.ruijia.qrcode.QrAIDLInterface;
import com.ruijia.qrcode.QrApplication;
import com.ruijia.qrcode.QrProgressCallback;
import com.ruijia.qrcode.listener.OnServiceAndActListener;
import com.ruijia.qrcode.utils.CheckUtils;
import com.ruijia.qrcode.utils.CodeUtils;
import com.ruijia.qrcode.utils.ConvertUtils;
import com.ruijia.qrcode.utils.IOUtils;
import com.ruijia.qrcode.utils.SPUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * aidl 链路层的服务端service,给aidl客户端提供service接口
 */
public class QRXmitService extends Service {
    public static final String TAG = "SJY";
    private static final int qrSize = 800;//该值和屏幕宽度尺寸相关
    //---------------------------变量-------------------------------
    private Handler handler;

    //---------------------------变量-------------------------------
    private AtomicBoolean isServiceDestory = new AtomicBoolean(false);
    //RemoteCallbackList是专门用于删除跨进程listener的接口，它是一个泛型，支持管理多个回调。
    private RemoteCallbackList<QrProgressCallback> mListener = new RemoteCallbackList<>();
    private String selectPath;//当前传输的文件
    private OnServiceAndActListener listener;//
    private List<String> newDatas = new ArrayList<>();
    private List<Bitmap> maps = new ArrayList<>();
    private int size = 0;//当前文件的list长度
    private long fileSize = 0;//文件大小


    /**
     * 客户端开启连接后，自动执行
     */
    public QRXmitService() {
        handler = new Handler();
        //设置默认发送时间间隔
        SPUtil.putInt(Constants.TIME_INTERVAL, Constants.DEFAULT_TIME);
        //默认文件大小
        SPUtil.putInt(Constants.FILE_SIZE, Constants.DEFAULT_SIZE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        //通过ServiceConnection在activity中拿到Binder对象
        return new QrAIDLServiceBinder();
    }


    //==========================================================================================================================
    //=================================以下为app 进程间的交互，包括客户端调用服务端，服务端回调客户端===================================
    //==========================================================================================================================


    //---------------------------------------------AIDL接口实现--------------------------------------------

    /**
     * 接口方法，由service实现
     */
    public class QrAIDLServiceBinder extends QrAIDLInterface.Stub {

        //act与service交互使用
        public QRXmitService geSerVice() {
            return QRXmitService.this;
        }

        //aidl使用
        @Override
        public void QRSend(String localPath) throws RemoteException {
            srvQrSend(localPath);
        }

        @Override
        public String QRRecv() throws RemoteException {
            return srvQRRecv();
        }

        @Override
        public boolean QrCtrl(int timeInterval, int StrLen) throws RemoteException {
            return srvQRCtrl(timeInterval, StrLen);
        }

        @Override
        public void register(QrProgressCallback listener) throws RemoteException {
            //绑定
            mListener.register(listener);
        }

        @Override
        public void unregister(QrProgressCallback listener) throws RemoteException {
            //解除
            mListener.unregister(listener);
        }

    }


    @Override
    public void onDestroy() {
        isServiceDestory.set(true);
        super.onDestroy();
    }


    //-----------------------《客户端-->服务端》操作（不同进程）----------------------

    /**
     *
     */
    public boolean srvQRCtrl(int timeInterval, int fileSize) {
        Log.d(TAG, "服务端设置参数-QRCtrl--timeInterval=" + timeInterval + "--fileSize=" + fileSize);

        SPUtil.putInt(Constants.TIME_INTERVAL, timeInterval);
        SPUtil.putInt(Constants.FILE_SIZE, fileSize);
        SPUtil.putInt(Constants.CON_TIME_OUT, 15);//TODO

        return (SPUtil.getInt(Constants.TIME_INTERVAL, 0) != 0
                && SPUtil.getInt(Constants.TIME_INTERVAL, 0) != 0
                && SPUtil.getInt(Constants.CON_TIME_OUT, 0) != 0);
    }

    /**
     * 核心方法
     * <p>
     * (1)验证文件是否可以传送
     * <p>
     * (2)文件分解成字符流，在分解成 指定长度的
     * <p>
     * 不管测试app端连续几次触发该方法，都需要将上一次该方法的调用覆盖，数据清空，也包括链路层传输的清空。
     */
    public void srvQrSend(String localPath) {
        Log.d("SJY", "QRXmitService--QrSend-localPath=" + localPath);
        //
        clearLastData();
        //
        //判断文件是否存在
        File file = new File(localPath);
        if (file == null || !file.exists()) {
            isTrans(false, "文件不存在");
        } else {
            //保存时间节点，用于统计传输总耗时
            SPUtil.putLong(Constants.START_TIME, System.currentTimeMillis());
            //
            selectPath = file.getAbsolutePath();
            split2IO(file);
        }
    }


    /**
     *
     */
    public String srvQRRecv() {
        return "请参考回调";
    }

    /**
     * TODO  未做
     * <p>
     * 清空处理
     */
    private void clearLastData() {
        selectPath = "";//当前传输的文件
        OnServiceAndActListener listener;//
        newDatas = new ArrayList<>();
        maps = new ArrayList<>();
        size = 0;//当前文件的list长度
    }


    //-----------------------文件拆分操作，耗时操作----------------------

    /**
     * (1)文件分解成字符流
     *
     * @param file
     */
    private void split2IO(final File file) {
        final int maxSize = SPUtil.getInt(Constants.FILE_SIZE, 0);
        if (maxSize == 0) {
            Log.e("SJY", "service无法使用SharedPreferences");
            return;
        }
        new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... voids) {
                long startTime = System.currentTimeMillis();
                //File转String
                String data = IOUtils.fileToBase64(file);
                long len = data.length();
                long time = System.currentTimeMillis() - startTime;

                //文件长度是否超出最大传输
                boolean isTrans = false;
                if ((len / 1024 / 1024) > maxSize) {
                    isTrans = false;
                } else {
                    isTrans = true;
                }

                //回调客户端
                final int N = mListener.beginBroadcast();
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.splitToIoTime(time, "splitToIoTime");
                        callback.isTrans(isTrans, "splitToIoTime--文件长度=" + len + "B");
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return data;

            }

            @Override
            protected void onPostExecute(String data) {
                super.onPostExecute(data);
                //拿到文件的字符流
                createArray(data);
                fileSize = data.length();//字符流大小
            }
        }.execute();

    }

    /**
     * (2)字符流-->List<String>
     */
    private void createArray(final String data) {
        new AsyncTask<Void, Void, List<String>>() {

            @Override
            protected List<String> doInBackground(Void... voids) {
                long startTime = System.currentTimeMillis();
                //String切割成list
                List<String> orgDatas = IOUtils.stringToArray(data);
                if (orgDatas == null) {
                    return null;
                }
                long time = System.currentTimeMillis() - startTime;
                //
                splitToArrayTime(time, "字符流-->原始List<String>");

                return orgDatas;
            }

            @Override
            protected void onPostExecute(List<String> list) {
                super.onPostExecute(list);
                //拿到原始list,转成bitmap
                if (list == null || list.size() <= 0) {
                    //回调客户端
                    isTrans(false, "createArray--原始数据长度超过qrcode指定长度！");
                    return;
                } else {
                    //判断数据长度是否超过处理能力
                    if (list.size() > 9999999) {
                        isTrans(false, "文件过大，超过链路层最大处理能力");
                        return;
                    } else {
                        createNewArray(list);
                    }
                }
            }
        }.execute();

    }

    /**
     * (3) 原始List转有标记的List数据
     * <p>
     * 说明：String数据段头标记：snd1234567,长度10;尾标记：RJQR,长度4
     * <p>
     * 头标记：
     * <p>
     * snd：长度3：表示发送 长度3
     * <p>
     * 12345:长度7：表示list第几个片段
     * <p>
     * 尾标记：长度4，表示这段数据是否解析正确 RJQR
     *
     * @param orgDatas
     */
    private void createNewArray(final List<String> orgDatas) {
        new AsyncTask<Void, Void, List<String>>() {

            @Override
            protected List<String> doInBackground(Void... voids) {
                List<String> sendDatas = new ArrayList<>();
                long startTime = System.currentTimeMillis();
                try {
                    //添加标记，
                    // 7位的位置标记
                    int size = orgDatas.size();
                    for (int i = 0; i < size; i++) {
                        String pos = ConvertUtils.int2String(i);
                        //拼接数据-->格式：snd(发送标记)+1234567(第几个，从0开始)+数据段
                        sendDatas.add("snd" + pos + orgDatas.get(i) + "RJQR");
                    }
                } catch (Exception e) {
                    isTrans(false, e.toString());
                    e.printStackTrace();
                    return null;
                }

                //回调客户端
                long time = System.currentTimeMillis() - startTime;
                createNewArray(time, "null");
                return sendDatas;
            }

            @Override
            protected void onPostExecute(List<String> list) {
                super.onPostExecute(list);
                //拿到有标记的List,再转qr bitmap
                if (list == null || list.size() <= 0) {
                    //已处理
                    return;
                } else {
                    /**
                     * 集中转qrbitmap，测试发现，线程分成2个最佳，再多也没用
                     */
                    newDatas = list;
                    size = newDatas.size();

                    //调起链路层传输数据
                    serviceStartAct();

                    //转qrbitmap 方式1
//                      createQrBitmap();

//                    方式2:
//                    try {
//                        if (size < 50) {//150KB
//                            createQrBitmap2(newDatas, 1);
//                        } else if (size < 100) {//300KB
//                            createQrBitmap2(newDatas, 1);
//                        } else if (size < 500) {//1.5M左右
//                            createQrBitmap2(newDatas, 1);
//                        } else {//大于1.5M
//                            createQrBitmap2(newDatas, 1);
//                        }
//                        //测试ArrayList的非线程安全
//                        Log.d("SJY", "原数据大小=" + newDatas.size() + "结果大小=" + maps.size());
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        Log.e("SJY", "createQrBitmap异常：" + e.toString());
//                    }
                }

            }
        }.execute();
    }

    /**
     * (4)list转qrbitmap
     * 方式1：list大段直接转，耗时长
     *
     * <p>
     */
    private void createQrBitmap() {

        new AsyncTask<Void, Void, List<Bitmap>>() {

            @Override
            protected List<Bitmap> doInBackground(Void... voids) {
                //
                List<Bitmap> sendImgs = new ArrayList<>();
                long startTime = System.currentTimeMillis();
                //sendDatas 转qrbitmap
                for (int i = 0; i < size; i++) {
                    long start = System.currentTimeMillis();
                    Bitmap bitmap = CodeUtils.createByMultiFormatWriter(newDatas.get(i), qrSize);
                    sendImgs.add(bitmap);
                    //回调客户端
                    long end = System.currentTimeMillis() - start;
                    createQrImgProgress(size, i, "生成单张二维码耗时=" + end);
                }
                //回调客户端
                long time = System.currentTimeMillis() - startTime;
                createQrImgTime(time, "createQrBitmap:list--->qrbitmap");
                return sendImgs;
            }

            @Override
            protected void onPostExecute(List<Bitmap> bitmapList) {
                super.onPostExecute(bitmapList);
                maps = new ArrayList<>();
                maps = bitmapList;
                //service与act的交互
                //调起链路层传输数据
                serviceStartAct();
            }
        }.execute();
    }

    /**
     * (4)list转qrbitmap
     * <p>
     * 方式2：并发线程池方式
     * <p>
     * 结论：ArrayList 非线程安全，容易导致size变大，线程数再大速度只能缩短一倍。
     *
     * @param list
     * @param nThreads
     * @throws Exception
     */
    List<String> subList = new ArrayList<>();

    public void createQrBitmap2(List<String> list, final int nThreads) throws Exception {
        maps = new ArrayList<>();
        subList = new ArrayList<>();
        int size = list.size();
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        List<Future<List<Bitmap>>> futures = new ArrayList<Future<List<Bitmap>>>(nThreads);
        long startTime = System.currentTimeMillis();//统计
        for (int i = 0; i < nThreads; i++) {
            if (i == (nThreads - 1)) {
                subList = list.subList(size / nThreads * i, list.size());
            } else {
                subList = list.subList(size / nThreads * i, size / nThreads * (i + 1));
            }

            final int finalI = i;
            Callable<List<Bitmap>> task = new Callable<List<Bitmap>>() {
                @Override
                public List<Bitmap> call() throws Exception {
                    List<Bitmap> unitLists = new ArrayList<>();
                    long startTime = System.currentTimeMillis();//统计
                    for (int j = 0; j < subList.size(); j++) {
                        long start = System.currentTimeMillis();//统计
                        Bitmap bitmap = CodeUtils.createByMultiFormatWriter(subList.get(j), qrSize);
                        unitLists.add(bitmap);
                        //回调客户端
                        long end = System.currentTimeMillis() - start;
                        createQrImgProgress(subList.size(), j, "生成单张二维码耗时=" + end + "---线程池编号：" + finalI);
                    }
                    //回调客户端
                    long time = System.currentTimeMillis() - startTime;
                    createQrImgTime(time, "单个线程池编号:" + finalI);
                    return unitLists;
                }
            };
            futures.add(executorService.submit(task));
        }
        for (Future<List<Bitmap>> mfuture : futures) {
            maps.addAll(mfuture.get());
        }
        //清空数据。
        subList = new ArrayList<>();
        //回调客户端
        long time = System.currentTimeMillis() - startTime;
        createQrImgTime(time, "所有线程池执行:list--->qrbitmap");
        executorService.shutdown();
    }


    //-----------------------《服务端-->客户端》回调（不同进程）----------------------

    /**
     * 回调客户端
     * <p>
     * 文件是否可以传输
     * <p>
     * 请安这个步骤操作
     */
    public void isTrans(final boolean isSuccess, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();//成对出现
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.isTrans(isSuccess, msg);
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        //false情况下，基本宣告这次文件传输是失败的，所以清空所有数据
        if (!isSuccess) {
            clearLastData();
        }

    }

    /**
     * 回调客户端
     * <p>
     * (1)文件转成字符流耗时。
     * <p>
     * 请安这个步骤操作
     *
     * @param time
     * @param msg
     */
    public void splitToIoTime(final long time, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.splitToIoTime(time, msg);
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 回调客户端
     * <p>
     * (2)字符流生成array
     * <p>
     * 请安这个步骤操作
     *
     * @param time
     * @param msg
     */
    public void splitToArrayTime(final long time, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.splitToArrayTime(time, msg);
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    /**
     * 回调客户端
     * <p>
     * (3)orglist转带标记的List
     * <p>
     * 请安这个步骤操作
     *
     * @param time
     * @param msg
     */
    public void createNewArray(final long time, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.createNewArrayTime(time, msg);
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 回调客户端
     * <p>
     * （4）合成二维码图的耗时
     * <p>
     * 请安这个步骤操作
     *
     * @param time
     * @param msg
     */
    public void createQrImgTime(final long time, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.createQrImgTime(time, msg);
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 回调客户端
     * <p>
     * 进度
     * <p>
     * 请安这个步骤操作
     *
     * @param total
     * @param msg
     */
    public void createQrImgProgress(final int total, final int position, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.createQrImgProgress(total, position, msg);
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 回调客户端
     * <p>
     * 传输进度
     * <p>
     * 请安这个步骤操作
     *
     * @param total
     * @param msg
     */
    public void qrTransProgress(final long time, final int total, final int position, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.transProgress(time, total, position, msg);
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    /**
     * 回调客户端
     * <p>
     * 二维码传输耗时统计
     * <p>
     * 请安这个步骤操作
     *
     * @param time
     * @param msg
     */
    public void transcomplete(final long time, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener == null) {
                    return;
                }
                //检查注册的回调个数
                final int N = mListener.beginBroadcast();
                //遍历出要用的callback
                try {
                    for (int i = 0; i < N; i++) {
                        QrProgressCallback callback = mListener.getBroadcastItem(i);
                        //处理回调
                        callback.transTime(time, msg);
                        callback.transComplete();
                        mListener.finishBroadcast();//成对出现2
                        // 解绑callback
//                        mListener.unregister(callback);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    //===========================================================================================================================
    //=================================以下为同一进程下，act与service的交互：包括service回调act,act回调service===================================
    //===========================================================================================================================

    //service与act通讯有两种方式1接口，2广播。本demo使用接口。

    /**
     * 由service调起act
     */

    private void serviceStartAct() {
        if (checkActAlive() && isActFrontShow()) {
            isTrans(true, "MainAct在前台运行");
            //接口回调
            if (listener != null) {
                listener.onQrsend(selectPath, newDatas, maps, fileSize);
            } else {
                isTrans(false, "链路层未启动，回调无法使用listener=null");
            }

        } else {
            isTrans(true, "MainAct不在前台，正在开启");
            startApp();
        }
    }

    private boolean checkActAlive() {
        return CheckUtils.isActivityAlive(QrApplication.getInstance(), "com.ruijia.qrcode", "MainAct");
    }

    private boolean isActFrontShow() {
        return CheckUtils.isActFrontShow(QrApplication.getInstance(), "com.ruijia.qrcode.MainAct");
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

    /**
     * act调用，回调给另一个app
     * 识别完成的回调
     */
    public void setAidlQrCodeComplete(long time, String result) {
        transcomplete(time, result);
    }

    /**
     * act调用，发送端发送一条二维码实际耗时
     */
    public void sendAidlQrUnitTime(long successTime, int size, int pos, String msg) {
        qrTransProgress(successTime, size, pos, msg);
    }


    /**
     * 设置回调
     *
     * @param listener
     */
    public void setListener(OnServiceAndActListener listener) {
        this.listener = listener;
    }

    /**
     * act的service连接完成后，通知service回调act
     */
    public void startServiceTrans() {
        //
        //接口回调
        if (listener != null) {
            listener.onQrsend(selectPath, newDatas, maps, fileSize);
        } else {
            isTrans(false, "链路层未启动，回调无法使用listener=null");
        }
    }
}
