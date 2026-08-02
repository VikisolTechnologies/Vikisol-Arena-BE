package com.vikisol.arena.common.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Storage abstraction for uploaded files (CVs, profile photos, deliverables). The only
 * implementation today is {@link LocalDiskFileStorageService}. Swapping in Cloudinary later
 * (once credentials exist) is a one-class change - callers only ever depend on this interface.
 */
public interface FileStorageService {

    /**
     * Stores the file under a module/entity-scoped folder and returns a URL the frontend can
     * fetch it from directly.
     */
    StoredFile store(MultipartFile file, String module, String entityId, String documentType);

    record StoredFile(String url, String fileName, long sizeBytes) {
    }
}
