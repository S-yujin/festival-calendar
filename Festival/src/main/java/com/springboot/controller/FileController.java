package com.springboot.controller;

import com.springboot.domain.FestivalAttachment;
import com.springboot.repository.FestivalAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequiredArgsConstructor
public class FileController {

    private final FestivalAttachmentRepository attachmentRepository;

    // ⚠ 실제 업로드 경로와 꼭 맞춰야 함!
    private final Path uploadDir = Paths.get("uploads");

    @GetMapping("/files/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable("id") Long id) {

        // 1) 첨부 메타데이터 조회
        FestivalAttachment attachment = attachmentRepository.findById(id).orElse(null);
        if (attachment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("해당 ID의 첨부파일이 없습니다: " + id);
        }

        try {
            // 2) 디스크의 실제 파일 경로 만들기
            Path filePath = uploadDir.resolve(attachment.getStoredFilename());

            if (!Files.exists(filePath)) {
                // 파일이 없으면 404
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("서버에 파일이 존재하지 않습니다: " + filePath.toAbsolutePath());
            }

            Resource resource = new UrlResource(filePath.toUri());

            // 3) Content-Type (없으면 기본값)
            String contentType = attachment.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }
            }

            // 4) 한글 파일명 인코딩
            String encodedFileName = UriUtils.encode(
                    attachment.getOriginalFilename(), StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedFileName + "\"")
                    .contentLength(Files.size(filePath))
                    .body(resource);

        } catch (IOException e) {
            // 예외 나면 500 + 메시지
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("파일을 읽는 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
