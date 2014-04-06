package no.bekk.bekkopen.mightycrawler;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class URLManager {

    private static final Pattern AMP_PATTERN = Pattern.compile("&amp;");

	private LinkedHashSet<String> urlsToVisit = new LinkedHashSet<>();
	private LinkedHashSet<String> urlsVisited = new LinkedHashSet<>();
	
	private IncludeExcludeFilter crawlFilter;
	
	static final Logger log = LoggerFactory.getLogger(URLManager.class);

	public URLManager(IncludeExcludeFilter f) {
		crawlFilter = f;
	}

	public Collection<String> updateQueues(Resource res) {
		markURLAsVisited(res.url);

		Collection<String> newURLs = res.urls;
		newURLs = normalizeURLs(newURLs, res.url);
		newURLs = removeKnownURLs(newURLs);
		newURLs = filterURLs(newURLs);

		addNewURLs(newURLs);
		log.info("Page: " + res.url + ", urls added to queue: " + newURLs.size());
		
		log.debug("Urls visited: {}", urlsVisited.size());
		log.debug("Urls to visit: {}", urlsToVisit.size());
		return newURLs;
	}
	
	public Collection<String> removeKnownURLs(Collection<String> newUrls) {
		newUrls.removeAll(urlsVisited);
		newUrls.removeAll(urlsToVisit);
		return newUrls;
	}

	public void markURLAsVisited(String url) {
		urlsToVisit.remove(url);
		urlsVisited.add(url);
	}

	public void addNewURLs(Collection<String> newUrls) {
		urlsToVisit.addAll(newUrls);
	}

	public Collection<String> filterURLs(Collection<String> urlList) {
        log.debug("Pre filtering: {}", urlList);

        Collection<String> filteredURLs = urlList.parallelStream()
                .filter(crawlFilter)
                .collect(Collectors.<String>toList());

		log.debug("Post filtering: {}", filteredURLs);
		return filteredURLs;
	}

	public Collection<String> normalizeURLs(Collection<String> urlList, String baseUrl) {
		return urlList.parallelStream()
                .map(url -> normalize(url, baseUrl))
                .collect(Collectors.<String>toList());
	}

	public String normalize(String url, String baseUrl) {
		String normalizedUrl = url.trim();
		normalizedUrl = StringUtils.substringBeforeLast(normalizedUrl, "#");
		normalizedUrl = StringUtils.substringBeforeLast(normalizedUrl, ";jsessionid");
		
		String absoluteURL = "";
		try {
			URI base = new URI(baseUrl);
			URI fullUrl = base.resolve(normalizedUrl);
			absoluteURL = fullUrl.toString();
			String query = fullUrl.getRawQuery();
			if (query != null) {
				String beforeQuery = StringUtils.substringBefore(absoluteURL, query);
				absoluteURL = beforeQuery + AMP_PATTERN.matcher(query).replaceAll("&");
			}
		} catch (URISyntaxException e) {
			log.error("Normalization error. Skipping URL. Base url: " + baseUrl + " violates URL standards (RFC 2396).");
		} catch (IllegalArgumentException e) {
			log.warn("Normalization error. Skipping URL since it violates URL standards (RFC 2396). Base: " + baseUrl + ", url: " + url);
		}
		log.debug("Normalized url: {} to: {}", normalizedUrl, absoluteURL);
		return absoluteURL;
	}

}
