package com.satyajit.upioffline.service.impl;

/*
 * Simulates the Bluetooth mesh network entirely in memory.
 * Each VirtualDevice is a phone. Gossip spreads packets between devices,
 * decrementing TTL each hop. Bridge nodes upload to the backend when they
 * get internet connectivity.
 */

import com.satyajit.upioffline.model.MeshPacket;
import com.satyajit.upioffline.service.MeshSimulatorService;
import com.satyajit.upioffline.service.VirtualDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MeshSimulatorServiceImpl implements MeshSimulatorService {

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    public MeshSimulatorServiceImpl() {
        seedDefaultDevices();
    }

    // 4 offline phones in a basement, 1 bridge phone with 4G outside
    private void seedDefaultDevices() {
        devices.put("phone-alice",     new VirtualDevice("phone-alice",     false));
        devices.put("phone-stranger1", new VirtualDevice("phone-stranger1", false));
        devices.put("phone-stranger2", new VirtualDevice("phone-stranger2", false));
        devices.put("phone-stranger3", new VirtualDevice("phone-stranger3", false));
        devices.put("phone-bridge",    new VirtualDevice("phone-bridge",    true));
    }

    @Override
    public Collection<VirtualDevice> getDevices() {
        return devices.values();
    }

    @Override
    public VirtualDevice getDevice(String id) {
        return devices.get(id);
    }

    @Override
    public void inject(String senderDeviceId, MeshPacket packet) {
        VirtualDevice sender = devices.get(senderDeviceId);
        if (sender == null)
            throw new IllegalArgumentException("Unknown device: " + senderDeviceId);
        sender.hold(packet);
        log.info("Packet {} injected at {} (TTL={})",
                packet.getPacketId().substring(0, 8), senderDeviceId, packet.getTtl());
    }

    // one round of gossip — snapshot taken at round start so packets
    // don't travel through all devices in one step
    @Override
    public GossipResult gossipOnce() {
        int transfers = 0;
        List<VirtualDevice> deviceList = new ArrayList<>(devices.values());

        // freeze state at round start
        Map<String, List<MeshPacket>> snapshot = new HashMap<>();
        for (VirtualDevice d : deviceList) {
            snapshot.put(d.getDeviceId(), new ArrayList<>(d.getHeldPackets()));
        }

        for (VirtualDevice src : deviceList) {
            for (MeshPacket pkt : snapshot.get(src.getDeviceId())) {
                if (pkt.getTtl() <= 0) continue;
                for (VirtualDevice dst : deviceList) {
                    if (dst == src) continue;
                    if (dst.holds(pkt.getPacketId())) continue;

                    MeshPacket copy = new MeshPacket();
                    copy.setPacketId(pkt.getPacketId());
                    copy.setTtl(pkt.getTtl() - 1);
                    copy.setCreatedAt(pkt.getCreatedAt());
                    copy.setCiphertext(pkt.getCiphertext());
                    dst.hold(copy);
                    transfers++;
                }
            }
        }

        log.info("Gossip round: {} transfers", transfers);
        return new GossipResult(transfers, snapshotMap());
    }

    @Override
    public Map<String, Integer> snapshotMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (VirtualDevice d : devices.values()) {
            m.put(d.getDeviceId(), d.packetCount());
        }
        return m;
    }

    @Override
    public List<BridgeUpload> collectBridgeUploads() {
        List<BridgeUpload> out = new ArrayList<>();
        for (VirtualDevice d : devices.values()) {
            if (!d.isHasInternet()) continue;
            for (MeshPacket pkt : d.getHeldPackets()) {
                out.add(new BridgeUpload(d.getDeviceId(), pkt));
            }
        }
        return out;
    }

    @Override
    public void resetMesh() {
        devices.values().forEach(VirtualDevice::clear);
    }
}