package com.ski.frs.ccu;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.ski.frs.ccu.cb.SBDevice;
import com.ski.frs.ccu.cb.SBMonitor;
import com.ski.frs.ccu.cb.SBPicture;
import com.ski.frs.ccu.cb.SBSubject;
import com.ski.frs.ccu.cb.StoreBlock;
import com.ski.frs.isis.ISIS;

import fomjar.server.FjServerToolkit;
import fomjar.server.msg.FjISIS;
import fomjar.util.FjLoopTask;
import fomjar.util.FjThreadFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@SuppressWarnings("unchecked")
public class StoreBlockService {
    
    private static final Logger logger = Logger.getLogger(StoreBlockService.class);
    
    private static StoreBlockService instance = null;
    public static synchronized StoreBlockService getInstance() {
        if (null == instance) {
            instance = new StoreBlockService();
        }
        return instance;
    }
    
    private ExecutorService pool;
    public  SBDevice    sb_dev;
    public  SBPicture   sb_pic;
    public  SBSubject   sb_sub;
    public  SBMonitor   sb_mon;
    private FjLoopTask  monitor;
    private Object      lock;
    
    private Map<String, CacheData> cache;
    
    private StoreBlockService() {
        pool = Executors.newFixedThreadPool(4, new FjThreadFactory("sbs"));
        sb_dev = new SBDevice();
        sb_pic = new SBPicture();
        sb_sub = new SBSubject();
        sb_mon = new SBMonitor();
        lock = new Object();
        
        monitor = new Monitor();
        
        cache = new HashMap<>();
    }
    
    public void open() {
        // load
        if (sb_dev.file().isFile()) pool.submit(new TaskLoad(lock, sb_dev));
        if (sb_pic.file().isFile()) pool.submit(new TaskLoad(lock, sb_pic));
        if (sb_sub.file().isFile()) pool.submit(new TaskLoad(lock, sb_sub));
        if (sb_mon.file().isFile()) pool.submit(new TaskLoad(lock, sb_mon));
        // save
        new Thread(monitor, "sbs-monitor").start();
    }
    
    public void close() {
        synchronized(lock) {
            // load
            pool.shutdownNow();
            // save
            monitor.close();
        }
    }
    
    private class Monitor extends FjLoopTask {
        
        private static final long TIMEOUT = 1000L * 60 * 2;
        
        private TaskSave t_dev;
        private TaskSave t_pic;
        private TaskSave t_sub;
        private TaskSave t_mon;
        
        public Monitor() {
            setDelay(TIMEOUT);
            setInterval(TIMEOUT);
            t_dev = new TaskSave(lock, sb_dev);
            t_pic = new TaskSave(lock, sb_pic);
            t_sub = new TaskSave(lock, sb_sub);
            t_mon = new TaskSave(lock, sb_mon);
        }
        @Override
        public void perform() {
            pool.submit(t_dev);
            pool.submit(t_pic);
            pool.submit(t_sub);
            pool.submit(t_mon);
            
            String save = FjServerToolkit.getServerConfig("ccu.save");
            save = (null == save ? "3" : save);
            int minute = Integer.parseInt(save);
            setInterval(1000L * 60 * minute);
        }
        
    }
    
    private static class TaskSave implements Runnable {
        
        private Object lock;
        private StoreBlock sb;
        
        public TaskSave(Object lock, StoreBlock sb) {
            this.lock = lock;
            this.sb = sb;
        }
        @Override
        public void run() {
            synchronized (lock) {
                try {
                    long begin = System.currentTimeMillis();
                    logger.error(String.format("save begin, file: %s", sb.file()));
                    sb.save();
                    logger.error(String.format("save success, file: %s, file size: %d, time consumed: %f s", sb.file(), sb.file().length(), (System.currentTimeMillis() - begin) / 1000.0f));
                } catch (IOException e) {logger.error(String.format("save failed, file: %s", sb.file()), e);}
            }
        }
    }
    
    private static class TaskLoad implements Runnable {
        
        private Object lock;
        private StoreBlock sb;
        
        public TaskLoad(Object lock, StoreBlock sb) {
            this.lock = lock;
            this.sb = sb;
        }
        @Override
        public void run() {
            synchronized (lock) {
                try {
                    long begin = System.currentTimeMillis();
                    logger.error(String.format("load begin, file: %s, file size: %d", sb.file(), sb.file().length()));
                    sb.load();
                    logger.error(String.format("load success, file: %s, time consumed: %f s", sb.file(), (System.currentTimeMillis() - begin) / 1000.0f));
                } catch (IOException | ClassNotFoundException e) {logger.error(String.format("load failed, file: %s", sb.file()), e);}
            }
        }
    }
    
    private static class CacheData {
        public long         time = System.currentTimeMillis();
        public JSONArray    data;
        public CacheData(JSONArray data) {this.data = data;}
    }
    
    public JSONObject dispatch(int inst, JSONObject args) {
        cache_clear();
        if (cache_ever(args)) return cache_page(args);
        
        for (Field field : ISIS.class.getFields()) {
            if (Integer.class.isAssignableFrom(field.getType())) continue;
            try {
                if (field.getInt(ISIS.class) == inst) {
                    Method method = StoreBlockService.class.getMethod(field.getName(), JSONObject.class);
                    synchronized (lock) {
                        JSONObject args_rsp = (JSONObject) method.invoke(this, args);
                        if (cache_need(inst, args)) {
                            cache(args, args_rsp);
                            args_rsp = cache_page(args);
                        }
                        return args_rsp;
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
                String desc = String.format("dispatch message failed, inst = 0x%08X(%s), args = %s", inst, field.getName(), args);
                logger.error(desc, e);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_INTERNAL_ERROR);
                json.put("desc", desc);
                return json;
            }
        }
        logger.error(String.format("illegal inst: 0x%08X, args: %s", inst, args));
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_ILLEGAL_INST);
        json.put("desc", "illegal inst: " + inst);
        return json;
    }
    
    private void cache_clear() {
        String time = FjServerToolkit.getServerConfig("ccu.cache.time");
        time = (null == time ? "2" : time);
        long timeout = 1000L * 60 * Integer.parseInt(time);
        List<String> pks = new LinkedList<>();
        cache.entrySet().forEach(e->{
            if (System.currentTimeMillis() - e.getValue().time >= timeout) {
                pks.add(e.getKey());
            }
        });
        pks.forEach(pk->cache.remove(pk));
    }
    
    private boolean cache_ever(JSONObject args) {
        if (!args.containsKey("pk"))    return false;
        String pk = args.getString("pk");
        if (!cache.containsKey(pk))     return false;
        return true;
    }
    
    private boolean cache_need(int inst, JSONObject args) {
        if (!args.containsKey("pk"))    return false;
        if (!args.containsKey("pf"))    return false;
        if (!args.containsKey("pt"))    return false;
        switch (inst) {
        case ISIS.INST_GET_PIC:
        case ISIS.INST_GET_SUB_ITEM:
            return true;
        default:
            return false;
        }
    }
    
    private JSONObject cache_page(JSONObject args) {
        String  pk = args.getString("pk");
        int     pf = args.getInt("pf");
        int     pt = args.getInt("pt");
        
        CacheData cd = cache.get(pk);
        
        if (null == cd || 0 == cd.data.size()) {
            JSONObject page = new JSONObject();
            page.put("pa",  0);
            page.put("pc",  0);
            page.put("sum", 0);
            JSONArray desc = new JSONArray();
            desc.add(page);
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", FjISIS.CODE_SUCCESS);
            args_rsp.put("desc", desc);
            return args_rsp;
        }
        
        JSONObject page = new JSONObject();
        int sum = cd.data.size();
        int pa  = (int) Math.ceil(1.0d * sum / (pt - pf + 1));
        int pc  = pf / pa + 1;
        page.put("pa",  pa);
        page.put("pc",  pc);
        page.put("sum", sum);
        
        JSONArray desc = new JSONArray();
        desc.add(page);
        for (int i = pf; i <= pt; i++) {
            if (0 <= i && i < cd.data.size()) {
                desc.add(cd.data.get(i));
            }
        }
        
        JSONObject args_rsp = new JSONObject();
        args_rsp.put("code", FjISIS.CODE_SUCCESS);
        args_rsp.put("desc", desc);
        return args_rsp;
    }
    
    private void cache(JSONObject args, JSONObject args_rsp) {
        if (FjISIS.CODE_SUCCESS != args_rsp.getInt("code")) return;
        if (!(args_rsp.get("desc") instanceof JSONArray)) return;
        
        String      pk      = args.getString("pk");
        JSONArray   desc    = args_rsp.getJSONArray("desc");
        cache.put(pk, new CacheData(desc));
    }
    
    public JSONObject INST_SET_PIC(JSONObject args) {
        if (!args.has("name") || !args.has("type") || !args.has("size") || !args.has("path")) {
            String desc = "illegal arguments, no name, type, size, path";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        
        if (!args.has("pid")) args.put("pid", "picture-" + UUID.randomUUID().toString().replace("-", ""));
        
        if (args.has("did")) {
            String did = args.getString("did"); // 设备下图片
            JSONObject dev = sb_dev.getDevice0(did);
            if (null == dev) {
                String desc = "illegal arguments, invalid did: " + did;
                logger.error(desc + ", " + args);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
                json.put("desc", desc);
                return json;
            }
            args.put("dpath", dev.getString("path"));
            dev.getJSONArray("pics").add(args);
        }
        if (args.has("sid") && args.has("siid")) {   // 主体库下图片
            String sid = args.getString("sid");
            String siid = args.getString("siid");
            JSONObject sub = sb_sub.getSubject0(sid);
            if (null == sub) {
                String desc = "illegal arguments, invalid sid: " + sid;
                logger.error(desc + ", " + args);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
                json.put("desc", desc);
                return json;
            }
            JSONObject item = sub.getJSONObject("items").getJSONObject(siid);
            if (null == item) {
                String desc = "illegal arguments, invalid siid: " + siid;
                logger.error(desc + ", " + args);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
                json.put("desc", desc);
                return json;
            }
            args.put("sname", sub.getString("name"));
            item.getJSONArray("pics").add(args);
        }
        sb_pic.setPicture(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_GET_PIC(JSONObject args) {
        List<JSONObject> pics = sb_pic.getPicture(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", pics);
        return json;
    }
    
    public JSONObject INST_SET_DEV(JSONObject args) {
        if (!args.has("path")) {
            String desc = "illegal arguments, path";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_dev.setDevice(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_DEL_DEV(JSONObject args) {
        if (!args.has("did")) {
            String desc = "illegal arguments, no did";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        List<String> did = new LinkedList<>();
        Object obj = args.get("did");
        if (obj instanceof String) did.add((String) obj);
        else if (obj instanceof JSONArray) did.addAll((JSONArray) obj);
        
        List<JSONObject> devs = sb_dev.delDevice(did.toArray(new String[did.size()]));
        
        devs.parallelStream().forEach(dev->{
            JSONArray pics = dev.getJSONArray("pics");
            List<String> pids = new LinkedList<Object>(pics).parallelStream()
                    .map(pic->(JSONObject) pic)
                    .map(pic->pic.getString("pid"))
                    .collect(Collectors.toList());
            sb_pic.delPicture(pids.toArray(new String[pids.size()]));
            dev.put("pics", pics.size());
        });
        
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", devs);
        return json;
    }
    
    public JSONObject INST_GET_DEV(JSONObject args) {
        String[] did = null;
        if (args.has("did")) {
            List<String> list = new LinkedList<>();
            Object obj = args.get("did");
            if (obj instanceof String) list.add((String) obj);
            else if (obj instanceof JSONArray) list.addAll((JSONArray) obj);
            did = list.toArray(new String[list.size()]);
        }
        List<JSONObject> devs = sb_dev.getDevice(did);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", devs);
        return json;
    }
    
    public JSONObject INST_SET_SUB(JSONObject args) {
        if (!args.has("name") || !args.has("type")) {
            String desc = "illegal arguments, no name, type";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_sub.setSubject(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_DEL_SUB(JSONObject args) {
        if (!args.has("sid")) {
            String desc = "illegal arguments, no sid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        List<String> sid = new LinkedList<>();
        Object obj = args.get("sid");
        if (obj instanceof String) sid.add((String) obj);
        else if (obj instanceof JSONArray) sid.addAll((JSONArray) obj);
        
        List<JSONObject> subs = sb_sub.delSubject(sid.toArray(new String[sid.size()]));
        
        subs.parallelStream().forEach(sub->{
            JSONObject items = sub.getJSONObject("items");
            if (items.isEmpty()) return;
            
            new LinkedList<Object>(items.values()).parallelStream()
                    .map(item->(JSONObject) item) 
                    .forEach(item->{
                        JSONArray pics = item.getJSONArray("pics");
                        List<String> pids = new LinkedList<Object>(pics)
                                .stream()
                                .map(pic->(JSONObject) pic)
                                .map(pic->pic.getString("pid"))
                                .collect(Collectors.toList());
                        sb_pic.delPicture(pids.toArray(new String[pids.size()]));
                        item.put("pics", pics.size());
                    });
        });
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", subs);
        return json;
    }
    
    public JSONObject INST_MOD_SUB(JSONObject args) {
        if (!args.has("sid")) {
            String desc = "illegal arguments, no sid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_sub.modSubject(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_GET_SUB(JSONObject args) {
        String[] sid = null;
        if (args.has("sid")) {
            List<String> list = new LinkedList<>();
            Object obj = args.get("sid");
            if (obj instanceof String) list.add((String) obj);
            else if (obj instanceof JSONArray) list.addAll((JSONArray) obj);
            sid = list.toArray(new String[list.size()]);
        }
        List<JSONObject> subs = sb_sub.getSubject(sid);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", subs);
        return json;
    }
    
    public JSONObject INST_SET_SUB_ITEM(JSONObject args) {
        if (!args.has("sid")) {
            String desc = "illegal arguments, no sid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        String sid = args.getString("sid");
        List<JSONObject> subs = sb_sub.getSubject(sid);
        if (subs.isEmpty()) {
            String desc = "illegal arguments, subject not exist: " + sid;
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        args.put("sname", subs.get(0).getString("name"));
        sb_sub.setSubjectItem(sid, args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_MOD_SUB_ITEM(JSONObject args) {
        if (!args.has("sid") || !args.has("siid")) {
            String desc = "illegal arguments, no sid, siid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_sub.modSubjectItem(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_GET_SUB_ITEM(JSONObject args) {
        List<JSONObject> items = sb_sub.getSubjectItem(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", items);
        return json;
    }
    
    public JSONObject INST_SET_MON(JSONObject args) {
        if (!args.has("name") || !args.has("dids") || !args.has("sids") || !args.has("tv")) {
            String desc = "illegal arguments, no name, dids, sids, tv";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        args.put("devs", new LinkedList<Object>(args.getJSONArray("dids")).stream()
                .map(did->(String) did)
                .map(did->sb_dev.data().get(did))
                .collect(Collectors.toList()));
        args.put("subs", new LinkedList<Object>(args.getJSONArray("sids")).stream()
                .map(sid->(String) sid)
                .map(sid->sb_sub.data().get(sid))
                .collect(Collectors.toList()));
        JSONObject desc = sb_mon.setMonitor(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", desc);
        return json;
    }
    
    public JSONObject INST_DEL_MON(JSONObject args) {
        if (!args.has("mid")) {
            String desc = "illegal arguments, no mid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        List<String> mid = new LinkedList<>();
        Object obj = args.get("mid");
        if (obj instanceof String) mid.add((String) obj);
        else if (obj instanceof JSONArray) mid.addAll((JSONArray) obj);
        
        List<JSONObject> mids = sb_mon.delMonitor(mid.toArray(new String[mid.size()]));
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", mids);
        return json;
    }
    
    public JSONObject INST_GET_MON(JSONObject args) {
        String[] mid = null;
        if (args.has("mid")) {
            List<String> list = new LinkedList<>();
            Object obj = args.get("mid");
            if (obj instanceof String) list.add((String) obj);
            else if (obj instanceof JSONArray) list.addAll((JSONArray) obj);
            mid = list.toArray(new String[list.size()]);
        }
        List<JSONObject> mids = sb_mon.getMonitor(mid);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", mids);
        return json;
    }

}


