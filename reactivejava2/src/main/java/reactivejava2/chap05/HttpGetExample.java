package reactivejava2.chap05;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import reactivejava2.common.Log;
import reactivejava2.common.OkHttpHelper;

/**
 * 
 * 자바의 http get 호출(콜백 호출)
 *
 */
public class HttpGetExample {
    public void get() {
        Request request = new Request.Builder()
                .url(OkHttpHelper.GITHUB_ZEN_URL)
                .build();
        
        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i(response.body().string());
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
        });
    }
    
    public static void main(String[] args) {
        HttpGetExample demo = new HttpGetExample();
        demo.get();
    }
}
