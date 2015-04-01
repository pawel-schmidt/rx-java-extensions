package com.appunite.rx.example.model.api;

import com.appunite.rx.example.model.model.PostWithBody;
import com.appunite.rx.example.model.model.PostsIdsResponse;
import com.appunite.rx.example.model.model.PostsResponse;
import com.appunite.rx.example.model.model.PostsResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;
import rx.Observable;

public interface GuestbookService {

    @GET("/v1/posts?limit=50&fields=next_token%2Cposts(id%2Cname)&prettyPrint=false")
    @Nonnull
    Observable<PostsResponse> listPosts(@Query("next_token") @Nullable String nextToken);

    @GET("/v1/posts_ids?limit=50&prettyPrint=false")
    @Nonnull
    Observable<PostsIdsResponse> listPostsIds(@Query("next_token") @Nullable String nextToken);

    @Nonnull
    @GET("/v1/posts/{postId}?prettyPrint=false")
    Observable<PostWithBody> getPost(@Path("postId") @Nonnull String id);
}
