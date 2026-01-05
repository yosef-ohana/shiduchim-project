package com.example.myproject.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ImageStorageService {

    private static final long MAX_BYTES = 8L * 1024 * 1024; // ✅ 8MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    public static class UploadResult {
        private final String secureUrl;
        private final String publicId;
        private final String format;
        private final Long bytes;

        public UploadResult(String secureUrl, String publicId, String format, Long bytes) {
            this.secureUrl = secureUrl;
            this.publicId = publicId;
            this.format = format;
            this.bytes = bytes;
        }

        public String getSecureUrl() { return secureUrl; }
        public String getPublicId() { return publicId; }
        public String getFormat() { return format; }
        public Long getBytes() { return bytes; }
    }

    private final Cloudinary cloudinary;

    public ImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /** העלאת תמונת משתמש ל-Cloudinary, מחזיר גם secureUrl וגם publicId. */
    public UploadResult uploadUserPhoto(MultipartFile file, Long userId) {
        Objects.requireNonNull(userId, "userId is required");

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        // ✅ 8MB enforcement
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("FILE_TOO_LARGE");
        }

        // ✅ allow-list of image types (לא כל image/*)
        String contentType = file.getContentType();
        String ct = (contentType == null) ? null : contentType.toLowerCase();
        if (ct == null || !ALLOWED_IMAGE_TYPES.contains(ct)) {
            throw new IllegalArgumentException("Only JPEG/PNG/WEBP content types are allowed");
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "shiduchim/users/" + userId,
                            "resource_type", "image",
                            "overwrite", false,
                            "unique_filename", true
                    )
            );

            Object url = uploadResult.get("secure_url");
            Object pid = uploadResult.get("public_id");
            Object format = uploadResult.get("format");
            Object bytes = uploadResult.get("bytes");

            if (url == null || pid == null) {
                throw new IllegalStateException("Cloudinary did not return secure_url/public_id");
            }

            return new UploadResult(
                    String.valueOf(url),
                    String.valueOf(pid),
                    format == null ? null : String.valueOf(format),
                    bytes instanceof Number ? ((Number) bytes).longValue() : null
            );

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }

    /** מחיקה בענן לפי publicId (אם אין publicId - אין מה למחוק). */
    public boolean deleteByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return false;

        try {
            Map<?, ?> res = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", "image")
            );
            Object result = res.get("result"); // "ok" / "not found" וכו'
            return result != null && String.valueOf(result).toLowerCase().contains("ok");
        } catch (Exception e) {
            // לא מפילים מערכת על delete בענן
            return false;
        }
    }
}