package com.nitditto.payload.response;

import com.nitditto.enums.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayResponse {

    private PaymentGateway code;

    private String name;

    // false khi cổng chưa được cấu hình trên môi trường này; FE nên ẩn hoặc làm mờ lựa chọn tương ứng.
    private boolean enabled;
}
