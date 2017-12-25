package com.openxu.socketclient;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {
    private String TAG = "MainActivity";

    Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case Constant.HANDMSG_CONNECTED:
                    LogUtil.i(TAG, "连接成功...");
                    break;
                case Constant.HANDMSG_CONNECT_FAIL:
                    LogUtil.i(TAG, "连接失败...");
                    break;
                case Constant.HANDMSG_NOTFOND_COMMAND:
                    LogUtil.i(TAG, "没有找到此命令文件");
                    break;
                case Constant.HANDMSG_SEND_NOCONNECT:
                    LogUtil.i(TAG, "连接已断开了...");
                    break;
                case Constant.HANDMSG_SENDDING:
                    break;
                case Constant.HANDMSG_SEND_SEC:
                    LogUtil.i(TAG, "命令发送成功...");
                    break;
                case Constant.HANDMSG_SEND_NOFK:
                    LogUtil.i(TAG, "命令发送无反馈...");
                    break;
                case Constant.HANDMSG_DO_FAIL:   //执行失败
                    LogUtil.i(TAG, "执行失败...");
                    break;
                case Constant.HANDMSG_SEND_COMMAND:   //命令显示
                    break;
                case Constant.HANDMSG_SEND_FAIL:
                    LogUtil.i(TAG, "命令发送失败...");
                    break;
                default:
                    break;
            }
        };
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceCon = new ServiceCon();
        bindService(new Intent(this, TranService.class), serviceCon,
                BIND_AUTO_CREATE);

    }

    public void onClick(View v){
        switch (v.getId()){
            case R.id.btn_getdir:
                new Thread(){public void run() {
                    binder.initThread("192.168.132.2", 12000);
                    binder.connect();
                };}.start();

                break;
        }
    }

    private ServiceCon serviceCon;
    private TranService.ServiceBinder binder;
    class ServiceCon implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (TranService.ServiceBinder) service;
            binder.setUiHandler(handler);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }



    @Override
    public void onBackPressed() {
        if (binder.getSendStatus()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        binder.disConnect();
        unbindService(serviceCon);
        super.onDestroy();
    }
















    //通过截取cmd流方式得到计算机的配置信息(不好)
    public static List<String> getIpAddress(){
        Process p = null;
        List<String> address  = new ArrayList<String>();
        try{
            p = new ProcessBuilder("ipconfig","/all").start();
        }catch(Exception e){
            return address;
        }
        StringBuffer sb = new StringBuffer();
        //读取进程输出值
        InputStream inputStream = p.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String s = "";
        try{
            while((s=br.readLine())!=null){
                sb.append(s+"\n");
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try{
                inputStream.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        System.out.println(sb);
        return address;
    }

    public static void getIpconfig(){
        Map<String,String> map = System.getenv();
        System.out.println(map.get("USERNAME"));//获取用户名
        System.out.println(map.get("COMPUTERNAME"));//获取计算机名
        System.out.println(map.get("USERDOMAIN"));//获取计算机域名
    }
    //得到计算机的ip地址和mac地址
    public static void getConfig(){
        try{
            InetAddress address = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(address);
            //ni.getInetAddresses().nextElement().getAddress();
            byte[] mac = ni.getHardwareAddress();
            String sIP = address.getHostAddress();
            String sMAC = "";
            Formatter formatter = new Formatter();
            for (int i = 0; i < mac.length; i++) {
                sMAC = formatter.format(Locale.getDefault(), "%02X%s", mac[i],
                        (i < mac.length - 1) ? "-" : "").toString();

            }
            System.out.println("IP：" + sIP);
            System.out.println("MAC：" + sMAC);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    //得到计算机的ip,名称,操作系统名称,操作系统版本
    public static void Config(){
        try{
            InetAddress addr = InetAddress.getLocalHost();
            String ip=addr.getHostAddress().toString(); //获取本机ip
            String hostName=addr.getHostName().toString(); //获取本机计算机名称
            System.out.println("本机IP："+ip+"\n本机名称:"+hostName);
            Properties props=System.getProperties();
            System.out.println("操作系统的名称："+props.getProperty("os.name"));
            System.out.println("操作系统的版本："+props.getProperty("os.version"));
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    //java环境
    public static void all(){
        Properties props=System.getProperties();
        System.out.println("Java的运行环境版本："+props.getProperty("java.version"));
        System.out.println("Java的运行环境供应商："+props.getProperty("java.vendor"));
        System.out.println("Java供应商的URL："+props.getProperty("java.vendor.url"));
        System.out.println("Java的安装路径："+props.getProperty("java.home"));
        System.out.println("Java的虚拟机规范版本："+props.getProperty("java.vm.specification.version"));
        System.out.println("Java的虚拟机规范供应商："+props.getProperty("java.vm.specification.vendor"));
        System.out.println("Java的虚拟机规范名称："+props.getProperty("java.vm.specification.name"));
        System.out.println("Java的虚拟机实现版本："+props.getProperty("java.vm.version"));
        System.out.println("Java的虚拟机实现供应商："+props.getProperty("java.vm.vendor"));
        System.out.println("Java的虚拟机实现名称："+props.getProperty("java.vm.name"));
        System.out.println("Java运行时环境规范版本："+props.getProperty("java.specification.version"));
        System.out.println("Java运行时环境规范供应商："+props.getProperty("java.specification.vender"));
        System.out.println("Java运行时环境规范名称："+props.getProperty("java.specification.name"));
        System.out.println("Java的类格式版本号："+props.getProperty("java.class.version"));
        System.out.println("Java的类路径："+props.getProperty("java.class.path"));
        System.out.println("加载库时搜索的路径列表："+props.getProperty("java.library.path"));
        System.out.println("默认的临时文件路径："+props.getProperty("java.io.tmpdir"));
        System.out.println("一个或多个扩展目录的路径："+props.getProperty("java.ext.dirs"));
        System.out.println("操作系统的名称："+props.getProperty("os.name"));
        System.out.println("操作系统的构架："+props.getProperty("os.arch"));
        System.out.println("操作系统的版本："+props.getProperty("os.version"));
        System.out.println("文件分隔符："+props.getProperty("file.separator"));//在 unix 系统中是＂／＂ System.out.println("路径分隔符："+props.getProperty("path.separator"));//在 unix 系统中是＂:＂ System.out.println("行分隔符："+props.getProperty("line.separator"));//在 unix 系统中是＂/n＂ System.out.println("用户的账户名称："+props.getProperty("user.name"));
        System.out.println("用户的主目录："+props.getProperty("user.home"));
        System.out.println("用户的当前工作目录："+props.getProperty("user.dir"));
    }


}
