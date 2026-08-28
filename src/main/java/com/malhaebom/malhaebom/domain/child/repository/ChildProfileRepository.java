package com.malhaebom.malhaebom.domain.child.repository;

import com.malhaebom.malhaebom.domain.child.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {
	List<ChildProfile> findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(Long userId);

	Optional<ChildProfile> findByIdAndActiveTrue(Long childId);

	boolean existsByUserIdAndNicknameAndActiveTrue(Long userId, String nickname);

	boolean existsByUserIdAndNicknameAndActiveTrueAndIdNot(Long userId, String nickname, Long childId);
}
