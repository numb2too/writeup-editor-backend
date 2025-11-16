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

    @Value("${writeup.json.path}")
    private String jsonFilePath;

    @Value("${writeup.folder.path}")
    private String writeupFolderPath;

    private final ObjectMapper objectMapper;

    public TestController() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostMapping("/saveToJson")
    public ResponseEntity<Map<String, Object>> saveToJson(@RequestBody List<Map<String, Object>> articles) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (articles == null || articles.isEmpty()) {
                response.put("success", false);
                response.put("message", "資料不能為空");
                return ResponseEntity.badRequest().body(response);
            }

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

            File jsonFile = new File(jsonFilePath);
            File parentDir = jsonFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            objectMapper.writeValue(jsonFile, articles);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            System.out.println("[" + timestamp + "] JSON 已更新，共 " + articles.size() + " 筆文章");

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

            File[] folders = writeupDir.listFiles(File::isDirectory);
            List<String> folderNames = new ArrayList<>();

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
     * 偵測新資料夾（改進版：檢測重複 + 大小寫）
     */
    @PostMapping("/detectNewFolders")
    public ApiResponse detectNewFolders() {
        try {
            // 1. 讀取現有 JSON 資料
            List<Map<String, Object>> existingArticles = readJsonFile();

            // 檢測重複的 folder
            Map<String, Long> folderCount = existingArticles.stream()
                    .map(article -> (String) article.get("folder"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                            folder -> folder,
                            Collectors.counting()
                    ));

            // 找出重複的 folder
            Map<String, Long> duplicateFolders = folderCount.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));

            Set<String> existingFolders = existingArticles.stream()
                    .map(article -> (String) article.get("folder"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 2. 讀取實際資料夾
            File baseDir = new File(writeupFolderPath);
            if (!baseDir.exists() || !baseDir.isDirectory()) {
                return ApiResponse.error("Writeups 目錄不存在: " + writeupFolderPath);
            }

            File[] folders = baseDir.listFiles(File::isDirectory);
            if (folders == null) {
                folders = new File[0];
            }

            Set<String> actualFolders = Arrays.stream(folders)
                    .map(File::getName)
                    .collect(Collectors.toSet());

            // Debug 日誌
            System.out.println("=== 偵測資料夾 Debug ===");
            System.out.println("JSON 中的資料夾: " + existingFolders);
            System.out.println("實際的資料夾: " + actualFolders);

            if (!duplicateFolders.isEmpty()) {
                System.out.println("⚠️ 發現重複的 folder:");
                duplicateFolders.forEach((folder, count) ->
                        System.out.println("  - " + folder + " (出現 " + count + " 次)")
                );
            }

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

            // 5. 檢測大小寫不一致
            Map<String, String> caseMismatch = new HashMap<>();
            for (String missing : missingFolders) {
                for (String actual : actualFolders) {
                    if (missing.equalsIgnoreCase(actual) && !missing.equals(actual)) {
                        caseMismatch.put(missing, actual);
                    }
                }
            }

            // Debug: 印出比對結果
            if (!newFolders.isEmpty()) {
                System.out.println("新資料夾: " + newFolders);
            }
            if (!missingFolders.isEmpty()) {
                System.out.println("遺失資料夾: " + missingFolders);
                for (String missing : missingFolders) {
                    System.out.println("  遺失: '" + missing + "'");
                    for (String actual : actualFolders) {
                        if (missing.equalsIgnoreCase(actual)) {
                            System.out.println("    -> 可能對應: '" + actual + "' (大小寫不同)");
                        }
                    }
                }
            }

            // 6. 準備回傳結果
            Map<String, Object> result = new HashMap<>();
            result.put("newFolders", newFolders);
            result.put("missingFolders", missingFolders);
            result.put("duplicateFolders", duplicateFolders);

            if (!caseMismatch.isEmpty()) {
                result.put("caseMismatch", caseMismatch);
            }

            String message = buildDetectionMessage(newFolders, missingFolders, caseMismatch, duplicateFolders);

            return ApiResponse.success(message, result);

        } catch (IOException e) {
            e.printStackTrace();
            return ApiResponse.error("偵測失敗: " + e.getMessage());
        }
    }

    /**
     * 建立偵測訊息（改進版：加入重複檢測）
     */
    private String buildDetectionMessage(List<String> newFolders, List<String> missingFolders,
                                         Map<String, String> caseMismatch, Map<String, Long> duplicateFolders) {
        List<String> messages = new ArrayList<>();

        if (!duplicateFolders.isEmpty()) {
            messages.add("發現 " + duplicateFolders.size() + " 個重複的資料夾");
        }

        if (!newFolders.isEmpty()) {
            messages.add("發現 " + newFolders.size() + " 個新資料夾");
        }

        if (!caseMismatch.isEmpty()) {
            messages.add("發現 " + caseMismatch.size() + " 個大小寫不一致");
        } else if (!missingFolders.isEmpty()) {
            messages.add("發現 " + missingFolders.size() + " 個遺失資料夾");
        }

        if (messages.isEmpty()) {
            return "沒有發現任何問題";
        }

        return String.join("；", messages);
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

        // 移除不合法字元（保留大小寫）
        folderName = folderName.trim().replaceAll("[\\\\/:*?\"<>|]", "");

        try {
            Path folderPath = Paths.get(writeupFolderPath, folderName);

            if (Files.exists(folderPath)) {
                return ApiResponse.error("資料夾「" + folderName + "」已存在");
            }

            Files.createDirectories(folderPath);

            Path readmePath = folderPath.resolve("README.md");
            String readmeContent = "# " + folderName + "\n\n" +
                    "## 簡介\n\n" +
                    "這是關於 " + folderName + " 的文章。\n\n" +
                    "## 內容\n\n" +
                    "請在此處編寫內容...\n";

            Files.writeString(readmePath, readmeContent);

            System.out.println("成功建立資料夾: " + folderPath.toString());

            return ApiResponse.success("成功建立資料夾「" + folderName + "」並建立 README.md", folderName);

        } catch (IOException e) {
            e.printStackTrace();
            return ApiResponse.error("建立資料夾失敗: " + e.getMessage());
        }
    }

    /**
     * 修正資料夾名稱大小寫（新增）
     */
    @PostMapping("/fixFolderCase")
    public ApiResponse fixFolderCase(@RequestBody Map<String, String> request) {
        String oldName = request.get("oldName");
        String newName = request.get("newName");

        if (oldName == null || newName == null) {
            return ApiResponse.error("參數不完整");
        }

        try {
            // 讀取 JSON
            List<Map<String, Object>> articles = readJsonFile();

            // 更新 JSON 中的資料夾名稱
            boolean updated = false;
            for (Map<String, Object> article : articles) {
                if (oldName.equals(article.get("folder"))) {
                    article.put("folder", newName);
                    if (oldName.equals(article.get("title"))) {
                        article.put("title", newName);
                    }
                    updated = true;
                }
            }

            if (updated) {
                // 儲存回 JSON
                File jsonFile = new File(jsonFilePath);
                objectMapper.writeValue(jsonFile, articles);
                return ApiResponse.success("已修正資料夾名稱：" + oldName + " → " + newName, null);
            } else {
                return ApiResponse.error("找不到要修正的資料夾");
            }

        } catch (IOException e) {
            e.printStackTrace();
            return ApiResponse.error("修正失敗: " + e.getMessage());
        }
    }
}