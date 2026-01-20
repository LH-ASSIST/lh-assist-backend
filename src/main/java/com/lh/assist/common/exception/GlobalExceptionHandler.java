package com.lh.assist.common.exception;

import lombok.extern.slf4j.Slf4j;
import com.lh.assist.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 중 발생하는 커스텀 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.error("비즈니스 예외 발생: {}", e.getErrorCode().getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getMessage(), errorCode.getStatus().value()),
                errorCode.getStatus()
        );
    }

    /**
     * 인프라/시스템 예외 처리
     */
    @ExceptionHandler(SystemException.class)
    protected ResponseEntity<ApiResponse<Void>> handleSystemException(SystemException e) {
        log.error("시스템 예외 발생: {}", e.getErrorCode().getMessage(), e);
        ErrorCode errorCode = e.getErrorCode();
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getMessage(), errorCode.getStatus().value()),
                errorCode.getStatus()
        );
    }

    /**
     * @Valid 또는 @Validated 조건에 맞지 않을 때 발생 (입력값 검증 실패)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("입력값 검증 실패: {}", e.getMessage());
        return new ResponseEntity<>(
                ApiResponse.error("입력 데이터가 형식에 맞지 않습니다.", ErrorCode.INVALID_INPUT_VALUE.getStatus().value()),
                ErrorCode.INVALID_INPUT_VALUE.getStatus()
        );
    }

    /**
     * 업로드 파일 크기 제한 초과
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("파일 크기 제한 초과: {}", e.getMessage());
        return new ResponseEntity<>(
                ApiResponse.error(ErrorCode.FILE_SIZE_EXCEEDED.getMessage(), ErrorCode.FILE_SIZE_EXCEEDED.getStatus().value()),
                ErrorCode.FILE_SIZE_EXCEEDED.getStatus()
        );
    }

    /**
     * 그 외 정의되지 않은 모든 시스템 예외 처리
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("알 수 없는 예외 발생", e);
        return new ResponseEntity<>(
                ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR.getStatus().value()),
                ErrorCode.INTERNAL_SERVER_ERROR.getStatus()
        );
    }
}
