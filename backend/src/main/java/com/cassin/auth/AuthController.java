package com.cassin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.dto.RegisterRequest;
import com.cassin.user.dto.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED) // 201: đã tạo mới một tài nguyên
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    // @Valid: chạy các annotation trong RegisterRequest. Sai thì không vào tới đây.
    return authService.register(request);
  }
}

