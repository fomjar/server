package com.ski.vcg.web.filter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import org.apache.log4j.Logger;

import com.ski.vcg.common.CommonDefinition;
import com.ski.vcg.common.CommonService;

import fomjar.server.FjServer;
import fomjar.server.FjServerToolkit;
import fomjar.server.msg.FjDscpMessage;
import fomjar.server.msg.FjHttpRequest;
import fomjar.server.msg.FjHttpResponse;
import fomjar.server.web.FjWebFilter;
import net.sf.json.JSONObject;

public class Filter4CommonPreprocess extends FjWebFilter {

    private static final Logger logger = Logger.getLogger(Filter4CommonPreprocess.class);

    @Override
    public boolean filter(FjHttpResponse response, FjHttpRequest request, SocketChannel conn, FjServer server) {
        // 首页重定向
        if (request.path().equals("/vcg") || request.path().equals("/vcg/")) {
            redirect(response, "/vcg/index.html");
            return false;
        }
        // 首页重定向
        if (request.path().equals("/omc") ||request.path().equals("/omc/")) {
            redirect(response, "/omc/index.html");
            return false;
        }

        // 请求页面
        if (request.path().endsWith(".html")) {
            if (request.path().startsWith("/vcg"))  return filterVcg(response, request, conn);
            if (request.path().startsWith("/omc"))  return filterOmc(response, request);
        }

        // 请求接口
        if (request.path().equals(Filter6CommonInterface.URL_KEY)) return filterInterface(response, request);

        return true;
    }

    private static boolean filterVcg(FjHttpResponse response, FjHttpRequest request, SocketChannel conn) {
        JSONObject args = request.argsToJson();
        int user = -1;

        // 用户预处理
        if (request.cookie().containsKey("user")) user = Integer.parseInt(request.cookie().get("user"), 16);
        else if (args.has("user")) {
            Object obj = args.get("user");
            if (obj instanceof Integer) user = (int) obj;
            else user = Integer.parseInt(obj.toString(), 16);
            response.setcookie("user", Integer.toHexString(user));
        }

        // 校验用户
        if (!request.path().startsWith("/vcg/index") && !request.path().startsWith("/vcg/query_game")) {
            if (-1 == user || null == CommonService.getChannelAccountByCaid(user)) {
                logger.info("anonymous access deny: " + request.url());

                JSONObject args_rsp = new JSONObject();
                args_rsp.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                args_rsp.put("desc", "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
                response.attr().put("Content-Type", "application/json");
                response.content(args_rsp);
                return false;
            }
        }

        // 校验是否需要补充信息
        if (!request.path().startsWith("/vcg/index") && !request.path().startsWith("/vcg/query_game")) {
            if (-1 != user && null != CommonService.getChannelAccountByCaid(user)) {
                if (0 == CommonService.getChannelAccountByCaid(user).c_phone.length()) {
                    if (!request.path().equals("/vcg/update_platform_account_map.html")) {
                        redirect(response, "/vcg/update_platform_account_map.html");
                        return false;
                    }
                }
            }
        }

        recordaccess(user, conn, request.url());

        return true;
    }

    private static boolean filterOmc(FjHttpResponse response, FjHttpRequest request) {
        return true;
    }

    private static boolean filterInterface(FjHttpResponse response, FjHttpRequest request) {
        JSONObject args = request.argsToJson();
        int inst = -1;
        if (args.has("inst")) {
            Object obj = args.get("inst");
            if (obj instanceof Integer) inst = (int) obj;
            else inst = Integer.parseInt(obj.toString(), 16);
        }
        if (args.has("user")) {
            Object obj = args.get("user");
            String user = null;
            if (obj instanceof Integer) user = Integer.toHexString((int) obj);
            else user = obj.toString();

            String cookie = "";
            if (request.attr().containsKey("Cookie")) {
                cookie = request.attr().get("Cookie");
                cookie += "; ";
            }
            cookie += "user=" + user;
            request.attr().put("Cookie", cookie);
        }
        // 校验用户
        if (CommonDefinition.ISIS.INST_ECOM_QUERY_GAME != inst && !request.cookie().containsKey("user")) {
            logger.error("anonymous access deny: " + args);
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
            args_rsp.put("desc", "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
            return false;
        }
        return true;
    }

    private static void recordaccess(int user, SocketChannel conn, String url) {
        try {
            String remote = String.format("%15s|%6d",
                    ((InetSocketAddress)conn.getRemoteAddress()).getHostName(),
                    ((InetSocketAddress)conn.getRemoteAddress()).getPort());
            String local = String.format("%25s|%6d|%s",
                    ((InetSocketAddress)conn.getLocalAddress()).getHostName(),
                    ((InetSocketAddress)conn.getLocalAddress()).getPort(),
                    url);
            JSONObject args = new JSONObject();
            args.put("caid",     user);
            args.put("remote",     remote);
            args.put("local",     local);
            FjDscpMessage msg = new FjDscpMessage();
            msg.json().put("fs", FjServerToolkit.getAnyServer().name());
            msg.json().put("ts", "cdb");
            msg.json().put("inst", CommonDefinition.ISIS.INST_ECOM_UPDATE_ACCESS_RECORD);
            msg.json().put("args", args);
            FjServerToolkit.getAnySender().send(msg);
        } catch (IOException e) {e.printStackTrace();}
    }
}
