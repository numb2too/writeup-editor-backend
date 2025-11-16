package com.numb2.writeup_editor_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.numb2.writeup_editor_backend.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TestController {

    // JSON 檔案路徑，可在 application.properties 設定
    @Value("${writeup.json.path}")
    private String jsonFilePath;

    @Value("${writeup.folder.path}")
    private String writeupFolderPath;

    private final ObjectMapper objectMapper;

    public TestController() {
        this.objectMapper = new ObjectMapper();
        // 格式化輸出（縮排 2 空格）
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * POST /api/saveToJson
     * 儲存文章資料到 JSON 檔案
     */
    @PostMapping("/saveToJson")
    public ResponseEntity<Map<String, Object>> saveToJson(@RequestBody List<Map<String, Object>> articles) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 驗證資料
            if (articles == null || articles.isEmpty()) {
                response.put("success", false);
                response.put("message", "資料不能為空");
                return ResponseEntity.badRequest().body(response);
            }

            // 驗證每篇文章的必要欄位
            for (int i = 0; i < articles.size(); i++) {
                Map<String, Object> article = articles.get(i);
                if (!article.containsKey("title") ||
                        !article.containsKey("folder") ||
                        !article.containsKey("date")) {
                    response.put("success", false);
                    response.put("message", "文章 " + (i + 1) + " 缺少必要欄位 (title, folder, date)");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            // 確保目錄存在
            File jsonFile = new File(jsonFilePath);
            File parentDir = jsonFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 寫入 JSON 檔案
            objectMapper.writeValue(jsonFile, articles);

            // 記錄日誌
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            System.out.println("[" + timestamp + "] JSON 已更新，共 " + articles.size() + " 筆文章");

            // 回傳成功訊息
            response.put("success", true);
            response.put("message", "儲存成功");
            response.put("count", articles.size());
            response.put("timestamp", timestamp);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "儲存失敗");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/writeup
     * 讀取 JSON 檔案
     */
    @GetMapping("/writeup")
    public ResponseEntity<Map<String, Object>> getWriteup() {
        Map<String, Object> response = new HashMap<>();

        try {
            File jsonFile = new File(jsonFilePath);

            if (!jsonFile.exists()) {
                response.put("success", false);
                response.put("message", "JSON 檔案不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // 讀取 JSON 檔案
            String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
            List<Map<String, Object>> articles = objectMapper.readValue(
                    jsonContent,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            response.put("success", true);
            response.put("data", articles);
            response.put("count", articles.size());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "讀取失敗");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/folders
     * 掃描 /writeups 資料夾，回傳所有資料夾名稱
     */
    @GetMapping("/folders")
    public ResponseEntity<Map<String, Object>> scanFolders() {
        Map<String, Object> response = new HashMap<>();

        try {
            File writeupDir = new File("writeups");

            if (!writeupDir.exists() || !writeupDir.isDirectory()) {
                response.put("success", false);
                response.put("message", "writeups 資料夾不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // 取得所有第一層資料夾
            File[] folders = writeupDir.listFiles(File::isDirectory);
            List<String> folderNames = new java.util.ArrayList<>();

            if (folders != null) {
                for (File folder : folders) {
                    folderNames.add(folder.getName());
                }
            }

            response.put("success", true);
            response.put("folders", folderNames);
            response.put("count", folderNames.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "掃描失敗");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 偵測新資料夾（原有功能 + 檢測遺失資料夾）
     */
    @PostMapping("/detectNewFolders")
    public ApiResponse detectNewFolders() {
        try {
            // 1. 讀取現有 JSON 資料
            List<Map<String, Object>> existingArticles = readJsonFile();
            Set<String> existingFolders = existingArticles.stream()
                    .map(article -> (String) article.get("folder"))
                    .collect(Collectors.toSet());

            // 2. 讀取實際資料夾
            File baseDir = new File(writeupFolderPath);
            if (!baseDir.exists() || !baseDir.isDirectory()) {
                return ApiResponse.error("Writeups 目錄不存在");
            }

            File[] folders = baseDir.listFiles(File::isDirectory);
            if (folders == null) {
                folders = new File[0];
            }

            Set<String> actualFolders = Arrays.stream(folders)
                    .map(File::getName)
                    .collect(Collectors.toSet());

            // 3. 找出新資料夾（實際有但 JSON 沒有）
            List<String> newFolders = actualFolders.stream()
                    .filter(folder -> !existingFolders.contains(folder))
                    .sorted()
                    .collect(Collectors.toList());

            // 4. 找出遺失資料夾（JSON 有但實際沒有）
            List<String> missingFolders = existingFolders.stream()
                    .filter(folder -> !actualFolders.contains(folder))
                    .sorted()
                    .collect(Collectors.toList());

            // 5. 準備回傳結果
            Map<String, Object> result = new HashMap<>();
            result.put("newFolders", newFolders);
            result.put("missingFolders", missingFolders);

            String message = buildDetectionMessage(newFolders, missingFolders);

            return ApiResponse.success(message, result);

        } catch (IOException e) {
            e.printStackTrace();
            return ApiResponse.error("偵測失敗: " + e.getMessage());
        }
    }

    /**
     * 建立偵測訊息
     */
    private String buildDetectionMessage(List<String> newFolders, List<String> missingFolders) {
        StringBuilder message = new StringBuilder();

        if (!newFolders.isEmpty()) {
            message.append("發現 ").append(newFolders.size()).append(" 個新資料夾");
        }

        if (!missingFolders.isEmpty()) {
            if (message.length() > 0) {
                message.append("；");
            }
            message.append("發現 ").append(missingFolders.size()).append(" 個遺失資料夾");
        }

        if (message.length() == 0) {
            message.append("沒有發現新增或遺失的資料夾");
        }

        return message.toString();
    }


    /**
     * 讀取 JSON 檔案
     */
    private List<Map<String, Object>> readJsonFile() throws IOException {
        File jsonFile = new File(jsonFilePath);
        if (!jsonFile.exists()) {
            return new ArrayList<>();
        }

        return objectMapper.readValue(jsonFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
    }

    /**
     * 新增資料夾並建立 README.md
     */
    @PostMapping("/createFolder")
    public ApiResponse createFolder(@RequestBody Map<String, String> request) {
        String folderName = request.get("folderName");

        if (folderName == null || folderName.trim().isEmpty()) {
            return ApiResponse.error("資料夾名稱不能為空");
        }

        // 移除不合法字元
        folderName = folderName.trim().replaceAll("[\\\\/:*?\"<>|]", "");

        try {
            // 建立資料夾路徑
            Path folderPath = Paths.get(writeupFolderPath, folderName);

            // 檢查資料夾是否已存在
            if (Files.exists(folderPath)) {
                return ApiResponse.error("資料夾「" + folderName + "」已存在");
            }

            // 建立資料夾
            Files.createDirectories(folderPath);

            // 建立 README.md 檔案
            Path readmePath = folderPath.resolve("README.md");
            String readmeContent = "# " + folderName + "\n\n" +
                    "## 簡介\n\n" +
                    "這是關於 " + folderName + " 的文章。\n\n" +
                    "## 內容\n\n" +
                    "請在此處編寫內容...\n";

            Files.writeString(readmePath, readmeContent);

            return ApiResponse.success("成功建立資料夾「" + folderName + "」並建立 README.md", folderName);

        } catch (IOException e) {
            e.printStackTrace();
            return ApiResponse.error("建立資料夾失敗: " + e.getMessage());
        }
    }
}