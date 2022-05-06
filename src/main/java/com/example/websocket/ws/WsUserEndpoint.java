package com.example.websocket.ws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.websocket.controller.JDBCController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * className: WsUserEndpoint
 * package:com.example.websocket.ws*Description:
 *
 * @Date:2022/2/3 12:41
 * @Author : guoxin@wkcto.com
 */
@ServerEndpoint("/user/{id}/{pwd}")
//@Component
@Controller
public class WsUserEndpoint {
    private static JDBCController jdbcController;

    //每个类的私有内容
    private Integer id;
    private Session session;

    // 注入的时候，给类的 service 注入
    @Autowired
    public void setChatService(JDBCController jdbcController) {
        WsUserEndpoint.jdbcController = jdbcController;
    }

    //连接成功
    @OnOpen
    public void onOpen(Session session,@PathParam("id")Integer id, @PathParam("pwd")String pwd) {
        this.id = id;
        this.session = session;
        System.out.println("收到连接请求，id= "+id+" pwd= "+pwd);
        if (jdbcController == null)
        {
            System.out.println("jdbcController 为空，无法检索数据库内容...");
            return;
        }
        List<Map<String, Object>> maps = jdbcController.UserLogin(id, pwd,session);
        try {
            if (maps.isEmpty()) {
                sendMessage("{cmd=loginresp,msg=login failed!}");
                //断开当前的连接
                session.close();
                System.out.println("id="+id+" 登陆失败，连接已断开...");
            }
            else {
                HashMap<String,String> mapSend = new HashMap<>();
                mapSend.put("cmd","loginresp");
                mapSend.put("msg","ok");
                sendMessage(mapSend.toString());
                maps.get(0).put("cmd","userinfo");
                sendMessage(maps.get(0).toString());
                jdbcController.AddOnlineConnection();
                System.out.println("id="+id+" 的用户登陆成功，用户名为："+maps.get(0).get("username").toString());
                System.out.println("当前在线连接数量为：" + jdbcController.GetOnlineConnectionsNum());
                //如果是用户登陆，然后数据库检索该人所有的可用设备
                List<Map<String, Object>> maps1;
                maps1 = jdbcController.GetUserDevs(id);
                //获得设备后添加设备的在线状态信息
                Map<String, Object> temp;
                Integer devid;
                Iterator<Map<String, Object>> iterator = maps1.iterator();
                while (iterator.hasNext()) {
                    temp = iterator.next();
                    devid = Integer.valueOf(temp.get("devid").toString());
                    temp.put("cmd","devinfo");
                    temp.put("onlinestate", jdbcController.GetDevState(devid));
                    sendMessage(temp.toString());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("连接成功");
    }

    //连接关闭
    @OnClose
    public void onClose() {
        jdbcController.RemoveOnlineConnection();
        jdbcController.UserOffLine(id);
        jdbcController.SetSQLUserState(id, false);  //更新数据库内容
        System.out.println("id为 "+id+" 的用户下线");
    }

    //接收到消息
    @OnMessage
    public String onMsg(String text) throws IOException {
        if (!text.isEmpty()){
            try {
                //首先解析接收到的消息内容
                //{"id":1,"age":2,"name":"zhang"}
                //{\"id\":1,\"age\":2,\"name\":\"zhang\"}
                JSONObject jsonObject = JSON.parseObject(text);
                Integer devid;
                HashMap<String, String> mapGet = new HashMap<>();
                mapGet = JsonObjectToHashMap(jsonObject);
                switch (mapGet.get("cmd")){
                    case "heatebeat":
                        return "{msg=ok}";
                    case "add":
                        //然后找到对应的用户信息并发送对应的信息到用户
                        devid = Integer.valueOf(jsonObject.get("devid").toString());
                        return jdbcController.AddDev(
                                id, devid,jsonObject.get("password").toString());
                    case "remove":
                        //然后找到对应的用户信息并发送对应的信息到用户
                        devid = Integer.valueOf(jsonObject.get("devid").toString());
                        return jdbcController.RemoveDev(
                                id, devid,jsonObject.get("password").toString());
                    default:
                    {
                        devid = Integer.valueOf(jsonObject.get("devid").toString());
                        if (jdbcController.hasPermission(id, devid)
                                && jdbcController.GetDevState(devid)){
                            Session sessionDev = jdbcController.GetDevSession(devid);
                            if (sessionDev != null) {
                                sessionDev.getBasicRemote().sendText(mapGet.toString());
                                return "{cmd=msgresp, msg=ok}";
                            }
                            else
                                return "{cmd:msgresp,msg=session is unavailable.}";
                        }
                        else
                            return "{cmd=msgresp, msg=the sevice is offline, please retry after opening the device}";
                        }
                    }
                } catch (Exception e) {
                e.printStackTrace();
                return "{cmd=msgresp, msg=json format wrong, the data receive is }" + text;
            }
        }
        else
            return "{cmd=msgresp, msg=null received.}";
    }


    //发送消息
    public void sendMessage(String message) throws IOException {
        session.getBasicRemote().sendText(message);
    }

    @OnError
    public void onError(Throwable t) {
        System.out.println(" websocket 连接出错！");
        System.out.println(t);
    }

    //1.將JSONObject對象轉換為HashMap<String,String>
    public static HashMap<String, String> JsonObjectToHashMap(JSONObject jsonObj){
        HashMap<String, String> data = new HashMap<String, String>();
        Iterator it = jsonObj.keySet().iterator();
        while(it.hasNext()){
            String key = String.valueOf(it.next().toString());
            String value = (String)jsonObj.get(key).toString();
            data.put(key, value);
        }
        return data;
    }
}
