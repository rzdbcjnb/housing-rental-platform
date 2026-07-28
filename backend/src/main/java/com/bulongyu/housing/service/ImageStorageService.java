package com.bulongyu.housing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.storage.ObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传业务服务
 */
@Service
public class ImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final long MAX_SIZE = 2L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    private final ObjectStorage storage;

    /**
     * 初始化 {@code ImageStorageService} 并注入所需依赖。
     *
     * @param storage 对象存储组件
     */
    public ImageStorageService(ObjectStorage storage) { this.storage = storage; }

    /**
     * 校验图片内容并保存到对象存储。
     *
     * @param image 图片文件
     * @param userId 当前认证用户编号
     */
    public String store(Long userId, MultipartFile image) {
        if (image == null || image.isEmpty()) invalid("请选择要上传的图片");
        if (image.getSize() > MAX_SIZE) invalid("图片大小不能超过2MB");
        String extension = extension(image.getOriginalFilename());
        if (!EXTENSIONS.contains(extension)) invalid("不支持的图片格式，仅支持 jpg、jpeg、png、gif");
        validateImage(image);
        String objectKey = "houses/" + userId + "/" + Instant.now().getEpochSecond() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "." + extension;
        try (InputStream input = image.getInputStream()) {
            return storage.upload(input, image.getSize(), image.getContentType(), objectKey);
        } catch (IOException exception) {
            throw new BusinessException("UPLOAD_READ_FAILED", "读取图片失败", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 校验权限后删除指定业务数据。
     *
     * @param url 访问地址
     */
    public void delete(Long userId, String url) {
        if (!belongsToUser(userId, url)) {
            throw new BusinessException("IMAGE_FORBIDDEN", "只能删除自己上传的图片", HttpStatus.FORBIDDEN);
        }
        log.info("删除图片，参数：userId={}，urlPresent={}", userId, url != null && !url.isBlank());
        storage.delete(url);
    }

    /**
     * 在房源事务提交后清理已被替换或删除的托管图片。
     *
     * @param url 旧图片地址
     */
    public void deleteManagedAfterCommit(String url) {
        if (url == null || url.isBlank()) return;
        Runnable cleanup = () -> {
            try {
                storage.delete(url);
            } catch (RuntimeException exception) {
                log.warn("清理旧房源图片失败，参数：urlPresent={}", true, exception);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    /**
     * 判断图片地址是否位于当前用户的 OSS 目录。
     *
     * @param userId 当前认证用户编号
     * @param url 图片地址
     */
    private boolean belongsToUser(Long userId, String url) {
        if (userId == null || url == null || url.isBlank()) return false;
        try {
            String path = URI.create(url).getPath();
            return path != null && path.startsWith("/houses/" + userId + "/");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 解码文件流并校验其为真实图片。
     *
     * @param image 图片文件
     */
    private void validateImage(MultipartFile image) {
        // 解码文件流以校验真实图片内容，不能仅信任文件扩展名。
        try (InputStream input = image.getInputStream()) {
            if (ImageIO.read(input) == null) invalid("文件内容不是有效图片");
        } catch (IOException exception) {
            throw new BusinessException("UPLOAD_READ_FAILED", "读取图片失败", HttpStatus.BAD_REQUEST);
        }
    }
    /**
     * 根据图片格式确定安全的文件扩展名。
     *
     * @param name 名称
     */
    private String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
    /**
     * 创建参数校验失败类型的业务异常。
     *
     * @param message 消息
     */
    private void invalid(String message) { throw new BusinessException("INVALID_IMAGE", message, HttpStatus.BAD_REQUEST); }
}
