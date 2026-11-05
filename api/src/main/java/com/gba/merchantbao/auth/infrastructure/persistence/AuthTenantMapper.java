package com.gba.merchantbao.auth.infrastructure.persistence;

import com.gba.merchantbao.auth.domain.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthTenantMapper {
    Tenant findActiveByCode(@Param("merchantCode") String merchantCode);
    Tenant findActiveById(@Param("merchantId") long merchantId);
}
