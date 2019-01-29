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

## 适配说明：
源码只适配1920*1080的手机屏幕，而且只适配了手机版，定制机需要改必要参数，否则代码不执行。

## 1 代码设计说明：

## 1.1 app说明
 拆分成3款app,保证链路层只传输文件，不参与其他功能。三款app间的通讯由aidl实现。最终如果给客户开发，只给A软件和B软件，链路层打包成app给客户即可。
 
 app如何使用：
 
 手机发送端：需要先打开链路层app，再按home键隐藏；然后再打开测试A。原因说明：aidl的使用需要先建立服务端，再建立客户端，链路层app是测试A的服务端，需要现开启
 
 手机接收端：需要先打开测试B,按home键隐藏；然后再打开链路层App，原因同上。
 
## 1.2 链路层的设计思路
 本代码有多个远程分支，每个分支的传输框架不相同，本分支做的传输框架策略是：**二维码提前生成+缓存文件保存+线程池读取缓存文件内容**。
 
 该框架优点：可以实现大文件传输,而且链路层传输速度快。能够保证内存不是无限制增加，提高传输稳定性。
 
 该框架缺点：二维码图片需要提前保存到缓存文件中，整体效率在耗时上，又增加了保存到缓存文件的时间。
 
 综合评估：整体利大于弊，因为二维码生成本来就是不稳定的耗时，每张二维码多了接近100ms的缓存时间，其实还是感觉不到，而且这时间不是在链路层传输中增加的，不影响传输效率。
 
## 1.3 链路层协议设计
链路app开启后，二维码可以互相发送，可以互为发送端或接收端，但是，如下是以一个方向传输讲述，方便理解。

###3.3.1.发送端

###1.字段发送规则
数据以2952的长度片段发送，传输规则是，10长度的首标记+2938有效内容长度+4长度尾标记。

###1.1首标记规则
  snd字符+7位的标记位，比如snd1234567,表示当前要传输的是第1234567位置的片段。

###1.2传输内容
  长度2952-14=2938，有效传输内容长度。

###1.3尾标记
  4长度，固定的RQRJ,QR表示传输QRcode，二维码，RJ表示本公司简写。

###2.字段发送处理（发送端）
  发送端发送数据规则，都是以固定间隔发送二维码图片数据。当数据发送结束后，最后会发送一张二维码，内容是：QrcodeContentSendOver+filePath+七位的总片段数size，QrcodeContentSendOver表示发送结束标记，filePath表示文件绝对路径，size表示总共需要传输的数据段数，比如：QrcodeContentSendOver/Storage/element/0/myuse.zip1234567

###3.字段接收处理（发送端）
发送端收到反馈信息，说明接收端收到结束标记，并将处理结果反馈回来。接收的信息需要分2种情况讨论，当只收到QrCodeContentReceiveOver，表示数据完全传输完成，发送端可以清理缓存等简单操作。
当收到rcv时，需要将字段处理，比如 rcv00000021/12/123/1234/12345RJQR（规则见3.3.2/1）去掉10长度首标记，和4长度尾标记，有效内容是1/12/123/1234/12345，表示第1，第12，第123，第1234，第12345的片段缺失，代码处理再次发送即可。

###3.3.2接收端

###1字段发送规则
10长度的首标记+内容+4长度尾标记
触发接收端发送信息，前提是接收端接收到了发送端字段：QrcodeContentSendOver，表示发送端数据已经传输完成，等待接收端处理结果，所以接收端处理出结果后，再反馈给发送端。

###1.1首标记规则
  rcv字符+7位的标记位,比如 rcv0000002,表示有2张二维码要解析。
###1.2传输内容
长度大于2000，返回的是片段标记位的拼接，比如：12/123/1234/12345，表示第12，第123，第1234和第12345的数据段缺失。
###1.3尾标记
  4长度，固定的RQRJ,QR表示传输QRcode，二维码，RJ表示本公司简写。

###2.字段发送处理（接收端）
类似发送端发送二维码，以固定时间间隔发送二维码图片，二维码内容是按照接收端发送规则发送的片段。发送结束后，再发送结束标记QrCodeContentReceiveOver。

###3.字段接收处理（接收端）
接收字段分为2种，当收到QrcodeContentSendOver字段时，表示发送端发送结束，统计接收端缺失数据并拼接，接收端发送规则：rcv+二维码个数+拼接字段+RJQR，然后将维码发送。
当收到snd时，需要将字段处理，比如snd1234567abcdefgRJQR,去掉10长度首标记，去掉4长度尾标记，有效字符流是abcdefg,保存数据即可。


## 性能优化分析
1. 倒计时优化:handler.postdelay倒计时不准确，改为 Timer+runOnUiThread方式

## 数据统计

说明
1. 该设计目前只适合传小文件（小于500KB）。
2. 每个测试都是测3次，之后换个条件再测试3次。
3. 关于第一次发送速率的解释：
    该速率的影响因素有两个，一个是文件大小，一个是线程池缓存二维码图的个数。如果是小文件，传送最后，线程池一般还会存有二维码图，发送间隔就是设置的间隔；
    如果是大文件，文件传输到一半时，线程池的图片会被用光，发送间隔由片段转二维码图的速度决定，所以这一块需要做缓存框架设计。

| 发送时间间隔（ms）| 文件大小 |转成字符流大小  | 第一次发送二维码速率（KB`/`s）|第二次+发送二维码速率（KB`/`s）|每次缺失耗时（ms）|总耗时（ms）|文件传输总效率（KB`/`s）|
| ---------- | -------------| ------------- | --------------| --------------| --------------| --------------|  --------------| 
| 175|  209KB| 280KB|13.4|(2次)16.3,15|1336, 2086|25132|11.141|
| 175|  209KB| 280KB|18.4|（4次）16.4, 15.3, 13.2, 10.55|1457, 1424, 1639, 1339,(2413)|36165|8.291|
| 175|  209KB| 280KB|18.4|（4次）14.65, 16.17, 15.58, 14.8,|2050, 1924, 1639, 1129, 1131,(4909)|38839|8.248|
| 190|  209KB| 280KB|15.4|（2次）15, 11.5|1071,1463,(2231)|27766|10.960|
| 190|  209KB| 280KB|15.45|（2次）14.7, 11.6|985,1068,(2050)|26701|11.353|
| 200|  352KB| 472KB|14.1|（4次）14.3, 13.2, 10.1, 6.5 |1630,1506,2128,1419,(1986)|50658|9.691|
| 200|  438KB| --|10.7|（3次）14.3, 12.7, 8.4 |1710,1585,2308,(2851)|74999|8.150|

##需要改进的优化方案

硬件上：
1.摄像头最好改成可单独调节角度的。
2.长时间使用需要降温处理

代码上：
聚焦问题需要做优化

### 关于聚焦的改进方案：
1. 只要使用ImageView显示二维码，必须设置ImageView的显示尺寸。
2. 只要显示结束，或者 img_result.setImageBitmap(null)操作，则ImageView尺寸必须归零，用于摄像头聚焦文字，方便聚焦


##bug 解决：
1. android.app.ServiceConnectionLeaked
这是act和service调用时，代码不严谨导致的，当act销毁时，必须解绑service,否则就会报错。在onDestroy中添加unbindService(conn)即可