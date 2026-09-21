package com.knowledgegraph.extraction;

import com.knowledgegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class LibraryOrganizationController {
    private final LibraryOrganizationService service;
    public LibraryOrganizationController(LibraryOrganizationService service) { this.service = service; }
    @GetMapping("/{id}/organization")
    public ApiResponse<List<LibraryOrganizationService.Destination>> get(@PathVariable long id) {
        return ApiResponse.ok(service.destinations(id));
    }
}
