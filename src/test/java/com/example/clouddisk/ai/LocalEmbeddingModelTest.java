package com.example.clouddisk.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalEmbeddingModelTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "AI_MODEL_URI", matches = ".+")
    void multilingualOnnxModelProducesExpectedVector() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(System.getenv("AI_MODEL_URI"));
        model.setTokenizerResource(System.getenv("AI_TOKENIZER_URI"));
        model.setTokenizerOptions(Map.of("padding", "true", "truncation", "true", "maxLength", "128"));
        model.afterPropertiesSet();
        for (int i = 0; i < 5; i++)
            assertEquals(384, model.embed("这是一份中文私人文档 " + i).length);
        assertEquals(384, model.embed("苹果采摘").length);
        assertEquals(384, model.embed("苹果什么时候采摘？").length);
        assertEquals(384, java.util.concurrent.CompletableFuture.supplyAsync(() -> model.embed("苹果采摘")).join().length);
    }
}
