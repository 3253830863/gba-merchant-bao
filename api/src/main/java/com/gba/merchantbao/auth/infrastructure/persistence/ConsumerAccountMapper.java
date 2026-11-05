package com.gba.merchantbao.auth.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Every operation requires a server-resolved tenant; no global account lookup. */
@Mapper
public interface ConsumerAccountMapper {
    ConsumerAccountRow findByPhone(@Param("merchantId") long merchantId, @Param("phone") String phone);
    ConsumerAccountRow findById(@Param("merchantId") long merchantId, @Param("accountId") long accountId);
    ConsumerAccountRow findOwnedById(@Param("merchantId") long merchantId, @Param("accountId") long accountId,
                                      @Param("ownerAccountId") long ownerAccountId);
    int insert(ConsumerAccountRow account);
    int updateDisplayName(@Param("merchantId") long merchantId, @Param("accountId") long accountId,
                          @Param("displayName") String displayName, @Param("expectedVersion") int expectedVersion);
    int delete(@Param("merchantId") long merchantId, @Param("accountId") long accountId,
               @Param("expectedVersion") int expectedVersion);
}
