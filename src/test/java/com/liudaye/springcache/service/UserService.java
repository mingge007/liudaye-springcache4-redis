package com.liudaye.springcache.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Cacheable(value = "test#600", key = "'getCache.a' + #a+'#100'")
    public Integer getCache(String a) {
        System.out.println("cache miss");
        return 1;
    }
}
