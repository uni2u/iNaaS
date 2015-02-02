package etri.iris.agent.util;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class PropertiesUtil {
	private Logger logger = Logger.getLogger(getClass());
	private String loggerPath = System.getProperty("user.dir") + "/log4j.properties";
	
	public Properties getProp() {
		Properties prop = new Properties();
		
		try {
			prop.load(new FileInputStream(new File(System.getProperty("user.dir") + "/agent.properties")));
		} catch (Exception e) {
			PropertyConfigurator.configure(loggerPath);
			logger.error("========== Unable to get properties. ==========");
		}
		
		return prop;
	}
	
	public String getProp(String args) {
		return getProp().get(args).toString();
	}
}
