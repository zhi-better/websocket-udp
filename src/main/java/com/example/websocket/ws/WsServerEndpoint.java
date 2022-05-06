package com.example.websocket.ws;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.websocket.controller.JDBCController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.ip.udp.UnicastReceivingChannelAdapter;
import org.springframework.integration.ip.udp.UnicastSendingMessageHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.*;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/**
 * 注意，此处的websocket主要是为了和设备仪器进行通信，所以连接时需要附带的参数是设备的 userid 和对应的设备密码
 */

//访问过程中输入链接: ws://127.0.0.1:8080/ws/1/123456
@ServerEndpoint("/ws/{id}/{pwd}")
@Component
public class WsServerEndpoint {

    private static HashMap<Integer, Session> mHashMapUser =
            new HashMap<>();   //用于 userid 和 session 的关系保存
    private static HashMap<Integer, List<Session>> mHashMapDevUserOnlineList =
            new HashMap<>();    //用于设备 userid 和对应的在线用户关系保存
    private static HashMap<Integer, UnicastSendingMessageHandler> mHashMapDevs =
            new HashMap<>();    //用于 userid 和 udpsend... 的关系保存
    private static JDBCController jdbcController;       //用于数据库操作

    //部分非静态私有变量
    private Integer userid;             //本次连接的用户 userid
    private Session session;        //本次连接的 session

    // 注入的时候，给类的 service 注入
    @Autowired
    public void setChatService(JDBCController jdbcController) {
        WsServerEndpoint.jdbcController = jdbcController;
    }

//    ================================ websocket =========================================

    //连接成功
    @OnOpen
    public void onOpen(Session session, @PathParam("id")Integer id, @PathParam("pwd")String pwd) throws IOException {

        //设置本地的变量
        this.session =session;
        this.userid = id;

        //首先判断是否登陆成功，如果登陆成功，返回对应的内容
        List<Map<String, Object>> mapList = jdbcController.UserLogin(id, pwd, session);

        JSONObject object = new JSONObject();
        if(mapList.size()!=0){
            object.put("id", userid);
            object.put("msg", "ok");
            sendWebsocketMessage(object.toString());
        }
        else {
            object.put("id", userid);
            object.put("msg", "login failed!");
            return;
        }

        //发送用户的信息
        object.clear();
        object.put("cmd", "userinfo");
        object.put("userid", userid);
        object.put("username", mapList.get(0).get("username"));
        sendWebsocketMessage(object.toJSONString());

        //获取所有设备信息
        List<Map<String, Object>> lstMapuserDevs = jdbcController.GetUserDevs(userid);
        object.clear();
        Integer devid = 0;
        for (int i = 0; i<lstMapuserDevs.size(); i++){
            devid = Integer.parseInt(lstMapuserDevs.get(i).get("devid").toString());
            object.put("devid", devid);
            object.put("cmd", "devstate");
            object.put("onlinestate", jdbcController.GetDevState(devid) ? "true" : "false");
            sendWebsocketMessage(object.toJSONString());
            object.clear();
        }

    }

    //连接关闭
    @OnClose
    public void onClose() {

        if (mHashMapUser.containsKey(userid)){
            mHashMapUser.remove(userid);
        }

        System.out.println("连接关闭");
    }

    //接收到消息
    @OnMessage
    public String onMsg(String text, Session session) throws IOException {

        return "servet 发送：" + text;
    }

    //发送消息
    public void sendWebsocketMessage(String message) throws IOException {
        session.getBasicRemote().sendText(message);
    }

    @OnError
    public void onError(Throwable t) {
        System.out.println(" websocket 连接出错！");
        System.out.println(t);
    }
//    ================================================================================

//    ====================================== udp ====================================

    //    此处应该是初始化 udp 服务器的位置，需要指定对应的端口号
    @Bean
    public UnicastReceivingChannelAdapter getUnicastReceivingChannelAdapter() {
        UnicastReceivingChannelAdapter adapter =
                new  UnicastReceivingChannelAdapter(4567);//实例化一个udp 4567端口
        adapter.setOutputChannelName("udp");

        return adapter;
    }

    //    此处是发送消息的处理内容
    public void sendUdpMessage(String ipAddress, Integer port, String message) {
        UnicastSendingMessageHandler unicastSendingMessageHandler = new UnicastSendingMessageHandler(ipAddress, port);
        unicastSendingMessageHandler.handleMessage(MessageBuilder.withPayload(message).build());
    }

    @ServiceActivator(inputChannel="udp")
    public void udpMessageHandle(Message<?> message) {

        //首先获取发送信息的 ip 和端口内容
        MessageHeaders headers = message.getHeaders();
        String ipAddress = headers.get("ip_address").toString();
        Integer port = Integer.parseInt(headers.get("ip_port").toString());

        //然后解析对应的命令内容
        JSONObject jsonMsg = JSON.parseObject(new String((byte[])message.getPayload()));
        Integer devid = jsonMsg.getInteger("id");

        switch (jsonMsg.get("cmd").toString())
        {
            case "on":
            {
                //为哈希表中增加对应的客户端连接的类
                UnicastSendingMessageHandler unicastSendingMessageHandler = new UnicastSendingMessageHandler(ipAddress, port);
                mHashMapDevs.put(devid, unicastSendingMessageHandler);
                List<Session> sessionList = new LinkedList<>();
                List<Map<String, Object>> mapList = jdbcController.GetDevUsersOnline(userid);
                //找到对应的所有在线的用户的 session 然后保存起来
                for( int i = 0 ; i < mapList.size() ; i++) {    //内部不锁定，效率最高，但在多线程要考虑并发操作的问题。
                    sessionList.add(mHashMapUser.get(mapList.get(i).keySet()));
                }
                mHashMapDevUserOnlineList.put(devid, sessionList);

                //首先获取对应的连接 ip 地址和端口
                System.out.println("connection accepted! ");
                System.out.println("ip: " + ipAddress);
                System.out.println("port: " + port);
                System.out.println("connections: " + mHashMapDevs.size());

                JSONObject object = new JSONObject();
                object.put("id", devid);
                object.put("cmd", "on_resp");
                sendUdpMessage(ipAddress, port, object.toJSONString());
            }
                break;
            case "off":
            {
                mHashMapDevs.remove(jsonMsg.get("id"));
                System.out.println("The device with userid = " + userid + "is offline! ");

            }
                break;
            case "heart_beat":
            {
                JSONObject object = new JSONObject();
                object.put("id", devid);
                object.put("cmd", "heart_resp");
                sendUdpMessage(ipAddress, port, object.toJSONString());
                System.out.println("Receive a heartbeat packet with userid = " + userid);
            }
                break;
            case "state_ack":
            {
                //首先查询对应的 userid 是谁的查询内容
                Integer userid = jsonMsg.getInteger("userid");
                try {
                    mHashMapUser.get(userid).getBasicRemote().sendText(message.toString());
                    System.out.println("Device details have been sent to user with userid = " + this.userid);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
                break;
            case "state_chg":
            {
                //首先查询对应的 userid 是谁的查询内容
                List<Session> sessionList = mHashMapDevUserOnlineList.get(devid);
                System.out.println("Sending notification message: " + message.toString());
                Iterator<Session> iterator = sessionList.iterator();
                while (iterator.hasNext()) {
                    Session good = iterator.next();
                    try {
                        good.getBasicRemote().sendText(message.toString());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("A total of "+ sessionList.size() + " online users, the notification message was sent successfully");
            }
                break;
            case "info_ack":
            {
                ;

            }
                break;
            default:
                break;
        }
    }

//    @Transformer(inputChannel="udp",outputChannel="udpString")
//    public JSON transformer(Message<?> message) {
//        MessageHeaders headers = message.getHeaders();
//        String ipAddress = headers.get("ip_address").toString();
//        Integer port = Integer.parseInt(headers.get("ip_port").toString());
//        //为哈希表中增加对应的客户端连接的类
//        UnicastSendingMessageHandler unicastSendingMessageHandler = new UnicastSendingMessageHandler(ipAddress, port);
//        mHashMapDevs.put(mHashMapDevs.size(), unicastSendingMessageHandler);
//        //首先获取对应的连接 ip 地址和端口
//        System.out.println("connection accepted! ");
//        System.out.println("ip: " + ipAddress);
//        System.out.println("port: " + port);
//        System.out.println("connections: " + mHashMapDevs.size());
//
//        JSON jsonMsg = JSON.parseArray(new String((byte[])message.getPayload()));
//
//        return jsonMsg;//把接收的数据转化为字符串
//    }
//
//    //    增加一个过滤器，主要用于检测 udp 消息头部等内容是否合法
//    @Filter(inputChannel="udpString",outputChannel="udpFilter")
//    public boolean filter(JSON message) {
//
//
//        return message.startsWith("abc");//如果接收数据开头不是abc直接过滤掉
//    }
//
//    //
//    @Router(inputChannel="udpFilter")
//    public String routing(String message) {
//        if(message.contains("1")) {//当接收数据包含数字1时
//            return "udpRoute1";
//        }
//        else {
//            return "udpRoute2";
//        }
//    }
//    @ServiceActivator(inputChannel="udpRoute2")
//    public void udpMessageHandle2(String message) {
//
//        System.out.println("udp2:" +message);
//    }
//
//    //@Bean
//    public TcpNetServerConnectionFactory getServerConnectionFactory() {
//        TcpNetServerConnectionFactory serverConnectionFactory = new TcpNetServerConnectionFactory(1234);
//        serverConnectionFactory.setSerializer(new ByteArrayRawSerializer());
//        serverConnectionFactory.setDeserializer(new ByteArrayRawSerializer());
//        serverConnectionFactory.setLookupHost(false);
//        return serverConnectionFactory;
//    }
//
//
//    @Bean
//    public TcpReceivingChannelAdapter getReceivingChannelAdapter() {
//        TcpReceivingChannelAdapter receivingChannelAdapter = new TcpReceivingChannelAdapter();
//        receivingChannelAdapter.setConnectionFactory(getServerConnectionFactory());
//        receivingChannelAdapter.setOutputChannelName("tcp");
//        return receivingChannelAdapter;
//    }
//
//
//    @ServiceActivator(inputChannel="tcp")
//    public void messageHandle(Message<?> message) {
//
//        System.out.println(new String((byte[])message.getPayload()));
//    }


//    ================================================================================
}
