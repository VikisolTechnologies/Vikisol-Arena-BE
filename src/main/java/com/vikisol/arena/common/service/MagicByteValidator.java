package com.vikisol.arena.common.service;

/**
 * PRODUCTION-CHECKLIST.md: "validate by magic bytes, not extension or client MIME." An
 * extension-only check (the previous state of LocalDiskFileStorageService) trivially accepts
 * an arbitrary file - HTML-with-script, an executable, a polyglot - renamed to `.pdf`. This
 * only confirms the file's actual signature matches its claimed extension; it does not scan
 * for malware (no ClamAV/scanning service is available in this environment - see BLOCKED.md).
 */
final class MagicByteValidator {

    private MagicByteValidator() {}

    static boolean matches(String extension, byte[] header) {
        if (header == null) return false;
        return switch (extension) {
            case ".pdf" -> startsWith(header, "%PDF-".getBytes());
            // DOC (legacy binary format) uses the OLE2 compound-file signature.
            case ".doc" -> startsWith(header, new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1});
            // DOCX is a ZIP container - same signature as any other ZIP/PK-format file, which is
            // the best a magic-byte check alone can do without unzipping and inspecting
            // [Content_Types].xml; still rules out non-ZIP files entirely (the common case of a
            // renamed non-Office file).
            case ".docx" -> startsWith(header, new byte[]{0x50, 0x4B, 0x03, 0x04});
            case ".png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case ".jpg", ".jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case ".gif" -> startsWith(header, "GIF87a".getBytes()) || startsWith(header, "GIF89a".getBytes());
            case ".webp" -> header.length >= 12 && startsWith(header, "RIFF".getBytes())
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    private static boolean startsWith(byte[] header, byte[] signature) {
        if (header.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) return false;
        }
        return true;
    }
}
