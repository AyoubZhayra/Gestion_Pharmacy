package com.pharmacy.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileStorageService {

    @Value("${upload.path}")
    private String uploadPath;

    public String storeFile(MultipartFile file) {
        try {
            Path root = Paths.get(uploadPath);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            // Generate unique filename
            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), root.resolve(filename));
            
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
        }
    }
} 