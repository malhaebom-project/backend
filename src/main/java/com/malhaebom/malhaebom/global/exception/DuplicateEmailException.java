package com.malhaebom.malhaebom.global.exception;

public class DuplicateEmailException extends ApiException {

	public DuplicateEmailException() {
		super(ErrorCode.EMAIL_ALREADY_EXISTS);
	}
}
