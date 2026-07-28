package com.bulongyu.housing.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeRagServiceTest {
    private ObjectProvider<VectorStore> provider;
    private VectorStore vectorStore;
    private AiModelGateway modelGateway;
    private KnowledgeRagService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        provider = mock(ObjectProvider.class);
        vectorStore = mock(VectorStore.class);
        modelGateway = mock(AiModelGateway.class);
        service = new KnowledgeRagService(provider, modelGateway);
    }

    @Test
    void searchesFaqSourcesWithoutCallingModelAndLimitsSnippetLength() {
        String longContent = "租房知识".repeat(600);
        Document document = Document.builder()
                .text(longContent)
                .metadata("type", "faq")
                .metadata("source_id", "faq-1")
                .metadata("question", "押金退还")
                .metadata("category", "contract")
                .score(0.9)
                .build();
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document));

        KnowledgeRagService.SearchResult result = service.search("押金什么时候退");

        assertThat(result.snippets()).singleElement().satisfies(snippet -> {
            assertThat(snippet.sourceId()).isEqualTo("faq-1");
            assertThat(snippet.content()).hasSize(2000);
        });
        assertThat(result.sources()).hasSize(1);
        verifyNoInteractions(modelGateway);
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFilterExpression().toString())
                .contains("type")
                .contains("faq");
    }

    @Test
    void reportsUnavailableKnowledgeStoreWithoutInventingSources() {
        when(provider.getIfAvailable()).thenReturn(null);

        KnowledgeRagService.SearchResult result = service.search("合同问题");

        assertThat(result.message()).contains("未启用");
        assertThat(result.snippets()).isEmpty();
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(modelGateway, vectorStore);
    }

    @Test
    void reportsTemporarilyUnavailableWhenVectorStoreCreationFails() {
        when(provider.getIfAvailable()).thenThrow(new IllegalStateException("Chroma unavailable"));

        KnowledgeRagService.SearchResult result = service.search("合同问题");

        assertThat(result.message()).contains("暂时不可用");
        assertThat(result.snippets()).isEmpty();
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(modelGateway, vectorStore);
    }}
