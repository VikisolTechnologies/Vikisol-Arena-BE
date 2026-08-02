package com.vikisol.arena.common.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

// Serves files stored by LocalDiskFileStorageService. Public GET (matches HRLMS-BE's convention
// of only gating uploads, not downloads of already-uploaded files) - fine for local-dev CVs/photos.
@RestController
public class FileController {

    @Value("${app.storage.root-dir:./uploads}")
    private String rootDir;

    @GetMapping("/files/{module}/{entityId}/{documentType}/{fileName}")
    public ResponseEntity<Resource> getFile(@PathVariable String module, @PathVariable String entityId,
                                             @PathVariable String documentType, @PathVariable String fileName) {
        Path path = Path.of(rootDir, module, entityId, documentType, fileName);
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(resource);
    }
}
