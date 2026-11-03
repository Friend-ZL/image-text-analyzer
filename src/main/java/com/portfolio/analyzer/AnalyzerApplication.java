package com.portfolio.analyzer;

import com.portfolio.analyzer.security.DevProfileGuardInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AnalyzerApplication.class);
        application.addInitializers(new DevProfileGuardInitializer());
        application.run(args);
    }
}
