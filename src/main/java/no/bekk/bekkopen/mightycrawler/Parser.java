package no.bekk.bekkopen.mightycrawler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class Parser  {

	private IncludeExcludeFilter linkFilter = null;
	
	static final Logger log = LoggerFactory.getLogger(Parser.class);

	public Parser(IncludeExcludeFilter f) {
		linkFilter = f;
	}
	
	public Collection<String> extractLinks(Resource res) {
		Collection<String> newUrls = linkFilter.getMatches(res.content);
		log.debug("Done parsing {}, number of URLs found: {}", res.url,  newUrls.size());
		return newUrls;
	}
}
