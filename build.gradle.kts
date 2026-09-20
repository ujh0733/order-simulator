plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "dev.junghun"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

springBoot {
	mainClass.set("dev.junghun.ordersimulator.OrderSimulatorApplicationKt")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

fun loadEnvFile(): Map<String, String> {
	val envFile = file(".env")
	if (!envFile.exists()) return emptyMap()
	return envFile.readLines()
		.map { it.trim().removeSuffix("\r") }
		.filter { it.isNotBlank() && !it.startsWith("#") }
		.mapNotNull { line ->
			val idx = line.indexOf('=')
			if (idx < 0) return@mapNotNull null
			val key = line.substring(0, idx).trim()
			var value = line.substring(idx + 1).trim()
			if (value.length >= 2 && (value.first() == '"' && value.last() == '"' || value.first() == '\'' && value.last() == '\'')) {
				value = value.substring(1, value.length - 1)
			}
			key to value
		}
		.toMap()
}

tasks.register<JavaExec>("collectSeoulRestaurants") {
	group = "data"
	description = "서울시 25개 자치구의 일반음식점 인허가 정보를 공공데이터포털 API에서 수집합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("dev.junghun.ordersimulator.datacollector.SeoulRestaurantDataCollector")
	environment(loadEnvFile())
}

tasks.register<JavaExec>("seedDummyUsers") {
	group = "data"
	description = "더미 고객/주소 데이터를 대량 생성합니다(기본 50만 명)."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("dev.junghun.ordersimulator.seed.DummyUserSeederKt")
}

tasks.register<JavaExec>("seedOrderHistory") {
	group = "data"
	description = "주문 내역 조회용 이력 데이터를 대량 생성합니다(기본 100만 건, 전부 COMPLETED)."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("dev.junghun.ordersimulator.seed.OrderHistorySeederKt")
}
