package com.springboot.config;

import com.springboot.security.MemberDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final MemberDetailsService memberDetailsService;

    public SecurityConfig(MemberDetailsService memberDetailsService) {
        this.memberDetailsService = memberDetailsService;
    }
    
    // 비밀번호 암호화
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationManager 빈 (나중에 필요할 수도 있어서 같이 등록)
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
        		// URL 접근권한
                .csrf(csrf -> csrf.disable()) // 처음엔 편하게 비활성화 (나중에 필요하면 다시 켜도 됨)
                .userDetailsService(memberDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/signup", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/members/**").authenticated()
                        .anyRequest().permitAll()
                )       
                
                // 폼 로그인 설정
                .formLogin(form -> form
                        .loginPage("/auth/login")     // 로그인 페이지 URL
                        .loginProcessingUrl("/auth/login")  // 로그인 POST 처리 URL
                        .usernameParameter("email")    // 폼에서 쓰는 name
                        .passwordParameter("password")
                        .defaultSuccessUrl("/festivals", true)
                        .permitAll()
                )
                
                // 로그아웃 설정
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/festivals")
                        .permitAll()
                );

        return http.build();
    }
}
