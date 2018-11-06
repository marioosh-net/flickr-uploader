package net.marioosh.google;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.photos.library.sample.factories.PhotosLibraryClientFactory;
//import com.google.api.gax.core.FixedCredentialsProvider;
//import com.google.auth.Credentials;
//import com.google.photos.library.sample.factories.PhotosLibraryClientFactory;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;

public class GooglePhotos {
	
	public static void init() throws IOException, GeneralSecurityException {
		
		String credentialsPath = System.getProperty("user.home")+File.separatorChar+"credentials.json";
		
		PhotosLibraryClient client = PhotosLibraryClientFactory.createClient(credentialsPath, null);
		
		/*
		PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();	
		PhotosLibraryClient client = PhotosLibraryClient.initialize(settings);
		*/
	}
}
