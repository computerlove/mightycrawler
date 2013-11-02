package no.bekk.bekkopen.mightycrawler;

import com.jolbox.bonecp.BoneCPDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Report {

    private String shutdownQuery;
    private Driver dbDriver = null;
    private DataSource datasource = null;
	
	private String reportDirectory = null;
	private Collection<String> reportSQL = null;

	static final Logger log = LoggerFactory.getLogger(Report.class);
    private boolean createReport;
    private Long crawlerId;

    public Report(Configuration c) {
		reportSQL = c.reportSQL;
		reportDirectory = c.reportDirectory;

		log.info("Using database driver: " + c.dbDriver);
		log.info("Using database dbConnectionString: " + c.dbConnectionString);
		log.info("Using report directory: " + c.reportDirectory);

        if (c.dataSource == null) {
            datasource = new BoneCPDataSource();
            ((BoneCPDataSource) datasource).setDriverClass(c.dbDriver);
            ((BoneCPDataSource) datasource).setJdbcUrl(c.dbConnectionString);


            try {
                dbDriver = (Driver) Class.forName(c.dbDriver).newInstance();
                DriverManager.registerDriver(dbDriver);
            } catch (Exception e) {
                log.error("Could not instantiate database driver: " + e.getMessage());
            }
        } else {
            this.datasource = c.dataSource;
        }

        if (c.initDb) {
            write("DROP SCHEMA PUBLIC CASCADE");
            write("SET DATABASE DEFAULT RESULT MEMORY ROWS 1000");
            write("CREATE CACHED TABLE downloads ( url VARCHAR(4095), http_code INTEGER default 0, content_type VARCHAR(255), response_time INTEGER default 0, downloaded_at DATETIME default NOW, downloaded BOOLEAN)");
            write("CREATE CACHED TABLE links ( url_from VARCHAR(4095), url_to VARCHAR(4095))");
            shutdownQuery = "SHUTDOWN";
        }

        createReport = c.createReport;
        crawlerId = c.crawlerId;
	}

	public void registerVisit(Resource res) {
		// TODO: Escaping
		write("INSERT INTO downloads (crawl_id, url, http_code, content_type, response_time, downloaded_at, downloaded) values (?,?,?,?,?,?,?)",
				crawlerId, res.url, res.responseCode, res.contentType, res.responseTime, res.timeStamp, res.isDownloaded);
	}

	public void registerOutboundLinks(String url, Collection<String> outlinks) {
        QueryRunner run = new QueryRunner(datasource);
        try {
            Object[][] params = createBatchParameters(url, outlinks);
            if (params.length > 0) {
                run.batch("INSERT INTO links (crawl_id, url_from, url_to) values (?, ?, ?)", params);
            }
        } catch (SQLException e) {
            log.error("Could not execute statement: ", e);
        }
    }

    private Object[][] createBatchParameters(String url, Collection<String> outlinks) {
        Object[][] params = new Object[outlinks.size()][3];
        int i = 0;
        for (String link: outlinks) {
            params[i++] = new Object[]{crawlerId, url, link};
        }
        return params;
    }

    private void printReport(String fileName, List<Map<String, Object>> result) {
		StringBuilder out = new StringBuilder();
		for (Map<String, Object> h : result) {
			Set<Entry<String, Object>> entries = h.entrySet();
			for (Entry<String, Object> e: entries) {
    			out.append(e.getValue()).append(" ");
			}
			out.append("\n");
		}

		try {
			File f = new File(reportDirectory + fileName);
			FileUtils.writeStringToFile(f, out.toString(), "UTF-8");
		} catch (IOException ioe) {
			log.error("Could not create report file: " + ioe.getMessage());
		}
	}

	public void createReport() {
		if (createReport && !reportSQL.isEmpty()) {
			for (String reportLine : reportSQL) {
				String[] reportInfo = reportLine.split("@");
				printReport(reportInfo[1], read(reportInfo[0]));
			}
		}
	}
	
	public List<Map<String, Object>> read(String sql){		
		ResultSetHandler<List<Map<String, Object>>> rsh = new MapListHandler();
		List<Map<String, Object>> result = null;
		QueryRunner run = new QueryRunner(datasource);		
		try {
			result = run.query(sql, rsh);
		} catch (SQLException e) {
			log.error("Could not execute statement: " + e.getMessage());
			log.error("SQL was: " + sql);
		}
		return result;
	}
	
	public int write(String sql, Object... params) {
		int updated = 0;
		QueryRunner run = new QueryRunner(datasource);
		try {
			updated = run.update(sql, params);
		} catch (SQLException e) {
			log.error("Could not execute statement: " + e.getMessage());
			log.error("SQL was: " + sql);
		}
		log.debug("Rows changed: {}", updated);
		return updated;
	}
	
	public void shutDown(){
        if (shutdownQuery != null) {
            write(shutdownQuery);
            try {
                DriverManager.deregisterDriver(dbDriver);
            } catch (SQLException e) {
                log.error("Could not deregister hsqldb driver: ", e);
            }
        }
	}
}
