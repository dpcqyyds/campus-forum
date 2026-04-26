package org.example.project.controller;

import org.example.project.api.ApiResponse;
import org.example.project.model.UserEntity;
import org.example.project.service.BackupService;
import org.example.project.service.ForumService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/backup")
public class BackupController {
    private final BackupService backupService;
    private final ForumService forumService;

    public BackupController(BackupService backupService, ForumService forumService) {
        this.backupService = backupService;
        this.forumService = forumService;
    }

    /**
     * 创建备份
     */
    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> createBackup(@RequestHeader("Authorization") String authorization) {
        UserEntity user = forumService.getUserByToken(authorization);
        forumService.requireSuperAdmin(user);

        String fileName = backupService.createBackup();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileName", fileName);
        data.put("message", "备份创建成功");
        return ApiResponse.ok(data);
    }

    /**
     * 获取备份列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> listBackups(@RequestHeader("Authorization") String authorization) {
        UserEntity user = forumService.getUserByToken(authorization);
        forumService.requireSuperAdmin(user);

        List<BackupService.BackupFileInfo> backups = backupService.listBackups();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("backups", backups);
        data.put("total", backups.size());
        return ApiResponse.ok(data);
    }

    /**
     * 下载备份文件
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadBackup(
            @PathVariable String fileName,
            @RequestHeader("Authorization") String authorization) {
        UserEntity user = forumService.getUserByToken(authorization);
        forumService.requireSuperAdmin(user);

        File file = backupService.getBackupFile(fileName);
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }

    /**
     * 删除备份文件
     */
    @DeleteMapping("/{fileName}")
    public ApiResponse<Map<String, Object>> deleteBackup(
            @PathVariable String fileName,
            @RequestHeader("Authorization") String authorization) {
        UserEntity user = forumService.getUserByToken(authorization);
        forumService.requireSuperAdmin(user);

        backupService.deleteBackup(fileName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "备份文件删除成功");
        return ApiResponse.ok(data);
    }

    /**
     * 上传并恢复备份
     */
    @PostMapping("/restore")
    public ApiResponse<Map<String, Object>> restoreBackup(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authorization) throws IOException {
        UserEntity user = forumService.getUserByToken(authorization);
        forumService.requireSuperAdmin(user);

        // 保存上传的文件到临时目录
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".sql")) {
            return ApiResponse.fail(400, "只支持.sql格式的备份文件");
        }

        Path tempFile = Files.createTempFile("restore_", ".sql");
        file.transferTo(tempFile.toFile());

        try {
            backupService.restoreBackup(tempFile);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "数据恢复成功");
            return ApiResponse.ok(data);
        } finally {
            // 清理临时文件
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 从备份列表恢复
     */
    @PostMapping("/restore/{fileName}")
    public ApiResponse<Map<String, Object>> restoreFromList(
            @PathVariable String fileName,
            @RequestHeader("Authorization") String authorization) {
        UserEntity user = forumService.getUserByToken(authorization);
        forumService.requireSuperAdmin(user);

        backupService.restoreBackup(fileName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "数据恢复成功");
        return ApiResponse.ok(data);
    }
}
