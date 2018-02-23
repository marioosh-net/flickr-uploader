package net.marioosh.flickr;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Utils {
	
	public static String padLeft(String s, int n) {
		return String.format("%1$"+n+"s", s);  
	}
	
	public static String padRight(String s, int n) {
		return String.format("%1$-"+n+"s", s);  
	}

	public static SSLSocketFactory trustAllSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
		TrustManager[] trustAllCerts = new TrustManager[]{
		    new X509TrustManager() {
		        public X509Certificate[] getAcceptedIssuers() {
		            return null;
		        }
		        public void checkClientTrusted(
		            X509Certificate[] certs, String authType) {
		        }
		        public void checkServerTrusted(
		            X509Certificate[] certs, String authType) {
		        }
		    }
		};		
		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());
		return sc.getSocketFactory();
	}
}
