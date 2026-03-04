package com.jimu.http.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.jimu.http.cache.JimuCacheProvider;
import com.jimu.http.cache.MemoryJimuCacheProvider;
import com.jimu.http.cache.RedisJimuCacheProvider;
import com.jimu.http.controller.HttpJimuController;
import com.jimu.http.engine.HttpJimuEngine;
import com.jimu.http.engine.HttpJimuScheduler;
import com.jimu.http.engine.support.HttpJimuTransportSupport;
import com.jimu.http.engine.support.JimuExpressionResolver;
import com.jimu.http.engine.step.AddFixedStepProcessor;
import com.jimu.http.engine.step.EncryptStepProcessor;
import com.jimu.http.engine.step.ScriptStepProcessor;
import com.jimu.http.engine.step.SignStepProcessor;
import com.jimu.http.engine.step.SortStepProcessor;
import com.jimu.http.service.HttpJimuJobLogService;
import com.jimu.http.service.HttpJimuPoolService;
import com.jimu.http.service.HttpJimuScriptMetaService;
import com.jimu.http.service.HttpJimuService;
import com.jimu.http.service.HttpJimuStepService;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

@AutoConfiguration
@Import({
        HttpJimuController.class,
        HttpJimuEngine.class,
        HttpJimuScheduler.class,
        HttpJimuTransportSupport.class,
        JimuExpressionResolver.class,
        SortStepProcessor.class,
        SignStepProcessor.class,
        EncryptStepProcessor.class,
        AddFixedStepProcessor.class,
        ScriptStepProcessor.class,
        HttpJimuService.class,
        HttpJimuPoolService.class,
        HttpJimuStepService.class,
        HttpJimuJobLogService.class,
        HttpJimuScriptMetaService.class
})
@MapperScan("com.jimu.http.mapper")
public class HttpJimuAutoConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataSource dataSource) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = resolveDbType(dataSource);
        if (dbType != null) {
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        } else {
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        }
        return interceptor;
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(JimuCacheProvider.class)
    public JimuCacheProvider redisJimuCacheProvider(StringRedisTemplate redisTemplate) {
        return new RedisJimuCacheProvider(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(JimuCacheProvider.class)
    public JimuCacheProvider memoryJimuCacheProvider() {
        return new MemoryJimuCacheProvider();
    }

    @Bean
    @ConditionalOnClass(JdbcTemplateLockProvider.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(LockProvider.class)
    public LockProvider jdbcShedLockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("jimu_shedlock")
                        .build()
        );
    }

    private DbType resolveDbType(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null) {
                return null;
            }
            String name = product.toLowerCase();
            if (name.contains("sql server")) return DbType.SQL_SERVER;
            if (name.contains("mysql")) return DbType.MYSQL;
            if (name.contains("mariadb")) return DbType.MARIADB;
            if (name.contains("postgresql")) return DbType.POSTGRE_SQL;
            if (name.contains("oracle")) return DbType.ORACLE;
            if (name.contains("h2")) return DbType.H2;
            if (name.contains("sqlite")) return DbType.SQLITE;
            if (name.contains("db2")) return DbType.DB2;
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }
}
