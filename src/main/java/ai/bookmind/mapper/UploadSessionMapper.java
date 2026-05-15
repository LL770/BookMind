package ai.bookmind.mapper;

import ai.bookmind.entity.UploadSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UploadSessionMapper {

    @Insert("INSERT INTO upload_session (upload_id, user_id, file_name, file_size, chunk_size, total_chunks, received_chunks, status) " +
            "VALUES (#{uploadId}, #{userId}, #{fileName}, #{fileSize}, #{chunkSize}, #{totalChunks}, #{receivedChunks}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UploadSession session);

    @Select("SELECT * FROM upload_session WHERE upload_id = #{uploadId}")
    UploadSession selectByUploadId(@Param("uploadId") String uploadId);

    @Select("SELECT * FROM upload_session WHERE upload_id = #{uploadId} FOR UPDATE")
    UploadSession selectByUploadIdForUpdate(@Param("uploadId") String uploadId);

    @Update("UPDATE upload_session SET received_chunks = #{receivedChunks} WHERE upload_id = #{uploadId}")
    int updateReceivedChunks(@Param("uploadId") String uploadId, @Param("receivedChunks") Integer receivedChunks);

    @Update("UPDATE upload_session SET status = #{status}, merged_book_id = #{mergedBookId} WHERE upload_id = #{uploadId}")
    int updateComplete(@Param("uploadId") String uploadId, @Param("status") Integer status, @Param("mergedBookId") Long mergedBookId);

    @Update("UPDATE upload_session SET status = 3 WHERE upload_id = #{uploadId}")
    int markFailed(@Param("uploadId") String uploadId);
}
