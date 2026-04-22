package com.example.clouddisk.mapper;

import com.example.clouddisk.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {
    @Insert("INSERT INTO user(username, email, password, salt, recycle_retention_days) VALUES(#{username}, #{email}, #{password}, #{salt}, #{recycleRetentionDays})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM user WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT * FROM user WHERE id = #{id}")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    User findById(@Param("id") Long id);

    @Update("UPDATE user SET used_space = used_space + #{size} WHERE id = #{userId}")
    int addUsedSpace(@Param("userId") Long userId, @Param("size") Long size);

    @Update("UPDATE user SET used_space = used_space - #{size} WHERE id = #{userId}")
    int subUsedSpace(@Param("userId") Long userId, @Param("size") Long size);

    @Update("UPDATE user SET used_space = #{usedSpace} WHERE id = #{userId}")
    int updateUsedSpace(@Param("userId") Long userId, @Param("usedSpace") Long usedSpace);

    @Update("UPDATE user SET username = #{username} WHERE id = #{userId}")
    int updateUsername(@Param("userId") Long userId, @Param("username") String username);

    @Update("UPDATE user SET email = #{email} WHERE id = #{userId}")
    int updateEmail(@Param("userId") Long userId, @Param("email") String email);

    @Update("UPDATE user SET password = #{password}, salt = #{salt} WHERE id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password, @Param("salt") String salt);

    @Update("UPDATE user SET avatar = #{avatar} WHERE id = #{userId}")
    int updateAvatar(@Param("userId") Long userId, @Param("avatar") String avatar);

    @Update("UPDATE user SET recycle_retention_days = #{days} WHERE id = #{userId}")
    int updateRecycleRetentionDays(@Param("userId") Long userId, @Param("days") Integer days);

    @Select("SELECT * FROM user")
    List<User> findAll();
}