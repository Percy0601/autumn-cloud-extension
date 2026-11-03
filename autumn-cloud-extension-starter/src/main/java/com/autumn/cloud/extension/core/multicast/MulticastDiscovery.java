package com.autumn.cloud.extension.core.multicast;

import com.autumn.cloud.extension.core.model.InstanceRegistration;
import com.autumn.cloud.extension.core.model.InstanceSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * UDP 组播服务发现核心：零依赖注册中心。
 *
 * <p>每个实例加入同一个组播组，按心跳周期广播自身注册信息（JSON 帧），
 * 接收方维护本地注册表；超时未收到心跳的实例被清扫下线，正常停机广播 bye 帧即时摘除。</p>
 *
 * <p>纯 JDK 实现（java.net.MulticastSocket），无反射，适配 GraalVM Native Image。</p>
 */
public final class MulticastDiscovery implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(MulticastDiscovery.class.getName());
    private static final int MAX_PACKET_BYTES = 8192;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 组播帧：hb = 心跳/注册，bye = 主动下线。 */
    record Frame(String type, InstanceRegistration registration) {
    }

    private record Entry(InstanceSnapshot snapshot, long lastSeenMs) {
    }

    private final MulticastConfig config;
    private final Map<String, Map<String, Entry>> registry = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean published = new AtomicBoolean(false);

    private volatile MulticastSocket socket;
    private volatile Thread receiverThread;
    private volatile ScheduledExecutorService senderScheduler;
    private volatile ScheduledExecutorService sweeper;
    private volatile InstanceRegistration self;

    public MulticastDiscovery(MulticastConfig config) {
        this.config = config;
    }

    /** 加入组播组，启动接收线程与过期清扫。 */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }
        MulticastSocket socket = new MulticastSocket(config.port());
        socket.setTimeToLive(config.ttl());
        NetworkInterface nif = resolveNetworkInterface(config.networkInterface());
        socket.joinGroup(new InetSocketAddress(InetAddress.getByName(config.group()), config.port()), nif);
        this.socket = socket;
        running.set(true);

        Thread thread = new Thread(this::receiveLoop, "autumn-multicast-receiver");
        thread.setDaemon(true);
        thread.start();
        this.receiverThread = thread;

        ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(namedDaemon("autumn-multicast-sweeper"));
        sweeper.scheduleWithFixedDelay(this::sweep, config.evictAfterMs(), config.evictAfterMs(), TimeUnit.MILLISECONDS);
        this.sweeper = sweeper;
    }

    /** 发布自身注册信息并按心跳周期持续广播；本进程立即可见自身实例。 */
    public void publish(InstanceRegistration registration) {
        if (!running.get()) {
            throw new IllegalStateException("MulticastDiscovery is not started");
        }
        if (!published.compareAndSet(false, true)) {
            throw new IllegalStateException("Registration already published");
        }
        this.self = registration;
        upsert(registration.snapshot(), System.currentTimeMillis());
        ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(namedDaemon("autumn-multicast-sender"));
        sender.scheduleAtFixedRate(this::sendHeartbeat, 0, config.heartbeatIntervalMs(), TimeUnit.MILLISECONDS);
        this.senderScheduler = sender;
    }

    /** 查询服务实例（惰性剔除过期实例）。 */
    public List<InstanceSnapshot> getInstances(String service) {
        Map<String, Entry> instances = registry.get(service);
        if (instances == null) {
            return List.of();
        }
        long deadline = System.currentTimeMillis() - config.evictAfterMs();
        return instances.values().stream()
                .filter(entry -> entry.lastSeenMs() >= deadline)
                .map(Entry::snapshot)
                .toList();
    }

    /** 已知的全部服务名。 */
    public List<String> listServices() {
        registry.values().removeIf(Map::isEmpty);
        return List.copyOf(registry.keySet());
    }

    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            send("bye", self);
        } catch (Exception ignored) {
            // 尽力而为的优雅下线
        }
        if (senderScheduler != null) {
            senderScheduler.shutdownNow();
        }
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
        if (socket != null) {
            try {
                socket.close(); // 同时解除 receiveLoop 阻塞
            } catch (Exception ignored) {
            }
        }
        Thread thread = receiverThread;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---------------- internal ----------------

    private void sendHeartbeat() {
        try {
            send("hb", self);
        } catch (Exception e) {
            LOGGER.fine("multicast heartbeat failed: " + e.getMessage());
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_BYTES];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                onFrame(json, System.currentTimeMillis());
            } catch (Exception e) {
                if (running.get()) {
                    LOGGER.fine("multicast receive failed: " + e.getMessage());
                }
            }
        }
    }

    /** 解析一帧并更新本地注册表（包级可见，便于测试）。 */
    void onFrame(String json, long nowMs) {
        try {
            Frame frame = MAPPER.readValue(json, Frame.class);
            if (frame.registration() == null) {
                return;
            }
            if ("bye".equals(frame.type())) {
                remove(frame.registration().service(), frame.registration().instanceId());
            } else {
                upsert(frame.registration().snapshot(), nowMs);
            }
        } catch (Exception e) {
            LOGGER.warning("invalid multicast frame ignored: " + e.getMessage());
        }
    }

    void sweep(long nowMs) {
        long deadline = nowMs - config.evictAfterMs();
        registry.forEach((service, instances) -> instances.values().removeIf(e -> e.lastSeenMs() < deadline));
    }

    private void sweep() {
        sweep(System.currentTimeMillis());
    }

    private void upsert(InstanceSnapshot snapshot, long nowMs) {
        registry.computeIfAbsent(snapshot.service(), key -> new ConcurrentHashMap<>())
                .put(snapshot.instanceId(), new Entry(snapshot, nowMs));
    }

    private void remove(String service, String instanceId) {
        Map<String, Entry> instances = registry.get(service);
        if (instances != null) {
            instances.remove(instanceId);
        }
    }

    private void send(String type, InstanceRegistration registration) throws IOException {
        MulticastSocket socket = this.socket;
        if (registration == null || socket == null) {
            return;
        }
        byte[] data = MAPPER.writeValueAsBytes(new Frame(type, registration));
        socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(config.group()), config.port()));
    }

    private static NetworkInterface resolveNetworkInterface(String preferred) throws IOException {
        if (preferred != null && !preferred.isBlank()) {
            NetworkInterface nif = NetworkInterface.getByName(preferred);
            if (nif == null) {
                throw new IOException("network interface not found: " + preferred);
            }
            return nif;
        }
        for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
            NetworkInterface nif = e.nextElement();
            if (!nif.isUp() || nif.isLoopback() || !nif.supportsMulticast()) {
                continue;
            }
            if (nif.getInetAddresses().hasMoreElements()) {
                return nif;
            }
        }
        NetworkInterface loopback = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
        if (loopback == null) {
            throw new IOException("no multicast-capable network interface available");
        }
        return loopback;
    }

    private static ThreadFactory namedDaemon(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix);
            thread.setDaemon(true);
            return thread;
        };
    }
}
