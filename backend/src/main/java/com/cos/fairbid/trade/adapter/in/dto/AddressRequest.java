package com.cos.fairbid.trade.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 배송지 입력 요청 DTO
 * Jackson 역직렬화를 위해 기본 생성자 + Setter 필요
 */
@Getter
@Setter
@NoArgsConstructor
public class AddressRequest {

    @NotBlank(message = "수령인 이름은 필수입니다.")
    private String recipientName;

    @NotBlank(message = "수령인 연락처는 필수입니다.")
    private String recipientPhone;

    @NotBlank(message = "우편번호는 필수입니다.")
    private String postalCode;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    private String addressDetail;
}
