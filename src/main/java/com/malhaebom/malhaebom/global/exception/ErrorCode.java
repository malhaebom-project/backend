package com.malhaebom.malhaebom.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

	INVALID_REQUEST(
		HttpStatus.BAD_REQUEST,
		"요청 값이 올바르지 않습니다."
	),
	UNAUTHORIZED(
		HttpStatus.UNAUTHORIZED,
		"인증이 필요합니다."
	),
	FORBIDDEN(
		HttpStatus.FORBIDDEN,
		"접근 권한이 없습니다."
	),
	EMAIL_ALREADY_EXISTS(
		HttpStatus.CONFLICT,
		"이미 사용 중인 이메일입니다."
	),
	NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"요청한 리소스를 찾을 수 없습니다."
	),
	QUESTION_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"문제를 찾을 수 없습니다."
	),
	LEARNING_SESSION_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"학습 세션을 찾을 수 없습니다."
	),
	LEARNING_TOPIC_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"학습 주제를 찾을 수 없습니다."
	),
	CHILD_PROFILE_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"어린이 프로필을 찾을 수 없습니다."
	),
	CHILD_ACCESS_DENIED(
		HttpStatus.FORBIDDEN,
		"어린이 프로필에 접근할 권한이 없습니다."
	),
	CHILD_NICKNAME_ALREADY_EXISTS(
		HttpStatus.CONFLICT,
		"이미 사용 중인 어린이 프로필 별명입니다."
	),
	LEARNING_SESSION_NOT_IN_PROGRESS(
		HttpStatus.CONFLICT,
		"진행 중인 학습 세션이 아닙니다."
	),
	INSUFFICIENT_QUESTIONS(
		HttpStatus.BAD_REQUEST,
		"요청한 개수만큼 문제를 구성할 수 없습니다."
	),
	SPEECH_ANSWER_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"음성 답변을 찾을 수 없습니다."
	),
	INVALID_AUDIO_FILE(
		HttpStatus.BAD_REQUEST,
		"유효하지 않은 음성 파일입니다."
	),
	CURRENT_QUESTION_MISMATCH(
		HttpStatus.BAD_REQUEST,
		"현재 진행 중인 문제가 아닙니다."
	),
	SPEECH_PROCESSING(
		HttpStatus.CONFLICT,
		"음성 답변을 처리하고 있습니다."
	),
	ANSWER_SUBMISSION_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"답변 제출 예약을 찾을 수 없습니다."
	),
	ANSWER_SUBMISSION_PROCESSING(
		HttpStatus.CONFLICT,
		"답변 제출을 처리하고 있습니다."
	),
	ANSWER_SUBMISSION_CONFLICT(
		HttpStatus.CONFLICT,
		"처리 중이거나 재시도할 답변 제출이 이미 있습니다."
	),
	ANSWER_SUBMISSION_TIMEOUT(
		HttpStatus.GATEWAY_TIMEOUT,
		"답변 제출 처리 시간이 초과되었습니다."
	),
	ANSWER_ASSESSMENT_FAILED(
		HttpStatus.BAD_GATEWAY,
		"답변 채점에 실패했습니다. 잠시 후 다시 시도해 주세요."
	),
	ANSWER_ASSESSMENT_OVERLOADED(
		HttpStatus.SERVICE_UNAVAILABLE,
		"답변 채점 요청이 많습니다. 잠시 후 다시 시도해 주세요."
	),
	SPEECH_NOT_RECOGNIZED(
		HttpStatus.UNPROCESSABLE_CONTENT,
		"음성을 인식하지 못했습니다."
	),
	AI_REQUEST_LIMIT_EXCEEDED(
		HttpStatus.TOO_MANY_REQUESTS,
		"음성 인식 요청이 많습니다. 잠시 후 다시 시도해 주세요."
	),
	STT_PROCESSING_FAILED(
		HttpStatus.INTERNAL_SERVER_ERROR,
		"음성 변환 처리에 실패했습니다."
	),
	STT_PROCESSING_TIMEOUT(
		HttpStatus.GATEWAY_TIMEOUT,
		"음성 변환 처리 시간이 초과되었습니다."
	);

	private final HttpStatus httpStatus;
	private final String message;

	ErrorCode(HttpStatus httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
	}

}
