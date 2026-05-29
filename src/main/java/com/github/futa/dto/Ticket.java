package com.github.futa.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Ticket {
    private String ticketId;
    private int number;
    private String nodeId;
    private TicketStatus status;
    private boolean justLogin;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime issuedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiryTime;

    public enum TicketStatus {
        PENDING,    // 待确认
        CONFIRMED,  // 已确认
        EXPIRED     // 已过期
    }
}
