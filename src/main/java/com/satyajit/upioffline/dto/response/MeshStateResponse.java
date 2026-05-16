package com.satyajit.upioffline.dto.response;

/*
 * API response shape for current mesh device state.
 * Used by the dashboard to show packet counts per device.
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class MeshStateResponse {
    private String deviceId;
    private boolean hasInternet;
    private int packetCount;
    private List<String> packetIds;
}