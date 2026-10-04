package com.blog.api;

import com.blog.application.comment.CommentService;
import com.blog.core.comment.Comment;
import java.util.List;

public class CommentsApi {
    private final CommentService service;

    public CommentsApi(CommentService service) {
        this.service = service;
    }

    public Comment add(String id, String body, String userId) {
        return service.add(id, body, userId);
    }

    public List<Comment> byUser(String userId) {
        return service.byUser(userId);
    }
}
