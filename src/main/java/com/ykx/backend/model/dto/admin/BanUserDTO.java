package com.ykx.backend.model.dto.admin;

import lombok.Data;

@Data
public class BanUserDTO {
    private String userId;
    private String reason;
}
