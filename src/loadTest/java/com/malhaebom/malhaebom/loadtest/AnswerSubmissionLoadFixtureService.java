package com.malhaebom.malhaebom.loadtest;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.child.ChildLevel;
import com.malhaebom.malhaebom.domain.child.ChildProfile;
import com.malhaebom.malhaebom.domain.child.repository.ChildProfileRepository;
import com.malhaebom.malhaebom.domain.learning.*;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtProperties;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtProvider;
import com.malhaebom.malhaebom.loadtest.AnswerSubmissionLoadFixtureManifest.Fixture;
import com.malhaebom.malhaebom.loadtest.AnswerSubmissionLoadFixtureManifest.StageFixtures;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AnswerSubmissionLoadFixtureService {
	private static final String TRANSCRIPT = "He is running.";
	private static final String OWNER_EMAIL = "loadtest-answer@malhaebom.invalid";
	private static final String CHILD_NICKNAME = "load-test";

	private final UserRepository userRepository;
	private final ChildProfileRepository childProfileRepository;
	private final QuestionRepository questionRepository;
	private final LearningSessionRepository learningSessionRepository;
	private final SpeechAnswerRepository speechAnswerRepository;
	private final JdbcTemplate jdbcTemplate;
	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;

	public AnswerSubmissionLoadFixtureService(
		UserRepository userRepository,
		ChildProfileRepository childProfileRepository,
		QuestionRepository questionRepository,
		LearningSessionRepository learningSessionRepository,
		SpeechAnswerRepository speechAnswerRepository,
		JdbcTemplate jdbcTemplate,
		JwtProvider jwtProvider,
		JwtProperties jwtProperties
	) {
		this.userRepository = userRepository;
		this.childProfileRepository = childProfileRepository;
		this.questionRepository = questionRepository;
		this.learningSessionRepository = learningSessionRepository;
		this.speechAnswerRepository = speechAnswerRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.jwtProvider = jwtProvider;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public AnswerSubmissionLoadFixtureManifest seed(String runId, List<Integer> stages) {
		validateRunId(runId);
		validateStages(stages);
		User owner = userRepository.findByEmail(OWNER_EMAIL)
			.orElseGet(() -> userRepository.save(User.create(
				"Load Test",
				OWNER_EMAIL,
				"load-test-login-disabled"
			)));
		ChildProfile child = childProfileRepository
			.findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(owner.getId())
			.stream()
			.filter(profile -> CHILD_NICKNAME.equals(profile.getNickname()))
			.findFirst()
			.orElseGet(() -> childProfileRepository.save(
				ChildProfile.create(
					owner,
					CHILD_NICKNAME,
					10,
					3,
					ChildLevel.BEGINNER
				)
			));
		Question question = questionRepository.save(Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"",
			"The boy is running.",
			new LinkedHashSet<>(Set.of(
				"He is running.",
				"He's running."
			)),
			"현재진행형으로 말해 보세요.",
			null
		));

		List<StageFixtures> stageFixtures = new ArrayList<>();
		for (int concurrency : stages) {
			List<Fixture> fixtures = new ArrayList<>(concurrency);
			for (int index = 0; index < concurrency; index++) {
				LearningSession session = learningSessionRepository.save(
					LearningSession.create(
						child.getId(),
						LearningTopic.DAILY_LIFE,
						Difficulty.EASY,
						List.of(question)
					)
				);
				LearningSessionQuestion sessionQuestion = session
					.getCurrentQuestion();
				SpeechAnswer speechAnswer = SpeechAnswer.start(
					sessionQuestion,
					requestKey(runId, concurrency, index),
					1
				);
				speechAnswer.complete(TRANSCRIPT, 0.99, "LOAD_TEST_FIXTURE");
				speechAnswerRepository.save(speechAnswer);
				fixtures.add(new Fixture(
					session.getId(),
					sessionQuestion.getId(),
					speechAnswer.getId()
				));
			}
			stageFixtures.add(new StageFixtures(concurrency, fixtures));
		}
		learningSessionRepository.flush();
		speechAnswerRepository.flush();
		return new AnswerSubmissionLoadFixtureManifest(
			runId,
			jwtProvider.createToken(
				owner.getId(),
				jwtProperties.access().expiration(),
				jwtProperties.access().signingKey()
			),
			question.getId(),
			stageFixtures
		);
	}

	@Transactional
	public void cleanup(AnswerSubmissionLoadFixtureManifest manifest) {
		List<Fixture> fixtures = manifest.allFixtures();
		List<Long> speechAnswerIds = fixtures.stream()
			.map(Fixture::speechAnswerId)
			.toList();
		List<Long> sessionQuestionIds = fixtures.stream()
			.map(Fixture::sessionQuestionId)
			.toList();
		List<Long> sessionIds = fixtures.stream()
			.map(Fixture::sessionId)
			.toList();

		deleteByIds(
			"answer_submissions",
			"speech_answer_id",
			speechAnswerIds
		);
		deleteByIds("answers", "speech_answer_id", speechAnswerIds);
		deleteByIds("speech_answers", "id", speechAnswerIds);
		deleteByIds(
			"learning_session_questions",
			"id",
			sessionQuestionIds
		);
		deleteByIds("learning_sessions", "id", sessionIds);
		jdbcTemplate.update(
			"delete from question_accepted_answers where question_id = ?",
			manifest.questionId()
		);
		jdbcTemplate.update(
			"delete from questions where id = ?",
			manifest.questionId()
		);
	}

	private void deleteByIds(
		String table,
		String column,
		List<Long> ids
	) {
		for (int start = 0; start < ids.size(); start += 500) {
			List<Long> chunk = ids.subList(
				start,
				Math.min(start + 500, ids.size())
			);
			String placeholders = String.join(
				",",
				java.util.Collections.nCopies(chunk.size(), "?")
			);
			jdbcTemplate.update(
				"delete from " + table + " where " + column
					+ " in (" + placeholders + ")",
				chunk.toArray()
			);
		}
	}

	private String requestKey(String runId, int concurrency, int index) {
		return "loadtest-answer-%s-%d-%d".formatted(
			runId,
			concurrency,
			index
		);
	}

	private void validateRunId(String runId) {
		if (runId == null || runId.isBlank() || runId.length() > 50) {
			throw new IllegalArgumentException(
				"load-test run-id는 1자 이상 50자 이하여야 합니다."
			);
		}
	}

	private void validateStages(List<Integer> stages) {
		if (stages == null || stages.isEmpty()
			|| stages.stream().anyMatch(stage -> stage == null || stage < 1)) {
			throw new IllegalArgumentException(
				"load-test 단계는 하나 이상의 양수여야 합니다."
			);
		}
	}
}
