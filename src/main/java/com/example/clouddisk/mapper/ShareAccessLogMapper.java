package com.example.clouddisk.mapper;

import com.example.clouddisk.entity.ShareAccessLog;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ShareAccessLogMapper {

    @Insert("INSERT INTO share_access_log(share_id, ip_address, user_agent, success, error_reason) " +
            "VALUES(#{shareId}, #{ipAddress}, #{userAgent}, #{success}, #{errorReason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShareAccessLog log);

    @Select("SELECT * FROM share_access_log WHERE share_id = #{shareId} ORDER BY access_time DESC")
    List<ShareAccessLog> findByShareId(@Param("shareId") Long shareId);
}