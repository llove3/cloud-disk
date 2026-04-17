package com.example.clouddisk.mapper;

import com.example.clouddisk.entity.FileInfo;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface FileMapper {

    @Insert("INSERT INTO file(user_id, file_name, file_size, file_path, file_md5, parent_id, version, deleted) " +
            "VALUES(#{userId}, #{fileName}, #{fileSize}, #{filePath}, #{fileMd5}, #{parentId}, #{version}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileInfo file);

    @Select("SELECT * FROM file WHERE user_id = #{userId} AND parent_id = #{parentId} AND deleted = FALSE")
    List<FileInfo> findByUserIdAndParentId(@Param("userId") Long userId, @Param("parentId") Long parentId);

    @Select("SELECT * FROM file WHERE id = #{id} AND user_id = #{userId} AND deleted = FALSE")
    FileInfo findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

//    @Update("UPDATE file SET deleted = TRUE WHERE id = #{id}")
//    int softDeleteById(@Param("id") Long id);

    @Update("UPDATE file SET deleted = TRUE, deleted_at = NOW() WHERE id = #{id}")
    int softDeleteById(@Param("id") Long id);

    @Select("SELECT * FROM file WHERE user_id = #{userId} AND deleted = TRUE ORDER BY deleted_at DESC")
    List<FileInfo> findRecycleBinByUserId(@Param("userId") Long userId);

    @Update("UPDATE file SET deleted = FALSE, deleted_at = NULL WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM file WHERE id = #{id}")
    int permanentDeleteById(@Param("id") Long id);

    @Select("SELECT * FROM file WHERE id = #{id} AND user_id = #{userId}")
    FileInfo findByIdAndUserIdIncludeDeleted(@Param("id") Long id, @Param("userId") Long userId);

}