package com.malhaebom.malhaebom.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountRole role;

	private User(String name, String email, String password, AccountRole role) {
		this.name = name;
		this.email = email;
		this.password = password;
		this.role = role;
	}

	public static User create(String name, String email, String encodedPassword) {
		return new User(name, email, encodedPassword, AccountRole.GUARDIAN);
	}

	public static User createAdmin(String name, String email, String encodedPassword) {
		return new User(name, email, encodedPassword, AccountRole.ADMIN);
	}

	public boolean isAdmin() {
		return role == AccountRole.ADMIN;
	}
}
