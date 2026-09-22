package com.example.clouddisk.ai;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiIndexTaskMapper {
    @Insert("INSERT INTO ai_index_task(file_id,user_id,generation,operation,status) " +
            "VALUES(#{fileId},#{userId},#{generation},#{operation},'PENDING')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiIndexTask task);

    @Select("SELECT * FROM ai_index_task WHERE id = #{id}")
    AiIndexTask findById(@Param("id") Long id);

    @Select("SELECT * FROM ai_index_task WHERE file_id = #{fileId} ORDER BY id DESC LIMIT 1")
    AiIndexTask findLatestForFile(@Param("fileId") Long fileId);

    @Select("SELECT * FROM ai_index_task WHERE status IN ('PENDING','RETRY') ORDER BY id LIMIT 100")
    List<AiIndexTask> findPending();

    @Update("UPDATE ai_index_task SET status = 'RETRY' WHERE status = 'RUNNING' " +
            "AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
    int recoverStale();

    @Update("UPDATE ai_index_task SET status = #{status}, last_error = #{error} WHERE id = #{id}")
    int setStatus(@Param("id") Long id, @Param("status") String status,
                  @Param("error") String error);

    @Update("UPDATE ai_index_task SET status = 'RUNNING', attempts = attempts + 1 " +
            "WHERE id = #{id} AND status IN ('PENDING','RETRY')")
    int startAttempt(@Param("id") Long id);

    @Update("UPDATE ai_index_task SET status = 'PENDING', attempts = 0, last_error = NULL " +
            "WHERE id = #{id} AND status = 'FAILED'")
    int retry(@Param("id") Long id);
}
