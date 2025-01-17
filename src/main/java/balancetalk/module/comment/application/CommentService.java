package balancetalk.module.comment.application;

import balancetalk.global.exception.BalanceTalkException;
import balancetalk.global.exception.ErrorCode;
import balancetalk.module.comment.domain.Comment;
import balancetalk.module.comment.domain.CommentLike;
import balancetalk.module.comment.domain.CommentLikeRepository;
import balancetalk.module.comment.domain.CommentRepository;
import balancetalk.module.comment.dto.CommentRequest;
import balancetalk.module.comment.dto.CommentResponse;
import balancetalk.module.comment.dto.ReplyCreateRequest;
import balancetalk.module.comment.dto.ReplyResponse;
import balancetalk.module.member.domain.Member;
import balancetalk.module.member.domain.MemberRepository;
import balancetalk.module.post.domain.BalanceOption;
import balancetalk.module.post.domain.Post;
import balancetalk.module.post.domain.PostRepository;
import balancetalk.module.report.domain.Report;
import balancetalk.module.report.domain.ReportRepository;
import balancetalk.module.report.dto.ReportRequest;
import balancetalk.module.member.dto.MyPageResponse;
import balancetalk.module.vote.domain.Vote;
import balancetalk.module.vote.domain.VoteRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.stream.Collectors;

import static balancetalk.global.exception.ErrorCode.*;
import static balancetalk.global.utils.SecurityUtils.getCurrentMember;

@Service
@Transactional
@RequiredArgsConstructor
public class CommentService {

    private static final int BEST_COMMENTS_SIZE = 3;
    private static final int MIN_COUNT_FOR_BEST_COMMENT = 15;

    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final VoteRepository voteRepository;
    private final ReportRepository reportRepository;

    @Value("${comments.max-depth}")
    private int maxDepth;

    public Comment createComment(CommentRequest request, Long postId) {
        Member member = getCurrentMember(memberRepository);
        Post post = validatePostId(postId);
        BalanceOption balanceOption = validateBalanceOptionId(request, post);
        voteRepository.findByMemberIdAndBalanceOption_PostId(member.getId(), postId)
                .orElseThrow(() -> new BalanceTalkException(ErrorCode.NOT_FOUND_VOTE));

        Comment comment = request.toEntity(member, post);
        return commentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> findAllComments(Long postId, String token, Pageable pageable) {
        validatePostId(postId);

        Page<Comment> comments = commentRepository.findAllByPostIdAndParentIsNull(postId, pageable);

        return comments.map(comment -> {
            Optional<Vote> voteForComment = voteRepository.findByMemberIdAndBalanceOption_PostId(
                    comment.getMember().getId(), postId);

            Long balanceOptionId = voteForComment.map(Vote::getBalanceOption).map(BalanceOption::getId)
                    .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_BALANCE_OPTION));

            if (token == null) {
                return CommentResponse.fromEntity(comment, balanceOptionId, false);
            } else {
                Member member = getCurrentMember(memberRepository);
                return CommentResponse.fromEntity(comment, balanceOptionId, member.hasLikedComment(comment));
            }
        });
    }

    @Transactional(readOnly = true)
    public Page<MyPageResponse> findAllByCurrentMember(Pageable pageable) {
        Member currentMember = getCurrentMember(memberRepository);

        return commentRepository.findAllByMemberEmail(currentMember.getEmail(), pageable)
                .map(MyPageResponse::fromEntity);
    }

    public Comment updateComment(Long commentId, Long postId, String content) {
        Comment comment = validateCommentId(commentId);
        validatePostId(postId);

        if (!getCurrentMember(memberRepository).equals(comment.getMember())) {
            throw new BalanceTalkException(FORBIDDEN_COMMENT_MODIFY);
        }

        if (!comment.getPost().getId().equals(postId)) {
            throw new BalanceTalkException(NOT_FOUND_COMMENT_AT_THAT_POST);
        }

        comment.updateContent(content);
        return comment;
    }

    public void deleteComment(Long commentId, Long postId) {
        Comment comment = validateCommentId(commentId);
        Member commentMember = commentRepository.findById(commentId).get().getMember();
        validatePostId(postId);

        if (!getCurrentMember(memberRepository).equals(commentMember)) {
            throw new BalanceTalkException(FORBIDDEN_COMMENT_DELETE);
        }

        if (!comment.getPost().getId().equals(postId)) {
            throw new BalanceTalkException(NOT_FOUND_COMMENT_AT_THAT_POST);
        }

        commentRepository.deleteById(commentId);
    }

    @Transactional
    public Comment createReply(Long postId, Long commentId, ReplyCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_POST));

        Comment parentComment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_COMMENT));

        Member member = getCurrentMember(memberRepository);

        // 부모 댓글과 연결된 게시글이 맞는지 확인
        if (!parentComment.getPost().equals(post)) {
            throw new BalanceTalkException(NOT_FOUND_PARENT_COMMENT);
        }

        validateDepth(parentComment);

        Comment reply = request.toEntity(member, post, parentComment);
        return commentRepository.save(reply);
    }

    public List<ReplyResponse> findAllReplies(Long postId, Long parentId, String token) {
        validatePostId(postId);

        List<Comment> replies = commentRepository.findAllByPostIdAndParentId(postId, parentId);

        if (token == null) {
            return replies.stream()
                    .map(reply -> ReplyResponse.fromEntity(reply, null, false))
                    .collect(Collectors.toList());
        } else {
            Member member = getCurrentMember(memberRepository);

            return replies.stream()
                    .map(reply -> {
                        boolean myLike = member.hasLikedComment(reply);

                        Optional<Vote> voteForComment = voteRepository.findByMemberIdAndBalanceOption_PostId(
                                reply.getMember().getId(), postId);

                        Long balanceOptionId = voteForComment.map(Vote::getBalanceOption).map(BalanceOption::getId)
                                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_BALANCE_OPTION));

                        return ReplyResponse.fromEntity(reply, balanceOptionId, myLike);
                    })
                    .collect(Collectors.toList());
        }
    }

    private Post validatePostId(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_POST));
    }

    private BalanceOption validateBalanceOptionId(CommentRequest request, Post post) {
        return post.getOptions().stream()
                .filter(option -> option.getId().equals(request.getSelectedOptionId()))
                .findFirst()
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_BALANCE_OPTION));
    }

    private Comment validateCommentId(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_COMMENT));
    }

    private void validateDepth(Comment parentComment) {
        int depth = calculateDepth(parentComment);
        if (depth >= maxDepth) {
            throw new BalanceTalkException(EXCEED_MAX_DEPTH);
        }
    }

    private int calculateDepth(Comment comment) {
        int depth = 0;
        while (comment.getParent() != null) {
            depth++;
            comment = comment.getParent();
        }
        return depth;
    }

    public Long likeComment(Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_COMMENT));
        Member member = getCurrentMember(memberRepository);

        if (commentLikeRepository.existsByMemberAndComment(member, comment)) {
            throw new BalanceTalkException(ErrorCode.ALREADY_LIKE_COMMENT);
        }

        CommentLike commentLike = CommentLike.builder()
                .comment(comment)
                .member(member)
                .build();
        commentLikeRepository.save(commentLike);

        return comment.getId();
    }

    public void cancelLikeComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_COMMENT));
        Member member = getCurrentMember(memberRepository);

        commentLikeRepository.deleteByMemberAndComment(member, comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> findBestComments(Long postId, String token) {
        Post post = validatePostId(postId);
        List<BalanceOption> options = post.getOptions();

        List<CommentResponse> responses = new ArrayList<>();
        for (BalanceOption option : options) {
            List<Long> memberIdsBySelectedOptionId =
                    memberRepository.findMemberIdsBySelectedOptionId(option.getId());

            List<Comment> bestComments = commentRepository.findBestCommentsByPostId(postId,
                    memberIdsBySelectedOptionId, MIN_COUNT_FOR_BEST_COMMENT, PageRequest.of(0, BEST_COMMENTS_SIZE));

            if (token == null) {
                responses.addAll(bestComments.stream()
                        .map(comment -> CommentResponse.fromEntity(comment, option.getId(), false)).toList());
            } else {
                Member member = getCurrentMember(memberRepository);
                responses.addAll(bestComments.stream()
                        .map(comment ->
                                CommentResponse.fromEntity(comment, option.getId(), member.hasLikedComment(comment)))
                        .toList());
            }
        }
        return responses;
    }

    public void reportComment(Long postId, Long commentId, ReportRequest reportRequest) {
        Comment comment = validateCommentId(commentId);
        Member member = getCurrentMember(memberRepository);
//        if (comment.getMember().equals(member)) {
//            throw new BalanceTalkException(FORBIDDEN_OWN_REPORT);
//        }

        if (!comment.getPost().getId().equals(postId)) {
            throw new BalanceTalkException(NOT_FOUND_COMMENT_AT_THAT_POST);
        }
        Report report = Report.builder()
                .content(reportRequest.getDescription())
                .reporter(member)
                .comment(comment)
                .category(reportRequest.getCategory())
                .build();
        reportRepository.save(report);
    }
}
