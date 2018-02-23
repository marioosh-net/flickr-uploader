package net.marioosh.flickr;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;
import org.scribe.model.Token;
import org.scribe.model.Verifier;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectory;
import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.auth.AuthInterface;
import com.flickr4java.flickr.auth.Permission;
import com.flickr4java.flickr.photos.Permissions;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photos.SearchParameters;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.photosets.Photosets;
import com.flickr4java.flickr.uploader.UploadMetaData;

/**
 * Flickr command-line uploader
 * Uploads all files from selected directory and subdirectories
 * Photos are tagged using Exif data
 * 
 * usage: java -jar flickr-uploader.jar
 *  -d <arg>     directory to upload
 *  -d1          don't create new sets for subdirectories
 *  -dd          delete double photos in all albums
 *  -dd1 <arg>   delete double photos in one album
 *  -h           help
 *  -l           list sets
 *  -nq          don't ask questions
 *  -ns          no save token
 *  -pub <arg>   file with permissions to parse
 *  -t <arg>     auth token
 *  -ts <arg>    auth token secret
 * 
 * @author marioosh
 *
 */
public class Uploader {

	private static Logger log = Logger.getLogger(Uploader.class);
	
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
    static String tokenSecret = "";
    static String dir = null;
    static boolean noSubsAlbum;
    static String pub = null;
    static boolean saveToken = true;
    static boolean nq = false;
    static boolean deleteDouble = false;
    static boolean deleteDoubleOne = false;
    static String deleteDoubleOneTitle;
    static boolean list = false;

    public static void main(String[] args) {
        try {
            Options options = new Options();
            options.addOption("t", true, "auth token");
            options.addOption("ts", true, "auth token secret");
            options.addOption("d", true, "directory to upload");
            options.addOption("d1", false, "don't create new sets for subdirectories");
            options.addOption("l", false, "list sets");
            options.addOption("ns", false, "no save token");
            options.addOption("nq", false, "don't ask questions");
            options.addOption("h", false, "help");
            options.addOption("pub", true, "file with permissions to parse");
            options.addOption("dd", false, "delete double photos in all albums");
            options.addOption("dd1", true, "delete double photos in one album");

            log.info("HOME: "+ System.getProperty("user.home"));

            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
	            HelpFormatter formatter = new HelpFormatter();
	            formatter.printHelp("java -jar flickr-uploader.jar", options);
	            log.debug("");
	            System.exit(0);
            }
            
            if (cmd.hasOption("t")) {
                token = cmd.getOptionValue("t");
            }
            if (cmd.hasOption("ts")) {
                tokenSecret = cmd.getOptionValue("ts");
            }

            if (cmd.hasOption("d")) {
                dir = cmd.getOptionValue("d");
            }
            if (cmd.hasOption("d1")) {
            	noSubsAlbum = true;
            }            
            if (cmd.hasOption("ns")) {
            	saveToken = false;
            }
            if(cmd.hasOption("nq")) {
                nq = true;
            }
            if(cmd.hasOption("pub")) {
            	pub = cmd.getOptionValue("pub");
            }
            if(cmd.hasOption("dd")) {
            	deleteDouble = true;
            }
            if(cmd.hasOption("dd1")) {
            	deleteDoubleOne = true;
            	deleteDoubleOneTitle = cmd.getOptionValue("dd1");
            }
            if(cmd.hasOption("l")) {
            	list = true;
            }            

            new Uploader();
        } catch (Exception e) {
        	e.printStackTrace();
            log.debug(e.getMessage());
        }
    }

    public Uploader() {
    	try {
			auth = auth();
			if(auth != null) {
				getSets();
	            if (auth.getPermission().equals(Permission.WRITE) || auth.getPermission().equals(Permission.DELETE)) {
	            	if(list) {
	            		listSets();
	            	}
	            	if(deleteDoubleOne && deleteDoubleOneTitle != null) {
	            		deleteDoubleOne();
	            	}
	            	if(deleteDouble) {
	            		deleteDouble();
	            	}
	            	if (pub != null) {
	            		System.out.print("\nParse \"" + pub + "\" to set public photos (y/n) ? ");						
						Scanner in = new Scanner(System.in);
						String yn = in.nextLine();
						in.close();
						if (yn.equalsIgnoreCase("y")) {
							publicPhotos(pub);
						}	            		
	            		return;
	            	}
	            	if(dir != null) {
						if (nq) {
							uploadDir(dir, !noSubsAlbum, null);
						} else {
							System.out.print("\nUpload \"" + dir + "\" directory (y/n) ? ");						
							Scanner in = new Scanner(System.in);
							String yn = in.nextLine();
							in.close();
							if (yn.equalsIgnoreCase("y")) {
								uploadDir(dir, !noSubsAlbum, null);
							}
						}
	            	}
	            }
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
		}
    }

    private void deleteDoubleOne() {
    	try {
	        Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
	        if(sets.getPhotosets().size() == 0) {
	        	log.debug("No Photosets");
	        }

	        File ff = File.createTempFile("todelete", ".photos");
            log.info(ff.getAbsolutePath());
            FileWriter fw = new FileWriter(ff);

	        File ff1 = File.createTempFile("okey", ".photos");
            log.info(ff1.getAbsolutePath());
            FileWriter fw1 = new FileWriter(ff1);
            
	        for (Object o : sets.getPhotosets()) {
	            Photoset s = (Photoset) o;
	            if(s.getTitle().equals(deleteDoubleOneTitle)) {
	            	log.info("Searching in \""+s.getTitle()+"\" photoset...");
		            Set<Photo1> ok = new HashSet<Photo1>();
		            Set<Photo1> todelete = new HashSet<Photo1>();

		            /**
		             * bulid full list
		             */
		            int page = 1;
		            int pages = 0;
		            do {
		            	PhotoList pl = f.getPhotosetsInterface().getPhotos(s.getId(), 500, page);
		            	for(Object o1: pl) {
		            		Photo p = (Photo) o1;
		            		Photo1 p1 = new Photo1(p.getTitle(), p.getId(), s.getTitle(), s.getId());
		            		if(!ok.add(p1)) {
		            			todelete.add(p1);
		            		}
		            	}
	                	if(page == 1) {
	                		pages = pl.getPages();
	                	}
		            } while (page++ < pages);
		            
		            String psPopId = null;
		            for(Photo1 p2: todelete) {
		            	if(p2.photosetId != psPopId) {
		            		fw.write(">"+p2.photosetId+":"+p2.photosetTitle+"\n");
		            	}
	            		fw.write("+"+p2.id+":"+p2.title+"\n");	            	
		            	psPopId = p2.photosetId;
		            	
		            	/**
		            	 * DELETE !
		            	 */
		            	try {
		            		f.getPhotosInterface().delete(p2.id);
		            		log.info("Delete photo ID: "+p2.id +"("+p2.photosetTitle+") DONE.");
		            	} catch (Exception e) {
		            		log.info("Delete photo ID: "+p2.id +"("+p2.photosetTitle+") FAILS.");	            		
		            		e.printStackTrace();
		            	}
		            	
		            }
		            fw.close();

		            for(Photo1 p2: ok) {
		            	if(p2.photosetId != psPopId) {
		            		fw1.write(">"+p2.photosetId+":"+p2.photosetTitle+"\n");
		            	}
	            		fw1.write("+"+p2.id+":"+p2.title+"\n");	            	
		            	psPopId = p2.photosetId;
		            }
		            fw1.close();
		            
		            log.info("OK       : "+ok.size());
		            log.info("TO DELETE: "+todelete.size());
		            
	            	break;
	            }
	        }
    	} catch (FlickrException e) {
    		e.printStackTrace();
    	} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void listSets() {
    	try {
	        Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
	        if(sets.getPhotosets().size() == 0) {
	        	log.debug("No Photosets");
	        } else {
	        	for(Object o: sets.getPhotosets()) {
	        		Photoset s = (Photoset) o;
	        		System.out.println(s.getTitle() + " "+s.getPhotoCount());
	        	}
	        }
    	} catch (FlickrException e) {
    		e.printStackTrace();
		}
	}
    
    private void deleteDouble() {
    	try {
	        Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
	        if(sets.getPhotosets().size() == 0) {
	        	log.debug("No Photosets");
	        }

	        File ff = File.createTempFile("todelete", "photos");
            log.info(ff.getAbsolutePath());
            FileWriter fw = new FileWriter(ff);

	        
	        for (Object o : sets.getPhotosets()) {
	            Photoset s = (Photoset) o;
	        	log.info("Searching in \""+s.getTitle()+"\" photoset...");
	            int page = 1;
	            int pages = 0;

	            Set<Photo1> ok = new HashSet<Photo1>();
	            Set<Photo1> todelete = new HashSet<Photo1>();
	            
	            /**
	             * bulid full list
	             */
	            do {
	            	PhotoList pl = f.getPhotosetsInterface().getPhotos(s.getId(), 500, page);
	            	for(Object o1: pl) {
	            		Photo p = (Photo) o1;
	            		Photo1 p1 = new Photo1(p.getTitle(), p.getId(), s.getTitle(), s.getId());
	            		if(!ok.add(p1)) {
	            			todelete.add(p1);
	            		}
	            	}
                	if(page == 1) {
                		pages = pl.getPages();
                	}
	            } while (page++ < pages);
	            
	            String psPopId = null;
	            for(Photo1 p2: todelete) {
	            	if(p2.photosetId != psPopId) {
	            		fw.write(">"+p2.photosetId+":"+p2.photosetTitle+"\n");
	            	}
            		fw.write("+"+p2.id+":"+p2.title+"\n");	            	
	            	psPopId = p2.photosetId;
	            	
	            	/**
	            	 * DELETE !
	            	 */
	            	try {
	            		f.getPhotosInterface().delete(p2.id);
	            		log.info("Delete photo ID: "+p2.id +"("+p2.photosetTitle+") DONE.");
	            	} catch (Exception e) {
	            		log.info("Delete photo ID: "+p2.id +"("+p2.photosetTitle+") FAILS.");	            		
	            		e.printStackTrace();
	            	}
	            }
	            
	            log.info("OK       : "+ok.size());
	            log.info("TO DELETE: "+todelete.size());
	            
	        }
            fw.close();	        
    	} catch (FlickrException e) {
    		e.printStackTrace();
    	} catch (IOException e) {
			e.printStackTrace();
		}
	}

    /**
     * list files to public - file format:
     * album_title1
     * ^file1.jpg
     * ^file2.jpg
     * ...
     * album_title2
     * ^file1.jpg
     * ^file2.jpg
     * ...
     * 
     * @param pub
     */
	private void publicPhotos(String pub) {
    	SearchParameters params = new SearchParameters();

    	/**
    	 * key - album title
    	 * value - list of photos titles
    	 */
    	HashMap<String, List<String>> a = new HashMap<String, List<String>>(){
    		@Override
    		public String toString() {
    			StringBuffer sb = new StringBuffer();
    			for(String key: this.keySet()) {
    				sb.append(key+" [");
    				for(String p: this.get(key)) {
    					sb.append(p+", ");
    				}
    				sb.append("]\n");
    			}
    			return sb.toString();
    		}
    	};
    	
    	try {
	    	BufferedReader br = new BufferedReader(new FileReader(pub));
	    	String line;
	    	List<String> photosList = null;
	    	String linePop = null;
	    	while ((line = br.readLine()) != null) {
	    		if(!line.startsWith("^")) {
	    			photosList = new ArrayList<String>();
	    			log.info("add ablum to find list: \""+line.trim()+"\"");
	    			a.put(line.trim(), photosList);	    			
	    		} else {
	    			photosList.add(line.substring(1));
	    		}
	    		linePop = line;
	    	}
	    	br.close();
	    	log.info("Public photos to find: "+a.size());
	    	log.debug(a);
	    	
	    	// wychodzac od albumow
            Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
            if(sets.getPhotosets().size() == 0) {
            	log.debug("No Photosets");
            }
            int found = 0;
            
            log.info("Photosets size: "+sets.getPhotosets().size());
            
            for (Object o : sets.getPhotosets()) {
                Photoset s = (Photoset) o;
                
                log.info("I have a \""+s.getTitle()+"\" photoset.");
                
                List<String> photosForPublicationInAlbum = a.get(s.getTitle().trim());
                if(photosForPublicationInAlbum != null) {
	                
	            	log.info("Searching in \""+s.getTitle()+"\" photoset...");
	                int page = 1;
	                int pages = 0;
	                do {
	                	PhotoList pl = f.getPhotosetsInterface().getPhotos(s.getId(), 500, page);
	                	int c = 0;
	                	for(Object o1: pl) {
	                		Photo p = (Photo) o1;
	                		if(photosForPublicationInAlbum.contains(p.getTitle())) {
	                			
	                			/**
	                			 * public albums have description...
	                			 */
	                			// f.getPhotosetsInterface().editMeta(s.getId(), s.getTitle(), "\u00A0");
	                			if(c == 0) {
	                				f.getPhotosetsInterface().editMeta(s.getId(), s.getTitle(), "\ufeff");
	                			}
	                			c++;
	                			
	                			log.info("FOUND PUBLIC: "+p.getTitle() + " [id: "+p.getId()+", photoset_id: "+s.getId()+"]");
	                			found++;
	                			
	                			/**
	                			 * set permissions
	                			 */
	                			Permissions perm = new Permissions();
	                			perm.setFamilyFlag(true); // is_family=1
	                			perm.setFriendFlag(true); // is_friend=1
	                			f.getPhotosInterface().setPerms(p.getId(), perm);
	                			
	                			/**
	                			 * set tags
	                			 */
	                			f.getPhotosInterface().setTags(p.getId(), new String[]{"public"});	                			
	                		}
	                	}
	                	if(page == 1) {
	                		pages = pl.getPages();
	                	}
	                	
	                } while (page++ < pages);
	                
	                log.debug(String.format("%-50s%s %d", s.getTitle(), s.getId(), s.getPhotoCount()));
                }
            }
            
            log.info("Found photos: "+found);
	    	
    	} catch (FlickrException e) {
    		e.printStackTrace();
    	} catch (IOException e) {
			e.printStackTrace();
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
			e.printStackTrace();
        	log.debug(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			log.debug(e.getMessage());
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
            String[] tokenAndSecret = readToken();
            if(tokenAndSecret != null) {
	            token = tokenAndSecret[0];
	            tokenSecret = tokenAndSecret[1];
            }
        }
        if (!token.equals("")) {
            try {
                auth = authInterface.checkToken(token, tokenSecret);
                if(saveToken) {
                	saveToken(token, tokenSecret);
                }
                RequestContext.getRequestContext().setAuth(auth);
                tokenInfo(auth);
                return auth;
                
            } catch (FlickrException e) {
            	e.printStackTrace();
            	log.debug(e.getMessage());
            }
        } else {
            try {
            	Token requestToken = authInterface.getRequestToken();
            	String urlString = authInterface.getAuthorizationUrl(requestToken, Permission.DELETE);
            	System.out.println("Follow this URL to authorise yourself on Flickr");
                System.out.println(urlString);
                System.out.print("Paste in the token it gives you: ");
                Scanner scanner = new Scanner(System.in);
                String line = scanner.nextLine();                
            	
                Token accessToken = authInterface.getAccessToken(requestToken, new Verifier(line));
                auth = authInterface.checkToken(accessToken);
                if(saveToken) {
                	saveToken(auth.getToken(), auth.getTokenSecret());
                }
                RequestContext.getRequestContext().setAuth(auth);
                tokenInfo(auth);
                return auth;
                
            } catch (FlickrException e) {
            	e.printStackTrace();
            	log.error(e);
                log.debug(e.getMessage());
            }
        }    	
        return null;
    }
    
    private void tokenInfo(Auth auth) {
    	log.info("-- AUTH --");
    	log.debug(String.format("Token      :%s", auth.getToken()));
        log.info(String.format("nsid       :%s", auth.getUser().getId()));
        log.info(String.format("Realname   :%s", auth.getUser().getRealName()));
        log.info(String.format("Username   :%s", auth.getUser().getUsername()));
        log.info(String.format("Permission :%s", auth.getPermission()));
    }

    /**
     * upload files from directory dir and subdirectories
     * @param dir
     */
    private void uploadDir(String dir, boolean subDirAsNewAlbum, Photoset s1) {
        Photoset s = s1;
        com.flickr4java.flickr.uploader.Uploader uploader = f.getUploader();
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
                StringBuffer sb = new StringBuffer();
                try {
                    sb.append(String.format("%-5s Uploading %-70s", i, p.getAbsolutePath()));
                    String photoId = uploader.upload(new FileInputStream(p), metaData);
                    sb.append(" photoId: " + photoId);
                    final String title = new File(dir).getName();
                    
                    Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(new FileInputStream(p)));
                    final Directory d = metadata.getDirectory(ExifDirectory.class);
                    ArrayList<String> tags = new ArrayList<String>(){{
                    	add(title);
                        if(d.containsTag(ExifDirectory.TAG_DATETIME)) {
                        	Date date = d.getDate(ExifDirectory.TAG_DATETIME);
                    		add(new SimpleDateFormat("yyyy.MM.dd").format(date));
                    		add(new SimpleDateFormat("yyyy").format(date));
                    	}
                    }};
                    f.getPhotosInterface().addTags(photoId, tags.toArray(new String[tags.size()]));
                    sb.append(", Tags: " + new HashSet<String>(tags));
                    
                    if (s == null) {
                        s = findSet(title);
                    }
                    if (s == null) {
                        try {
                        	
                            s = f.getPhotosetsInterface().create(title, "", photoId);
                            sb.append(", photosetId: "+ s.getId() + " (new); ");

                        } catch (Exception e1) {
                        	log.error(e1.getMessage());
                            sb.append(" >>> "+e1.getMessage());
                        }
                    } else {
                        // add photo to photoset
                        f.getPhotosetsInterface().addPhoto(s.getId(), photoId);
                        sb.append(", photosetId: "+ s.getId() + "; ");
                    }

                } catch (Exception e) {
                	log.debug(e.getMessage());
                    sb.append(" >>> "+e.getMessage());
                }
                
                log.info(sb.toString());
            }
            if (p.isDirectory()) {
                uploadDir(p.getAbsolutePath(), subDirAsNewAlbum, subDirAsNewAlbum ? null : s);
            }
            i++;
        }
    }

    private Photosets getSets() {
    	log.debug("-- SETS --");
        try {
            Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
            if(sets.getPhotosets().size() == 0) {
            	log.debug("No Photosets");
            }
            for (Object o : sets.getPhotosets()) {
                Photoset s = (Photoset) o;
                log.debug(String.format("%-50s%s %d", s.getTitle(), s.getId(), s.getPhotoCount()));
            }
            return sets;
        } catch (Exception e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
        }
        return null;
    }

    private boolean saveToken(String token, String tokenSecret) {
    	StringBuffer sb = new StringBuffer();
        File f = new File(System.getProperty("user.home"), Uploader.TOKEN_FILE);
        try {
       		f.createNewFile();
            if (f.exists()) {
            	sb.append("Saving token \""+token+"\" to \""+f.getAbsolutePath()+"\" ... ");
                FileWriter w = new FileWriter(f);
                BufferedWriter writer = new BufferedWriter(w);
                writer.write(token+"\n");
                writer.write(tokenSecret);
                writer.close();
                sb.append("DONE");
            }
            return true;
        } catch (Exception e) {
        	sb.append("FAIL");
            log.error(e.getMessage());
        }
        log.debug(sb.toString());
        return false;
    }

    /**
     * read token from file
     * @return
     */
    private String[] readToken() {
        File f = new File(System.getProperty("user.home"), Uploader.TOKEN_FILE);
        if(f.exists()) {
            log.debug("Reading token from \""+f.getAbsolutePath()+"\"");
            try {
                FileReader r = new FileReader(f);
                BufferedReader br = new BufferedReader(r);
                String token = br.readLine();
                String tokenSecret = br.readLine();
                return new String[]{token,tokenSecret};
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return null;
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
			log.debug(e.getMessage());
		}
    }
}

class Photo1 {
	String photosetId;
	String photosetTitle;
	String title;
	String id;
	public Photo1(String title, String id, String photosetTitle, String photosetId) {
		super();
		this.title = title;
		this.id = id;
		this.photosetId = photosetId;
		this.photosetTitle = photosetTitle;
	}
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Photo1) {
			Photo1 p1 = (Photo1) obj;
			if(p1.title.equals(this.title)) {
				return true;
			}
		}
		return false;
	}
	@Override
	public int hashCode() {
		return this.photosetTitle.hashCode();
	}
}
