package balancetalk.module.member.presentation;

import balancetalk.module.member.application.MemberService;
import balancetalk.module.member.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "member", description = "회원 API")
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/join")
    @Operation(summary = "회원 가입", description = "닉네임, 이메일, 비밀번호를 입력하여 회원 가입을 한다.")
    public String join(@Valid @RequestBody JoinRequest joinDto, HttpServletRequest request) {
        memberService.join(joinDto);
        return "회원가입이 정상적으로 처리되었습니다.";
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "회원 가입 한 이메일과 패스워드를 사용하여 로그인 한다.")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
        return memberService.login(loginRequest);
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{memberId}")
    @Operation(summary = "단일 회원 조회", description = "memberId와 일치하는 회원 정보를 조회한다.")
    public MemberResponse findMemberInfo(@PathVariable("memberId") Long memberId) {
        return memberService.findById(memberId);
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @Operation(summary = "전체 회원 조회", description = "모든 회원 정보를 조회한다.")
    public List<MemberResponse> findAllMemberInfo() {
        return memberService.findAll();
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/nickname")
    @Operation(summary = "회원 닉네임 수정", description = "회원 닉네임을 수정한다.")
    public String updateNickname(@Valid @RequestBody String newNickname, HttpServletRequest request) {
        memberService.updateNickname(newNickname, request);
        return "회원 닉네임이 변경되었습니다.";
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/password")
    @Operation(summary = "회원 비밀번호 수정", description = "회원 패스워드를 수정한다.")
    public String updatePassword(@Valid @RequestBody String newPassword, HttpServletRequest request) {
        memberService.updatePassword(newPassword, request);
        return "회원 비밀번호가 변경되었습니다.";
    }

    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping
    @Operation(summary = "회원 삭제", description = "해당 id값과 일치하는 회원 정보를 삭제한다.")
    public String deleteMember(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        memberService.delete(loginRequest, request);
        return "회원 탈퇴가 정상적으로 처리되었습니다.";
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그인 된 회원을 로그 아웃한다.")
    public String logout() {
        memberService.logout();
        return "로그아웃이 정상적으로 처리되었습니다.";
    }
}
