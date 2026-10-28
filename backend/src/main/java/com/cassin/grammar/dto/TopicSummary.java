package com.cassin.grammar.dto;

import com.cassin.grammar.CefrLevel;

public record TopicSummary(Long id, String title, CefrLevel level, long questionCount) {}
