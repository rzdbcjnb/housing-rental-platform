package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeIndexServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void replacesHouseAndFaqCollectionsWithGroundedDocuments() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        VectorStore store = mock(VectorStore.class);
        HouseMapper houses = mock(HouseMapper.class);
        UserMapper users = mock(UserMapper.class);
        when(provider.getIfAvailable()).thenReturn(store);
        when(users.findProfileByUserId(9L)).thenReturn(new UserProfile(3L, 9L, "13800000000",
                "admin", "", LocalDateTime.now(), LocalDateTime.now()));
        LocalDateTime sourceUpdatedAt = LocalDateTime.of(2026, 7, 27, 12, 30);
        when(houses.latestPublicUpdateTime()).thenReturn(sourceUpdatedAt);
        when(houses.countPublic(any())).thenReturn(1L);
        when(houses.findPublic(any(), eq(0), eq(100))).thenReturn(List.of(house()));

        KnowledgeIndexService.IndexResult result = new KnowledgeIndexService(provider, houses, users).syncAll(9L);

        assertThat(result.houses()).isEqualTo(1);
        assertThat(result.houseDocuments()).isEqualTo(4);
        assertThat(result.faqDocuments()).isEqualTo(7);
        verify(store).delete("type == 'index_state'");
        verify(store).delete("type == 'house'");
        verify(store).delete("type == 'faq'");
        verify(store, times(3)).add(argThat(documents -> documents.stream().allMatch(this::hasTrustedType)));
    }

    private boolean hasTrustedType(Document document) {
        return List.of("house", "faq", "index_state").contains(document.getMetadata().get("type"));
    }

    private HouseRow house() {
        return new HouseRow(1L, "地铁两室", "采光好", new BigDecimal("1900"), 75,
                "2室1厅1卫1厨", 2, 1, 1, 1, "whole", 3L, "高新街道", "", 1L,
                "approved", 0, true, LocalDateTime.now(), LocalDateTime.now(),
                "高新街道", "甘井子区", "大连", 1L, "owner", "13800000001", "");
    }
}
