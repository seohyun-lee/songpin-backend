package sws.songpin.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sws.songpin.domain.bookmark.service.BookmarkService;
import sws.songpin.domain.playlist.service.PlaylistService;

@Tag(name = "MyPage", description = "MyPage 관련 API입니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/me")
public class MyPageController {
    private final PlaylistService playlistService;
    private final BookmarkService bookmarkService;

    @Operation(summary = "내 플레이리스트 목록 조회", description = "마이페이지에서 내 플레이리스트 목록 조회")
    @GetMapping("/playlists")
    public ResponseEntity<?> getAllPlaylists(){
        return ResponseEntity.ok(playlistService.getAllPlaylists());
    }

    @Operation(summary = "내 북마크 목록 조회", description = "마이페이지에서 북마크 목록 조회")
    @GetMapping("/bookmarks")
    public ResponseEntity<?> getAllBookmarks(){
        return ResponseEntity.ok(bookmarkService.getAllBookmarks());
    }

}
