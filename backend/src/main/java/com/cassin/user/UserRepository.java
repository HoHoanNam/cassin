package com.cassin.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// Interface để tương tác với bảng users trong DB
// Sinh ra các lệnh SQL thông qua tên method (Method naming conventions)
public interface UserRepository extends JpaRepository<User, Long> {
  // Dùng Spring Data JPA tự tạo câu truy vấn:
  // SELECT EXISTS(SELECT 1 FROM users WHERE email = ? LIMIT 1)
  boolean existsByEmail(String email);

  // Tự động sinh SELECT * FROM users WHERE email = ?
  Optional<User> findByEmail(String email);
}
