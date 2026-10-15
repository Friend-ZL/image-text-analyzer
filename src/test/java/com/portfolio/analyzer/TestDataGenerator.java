package com.portfolio.analyzer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 用于生成测试数据的工具类
 */
public class TestDataGenerator {
    
    public static void main(String[] args) throws IOException {
        String resourcePath = "src/test/resources/test-data/";
        new File(resourcePath).mkdirs();
        
        // 生成测试图片
        createTestImage(resourcePath + "test.jpg");
        createTestImage(resourcePath + "test.png");
        
        // 生成测试PDF
        createTestPdf(resourcePath + "test.pdf");
        
        System.out.println("Test data generated successfully!");
    }
    
    private static void createTestImage(String path) throws IOException {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // 绘制渐变背景
        GradientPaint gradient = new GradientPaint(
            0, 0, new Color(102, 126, 234),
            200, 200, new Color(118, 75, 162)
        );
        g.setPaint(gradient);
        g.fillRect(0, 0, 200, 200);
        
        // 绘制文本
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("TEST", 60, 110);
        
        g.dispose();
        
        String format = path.endsWith(".png") ? "PNG" : "JPEG";
        ImageIO.write(image, format, new File(path));
        System.out.println("Created: " + path);
    }
    
    private static void createTestPdf(String path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // 第一页
            PDPage page1 = new PDPage();
            document.addPage(page1);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page1)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Test PDF Document");
                contentStream.endText();
                
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 650);
                contentStream.showText("This is a test PDF file for unit testing.");
                contentStream.endText();
            }
            
            // 第二页
            PDPage page2 = new PDPage();
            document.addPage(page2);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page2)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Page 2 of the test document.");
                contentStream.endText();
            }
            
            document.save(path);
            System.out.println("Created: " + path);
        }
    }
}
