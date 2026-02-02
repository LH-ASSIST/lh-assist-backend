package com.lh.assist.test.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class PdfHighlightTestController {

    private final ObjectMapper objectMapper;

    @GetMapping("/test/pdf-viewer")
    public String pdfViewer(Model model) throws JsonProcessingException {
        List<MockHighlight> highlights = new ArrayList<>();
        highlights.add(new MockHighlight(1, 100, 500, 200, 20, "HIGH", "소득 기준 위반"));
        highlights.add(new MockHighlight(1, 150, 400, 150, 30, "MEDIUM", "기간 산정 오류"));
        highlights.add(new MockHighlight(2, 100, 700, 300, 50, "LOW", "단순 오타 의심"));

        model.addAttribute("highlightsJson", objectMapper.writeValueAsString(highlights));
        return "test/pdf-test";
    }

    @GetMapping(value = "/test/pdf-file", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody
    public byte[] getPdfFile() throws IOException {
        ClassPathResource pdfFile = new ClassPathResource("static/test.pdf");
        try (var inputStream = pdfFile.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private record MockHighlight(
            int page,
            double x,
            double y,
            double width,
            double height,
            String level,
            String msg
    ) {
    }
}
