package com.liudaye.springcache.redis;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * cachekey处理工具
 *
 * @author liuhongming
 * @date 2018-08-10
 */
public class VVCacheKeyUtil {
    private static Logger _log = LoggerFactory.getLogger(VVCacheKeyUtil.class);

    /**
     * 处理key中包含的有效期数据
     * e.g.:"user.userId_1#300"
     * key="user.userId_1" 有效为300秒
     *
     * @param key               可转换String类型的对象
     * @param defaultExpiration 默认有效时间
     * @return <dr/>
     * String  key          null
     * </dr>
     * Long    expiration  -1
     */
    public static JSONObject getKeyExpire(Object key, Long defaultExpiration) {
        String keyStr = String.valueOf(key);
        String[] keys = keyStr.split("#");
        String k = keyStr;
        long expiration = defaultExpiration;
        try {
            k = keys.length > 0 ? keys[0] : k;
            expiration = keys.length > 1 ? Long.valueOf(keys[1]) : expiration;
        } catch (NumberFormatException e) {
            k = keyStr;
            expiration = defaultExpiration;
            _log.warn("handle key's expire time error");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", k);
        jsonObject.put("expiration", expiration);
        return jsonObject;
    }

    public static JSONObject getKeyExpire(Object key) {
        return getKeyExpire(key, -1l);
    }

    public static void main(String[] args) {
        System.out.println(getKeyExpire("class com.liudaye.musiclive.user.service.UserService.user.userId_1#100"));
    }
}
