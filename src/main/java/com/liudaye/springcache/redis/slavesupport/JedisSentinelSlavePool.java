package com.liudaye.springcache.redis.slavesupport;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.util.Pool;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 从库连接池
 */
public class JedisSentinelSlavePool extends Pool<Jedis> {

    private final String masterName;

    protected GenericObjectPoolConfig poolConfig;

    protected int connectionTimeout = Protocol.DEFAULT_TIMEOUT;

    protected int soTimeout = Protocol.DEFAULT_TIMEOUT;

    protected String password;

    protected int database = Protocol.DEFAULT_DATABASE;

    protected String clientName;

    protected final Set<MasterListener> masterListeners = new HashSet<MasterListener>();

    protected Logger log = LoggerFactory.getLogger(getClass().getName());

    private volatile JedisSentinelSlaveFactory factory;
    private volatile HostAndPort currentSentinel;

    private Set<String> sentinels;

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig) {
        this(masterName, sentinels, poolConfig, Protocol.DEFAULT_TIMEOUT, null,
                Protocol.DEFAULT_DATABASE);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels) {
        this(masterName, sentinels, new GenericObjectPoolConfig(), Protocol.DEFAULT_TIMEOUT, null,
                Protocol.DEFAULT_DATABASE);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels, String password) {
        this(masterName, sentinels, new GenericObjectPoolConfig(), Protocol.DEFAULT_TIMEOUT, password);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig, int timeout, final String password) {
        this(masterName, sentinels, poolConfig, timeout, password, Protocol.DEFAULT_DATABASE);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig, final int timeout) {
        this(masterName, sentinels, poolConfig, timeout, null, Protocol.DEFAULT_DATABASE);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig, final String password) {
        this(masterName, sentinels, poolConfig, Protocol.DEFAULT_TIMEOUT, password);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig, int timeout, final String password,
                                  final int database) {
        this(masterName, sentinels, poolConfig, timeout, timeout, password, database);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig, int timeout, final String password,
                                  final int database, final String clientName) {
        this(masterName, sentinels, poolConfig, timeout, timeout, password, database, clientName);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig, final int timeout, final int soTimeout,
                                  final String password, final int database) {
        this(masterName, sentinels, poolConfig, timeout, soTimeout, password, database, null);
    }

    public JedisSentinelSlavePool(String masterName, Set<String> sentinels,
                                  final GenericObjectPoolConfig poolConfig, final int connectionTimeout, final int soTimeout,
                                  final String password, final int database, final String clientName) {
        this.poolConfig = poolConfig;
        this.connectionTimeout = connectionTimeout;
        this.soTimeout = soTimeout;
        this.password = password;
        this.database = database;
        this.clientName = clientName;
        this.masterName = masterName;
        this.sentinels = sentinels;

        HostAndPort aSentinel = initsentinels(this.sentinels, masterName);
        initPool(aSentinel);
    }

    public void destroy() {
        for (JedisSentinelSlavePool.MasterListener m : masterListeners) {
            m.shutdown();
        }

        super.destroy();
    }

    public HostAndPort getCurrentSentinel() {
        return currentSentinel;
    }

    private void initPool(HostAndPort sentinel) {
        if (!sentinel.equals(currentSentinel)) {
            currentSentinel = sentinel;
            if (factory == null) {
                factory = new JedisSentinelSlaveFactory(sentinel.getHost(), sentinel.getPort(), connectionTimeout,
                        soTimeout, password, database, clientName, false, null, null, null, masterName);
                initPool(poolConfig, factory);
            } else {
                factory.setHostAndPortOfASentinel(currentSentinel);
                // although we clear the pool, we still have to check the
                // returned object
                // in getResource, this call only clears idle instances, not
                // borrowed instances
                internalPool.clear();
            }

            log.info("Created JedisPool to sentinel at " + sentinel);
        }
    }

    private HostAndPort initsentinels(Set<String> sentinels, final String masterName) {

        HostAndPort aSentinel = null;
        boolean sentinelAvailable = false;

        log.info("Trying to find a valid sentinel from available Sentinels...");

        for (String sentinelStr : sentinels) {
            final HostAndPort hap = HostAndPort.parseString(sentinelStr);

            log.info("Connecting to Sentinel " + hap);

            Jedis jedis = null;
            try {
                jedis = new Jedis(hap.getHost(), hap.getPort());
                sentinelAvailable = true;

                List<String> masterAddr = jedis.sentinelGetMasterAddrByName(masterName);
                if (masterAddr == null || masterAddr.size() != 2) {
                    log.warn("Can not get master addr from sentinel, master name: " + masterName
                            + ". Sentinel: " + hap + ".");
                    continue;
                }

                aSentinel = hap;
                log.info("Found a Redis Sentinel at " + aSentinel);
                break;
            } catch (JedisException e) {
                log.warn("Cannot get master address from sentinel running @ " + hap + ". Reason: " + e
                        + ". Trying next one.");
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }

        if (aSentinel == null) {
            if (sentinelAvailable) {
                // can connect to sentinel, but master name seems to not monitored
                throw new JedisException("Can connect to sentinel, but " + masterName
                        + " seems to be not monitored...");
            } else {
                throw new JedisConnectionException("All sentinels down, cannot determine where is "
                        + masterName + " master is running...");
            }
        }

        log.info("Found Redis sentinel running at " + aSentinel + ", starting Sentinel listeners...");

        for (String sentinel : sentinels) {
            final HostAndPort hap = HostAndPort.parseString(sentinel);
            JedisSentinelSlavePool.MasterListener masterListener = new JedisSentinelSlavePool.MasterListener(masterName, hap.getHost(), hap.getPort());
            // whether MasterListener threads are alive or not, process can be stopped
            masterListener.setDaemon(true);
            masterListeners.add(masterListener);
            masterListener.start();
        }

        return aSentinel;
    }

    private HostAndPort toHostAndPort(List<String> getMasterAddrByNameResult) {
        String host = getMasterAddrByNameResult.get(0);
        int port = Integer.parseInt(getMasterAddrByNameResult.get(1));

        return new HostAndPort(host, port);
    }

    @Override
    public Jedis getResource() {
//        while (true) {
            Jedis jedis = super.getResource();
            jedis.setDataSource(this);
            return jedis;
            // get a reference because it can change concurrently
//            final HostAndPort master = currentSentinel;
//            final HostAndPort connection = new HostAndPort(jedis.getClient().getHost(), jedis.getClient()
//                    .getPort());
//
//            if (master.equals(connection)) {
//                // connected to the correct master
//                return jedis;
//            } else {
//                returnBrokenResource(jedis);
//            }
//        }
    }

    /**
     * @deprecated starting from Jedis 3.0 this method will not be exposed. Resource cleanup should be
     * done using @see {@link Jedis#close()}
     */
    @Override
    @Deprecated
    public void returnBrokenResource(final Jedis resource) {
        if (resource != null) {
            returnBrokenResourceObject(resource);
        }
    }

    /**
     * @deprecated starting from Jedis 3.0 this method will not be exposed. Resource cleanup should be
     * done using @see {@link Jedis#close()}
     */
    @Override
    @Deprecated
    public void returnResource(final Jedis resource) {
        if (resource != null) {
            resource.resetState();
            returnResourceObject(resource);
        }
    }

    protected class MasterListener extends Thread {

        protected String masterName;
        protected String host;
        protected int port;
        protected long subscribeRetryWaitTimeMillis = 5000;
        protected volatile Jedis j;
        protected AtomicBoolean running = new AtomicBoolean(false);

        protected MasterListener() {
        }

        public MasterListener(String masterName, String host, int port) {
            super(String.format("MasterListener-%s-[%s:%d]", masterName, host, port));
            this.masterName = masterName;
            this.host = host;
            this.port = port;
        }

        public MasterListener(String masterName, String host, int port,
                              long subscribeRetryWaitTimeMillis) {
            this(masterName, host, port);
            this.subscribeRetryWaitTimeMillis = subscribeRetryWaitTimeMillis;
        }

        @Override
        public void run() {

            running.set(true);

            while (running.get()) {

                j = new Jedis(host, port);

                try {
                    // double check that it is not being shutdown
                    if (!running.get()) {
                        break;
                    }

                    j.subscribe(new SentinelSlaveChangePubSub(), "+switch-master", "+slave", "+sdown", "+odown", "+reboot");

                } catch (JedisConnectionException e) {

                    if (running.get()) {
                        log.error("Lost connection to Sentinel at " + host + ":" + port
                                + ". Sleeping 5000ms and retrying.", e);
                        try {
                            Thread.sleep(subscribeRetryWaitTimeMillis);
                        } catch (InterruptedException e1) {
                            log.info("Sleep interrupted: ", e1);
                        }
                    } else {
                        log.info("Unsubscribing from Sentinel at " + host + ":" + port);
                    }
                } finally {
                    j.close();
                }
            }
        }

        public void shutdown() {
            try {
                log.info("Shutting down listener on " + host + ":" + port);
                running.set(false);
                // This isn't good, the Jedis object is not thread safe
                if (j != null) {
                    j.disconnect();
                }
            } catch (Exception e) {
                log.error("Caught exception while shutting down: ", e);
            }
        }

        private class SentinelSlaveChangePubSub extends JedisPubSub {
            @Override
            public void onMessage(String channel, String message) {
                if (masterName == null) {
                    log.error("Master Name is null!");
                    throw new InvalidParameterException("Master Name is null!");
                }
                log.info("Get message on chanel: " + channel + " published: " + message + "." + " current sentinel " + host + ":" + port);

                String[] msg = message.split(" ");
                List<String> msgList = Arrays.asList(msg);
                if (msgList.isEmpty()) {
                    return;
                }
                boolean needResetPool = false;
                if (masterName.equalsIgnoreCase(msgList.get(0))) { //message from channel +switch-master
                    //message looks like [+switch-master mymaster 192.168.0.2 6479 192.168.0.1 6479]
                    needResetPool = true;
                }
                int tmpIndex = msgList.indexOf("@") + 1;
                //message looks like  [+reboot slave 192.168.0.3:6479 192.168.0.3 6479 @ mymaster 192.168.0.1 6479]
                if (tmpIndex > 0 && masterName.equalsIgnoreCase(msgList.get(tmpIndex))) { //message from other channels
                    needResetPool = true;
                }
                if (needResetPool) {
                    HostAndPort aSentinel = initsentinels(sentinels, masterName);
                    initPool(aSentinel);
                } else {
                    log.info("message is not for master " + masterName);
                }

            }
        }
    }
}