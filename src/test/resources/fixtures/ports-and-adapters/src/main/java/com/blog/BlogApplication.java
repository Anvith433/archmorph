package com.blog;

import com.blog.api.CommentsApi;
import com.blog.api.CurrentUserApi;
import com.blog.api.UsersApi;
import com.blog.application.comment.CommentService;
import com.blog.application.user.UserService;
import com.blog.graphql.MeDatafetcher;
import com.blog.infrastructure.repository.InMemoryCommentRepository;
import com.blog.infrastructure.repository.InMemoryUserRepository;
import com.blog.infrastructure.service.DefaultJwtService;

public class BlogApplication {

    public static void main(String[] args) {
        UserService users = new UserService(new InMemoryUserRepository(), new DefaultJwtService());
        CommentService comments = new CommentService(new InMemoryCommentRepository());
        UsersApi usersApi = new UsersApi(users);
        CurrentUserApi currentUser = new CurrentUserApi(users);
        CommentsApi commentsApi = new CommentsApi(comments);
        MeDatafetcher me = new MeDatafetcher(users);
        usersApi.register("1", "ada");
        commentsApi.add("c1", "hello", "1");
        System.out.println(currentUser.me("1").getUsername() + " " + me.me("1").getId());
    }
}
