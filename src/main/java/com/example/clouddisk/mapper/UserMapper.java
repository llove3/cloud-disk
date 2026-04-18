package com.example.clouddisk.mapper;

import com.example.clouddisk.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Insert("INSERT INTO user(username, password, salt) VALUES(#{username}, #{password}, #{salt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(@Param("id") Long id);
    @Update("UPDATE user SET used_space = used_space + #{size} WHERE id = #{userId}")
    int addUsedSpace(@Param("userId") Long userId, @Param("size") Long size);

    @Update("UPDATE user SET used_space = used_space - #{size} WHERE id = #{userId}")
    int subUsedSpace(@Param("userId") Long userId, @Param("size") Long size);
}