package dev.junghun.ordersimulator.security

import dev.junghun.ordersimulator.admin.AdminRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    private val adminRepository: AdminRepository,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // admin 테이블에서 직접 조회한다. 별도 회원가입 없이 Flyway 시드로만 계정을 관리한다.
    @Bean
    fun userDetailsService(): UserDetailsService = UserDetailsService { username ->
        val admin = adminRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("존재하지 않는 관리자입니다: $username")
        User.withUsername(admin.username)
            .password(admin.password)
            .roles("ADMIN")
            .build()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/login", permitAll)
                authorize("/css/**", permitAll)
                authorize("/js/**", permitAll)
                authorize("/data/**", permitAll)
                // 그라파나가 인증 없이 스크레이핑할 수 있어야 한다.
                authorize("/actuator/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            formLogin {
                loginPage = "/login"
                defaultSuccessUrl("/admin", true)
                permitAll = true
            }
            logout {
                permitAll = true
            }
        }
        return http.build()
    }
}
