package com.cassin.badge;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.badge.dto.BadgeResponse;
import com.cassin.badge.dto.EarnedBadgeResponse;
import com.cassin.badge.dto.MarkSeenRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Huy hiệu của người đang đăng nhập. Không có endpoint "cấp huy hiệu": chỉ server tự cấp (BadgeEventListener).
 * Bảo vệ bởi SecurityConfig: anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {
  private final BadgeService badgeService;

  @GetMapping
  public List<BadgeResponse> list(@AuthenticationPrincipal AuthUser user) {
    return badgeService.list(user.id());
  }

  @GetMapping("/unseen")
  public List<EarnedBadgeResponse> unseen(@AuthenticationPrincipal AuthUser user) {
    return badgeService.unseen(user.id());
  }

  @PostMapping("/seen")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markSeen(@Valid @RequestBody MarkSeenRequest request, @AuthenticationPrincipal AuthUser user) {
    badgeService.markSeen(user.id(), request.items());
  }
}
