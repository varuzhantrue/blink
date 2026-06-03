package com.truecorp.blink.service;

import com.truecorp.blink.dto.SignupRequest;
import com.truecorp.blink.exception.UsernameAlreadyExistsException;
import com.truecorp.blink.model.User;
import com.truecorp.blink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void register_ShouldEncodePasswordAndSaveUser_WhenUsernameIsAvailable() {
        SignupRequest request = new SignupRequest();
        request.setUsername("alice");
        request.setPassword("secret123");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");

        userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("alice", saved.getUsername());
        assertEquals("hashed", saved.getPassword());
        assertTrue(saved.getRoles().contains("USER"));
    }

    @Test
    void register_ShouldThrowUsernameAlreadyExistsException_WhenUsernameIsTaken() {
        SignupRequest request = new SignupRequest();
        request.setUsername("alice");
        request.setPassword("secret123");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));

        UsernameAlreadyExistsException ex = assertThrows(
                UsernameAlreadyExistsException.class,
                () -> userService.register(request)
        );

        assertTrue(ex.getMessage().contains("alice"));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
