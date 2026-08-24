package com.malhaebom.malhaebom.domain.learning.repository.projection;

public interface ChildStatisticsProjection {

	Long getChildId();

	Long getTotalStudyCount();

	Long getCorrectCount();

	Long getQuestionCount();
}
