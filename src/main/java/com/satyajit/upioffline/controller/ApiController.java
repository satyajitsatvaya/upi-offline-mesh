package com.satyajit.upioffline.controller;

/*
 * Endpoints split into three groups:
 *   /api/server-key        — RSA public key for sender devices
 *   /api/demo/*, /api/mesh/* — simulation controls for the dashboard
 *   /api/bridge/ingest     — the real production endpoint bridge nodes call
 *   /api/accounts, /api/transactions — dashboard data
 */

import com.satyajit.upioffline.crypto.ServerKeyHolder;
import com.satyajit.upioffline.dto.ApiResponse;
import com.satyajit.upioffline.dto.request.SendPaymentRequest;
import com.satyajit.upioffline.dto.response.AccountResponse;
import com.satyajit.upioffline.dto.response.MeshStateResponse;
import com.satyajit.upioffline.dto.response.TransactionResponse;
import com.satyajit.upioffline.model.MeshPacket;
import com.satyajit.upioffline.repository.AccountRepository;
import com.satyajit.upioffline.repository.TransactionRepository;
import com.satyajit.upioffline.service.BridgeIngestionService;
import com.satyajit.upioffline.service.DemoService;
import com.satyajit.upioffline.service.IdempotencyService;
import com.satyajit.upioffline.service.MeshSimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ServerKeyHolder serverKey;
    private final DemoService demo;
    private final MeshSimulatorService mesh;
    private final BridgeIngestionService bridge;
    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final IdempotencyService idempotency;


    // server RSA public key — sender devices fetch this once to encrypt payloads
    @GetMapping("/server-key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getServerPublicKey() {
        return ResponseEntity.ok(ApiResponse.ok("Server public key",
                Map.of(
                        "publicKey", serverKey.getPublicKeyBase64(),
                        "algorithm", "RSA-2048 / OAEP-SHA256",
                        "hybridScheme", "RSA-OAEP encrypts an AES-256-GCM session key"
                )));
    }

    // simulates sender phone — builds encrypted packet and injects into mesh
    @PostMapping("/demo/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> demoSend(
            @Valid @RequestBody SendPaymentRequest req) throws Exception {

        MeshPacket packet = demo.createPacket(
                req.getSenderVpa(),
                req.getReceiverVpa(),
                req.getAmount(),
                req.getPin(),
                req.getTtl() == null ? 5 : req.getTtl());

        String startDevice = req.getStartDevice() == null ? "phone-alice" : req.getStartDevice();
        mesh.inject(startDevice, packet);

        return ResponseEntity.ok(ApiResponse.ok("Packet injected into mesh",
                Map.of(
                        "packetId", packet.getPacketId(),
                        "ciphertextPreview", packet.getCiphertext().substring(0, 64) + "...",
                        "ttl", packet.getTtl(),
                        "injectedAt", startDevice
                )));
    }

    // current state of all virtual devices in the mesh
    @GetMapping("/mesh/state")
    public ResponseEntity<ApiResponse<List<MeshStateResponse>>> meshState() {
        List<MeshStateResponse> deviceData = mesh.getDevices().stream()
                .map(d -> new MeshStateResponse(
                        d.getDeviceId(),
                        d.isHasInternet(),
                        d.packetCount(),
                        d.getHeldPackets().stream()
                                .map(p -> p.getPacketId().substring(0, 8))
                                .toList()
                )).toList();

        return ResponseEntity.ok(ApiResponse.ok("Mesh state", deviceData));
    }

    // runs one gossip round — every device shares packets with every other device
    @PostMapping("/mesh/gossip")
    public ResponseEntity<ApiResponse<Map<String, Object>>> meshGossip() {
        MeshSimulatorService.GossipResult r = mesh.gossipOnce();
        return ResponseEntity.ok(ApiResponse.ok("Gossip round complete",
                Map.of(
                        "transfers", r.transfers(),
                        "deviceCounts", r.deviceCounts()
                )));
    }

    // bridge nodes walk outside and upload — parallel to exercise concurrent idempotency
    @PostMapping("/mesh/flush")
    public ResponseEntity<ApiResponse<Map<String, Object>>> meshFlush() {
        List<MeshSimulatorService.BridgeUpload> uploads = mesh.collectBridgeUploads();

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        uploads.parallelStream().forEach(up -> {
            BridgeIngestionService.IngestResult r =
                    bridge.ingest(up.packet(), up.bridgeNodeId(), 5 - up.packet().getTtl());
            synchronized (results) {
                results.add(Map.of(
                        "bridgeNode", up.bridgeNodeId(),
                        "packetId", up.packet().getPacketId().substring(0, 8),
                        "outcome", r.outcome(),
                        "reason", r.reason() == null ? "" : r.reason(),
                        "transactionId", r.transactionId() == null ? -1 : r.transactionId()
                ));
            }
        });

        return ResponseEntity.ok(ApiResponse.ok("Bridge flush complete",
                Map.of(
                        "uploadsAttempted", uploads.size(),
                        "results", results
                )));
    }

    // resets mesh and idempotency cache
    @PostMapping("/mesh/reset")
    public ResponseEntity<ApiResponse<String>> meshReset() {
        mesh.resetMesh();
        idempotency.clear();
        return ResponseEntity.ok(ApiResponse.ok("Mesh and idempotency cache cleared", null));
    }

    // THE production endpoint — real bridge nodes POST here
    @PostMapping("/bridge/ingest")
    public ResponseEntity<ApiResponse<BridgeIngestionService.IngestResult>> ingest(
            @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count", defaultValue = "0") int hopCount) {

        BridgeIngestionService.IngestResult r = bridge.ingest(packet, bridgeNodeId, hopCount);
        return ResponseEntity.ok(ApiResponse.ok(r.outcome(), r));
    }

    // dashboard — account balances
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> listAccounts() {
        List<AccountResponse> response = accountRepo.findAll().stream()
                .map(a -> new AccountResponse(a.getVpa(), a.getHolderName(), a.getBalance()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Accounts", response));
    }

    // dashboard — last 20 transactions
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> listTransactions() {
        List<TransactionResponse> response = txRepo.findTop20ByOrderByIdDesc().stream()
                .map(t -> new TransactionResponse(
                        t.getId(),
                        t.getSenderVpa(),
                        t.getReceiverVpa(),
                        t.getAmount(),
                        t.getStatus().name(),
                        t.getSignedAt(),
                        t.getSettledAt(),
                        t.getBridgeNodeId(),
                        t.getHopCount()
                )).toList();
        return ResponseEntity.ok(ApiResponse.ok("Transactions", response));
    }
}