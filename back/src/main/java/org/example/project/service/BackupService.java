package org.example.project.service;

import org.example.project.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class BackupService {
    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAX_BACKUP_FILES = 30; // 保留最近30个备份

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.backup.directory:./backups}")
    private String backupDirectory;

    /**
     * 手动备份数据库
     */
    public String createBackup() {
        try {
            // 确保备份目录存在
            File backupDir = new File(backupDirectory);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            // 生成备份文件名
            String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
            String backupFileName = "backup_" + timestamp + ".sql";
            String backupFilePath = Paths.get(backupDirectory, backupFileName).toString();

            // 从JDBC URL中提取数据库名
            String databaseName = extractDatabaseName(datasourceUrl);

            // 执行mysqldump命令
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysqldump",
                    "-u" + dbUsername,
                    "-p" + dbPassword,
                    "--databases", databaseName,
                    "--result-file=" + backupFilePath,
                    "--single-transaction",
                    "--quick",
                    "--lock-tables=false"
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("备份失败，退出码: {}, 输出: {}", exitCode, output);
                // 将英文错误信息转换为中文
                String errorMsg = translateMysqlError(output.toString());
                throw new ApiException("数据库备份失败: " + errorMsg);
            }

            logger.info("数据库备份成功: {}", backupFileName);

            // 清理旧备份
            cleanOldBackups();

            return backupFileName;
        } catch (IOException | InterruptedException e) {
            logger.error("备份过程出错", e);
            throw new ApiException("数据库备份失败: " + e.getMessage());
        }
    }

    /**
     * 自动定时备份 - 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoBackup() {
        try {
            logger.info("开始执行自动备份...");
            String backupFileName = createBackup();
            logger.info("自动备份完成: {}", backupFileName);
        } catch (Exception e) {
            logger.error("自动备份失败", e);
        }
    }

    /**
     * 获取备份文件列表
     */
    public List<BackupFileInfo> listBackups() {
        List<BackupFileInfo> backupFiles = new ArrayList<>();
        File backupDir = new File(backupDirectory);

        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return backupFiles;
        }

        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".sql"));
        if (files != null) {
            for (File file : files) {
                BackupFileInfo info = new BackupFileInfo();
                info.setFileName(file.getName());
                info.setFilePath(file.getAbsolutePath());
                info.setFileSize(file.length());
                info.setCreatedAt(file.lastModified());
                backupFiles.add(info);
            }
        }

        // 按创建时间倒序排列
        backupFiles.sort(Comparator.comparing(BackupFileInfo::getCreatedAt).reversed());
        return backupFiles;
    }

    /**
     * 删除备份文件
     */
    public void deleteBackup(String fileName) {
        // 安全检查：只允许删除backup_开头的.sql文件
        if (!fileName.startsWith("backup_") || !fileName.endsWith(".sql")) {
            throw new ApiException("非法的备份文件名");
        }

        Path filePath = Paths.get(backupDirectory, fileName);
        try {
            if (!Files.exists(filePath)) {
                throw new ApiException("备份文件不存在");
            }
            Files.delete(filePath);
            logger.info("删除备份文件: {}", fileName);
        } catch (IOException e) {
            logger.error("删除备份文件失败", e);
            throw new ApiException("删除备份文件失败: " + e.getMessage());
        }
    }

    /**
     * 恢复数据库（从备份列表）
     */
    public void restoreBackup(String fileName) {
        // 安全检查
        if (!fileName.startsWith("backup_") || !fileName.endsWith(".sql")) {
            throw new ApiException("非法的备份文件名");
        }

        Path filePath = Paths.get(backupDirectory, fileName);
        if (!Files.exists(filePath)) {
            throw new ApiException("备份文件不存在");
        }

        restoreFromFile(filePath, fileName);
    }

    /**
     * 恢复数据库（从完整路径）
     */
    public void restoreBackup(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new ApiException("备份文件不存在");
        }
        restoreFromFile(filePath, filePath.getFileName().toString());
    }

    /**
     * 从文件恢复数据库
     */
    private void restoreFromFile(Path filePath, String displayName) {
        try {
            String databaseName = extractDatabaseName(datasourceUrl);

            // 执行mysql命令恢复数据库
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysql",
                    "-u" + dbUsername,
                    "-p" + dbPassword,
                    databaseName
            );

            processBuilder.redirectInput(filePath.toFile());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("恢复失败，退出码: {}, 输出: {}", exitCode, output);
                String errorMsg = translateMysqlError(output.toString());
                throw new ApiException("数据库恢复失败: " + errorMsg);
            }

            logger.info("数据库恢复成功: {}", displayName);
        } catch (IOException | InterruptedException e) {
            logger.error("恢复过程出错", e);
            throw new ApiException("数据库恢复失败: " + e.getMessage());
        }
    }

    /**
     * 上传并恢复备份文件
     */
    public void uploadAndRestore(byte[] fileContent, String originalFileName) {
        try {
            // 生成新的文件名
            String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
            String fileName = "backup_uploaded_" + timestamp + ".sql";
            Path filePath = Paths.get(backupDirectory, fileName);

            // 确保备份目录存在
            File backupDir = new File(backupDirectory);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            // 保存上传的文件
            Files.write(filePath, fileContent);
            logger.info("上传备份文件: {}", fileName);

            // 执行恢复
            restoreBackup(fileName);
        } catch (IOException e) {
            logger.error("上传备份文件失败", e);
            throw new ApiException("上传备份文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取备份文件
     */
    public File getBackupFile(String fileName) {
        // 安全检查
        if (!fileName.startsWith("backup_") || !fileName.endsWith(".sql")) {
            throw new ApiException("非法的备份文件名");
        }

        Path filePath = Paths.get(backupDirectory, fileName);
        if (!Files.exists(filePath)) {
            throw new ApiException("备份文件不存在");
        }

        return filePath.toFile();
    }

    /**
     * 获取备份文件路径
     */
    public Path getBackupFilePath(String fileName) {
        // 安全检查
        if (!fileName.startsWith("backup_") || !fileName.endsWith(".sql")) {
            throw new ApiException("非法的备份文件名");
        }

        Path filePath = Paths.get(backupDirectory, fileName);
        if (!Files.exists(filePath)) {
            throw new ApiException("备份文件不存在");
        }

        return filePath;
    }

    /**
     * 清理旧备份文件，保留最近N个
     */
    private void cleanOldBackups() {
        try {
            List<BackupFileInfo> backups = listBackups();
            if (backups.size() > MAX_BACKUP_FILES) {
                // 删除超出数量的旧备份
                for (int i = MAX_BACKUP_FILES; i < backups.size(); i++) {
                    BackupFileInfo backup = backups.get(i);
                    Files.deleteIfExists(Paths.get(backup.getFilePath()));
                    logger.info("清理旧备份: {}", backup.getFileName());
                }
            }
        } catch (Exception e) {
            logger.error("清理旧备份失败", e);
        }
    }

    /**
     * 从JDBC URL中提取数据库名
     */
    private String extractDatabaseName(String jdbcUrl) {
        // jdbc:mysql://localhost:3306/campus_forum?...
        try {
            logger.info("解析JDBC URL: {}", jdbcUrl);

            // 使用正则表达式匹配数据库名
            // 格式: jdbc:mysql://host:port/database?params
            // 找到第三个 / 之后、? 之前的部分
            int protocolEnd = jdbcUrl.indexOf("://");
            if (protocolEnd == -1) {
                throw new ApiException("无效的JDBC URL格式");
            }

            // 跳过 protocol://
            int dbStart = jdbcUrl.indexOf('/', protocolEnd + 3);
            if (dbStart == -1) {
                throw new ApiException("无效的JDBC URL格式：缺少数据库名");
            }

            // 从第三个 / 之后开始提取
            String remaining = jdbcUrl.substring(dbStart + 1);
            logger.info("提取的数据库部分: {}", remaining);

            // 去掉参数部分（? 之前）
            int questionMarkIndex = remaining.indexOf('?');
            String dbName = questionMarkIndex > 0 ? remaining.substring(0, questionMarkIndex) : remaining;
            logger.info("解析的数据库名: {}", dbName);

            if (dbName.isEmpty()) {
                throw new ApiException("无法从URL中提取数据库名");
            }

            return dbName;
        } catch (Exception e) {
            logger.error("解析数据库名称失败: {}", e.getMessage());
            throw new ApiException("无法解析数据库名称: " + e.getMessage());
        }
    }

    /**
     * 翻译MySQL错误信息为中文
     */
    private String translateMysqlError(String errorMsg) {
        if (errorMsg == null || errorMsg.isEmpty()) {
            return "未知错误";
        }

        // 移除警告信息
        String msg = errorMsg.replaceAll("mysqldump: \\[Warning\\] Using a password on the command line interface can be insecure\\.\\s*", "");

        // 翻译常见错误
        if (msg.contains("Unknown database")) {
            return "数据库不存在，请检查数据库配置";
        }
        if (msg.contains("Access denied")) {
            return "数据库访问被拒绝，请检查用户名和密码";
        }
        if (msg.contains("Can't connect")) {
            return "无法连接到数据库服务器，请检查数据库是否运行";
        }

        return msg.trim();
    }

    /**
     * 备份文件信息
     */
    public static class BackupFileInfo {
        private String fileName;
        private String filePath;
        private long fileSize;
        private long createdAt;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }
    }
}
