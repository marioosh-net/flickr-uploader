package com.kotekmiau.flickr;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.xml.sax.SAXException;
import com.aetrion.flickr.Flickr;
import com.aetrion.flickr.FlickrException;
import com.aetrion.flickr.REST;
import com.aetrion.flickr.RequestContext;
import com.aetrion.flickr.auth.Auth;
import com.aetrion.flickr.auth.AuthInterface;
import com.aetrion.flickr.auth.Permission;
import com.aetrion.flickr.photos.Photo;
import com.aetrion.flickr.photos.PhotoList;
import com.aetrion.flickr.photos.SearchParameters;
import com.aetrion.flickr.photosets.Photoset;
import com.aetrion.flickr.photosets.Photosets;
import com.aetrion.flickr.uploader.UploadMetaData;
import com.aetrion.flickr.util.IOUtilities;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectory;
import com.drew.metadata.exif.ExifReader;

/**
 * Flickr command-line uploader
 * Uploads all files from selected directory and subdirectories
 * Photos are tagged using Exif data
 * 
 * usage: java -jar flickr-uploader.jar
 *	-d <arg>    directory to upload
 *	-ns <arg>   no save token
 *	-t <arg>    auth token
 * 
 * @author marioosh
 *
 */
public class Uploader {

	final static String TOKEN_FILE = ".flickr-token";
	final static String CONFIG_FILE = ".flickr-uploader";

	/**
	 * Main entry point for the Flickrj API
	 */
    private Flickr f;
    private Auth auth;

    /**
     * params
     */
    static String token = "";
    static String dir = ".";
    static boolean saveToken = true;

    public static void main(String[] args) {
        try {
            Options options = new Options();
            options.addOption("t", true, "auth token");
            options.addOption("d", true, "directory to upload");
            options.addOption("ns", false, "no save token");
            options.addOption("h", false, "help");

            System.out.println("HOME: "+ System.getProperty("user.home") + "\n");

            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
	            HelpFormatter formatter = new HelpFormatter();
	            formatter.printHelp("java -jar flickr-uploader.jar", options);
	            System.out.println("");
            }
            
            if (cmd.hasOption("t")) {
                token = cmd.getOptionValue("t");
            }

            if (cmd.hasOption("d")) {
                dir = cmd.getOptionValue("d");
            }
            if (cmd.hasOption("ns")) {
            	saveToken = false;
            }

            new Uploader();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public Uploader() {
    	try {
			auth = auth();
			if(auth != null) {
				getSets();
	            if (auth.getPermission().equals(Permission.WRITE) || auth.getPermission().equals(Permission.DELETE)) {
	                Console c = System.console();
	                String yn = c.readLine("\nUpload \"" + dir + "\" directory (y/n) ? ");
	                if (yn.equalsIgnoreCase("y")) {
	                    uploadDir(dir);
	                }
	            }
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
    }

    /**
     * Authentication
     * @return Auth object
     * @throws Exception
     */
    private Auth auth() throws Exception {
    	
	   	/**
    	 * read .flickr-uploader
    	 */
        FileInputStream in1 = null;
        Properties properties = null;        
        try {
        	File f = new File(System.getProperty("user.home"), Uploader.CONFIG_FILE);
        	properties = new Properties();
        	if(f.createNewFile()) {
        		properties.put("apiKey", "API_KEY_HERE");
        		properties.put("secret", "SECRET_HERE");
        		properties.store(new FileOutputStream(f), "flickr-uploader configuration file");
        	}
        	in1 = new FileInputStream(f);
            properties.load(in1);
        } catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
			System.out.println(e.getMessage());
		} finally {
            in1.close();        	
        }
        
        /**
         * create Flicker object
         */
        f = new Flickr(properties.getProperty("apiKey"), properties.getProperty("secret"), new REST());
        RequestContext.getRequestContext();

        AuthInterface authInterface = f.getAuthInterface();
        if (token.equals("")) {
            token = readToken();
        }
        if (!token.equals("")) {
            try {
                auth = authInterface.checkToken(token);
                if(saveToken) {
                	saveToken(token);
                }
                RequestContext.getRequestContext().setAuth(auth);
                tokenInfo(auth);
                return auth;
                
            } catch (FlickrException e) {
                System.out.println(e.getMessage());
            }
        } else {
            try {
                final String frob = authInterface.getFrob();

                URL url = authInterface.buildAuthenticationUrl(Permission.DELETE, frob);
                System.out.println("Press return after you granted access at this URL:");
                System.out.println(url.toExternalForm());
                BufferedReader infile = new BufferedReader(new InputStreamReader(System.in));
                String line = infile.readLine();

                auth = authInterface.getToken(frob);
                if(saveToken) {
                	saveToken(auth.getToken());
                }
                RequestContext.getRequestContext().setAuth(auth);
                tokenInfo(auth);
                return auth;
                
            } catch (FlickrException e) {
                System.out.println(e.getMessage());
            }
        }    	
        return null;
    }
    
    private void tokenInfo(Auth auth) {
    	System.out.println("-- AUTH --");
    	System.out.printf("Token      :%s\n", auth.getToken());
        System.out.printf("nsid       :%s\n", auth.getUser().getId());
        System.out.printf("Realname   :%s\n", auth.getUser().getRealName());
        System.out.printf("Username   :%s\n", auth.getUser().getUsername());
        System.out.printf("Permission :%s\n", auth.getPermission());
    }

    /**
     * upload files from directory dir and subdirectories
     * @param dir
     */
    private void uploadDir(String dir) {
        Photoset s = null;
        com.aetrion.flickr.uploader.Uploader uploader = f.getUploader();
        File[] l = new File(dir).listFiles();
        Arrays.sort(l, new Comparator<File>() {

            public int compare(File o1, File o2) {
                if (o1.isDirectory()) {
                    return 1;
                }
                return 0;
            }
        });

        int i = 0;
        for (File p : l) {
            if (p.isFile()) {
                UploadMetaData metaData = new UploadMetaData();
                metaData.setHidden(true);
                metaData.setPublicFlag(false);
                metaData.setTitle(p.getName());
                try {
                    // System.out.print(p.getAbsolutePath() + " ... ");
                    System.out.printf("%-70s", p.getAbsolutePath() + " ... ");
                    String photoId = uploader.upload(new FileInputStream(p), metaData);
                    System.out.print("photoId: " + photoId);
                    String title = new File(dir).getName();
                    
                    Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(new FileInputStream(p)));
                    Directory d = metadata.getDirectory(ExifDirectory.class);
                    Date date = d.getDate(ExifDirectory.TAG_DATETIME);
                    
                    String[] tags = new String[]{title, new SimpleDateFormat("yyyy.MM.dd").format(date), new SimpleDateFormat("yyyy").format(date)};
                    f.getPhotosInterface().addTags(photoId, tags);
                    System.out.print(", Tags: " + new HashSet<String>(Arrays.asList(tags)));
                    
                    if (i == 0) {
                        s = findSet(new File(dir).getName());
                        if (s == null) {
                            try {
                            	
                                s = f.getPhotosetsInterface().create(title, "", photoId);
                                System.out.print(", photosetId: "+ s.getId() + " (new); ");
                                f.getPhotosetsInterface().addPhoto(s.getId(), photoId);

                            } catch (Exception e1) {
                                System.out.print(e1.getMessage());
                            }
                        }
                    } else {
                        if (s != null) {
                            // add photo to photoset
                            System.out.print(", photosetId: "+ s.getId() + "; ");
                            f.getPhotosetsInterface().addPhoto(s.getId(), photoId);
                        }
                    }
                    System.out.println("");

                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
            if (p.isDirectory()) {
                uploadDir(p.getAbsolutePath());
            }
            i++;
        }
    }

    private Photosets getSets() {
    	System.out.println("-- SETS --");
        try {
            Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
            if(sets.getPhotosets().size() == 0) {
            	System.out.println("No Photosets");
            }
            for (Object o : sets.getPhotosets()) {
                Photoset s = (Photoset) o;
                System.out.printf("%-30s%s %d\n", s.getTitle(), s.getId(), s.getPhotoCount());
            }
            return sets;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }
    
    private Photoset findSet(String name) {
        try {
            Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
            for (Object o : sets.getPhotosets()) {
                Photoset s = (Photoset) o;
                if (s.getTitle().equals(name)) {
                    return s;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private boolean saveToken(String token) {
        File f = new File(System.getProperty("user.home"), Uploader.TOKEN_FILE);
        try {
       		f.createNewFile();
            if (f.exists()) {
            	System.out.print("Saving token \""+token+"\" to \""+f.getAbsolutePath()+"\" ... ");
                FileWriter w = new FileWriter(f);
                BufferedWriter writer = new BufferedWriter(w);
                writer.write(token);
                writer.close();
                System.out.println("DONE");
            }
            return true;
        } catch (Exception e) {
        	System.out.println("FAIL");
            System.out.println(e.getMessage());
        }
        return false;
    }

    /**
     * read token from file
     * @return
     */
    private String readToken() {
        File f = new File(System.getProperty("user.home"), Uploader.TOKEN_FILE);
        if(f.exists()) {
            System.out.println("Reading token from \""+f.getAbsolutePath()+"\"");
            try {
                FileReader r = new FileReader(f);
                BufferedReader br = new BufferedReader(r);
                String token = br.readLine();
                return token;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        return "";
    }
    
    /**
     * delete all photos
     */
    private void deleteAllPhotos() {
        try {
        	SearchParameters p = new SearchParameters();
        	p.setUserId(auth.getUser().getId());
        	PhotoList list = f.getPhotosInterface().search(p, 1000000000, 0);
			for(Object o: list) {
				f.getPhotosInterface().delete(((Photo)o).getId());
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
    }
}
