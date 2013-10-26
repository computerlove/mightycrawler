package no.bekk.bekkopen.mightycrawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IncludeExcludeFilter implements Predicate<String>{
	private Pattern includeFilter;
	private Pattern excludeFilter;

	static final Logger log = LoggerFactory.getLogger(IncludeExcludeFilter.class);
	
	public IncludeExcludeFilter(String include, String exclude) {
		includeFilter = Pattern.compile(include);
		excludeFilter = Pattern.compile(exclude);
	}

	// TODO: Convenience method to filter lists of items
	public boolean letsThrough(String item) {
		Matcher includeMatcher = includeFilter.matcher(item);
	    if (includeMatcher.matches()) {
    		log.debug("Item {} matches the inclusion filter.", item);
	    	Matcher excludeMatcher = excludeFilter.matcher(item);
	    	if (!excludeMatcher.matches()) {
	    		log.debug("Item {} included as it matched the inclusion filter and did not match the exclusion filter.", item);
	    		return true;
	    	} else {
	    		log.debug("Item {} excluded as it matched the exclusion filter.", item);
	    	}
    	} else {
    		log.debug("Item {} excluded as it did not match the inclusion filter.", item);
    	}
	    return false;
	}
	
	public Collection<String> getMatches(String content) {
		Collection<String> matchList = new HashSet<>();
		Matcher matcher = includeFilter.matcher(content);
		while (matcher.find()) {
			int i=1;
			while (i <= matcher.groupCount()) {
				if (matcher.group(i) != null) {
					matchList.add(matcher.group(i));				
				}
				i++;
			}
		}
		return matchList;
	}

    @Override
    public boolean test(String s) {
        return letsThrough(s);
    }
}
