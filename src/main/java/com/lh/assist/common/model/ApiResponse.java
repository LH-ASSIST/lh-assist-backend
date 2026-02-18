package com.lh.assist.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import com.lh.assist.common.exception.ErrorCode;

@Getter
@Schema(description = "공통 응답")
public class ApiResponse<T> {
	@Schema(description = "HTTP 상태 코드", example = "200")
	private final int status;
	@Schema(description = "에러 코드", example = "C001", nullable = true)
	private final String code;
	@Schema(description = "메시지", example = "success")
	private final String message;
	@Schema(description = "응답 데이터")
	private final T data;

	private ApiResponse(
			int status,
			String code,
			String message,
			T data
	) {
		this.status = status;
		this.code = code;
		this.message = message;
		this.data = data;
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(HttpStatus.OK.value(), null, "success", data);
	}

	public static <T> ApiResponse<T> created(T data) {
		return new ApiResponse<>(HttpStatus.CREATED.value(), null, "success created", data);
	}

	public static <T> ApiResponse<T> noContent() {
		return new ApiResponse<>(HttpStatus.NO_CONTENT.value(), null, "success noContent", null);
	}

	public static ApiResponse<Void> error(String message, int status) {
		return new ApiResponse<>(status, null, message, null);
	}

	public static ApiResponse<Void> error(ErrorCode errorCode) {
		return new ApiResponse<>(
				errorCode.getStatus().value(),
				errorCode.getCode(),
				errorCode.getMessage(),
				null
		);
	}
}
