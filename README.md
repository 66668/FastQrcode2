# 原生摄像头版：
## 3款app说明：

1. app项目:原生摄像头版本,qr链路识别端,是测试A软件的服务端，是测试B软件的客户端，不对客户提供源码，只提供打包apk。
（测试A<--->qr链路识别端,qr链路识别端-->测试B）
2. clientdemo:测试A软件。用于向链路层发送控制信息，发送传输文件等，同时可以监听链路层的传输状态。(测试A<--->qr链路识别端)
3. fileapp:测试B软件。接收链路端的传输文件。(qr链路识别端-->测试B）

三款软件的通讯使用aidl,链路层与测试A互相联系，链路层和测试B互相联系，测试A和测试B之间没有任何联系，各个软件的功能互不影响。
深度开发测试A和B软件即可，链路层的传输已经深度完成，只负责传输信息。


1. 在service的aidl中添加 register(自己的callback)和unregister(自己的callback)
2. 在服务端的aidl中再创建一个自己的callback,添加回调的方法。

## 性能优化分析
1. 倒计时优化:handler.postdelay倒计时不准确，改为 Timer+runOnUiThread方式

