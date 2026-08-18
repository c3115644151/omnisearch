package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchPageBatch;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CaptchaCapableDataSource extends DataSource {
    CompletableFuture<List<SearchHit>> submitCaptcha(SearchQuery originalQuery, CaptchaContext captcha, String answer);

    CompletableFuture<SearchPageBatch> submitCaptchaForSearchPage(SearchQuery originalQuery, String pageUrl, CaptchaContext captcha, String answer);

    CompletableFuture<ItemPage> submitCaptchaForPage(String pageId, CaptchaContext captcha, String answer);
}
