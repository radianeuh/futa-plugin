package com.github.futa.dto;


import lombok.Data;

@Data
public class ConfirmRequest {
    private String ticketId;
    private boolean justLogin;

    public ConfirmRequest(String ticketId) {
        this.ticketId = ticketId;
    }
}
