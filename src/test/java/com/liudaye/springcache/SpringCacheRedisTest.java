package com.liudaye.springcache;

import com.liudaye.springcache.service.UserService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(value = SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:springbeans-cache-redis.xml")
public class SpringCacheRedisTest {
    @Autowired
    private UserService userService;


    @Test
    public void testGetCache() {
        userService.getCache("a");
        userService.getCache("a");

    }
}
