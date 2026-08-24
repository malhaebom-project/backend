package com.malhaebom.malhaebom.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageUrlResolver;
import com.malhaebom.malhaebom.service.dto.ChildStatistics;
import com.malhaebom.malhaebom.service.dto.ChildStatisticsProjection;
import com.malhaebom.malhaebom.service.dto.LearningHistory;
import com.malhaebom.malhaebom.service.dto.LearningHistoryItem;
import com.malhaebom.malhaebom.service.dto.LearningHistoryProjection;
import com.malhaebom.malhaebom.service.dto.LearningSessionPeriodProjection;
import com.malhaebom.malhaebom.service.dto.LearningStatistics;
import com.malhaebom.malhaebom.service.dto.TopicStatistics;
import com.malhaebom.malhaebom.service.dto.TopicStatisticsProjection;
import com.malhaebom.malhaebom.service.dto.WrongAnswer;
import com.malhaebom.malhaebom.service.dto.WrongAnswerProjection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningRecordQueryService {

	private static final int RECENT_WRONG_ANSWER_LIMIT = 10;
	private static final List<AnswerResult> WRONG_ANSWER_RESULTS = List.of(
		AnswerResult.PARTIALLY_CORRECT,
		AnswerResult.INCORRECT,
		AnswerResult.UNRECOGNIZED
	);
	private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Seoul");
	private static final ZoneId STORAGE_ZONE = ZoneOffset.UTC;
	private final ChildProfileService childProfileService;
	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;
	private final QuestionImageUrlResolver questionImageUrlResolver;
	private final Clock clock;

	@Transactional(readOnly = true)
	public LearningHistory getHistory(
		Long userId,
		Long childId,
		int page,
		int size,
		LocalDateTime startAt,
		LocalDateTime endAt
	) {
		childProfileService.getOwnedActive(userId, childId);

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

	@Transactional(readOnly = true)
	public LearningStatistics getStatistics(Long userId, Long childId) {
		childProfileService.getOwnedActive(userId, childId);

		ChildStatistics overallStatistics = learningSessionRepository
			.findChildStatistics(List.of(childId))
			.stream()
			.findFirst()
			.map(this::toChildStatistics)
			.orElseGet(ChildStatistics::empty);
		List<LearningSessionPeriodProjection> periods =
			learningSessionRepository.findLearningSessionPeriods(
				childId,
				LearningSessionStatus.COMPLETED
			);
		List<TopicStatistics> topicStatistics = learningSessionRepository
			.findTopicStatistics(childId, LearningSessionStatus.COMPLETED)
			.stream()
			.sorted(Comparator.comparing(
				projection -> projection.getTopic().getTopicId()
			))
			.map(this::toTopicStatistics)
			.toList();

		return new LearningStatistics(
			overallStatistics.totalStudyCount(),
			calculateTotalStudySeconds(periods),
			overallStatistics.totalCorrectRate(),
			calculateConsecutiveStudyDays(periods),
			topicStatistics
		);
	}

	@Transactional(readOnly = true)
	public List<WrongAnswer> getRecentWrongAnswers(
		Long userId,
		Long childId
	) {
		childProfileService.getOwnedActive(userId, childId);

		return answerRepository.findRecentWrongAnswers(
			childId,
			WRONG_ANSWER_RESULTS,
			PageRequest.of(0, RECENT_WRONG_ANSWER_LIMIT)
		).stream()
			.map(this::toWrongAnswer)
			.toList();
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

	private ChildStatistics toChildStatistics(
		ChildStatisticsProjection projection
	) {
		return new ChildStatistics(
			projection.getTotalStudyCount(),
			projection.getCorrectCount(),
			projection.getQuestionCount()
		);
	}

	private TopicStatistics toTopicStatistics(
		TopicStatisticsProjection projection
	) {
		long questionCount = projection.getQuestionCount();
		long correctCount = projection.getCorrectCount();
		return new TopicStatistics(
			projection.getTopic().getName(),
			questionCount,
			calculateCorrectRate(correctCount, questionCount)
		);
	}

	private WrongAnswer toWrongAnswer(WrongAnswerProjection projection) {
		return new WrongAnswer(
			projection.getAnswerId(),
			projection.getQuestionId(),
			projection.getQuestionText(),
			questionImageUrlResolver.resolve(projection.getImageUrl()),
			projection.getAnswerText(),
			projection.getModelAnswer(),
			projection.getFeedbackText(),
			projection.getAnsweredAt()
		);
	}

	private long calculateTotalStudySeconds(
		List<LearningSessionPeriodProjection> periods
	) {
		return periods.stream()
			.mapToLong(period -> calculateStudySeconds(
				period.getStartedAt(),
				period.getCompletedAt()
			))
			.sum();
	}

	private int calculateConsecutiveStudyDays(
		List<LearningSessionPeriodProjection> periods
	) {
		Set<LocalDate> studyDates = new HashSet<>();
		for (LearningSessionPeriodProjection period : periods) {
			if (period.getCompletedAt() != null) {
				studyDates.add(toStudyDate(period.getCompletedAt()));
			}
		}

		LocalDate today = LocalDate.now(clock.withZone(STUDY_ZONE));
		LocalDate cursor = studyDates.contains(today)
			? today
			: today.minusDays(1);
		if (!studyDates.contains(cursor)) {
			return 0;
		}

		int consecutiveDays = 0;
		while (studyDates.contains(cursor)) {
			consecutiveDays++;
			cursor = cursor.minusDays(1);
		}
		return consecutiveDays;
	}

	private static LocalDate toStudyDate(LocalDateTime storedAt) {
		return storedAt.atZone(STORAGE_ZONE)
			.withZoneSameInstant(STUDY_ZONE)
			.toLocalDate();
	}

	private double calculateCorrectRate(long correctCount, long questionCount) {
		if (questionCount == 0) {
			return 0.0;
		}
		return Math.round(correctCount * 1000.0 / questionCount) / 10.0;
	}

	private long calculateStudySeconds(
		LocalDateTime startedAt,
		LocalDateTime completedAt
	) {
		return Duration.between(startedAt, completedAt).getSeconds();
	}

}
