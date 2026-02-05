package com.lh.assist.suggestion.api.docs;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(name = "건의사항", description = "건의사항 관련 API")
public @interface SuggestionApiDocs {
}
