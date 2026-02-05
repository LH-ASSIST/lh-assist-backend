package com.lh.assist.admin.api.docs;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(name = "관리자 전용", description = "관리자 전용 API")
public @interface AdminApiDocs {
}
