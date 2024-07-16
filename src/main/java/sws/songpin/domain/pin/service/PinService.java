package sws.songpin.domain.pin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sws.songpin.domain.bookmark.dto.response.BookmarkListResponseDto;
import sws.songpin.domain.bookmark.entity.Bookmark;
import sws.songpin.domain.genre.entity.Genre;
import sws.songpin.domain.genre.entity.GenreName;
import sws.songpin.domain.genre.service.GenreService;
import sws.songpin.domain.member.entity.Member;
import sws.songpin.domain.member.service.MemberService;
import sws.songpin.domain.model.Visibility;
import sws.songpin.domain.pin.dto.request.PinAddRequestDto;
import sws.songpin.domain.pin.dto.request.PinUpdateRequestDto;
import sws.songpin.domain.pin.entity.Pin;
import sws.songpin.domain.pin.repository.PinRepository;
import sws.songpin.domain.place.entity.Place;
import sws.songpin.domain.place.service.PlaceService;
import sws.songpin.domain.playlist.dto.response.PlaylistUnitDto;
import sws.songpin.domain.playlist.entity.Playlist;
import sws.songpin.domain.playlistpin.entity.PlaylistPin;
import sws.songpin.domain.playlistpin.repository.PlaylistPinRepository;
import sws.songpin.domain.song.dto.response.SongDetailsPinDto;
import sws.songpin.domain.song.entity.Song;
import sws.songpin.domain.song.repository.SongRepository;
import sws.songpin.domain.song.service.SongService;
import sws.songpin.global.exception.CustomException;
import sws.songpin.global.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PinService {
    private final PinRepository pinRepository;
    private final SongRepository songRepository;
    private final PlaylistPinRepository playlistPinRepository;
    private final MemberService memberService;
    private final SongService songService;
    private final PlaceService placeService;
    private final GenreService genreService;

    // 음악 핀 생성 - 노래, 장소가 없다면 추가하기
    public Long createPin(PinAddRequestDto pinAddRequestDto) {
        Member member = memberService.getCurrentMember();
        Song finalSong = songService.getOrCreateSong(pinAddRequestDto.song());
        Place finalPlace = placeService.getOrCreatePlace(pinAddRequestDto.place());
        Genre genre = genreService.getGenreByGenreName(pinAddRequestDto.genreName());

        Pin pin = Pin.builder()
                .listenedDate(pinAddRequestDto.listenedDate())
                .memo(pinAddRequestDto.memo())
                .visibility(pinAddRequestDto.visibility())
                .member(member)
                .song(finalSong)
                .place(finalPlace)
                .genre(genre)
                .build();
        pinRepository.save(pin);
        updateSongAvgGenreName(finalSong);

        // 노래 상세정보 페이지로 이동
        return finalSong.getSongId();
    }

    // 음악 핀 수정
    public Long updatePin(Long pinId, PinUpdateRequestDto pinUpdateRequestDto) {
        Pin pin = validatePinCreator(pinId);

        Genre genre = genreService.getGenreByGenreName(pinUpdateRequestDto.genreName());
        pin.updatePin(pinUpdateRequestDto.listenedDate(), pinUpdateRequestDto.memo(), pinUpdateRequestDto.visibility(), genre);
        pinRepository.save(pin);

        return pin.getSong().getSongId();
    }

    // 음악 핀 삭제
    public void deletePin(Long pinId) {
        Pin pin = validatePinCreator(pinId);
        List<PlaylistPin> playlistPins = playlistPinRepository.findAllByPin(pin);
        for (PlaylistPin playlistPin : playlistPins) {
            Playlist playlist = playlistPin.getPlaylist();
            playlist.removePlaylistPin(playlistPin);
        }
        playlistPinRepository.deleteAll(playlistPins);
        pinRepository.delete(pin);
    }

    private void updateSongAvgGenreName(Song song) {
        List<Genre> genres = pinRepository.findAllBySong(song).stream()
                .map(Pin::getGenre)
                .collect(Collectors.toList());

        Optional<GenreName> avgGenreName = songService.calculateAvgGenreName(genres);
        avgGenreName.ifPresent(song::setAvgGenreName);
        songRepository.save(song);
    }

    // 현재 로그인된 사용자가 핀의 생성자인지 확인
    @Transactional(readOnly = true)
    public Pin validatePinCreator(Long pinId) {
        Pin pin = getPinById(pinId);
        Member currentMember = memberService.getCurrentMember();
        if (!pin.getMember().getMemberId().equals(currentMember.getMemberId())){
            throw new CustomException(ErrorCode.UNAUTHORIZED_REQUEST);
        }
        return pin;
    }

    // 특정 노래에 대한 핀 조회
    @Transactional(readOnly = true)
    public List<SongDetailsPinDto> getPinsForSong(Long songId, boolean onlyMyPins) {
        Song song = songService.getSongById(songId);
        List<Pin> pins;
        Member currentMember = onlyMyPins ? memberService.getCurrentMember() : null;

        if (onlyMyPins && currentMember != null) {
            // 내 핀만 보기 - 현재 사용자의 모든 핀 가져오기(visibility 상관없음)
            pins = pinRepository.findAllBySongAndMember(song, currentMember);
        } else {
            // 전체 핀 보기
            // 1. "비공개 핀"에 대해 현재 사용자가 작성한 것만 가져오기
            pins = new ArrayList<>();
            if (currentMember != null) {
                pins.addAll(pinRepository.findMyPrivatePins(song, currentMember, Visibility.PRIVATE));
            }
            // 2. "공개 핀"에 대해 모든 사용자가 작성한 것 가져오기
            List<Pin> publicPins = pinRepository.findAllPublicPins(song, Visibility.PUBLIC);

//            // Sol2 - 그런데 어차피 데이터베이스 2번 호출은 불가피해서 그냥 위의 방식으로 진행
//            // Sol3 - 쿼리문 사용
//            // 모든 사용자의 공개 핀
//            List<Pin> publicPins = pinRepository.findAllBySongAndVisibility(song, Visibility.PUBLIC);
//            // 현재 사용자의 모든 핀
//            List<Pin> myPins = currentMember != null ? pinRepository.findAllBySongAndMember(song, currentMember) : Collections.emptyList();
//            // 중복 제거
//            Set<Pin> uniquePins = new HashSet<>(publicPins);
//            uniquePins.addAll(myPins);
//            pins = new ArrayList<>(uniquePins);
        }

        Long currentMemberId = currentMember != null ? currentMember.getMemberId() : null;
        return pins.stream()
                .map(pin -> {
                    // currentMemberId와 핀의 생성자가 일치하면 true
                    Boolean isMine = currentMemberId != null && pin.getMember().getMemberId().equals(currentMemberId);
                    return SongDetailsPinDto.from(pin, currentMemberId);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Pin getPinById(Long pinId) {
        return pinRepository.findById(pinId)
                .orElseThrow(() -> new CustomException(ErrorCode.PIN_NOT_FOUND));
    }

}
