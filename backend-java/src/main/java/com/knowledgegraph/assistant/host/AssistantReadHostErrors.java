package com.knowledgegraph.assistant.host;

import com.knowledgegraph.common.*;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.*;

/** Extends the approved common envelope without changing its main-application behavior. */
@RestControllerAdvice
@Conditional(StandaloneHostOnly.class)
public final class AssistantReadHostErrors extends GlobalExceptionHandler {
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> unmapped(NoHandlerFoundException error) {
        return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND","NOT_FOUND"));
    }
}
