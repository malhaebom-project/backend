package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.repository.projection.WrongAnswerProjection;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

	Optional<Answer> findFirstBySessionQuestion_IdOrderByAttemptNoDesc(
		Long sessionQuestionId
	);

	boolean existsBySpeechAnswer_Id(Long speechAnswerId);

	@Query("""
		select answer.id as answerId,
			question.id as questionId,
			question.questionText as questionText,
			question.imageUrl as imageUrl,
			answer.answerText as answerText,
			answer.modelAnswerSnapshot as modelAnswer,
			answer.feedbackText as feedbackText,
			answer.submittedAt as answeredAt
		from Answer answer
		join answer.sessionQuestion sessionQuestion
		join sessionQuestion.learningSession learningSession
		join sessionQuestion.question question
		where learningSession.childId = :childId
			and answer.result in :results
		order by answer.submittedAt desc, answer.id desc
		""")
	List<WrongAnswerProjection> findRecentWrongAnswers(
		@Param("childId") Long childId,
		@Param("results") List<AnswerResult> results,
		Pageable pageable
	);
}
