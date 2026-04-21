package com.zhaoyichi.devplatformbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后端应用启动入口。
 *
 * <p>项目面向“静态前端 + Spring Boot API + MySQL”的一体化联调场景：
 * 本地启动后，数据库表/字段/索引由 {@code DatabaseSchemaInitializer} 做兼容性补齐；
 * 请求链路通过 {@code RequestTraceFilter} 生成 traceId 便于排错。</p>
 */
@SpringBootApplication
@MapperScan("com.zhaoyichi.devplatformbackend.mapper")
@EnableScheduling
@EnableAsync
public class DevPlatformBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevPlatformBackendApplication.class, args);
	}
}
