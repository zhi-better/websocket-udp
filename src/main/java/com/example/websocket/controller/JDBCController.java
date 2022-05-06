package com.example.websocket.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.websocket.Session;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class JDBCController {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private Integer numOnlineConnections = 0;

    public void ResetAllUserState(){
        String sql = "UPDATE users SET users.onlinestate = 0;";
        jdbcTemplate.execute(sql);
    }

    //是否具有设备控制权限
    public Boolean hasPermission(Integer userid, Integer devid){
        String sql = "SELECT COUNT(*) FROM resources WHERE userid = "
                        +userid+" AND devid="+devid+";";
        return jdbcTemplate.queryForObject(sql,Integer.class) == 1;
    }

    //用户注册
    public String UserRegister(String name, String password){
        Integer id = 0;
        String sql = "SELECT COUNT(*) FROM users;";
        HashMap<String,String> mapSend = new HashMap<>();
        id = jdbcTemplate.queryForObject(sql,Integer.class) + 1;
        sql = "INSERT INTO users (userid, username, pwd) VALUES("
                +id+",\'"+name+"\', \'"+password+"\');";
        jdbcTemplate.update(sql);
        mapSend.put("msg","ok");
        mapSend.put("userid",id.toString());
        mapSend.put("username",name);
        return mapSend.toString();
    }

    //用户注册
    public String DevsRegister(String name, String password){
        Integer id = 0;
        String sql = "SELECT COUNT(*) FROM devs;";
        HashMap<String,String> mapSend = new HashMap<>();
        id = jdbcTemplate.queryForObject(sql,Integer.class) + 1;
        sql = "INSERT INTO devs (devid, devname, devpassword,onlinestate) VALUES("
                +id+",\'"+name+"\', \'"+password+"\',0);";
        jdbcTemplate.update(sql);
        mapSend.put("msg","ok");
        mapSend.put("devid",id.toString());
        mapSend.put("devname",name);
        return mapSend.toString();
    }

    //增加在线设备数量
    public void AddOnlineConnection(){
        numOnlineConnections++;
    }

    //减少在线设备数量
    public void RemoveOnlineConnection(){
        numOnlineConnections--;
    }

    //获取当前在线的连接数量
    public Integer GetOnlineConnectionsNum(){
        return numOnlineConnections;
    }

    //为用户增加设备
    public String AddDev(Integer userid, Integer devid, String pwd){
        String sql;
        Map<String, Object> temp;
        sql = "SELECT devs.devname,devs.onlinestate FROM devs WHERE devid = "
                +devid+" AND devpassword = \'"+pwd+"\';";
        temp = jdbcTemplate.queryForMap(sql);
        if (temp.isEmpty()) {
            return "{cmd=msgresp,msg=cannot find devid or dev password wrong!}";
        }
        sql = "INSERT INTO resources (userid, devid) VALUES("
                +userid+", "+devid+");";
        jdbcTemplate.update(sql);
        return "{cmd=addresp,msg=ok,devid="+devid+
                ",devname="+temp.get("devname")+
                ",devpassword="+pwd+",onlinestate="+
                temp.get("onlinestate").toString()+"}";
    }

    //为用户删除设备
    public String RemoveDev(Integer userid, Integer devid,String pwd){
        String sql;
        String devname;
        sql = "SELECT devs.devname FROM devs WHERE devid = "
                +devid+" AND devpassword = \'"+pwd+"\';";
        devname = jdbcTemplate.queryForObject(sql, String.class);
        if (devname.isEmpty()) {
            return "{cmd=msgresp,msg=cannot find devid or dev password wrong!}";
        }
        sql = "DELETE FROM resources WHERE resources.userid="
                +userid+" and resources.devid="+devid+";";
        jdbcTemplate.update(sql);
        return "{cmd=removeresp,msg=ok,devid="+devid+",devname="+devname+",devpassword="+pwd+"}";
    }

    //设备登陆
    public List<Map<String, Object>> DevLogin(Integer id, String pwd){
        String sql = "SELECT devs.devid,devs.devname FROM devs WHERE devid = "
                + id + " and devpassword=\'" + pwd + "\';";

        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
        return maps;
    }

    //用户登陆
    public List<Map<String, Object>> UserLogin(Integer id, String pwd,Session session){
        String sql = "select userid,username FROM users where userid = "
                + id + " and pwd = \'" + pwd + "\';";
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);

        if (!maps.isEmpty()){
            //保存对应的 id 和 session
            sql = "update users set users.onlinestate=1 where userid="+id;
            jdbcTemplate.update(sql);
        }
        return maps;
    }

    //获取某用户 id 对应的所有设备
    public List<Map<String, Object>> GetUserDevs(Integer userid){
        String sql =
                "SELECT devs.devid,devs.devname,devs.devpassword FROM devs WHERE devs.devid in (select DISTINCT resources.devid FROM users left join resources on users.userid = resources.userid AND users.userid="
                +userid + ");";
        return jdbcTemplate.queryForList(sql);
    }

    //获取设备 id 对应的所有用户
    public List<Map<String, Object>> GetDevUsersOnline(Integer devid){
        String sql =
                "SELECT users.* FROM users WHERE users.userid in (select DISTINCT resources.userid FROM devs left join resources on devs.devid = resources.devid AND devs.devid="
                        +devid + ") AND users.onlinestate = 1;";
        return jdbcTemplate.queryForList(sql);
    }

    //设置用户的在线状态，防止数据库更新不及时或者未更新导致数据不匹配
    public void SetSQLUserState(Integer id, Boolean state){
        String sql = "update users set users.onlinestate="+state+" where userid="+id+";";
        jdbcTemplate.update(sql);
    }

    //下线用户
    public void UserOffLine(Integer id) {
        String sql = "update users set users.onlinestate=0 where userid="+id;
        jdbcTemplate.update(sql);
    }

    @RequestMapping("/userList")
    public List<Map<String, Object>> userList(){
        String sql = "select * from text";
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
        return maps;
    }

    @RequestMapping("/addUser")
    public String addUser(){
        String sql = "insert into mybatis.text(id,name) value (3, '小明')";
        jdbcTemplate.update(sql);
        return "addUser-ok";
    }
    @RequestMapping("/updateUser/{id}")
    public String updateUser(@PathVariable("id") Integer id){
        String sql = "update mybatis.text set name=? where id=" + id;

        //封装
        Object objects = "小明";
        jdbcTemplate.update(sql, objects);
        return "update-ok";
    }
    @RequestMapping("/deleteUser/{id}")
    public String deleteUser(@PathVariable("id") Integer id){
        String sql = "delete from mybatis.text where id=?";
        jdbcTemplate.update(sql, id);
        return "update-ok";
    }

//    @RequestMapping("/test")
//    public String test(){
//
//        return "ok";
//    }
}
