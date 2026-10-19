# ============================================
# Stage 1: 构建阶段（Maven + JDK 17）
# ============================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# 先拷贝 pom.xml 并预下载依赖（利用 Docker 层缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 拷贝源码并构建
COPY src ./src
RUN mvn package -DskipTests -B

# ============================================
# Stage 2: 分层提取阶段
# ============================================
FROM eclipse-temurin:17-jdk-alpine AS layers

WORKDIR /extract

# 拷贝构建产物并提取分层
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ============================================
# Stage 3: 运行阶段（JRE 17 精简镜像）
# ============================================
FROM eclipse-temurin:17-jre-alpine

# 创建非 root 用户
RUN addgroup -S spring -g 1000 && \
    adduser -S spring -u 1000 -G spring

WORKDIR /app

# 按顺序拷贝各层（利用层缓存，dependencies 变化最少）
COPY --from=layers --chown=spring:spring /extract/dependencies/ ./
COPY --from=layers --chown=spring:spring /extract/spring-boot-loader/ ./
COPY --from=layers --chown=spring:spring /extract/snapshot-dependencies/ ./
COPY --from=layers --chown=spring:spring /extract/application/ ./

# 暴露端口
EXPOSE 8080

# 切换到非 root 用户
USER spring:spring

# JVM 容器参数优化
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
