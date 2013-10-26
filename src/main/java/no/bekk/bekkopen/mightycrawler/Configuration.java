package no.bekk.bekkopen.mightycrawler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;


public class Configuration {

	public Collection<String> startURLs = new ArrayList<>();
	public String includePattern = "";
	public String excludePattern = "";

	public boolean crawlingMode = true;
	
	public String extractPattern = "";
	public String linkPattern = "";
	public String storePattern = "";

	public String userAgent = "";
	public int httpPort = 80;
	
	public boolean useCookies = true;
	public boolean followRedirects = true;
		
	public int downloadThreads;

	public int maxVisits;
	public int maxDownloads;
	public int maxRecursion;
	public int maxTime;
	public int downloadDelay;
	public int responseTimeout;
	public int crawlerTimeout;

	public String urlFile = "";
	public String databaseDirectory = "";
	public String outputDirectory = "";
	public String reportDirectory = "";

	public Collection<String> reportSQL = new ArrayList<>();

	public IncludeExcludeFilter crawlFilter;
	public IncludeExcludeFilter extractFilter;
	public IncludeExcludeFilter linkFilter;
	public IncludeExcludeFilter storeFilter;
	
	static final Logger log = LoggerFactory.getLogger(Configuration.class);
	
	public Configuration(String filename) {
		init(filename);
	}
		
	public void init(String fileName) {
		Properties p = new Properties();
		String firstURL = null;
		try (FileReader fr = new FileReader(fileName)){
			p.load(fr);
		
			String start = p.getProperty("startURLs", "");
			startURLs = Arrays.asList(start.split("\\|"));

			urlFile = p.getProperty("urlFile", "");
			File f = new File(urlFile);
			if (f.exists()) {
				startURLs = FileUtils.readLines(f);		
				crawlingMode = false;
			}

			if (startURLs.iterator().hasNext()) {
				firstURL = startURLs.iterator().next();
			}
			
			int port = new URL(firstURL).getPort();
			if (port != -1) {
				httpPort = port;
			}
			
	 		String[] inc = start.split("\\|");
			String defaultIncludes = StringUtils.join(inc, ".*|") + ".*";
			if (!crawlingMode) {
				defaultIncludes = "";
			}
			includePattern = p.getProperty("includePattern", defaultIncludes);
			excludePattern = p.getProperty("excludePattern", "");
			
			extractPattern = p.getProperty("extractPattern", "");
			linkPattern =  p.getProperty("linkPattern", "");
			storePattern = p.getProperty("storePattern", "");
			
			userAgent = p.getProperty("userAgent", "");

			useCookies = Boolean.valueOf(p.getProperty("useCookies", "true"));
			followRedirects = Boolean.valueOf(p.getProperty("followRedirects", "true"));

			downloadThreads = Integer.parseInt(p.getProperty("downloadThreads", "1"));
				
			maxVisits = Integer.parseInt(p.getProperty("maxVisits", "1"));
			maxDownloads = Integer.parseInt(p.getProperty("maxDownloads", "1"));
			maxRecursion = Integer.parseInt(p.getProperty("maxRecursion", "1"));
			maxTime = Integer.parseInt(p.getProperty("maxTime", String.valueOf(Integer.MAX_VALUE)));

			downloadDelay = Integer.parseInt(p.getProperty("downloadDelay", "5"));		
			responseTimeout = Integer.parseInt(p.getProperty("responseTimeout", "10"));		
			crawlerTimeout = Integer.parseInt(p.getProperty("crawlerTimeout", "30"));

            String tempDir = System.getProperty("java.io.tmpdir");
            outputDirectory = p.getProperty("outputDirectory", tempDir);
			reportDirectory = p.getProperty("reportDirectory", tempDir);
			databaseDirectory = p.getProperty("databaseDirectory", tempDir);
			
			String sql = p.getProperty("reportSQL", "");
			if (sql.length() == 0) {
				reportSQL = new ArrayList<>();
			} else {
				reportSQL = Arrays.asList(sql.split("\\|"));
			}			
			
			crawlFilter = new IncludeExcludeFilter(includePattern, excludePattern);
			extractFilter = new IncludeExcludeFilter(extractPattern, "");
			linkFilter = new IncludeExcludeFilter(linkPattern, "");
			storeFilter = new IncludeExcludeFilter(storePattern, "");
			
		} catch (MalformedURLException mue) {
			if (firstURL == null) {
				log.info("List of URLs to crawl was empty. Aborting.");
			} else {
				log.error("Can not parse start URL. Aborting. Error was: " + mue);
			}
			System.exit(1);
		} catch (IOException ioe) {
			System.err.println("\nError reading configuration file: " + ioe);
			System.err.println("Aborting.");
			System.exit(1);
		} catch (NumberFormatException nfe) {
			log.error("Error reading configuration value: " + nfe);
		}
	}	
}

