package net.marioosh.google;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient.ListAlbumsPagedResponse;
import com.google.photos.library.v1.proto.Album;

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
	 * default location in user home
	 */
	private static final String DEFAULT_CREDENTIALS_LOCATION = System.getProperty("user.home")+File.separatorChar+
			".ssh"+File.separatorChar+"credentials.json";

	/**
	 * Scopes: 
	 * https://developers.google.com/photos/library/guides/authentication-authorization
	 */
	private static final List<String> REQUIRED_SCOPES = Arrays.asList(
		"https://www.googleapis.com/auth/photoslibrary.readonly",
		"https://www.googleapis.com/auth/photoslibrary.appendonly");
	  
	/**
	 * @param credentialsPath
	 * @return
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @throws Exception
	 */
	private static Credentials authorize(String credentialsPath) throws FileNotFoundException, IOException {
		
		if(credentialsPath == null) {
			credentialsPath = DEFAULT_CREDENTIALS_LOCATION;
		}
		
		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(new FileInputStream(credentialsPath)));
		
		String clientId = clientSecrets.getDetails().getClientId();
		String clientSecret = clientSecrets.getDetails().getClientSecret();
		    
		// set up authorization code flow
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
				clientSecrets, REQUIRED_SCOPES)
						.setDataStoreFactory(dataStoreFactory).build();
		// authorize
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		
		return UserCredentials.newBuilder()
	        .setClientId(clientId)
	        .setClientSecret(clientSecret)
	        .setRefreshToken(credential.getRefreshToken())
	        .build();		
	}
	
	public static PhotosLibraryClient getClient() throws IOException, GeneralSecurityException {
		return getClient(null);
	}
	
	public static PhotosLibraryClient getClient(String credentialsPath) throws IOException, GeneralSecurityException {
		
		String osArch = System.getProperty("sun.arch.data.model");
		log.info("Detected Java: " + osArch +"bit");
		if(!"64".equals(osArch)) {
			log.warn("WARNING: gRPC problems with Java 32-bit, consider use Java 64-bit. More info: https://github.com/grpc/grpc-java/blob/master/SECURITY.md#troubleshooting\n\n");
		}
		
		httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
		
		// authorization
		Credentials credentials = authorize(credentialsPath);

		PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
		
		return PhotosLibraryClient.initialize(settings);
	}

	public static void main(String[] args) throws IOException, GeneralSecurityException {
		ListAlbumsPagedResponse response = getClient(null).listAlbums();
		Iterator<Album> it = response.iterateAll().iterator();
		while(it.hasNext()) {
			Album album = it.next();
			log.info(album.getTitle());
		}
	}
}
