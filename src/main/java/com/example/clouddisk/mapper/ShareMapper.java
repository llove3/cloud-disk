package com.example.clouddisk.mapper;

import com.example.clouddisk.entity.Share;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ShareMapper {

    @Insert("INSERT INTO share(file_id, user_id, share_code, password, expire_time) VALUES(#{fileId}, #{userId}, #{shareCode}, #{password}, #{expireTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Share share);

    @Select("SELECT * FROM share WHERE share_code = #{code}")
    Share findByCode(@Param("code") String code);

    @Delete("DELETE FROM share WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}