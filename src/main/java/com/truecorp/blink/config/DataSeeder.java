package com.truecorp.blink.config;

import com.truecorp.blink.model.User;
import com.truecorp.blink.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class DataSeeder {
    @Bean
    public ApplicationRunner initializeData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("password123"));
                admin.setRoles(Set.of("ROLE_ADMIN"));
                userRepository.save(admin);
                System.out.println("✅ Default admin user created: admin / password123");
            }
        };
    }
}
