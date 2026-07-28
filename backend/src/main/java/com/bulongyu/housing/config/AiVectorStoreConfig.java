package com.bulongyu.housing.config;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 向量库的可选依赖配置。
 */
@Configuration
public class AiVectorStoreConfig {

    /**
     * 将向量库 Bean 标记为懒初始化，避免 Chroma 暂时不可用时阻止主应用启动。
     *
     * @return 向量库 Bean 定义调整器
     */
    @Bean
    public static BeanFactoryPostProcessor optionalVectorStoreLazyInitializer() {
        return beanFactory -> {
            String[] names = beanFactory.getBeanNamesForType(VectorStore.class, true, false);
            for (String name : names) {
                if (beanFactory.containsBeanDefinition(name)) {
                    beanFactory.getBeanDefinition(name).setLazyInit(true);
                }
            }
        };
    }
}
