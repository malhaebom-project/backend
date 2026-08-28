package com.malhaebom.malhaebom.domain.child;

import com.malhaebom.malhaebom.domain.BaseEntity;
import com.malhaebom.malhaebom.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "children_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfile extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 30)
	private String nickname;

	@Column(nullable = false)
	private int age;

	@Column(nullable = false)
	private int grade;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ChildLevel level;

	@Column(nullable = false)
	private boolean active;

	public static ChildProfile create(User user, String nickname, int age, int grade, ChildLevel level) {
		ChildProfile profile = new ChildProfile();
		profile.user = user;
		profile.nickname = normalizeNickname(nickname);
		profile.age = age;
		profile.grade = grade;
		profile.level = level;
		profile.active = true;
		return profile;
	}

	public void update(String nickname, Integer age, Integer grade, ChildLevel level) {
		if (nickname != null) {
			this.nickname = normalizeNickname(nickname);
		}
		if (age != null) {
			this.age = age;
		}
		if (grade != null) {
			this.grade = grade;
		}
		if (level != null) {
			this.level = level;
		}
	}

	public void deactivate() {
		active = false;
	}

	public boolean isOwnedBy(Long userId) {
		return user.getId().equals(userId);
	}

	private static String normalizeNickname(String nickname) {
		return nickname.trim();
	}
}
