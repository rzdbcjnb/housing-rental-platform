package com.bulongyu.housing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片上传配置属性
 */
@ConfigurationProperties(prefix = "app.oss")
public record AliOssProperties(String endpoint, String accessKeyId, String accessKeySecret,
                               String bucketName, String publicBaseUrl) {
    /**
     * 获取 OSS 文件访问地址，未配置时根据地域和存储桶生成默认地址。
     */
    public String effectiveBaseUrl() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) return publicBaseUrl.replaceAll("/+$", "");
        String host = endpoint == null ? "" : endpoint.replaceFirst("^https?://", "").replaceAll("/+$", "");
        return "https://" + bucketName + "." + host;
    }
}
