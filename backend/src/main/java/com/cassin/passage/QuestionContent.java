package com.cassin.passage;

import java.util.List;

/** Phần chung của mọi dạng câu hỏi, gom lại để factory của 3 lớp con không phải nhận 4 tham số rời. */
public record QuestionContent(String prompt, String evidence, String explanation, List<HintInput> hints) {
}
