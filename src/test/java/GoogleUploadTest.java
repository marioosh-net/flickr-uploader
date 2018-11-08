import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.api.client.util.IOUtils;
import com.google.photos.library.v1.proto.Album;
import com.google.photos.library.v1.proto.MediaItem;

import net.marioosh.google.GooglePhotos;

public class GoogleUploadTest {

	private final Logger log = Logger.getLogger(GoogleUploadTest.class);
	private static GooglePhotos g;	
	
	private final String TEST_ALBUM = "testing";
	
	@BeforeClass
	public static void setup() throws IOException, GeneralSecurityException {
		g = GooglePhotos.getInstance(null);
	}
	
	@Test
	public void albumCreate() throws IOException {
		Album a = g.findAlbumByTitle(TEST_ALBUM);
		if(a == null) {
			g.createAlbum(TEST_ALBUM);
		}
	}
	
	@Test
	public void upload() throws IOException {
		InputStream in = GoogleUploadTest.class.getClassLoader().getResourceAsStream("sublime.png");
		File temp = File.createTempFile("uploaded_", ".png");
		FileOutputStream fo = new FileOutputStream(temp);
		IOUtils.copy(in, fo);
		String uploadToken = g.upload(temp, "sublime.png");
		Assert.assertTrue(uploadToken!=null && !uploadToken.isEmpty());
		log.info(uploadToken);
		
		Album a = g.findAlbumByTitle(TEST_ALBUM);
		Assert.assertTrue("Album (id: "+a.getId()+") not writable", a.getIsWriteable());
		
		MediaItem item = g.createMedia(uploadToken, a.getIsWriteable()?a.getId():null);
		log.info(item.getBaseUrl());
		log.info(item.toString());		
	}
}
