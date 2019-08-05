// FileAidlInterface.aidl
package com.ruijia.file;

/**
* 测试b与链路层的aidl（测试b是服务端，链路层是客户端，单向通讯）
*/
interface FileAidlInterface {

   /**
   * 链路层将文件发送给 测试B软件
   */
    boolean QRRecv(String filePath);
}
