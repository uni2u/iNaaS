package etri.sdn.controller.module.ovsdb;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;
import java.util.Arrays;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;


public class BSNSslContextFactory {

	private static final String PROTOCOL = "SSL";
	private static final SSLContext SERVER_CONTEXT;
	private static final SSLContext CLIENT_CONTEXT;
	
	static {
		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
	
		if (algorithm == null) {
			algorithm = "SunX509";
		}
		/**
		* The following makes sure we do not do any certificate validation when
		* we create a HTTPS connection.
		*/
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		
			public void checkClientTrusted(X509Certificate[] certs, String authType) throws java.security.cert.CertificateException {
			}
		
			public void checkServerTrusted(X509Certificate[] certs, String authType) throws java.security.cert.CertificateException {
			}
		}};
		
		SSLContext serverContext = null;
		SSLContext clientContext = null;
		//char[] password = "password".toCharArray();
		char[] password = "importkey".toCharArray();
		
		try {
		
			KeyStore ks = KeyStore.getInstance("JKS");
			InputStream is = new java.io.FileInputStream("/Users/gregor/work/master/keystore.jks");
			ks.load(is, password);
			is.close();
		
			// Set up key manager factory to use our key store
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
			kmf.init(ks, password);
			// Initialize the SSLContext to work with our key managers.
			// Instead of using trustAllCerts we can also just use null
			serverContext = SSLContext.getInstance(PROTOCOL);
			serverContext.init(kmf.getKeyManagers(), trustAllCerts, null);
			clientContext = SSLContext.getInstance(PROTOCOL);
			clientContext.init(kmf.getKeyManagers(), trustAllCerts, null);
		} catch (Exception e) {
			// TODO: error handling is broken!
			throw new Error("Failed to initialize the SSLContexts", e);
		}
		finally {
			Arrays.fill(password, '\0');
		}
		SERVER_CONTEXT = serverContext;
		CLIENT_CONTEXT = clientContext;
	}
		
	public static SSLContext getServerContext() {
		return SERVER_CONTEXT;
	}

	public static SSLContext getClientContext() {
		return CLIENT_CONTEXT;
	}
}
