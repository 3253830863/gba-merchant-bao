package com.gba.merchantbao.auth.mapper;

import com.gba.merchantbao.auth.entity.StaffAccountRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StaffAccountMapper {
    StaffAccountRow findByUsername(@Param("merchantId") long merchantId, @Param("username") String username);
    StaffAccountRow findByPhone(@Param("merchantId") long merchantId, @Param("phone") String phone);
    StaffAccountRow findById(@Param("merchantId") long merchantId, @Param("accountId") long accountId);
    int insert(StaffAccountRow account);
    int updateDisplayName(@Param("merchantId") long merchantId, @Param("accountId") long accountId,
                          @Param("displayName") String displayName, @Param("expectedVersion") int expectedVersion);
    int delete(@Param("merchantId") long merchantId, @Param("accountId") long accountId,
               @Param("expectedVersion") int expectedVersion);
}
