package com.patrick.rxstudy10;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.patrick.rxstudy10.example.FileCacheObservableTransformer;
import com.patrick.rxstudy10.example.TimingObservableTransformer;
import com.patrick.rxstudy10.stock.RetrofitStockServiceFactory;
import com.patrick.rxstudy10.stock.WorldTradingDataService;
import com.patrick.rxstudy10.storio.LocalItemPersistenceHandlingTransformer;
import com.patrick.rxstudy10.storio.StockUpdateTable;
import com.patrick.rxstudy10.storio.StorIOFactory;
import com.pushtorefresh.storio3.sqlite.queries.Query;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.MaybeSource;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import twitter4j.FilterQuery;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import static com.patrick.rxstudy10.LogUtil.log;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.hello_world_salute)
    TextView mHelloText;
    @BindView(R.id.stock_updates_recycler_view)
    RecyclerView mRecyclerView;

    @BindView(R.id.no_data_available)
    TextView mNoDataAvailableView;

    private LinearLayoutManager mLayoutManager;
    private StockDataAdapter mStockDataAdapter;
    private Disposable mDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        RxJavaPlugins.setErrorHandler(ErrorHandler.get());

        Observable.just("Hello! Please use this app responsibly!")
                .subscribe(mHelloText::setText);

        mRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mStockDataAdapter = new StockDataAdapter();
        mRecyclerView.setAdapter(mStockDataAdapter);

        // Retrofit 서비스의 인스턴스 생성
        WorldTradingDataService stockService = new RetrofitStockServiceFactory().create();

        // 쿼리의 파라미터 정의
        List<String> symbols = Arrays.asList("MSFT", "AAPL", "GOOGL");
        String key = BuildConfig.API_KEY;

        // 트위터 구성(authentication setting)
        final Configuration configuration = new ConfigurationBuilder()
                // 디버깅 설정
                .setDebugEnabled(BuildConfig.DEBUG)
                // key 설정
                .setOAuthConsumerKey(BuildConfig.CONSUMER_KEY)
                .setOAuthConsumerSecret(BuildConfig.CONSUMER_SECRET)
                .setOAuthAccessToken(BuildConfig.ACCESS_TOKEN)
                .setOAuthAccessTokenSecret(BuildConfig.ACCESS_TOKEN_SECRET)
                .build();

        // 모니터링할 키워드
        final String[] trackingKeyword = { "Google", "Microsoft", "Apple" };

        // 수신할 유형의 필터 정의
        final FilterQuery filterQuery = new FilterQuery()
                .track(trackingKeyword)
                .language("en", "kr");


        // 주식 정보 Observable 과 TwitterStream Observable 을 merge
        mDisposable = Observable.merge(
                createFinancialStockUpdateObservable(stockService, symbols, key),
                createTweetStockUpdateObservable(configuration, trackingKeyword, filterQuery))
                .subscribeOn(Schedulers.io())
                .doOnError(ErrorHandler.get())
                .compose(addUiErrorHandling())
                .compose(LocalItemPersistenceHandlingTransformer.addLocalItemPersistenceHandling(this))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data ->  {
                    log("New update => " + data.getStockSymbol() + " : " + data.getPrice());
                    mNoDataAvailableView.setVisibility(View.GONE);
                    mStockDataAdapter.add(data);
                    mRecyclerView.smoothScrollToPosition(0);
                }, error -> {
                    log("onError...");
                    if (mStockDataAdapter.getItemCount() == 0)
                        mNoDataAvailableView.setVisibility(View.VISIBLE);
                });

        //fileCaching();
        //timing();
        //timing2();
        //logDebug();
    }

    // 주식 시세 가져오는 Observable 생성
    private Observable<StockUpdate> createFinancialStockUpdateObservable(
            WorldTradingDataService service, List<String> symbols, String key) {
        return Observable.interval(10, TimeUnit.SECONDS)
                .flatMapMaybe(i -> Observable.fromIterable(symbols)
                        .reduce((symbol1, symbol2) -> symbol1 + "," + symbol2))
                .flatMapSingle(symbol -> service.stockQuery(symbol, key))
                .flatMap(result -> Observable.fromIterable(result.getData()))
                .map(StockUpdate::create)
                // Stock symbol 로 grouping
                .groupBy(StockUpdate::getStockSymbol)
                .flatMap(Observable::distinctUntilChanged);
    }

    // 트윗 정보 가져오는 Observable 생성
    private Observable<StockUpdate> createTweetStockUpdateObservable(
            Configuration configuration, String[] trackingKeywords, FilterQuery filterQuery) {
        return observeTwitterStream(configuration, filterQuery)
                // 수신 속도 조절
                .sample(3, TimeUnit.SECONDS)
                .map(StockUpdate::create)
                .flatMapMaybe(skipNotContainsKeyword(trackingKeywords));
    }

    // Observable 흐름에서 메인 스레드 에러 표시(Toast)
    private Observable<StockUpdate> addUiErrorHandling(Observable<StockUpdate> observable) {
        return observable.observeOn(AndroidSchedulers.mainThread())
                .doOnError(this::showToastErrorNotificationMethod)
                .observeOn(Schedulers.io());
    }

    // Observable 흐름에서 메인 스레드 에러 표시(Toast) => ObservableTransformer 로 감쌈
    @NonNull
    private ObservableTransformer<StockUpdate, StockUpdate> addUiErrorHandling() {
        return this::addUiErrorHandling;
    }

    private Function<StockUpdate, MaybeSource<? extends StockUpdate>> skipNotContainsKeyword(String[] trackingKeyword) {
        return update -> Observable.fromArray(trackingKeyword)
                .filter(keyword -> update.getTwitterStatus()
                        .toLowerCase().contains(keyword.toLowerCase()))
                .map(keyword -> update)
                .firstElement();
    }

    private void showToastErrorNotificationMethod(Throwable error) {
        Toast.makeText(this,
                "We couldn't reach internet - falling back to local data",
                Toast.LENGTH_SHORT).show();
    }

    private Predicate<StockUpdate> containsAnyOfKeywords(String[] trackingKeyword) {
        return stockUpdate -> {
            for (String keyword : trackingKeyword) {
                if (stockUpdate.getTwitterStatus().contains(keyword)) {
                    return true;
                }
            }
            return false;
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mDisposable != null && !mDisposable.isDisposed()) {
            mDisposable.dispose();
        }
    }

    // Observable 로 TwitterStream status 출력
    private Observable<Status> observeTwitterStream(
            Configuration configuration, FilterQuery filterQuery) {
        return Observable.create(emitter -> {
            // TwitterStream 객체 초기화
            final TwitterStream twitterStream = new TwitterStreamFactory(configuration).getInstance();

            // Observable 이 제거될 때 TwitterStream 을 제거하기 위한 콜백 추가
            emitter.setCancellable(() -> twitterStream.shutdown());

            StatusListener listener = new StatusListener() {
                @Override
                public void onStatus(Status status) {
                    // Observable 에 상태 업데이트 추가
                    emitter.onNext(status);
                    log(status.getUser().getName() + " : " + status.getText());
                }

                @Override
                public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) { }

                @Override
                public void onTrackLimitationNotice(int numberOfLimitedStatuses) { }

                @Override
                public void onScrubGeo(long userId, long upToStatusId) { }

                @Override
                public void onStallWarning(StallWarning warning) { }

                @Override
                public void onException(Exception ex) {
                    // 예외 처리 추가
                    emitter.onError(ex);
                    log(ex);
                }
            };

            // TwitterStream 에 리스너 추가
            twitterStream.addListener(listener);
            // Status 얻기 위한 필터 추가
            twitterStream.filter(filterQuery);
        });
    }

    // 파일 캐싱 예
    private void fileCaching() {
        Observable.just("1")
                .compose(FileCacheObservableTransformer.cacheToLocalFileNamed("test", this))
                .subscribe(LogUtil::log );
    }

    // 경과 시간 측정 예
    private void timing() {
        Observable.interval(4, TimeUnit.SECONDS)
                .take(5)
                .compose(TimingObservableTransformer.timeItems(second -> {
                    log("Second passed since the start : " + second);
                }))
                .subscribe(item -> log(item.toString()));
    }

    // 같은 변수를 참조하는 다른 구독
    private void timing2() {
        final Observable observable = Observable.interval(4, TimeUnit.SECONDS)
                .take(5)
                .compose(TimingObservableTransformer.timeItems(second ->
                        log("Second passed since the start : " + second)));

        observable.subscribe(item -> log(item.toString()));
        observable.subscribe(item -> log(item.toString()));
    }

    private void logDebug() {
        Observable.interval(2, TimeUnit.SECONDS)
                .take(5)
                .compose(LoggerTransformer.debugLog("afterInterval"))
                .observeOn(AndroidSchedulers.mainThread())
                .compose(LoggerTransformer.debugLog("afterFlatMap"))
                .subscribe();
    }
}
