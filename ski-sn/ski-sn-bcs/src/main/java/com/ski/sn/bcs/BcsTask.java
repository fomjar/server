package com.ski.sn.bcs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.ski.sn.common.CommonDefinition;
import com.ski.sn.common.CommonService;

import fomjar.server.FjMessage;
import fomjar.server.FjMessageWrapper;
import fomjar.server.FjServer;
import fomjar.server.FjServerToolkit;
import fomjar.server.msg.FjDscpMessage;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BcsTask implements FjServer.FjServerTask {
    
    private static final Logger logger = Logger.getLogger(BcsTask.class);
    
    private Map<String, FjDscpMessage>  cache_request;
    private Map<String, String>         cache_vcode;
    private Map<Long, JSONArray>        cache_message;
    private MonitorUserState            monitor_userstate;
    
    @Override
    public void initialize(FjServer server) {
        cache_request   = new ConcurrentHashMap<String, FjDscpMessage>();
        cache_vcode     = new ConcurrentHashMap<String, String>();
        cache_message   = new ConcurrentHashMap<Long, JSONArray>();
        monitor_userstate = new MonitorUserState();
        monitor_userstate.start();
    }

    @Override
    public void destroy(FjServer server) {
        cache_request.clear();
        cache_vcode.clear();
        cache_message.clear();
        monitor_userstate.close();
    }

    @Override
    public void onMessage(FjServer server, FjMessageWrapper wrapper) {
        FjMessage msg = wrapper.message();
        if (!(msg instanceof FjDscpMessage)) {
            logger.error("unsupported format message, raw data:\n" + wrapper.attachment("raw"));
            return;
        }

        FjDscpMessage dmsg = (FjDscpMessage) msg;
        try {
            if (dmsg.fs().startsWith("wsi")) { // 用户侧请求
                logger.info(String.format("[ REQUEST  ] - %s:%s:0x%08X", dmsg.fs(), dmsg.sid(), dmsg.inst()));
                processRequest(dmsg);
            } else { // 平台侧响应
                logger.info(String.format("[ RESPONSE ] - %s:%s:0x%08X", dmsg.fs(), dmsg.sid(), dmsg.inst()));
                if (cache_request.containsKey(dmsg.sid())) processResponse(dmsg, cache_request.remove(dmsg.sid()));
            }
        } catch (Exception e) {
            logger.error("unexpected error occurred by message: " + dmsg, e);
            CommonService.response(dmsg, CommonDefinition.CODE.CODE_ERROR, "发生了一个奇怪的错误😱");
        }
    }
    
    private void catchResponse(FjDscpMessage request) {
        cache_request.put(request.sid(), request);
    }
    
    public void processRequest(FjDscpMessage request) {
        switch (request.inst()) {
        case CommonDefinition.ISIS.INST_APPLY_AUTHORIZE:
            requestApplyAuthorize(request);
            break;
        case CommonDefinition.ISIS.INST_APPLY_VERIFY:
            requestApplyVerify(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_MESSAGE:
            requestQueryMessage(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_MESSAGE_FOCUS:
            requestQueryMessageFocus(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_MESSAGE_REPLY:
            requestQueryMessageReply(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_MESSAGE:
            requestUpdateMessage(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_USER:
            requestUpdateUser(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_USER_STATE:
            requestUpdateUserState(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_MESSAGE_FOCUS:
            requestUpdateMessageFocus(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_MESSAGE_REPLY:
            requestUpdateMessageReply(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY:
            requestUpdateActivity(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_ROLE:
            requestUpdateActivityRole(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_PLAYER:
            requestUpdateActivityPlayer(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE:
            requestUpdateActivityModule(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_PRIVILEGE:
            requestUpdateActivityModulePrivilege(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE:
            requestUpdateActivityModuleVote(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE_ITEM:
            requestUpdateActivityModuleVoteItem(request);
            break;
        case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE_PLAYER:
            requestUpdateActivityModuleVotePlayer(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY:
            requestQueryActivity(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_ROLE:
            requestQueryActivityRole(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_PLAYER:
            requestQueryActivityPlayer(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE:
            requestQueryActivityModule(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_PRIVILEGE:
            requestQueryActivityModulePrivilege(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE:
            requestQueryActivityModuleVote(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE_ITEM:
            requestQueryActivityModuleVoteItem(request);
            break;
        case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE_PLAYER:
            requestQueryActivityModuleVotePlayer(request);
            break;
        default:
            logger.error("illegal inst: " + request);
            CommonService.response(request, CommonDefinition.CODE.CODE_ILLEGAL_INST, "未知指令");
            break;
        }
    }
    
    public void processResponse(FjDscpMessage response, FjDscpMessage request) {
        JSONObject args = response.argsToJsonObject();
        if (CommonDefinition.CODE.CODE_SUCCESS != CommonService.getResponseCode(response)) {
            logger.error(String.format("内部错误! request = %s, response = %s", request, response));
            args.put("desc", "发生了一个奇怪的错误😱");
        } else {
            switch(response.inst()) {
            case CommonDefinition.ISIS.INST_APPLY_AUTHORIZE:
                responseApplyAuthorize(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_USER_STATE:
                responseQueryUserState(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_MESSAGE:
                responseQueryMessage(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_MESSAGE_FOCUS:
                responseQueryMessageFocus(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_MESSAGE_REPLY:
                responseQueryMessageReply(args, request);
                break;
            case CommonDefinition.ISIS.INST_UPDATE_MESSAGE:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_USER_STATE:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_MESSAGE_FOCUS:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_MESSAGE_REPLY:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_ROLE:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_PLAYER:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_PRIVILEGE:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE_ITEM:
                break;
            case CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE_PLAYER:
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY:
                responseQueryActivity(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_ROLE:
                responseQueryActivityRole(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_PLAYER:
                responseQueryActivityPlayer(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE:
                responseQueryActivityModule(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_PRIVILEGE:
                responseQueryActivityModulePrivilege(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE:
                responseQueryActivityModuleVote(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE_ITEM:
                responseQueryActivityModuleVoteItem(args, request);
                break;
            case CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE_PLAYER:
                responseQueryActivityModuleVotePlayer(args, request);
                break;
            default:
                if (CommonDefinition.CODE.CODE_SUCCESS != CommonService.getResponseCode(response)) {
                    logger.error(String.format("内部错误! request = %s, response = %s", request, response));
                    args.put("desc", "发生了一个奇怪的错误😱");
                }
                break;
            }
        }
        response.json().put("fs",   FjServerToolkit.getAnyServer().name());
        response.json().put("ts",   request.fs());
        response.json().put("args", args);
        FjServerToolkit.getAnySender().send(response);
    }
    
    private void requestApplyAuthorize(FjDscpMessage request) {
        JSONObject args = request.argsToJsonObject();
        if (args.has("phone")) {    // 手动登录
            if (!illegalArgs(request, "phone", "pass")) return;
            
            String phone = args.getString("phone");
            String pass  = args.getString("pass");
            
            JSONObject args_cdb = new JSONObject();
            args_cdb.put("phone", phone);
            args_cdb.put("pass",  pass);
            CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_APPLY_AUTHORIZE, args_cdb);
            catchResponse(request);
        } else {    // 自动登录
            if (!illegalArgs(request, "token", "uid")) return;
            
            String token = args.getString("token");
            long   uid   = args.getLong("uid");
            
            JSONObject args_cdb = new JSONObject();
            args_cdb.put("token", token);
            args_cdb.put("uid",   uid);
            CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_USER_STATE, args_cdb);
            catchResponse(request);
        }
    }
    
    private void responseApplyAuthorize(JSONObject args, FjDscpMessage request) {
        if (0 == args.getJSONArray("desc").size()) {
            logger.error("用户名或密码错误: " + request);
            args.put("code", CommonDefinition.CODE.CODE_UNAUTHORIZED);
            args.put("desc", "用户名或密码错误");
            return;
        }
        
        JSONArray user = args.getJSONArray("desc").getJSONArray(0);
        String token = UUID.randomUUID().toString().replace("-", "");
        {
            JSONObject args_cdb = new JSONObject();
            args_cdb.put("uid",         Long.parseLong(user.getString(0)));
            args_cdb.put("state",       CommonDefinition.Field.USER_STATE_ONLINE);
            args_cdb.put("token",       token);
            CommonService.requesta("cdb", CommonDefinition.ISIS.INST_UPDATE_USER_STATE, args_cdb);
        }
        
        JSONObject desc = new JSONObject();
        int i = 0;
        desc.put("uid",     Long.parseLong(user.getString(i++)));
        desc.put("create",  user.getString(i++));
        desc.put("pass",    user.getString(i++));
        desc.put("phone",   user.getString(i++));
        desc.put("email",   user.getString(i++));
        desc.put("name",    user.getString(i++));
        desc.put("cover",   user.getString(i++));
        desc.put("gender",  Integer.parseInt(user.getString(i++)));
        desc.put("token",   token);
        
        args.put("desc", desc);
    }
    
    private void responseQueryUserState(JSONObject args, FjDscpMessage request) {
        if (0 == args.getJSONArray("desc").size()) {
            logger.error("缓存已失效，请重新登录: " + request);
            args.put("code", CommonDefinition.CODE.CODE_UNAUTHORIZED);
            args.put("desc", "缓存已失效，请重新登录");
            return;
        }
        JSONArray user = args.getJSONArray("desc").getJSONArray(0);
        {
            JSONObject args_cdb = new JSONObject();
            args_cdb.put("uid",         Long.parseLong(user.getString(0)));
            args_cdb.put("state",       CommonDefinition.Field.USER_STATE_ONLINE);
            CommonService.requesta("cdb", CommonDefinition.ISIS.INST_UPDATE_USER_STATE, args_cdb);
        }
        
        JSONObject desc = new JSONObject();
        int i = 0;
        desc.put("uid",     Long.parseLong(user.getString(i++)));
        desc.put("create",  user.getString(i++));
        desc.put("pass",    user.getString(i++));
        desc.put("phone",   user.getString(i++));
        desc.put("email",   user.getString(i++));
        desc.put("name",    user.getString(i++));
        desc.put("cover",   user.getString(i++));
        desc.put("gender",  Integer.parseInt(user.getString(i++)));
        desc.put("token",   request.argsToJsonObject().getString("token"));
        
        args.put("desc", desc);
    }
    
    private void requestApplyVerify(FjDscpMessage request) {
        if (!illegalArgs(request, "type")) return;
        
        JSONObject args = request.argsToJsonObject();
        switch (args.getString("type")) {
        case "phone":
            if (!illegalArgs(request, "phone")) break;
            
            String phone = args.getString("phone");
            if (args.has("vcode")) {
                String vcode = args.getString("vcode");
                if (vcode.equals(cache_vcode.get(phone))) {
                    CommonService.response(request, CommonDefinition.CODE.CODE_SUCCESS, null);
                } else {
                    CommonService.response(request, CommonDefinition.CODE.CODE_ERROR, "验证失败");
                }
            } else {
                String time  = String.valueOf(System.currentTimeMillis());
                String vcode = time.substring(time.length() - 4);
                JSONObject args_ura = new JSONObject();
                args_ura.put("phone", phone);
                args_ura.put("vcode", vcode);
                CommonService.requesta("ura", request.sid(), CommonDefinition.ISIS.INST_APPLY_VERIFY, args_ura);
                catchResponse(request);
                cache_vcode.put(phone, vcode); // for verify
            }
            break;
        }
    }
    
    private void requestQueryMessage(FjDscpMessage request) {
        if (!illegalArgs(request, "uid", "lat", "lng", "pos", "len")) return;
        
        JSONObject args = request.argsToJsonObject();
        long    uid = args.getLong("uid");
        double  lat = args.getDouble("lat");
        double  lng = args.getDouble("lng");
        int     pos = args.getInt("pos");
        int     len = args.getInt("len");
        
        if (0 == len) {
            CommonService.response(request, CommonDefinition.CODE.CODE_ILLEGAL_ARGS, "参数错误");
            return;
        }
        
        if (0 == pos || !cache_message.containsKey(uid) || 0 == cache_message.get(uid).size()) { // generate message list
            JSONObject args_cdb = new JSONObject();
            args_cdb.put("lat",     lat);
            args_cdb.put("lng",     lng);
            args_cdb.put("geohash", GeoHash.encode(lat, lng));
            CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_MESSAGE, args_cdb);
            catchResponse(request);
        } else {
            JSONArray msgs = cache_message.get(uid);
            JSONArray desc = new JSONArray();
            for (int i = pos; i < pos + len && i < msgs.size(); i++) desc.add(msgs.get(i));
            CommonService.response(request, CommonDefinition.CODE.CODE_SUCCESS, desc);
        }
    }
    
    private void responseQueryMessage(JSONObject args, FjDscpMessage request) {
        JSONObject args_req = request.argsToJsonObject();
        long    uid = args_req.getLong("uid");
        int     pos = args_req.getInt("pos");
        int     len = args_req.getInt("len");
        
        String  desc = args.getJSONArray("desc").getString(0);
        if ("null".equals(desc)) {
            logger.info("no message queried for request: " + request);
            args.put("desc", new JSONArray());
            return;
        }
        
        JSONArray msgs = new JSONArray();
        for (String message : desc.split("'\n", -1)) {
            String[] fields = message.split("'\t", -1);
            int i = 0;
            JSONObject msg = new JSONObject();
            msg.put("mid",      fields[i++]);
            msg.put("time",     fields[i++]);
            msg.put("distance", Integer.parseInt(fields[i++]));
            msg.put("second",   Integer.parseInt(fields[i++]));
            msg.put("focus",    Integer.parseInt(fields[i++]));
            msg.put("reply",    Integer.parseInt(fields[i++]));
            msg.put("uid",      Long.parseLong(fields[i++]));
            msg.put("uname",    fields[i++]);
            msg.put("ucover",   fields[i++]);
            msg.put("ugender",  Integer.parseInt(fields[i++]));
            msg.put("mtype",    Integer.parseInt(fields[i++]));
            msg.put("mtext",    fields[i++]);
            msg.put("mimage",   fields[i++]);
            msgs.add(msg);
        }
        cache_message.put(uid, msgs);
        
        JSONArray msgs_rsp = cache_message.get(uid);
        JSONArray desc_rsp = new JSONArray();
        for (int i = pos; i < pos + len && i < msgs_rsp.size(); i++) desc_rsp.add(msgs_rsp.get(i));
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryMessageFocus(FjDscpMessage request) {
        if (!illegalArgs(request, "mid")) return;
        
        JSONObject args = request.argsToJsonObject();
        String mid = args.getString("mid");
        String geohash6 = mid.substring(0, 6);
        
        JSONObject args_cdb = new JSONObject();
        args_cdb.put("mid", mid);
        args_cdb.put("geohash6", geohash6);
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_MESSAGE_FOCUS, args_cdb);
        catchResponse(request);
    }
    
    private void responseQueryMessageFocus(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject focus = new JSONObject();
            int i = 0;
            focus.put("mid",        fields.getString(i++));
            focus.put("uid",        Long.parseLong(fields.getString(i++)));
            focus.put("uname",      fields.getString(i++));
            focus.put("ucover",     fields.getString(i++));
            focus.put("ugender",    Integer.parseInt(fields.getString(i++)));
            focus.put("time",       fields.getString(i++));
            focus.put("type",       Integer.parseInt(fields.getString(i++)));
            desc_rsp.add(focus);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryMessageReply(FjDscpMessage request) {
        if (!illegalArgs(request, "mid")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_MESSAGE_REPLY, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryMessageReply(JSONObject args, FjDscpMessage request) {
        String desc = args.getJSONArray("desc").getString(0);
        if ("null".equals(desc)) {
            args.put("desc", new JSONArray());
            return;
        }
        
        JSONArray desc_rsp = new JSONArray();
        for (String message : desc.split("'\n", -1)) {
            String[] fields = message.split("'\t", -1);
            JSONObject reply = new JSONObject();
            int i = 0;
            reply.put("mid",        fields[i++]);
            reply.put("time",       fields[i++]);
            reply.put("uid",        Long.parseLong(fields[i++]));
            reply.put("uname",      fields[i++]);
            reply.put("ucover",     fields[i++]);
            reply.put("ugender",    Integer.parseInt(fields[i++]));
            reply.put("mtype",      Integer.parseInt(fields[i++]));
            reply.put("mtext",      fields[i++]);
            reply.put("mimage",     fields[i++]);
            desc_rsp.add(reply);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestUpdateMessage(FjDscpMessage request) {
        if (!illegalArgs(request, "uid", "coosys", "lat", "lng", "type")) return;
        
        JSONObject args = request.argsToJsonObject();
        double  lat     = args.getDouble("lat");
        double  lng     = args.getDouble("lng");
        String  geohash = GeoHash.encode(lat, lng);
        String  mid     = String.format("%s:%d", geohash, System.currentTimeMillis());
        
        args.put("geohash", geohash);
        args.put("mid",     mid);
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_MESSAGE, args);
        catchResponse(request);
    }
    
    private void requestUpdateUser(FjDscpMessage request) {
        JSONObject args = request.argsToJsonObject();
        if (args.has("phone") && args.has("vcode")) {
            String phone = args.getString("phone");
            String vcode = args.getString("vcode");
            if (!vcode.equals(cache_vcode.get(phone))) {
                CommonService.response(request, CommonDefinition.CODE.CODE_ERROR, "验证失败");
                return;
            }
            cache_vcode.remove(phone);
        }
        
        JSONObject args_cdb = new JSONObject();
        if (args.has("uid"))    args_cdb.put("uid",     args.getInt("uid"));
        if (args.has("pass"))   args_cdb.put("pass",    args.getString("pass"));
        if (args.has("phone"))  args_cdb.put("phone",   args.getString("phone"));
        if (args.has("name"))   args_cdb.put("name",    args.getString("name"));
        if (args.has("cover"))  args_cdb.put("cover",   args.getString("cover")); // data:image/jpeg;base64,/9j/4SxpRXhpZgA...
        if (args.has("gender")) args_cdb.put("gender",  args.getInt("gender"));
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_USER, args_cdb);
        catchResponse(request);
    }
    
    private void requestUpdateUserState(FjDscpMessage request) {
        if (!illegalArgs(request, "uid")) return;
        
        JSONObject args = request.argsToJsonObject();
        
        int uid = args.getInt("uid");
        monitor_userstate.notify(uid);
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_USER_STATE, args);
        catchResponse(request);
    }
    
    private void requestUpdateMessageFocus(FjDscpMessage request) {
        if (!illegalArgs(request, "uid", "mid", "type")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_MESSAGE_FOCUS, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestUpdateMessageReply(FjDscpMessage request) {
        if (!illegalArgs(request, "uid", "mid", "coosys", "lat", "lng", "type")) return;
        
        JSONObject args = request.argsToJsonObject();
        double  lat     = args.getDouble("lat");
        double  lng     = args.getDouble("lng");
        String  geohash = GeoHash.encode(lat, lng);
        String  rid     = String.format("%s:%d", geohash, System.currentTimeMillis());
        
        args.put("rid",     rid);
        args.put("geohash", geohash);
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_MESSAGE_REPLY, args);
        catchResponse(request);
    }
    
    private void requestUpdateActivity(FjDscpMessage request) {
        JSONObject args = request.argsToJsonObject();
        if (args.has("lat") && args.has("lng")) {
            double  lat     = args.getDouble("lat");
            double  lng     = args.getDouble("lng");
            String  geohash = GeoHash.encode(lat, lng);
            args.put("geohash", geohash);
        }
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY, args);
        catchResponse(request);
    }
    
    private void requestUpdateActivityRole(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "arsn", "name", "apply", "count")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_ROLE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestUpdateActivityPlayer(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "uid", "arsn")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_PLAYER, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestUpdateActivityModule(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn", "type", "title")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestUpdateActivityModulePrivilege(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn", "arsn", "privilege")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_PRIVILEGE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestUpdateActivityModuleVote(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn", "select", "anonym", "item")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestUpdateActivityModuleVoteItem(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn", "amvisn", "arg0")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE_ITEM, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestUpdateActivityModuleVotePlayer(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn", "amvisn", "uid", "result")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_UPDATE_ACTIVITY_MODULE_VOTE_PLAYER, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void requestQueryActivity(FjDscpMessage request) {
        JSONObject args = request.argsToJsonObject();
        if (args.has("owner")) {
            // do nothing
        } else {
            if (!illegalArgs(request, "lat", "lng")) return;
            
            double  lat     = args.getDouble("lat");
            double  lng     = args.getDouble("lng");
            String  geohash = GeoHash.encode(lat, lng);
            args.put("geohash", geohash);
        }
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY, args);
        catchResponse(request);
    }
    
    private void responseQueryActivity(JSONObject args, FjDscpMessage request) {
        String desc = args.getJSONArray("desc").getString(0);
        if ("null".equals(desc)) {
            args.put("desc", new JSONArray());
            return;
        }
        
        JSONArray desc_rsp = new JSONArray();
        for (String message : desc.split("'\n", -1)) {
            String[] fields = message.split("'\t", -1);
            JSONObject activity = new JSONObject();
            int i = 0;
            activity.put("aid",     Integer.parseInt(fields[i++]));
            activity.put("owner",   Integer.parseInt(fields[i++]));
            activity.put("uname",   fields[i++]);
            activity.put("ucover",  fields[i++]);
            activity.put("ugender", Integer.parseInt(fields[i++]));
            activity.put("acreate", fields[i++]);
            activity.put("atitle",  fields[i++]);
            activity.put("atext",   fields[i++]);
            activity.put("aimage",  fields[i++]);
            activity.put("abegin",  fields[i++]);
            activity.put("aend",    fields[i++]);
            activity.put("astate",  Integer.parseInt(fields[i++]));
            desc_rsp.add(activity);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryActivityRole(FjDscpMessage request) {
        if (!illegalArgs(request, "aid")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY_ROLE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryActivityRole(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject role = new JSONObject();
            int i = 0;
            role.put("aid",     Integer.parseInt(fields.getString(i++)));
            role.put("arsn",    Integer.parseInt(fields.getString(i++)));
            role.put("name",    fields.getString(i++));
            role.put("apply",   Integer.parseInt(fields.getString(i++)));
            role.put("count",   Integer.parseInt(fields.getString(i++)));
            desc_rsp.add(role);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryActivityPlayer(FjDscpMessage request) {
        if (!illegalArgs(request, "aid")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY_PLAYER, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryActivityPlayer(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject role = new JSONObject();
            int i = 0;
            role.put("aid",     Integer.parseInt(fields.getString(i++)));
            role.put("uid",     Integer.parseInt(fields.getString(i++)));
            role.put("uname",   fields.getString(i++));
            role.put("ucover",  fields.getString(i++));
            role.put("ugender", Integer.parseInt(fields.getString(i++)));
            role.put("arsn",    Integer.parseInt(fields.getString(i++)));
            role.put("time",    fields.getString(i++));
            desc_rsp.add(role);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryActivityModule(FjDscpMessage request) {
        if (!illegalArgs(request, "aid")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryActivityModule(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject role = new JSONObject();
            int i = 0;
            role.put("aid",     Integer.parseInt(fields.getString(i++)));
            role.put("amsn",    Integer.parseInt(fields.getString(i++)));
            role.put("type",    Integer.parseInt(fields.getString(i++)));
            role.put("title",   fields.getString(i++));
            role.put("text",    fields.getString(i++));
            desc_rsp.add(role);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryActivityModulePrivilege(FjDscpMessage request) {
        if (!illegalArgs(request, "aid")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_PRIVILEGE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryActivityModulePrivilege(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject role = new JSONObject();
            int i = 0;
            role.put("aid",         Integer.parseInt(fields.getString(i++)));
            role.put("amsn",        Integer.parseInt(fields.getString(i++)));
            role.put("arsn",        Integer.parseInt(fields.getString(i++)));
            role.put("privilege",   Integer.parseInt(fields.getString(i++)));
            desc_rsp.add(role);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryActivityModuleVote(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryActivityModuleVote(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject role = new JSONObject();
            int i = 0;
            role.put("aid",     Integer.parseInt(fields.getString(i++)));
            role.put("amsn",    Integer.parseInt(fields.getString(i++)));
            role.put("select",  Integer.parseInt(fields.getString(i++)));
            role.put("anonym",  Integer.parseInt(fields.getString(i++)));
            role.put("item",    Integer.parseInt(fields.getString(i++)));
            desc_rsp.add(role);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryActivityModuleVoteItem(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE_ITEM, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryActivityModuleVoteItem(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject role = new JSONObject();
            int i = 0;
            role.put("aid",     Integer.parseInt(fields.getString(i++)));
            role.put("amsn",    Integer.parseInt(fields.getString(i++)));
            role.put("amvisn",  Integer.parseInt(fields.getString(i++)));
            role.put("arg0",    fields.getString(i++));
            role.put("arg1",    fields.getString(i++));
            role.put("arg2",    fields.getString(i++));
            role.put("arg3",    fields.getString(i++));
            role.put("arg4",    fields.getString(i++));
            role.put("arg5",    fields.getString(i++));
            role.put("arg6",    fields.getString(i++));
            role.put("arg7",    fields.getString(i++));
            role.put("arg8",    fields.getString(i++));
            role.put("arg9",    fields.getString(i++));
            desc_rsp.add(role);
        }
        args.put("desc", desc_rsp);
    }
    
    private void requestQueryActivityModuleVotePlayer(FjDscpMessage request) {
        if (!illegalArgs(request, "aid", "amsn")) return;
        
        CommonService.requesta("cdb", request.sid(), CommonDefinition.ISIS.INST_QUERY_ACTIVITY_MODULE_VOTE_PLAYER, request.argsToJsonObject());
        catchResponse(request);
    }
    
    private void responseQueryActivityModuleVotePlayer(JSONObject args, FjDscpMessage request) {
        JSONArray desc = args.getJSONArray("desc");
        if (0 == desc.size()) return;
        
        JSONArray desc_rsp = new JSONArray();
        for (Object obj : args.getJSONArray("desc")) {
            JSONArray fields = (JSONArray) obj;
            JSONObject role = new JSONObject();
            int i = 0;
            role.put("aid",     Integer.parseInt(fields.getString(i++)));
            role.put("amsn",    Integer.parseInt(fields.getString(i++)));
            role.put("amvisn",  Integer.parseInt(fields.getString(i++)));
            role.put("uid",     Integer.parseInt(fields.getString(i++)));
            role.put("uname",   fields.getString(i++));
            role.put("ucover",  fields.getString(i++));
            role.put("ugender", Integer.parseInt(fields.getString(i++)));
            role.put("result",  Integer.parseInt(fields.getString(i++)));
            role.put("time",    fields.getString(i++));
            desc_rsp.add(role);
        }
        args.put("desc", desc_rsp);
    }
    
    private static boolean illegalArgs(FjDscpMessage request, String... keys) {
        JSONObject args = request.argsToJsonObject();
        for (String key : keys) {
            if (!args.has(key)) {
                logger.error("illegal arguments, no: " + key);
                CommonService.response(request, CommonDefinition.CODE.CODE_ILLEGAL_ARGS, "参数错误");
                return false;
            }
        }
        return true;
    }

}
