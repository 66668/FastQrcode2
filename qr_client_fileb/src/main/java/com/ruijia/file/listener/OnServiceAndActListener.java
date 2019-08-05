package com.ruijia.file.listener;

import android.graphics.Bitmap;

import java.util.List;

/**
 * service向act发起调用
 */
public interface OnServiceAndActListener {
    void onQrRecv(String selectPath);
}
