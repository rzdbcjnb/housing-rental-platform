package com.bulongyu.housing.service.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticRetrieverTest {
    @SuppressWarnings("unchecked")
    @Test
    void filtersHouseDocumentsDuringVectorSearch() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        SemanticRetriever retriever = new SemanticRetriever(provider, 0.58);

        SemanticRetriever.Retrieval result = retriever.retrieveHouseIds("大连两室房", 20);

        assertThat(result.status()).isEqualTo(SemanticRetriever.RetrievalStatus.SUCCESS_EMPTY);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        SearchRequest request = requestCaptor.getValue();
        assertThat(request.getTopK()).isEqualTo(80);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.58);
        assertThat(request.hasFilterExpression()).isTrue();
        assertThat(request.getFilterExpression().toString())
                .contains("type")
                .contains("house");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fallsBackWhenVectorStoreCreationFails() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenThrow(new IllegalStateException("Chroma unavailable"));
        SemanticRetriever retriever = new SemanticRetriever(provider, 0.58);

        SemanticRetriever.Retrieval result = retriever.retrieveHouseIds("大连两室房", 20);

        assertThat(result.status()).isEqualTo(SemanticRetriever.RetrievalStatus.UNAVAILABLE);
        assertThat(result.ids()).isEmpty();
        assertThat(result.scores()).isEmpty();
    }}
