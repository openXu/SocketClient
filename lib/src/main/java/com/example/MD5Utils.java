package com.example;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;

/**
 * author : openXu
 * create at : 2017/6/19 11:26
 * project : Client_Android_zhongxiao_dev
 * version : 1.0
 * class describe?
 */
public class MD5Utils {


    public static void main(String[] args) {
        startService();
    }



    private static void startService(){
        ServerSocket ss = null;
        Socket sk = null;
        try{
            System.out.println("create server socket....");
            ss = new ServerSocket(12000);
            System.out.println("wait for a connection....");
            while(true) {    //?????????????????????
                sk = ss.accept();  //?????????????
                System.out.println("get a socket object...");
                new SocketThread(sk).start();//????socketThread????????socket??
            }
        } catch(Exception ex){
            ex.printStackTrace();
        } finally{
            try{
                if(ss != null)
                    ss.close();
                if(sk != null)
                    sk.close();
            }catch(Exception ex){
                System.out.println(ex.getMessage());
            }
        }
    }






}
