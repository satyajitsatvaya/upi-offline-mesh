package com.satyajit.upioffline.service;

/*
 * Represents a single phone in the mesh network.
 * In production this state lives on a physical Android device,
 * with packets exchanged over BLE GATT.
 * Here it's an in-memory object managed by MeshSimulatorService.
 */

import com.satyajit.upioffline.model.MeshPacket;
import lombok.Getter;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class VirtualDevice {

    private final String deviceId;
    private final boolean hasInternet;

    // packetId -> packet, ConcurrentHashMap prevents duplicate packets on same device
    private final Map<String, MeshPacket> heldPackets = new ConcurrentHashMap<>();

    public VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    // putIfAbsent ensures same packet never stored twice on this device
    public void hold(MeshPacket packet) {
        heldPackets.putIfAbsent(packet.getPacketId(), packet);
    }

    public Collection<MeshPacket> getHeldPackets() {
        return heldPackets.values();
    }

    public boolean holds(String packetId) {
        return heldPackets.containsKey(packetId);
    }

    public int packetCount() {
        return heldPackets.size();
    }

    public void clear() {
        heldPackets.clear();
    }
}