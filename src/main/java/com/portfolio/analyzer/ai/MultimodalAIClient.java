package com.portfolio.analyzer.ai;

import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.exception.AIServiceException;
import com.portfolio.analyzer.exception.BusinessException;
import com.portfolio.analyzer.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

/**
 * 多模态AI客户端
 * 
 * <p>支持VLM（视觉语言模型）和纯文本模型。
 * 
 * @since Stage 1
 */
@Component
public class MultimodalAIClient {
    
    private static final Logger logger = LoggerFactory.getLogger(MultimodalAIClient.class);
    
    private final ChatClient vlmChatClient;
    private final ChatClient textChatClient;
    
    @Value("${spring.ai.openai.chat.options.model:qwen-vl-max}")
    private String vlmModelName;
    
    @Value("${app.rag.chat-model:qwen-plus}")
    private String textModelName;
    
    public MultimodalAIClient(
            ChatClient.Builder chatClientBuilder,
            OpenAiApi openAiApi,
            @Value("${app.rag.chat-model:qwen-plus}") String textModel) {
        
        this.vlmChatClient = chatClientBuilder.build();
        
        OpenAiChatModel textChatModel = new OpenAiChatModel(openAiApi,
                OpenAiChatOptions.builder()
                        .withModel(textModel)
                        .withTemperature(0.7)
                        .build());
        this.textChatClient = ChatClient.builder(textChatModel).build();
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
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片列表不能为空");
        }
        
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.QUESTION_EMPTY);
        }
        
        logger.info("Analyzing {} image(s) with question: {}", images.size(), LogSanitizer.sanitizeQuestion(question));
        
        try {
            Media[] mediaArray = images.stream()
                .map(img -> new Media(MimeTypeUtils.IMAGE_PNG, img))
                .toArray(Media[]::new);
            
            logger.debug("Prepared {} media objects for AI analysis", mediaArray.length);
            
            String response = vlmChatClient.prompt()
                .user(u -> u.text(question).media(mediaArray))
                .call()
                .content();
            
            logger.info("Received response from VLM (length: {})", 
                response != null ? response.length() : 0);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to analyze images with AI model: {}", e.getMessage(), e);
            // 不透传上游错误信息，只返回固定错误码
            throw new AIServiceException("模型服务调用失败", e);
        }
    }
    
    /**
     * 纯文本聊天（用于RAG场景）
     * 
     * @param prompt 完整的提示词（包含检索到的上下文）
     * @return AI的回答
     */
    public String chat(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        
        logger.info("Sending chat request to text model (prompt length: {})", prompt.length());
        
        try {
            String response = textChatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            logger.info("Received chat response from text model (length: {})", 
                response != null ? response.length() : 0);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to chat with text model: {}", e.getMessage(), e);
            // 不透传上游错误信息
            throw new AIServiceException("文本模型调用失败", e);
        }
    }
    
    public String getModelName() {
        return vlmModelName;
    }
    
    public String getTextModelName() {
        return textModelName;
    }
}
