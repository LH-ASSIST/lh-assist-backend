package com.lh.assist.analysis.api.docs;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(name = "리스크 분석", description = "분석 결과 조회 API")
public @interface AnalysisApiDocs {
}