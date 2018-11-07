package net.marioosh.google;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Collections;

import org.apache.log4j.Logger;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

public class GooglePhotos {

	private static Logger log = Logger.getLogger(GooglePhotos.class);

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to make
	 * it a single globally shared instance across your application.
	 */
	private static FileDataStoreFactory dataStoreFactory;

	/** Global instance of the HTTP transport. */
	private static HttpTransport httpTransport;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Directory to store user credentials. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
			".ssh/google_photos_api");

	/**
	 * default credentials JSON location
	 */
	private static final String DEFAULT_CREDENTIALS_LOCATION = System.getProperty("user.home")+File.separatorChar+
			".ssh"+File.separatorChar+"credentials.json";

	/**
	 * scopes: https://developers.google.com/identity/protocols/googlescopes
	 * 
	 * @param credentialsPath
	 * @return
	 * @throws Exception
	 */
	public static Credential authorize(String credentialsPath) throws Exception {
		
		if(credentialsPath == null) {
			credentialsPath = DEFAULT_CREDENTIALS_LOCATION;
		}
		
		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(new FileInputStream(credentialsPath)));
		// set up authorization code flow
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
				clientSecrets, Collections.singleton("https://www.googleapis.com/auth/drive"))
						.setDataStoreFactory(dataStoreFactory).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	}

	public static void main(String[] args) throws Exception {

		httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
		
		// authorization
		Credential credential = authorize(null);
		log.info(credential.getAccessToken());

	}
}
