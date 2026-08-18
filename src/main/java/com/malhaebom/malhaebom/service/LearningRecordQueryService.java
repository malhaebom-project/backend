package com.malhaebom.malhaebom.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.LearningHistory;
import com.malhaebom.malhaebom.service.dto.LearningHistoryItem;
import com.malhaebom.malhaebom.service.dto.LearningHistoryProjection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningRecordQueryService {

	private static final int MAX_PAGE_SIZE = 50;
	private static final LocalDateTime DEFAULT_START_AT =
		LocalDate.of(1970, 1, 1).atStartOfDay();
	private static final LocalDateTime DEFAULT_END_AT =
		LocalDate.of(9999, 1, 1).atStartOfDay();

	private final ChildProfileService childProfileService;
	private final LearningSessionRepository learningSessionRepository;

	@Transactional(readOnly = true)
	public LearningHistory getHistory(
		Long userId,
		Long childId,
		int page,
		int size,
		LocalDate startDate,
		LocalDate endDate
	) {
		validateRequest(page, size, startDate, endDate);
		childProfileService.getOwnedActive(userId, childId);

		LocalDateTime startAt = startDate == null
			? DEFAULT_START_AT
			: startDate.atStartOfDay();
		LocalDateTime endAt = endDate == null
			? DEFAULT_END_AT
			: endDate.plusDays(1).atStartOfDay();
		Page<LearningHistoryProjection> history =
			learningSessionRepository.findLearningHistory(
				childId,
				LearningSessionStatus.COMPLETED,
				startAt,
				endAt,
				PageRequest.of(page, size)
			);

		return new LearningHistory(
			history.getContent().stream()
				.map(this::toHistoryItem)
				.toList(),
			history.getNumber(),
			history.getSize(),
			history.getTotalElements(),
			history.getTotalPages()
		);
	}

	private LearningHistoryItem toHistoryItem(
		LearningHistoryProjection projection
	) {
		int questionCount = Math.toIntExact(projection.getQuestionCount());
		int correctCount = Math.toIntExact(projection.getCorrectCount());

		return new LearningHistoryItem(
			projection.getSessionId(),
			projection.getTopic().getName(),
			projection.getDifficulty(),
			questionCount,
			correctCount,
			calculateCorrectRate(correctCount, questionCount),
			calculateStudySeconds(
				projection.getStartedAt(),
				projection.getCompletedAt()
			),
			projection.getCompletedAt()
		);
	}

	private double calculateCorrectRate(int correctCount, int questionCount) {
		if (questionCount == 0) {
			return 0.0;
		}
		return Math.round(correctCount * 1000.0 / questionCount) / 10.0;
	}

	private long calculateStudySeconds(
		LocalDateTime startedAt,
		LocalDateTime completedAt
	) {
		if (startedAt == null || completedAt == null) {
			return 0L;
		}
		return Math.max(0L, Duration.between(startedAt, completedAt).getSeconds());
	}

	private void validateRequest(
		int page,
		int size,
		LocalDate startDate,
		LocalDate endDate
	) {
		if (page < 0) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"페이지 번호는 0 이상이어야 합니다."
			);
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"페이지 크기는 1 이상 50 이하여야 합니다."
			);
		}
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"시작일은 종료일보다 늦을 수 없습니다."
			);
		}
	}
}
