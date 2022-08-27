package com.randomnoun.common.db.explain;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.json.simple.parser.ParseException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.randomnoun.common.Text;
import com.randomnoun.common.log4j.Log4jCliConfiguration;

/** All the CLI options */
public class SqlExplainToImageCli {

	public static void main(String args[]) throws IOException, ParseException, ClassNotFoundException, SQLException {
		// create the command line parser
		CommandLineParser parser = new DefaultParser();

		// create the Options
		Options options = new Options();
		options.addOption( Option.builder("h").longOpt( "help" ).desc( "This usage text" ).build() );
		options.addOption( Option.builder("i").longOpt( "infile" ).desc( "input file, or '-' for stdin" ).hasArg().argName("infile").build() );
		options.addOption( Option.builder("o").longOpt( "outfile" ).desc( "output file, or '-' for stdout" ).hasArg().argName("outfile").build() );
		options.addOption( Option.builder("f").longOpt( "format" ).desc( "output format" ).hasArg().argName("format").build() );
		options.addOption( Option.builder("j").longOpt( "jdbc" ).desc( "JDBC connection string" ).hasArg().argName("jdbc").build() );
		options.addOption( Option.builder("u").longOpt( "username" ).desc( "JDBC username" ).hasArg().argName("username").build() );
		options.addOption( Option.builder("p").longOpt( "password" ).desc( "JDBC password" ).hasArg().argName("password").build() );
		options.addOption( Option.builder("d").longOpt( "driver" ).desc( "JDBC driver class name; default = org.mariadb.jdbc.Driver" ).hasArg().argName("driver").build() );
		options.addOption( Option.builder("q").longOpt( "sql" ).desc( "SQL to explain" ).hasArg().argName("sql").build() );

		/*
		options.addOption( Option.builder("O").longOpt( "option" ).desc( "use value for script option" )
            .hasArgs().valueSeparator('=').argName("property=value").build() );
        */

			// maybe get these from the script itself. although, why ?
		
		String footer = "\n" +
		  "This command will convert a MySQL JSON execution plan to diagram form.\n" +
		  "The execution plan can be supplied via stdin or --infile (1), or can be retrieved from a MySQL server (2).\n" +
		  "\n" +
		  "(1): When supplying the execution plan via stdin or --infile, use the JSON generated via the 'EXPLAIN FORMAT=JSON (query)' statement, e.g.\n" +
		  "\n" +
		  "  mysql --user=root --password=abc123 --silent --raw --skip-column-names \\\n" +
		  "    --execute \"EXPLAIN FORMAT=JSON SELECT 1 FROM DUAL\" sakila > plan.json\n" +
		  "\n" +
		  "to generate the query plan JSON, then\n" +
		  "\n" + 
		  "  SqlExplainToImageCli --infile plan.json --outfile plan.svg\n" +
		  "or\n" +
		  "  cat plan.json | SqlPlainToImageCli > plan.svg\n" +
		  "\n" +
		  "to generate the SVG diagram.\n" +
		  "\n" +
		  "(2) When supplying the SQL against a database instance, you must supply the connection string, username, password and sql, e.g.:\n" +
		  "\n" +
		  "  SqlExplainToImageCli --jdbc jdbc:mysql://localhost/sakila --username root --password abc123 \\\n" +
		  "    --sql \"SELECT 1 fROM DUAL\" --outfile plan.svg" +
		  "\n";
	
		CommandLine line = null;
		boolean usage = false;
		try {
		    line = parser.parse( options, args );
		} catch (org.apache.commons.cli.ParseException exp) {
		    System.err.println( exp.getMessage() );
		    usage = true;
		}
		String driverName = line.getOptionValue("driverName", "org.mariadb.jdbc.Driver");
		boolean help = line.hasOption("help");
		String infile = line.getOptionValue("infile");
		String jdbc = line.getOptionValue("jdbc");
		String username = line.getOptionValue("username");
		String password = line.getOptionValue("password");
		String sql = line.getOptionValue("sql");
		
		String outfile = line.getOptionValue("outfile");
		
		String format = line.getOptionValue("format", "svg");
		
		if (help || usage) {
			HelpFormatter formatter = new HelpFormatter();
		    formatter.setWidth(100);
		    formatter.setOptionComparator(null);
		    formatter.printHelp( "SqlExplainToImageCli [options]", null, options, footer );
		    System.exit(1);
		}
		
		if (!(format.equals("svg") || format.equals("html"))) {
			System.err.println("Invalid --format; expected svg or html");
			System.exit(1);
		}
			
		Reader r;
		PrintWriter pw;
		
		if (!Text.isBlank(infile) && !Text.isBlank(sql)) {
			System.err.println("Cannot supply both --infile and --sql options");
			System.exit(1);
		}
		if (!Text.isBlank(sql)) {
			Class.forName(driverName);
			Connection conn = DriverManager.getConnection(jdbc, username, password);
			DataSource ds = new SingleConnectionDataSource(conn, false);
			sql = "EXPLAIN FORMAT=JSON " + sql;
			JdbcTemplate jt = new JdbcTemplate(ds);
			String json = jt.queryForObject(sql, String.class);
			r = new StringReader(json);
		} else if (Text.isBlank(infile) || infile.equals("-")) {
			r = new InputStreamReader(System.in);
		} else {
			FileInputStream fis = new FileInputStream(infile);
			r = new InputStreamReader(fis);
		}
		
		if (Text.isBlank(outfile) || outfile.equals("-")) {
			pw = new PrintWriter(System.out);
		} else {
			FileOutputStream fos = new FileOutputStream(outfile);
			pw = new PrintWriter(fos);
		}
		
		Log4jCliConfiguration lcc = new Log4jCliConfiguration();
		Properties lprops = new Properties();
		lprops.put("log4j.rootCategory", "WARN, CONSOLE");
		lcc.init("[SqlExplainToImageCli]", lprops);
		
		SqlExplainToImage seti = new SqlExplainToImage();
		seti.parseJson(r, "1.2.3");
		if (format.equals("svg")) {
			seti.writeSvg(pw);
		} else {
			seti.writeHtml(pw);
		}
		pw.flush();
		pw.close();
	}
	
}
