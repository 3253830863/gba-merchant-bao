package com.gba.merchantbao.auth.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated
@ConfigurationProperties("merchant.auth.limits")
public class AuthLimits {
    @Min(1) private int smsCooldownSeconds = 60;
    @Min(1) private int smsPhonePerHour = 10;
    @Min(1) private int smsIpPerHour = 30;
    @Min(1) private int loginIpPerWindow = 30;
    @Min(1) private int refreshIpPerWindow = 30;
    @Min(1) private int loginWindowSeconds = 900;
    @Min(1) private int passwordFailures = 5;
    @Min(1) private int passwordWindowSeconds = 900;
    @Min(1) private int refreshRetrySeconds = 10;
}
