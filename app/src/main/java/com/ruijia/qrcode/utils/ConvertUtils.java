package com.ruijia.qrcode.utils;

public class ConvertUtils {

    //int 转7位数的String长度
    public static String int2String(int size) throws Exception {

        String str = new Integer(size).toString();
        if (str.length() == 1) {
            return "000000" + str;
        } else if (str.length() == 2) {
            return "00000" + str;
        } else if (str.length() == 3) {
            return "0000" + str;
        } else if (str.length() == 4) {
            return "000" + str;
        } else if (str.length() == 5) {
            return "00" + str;
        } else if (str.length() == 6) {
            return "0" + str;
        } else if (str.length() == 7) {
            return str;
        } else {
            throw new Exception("文件过大，无法解析");
        }
    }
}
