package balancetalk.module.post.dto;

import balancetalk.module.file.domain.File;
import balancetalk.module.member.domain.Member;
import balancetalk.module.post.domain.BalanceOption;
import balancetalk.module.post.domain.Post;
import balancetalk.module.post.domain.PostCategory;
import balancetalk.module.post.domain.PostTag;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class PostRequest {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "게시글 제목", example = "게시글 제목")
    private String title;

    @NotNull
    @Future
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    @Schema(description = "투료 종료 기한", example = "2024-12-25T15:30:00", type = "string")
    private LocalDateTime deadline;

    @NotNull
    @Schema(description = "게시글 카테고리", example = "CASUAL")
    private PostCategory category;

    private List<BalanceOptionRequest> balanceOptions;

    private List<PostTagDto> tags;

    public Post toEntity(Member member, List<File> images) {
        return Post.builder()
                .member(member)
                .title(title)
                .deadline(deadline)
                .category(category)
                .options(getBalanceOptions(images))
                .postTags(getPostTags())
                .build();
    }

    private List<BalanceOption> getBalanceOptions(List<File> images) {
        if (images.isEmpty()) {
            return balanceOptions.stream()
                    .map(balanceOption -> balanceOption.toEntity(null))
                    .collect(Collectors.toList());
        } else {
            Map<String, File> fileNameToFileMap = images.stream()
                    .collect(Collectors.toMap(File::getStoredName, Function.identity()));

            return balanceOptions.stream()
                    .map(balanceOption ->
                            balanceOption.toEntity(
                                    fileNameToFileMap.getOrDefault(balanceOption.getStoredImageName(), null)))
                    .collect(Collectors.toList());
        }
    }

    private List<PostTag> getPostTags() {
        return tags.stream().map(PostTagDto::toEntity).collect(Collectors.toList());
    }
}
