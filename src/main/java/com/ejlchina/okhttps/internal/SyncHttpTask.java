package com.ejlchina.okhttps.internal;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import com.ejlchina.okhttps.Cancelable;
import com.ejlchina.okhttps.HttpResult;
import com.ejlchina.okhttps.HttpTask;
import com.ejlchina.okhttps.HttpResult.State;

import okhttp3.Call;


/**
 * 同步 Http 请求任务
 *  
 * @author Troy.Zhou
 * 
 */
public class SyncHttpTask extends HttpTask<SyncHttpTask> {

	public SyncHttpTask(HttpClient client, String url) {
		super(client, url);
	}
	
    /**
     * 发起 GET 请求
     * @return 请求结果  
     */
    public HttpResult get() {
        return request("GET");
    }

    /**
     * 发起 POST 请求
     * @return 请求结果  
     */
    public HttpResult post() {
        return request("POST");
    }

    /**
     * 发起 PUT 请求
     * @return 请求结果  
     */
    public HttpResult put() {
        return request("PUT");
    }
    
    /**
     * 发起 DELETE 请求
     * @return 请求结果  
     */
    public HttpResult delete() {
        return request("DELETE");
    }

    static class SyncCall implements Cancelable {

		Call call;
		boolean done = false;
		boolean canceled = false;

		@Override
		public synchronized boolean cancel() {
			if (done) {
				return false;
			}
			if (call != null) {
				call.cancel();
			}
			canceled = true;
			return true;
		}

	}

    private HttpResult request(String method) {
    	RealHttpResult result = new RealHttpResult(this, httpClient.getExecutor());
		SyncCall syncCall = new SyncCall();
		// 注册标签任务
		registeTagTask(syncCall);
		CountDownLatch latch = new CountDownLatch(1);
    	httpClient.preprocess(this, () -> {
			synchronized (syncCall) {
				if (syncCall.canceled) {
					result.exception(State.CANCELED, null);
					latch.countDown();
					return;
				}
				syncCall.call = prepareCall(method);
			}
            try {
				result.response(syncCall.call.execute());
				syncCall.done = true;
            } catch (IOException e) {
				result.exception(toState(e, true), e);
            } finally {
				latch.countDown();
			}
    	});
		if (result.getState() == null) {
			timeoutAwait(latch);
		}
		// 移除标签任务
		removeTagTask();
		IOException e = result.getError();
		State state = result.getState();
    	if (e != null && state != State.CANCELED
    			&& !nothrow) {
    		throw new HttpException(state, "执行异常", e);
    	}
        return result;
    }

}
