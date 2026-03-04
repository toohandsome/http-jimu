package com.jimu.http;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.jimu.http.config.HttpJimuAutoConfiguration;
import com.jimu.http.service.HttpJimuJobLogService;
import com.jimu.http.service.HttpJimuPoolService;
import com.jimu.http.service.HttpJimuStepService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpJimuAutoConfigurationTest {

    @Test
    void shouldCreateMybatisInterceptor() throws Exception {
        HttpJimuAutoConfiguration conf = new HttpJimuAutoConfiguration();
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        MybatisPlusInterceptor interceptor = conf.mybatisPlusInterceptor(dataSource);
        assertNotNull(interceptor);
    }

    @Test
    void shouldInstantiateSimpleServices() {
        assertNotNull(new HttpJimuJobLogService());
        assertNotNull(new HttpJimuPoolService());
        assertNotNull(new HttpJimuStepService());
    }
}
