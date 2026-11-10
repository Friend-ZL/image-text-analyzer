package com.portfolio.analyzer.security;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.TransactionExecutionListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 把应用数据源和 Flyway owner 拆开之后的启动约束，以及每个事务开始时设置租户。
 */
@Configuration
public class TenantIsolationConfiguration {

    @Bean
    public static BeanPostProcessor tenantGuardingDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof HikariDataSource hikari && !(bean instanceof TenantGuardingDataSource)) {
                    return new TenantGuardingDataSource(hikari);
                }
                return bean;
            }
        };
    }

    @Bean
    public static BeanPostProcessor tenantTransactionListenerPostProcessor() {
        return new TenantTransactionListenerPostProcessor();
    }

    @Bean
    @Profile("!dev & !local")
    public DataSourceRoleGuard dataSourceRoleGuard(Environment environment) {
        return new DataSourceRoleGuard(environment);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FlywayConfigurationCustomizer appRwPasswordGuard(Environment environment) {
        AppRwPasswordRules.check(environment.getProperty("DATABASE_APP_RW_PASSWORD"));
        return configuration -> {
        };
    }

    /**
     * 迁移创建 {@code app_rw} 之后再确认应用账号能连上。
     * 池在角色出现之前不能因为登录失败而中断启动，所以这里单独失败。
     */
    @Bean
    @DependsOn("flywayInitializer")
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner appRwConnectionCheck(DataSource dataSource) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(2)) {
                    throw new IllegalStateException(
                            "Application datasource (app_rw) connection is not valid after Flyway");
                }
            } catch (SQLException ex) {
                throw new IllegalStateException(
                        "Application datasource (app_rw) cannot connect after Flyway", ex);
            }
        };
    }

    /**
     * 非 dev/local 启动时核对应用用户不是 Flyway owner。
     */
    public static final class DataSourceRoleGuard {
        public DataSourceRoleGuard(Environment environment) {
            DataSourceRoleRules.check(
                    environment.getProperty("spring.datasource.username"),
                    environment.getProperty("spring.flyway.user"),
                    environment.getProperty("spring.datasource.url"));
        }
    }

    /**
     * 尽早注册，避免在事务管理器创建之后才装上监听器。
     * 监听器里再取 {@link TenantSession}，免得本处理器依赖业务 Bean。
     */
    static final class TenantTransactionListenerPostProcessor implements BeanPostProcessor, BeanFactoryAware {

        private BeanFactory beanFactory;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof ConfigurableTransactionManager manager) {
                manager.addListener(new TransactionExecutionListener() {
                    @Override
                    public void afterBegin(org.springframework.transaction.TransactionExecution transaction,
                                           Throwable beginFailure) {
                        if (beginFailure != null) {
                            return;
                        }
                        beanFactory.getBean(TenantSession.class).onTransactionBegin();
                    }
                });
            }
            return bean;
        }
    }
}
