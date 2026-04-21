package com.example.clouddisk.mapper;

import com.example.clouddisk.entity.Share;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ShareMapper {

    @Insert("INSERT INTO share(file_id, user_id, share_code, password, expire_time) VALUES(#{fileId}, #{userId}, #{shareCode}, #{password}, #{expireTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Share share);

    @Select("SELECT * FROM share WHERE share_code = #{code}")
    Share findByCode(@Param("code") String code);

    @Delete("DELETE FROM share WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT s.*, f.file_name as fileName FROM share s JOIN file f ON s.file_id = f.id WHERE s.user_id = #{userId} ORDER BY s.created_at DESC")
    List<Share> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM share WHERE id = #{id}")
    Share findById(@Param("id") Long id);

}