package com.example.myproject.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * שירות העלאת קבצים לענן (Cloudinary).
 */
@Service
public class ImageStorageService {

    private final Cloudinary cloudinary;

    public ImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Upload user photo to Cloudinary.
     *
     * @param file   Multipart file (image)
     * @param userId folder structure per user
     * @return secure_url of uploaded image
     */
    public String uploadUserPhoto(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        try {
            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "shiduchim/users/" + userId,
                            "resource_type", "image"
                    )
            );

            Object url = uploadResult.get("secure_url");
            if (url == null) {
                throw new IllegalStateException("Cloudinary did not return secure_url");
            }

            return url.toString();

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }
}
