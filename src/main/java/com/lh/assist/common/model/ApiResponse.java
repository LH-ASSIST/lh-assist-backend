package com.lh.assist.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@Schema(description = "공통 응답")
public class ApiResponse<T> {
	@Schema(description = "HTTP 상태 코드", example = "200")
	private final int status;
	@Schema(description = "메시지", example = "success")
	private final String message;
	@Schema(description = "응답 데이터")
	private final T data;

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(HttpStatus.OK.value(), "success", data);
	}

	public static <T> ApiResponse<T> created(T data) {
		return new ApiResponse<>(HttpStatus.CREATED.value(), "success created", data);
	}

	public static <T> ApiResponse<T> noContent() {
		return new ApiResponse<>(HttpStatus.NO_CONTENT.value(), "success noContent", null);
	}

	public static ApiResponse<Void> error(String message, int status) {
		return new ApiResponse<>(status, message, null);
	}
}