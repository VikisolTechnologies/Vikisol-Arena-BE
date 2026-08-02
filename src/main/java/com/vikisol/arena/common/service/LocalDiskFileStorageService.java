package com.vikisol.arena.common.service;

import com.vikisol.arena.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

// Local-disk implementation for this phase - deliberately simple so it's a one-class swap to
// Cloudinary later (see FileStorageService). Not durable across redeploys, which is fine since
// this phase never deploys anywhere; everything runs on localhost against local Postgres.
@Service
@Slf4j
public class LocalDiskFileStorageService implements FileStorageService {

    @Value("${app.storage.root-dir:./uploads}")
    private String rootDir;

    @Value("${app.storage.public-base-url:http://localhost:8081/api/v1/files}")
    private String publicBaseUrl;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".png", ".jpg", ".jpeg", ".gif", ".webp");

    @Override
    public StoredFile store(MultipartFile file, String module, String entityId, String documentType) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("File type not allowed. Supported: PDF, Word, and common image formats.");
        }
        try {
            String folder = "%s/%s/%s".formatted(sanitize(module), sanitize(entityId), sanitize(documentType));
            Path dir = Path.of(rootDir, folder);
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + extension;
            Path target = dir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String url = publicBaseUrl + "/" + folder + "/" + storedName;
            return new StoredFile(url, originalName, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "unspecified";
        String cleaned = value.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isBlank() ? "unspecified" : cleaned;
    }
}
