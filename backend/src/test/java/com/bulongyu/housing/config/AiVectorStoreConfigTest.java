package com.bulongyu.housing.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiVectorStoreConfigTest {

    @Test
    void delaysVectorStoreCreationUntilItIsActuallyRequested() {
        AtomicBoolean created = new AtomicBoolean(false);
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.register(AiVectorStoreConfig.class);
            context.registerBean("vectorStore", VectorStore.class, () -> {
                created.set(true);
                throw new IllegalStateException("Chroma unavailable");
            });

            context.refresh();

            assertThat(created).isFalse();
            assertThatThrownBy(() -> context.getBean("vectorStore"))
                    .hasRootCauseMessage("Chroma unavailable");
            assertThat(created).isTrue();
        }
    }
}
