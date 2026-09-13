package com.knowledgegraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 课程/书籍知识图谱系统 — Java 后端入口（阶段 A 骨架）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowledgeGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeGraphApplication.class, args);
    }
}
