package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.AccountRole;
import com.malhaebom.malhaebom.domain.User;

public record UserResponse(Long guardianId, String email, String name, AccountRole role) {
	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
	}
}
