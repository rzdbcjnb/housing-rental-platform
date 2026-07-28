package com.bulongyu.housing.controller;


import com.bulongyu.housing.security.CurrentUserId;
import com.bulongyu.housing.service.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 图片上传接口控制器
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {
    private final ImageStorageService storage;
    /**
     * 初始化 {@code UploadController} 并注入所需依赖。
     *
     * @param storage 对象存储组件
     */
    public UploadController(ImageStorageService storage) { this.storage = storage; }

    /**
     * 校验图片并上传到阿里云 OSS。
     *
     * @param image 图片文件
     */
    @PostMapping({"/", "/image/"})
    public ResponseEntity<Map<String,String>> upload(
            @CurrentUserId Long userId, @RequestPart("image") MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", storage.store(userId, image)));
    }

    /**
     * 校验权限后删除指定业务数据。
     *
     * @param url 访问地址
     * @return 无响应正文的 HTTP 结果
     */
    @DeleteMapping({"/", "/image/"})
    public Map<String,String> delete(@CurrentUserId Long userId, @RequestParam String url) {
        storage.delete(userId, url);
        return Map.of("message", "图片删除成功");
    }
}
