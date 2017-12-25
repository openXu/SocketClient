package com.example;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Calendar;

/**
 * Created by Admin on 2017/12/25.
 */

public class SocketThread extends Thread {
    public Socket socket;
    public SocketThread(Socket socket) {
        this.socket = socket;
    }
    public void run() {
        PrintWriter pw = null;
        Calendar c;
        try {
            pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
            while(true){//服务器每隔3秒向客户端发送当前时间信息
                Thread.sleep(3000);
                c = Calendar.getInstance(); //获取当前时间
                pw.println("Server Time:"+c.getTime().toString());
                pw.flush();
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        } finally {
            try {
                if (pw != null)
                    pw.close();
                if (socket != null)
                    socket.close();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

}