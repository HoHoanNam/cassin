package com.cassin.passage;

import com.cassin.passage.dto.AttemptResponse;

/**
 * Kết quả của "Làm bài": đề của lượt làm + lượt đó vừa được TẠO hay là lượt cũ đang làm dở.
 * Controller dựa vào created để trả 201 hay 200. Không phải JSON nên không nằm trong dto/.
 */
public record StartedAttempt(AttemptResponse attempt, boolean created) {
}
