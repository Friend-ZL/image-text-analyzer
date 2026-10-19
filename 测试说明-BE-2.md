# BE-2 测试说明

## 测试概览

本 PR 包含以下测试：

### 单元测试（6个）

1. **TenantPrincipalTest**（8个测试方法）
   - 测试 TenantPrincipal 的不可变性
   - 测试防御性拷贝
   - 测试参数验证

2. **DevTenantContextTest**（9个测试方法）
   - 测试从请求头提取租户信息
   - 测试角色和用户组解析
   - 测试缺失信息的异常处理

3. **JwtTenantContextTest**（10个测试方法）
   - 测试从 JWT claims 提取信息
   - 测试 Keycloak realm_access.roles 兼容性
   - 测试多来源角色合并
   - 测试异常场景

### 集成测试（2个）

4. **SecurityIntegrationTest**（4个测试方法）
   - 测试无 token 返回 401（E40101）
   - 测试缺少 tenant claim 返回 401（E40102）
   - 测试 health 端点无需认证
   - 测试静态资源无需认证
   - 验证响应体格式和 traceId

5. **FlywayMigrationIT**（5个测试方法）
   - 使用 Testcontainers `pgvector/pgvector:pg16`
   - 测试 V1 基线迁移应用
   - 验证 pgvector 扩展启用
   - 验证不包含 chunk 表（预留给 PR #2）

## 运行测试

### 前置条件

- Java 17+
- Maven 3.6+
- Docker（用于 Testcontainers，可选）

### 运行所有单元测试

```bash
mvn test
```

### 运行集成测试

```bash
mvn verify
```

### 运行特定测试

```bash
# 单元测试
mvn test -Dtest=TenantPrincipalTest
mvn test -Dtest=DevTenantContextTest
mvn test -Dtest=JwtTenantContextTest

# 集成测试
mvn test -Dtest=SecurityIntegrationTest
mvn verify -Dit.test=FlywayMigrationIT
```

### 无 Docker 环境说明

如果运行环境中没有 Docker，`FlywayMigrationIT` 会被 Testcontainers 自动跳过。

测试报告会在以下位置：
- 单元测试：`target/surefire-reports/`
- 集成测试：`target/failsafe-reports/`

## 测试覆盖

- ✅ TenantContext 接口实现（DevTenantContext 和 JwtTenantContext）
- ✅ TenantPrincipal 不可变性和防御性拷贝
- ✅ JWT claims 映射和提取逻辑
- ✅ 401/403 响应体格式（统一 ApiResponse + traceId）
- ✅ 新增错误码（E40101、E40102、E40301）
- ✅ Flyway 基线迁移（V1__baseline.sql）
- ✅ pgvector 扩展启用

## 已知限制

- 集成测试需要 Docker 环境运行 Testcontainers
- 开发环境中如未安装 Maven，需先安装或使用 CI 环境
- Security 集成测试使用 spring-security-test 的 `jwt()` mock，不依赖真实 IdP

## CI 环境验证

建议在以下环境验证：
1. GitHub Actions
2. GitLab CI
3. Jenkins

所有测试不依赖外部服务（DASHSCOPE_API_KEY、真实 Keycloak）。
