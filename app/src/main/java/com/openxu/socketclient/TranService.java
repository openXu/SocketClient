package com.openxu.socketclient;


import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

public class TranService  extends Service {

    private String TAG = "TranService";
    private static Socket socket; // Socket 数据
    public boolean conState; // 连接状态
    private boolean sendingCommand = false; // 是不是正在发送命令（非测试）
    private Handler handler = null;
    private ServiceBinder binder;
    private SocketAddress remoteAddr;
    private Object sendLock = new Object();
    private Object readLock = new Object();
    private Object heartLock = new Object();
    private Object reconLock = new Object();

    private ReconThread reconThread;
    private HeartThread heartThread;
    private ReadThread readThread;

    public class ServiceBinder extends Binder {

        public void setUiHandler(Handler handler) {
            LogUtil.i(TAG, "设置handler：" + handler);
            TranService.this.handler = handler;
        }
        public void initThread(String hostname, int port){
            initClient( hostname,  port);
        }
        public void connect() {
            startConnect();
        }
        public boolean getSendStatus(){
            return sendingCommand;
        }
        public void sendCommand(String data){
            sendData(data);
        }
        public void sendCommand(String[] data){
            sendData(data);
        }
        public void sendCommandRead(String data){
            sendDataAndRead(data);
        }
        public void disConnect() {
            disconnect();
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        binder = new ServiceBinder();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    private void initClient(String hostname, int port) {
        remoteAddr = new InetSocketAddress(hostname, port);
        socket = new Socket();

        reconThread = new ReconThread();
        heartThread  = new HeartThread();
        readThread = new ReadThread();
        reconThread.start();
        heartThread.start();
        readThread.start();
    }

    /**
     * 创建连接
     * @return
     */
    private boolean startConnect() {
        ISRECONNECT = true;
        ISHEART = true;
        ISREAD = true;

        if (socket == null || socket.isClosed())
            socket = new Socket();
        try {
            socket.setKeepAlive(false);
        } catch (SocketException e1) {
            e1.printStackTrace();
        }
        try {
            socket.connect(remoteAddr, 5000);
        } catch (IOException e) {
            LogUtil.v(TAG, e.getMessage());
            sendEnptyMsg(Constant.HANDMSG_CONNECT_FAIL);
            LogUtil.v(TAG, "----------第一次连接失败，开启重连机制");
            conState = false;
            synchronized (reconLock) {
                reconLock.notifyAll();
            }
            return false;
        }

        conState = true;
        sendEnptyMsg(Constant.HANDMSG_CONNECTED);

        LogUtil.v(TAG, "----------第一次连接成功，开启心跳检测");
        synchronized (heartLock) {
            heartLock.notifyAll();
        }
        LogUtil.v(TAG, "----------第一次连接成功，开启读取数据线程");
        synchronized (readLock) {
            readLock.notifyAll();
        }
        return true;
    }

    private void disconnect() {
        ISRECONNECT = false;  //改变while循环标记，然后唤醒线程，让run方法执行完毕，线程就死了
        ISHEART = false;
        ISREAD = false;
        conState = false;
        synchronized (reconLock) {
            reconLock.notifyAll();
        }
        synchronized (heartLock) {
            heartLock.notifyAll();
        }
        if (socket != null) {
            try {
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.close();
                LogUtil.v(TAG,  "连接断开成功了");
                sendEnptyMsg(Constant.HANDMSG_CONNECT_FAIL);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                socket = null;
            }
        }else{
            sendEnptyMsg(Constant.HANDMSG_CONNECT_FAIL);
        }
        reconThread = null;
        heartThread = null;
        readThread = null;
    }

    private boolean ISRECONNECT = true;
    private boolean ISHEART = true;
    private boolean ISREAD = true;
    /**
     * 重连线程
     */
    private class ReconThread extends Thread{
        public void run() {
            synchronized (reconLock) {
                try {
                    LogUtil.v(TAG,  "程序启动，重连线程等待");
                    reconLock.wait();
                } catch (InterruptedException e2) {
                    e2.printStackTrace();
                }
                while (ISRECONNECT) {
                    if (socket == null || socket.isClosed())
                        socket = new Socket();
                    try {
                        socket.setKeepAlive(false);
                        socket.connect(remoteAddr, 5000);
                    } catch (Exception e) {
                        stopConn();
                        try {
                            LogUtil.v(TAG,  "重连失败，等待" + Constant.reConTime + "秒");
                            reconLock.wait(Constant.reConTime);
                            continue;
                        } catch (InterruptedException e1) {
                        }
                    }
                    conState = true;
                    sendEnptyMsg(Constant.HANDMSG_CONNECTED);

                    LogUtil.v(TAG, "----------重连成功，开启心跳检测");
                    synchronized (heartLock) {
                        heartLock.notifyAll();
                    }
                    LogUtil.v(TAG, "----------重连成功，开启读取数据线程");
                    synchronized (readLock) {
                        readLock.notifyAll();
                    }
                    try {
                        LogUtil.v(TAG, "----------重连成功，退出重连机制");
                        reconLock.wait();
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                LogUtil.v(TAG,  "重连线程执行完毕，销毁了");
            }
        };
    }

    /**
     * 心跳包线程
     */
    private class HeartThread extends Thread{
        public void run() {
            synchronized (heartLock) {
                try {
                    LogUtil.v(TAG,  "程序启动，心跳线程等待");
                    heartLock.wait();
                } catch (InterruptedException e2) {
                    e2.printStackTrace();
                }
                while (ISHEART) {
                    boolean longWait = false;
                    synchronized (sendLock) {
                        try {
                            /**
                             * 往输出流发送一个字节的数据，只要对方Socket的SO_OOBINLINE属性没有打开，
                             * 就会自动舍弃这个字节，而SO_OOBINLINE属性默认情况下就是关闭的
                             */
                            socket.sendUrgentData(0xFF);
                            OutputStream out = socket.getOutputStream();
                            out.flush(); // 避免发送命令时出现空格
                            LogUtil.i(TAG, "~~~~~~#############心跳包检测到  连接还活着.......");
                        } catch (Exception e) {
                            LogUtil.e(TAG, "~~~~~~#############心跳包检测到  连接已断开了.......");
                            e.printStackTrace();
                            sendEnptyMsg(Constant.HANDMSG_CONNECT_FAIL);
                            stopConn();
                            synchronized (reconLock) {
                                LogUtil.e(TAG, "----------心跳包检测到  连接已断开了，开启重连机制");
                                reconLock.notifyAll();
                            }
                            longWait = true; // 已经断开了就不要发送心跳了，唤醒重连线程
                        }
                    }
                    try {
                        if (longWait) {
                            LogUtil.v(TAG, "----------心跳包检测线程等待");
                            heartLock.wait();
                        } else {
                            heartLock.wait(Constant.heartTime);
                        }
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                }
                LogUtil.v(TAG,  "心跳线程执行完毕，销毁了");
            }
        };
    }

    private String readLine;
    private class ReadThread extends Thread{
        public void run() {
            synchronized (readLock) {
                try {
                    LogUtil.v(TAG,  "程序启动，读取返回数据线程等待");
                    readLock.wait();
                } catch (InterruptedException e2) {
                    e2.printStackTrace();
                }
                while (ISREAD) {
                    LogUtil.v(TAG,  "读取线程进入循环了");
                    boolean longWait = false;
                    synchronized (readLock) {
                        try {
                            InputStream in = socket.getInputStream();
                            byte[] buf = new byte[1024];
                            while(true){
                                int len = in.read(buf);
                                readLine = new String(buf,0,len);
                                String command = "<<收到反馈:" + readLine;
                                sendMsg(command, Constant.HANDMSG_SEND_COMMAND);
                                LogUtil.v(TAG,  command);
                                if(readLine.equalsIgnoreCase("K")){
                                    SEND_OK = 1;
                                }/*else if(line.equalsIgnoreCase("E")){
									SEND_OK = 2;
								}*/else{
                                    SEND_OK = 2;
                                }
                                synchronized (sendLock) {
                                    sendLock.notifyAll();
                                }
                            }

                        } catch (Exception e) {
                            LogUtil.v(TAG, "接收数据失败，断开连接");
                            e.printStackTrace();
                            sendEnptyMsg(Constant.HANDMSG_CONNECT_FAIL);
                            stopConn();
                            synchronized (reconLock) {
                                LogUtil.v(TAG, "----------接收数据失败，开启重连机制");
                                reconLock.notifyAll();
                            }
                            longWait = true; // 已经断开了就不要发送心跳了，唤醒重连线程
                        }
                    }
                    try {
                        if (longWait) {
                            LogUtil.v(TAG, "----------接受线程等待");
                            readLock.wait();
                        }
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                }
                LogUtil.v(TAG,  "接受数据线程销毁了");
            }
        };
    }

    /**
     * 发送数据
     * @param data
     */
    private void sendData(String data) {
        if(sendingCommand){
            sendEnptyMsg(Constant.HANDMSG_SENDDING);
            return;
        }
        synchronized (sendLock) {
            if (!conState) {
                LogUtil.v(TAG, "连接已断开，发送失败，开启重连机制");
                handler.sendEmptyMessage(Constant.HANDMSG_SEND_NOCONNECT);
            } else {
                sendingCommand = true;
                try {
                    OutputStream out = socket.getOutputStream();
                    out.write(data.getBytes());
                    LogUtil.i(TAG, ">>发送"+data+"成功");
                    out.flush();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LogUtil.i(TAG, ">>发送" + new String(data) + "成功");
                    sendEnptyMsg(Constant.HANDMSG_SEND_SEC);
                } catch (IOException e) {
                    e.printStackTrace();
                    handler.sendEmptyMessage(Constant.HANDMSG_SEND_FAIL);
                }finally{
                    LogUtil.v(TAG,  "将发送标志置为false");
                    sendingCommand = false;
                }
            }
        }
    }

    /**
     * 发送数据
     */
    private void sendData(String[] datas){
        if(sendingCommand){
            sendEnptyMsg(Constant.HANDMSG_SENDDING);
            return;
        }
        synchronized (sendLock) {
            if (!conState) {
                LogUtil.v(TAG, "连接已断开，发送失败，开启重连机制");
                handler.sendEmptyMessage(Constant.HANDMSG_SEND_NOCONNECT);
            } else {
                sendingCommand = true;
                try {
                    for(String data : datas){
                        OutputStream out = socket.getOutputStream();
                        out.write(data.getBytes());
                        LogUtil.i(TAG, ">>发送"+data+"成功");
                        out.flush();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    sendEnptyMsg(Constant.HANDMSG_SEND_SEC);
                } catch (IOException e) {
                    e.printStackTrace();
                    handler.sendEmptyMessage(Constant.HANDMSG_SEND_FAIL);
                }finally{
                    LogUtil.v(TAG,  "将发送标志置为false");
                    sendingCommand = false;
                }
            }
        }
    }

    long send_time;
    long times;
    int SEND_OK = 0;   //0表示没有收到反馈，1表示k成功， 2表示其他反馈，执行失败
    boolean SEND_CONTINU1 = true;
    int RESEND_CON = 3;
    /**
     * 发送数据
     * @param data
     */
    private void sendDataAndRead(String data) {
        if(sendingCommand){
            sendEnptyMsg(Constant.HANDMSG_SENDDING);
            return;
        }
        synchronized (sendLock) {
            if (!conState) {
                LogUtil.v(TAG, "连接已断开，发送失败，开启重连机制");
                handler.sendEmptyMessage(Constant.HANDMSG_SEND_NOCONNECT);
            } else {
                send_time = System.currentTimeMillis();
                sendingCommand = true;
                try {
                    SEND_OK = 0;
                    for(int i =0; i<=RESEND_CON; i++){
                        times = System.currentTimeMillis();
                        String command = ">>第"+(i+1)+"次发送指令"+data;
                        sendMsg(command, Constant.HANDMSG_SEND_COMMAND);
                        LogUtil.v(TAG,  command);
                        OutputStream out = socket.getOutputStream();
                        out.write(data.getBytes());
                        LogUtil.i(TAG, ">>发送"+data+"完毕");
                        out.flush();
                        try {
                            sendLock.wait(3000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        LogUtil.i(TAG, "发送线程被唤醒了,等待了"+(System.currentTimeMillis()-times));
                        if(SEND_OK == 0){         //没有收到反馈
                            LogUtil.i(TAG, ">>硬件无反馈");
                            sendEnptyMsg(Constant.HANDMSG_SEND_NOFK);   //无反馈
                            if(i==RESEND_CON){
                                break;   //继续发送下一条命令
                            }
                        }else if(SEND_OK == 1){   //成功
                            LogUtil.i(TAG, ">>发送"+data+"成功");
                            break;
                        }else  if(SEND_OK == 2){  //收到其他字符
                            LogUtil.v(TAG, ">>执行失败");
                            sendEnptyMsg(Constant.HANDMSG_DO_FAIL);    //执行失败
                            if(i==RESEND_CON){
                                break;     //继续发送下一条命令
                            }
                        }
                    }
                    send_time = System.currentTimeMillis() - send_time;
                    if(send_time<500){
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    sendEnptyMsg(Constant.HANDMSG_SEND_SEC);
                } catch (IOException e) {
                    e.printStackTrace();
                    handler.sendEmptyMessage(Constant.HANDMSG_SEND_FAIL);
                }finally{
                    LogUtil.v(TAG,  "将发送标志置为false");
                    sendingCommand = false;
                }
            }
        }
    }

    private boolean stopConn() {
        conState = false;
        if (socket == null)
            return true;
        try {
            socket.shutdownInput();
            socket.shutdownOutput();
            socket.close();
        } catch (Exception e) {
            return false;
        } finally {
            socket = null;
        }
        return true;
    }

    private void sendEnptyMsg(int type) {
        if (handler != null)
            handler.sendEmptyMessage(type);
    }
    private void sendMsg(String command, int type) {
        if (handler != null){
            Message msg = handler.obtainMessage();
            msg.obj = command;
            msg.what = type;
            handler.sendMessage(msg);
        }
    }
}