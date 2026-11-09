package com.example.nutriuniv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class NutriUnivApplication {

	// 서버 시간대를 한국으로 고정한다. 컨테이너·RDS 기본값은 UTC 라서 LocalDateTime.now(), @Scheduled cron,
	// "오늘" 집계가 9시간 어긋났다. PostgreSQL 드라이버도 접속할 때 이 값을 세션 TimeZone 으로 보낸다.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(NutriUnivApplication.class, args);
	}

}
