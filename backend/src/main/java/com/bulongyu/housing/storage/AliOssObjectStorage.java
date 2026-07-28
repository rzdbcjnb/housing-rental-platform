package com.bulongyu.housing.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.config.AliOssProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;

/**
 * 图片上传对象存储组件
 */
@Component
@EnableConfigurationProperties(AliOssProperties.class)
public class AliOssObjectStorage implements ObjectStorage {
    private static final Logger log = LoggerFactory.getLogger(AliOssObjectStorage.class);

    private final AliOssProperties properties;
    /**
     * 初始化 {@code AliOssObjectStorage} 并注入所需依赖。
     *
     * @param properties 配置属性
     */
    public AliOssObjectStorage(AliOssProperties properties) { this.properties = properties; }

    /**
     * 校验图片并上传到阿里云 OSS。
     *
     * @param input 输入值
     * @param contentLength 文件大小
     * @param contentType 文件内容类型
     * @param objectKey OSS 对象键
     */
    @Override
    public String upload(InputStream input, long contentLength, String contentType, String objectKey) {
        validateConfiguration();
        OSS client = client();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            metadata.setContentType(contentType);
            client.putObject(properties.bucketName(), objectKey, input, metadata);
            return properties.effectiveBaseUrl() + "/" + objectKey;
        } catch (RuntimeException exception) {
            log.error("上传OSS对象失败，参数：bucket={}，objectKey={}",
                    properties.bucketName(), objectKey, exception);
            throw new BusinessException("OSS_UPLOAD_FAILED", "图片上传到OSS失败", HttpStatus.BAD_GATEWAY);
        } finally {
            client.shutdown();
        }
    }

    /**
     * 校验权限后删除指定业务数据。
     *
     * @param objectUrl OSS 对象地址
     */
    @Override
    public void delete(String objectUrl) {
        log.info("删除OSS对象，参数：objectUrlPresent={}", objectUrl != null && !objectUrl.isBlank());
        validateConfiguration();
        URI objectUri;
        URI baseUri;
        try {
            objectUri = URI.create(objectUrl);
            baseUri = URI.create(properties.effectiveBaseUrl());
        } catch (RuntimeException exception) {
            throw invalidUrl();
        }
        String path = objectUri.getPath();
        if (path == null || !path.startsWith("/houses/")
                || baseUri.getScheme() == null || baseUri.getHost() == null
                || objectUri.getScheme() == null || objectUri.getHost() == null
                || !baseUri.getScheme().equalsIgnoreCase(objectUri.getScheme())
                || !baseUri.getHost().equalsIgnoreCase(objectUri.getHost())) throw invalidUrl();
        String key = path.substring(1);
        OSS client = client();
        try { client.deleteObject(properties.bucketName(), key); }
        catch (RuntimeException exception) {
            log.error("删除OSS对象失败，参数：bucket={}，objectKey={}",
                    properties.bucketName(), key, exception);
            throw new BusinessException("OSS_DELETE_FAILED", "OSS图片删除失败", HttpStatus.BAD_GATEWAY);
        } finally { client.shutdown(); }
    }

    /**
     * 创建并缓存阿里云 OSS 客户端。
     */
    private OSS client() {
        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setMaxConnections(64);
        return new OSSClientBuilder().build(properties.endpoint(), properties.accessKeyId(),
                properties.accessKeySecret(), config);
    }
    /**
     * 校验阿里云 OSS 配置是否完整。
     */
    private void validateConfiguration() {
        if (blank(properties.endpoint()) || blank(properties.accessKeyId())
                || blank(properties.accessKeySecret()) || blank(properties.bucketName()))
            throw new BusinessException("OSS_NOT_CONFIGURED", "阿里云OSS配置不完整", HttpStatus.SERVICE_UNAVAILABLE);
    }
    /**
     * 判断文本是否为空。
     *
     * @param value 字段值
     * @return 条件成立时返回 true，否则返回 false
     */
    private boolean blank(String value) { return value == null || value.isBlank(); }
    /**
     * 创建 OSS 地址无效的业务异常。
     */
    private BusinessException invalidUrl() {
        return new BusinessException("INVALID_OSS_URL", "无效OSS图片URL", HttpStatus.BAD_REQUEST);
    }
}
