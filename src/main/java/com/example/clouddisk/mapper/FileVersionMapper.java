package com.example.clouddisk.mapper;

import com.example.clouddisk.entity.FileVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FileVersionMapper {

    @Insert("INSERT INTO file_version(file_id, version_number, file_name, file_size, file_path, file_md5) " +
            "VALUES(#{fileId}, #{versionNumber}, #{fileName}, #{fileSize}, #{filePath}, #{fileMd5})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileVersion version);

    @Select("SELECT * FROM file_version WHERE file_id = #{fileId} ORDER BY version_number DESC")
    List<FileVersion> findByFileId(@Param("fileId") Long fileId);

    @Select("SELECT * FROM file_version WHERE file_id = #{fileId} AND version_number = #{versionNumber}")
    FileVersion findByFileIdAndVersion(@Param("fileId") Long fileId, @Param("versionNumber") Integer versionNumber);

    @Delete("DELETE FROM file_version WHERE file_id = #{fileId}")
    int deleteByFileId(@Param("fileId") Long fileId);
}