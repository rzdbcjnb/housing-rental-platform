package com.bulongyu.housing.storage;

import java.io.InputStream;

/**
 * 图片上传对象存储组件
 */
public interface ObjectStorage {
    /**
     * 校验图片并上传到阿里云 OSS。
     *
     * @param input 输入值
     * @param contentLength 文件大小
     * @param contentType 文件内容类型
     * @param objectKey OSS 对象键
     */
    String upload(InputStream input, long contentLength, String contentType, String objectKey);
    /**
     * 校验权限后删除指定业务数据。
     *
     * @param objectUrl OSS 对象地址
     */
    void delete(String objectUrl);
}
