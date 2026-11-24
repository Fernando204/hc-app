package com.horsecontrol.eloquestre.controllers.files;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.services.files.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Upload de arquivos. Os arquivos são servidos publicamente em GET /files/{path} (ver StaticFilesConfig). */
@RestController
public class FileController {

    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }

    /** POST /uploads?bucket=avatars|media (multipart, campo "file") → {path} */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@RequestAttribute(JwtAuthFilter.USER_ID) String me,
                                      @RequestParam String bucket, @RequestParam("file") MultipartFile file) {
        return Map.of("path", storage.store(me, bucket, file));
    }
}
