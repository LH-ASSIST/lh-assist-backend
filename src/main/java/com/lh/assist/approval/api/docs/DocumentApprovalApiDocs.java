package com.lh.assist.approval.api.docs;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(name = "문서 승인", description = "문서 승인 관련 API")
public @interface DocumentApprovalApiDocs
{
}