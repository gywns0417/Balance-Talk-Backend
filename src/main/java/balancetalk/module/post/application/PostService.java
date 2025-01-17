package balancetalk.module.post.application;

import static balancetalk.global.exception.ErrorCode.*;
import static balancetalk.global.utils.SecurityUtils.getCurrentMember;

import balancetalk.global.exception.BalanceTalkException;
import balancetalk.global.redis.application.RedisService;
import balancetalk.module.bookmark.domain.BookmarkRepository;
import balancetalk.module.file.domain.File;
import balancetalk.module.file.domain.FileRepository;
import balancetalk.module.member.domain.Member;
import balancetalk.module.member.domain.MemberRepository;
import balancetalk.module.member.domain.Role;
import balancetalk.module.member.dto.MyPageResponse;
import balancetalk.module.post.domain.*;
import balancetalk.module.post.dto.BalanceOptionRequest;
import balancetalk.module.post.dto.PostRequest;
import balancetalk.module.post.dto.PostResponse;
import balancetalk.module.report.domain.Report;
import balancetalk.module.report.domain.ReportRepository;
import balancetalk.module.report.dto.ReportRequest;
import balancetalk.module.post.dto.*;
import balancetalk.module.vote.domain.VoteRepository;
import java.util.stream.Collectors;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {
    private static final int BEST_POSTS_SIZE = 5;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PostLikeRepository postLikeRepository;
    private final FileRepository fileRepository;
    private final VoteRepository voteRepository;
    private final BookmarkRepository bookmarkRepository;
    private final RedisService redisService;
    private final ReportRepository reportRepository;

    public PostResponse save(final PostRequest request) {
        Member writer = getCurrentMember(memberRepository);
        if (redisService.getValues(writer.getEmail()) == null) {
            throw new BalanceTalkException(FORBIDDEN_POST_CREATE);
        }

        List<File> images = getImages(request);
        Post post = request.toEntity(writer, images);

        List<BalanceOption> options = post.getOptions();
        for (BalanceOption option : options) {
            option.addPost(post);
        }
        List<PostTag> postTags = post.getPostTags();
        for (PostTag postTag : postTags) {
            postTag.addPost(post);
        }

        return PostResponse.fromEntity(postRepository.save(post), writer, false, false, false);
    }

    private List<File> getImages(PostRequest postRequest) {
        List<BalanceOptionRequest> balanceOptions = postRequest.getBalanceOptions();
        return balanceOptions.stream()
                .filter(optionDto -> optionDto.getStoredImageName() != null && !optionDto.getStoredImageName().isEmpty())
                .map(optionDto -> fileRepository.findByStoredName(optionDto.getStoredImageName())
                        .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_FILE)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> findAll(String token, Pageable pageable) {
        Page<Post> posts = postRepository.findAll(pageable);

        if (token == null) {
            return posts.map(post -> PostResponse.fromEntity(post, null, false, false, false));
        }
        Member member = getCurrentMember(memberRepository);

        return posts.map(post -> PostResponse.fromEntity(post,
                member,
                member.hasLiked(post),
                member.hasBookmarked(post),
                member.hasVoted(post)));
    }

    @Transactional
    public PostResponse findById(Long postId, String token) {
        Post post = getCurrentPost(postId);

        if (token == null) {
            post.increaseViews();
            return PostResponse.fromEntity(post, null, false, false, false);
        }

        Member member = getCurrentMember(memberRepository);

        if (member.getRole() == Role.USER) {
             post.increaseViews();
        }
        return PostResponse.fromEntity(post, member, member.hasLiked(post), member.hasBookmarked(post),
                member.hasVoted(post));
    }

    @Transactional(readOnly = true)
    public Page<MyPageResponse> findAllByCurrentMember(Pageable pageable) {
        Member currentMember = getCurrentMember(memberRepository);

        return postRepository.findAllByMemberId(currentMember.getId(), pageable)
                .map(MyPageResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<MyPageResponse> findAllVotedByCurrentMember(Pageable pageable) {
        Member currentMember = getCurrentMember(memberRepository);

        return voteRepository.findAllByMemberId(currentMember.getId(), pageable)
                .map(vote -> MyPageResponse.fromEntity(vote, vote.getBalanceOption().getPost()));
    }

    @Transactional(readOnly = true)
    public Page<MyPageResponse> findAllBookmarkedByCurrentMember(Pageable pageable) {
        Member currentMember = getCurrentMember(memberRepository);

        return bookmarkRepository.findAllByMemberId(currentMember.getId(), pageable)
                .map(MyPageResponse::fromEntity);
    }


    @Transactional
    public void deleteById(Long postId) {
        Post post = getCurrentPost(postId);
        Member member = getCurrentMember(memberRepository);
        if (!post.getMember().getEmail().equals(member.getEmail())) {
            throw new BalanceTalkException(FORBIDDEN_POST_DELETE);
        }
        postRepository.deleteById(postId);
    }

    public Long likePost(Long postId) {
        Post post = getCurrentPost(postId);
        Member member = getCurrentMember(memberRepository);
        if (postLikeRepository.existsByMemberAndPost(member, post)) {
            throw new BalanceTalkException(ALREADY_LIKE_POST);
        }


        PostLike postLike = PostLike.builder()
                .post(post)
                .member(member)
                .build();
        postLikeRepository.save(postLike);

        return post.getId();
    }

    public void cancelLikePost(Long postId) {
        Post post = getCurrentPost(postId);
        Member member = getCurrentMember(memberRepository);
        if (notExistsPostLikeBy(member, post)) {
            throw new BalanceTalkException(NOT_FOUND_POST_LIKE);
        }
        postLikeRepository.deleteByMemberAndPost(member, post);
    }

    private boolean notExistsPostLikeBy(Member member, Post post) {
        return !postLikeRepository.existsByMemberAndPost(member, post);
    }
    private Post getCurrentPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_POST));
    }

    @Transactional(readOnly = true)
    public List<PostResponse> findBestPosts(String token) {
        PageRequest limit = PageRequest.of(0, BEST_POSTS_SIZE);
        List<Post> posts = postRepository.findBestPosts(limit);

        if (token == null) {
            return posts.stream()
                    .map(post -> PostResponse.fromEntity(post, null, false, false, false))
                    .collect(Collectors.toList());
        }

        Member member = getCurrentMember(memberRepository);

        return posts.stream()
                .map(post -> PostResponse.fromEntity(post,
                        member,
                        member.hasLiked(post),
                        member.hasBookmarked(post),
                        member.hasVoted(post)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PostResponse> findPostsByTitle(String token, String keyword) {
        List<Post> posts = postRepository.findByTitleContaining(keyword);
        if (token == null) {
            return posts.stream()
                    .map(post -> PostResponse.fromEntity(post, null, false, false, false))
                    .collect(Collectors.toList());
        }
        Member member = getCurrentMember(memberRepository);
        return posts.stream()
                .map(post -> PostResponse.fromEntity(post,
                        member,
                        member.hasLiked(post),
                        member.hasBookmarked(post),
                        member.hasVoted(post)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PostResponse> findPostsByTag(String token, String tagName) {
        List<Post> posts = postRepository.findByPostTagsContaining(tagName);
        if (token == null) {
            return posts.stream()
                    .map(post -> PostResponse.fromEntity(post, null, false, false, false))
                    .collect(Collectors.toList());
        }
        Member member = getCurrentMember(memberRepository);
        return posts.stream()
                .map(post -> PostResponse.fromEntity(post,
                        member,
                        member.hasLiked(post),
                        member.hasBookmarked(post),
                        member.hasVoted(post)))
                .collect(Collectors.toList());
    }

    public void reportPost(Long postId, ReportRequest reportRequest) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BalanceTalkException(NOT_FOUND_POST));
        Member member = getCurrentMember(memberRepository);
//        if (post.getMember().equals(member)) {
//            throw new BalanceTalkException(FORBIDDEN_OWN_REPORT);
//        }
        Report report = Report.builder()
                .content(reportRequest.getDescription())
                .reporter(member)
                .post(post)
                .category(reportRequest.getCategory())
                .build();
        reportRepository.save(report);
    }
}
