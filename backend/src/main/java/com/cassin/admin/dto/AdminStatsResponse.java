package com.cassin.admin.dto;

public record AdminStatsResponse(
		long users,
		long systemDecks,
		long systemCards,
		long topics,
		long questions,
		long quizAttempts,
		long learnersToday) {
}
