package net.marioosh.flickr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.photos.Photo;

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
	
	public static void downloadUrlToFile(String urlString, File outFile) throws IOException {
		URL url = new URL(urlString);
		URLConnection connection = url.openConnection();        		
		InputStream in = connection.getInputStream();
		
		FileOutputStream out = new FileOutputStream(outFile);        		
		byte[] buffer = new byte[1024];
		int len;
		while ((len = in.read(buffer)) != -1) {
		    out.write(buffer, 0, len);
		}
		in.close();
		out.close();		
	}

	/**
	 * regognize flickr photo url
	 * @param downloadQuality
	 * @param p
	 * @return
	 * @throws FlickrException
	 */
	public static String getPhotoUrl(String downloadQuality, Photo p) throws FlickrException {
    	return downloadQuality==null?p.getMediumUrl()
			:downloadQuality.equals("o")?p.getOriginalUrl()
			:downloadQuality.equals("l")?p.getLargeUrl()
			:downloadQuality.equals("m")?p.getMediumUrl()
			:downloadQuality.equals("s")?p.getSmallUrl()
			:downloadQuality.equals("sq")?p.getSquareLargeUrl()
			:downloadQuality.equals("t")?p.getThumbnailUrl()
			:p.getMediumUrl();
	}
	
}
