package com.satyajit.upioffline.service;

/*
 * Contract for the in-memory Bluetooth mesh simulator.
 */

import com.satyajit.upioffline.model.MeshPacket;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MeshSimulatorService {
    void inject(String senderDeviceId, MeshPacket packet);
    GossipResult gossipOnce();
    List<BridgeUpload> collectBridgeUploads();
    Collection<VirtualDevice> getDevices();
    VirtualDevice getDevice(String id);
    Map<String, Integer> snapshotMap();
    void resetMesh();

    record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
}