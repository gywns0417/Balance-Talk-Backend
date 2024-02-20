package balancetalk.module.comment.dto;

import balancetalk.module.comment.domain.Comment;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class CommentResponse {

    @Schema(description = "댓글 id", example = "1345")
    private Long id;

    @Schema(description = "댓글 내용", example = "댓글 내용...")
    private String content;

    @Schema(description = "댓글 작성자", example = "닉네임")
    private String memberName;

    @Schema(description = "해당 댓글에 맞는 게시글 id", example = "1")
    private Long postId;
  
    @Schema(description = "해당 댓글에 맞는 선택지 id", example = "23")
    private Long selectedOptionId;

    @Schema(description = "댓글 추천 수", example = "24")
    private int likeCount;

    @Schema(description = "댓글 생성 날짜")
    private LocalDateTime createdAt;

    @Schema(description = "댓글 수정 날짜")
    private LocalDateTime lastModifiedAt;

    public static CommentResponse fromEntity(Comment comment, Long balanceOptionId) {


        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .memberName(comment.getMember().getNickname())
                .postId(comment.getPost().getId())
                .selectedOptionId(balanceOptionId)
                // .likeCount(comment.getLikes().size()) //TODO: likeCount가 NULL이라 Response 어려움
                .createdAt(comment.getCreatedAt())
                .lastModifiedAt(comment.getLastModifiedAt())
                .build();
    }
}