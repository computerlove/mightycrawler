package no.bekk.bekkopen.mightycrawler;

import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class DownloadManager extends Thread {

	private HttpClient httpClient;
	private PoolingClientConnectionManager cm;

	private ExecutorService workerService = null;
	private CompletionService<Resource> completionService = null;
	
	private Configuration c;
	private Report r;
	
	private Parser p = null;
	private URLManager u = null;
	private Storage s = null;

	private long startTime = 0;

	public int urlsVisited;
	public int urlsDownloaded;
	public int recursionLevel;
	public int runningFor;

	static final Logger log = LoggerFactory.getLogger(DownloadManager.class);

	public DownloadManager(Configuration c) {
		this.c = c;
		this.s = new Storage(c);
		
		r = new Report(c);
		u = new URLManager(c.crawlFilter);
		p = new Parser(c.linkFilter);

        httpClient = createHttpClient(c);
		
		workerService = Executors.newFixedThreadPool(c.downloadThreads);
		completionService = new ExecutorCompletionService<>(workerService);

		startTime = System.currentTimeMillis();
	}

    public void addToQueue(Resource res) {
		completionService.submit(new DownloadWorker(httpClient, res, c));
		log.debug("Added downloading of {} to queue.", res.url);
	}

	public void addToQueue(Collection<String> URLs, int recursionLevel) {
        URLs.parallelStream()
                .map(url -> new Resource(url, recursionLevel))
                .forEach(this::addToQueue);
	}
	
	public int getQueueSize() {
		return ((ThreadPoolExecutor) workerService).getQueue().size();
	}
	
	public boolean isTerminated() {
		return workerService.isTerminated();
	}
	
	public void run() {
		Future<Resource> result  = null;
		while (!workerService.isShutdown()) {
			try {
				result = completionService.poll(c.crawlerTimeout, TimeUnit.SECONDS);
				if (result != null) {
					Resource res = result.get();
					log.info("Request for: " + res.url + " done.");
					log.debug("Recursion level: {}", res.recursionLevel);
					processResource(res);
				}
			} catch (RejectedExecutionException ree) {
				log.error("Could not accept new download task. Error: " + ree);
			} catch (ExecutionException ee) {
				log.error("Exception thrown in download worker: " + ee);
			} catch (Exception e) {
				log.error("Error waiting for download worker: " + e);
			}
			
			if (shouldStop(result)) {
				List<Runnable> queuedTasks = workerService.shutdownNow();
				log.debug("Cancelling " + queuedTasks.size() + " queued visits.");				
			}
		}
		
		cm.shutdown();
		log.debug("DownloadManager has shut down.");
		
		r.createReport();
		r.shutDown();		
	}
	

	public void processResource(Resource res) {
		urlsVisited++;
		r.registerVisit(res);

		if (res.isDownloaded) {
			urlsDownloaded++;
			if (c.extractFilter.letsThrough(res.contentType)) {
				res.urls = p.extractLinks(res);
				Collection<String> newUrls = u.updateQueues(res);
				addToQueue(newUrls, res.recursionLevel + 1);
				r.registerOutboundLinks(res.url, newUrls);
			}

			if (c.storeFilter.letsThrough(res.contentType)) {
				s.save(res.url, res.content);
			}
		}

		if (res.wasRedirect) {
			// treat the redirect target as a newly discovered url
			Collection<String> newUrls = u.updateQueues(res);
			addToQueue(newUrls, res.recursionLevel);
		}
				
		recursionLevel = res.recursionLevel;
	}
	
	public boolean shouldStop(Future<Resource> result) {
		if (result == null) {
			log.info("Stopping the crawler since no visits have completed within " + c.crawlerTimeout + " seconds.");
			return true;
		}
		
		if (urlsVisited == c.maxVisits) {
			log.info("Stopping, reached maxVisits (" + c.maxVisits + ")");
			return true;
		}
		if (urlsDownloaded == c.maxDownloads) {
			log.info("Stopping, reached maxDownloads (" + c.maxDownloads + ")");
			return true;
		}
		if (recursionLevel > c.maxRecursion) {
			log.info("Stopping, reached maxRecursion (" + c.maxRecursion + ")");
			return true;
		}
		runningFor = (int) ((System.currentTimeMillis() - startTime) / (1000 * 60));
		if (runningFor >= c.maxTime) {
			log.info("Stopping, reached maxTime (" + c.maxTime + " minutes)");
			return true;
		}

		log.debug("Running for " + runningFor + " minutes");
		log.debug("Visited resources: " + urlsVisited);
		log.debug("Visit queue size: " + getQueueSize());
		log.debug("Downloaded resources: " + urlsDownloaded);
		
		return false;
	}

    private HttpClient createHttpClient(Configuration c) {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(params, c.responseTimeout * 1000);
        HttpProtocolParams.setUserAgent(params, c.userAgent);
        HttpProtocolParams.setContentCharset(params, "UTF-8");

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", c.httpPort, PlainSocketFactory.getSocketFactory()));
        schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));

        cm = new PoolingClientConnectionManager(schemeRegistry);
        cm.setMaxTotal(c.downloadThreads);
        cm.setDefaultMaxPerRoute(c.downloadThreads);

        DefaultHttpClient client = new DefaultHttpClient(cm, params);
        // TODO: enable compression support (zip, deflate)
        client.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS, false);

        if (c.useCookies) {
            client.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
        } else {
            client.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        }
        return client;
    }
}
