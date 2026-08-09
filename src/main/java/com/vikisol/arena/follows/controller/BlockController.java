package com.vikisol.arena.follows.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.follows.dto.BlockedUserResponse;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> block(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
        blockService.block(principal.getId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> unblock(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
        blockService.unblock(principal.getId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> getMyBlocks(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(blockService.getMyBlocks(principal.getId())));
    }
}
