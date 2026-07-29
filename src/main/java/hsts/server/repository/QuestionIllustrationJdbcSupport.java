package hsts.server.repository;

import hsts.common.QuestionIllustrationDTO;
import hsts.server.entity.QuestionIllustration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

final class QuestionIllustrationJdbcSupport {
    private QuestionIllustrationJdbcSupport() {
    }

    static QuestionIllustration readEntity(ResultSet resultSet) throws SQLException {
        String mediaType = resultSet.getString("illustration_media_type");
        byte[] content = resultSet.getBytes("illustration_content_bytes");
        Object byteLength = resultSet.getObject("illustration_byte_length");
        Object width = resultSet.getObject("illustration_width_pixels");
        Object height = resultSet.getObject("illustration_height_pixels");
        String checksum = resultSet.getString("illustration_content_sha256");
        LocalDateTime createdAt = resultSet.getObject(
                "illustration_created_at", LocalDateTime.class
        );

        if (mediaType == null && content == null && byteLength == null
                && width == null && height == null && checksum == null
                && createdAt == null) {
            return null;
        }
        if (mediaType == null || content == null || byteLength == null
                || width == null || height == null || checksum == null
                || createdAt == null) {
            throw new IllegalArgumentException(
                    "Question illustration data is incomplete"
            );
        }

        int persistedLength = ((Number) byteLength).intValue();
        if (persistedLength != content.length) {
            throw new IllegalArgumentException(
                    "Question illustration byte length is invalid"
            );
        }
        return QuestionIllustration.rehydrate(
                mediaType,
                content,
                persistedLength,
                ((Number) width).intValue(),
                ((Number) height).intValue(),
                checksum,
                createdAt
        );
    }

    static QuestionIllustrationDTO readDto(ResultSet resultSet) throws SQLException {
        QuestionIllustration illustration = readEntity(resultSet);
        return illustration == null ? null : new QuestionIllustrationDTO(
                illustration.getMediaType(),
                illustration.getContent(),
                illustration.getByteLength(),
                illustration.getWidth(),
                illustration.getHeight(),
                illustration.getSha256()
        );
    }
}
