package com.lh.assist.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.analysis.api.dto.response.AnalysisSectionResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisSummaryResponse;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.analysis.domain.repository.AnalysisSectionRepository;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.infrastructure.aws.sqs.SqsMessageProducer;
import com.lh.assist.support.ReflectionTestUtils;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    @Mock
    private AnalysisJobRepository analysisJobRepository;

    @Mock
    private AnalysisSectionRepository analysisSectionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SqsMessageProducer sqsMessageProducer;

    @InjectMocks
    private AnalysisService analysisService;

    @Test
    @DisplayName("분석 요약 조회 시 리스크 등급과 위반 건수를 반환해야 한다")
    void 분석_요약_조회_성공() {
        User user = TestDataFactory.user("owner@lh.com");
        ReflectionTestUtils.setField(user, "userId", 1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build();
        ReflectionTestUtils.setField(document, "docId", 10L);

        AnalysisResult result = AnalysisResult.builder()
                .document(document)
                .baseDate(LocalDate.now())
                .totalRiskScore(85)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build();
        ReflectionTestUtils.setField(result, "analysisId", 100L);

        when(userRepository.findByEmail("owner@lh.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(analysisResultRepository.findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(10L, AnalysisResultStatus.SUCCEEDED))
                .thenReturn(Optional.of(result));
        when(analysisSectionRepository.countByAnalysisResult_AnalysisIdAndIsViolationTrue(100L)).thenReturn(3L);

        AnalysisSummaryResponse response = analysisService.getAnalysisSummary(10L, "owner@lh.com");

        assertThat(response.getAnalysisId()).isEqualTo(100L);
        assertThat(response.getTotalRiskScore()).isEqualTo(85);
        assertThat(response.getRiskLevel()).isEqualTo("HIGH");
        assertThat(response.getTotalViolations()).isEqualTo(3);
    }

    @Test
    @DisplayName("분석 요약 조회 시 총점이 없으면 0으로 처리해야 한다")
    void 분석_요약_총점_없음() {
        User user = TestDataFactory.user("owner2@lh.com");
        ReflectionTestUtils.setField(user, "userId", 1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build();
        ReflectionTestUtils.setField(document, "docId", 15L);

        AnalysisResult result = AnalysisResult.builder()
                .document(document)
                .baseDate(LocalDate.now())
                .totalRiskScore(null)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build();
        ReflectionTestUtils.setField(result, "analysisId", 110L);

        when(userRepository.findByEmail("owner2@lh.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(15L)).thenReturn(Optional.of(document));
        when(analysisResultRepository.findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(15L, AnalysisResultStatus.SUCCEEDED))
                .thenReturn(Optional.of(result));
        when(analysisSectionRepository.countByAnalysisResult_AnalysisIdAndIsViolationTrue(110L)).thenReturn(0L);

        AnalysisSummaryResponse response = analysisService.getAnalysisSummary(15L, "owner2@lh.com");

        assertThat(response.getTotalRiskScore()).isZero();
        assertThat(response.getRiskLevel()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("분석 요약 조회 시 점수에 따라 위험 등급이 계산되어야 한다")
    void 분석_요약_위험_등급_계산() {
        User user = TestDataFactory.user("owner@lh.com");
        ReflectionTestUtils.setField(user, "userId", 1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build();
        ReflectionTestUtils.setField(document, "docId", 11L);

        AnalysisResult result = AnalysisResult.builder()
                .document(document)
                .baseDate(LocalDate.now())
                .totalRiskScore(30)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build();
        ReflectionTestUtils.setField(result, "analysisId", 101L);

        when(userRepository.findByEmail("owner@lh.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));
        when(analysisResultRepository.findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(11L, AnalysisResultStatus.SUCCEEDED))
                .thenReturn(Optional.of(result));
        when(analysisSectionRepository.countByAnalysisResult_AnalysisIdAndIsViolationTrue(101L)).thenReturn(0L);

        AnalysisSummaryResponse response = analysisService.getAnalysisSummary(11L, "owner@lh.com");

        assertThat(response.getRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("분석 섹션 조회 시 좌표와 위반 여부가 매핑되어야 한다")
    void 분석_섹션_조회_성공() {
        User user = TestDataFactory.user("owner@lh.com");
        ReflectionTestUtils.setField(user, "userId", 1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build();
        ReflectionTestUtils.setField(document, "docId", 12L);

        AnalysisResult result = AnalysisResult.builder()
                .document(document)
                .baseDate(LocalDate.now())
                .totalRiskScore(10)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build();
        ReflectionTestUtils.setField(result, "analysisId", 102L);

        AnalysisSection section = AnalysisSection.builder()
                .analysisResult(result)
                .externalSectionId("TXT_001_001")
                .bbox("[10,20,30,40]")
                .pageNumber(1)
                .isViolation(true)
                .riskScore(90)
                .reasoning("위반 사유")
                .build();
        ReflectionTestUtils.setField(section, "sectionId", 1000L);

        when(userRepository.findByEmail("owner@lh.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(12L)).thenReturn(Optional.of(document));
        when(analysisResultRepository.findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(12L, AnalysisResultStatus.SUCCEEDED))
                .thenReturn(Optional.of(result));
        when(analysisSectionRepository.findAllByAnalysisResult_AnalysisId(102L)).thenReturn(List.of(section));

        List<AnalysisSectionResponse> responses = analysisService.getAnalysisSections(12L, "owner@lh.com");

        assertThat(responses).hasSize(1);
        AnalysisSectionResponse response = responses.get(0);
        assertThat(response.getSectionId()).isEqualTo(1000L);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getBbox()).isEqualTo("[10,20,30,40]");
        assertThat(response.isViolation()).isTrue();
        assertThat(response.getRiskScore()).isEqualTo(90);
        assertThat(response.getReasoning()).isEqualTo("위반 사유");
    }

    @Test
    @DisplayName("분석 결과가 없으면 NOT_FOUND가 발생해야 한다")
    void 분석_결과_없음() {
        User user = TestDataFactory.user("owner@lh.com");
        ReflectionTestUtils.setField(user, "userId", 1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build();
        when(userRepository.findByEmail("owner@lh.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(13L)).thenReturn(Optional.of(document));
        when(analysisResultRepository.findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(13L, AnalysisResultStatus.SUCCEEDED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.getAnalysisSummary(13L, "owner@lh.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ANALYSIS_NOT_FOUND);
    }

    @Test
    @DisplayName("분석 결과가 없으면 섹션 조회도 NOT_FOUND가 발생해야 한다")
    void 분석_섹션_결과_없음() {
        User user = TestDataFactory.user("owner-section@lh.com");
        ReflectionTestUtils.setField(user, "userId", 1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build();
        when(userRepository.findByEmail("owner-section@lh.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(16L)).thenReturn(Optional.of(document));
        when(analysisResultRepository.findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(16L, AnalysisResultStatus.SUCCEEDED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.getAnalysisSections(16L, "owner-section@lh.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ANALYSIS_NOT_FOUND);
    }

    @Test
    @DisplayName("문서 소유자가 아니면 분석 조회가 거부되어야 한다")
    void 분석_조회_소유자_아니면_거부() {
        User owner = TestDataFactory.user("owner@lh.com");
        ReflectionTestUtils.setField(owner, "userId", 1L);
        User requester = TestDataFactory.user("other@lh.com");
        ReflectionTestUtils.setField(requester, "userId", 2L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build();

        when(userRepository.findByEmail("other@lh.com")).thenReturn(Optional.of(requester));
        when(documentRepository.findById(14L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> analysisService.getAnalysisSummary(14L, "other@lh.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("문서 소유자가 아니면 섹션 조회도 거부되어야 한다")
    void 분석_섹션_조회_소유자_아니면_거부() {
        User owner = TestDataFactory.user("owner3@lh.com");
        ReflectionTestUtils.setField(owner, "userId", 1L);
        User requester = TestDataFactory.user("other3@lh.com");
        ReflectionTestUtils.setField(requester, "userId", 2L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build();

        when(userRepository.findByEmail("other3@lh.com")).thenReturn(Optional.of(requester));
        when(documentRepository.findById(17L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> analysisService.getAnalysisSections(17L, "other3@lh.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("이메일이 비어 있으면 인증 오류가 발생해야 한다")
    void 분석_조회_이메일_비어있음() {
        assertThatThrownBy(() -> analysisService.getAnalysisSummary(10L, " "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("문서 소유자가 아니면 접근 거부가 발생해야 한다")
    void 문서_소유자_아니면_거부() {
        User requester = TestDataFactory.user("user1@lh.com");
        ReflectionTestUtils.setField(requester, "userId", 1L);
        User owner = TestDataFactory.user("user2@lh.com");
        ReflectionTestUtils.setField(owner, "userId", 2L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/2/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build();
        when(userRepository.findByEmail("req@lh.com")).thenReturn(Optional.of(requester));
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

        LocalDate baseDate = LocalDate.now();

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(10L, "req@lh.com", baseDate))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(sqsMessageProducer, never()).sendAnalysisRequested(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("기준일이 없으면 입력값 오류가 발생해야 한다")
    void 기준일_없으면_입력값_오류() {
        User requester = TestDataFactory.user("user1@lh.com");
        ReflectionTestUtils.setField(requester, "userId", 1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(null)
                .user(requester)
                .build();
        when(userRepository.findByEmail("req@lh.com")).thenReturn(Optional.of(requester));
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(11L, "req@lh.com", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("문서 ID가 없으면 입력값 오류가 발생해야 한다")
    void 문서_ID_없으면_입력값_오류() {
        User requester = TestDataFactory.user("user1@lh.com");
        ReflectionTestUtils.setField(requester, "userId", 1L);
        when(userRepository.findByEmail("req@lh.com")).thenReturn(Optional.of(requester));

        LocalDate baseDate = LocalDate.now();

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(null, "req@lh.com", baseDate))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("사용자가 없으면 인증 오류가 발생해야 한다")
    void 사용자_없으면_인증_오류() {
        when(userRepository.findByEmail("missing@lh.com")).thenReturn(Optional.empty());

        LocalDate baseDate = LocalDate.now();

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(1L, "missing@lh.com", baseDate))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

}
