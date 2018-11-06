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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
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
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.codec.digest.DigestUtils;
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
import com.flickr4java.flickr.photos.Extras;
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
 *  -d <directory>          directory to upload
 *  -d1                     don't create new sets for subdirectories
 *  -dd                     delete double photos in all albums
 *  -dd1 <photoset_title>   delete double photos (having the same name) in
 *                          one album
 *  -g <photoset_title>     download all photos of one album
 *  -gq <quality>           photo quality to download (available: o-original,
 *                          l-large, m-medium, s-small, sq-square, t-thumbnail
 *  -h                      help
 *  -l                      list sets
 *  -nq                     don't ask questions
 *  -ns                     no save token
 *  -pub <file>             file with permissions to parse
 *  -t <token>              auth token
 *  -ts <token_scret>       auth token secret
 * 
 * @author marioosh
 *
 */
public class Uploader {

	private static Logger log = Logger.getLogger(Uploader.class);
	
	private static final SimpleDateFormat DATE_FORMAT_DATE = new SimpleDateFormat("yyyy.MM.dd");
	private static final SimpleDateFormat DATE_FORMAT_YEAR = new SimpleDateFormat("yyyy");
	private static final String SHA1_TAG_PREFIX = "sha1x";
	private static final String DOWNLOAD_PREFIX = "download_";	
	private final static String FILE_PATTERN = "([^\\s]+(\\.(?i)(jpg|jpeg|png|gif|bmp|mp4|avi|wmv|mov|mpg|3gp|ogg|ogv))$)";
	
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
    static String download;
    static String downloadById;
    static String migrateById;
    static String migrate;
    static String downloadQuality;

    public static void main(String[] args) {
        try {
            Options options = new Options();
            Option tOpt = new Option("t", true, "auth token");
            Option tsOpt = new Option("ts", true, "auth token secret");
            Option dOpt = new Option("d", true, "directory to upload");
            Option d1Opt = new Option("d1", false, "don't create new sets for subdirectories");
            Option lOpt = new Option("l", false, "list sets");
            Option nsOpt = new Option("ns", false, "no save token");
            Option nqOpt = new Option("nq", false, "don't ask questions");
            Option hOpt = new Option("h", false, "help");
            Option pubOpt = new Option("pub", true, "file with permissions to parse");
            Option ddOpt = new Option("dd", false, "delete double photos in all albums");
            Option dd1Opt = new Option("dd1", true, "delete double photos (having the same name) in one album");
            Option gOpt = new Option("g", true, "download all photos of one album, by title");
            Option g2Opt = new Option("g2", true, "download all photos of one album, by photosetId");
            Option gqOpt = new Option("gq", true, "photo quality to download (available: o-original, l-large, m-medium, s-small, sq-square, t-thumbnail");
            Option mOpt = new Option("m", true, "migrate all photos of one album, by title");
            Option m2Opt = new Option("m2", true, "migrate all photos of one album, by photosetId");
            
            tOpt.setArgName("token");
            tsOpt.setArgName("token_scret");
            dOpt.setArgName("directory");
            pubOpt.setArgName("file");
            dd1Opt.setArgName("photoset_title");
            gOpt.setArgName("photoset_title");
            g2Opt.setArgName("photoset_id");
            mOpt.setArgName("photoset_title");
            m2Opt.setArgName("photoset_id");
            gqOpt.setArgName("quality");
            
            options.addOption(tOpt);
            options.addOption(tsOpt);
            options.addOption(dOpt);
            options.addOption(d1Opt);
            options.addOption(lOpt);
            options.addOption(nsOpt);
            options.addOption(nqOpt);
            options.addOption(hOpt);
            options.addOption(pubOpt);
            options.addOption(ddOpt);
            options.addOption(dd1Opt);
            options.addOption(gOpt);
            options.addOption(g2Opt);
            options.addOption(gqOpt);
            options.addOption(mOpt);
            options.addOption(m2Opt);

            log.debug("HOME: "+ System.getProperty("user.home"));

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
        	if((cmd.hasOption("g")||cmd.hasOption("g2")) && cmd.hasOption("gq")) {
        		downloadQuality = cmd.getOptionValue("gq");
        	}
            if(cmd.hasOption("g")) {
            	download = cmd.getOptionValue("g");
            }            
            if(cmd.hasOption("g2")) {
            	downloadById = cmd.getOptionValue("g2");
            }            
            if(cmd.hasOption("m")) {
            	migrate = cmd.getOptionValue("m");
            }            
            if(cmd.hasOption("m2")) {
            	migrateById = cmd.getOptionValue("m2");
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
	            		deleteDoubles(deleteDoubleOneTitle);
	            	}
	            	if(deleteDouble) {
	            		deleteDoubles(null);
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
	            	if(download != null) {
	            		downloadPhotos(download, false);
	            	}
	            	if(downloadById != null) {
	            		downloadPhotos(downloadById, true);
	            	}	            	
	            }
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
		}
    }

	/**
     * search duplicate photos (had the same names) in photoset and delete them
     * 
     * @param photoset
     * @param logDeleted
     * @param logLeft
     * @throws IOException
     * @throws FlickrException
     */
    private void deleteDoubleOne(Photoset photoset, FileWriter logDeleted, FileWriter logLeft) throws IOException, FlickrException {
    	
    	log.info("Searching in \""+photoset.getTitle()+"\" photoset...");
        Set<Photo1> ok = new HashSet<Photo1>();
        List<Photo1> todelete = new ArrayList<Photo1>();

        /**
         * bulid full list
         */
        int page = 1;
        int pages = 0;
        do {
        	PhotoList<Photo> pl = f.getPhotosetsInterface().getPhotos(photoset.getId(), Extras.ALL_EXTRAS, Flickr.PRIVACY_LEVEL_NO_FILTER, 500, page);
        	for(Photo p: pl) {
        		String sha1 = null;
        		for(com.flickr4java.flickr.tags.Tag tag: p.getTags()) {
        			String v = tag.getValue();       
        			if(v.startsWith(SHA1_TAG_PREFIX)) {
        				sha1 = v.substring(5);
        				sha1 = sha1.length()>0?sha1:null;
            			break;        				
        			}
        		}       		
        		Photo1 p1 = new Photo1(p.getTitle(), p.getId(), photoset.getTitle(), photoset.getId(), sha1);
        		if(!ok.add(p1)) {
        			todelete.add(p1);
        		}
        	}
        	if(page == 1) {
        		pages = pl.getPages();
        	}
        } while (page++ < pages);
        
        log.info("OK       : "+ok.size());
        log.info("TO DELETE: "+todelete.size());
        
        if(todelete.size() > 0) {
			System.out.print("\nAre You sure to DELETE " + todelete.size() + " photos ? ");						
			Scanner in = new Scanner(System.in);
			String yn = in.nextLine();
			in.close();
			if(yn.equalsIgnoreCase("y")) {
				
		        String psPopId = null;
		        for(Photo1 p2: todelete) {
		        	if(p2.photosetId != psPopId) {
		        		logDeleted.write(">"+p2.photosetId+":"+p2.photosetTitle+"\n");
		        	}
		    		logDeleted.write("+"+p2.id+":"+p2.title+"\n");	            	
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
			}
        }

		String psPopId = null;
        for(Photo1 p2: ok) {
        	if(p2.photosetId != psPopId) {
        		logLeft.write(">"+p2.photosetId+":"+p2.photosetTitle+"\n");
        	}
    		logLeft.write("+"+p2.id+":"+p2.title+"\n");	            	
        	psPopId = p2.photosetId;
        }
        
    }
    
    private void deleteDoubles(String deleteDoubleOneTitle) {
    	try {
	        Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
	        if(sets.getPhotosets().size() == 0) {
	        	log.debug("No Photosets");
	        }

	        File ff = File.createTempFile("todelete", ".photos");
            log.info(ff.getAbsolutePath());
            FileWriter logDeleted = new FileWriter(ff);

	        File ff1 = File.createTempFile("okey", ".photos");
            log.info(ff1.getAbsolutePath());
            FileWriter logLeft = new FileWriter(ff1);
            
	        for (Object o : sets.getPhotosets()) {
	            Photoset s = (Photoset) o;
	            if(deleteDoubleOneTitle == null) {
	            	deleteDoubleOne(s, logDeleted, logLeft);
	            } else if(deleteDoubleOneTitle != null && s.getTitle().equals(deleteDoubleOneTitle)) {
	            	deleteDoubleOne(s, logDeleted, logLeft);
	            	break;
	            }
	        }
	        
	        logDeleted.close();
	        logLeft.close();
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
	        		Date updated = new Date(Long.parseLong(s.getDateUpdate())*1000);
	        		Date created = new Date(Long.parseLong(s.getDateCreate())*1000);
	        		Date printed = updated.after(created) ? updated: created;
	        		System.out.println(Utils.padRight(s.getPhotoCount()+"",5)+" "+DATE_FORMAT_DATE.format(printed)+" "+s.getId()+" "+s.getTitle());
	        	}
	        }
    	} catch (FlickrException e) {
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
    	log.debug("-- AUTH --");
    	//log.debug(String.format("Token      :%s", auth.getToken()));
        log.debug(String.format("nsid       :%s", auth.getUser().getId()));
        log.debug(String.format("Realname   :%s", auth.getUser().getRealName()));
        log.debug(String.format("Username   :%s", auth.getUser().getUsername()));
        log.debug(String.format("Permission :%s", auth.getPermission()));
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
        
        HashMap<String, String> uploadedSha1 = new HashMap<String, String>();

        int i = 0;
        for (File p : l) {
            if (p.isFile()) {
                UploadMetaData metaData = new UploadMetaData();
                metaData.setHidden(true);
                metaData.setPublicFlag(false);
                metaData.setTitle(p.getName());
                StringBuffer sb = new StringBuffer();
                try {
                    // hash
                    FileInputStream fis = new FileInputStream(p);
                    String sha1 = DigestUtils.sha1Hex(fis);
                    fis.close();
                    
                    /**
                     * skip indetical file in uploaded directory
                     */
                    String path = uploadedSha1.get(sha1);
                    if(path != null) {
                    	log.info("Skipping \""+p.getName()+"\", identical file: \""+path+"\" uploaded before.");
                    	continue;
                    }
                	
                	sb.append(String.format("%-5s Uploading %-70s", i, p.getAbsolutePath()));
                    String photoId = uploader.upload(new FileInputStream(p), metaData);
                    
                    uploadedSha1.put(sha1, p.getAbsolutePath());
                    
                    sb.append(" photoId: " + photoId);
                    final String title = new File(dir).getName();
                    
                    Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(new FileInputStream(p)));
                    final Directory d = metadata.getDirectory(ExifDirectory.class);
                    ArrayList<String> tags = new ArrayList<String>(){{
                    	add(title);
                        if(d.containsTag(ExifDirectory.TAG_DATETIME)) {
                        	Date date = d.getDate(ExifDirectory.TAG_DATETIME);
                    		add(DATE_FORMAT_DATE.format(date));
                    		add(DATE_FORMAT_YEAR.format(date));
                    	}
                    }};

                    tags.add("sha1");
                    tags.add(SHA1_TAG_PREFIX+sha1);
                    
                    f.getPhotosInterface().addTags(photoId, tags.toArray(new String[tags.size()]));
                    sb.append(", Tags: " + new HashSet<String>(tags));
                    
                    if (s == null) {
                        s = findSet(title, false);
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
                log.debug(String.format("%-50s %s %d", s.getTitle(), s.getId(), s.getPhotoCount()));
            }
            return sets;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }
    
    private Photoset findSet(String nameOrId, boolean byId) {
        try {
            Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
            for (Object o : sets.getPhotosets()) {
                Photoset s = (Photoset) o;
                if ((byId?s.getId():s.getTitle()).equals(nameOrId)) {
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
    
    private void downloadPhotos(String albumTitle, boolean byId) throws FlickrException, KeyManagementException, NoSuchAlgorithmException, IOException {    	
		HttpsURLConnection.setDefaultSSLSocketFactory(Utils.trustAllSocketFactory());    	
    	Photoset s = findSet(albumTitle, byId);
        
    	Set<String> extras = new HashSet<String>();
    	String q = downloadQuality==null?Extras.URL_M
			:downloadQuality.equals("o")?Extras.URL_O
			:downloadQuality.equals("l")?Extras.URL_L
			:downloadQuality.equals("m")?Extras.URL_M
			:downloadQuality.equals("s")?Extras.URL_S
			:downloadQuality.equals("sq")?Extras.URL_SQ
			:downloadQuality.equals("t")?Extras.URL_T
			:Extras.URL_M;
    	String quality = downloadQuality==null?"medium"
			:downloadQuality.equals("o")?"original"
			:downloadQuality.equals("l")?"large"
			:downloadQuality.equals("m")?"medium"
			:downloadQuality.equals("s")?"small"
			:downloadQuality.equals("sq")?"square"
			:downloadQuality.equals("t")?"thumbnail"
			:"medium";    	
    	extras.add(q);
    	extras.add(Extras.ORIGINAL_FORMAT);
    	
    	Pattern pattern = Pattern.compile(FILE_PATTERN);
    	
    	int page = 1;
        int pages = 0;
        do {
        	PhotoList<Photo> pl = f.getPhotosetsInterface().getPhotos(s.getId(), extras, Flickr.PRIVACY_LEVEL_NO_FILTER, 500, page);

        	if(page == 1) {
	    		System.out.print("\nDownload (quality "+quality+") " + pl.getTotal() + " photos (y/n) ? ");						
	    		Scanner in = new Scanner(System.in);
	    		String yn = in.nextLine();
	    		in.close();
	    		if (!yn.equalsIgnoreCase("y")) {
	    			break;
	    		}    	
        	}
        	
        	for(Photo p: pl) {       		
            	String urlString = downloadQuality==null?p.getMediumUrl()
    				:downloadQuality.equals("o")?p.getOriginalUrl()
    				:downloadQuality.equals("l")?p.getLargeUrl()
    				:downloadQuality.equals("m")?p.getMediumUrl()
    				:downloadQuality.equals("s")?p.getSmallUrl()
    				:downloadQuality.equals("sq")?p.getSquareLargeUrl()
    				:downloadQuality.equals("t")?p.getThumbnailUrl()
    				:p.getMediumUrl();

                String outFileTitle = p.getTitle();                
            	if(!pattern.matcher(outFileTitle).matches()) {
            		outFileTitle += "."+p.getOriginalFormat();
            	}            	
            	File outDir = new File(DOWNLOAD_PREFIX+quality+"_"+ s.getId());
        		if(!outDir.isDirectory() && !outDir.exists()) {
        			boolean ok = outDir.mkdir();
        			if(!ok) {
        				throw new IOException("Can't create outpu directory "+outDir.getAbsolutePath());
        			}
        		}
        		File outFile = new File(outDir, outFileTitle);
                
            	log.info("Downloading "+urlString + " -> " + outFile+" ...");
            	            	
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
        	if(page == 1) {
        		pages = pl.getPages();
        	}
        } while (page++ < pages);

	}
    
}

class Photo1 {
	String photosetId;
	String photosetTitle;
	String title;
	String id;
	String sha1;
	public Photo1(String title, String id, String photosetTitle, String photosetId, String sha1) {
		super();
		this.title = title;
		this.id = id;
		this.photosetId = photosetId;
		this.photosetTitle = photosetTitle;
		this.sha1 = sha1;
	}
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Photo1) {
			Photo1 p1 = (Photo1) obj;
			if(p1.sha1!=null && p1.sha1.equals(this.sha1)) {
				return true;
			}
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
	@Override
	public String toString() {
		return "Photo1 [photosetId=" + photosetId + ", photosetTitle=" + photosetTitle + ", title=" + title + ", id="
				+ id + "]";
	}	
	
}
