package net.marioosh.google;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.photos.Photo;
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
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient.SearchMediaItemsPagedResponse;
import com.google.photos.library.v1.proto.Album;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsResponse;
import com.google.photos.library.v1.proto.MediaItem;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.proto.NewMediaItemResult;
import com.google.photos.library.v1.proto.SearchMediaItemsRequest;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.rpc.Code;
import com.google.rpc.Status;

import net.marioosh.flickr.Utils;

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
	
	
	private static GooglePhotos instance;
	private PhotosLibraryClient client;
	private String credentialsPath;
	
	private GooglePhotos(String credentialsPath) {
		this.credentialsPath = credentialsPath;
	}
	
	/**
	 * @param credentialsPath
	 * @return
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @throws Exception
	 */
	private Credentials authorize() throws FileNotFoundException, IOException {
		
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

	public static GooglePhotos getInstance(String credentialsPath) throws IOException, GeneralSecurityException {
		if(instance == null) {
			instance = new GooglePhotos(credentialsPath);
			instance.initClient();
		}
		return instance;
	}
	
	private void initClient() throws IOException, GeneralSecurityException {
		
		String osArch = System.getProperty("sun.arch.data.model");
		log.debug("Detected Java: " + osArch +"bit");
		if(!"64".equals(osArch)) {
			log.warn("WARNING: gRPC problems with Java 32-bit, consider use Java 64-bit. More info: https://github.com/grpc/grpc-java/blob/master/SECURITY.md#troubleshooting\n\n");
		}
		
		httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
		
		// authorization
		Credentials credentials = authorize();

		PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
		
		this.client = PhotosLibraryClient.initialize(settings);
	}
	
	public Album findAlbumByTitle(String title) {
		ListAlbumsPagedResponse l = client.listAlbums();
		Iterator<Album> it = l.iterateAll().iterator();
		while(it.hasNext()) {
			Album a = it.next();
			if(a.getTitle().equals(title)) {
				return a;
			}
		}
		return null;
	}
	
	public PhotosLibraryClient getClient() {
		return client;
	}
	
	public static void main(String[] args) throws IOException, GeneralSecurityException {
		GooglePhotos googlePhotos = GooglePhotos.getInstance(null);
		ListAlbumsPagedResponse response = googlePhotos.getClient().listAlbums();
		Iterator<Album> it = response.iterateAll().iterator();
		while(it.hasNext()) {
			Album album = it.next();
			log.info(album.getTitle());
		}
	}

	/**
	 * copy Flickr photo to Google Photos album
	 * @param p
	 * @param downloadQuality
	 * @param a
	 * @throws FlickrException 
	 * @throws IOException 
	 */
	public void migrate(Photo p, String downloadQuality, Album a) throws FlickrException, IOException {
		String urlString = Utils.getPhotoUrl(downloadQuality, p);
		File outFile = File.createTempFile("flickr_", ".tmp");
		Utils.downloadUrlToFile(urlString, outFile);		
		String uploadToken = upload(outFile, p.getTitle());
		createMedia(uploadToken, null, a.getId());
		outFile.delete();
	}
	
	public List<String> listFilenames(Album a) {
		List<MediaItem> l = listPhotos(a);
		Supplier<List<String>> supplier = new Supplier<List<String>>() {
    		public List<String> get() {
    			return new ArrayList<String>();
    		}
    	};
		return l.stream().map(new Function<MediaItem, String>() {
    		public String apply(MediaItem t) {
    			return t.getFilename();
    		}
    	}).collect(Collectors.toCollection(supplier));
	}
	
	public List<MediaItem> listPhotos(Album a) {
		List<MediaItem> l = new ArrayList<MediaItem>();
		SearchMediaItemsRequest req = SearchMediaItemsRequest.newBuilder()
				.setAlbumId(a.getId())
				.build();
		
		SearchMediaItemsPagedResponse response = client.searchMediaItems(req);
		Iterator<MediaItem> it = response.iterateAll().iterator();
		while(it.hasNext()) {
			l.add(it.next());
		}
		return l;
	}
	
	public Album createAlbum(String albumTitle) {		
		Album newAlbum = Album.newBuilder()
				.setIsWriteable(true)
				.setTitle(albumTitle).build();
		Album createdAlbum = client.createAlbum(newAlbum);
		log.debug("Album \""+albumTitle+"\" created.");
		return createdAlbum;
	}
	
	/**
	 * 
	 * @param file uploade file
	 * @param fileName fileName
	 * @return uploadToken
	 * @throws IOException 
	 */
	public String upload(File file, String fileName) throws IOException {
		log.debug("Uploading "+fileName+" ...");
		UploadMediaItemRequest uploadRequest = UploadMediaItemRequest.newBuilder()
			// filename of the media item along with the file extension
			.setFileName(fileName)
			.setDataFile(new RandomAccessFile(file, "r")).build();		
		UploadMediaItemResponse uploadResponse = client.uploadMediaItem(uploadRequest);
		if (uploadResponse.getError().isPresent()) {
		    // If the upload results in an error, handle it
			UploadMediaItemResponse.Error error = uploadResponse.getError().get();
			throw new IOException("Upload failed", error.getCause());
		  } else {
		    // If the upload is successful, get the uploadToken
			log.debug("Uploaded "+fileName+".");
		    return uploadResponse.getUploadToken().get();
		    // Use this upload token to create a media item
		  }		
	}
	
	public MediaItem createMedia(String uploadToken, String albumId) throws IOException {
		return createMedia(uploadToken, null, albumId);
	}
	
	public MediaItem createMedia(String uploadToken, String itemDescription, String albumId) throws IOException {
		try {
			log.debug("Creating MediaItem ...");
			// Create a NewMediaItem with the uploadToken obtained from the previous upload
			// request, and a description
			NewMediaItem newMediaItem = itemDescription!=null
					?NewMediaItemFactory.createNewMediaItem(uploadToken, itemDescription)
					:NewMediaItemFactory.createNewMediaItem(uploadToken);
			List<NewMediaItem> newItems = Arrays.asList(newMediaItem);
	
			BatchCreateMediaItemsResponse response = albumId!=null
					?client.batchCreateMediaItems(albumId, newItems)
					:client.batchCreateMediaItems(newItems);
			if(response.getNewMediaItemResultsList().size() == 1) {
				NewMediaItemResult itemsResponse = response.getNewMediaItemResultsList().get(0);
				Status status = itemsResponse.getStatus();
				if (status.getCode() == Code.OK_VALUE) {
					// The item is successfully created in the user's library
					log.debug("MediaItem created.");
					return itemsResponse.getMediaItem();
				} else {
					// The item could not be created. Check the status and try again
					throw new IOException("Creating mediaItem failed: " + status.getMessage() + ", code " + status.getCode());
				}			
			} else {
				throw new IOException("Creating mediaItem failed: Too many new media item results");
			}
		} catch (Exception e) {
			log.error(e);
			throw new IOException(e);
		}
	}
}
