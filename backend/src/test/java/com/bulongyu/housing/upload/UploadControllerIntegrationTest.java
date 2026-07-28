package com.bulongyu.housing.upload;

import com.bulongyu.housing.storage.ObjectStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired FakeOssStorage oss;

    @Test
    void validatesUploadsAndDeletesThroughOss() throws Exception {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile image = new MockMultipartFile("image", "room.png", "image/png", png);
        mvc.perform(multipart("/api/upload/").file(image)).andExpect(status().isUnauthorized());
        String body = mvc.perform(multipart("/api/upload/").file(image)
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("https://test-bucket.oss-cn-test.aliyuncs.com/houses/1/")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        assertThat(oss.objectKey).startsWith("houses/1/").endsWith(".png");
        mvc.perform(delete("/api/upload/").param("url", url).with(jwt().jwt(j -> j.subject("2"))))
                .andExpect(status().isForbidden());
        assertThat(oss.contentLength).isEqualTo(png.length);
        mvc.perform(delete("/api/upload/").param("url", url).with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("图片删除成功"));
        assertThat(oss.deletedUrl).isEqualTo(url);

        MockMultipartFile fake = new MockMultipartFile("image", "bad.png", "image/png", "not-image".getBytes());
        mvc.perform(multipart("/api/upload/").file(fake).with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class StorageTestConfig {
        @Bean @Primary FakeOssStorage fakeOssStorage() { return new FakeOssStorage(); }
    }

    static class FakeOssStorage implements ObjectStorage {
        String objectKey; String deletedUrl; long contentLength;
        @Override public String upload(InputStream input, long length, String contentType, String key) {
            objectKey = key; contentLength = length;
            return "https://test-bucket.oss-cn-test.aliyuncs.com/" + key;
        }
        @Override public void delete(String objectUrl) { deletedUrl = objectUrl; }
    }
}
