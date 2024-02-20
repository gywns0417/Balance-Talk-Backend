package balancetalk.module.vote.dto;

import balancetalk.module.member.domain.Member;
import balancetalk.module.post.domain.BalanceOption;
import balancetalk.module.vote.domain.Vote;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class VoteRequest {
    @Schema(description = "투표한 선택지 id", example = "23")
    private Long selectedOptionId;

    @Schema(description = "회원 id", example = "1")
    private Long memberId;

    public Vote toEntity(BalanceOption balanceOption, Member member) {
        return Vote.builder()
                .balanceOption(balanceOption)
                .member(member)
                .build();
    }
}