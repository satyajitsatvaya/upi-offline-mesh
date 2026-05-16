package com.satyajit.upioffline.service;

/*
 * Contract for demo account seeding and sender phone simulation.
 */

import com.satyajit.upioffline.model.MeshPacket;
import java.math.BigDecimal;

public interface DemoService {
    MeshPacket createPacket(String senderVpa, String receiverVpa,
                            BigDecimal amount, String pin, int ttl) throws Exception;
}