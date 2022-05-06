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
 * className: WsDevEndpoint
 * package:com.example.websocket.ws*Description:
 *
 * @Date:2022/2/3 12:39
 * @Author : guoxin@wkcto.com
 */
@ServerEndpoint("/dev/{id}/{pwd}")
//@Component
@Controller
public class WsDevEndpoint {
       //设备登陆记录
    private static JDBCController jdbcController;

    //每个类的私有内容
    private Integer id;
    private Session session;
    private String devName;

    // 注入的时候，给类的 service 注入
    @Autowired
    public void setChatService(JDBCController jdbcController) {
        WsDevEndpoint.jdbcController = jdbcController;
        System.out.println("成功将类的 jdbcController 注入，类的 jdbcController 为："+jdbcController);
    }

    //给所有的在线用户发送消息
    public void SendAllOnlineUsersMsg(String msg){
        //如果是设备登陆，需要向着所有设备的在线拥有者发送登陆的消息
        List<Map<String, Object>> maps;
        maps = jdbcController.GetDevUsersOnline(id);
        //获得设备后添加设备的在线状态信息
        Map<String, Object> temp;
        Integer userid;
        HashMap<String,String> mapSend = new HashMap<>();
        Iterator<Map<String, Object>> iterator = maps.iterator();
        Session session1;
        while (iterator.hasNext()) {
            temp = iterator.next();
            userid = Integer.valueOf(temp.get("userid").toString());
            session1 = jdbcController.GetUserSession(userid);
            if (session1 != null)   //防止数据库记录出错导致此处无法获得正确的 session
            {
                try {
                    session1.getBasicRemote().sendText(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else
            {
                //修正当前数据库存储的在线信息
                jdbcController.SetSQLUserState(userid, false);
            }
        }
    }

    //连接成功
    @OnOpen
    public void onOpen(Session session, @PathParam("type")String type, @PathParam("id")Integer id, @PathParam("pwd")String pwd) {
        this.id = id;
        this.session = session;
        System.out.println("收到连接请求，id= "+id+" pwd= "+pwd);
        if (jdbcController == null)
        {
            System.out.println("jdbcController 为空，无法检索数据库内容...");
            return;
        }
        List<Map<String, Object>> maps = jdbcController.DevLogin(id, pwd, session);

        try {
            if (maps.isEmpty()) {
                sendMessage("{\"cmd\":\"loginresp\",\"msg\":\"login failed!\"}");
                //断开当前的连接
                session.close();
                System.out.println("id="+id+" 登陆失败，连接已断开...");
            }
            else {
                HashMap<String,String> mapSend = new HashMap<>();
                this.devName = maps.get(0).get("devname").toString();
                //首先发送登陆成功的消息
                mapSend.put("cmd","loginresp");
                mapSend.put("msg","ok");
                sendMessage(mapSend.toString());
                mapSend.clear();
                //然后发送数据库的检索结果，包括登陆的 id 和 name
                maps.get(0).put("cmd","userinfo");
                sendMessage(maps.get(0).toString());
                jdbcController.AddOnlineConnection();
                System.out.println("id="+id+" 的设备登陆成功，设备名："+devName);
                System.out.println("当前在线连接数量为：" + jdbcController.GetOnlineConnectionsNum());

                //初始化设备登陆信息
                mapSend.put("cmd","devstate");
                mapSend.put("devid",id.toString());
                mapSend.put("devname", this.devName);
                mapSend.put("onlinestate","true");
                SendAllOnlineUsersMsg(mapSend.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("连接成功");
    }

    //连接关闭
    @OnClose
    public void onClose() {

        jdbcController.DevOffLine(id);
        jdbcController.RemoveOnlineConnection();
        //向对应的在线用户发送设备下线消息
        List<Map<String, Object>> maps;
        maps = jdbcController.GetDevUsersOnline(id);
        Integer userid;
        HashMap<String,String> mapSend = new HashMap<>();
        Iterator<Map<String, Object>> iterator = maps.iterator();
        Session session1;
        //初始化设备登陆信息
        mapSend.put("cmd","devstate");
        mapSend.put("devid",id.toString());
        mapSend.put("devname", this.devName);
        mapSend.put("onlinestate","false");
        while (iterator.hasNext()) {
            userid = Integer.valueOf(iterator.next().get("userid").toString());
            session1 = jdbcController.GetUserSession(userid);
            if (session1 != null) {
                try {
                    session1.getBasicRemote().sendText(mapSend.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("id=" + id + " 的设备已下线...");
    }

    //接收到消息
    @OnMessage
    public String onMsg(String text, Session session) throws IOException {
        System.out.println("用户 id 为 " + id + " 的用户收到消息："+text);
        if (!text.isEmpty()){
            try {
                //{"id":1,"age":2,"name":"zhang"}
                //{\"id\":1,\"age\":2,\"name\":\"zhang\"}
                //首先解析接收到的消息内容
                JSONObject jsonObject = JSON.parseObject(text);
                HashMap<String, String> mapGet = new HashMap<>();
                mapGet = JsonObjectToHashMap(jsonObject);
                //然后找到对应的用户信息并发送对应的信息到用户
                SendAllOnlineUsersMsg(mapGet.toString());
                return "{\"cmd\":\"msgresp\",\"msg\":\"ok.\"}";
            } catch (Exception e) {
                e.printStackTrace();
                return "{\"cmd\":\"msgresp\",\"msg\":\"json format wrong, the data receive is \"}" + text;
            }
        }
        else
            return "{\"cmd\":\"msgresp\",\"msg\":\"null msg.\"}";
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
