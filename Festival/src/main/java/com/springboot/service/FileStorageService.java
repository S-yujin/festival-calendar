package com.springboot.service;

import com.springboot.domain.FestivalAttachment;
import com.springboot.domain.FestivalEvent;
import com.springboot.repository.FestivalAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;
    private final FestivalAttachmentRepository attachmentRepository;

    public FileStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            FestivalAttachmentRepository attachmentRepository) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.attachmentRepository = attachmentRepository;

        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("업로드 폴더를 생성할 수 없습니다: " + this.uploadDir, e);
        }
    }

    public FestivalAttachment storeFile(MultipartFile file,
                                        FestivalEvent event,  // Festivals → FestivalEvent
                                        String uploaderName) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String storedFilename = UUID.randomUUID().toString() + ext;
        Path target = uploadDir.resolve(storedFilename);

        Files.copy(file.getInputStream(), target);

        FestivalAttachment att = new FestivalAttachment();
        att.setEvent(event);  // setFestival → setEvent
        att.setOriginalFilename(originalFilename);
        att.setStoredFilename(storedFilename);
        att.setContentType(file.getContentType());
        att.setSize(file.getSize());
        att.setUploadedAt(LocalDateTime.now());
        att.setUploadedBy(uploaderName);

        return attachmentRepository.save(att);
    }

    public Resource loadAsResource(FestivalAttachment attachment) {
        Path filePath = uploadDir.resolve(attachment.getStoredFilename());
        return new FileSystemResource(filePath.toFile());
    }
    
    public FestivalAttachment getAttachment(Long id) {
        return attachmentRepository.findById(id).orElse(null);
    }
}