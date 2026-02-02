package com.lh.assist.analysis.api;

import com.lh.assist.analysis.api.dto.response.AnalysisSectionResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisSummaryResponse;
import com.lh.assist.analysis.api.docs.AnalysisSectionDocs;
import com.lh.assist.analysis.api.docs.AnalysisSummaryDocs;
import com.lh.assist.analysis.api.docs.AnalysisApiDocs;
import com.lh.assist.analysis.application.AnalysisService;
import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis")
@AnalysisApiDocs
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/documents/{docId}/summary")
    @PreAuthorize("isAuthenticated()")
    @AnalysisSummaryDocs
    public ResponseEntity<ApiResponse<AnalysisSummaryResponse>> getAnalysisSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long docId
    ) {
        AnalysisSummaryResponse response = analysisService.getAnalysisSummary(docId, principal.email());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/documents/{docId}/sections")
    @PreAuthorize("isAuthenticated()")
    @AnalysisSectionDocs
    public ResponseEntity<ApiResponse<List<AnalysisSectionResponse>>> getAnalysisSections(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long docId
    ) {
        List<AnalysisSectionResponse> responses = analysisService.getAnalysisSections(docId, principal.email());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}