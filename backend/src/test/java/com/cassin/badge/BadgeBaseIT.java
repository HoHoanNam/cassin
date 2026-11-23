package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.cassin.badge.event.BadgeTrigger;
import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Phase 0: kiểm tra phần chung của module huy hiệu bằng 2 rule GIẢ (chưa có rule thật nào).
 *
 *   - V18 chạy được, Hibernate validate khớp entity
 *   - nhảy nhiều cấp, không cấp trùng, unseen chỉ trả cấp cao nhất, seen đánh dấu mọi cấp <= N
 *   - chỉ huy hiệu có rule mới hiện, bí mật bị che tới khi đạt
 *
 * @Import thêm rule giả → context riêng (khác các IT khác). Chấp nhận: rule giả dùng mã THẬT (DILIGENT, NIGHT_OWL),
 * để chung context thì Phase 1 thêm rule DILIGENT thật sẽ trùng mã.
 * Sự kiện chạy đồng bộ nhờ cassin.badge.async=false trong application-it.yml.
 */
@IntegrationTest
@Import(BadgeBaseIT.FakeRules.class)
class BadgeBaseIT {

  /** Sự kiện giả, đóng vai ReviewAnsweredEvent... */
  record FakeEvent(Long userId) implements BadgeTrigger {
  }

  /** Tiến độ giả theo (mã, userId): test tự đặt rồi phát sự kiện. */
  static final Map<String, Long> PROGRESS = new ConcurrentHashMap<>();

  static BadgeRule fakeRule(String code) {
    return new BadgeRule() {
      @Override
      public String code() {
        return code;
      }

      @Override
      public Set<Class<? extends BadgeTrigger>> triggers() {
        return Set.of(FakeEvent.class);
      }

      @Override
      public long progress(Long userId) {
        return PROGRESS.getOrDefault(code + ":" + userId, 0L);
      }
    };
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeRules {
    @Bean
    BadgeRule fakeDiligent() {
      return fakeRule("DILIGENT"); // 30 / 300 / 1500
    }

    @Bean
    BadgeRule fakeNightOwl() {
      return fakeRule("NIGHT_OWL"); // bí mật, 1 cấp
    }
  }

  @Autowired
  MockMvc mvc;
  @Autowired
  ApplicationEventPublisher publisher;
  @Autowired
  UserBadgeRepository userBadgeRepository;

  ApiSession me;
  Long myId;

  @BeforeEach
  void setUp() throws Exception {
    me = new ApiClient(mvc).newLearner();
    String body = mvc.perform(get("/api/me").header(AUTHORIZATION, me.bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    myId = ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private void progress(String code, long value) {
    PROGRESS.put(code + ":" + myId, value);
    publisher.publishEvent(new FakeEvent(myId));
  }

  @Test
  @DisplayName("Chỉ huy hiệu đã có rule mới hiện, theo sort_order; chưa đạt thì level 0")
  void listsOnlyEnabledBadges() throws Exception {
    mvc.perform(get("/api/badges").header(AUTHORIZATION, me.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", contains("DILIGENT", "NIGHT_OWL")))
        .andExpect(jsonPath("$[0].level").value(0))
        .andExpect(jsonPath("$[0].tiers[*].target", contains(30, 300, 1500)));
  }

  @Test
  @DisplayName("Nhảy 0 → 350: cấp đủ I và II; unseen chỉ trả cấp II")
  void jumpsSeveralLevels() throws Exception {
    progress("DILIGENT", 350);

    assertThat(userBadgeRepository.findByIdUserId(myId)).extracting(UserBadge::level).containsExactlyInAnyOrder(1, 2);
    mvc.perform(get("/api/badges").header(AUTHORIZATION, me.bearer()))
        .andExpect(jsonPath("$[0].level").value(2))
        .andExpect(jsonPath("$[0].progress").value(350));
    mvc.perform(get("/api/badges/unseen").header(AUTHORIZATION, me.bearer()))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].code").value("DILIGENT"))
        .andExpect(jsonPath("$[0].level").value(2))
        .andExpect(jsonPath("$[0].target").value(300))
        .andExpect(jsonPath("$[0].maxLevel").value(3));
  }

  @Test
  @DisplayName("Sự kiện lặp lại không cấp trùng")
  void idempotent() {
    progress("DILIGENT", 40);
    progress("DILIGENT", 40);
    progress("DILIGENT", 40);

    assertThat(userBadgeRepository.findByIdUserId(myId)).hasSize(1);
  }

  @Test
  @DisplayName("Xem cấp II = đã xem cả cấp I; gọi lại vẫn 204")
  void markSeenCoversLowerLevels() throws Exception {
    progress("DILIGENT", 350);
    String body = """
        {"items": [{"code": "DILIGENT", "level": 2}]}
        """;
    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/api/badges/seen").header(AUTHORIZATION, me.bearer()).contentType(APPLICATION_JSON).content(body))
          .andExpect(status().isNoContent());
    }

    assertThat(userBadgeRepository.findByIdUserId(myId)).allSatisfy(ub -> assertThat(ub.getSeenAt()).isNotNull());
    mvc.perform(get("/api/badges/unseen").header(AUTHORIZATION, me.bearer())).andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("Bí mật bị che tới khi đạt")
  void secretIsMaskedUntilEarned() throws Exception {
    mvc.perform(get("/api/badges").header(AUTHORIZATION, me.bearer()))
        .andExpect(jsonPath("$[1].name").value(BadgeService.SECRET_NAME))
        .andExpect(jsonPath("$[1].progress").value(0));

    progress("NIGHT_OWL", 1);

    mvc.perform(get("/api/badges").header(AUTHORIZATION, me.bearer()))
        .andExpect(jsonPath("$[1].name").value("Cú đêm"))
        .andExpect(jsonPath("$[1].level").value(1));
  }

  @Test
  @DisplayName("Huy hiệu của người này không lẫn sang người khác")
  void isolatedPerUser() throws Exception {
    progress("DILIGENT", 350);
    ApiSession other = new ApiClient(mvc).newLearner();

    mvc.perform(get("/api/badges/unseen").header(AUTHORIZATION, other.bearer())).andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("Body sai → 400; chưa đăng nhập → 401")
  void validationAndAuth() throws Exception {
    mvc.perform(post("/api/badges/seen").header(AUTHORIZATION, me.bearer()).contentType(APPLICATION_JSON)
        .content("""
            {"items": [{"code": "DILIGENT", "level": 5}]}
            """))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/badges")).andExpect(status().isUnauthorized());
  }
}
