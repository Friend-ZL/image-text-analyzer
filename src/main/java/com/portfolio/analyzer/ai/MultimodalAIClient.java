package com.portfolio.analyzer.ai;

import com.portfolio.analyzer.exception.AIServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

@Component
public class MultimodalAIClient {
    
    private static final Logger logger = LoggerFactory.getLogger(MultimodalAIClient.class);
    
    private final ChatClient chatClient;
    
    @Value("${spring.ai.openai.chat.options.model:qwen-vl-max}")
    private String modelName;
    
    public MultimodalAIClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }
    
    /**
     * 使用视觉语言模型分析图片并回答问题
     * 
     * @param images 图片字节数组列表
     * @param question 用户问题
     * @return AI的回答
     */
    public String analyzeImages(List<byte[]> images, String question) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("图片列表不能为空");
        }
        
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        
        logger.info("Analyzing {} image(s) with question: {}", images.size(), question);
        
        try {
            // 将图片转换为Media对象数组
            Media[] mediaArray = images.stream()
                .map(img -> new Media(MimeTypeUtils.IMAGE_PNG, img))
                .toArray(Media[]::new);
            
            logger.debug("Prepared {} media objects for AI analysis", mediaArray.length);
            
            // 调用AI模型
            String response = chatClient.prompt()
                .user(u -> u.text(question).media(mediaArray))
                .call()
                .content();
            
            logger.info("Received response from AI model (length: {})", 
                response != null ? response.length() : 0);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to analyze images with AI model", e);
            throw new AIServiceException("AI模型调用失败: " + e.getMessage(), e);
        }
    }
    
    public String getModelName() {
        return modelName;
    }
}
