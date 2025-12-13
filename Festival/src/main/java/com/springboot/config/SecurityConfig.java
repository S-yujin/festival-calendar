package com.springboot.config;

import com.springboot.security.MemberDetailsService;
import com.springboot.domain.Member;
import com.springboot.repository.MemberRepository;

import jakarta.servlet.http.HttpSession;

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
    private final MemberRepository memberRepository;

    public SecurityConfig(MemberDetailsService memberDetailsService, 
                         MemberRepository memberRepository) {
        this.memberDetailsService = memberDetailsService;
        this.memberRepository = memberRepository;
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**")
                )
                .userDetailsService(memberDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/css/**", "/js/**", "/files/**", "/images/**").permitAll()
                        .requestMatchers("/festivals", "/festivals/**").permitAll()
                        .requestMatchers("/api/**").authenticated()  // API는 인증 필요
                        .requestMatchers("/members/**").authenticated()
                        .anyRequest().permitAll()
                )       
                
                // 폼 로그인 설정
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            // 로그인 성공 시 세션에 member 저장
                            String email = authentication.getName();
                            Member member = memberRepository.findByEmail(email)
                                    .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
                            
                            HttpSession session = request.getSession();
                            session.setAttribute("member", member);
                            
                            response.sendRedirect("/festivals");
                        })
                        .failureUrl("/auth/login?error")
                        .permitAll()
                )
                
                // 로그아웃 설정
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/festivals")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }
}