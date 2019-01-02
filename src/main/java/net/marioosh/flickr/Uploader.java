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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import org.apache.log4j.Level;
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
import com.google.photos.library.v1.proto.Album;

import net.marioosh.google.GooglePhotos;
import net.marioosh.google.NoPermissionToAddPhotoToAlbum;

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
	private static final String DOWNLOAD_PREFIX = "d_";	
	private final static String FILE_PATTERN = "([^\\s]+(\\.(?i)(jpg|jpeg|png|gif|bmp|mp4|avi|wmv|mov|mpg|3gp|ogg|ogv))$)";
	
	final static String TOKEN_FILE = ".flickr-token";
	final static String CONFIG_FILE = ".flickr-uploader";
	final static String MIGRATED_ALBUM_LIST_FILE = ".flickr-migrated";
	
	/**
	 * Main entry point for the Flickrj API
	 */
    private Flickr f;
    private Auth auth;
    
    /**
     * Entry point to Google Photos
     */
    private GooglePhotos googlePhotos;

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
    static boolean listGP = false;
    static String download;
    static String downloadById;
    static String migrateById;
    static String migrate;
    static String downloadQuality;
    static boolean downloadAll;
    static boolean migrateAll;
    
    static String googleApiCredentialPath;
    private static Set<String> migratedPhotosets = new HashSet<String>();
    private static boolean checkMigrated;
    private static String sm;
    private static boolean deleteAllPrivatePhotos;
    private static String deleteAllPrivatePhotosPhotoset;
    private static boolean simulate;
    
    public static void main(String[] args) {
		log.info("=========================================================================");    	
    	log.info("START");
        try {
            Options options = new Options();
            Option vOpt = new Option("v", false, "verbose");
            Option vvOpt = new Option("vv", false, "stronger verbose");
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
            Option gaOpt = new Option("ga", false, "download all photos of all albums");
            Option maOpt = new Option("ma", false, "migrate all photos of all albums");
            Option cpOpt = new Option("cp", true, "Google Photos credentials.json path");
            Option cmOpt = new Option("cm", false, "Check migrated list file (.flickr-migrated)");
            Option glOpt = new Option("gl", false, "list albums (Google Photos)");
            Option smOpt = new Option("sm", true, "save migrated file locally");
            Option dapOpt = new Option("dap", false, "delete all private photos in all photosets");
            Option dap1Opt = new Option("p", true, "photoset (name or id) to delete all private photos");
            Option sOpt = new Option("s", false, "simulate deleting only, photos will not be deleted");
            
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
            cpOpt.setArgName("credentials_json");
            smOpt.setArgName("storage_path");
            
            options.addOption(vOpt);
            options.addOption(vvOpt);
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
            options.addOption(gaOpt);
            options.addOption(maOpt);
            options.addOption(cmOpt);
            options.addOption(glOpt);
            options.addOption(smOpt);
            options.addOption(dapOpt);
            options.addOption(dap1Opt);
            options.addOption(sOpt);

            log.debug("HOME: "+ System.getProperty("user.home"));

            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(options, args);

            if(cmd.hasOption("v")) {
            	Logger.getLogger("console").setLevel(Level.DEBUG);
            	Logger.getLogger("file").setLevel(Level.DEBUG);
            }
            if(cmd.hasOption("vv")) {
            	Logger.getRootLogger().setLevel(Level.DEBUG);
            }            
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
        	if(cmd.hasOption("gq")) {
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
            if(cmd.hasOption("gl")) {
            	listGP = true;
            }            
            if(cmd.hasOption("ga")) {
            	downloadAll = true;
            }
            if(cmd.hasOption("ma")) {
            	migrateAll = true;
            }            
            if(cmd.hasOption("cp")) {
            	googleApiCredentialPath = cmd.getOptionValue("cp");
            }
            if(cmd.hasOption("cm")) {
            	checkMigrated = true;
            }
            if(cmd.hasOption("sm")) {
            	sm = cmd.getOptionValue("sm");
            }
           	deleteAllPrivatePhotos = cmd.hasOption("dap");
           	simulate = cmd.hasOption("s");
           	if(cmd.hasOption("p")) {
           		deleteAllPrivatePhotosPhotoset = cmd.getOptionValue("p");
           	}

            new Uploader();
        } catch (Exception e) {
        	e.printStackTrace();
            log.debug(e.getMessage());
        } finally {
        	log.info("Clearing temporary files...");
        	for(File f: new File(System.getProperty("java.io.tmpdir")).listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith("flickr_") && name.endsWith(".tmp");
				}
			})) {
        		f.delete();
        	};        	
        	log.info("EXIT");
        }
    }

	public Uploader() {
    	try {
			auth = auth();
			
        	if(migrate != null || migrateById != null || migrateAll || listGP) {
        		// init only
        		googlePhotos = GooglePhotos.getInstance(googleApiCredentialPath);
        		if(!listGP) {
        			loadMigrated();
        		}
        	}
			
			if(auth != null) {
				getSets();
	            if (auth.getPermission().equals(Permission.WRITE) || auth.getPermission().equals(Permission.DELETE)) {
	            	if(list) {
	            		listSets();
	            	}
	            	if(listGP) {
	            		googlePhotos.listAlbums();
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
	            		downloadPhotos(download, false, false);
	            	}
	            	if(downloadById != null) {
	            		downloadPhotos(downloadById, true, false);
	            	}
	            	
	            	if(migrate != null) {
	            		migratePhotoset(migrate, false, null);
	            	}
	            	if(migrateById != null) {
	            		migratePhotoset(migrateById, true, null);
	            	}
	            	if(migrateAll) {
	            		migrateAllPhotosets();
	            	}	            	
	            	if(downloadAll) {
	            		downloadPhotos();
	            	}
	            	if(deleteAllPrivatePhotos) {
	            		deleteAllPrivatePhotos(deleteAllPrivatePhotosPhotoset);
	            	}
	            	
	            }
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
		}
    }

	private void deleteAllPrivatePhotos(String nameOrId) throws FlickrException {
		Photoset psOne = null;
		if(nameOrId != null) {
			psOne = findSet(nameOrId, true);
			if(psOne == null) {
				psOne = findSet(nameOrId, false);
			}
		}
		
		List<Photoset> l = new ArrayList<Photoset>();
		if(psOne != null) {
			l.add(psOne);
		} else {
	    	Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
	    	int total = sets.getTotal();
	    	log.info("Flickr photosets: "+total);
	    	l.addAll(sets.getPhotosets());
		}
		

    	Set<String> extras = extras(downloadQuality);
		extras.add(Extras.TAGS);
		extras.add(Extras.MEDIA);
    	
		int c = 0;
		int pc = 0;
		int skipped = 0;
		int pcWithSkipped = 0;
        for(Photoset s: l) {
        	log.info("Processing photoset '"+s.getTitle()+"' (id:"+s.getId()+", description:"+s.getDescription()+") ... ");
        	boolean publishedPhotoset = (s.getDescription() != null && (s.getDescription().toLowerCase().contains("public") 
        			|| s.getDescription().toLowerCase().equals(" ")));
        	int page = 1;
            int pages = 0;
            do {
            	PhotoList<Photo> pl = f.getPhotosetsInterface().getPhotos(s.getId(), extras, Flickr.PRIVACY_LEVEL_NO_FILTER, 500, page);
            	boolean once = true;
            	for(Photo p: pl) {
            		if(publishedPhotoset && (p.isFamilyFlag()||p.isFriendFlag()||p.isPublicFlag())) {
            			log.info("Skipping photo '"+p.getTitle()+"' (id: "+p.getId() +") ... ");
            			skipped++;
            			if(once) {
            				pcWithSkipped++;
            				once = false;
            			}
            		} else {
            			log.info("Deleting photo '"+p.getTitle()+"' (id: "+p.getId() +") ... ");
            			if(!simulate) {
            				f.getPhotosInterface().delete(p.getId());
            			}
	            		c++;
            		}            		
            	}
            	if(page == 1) {
            		pages = pl.getPages();
            	}
            } while (page++ < pages);
            pc++;
        }	
        
        log.info("Deleted "+c+" photos in "+pc+" photosets.");
        log.info("Skipped "+skipped+" photos in "+pc+" photosets.");
        log.info("Photosets with non-private photos: "+pcWithSkipped);
	}

	/**
	 * restore migrated list from file
	 */
	private void loadMigrated() {
		File f = new File(System.getProperty("user.home"), MIGRATED_ALBUM_LIST_FILE);
		try {
			FileInputStream in = new FileInputStream(f);
			ObjectInputStream ois = new ObjectInputStream(in);
			Object o = ois.readObject();
			if(o instanceof List) {
				List<String> migratedPhotosets1 = (List<String>) o;
				for(String ps: migratedPhotosets1) {
					migratedPhotosets.add(ps);
				}
			} else {
				migratedPhotosets = (Set<String>) o;
			}
			ois.close();			
			if(migratedPhotosets == null) {
				migratedPhotosets = new HashSet<String>();
			}
		} catch (Exception e) {
			migratedPhotosets = new HashSet<String>();
			log.error("Can't load store migrated list from file ("+f.getAbsolutePath()+").");
		}
		log.info("Migrated photosets: "+migratedPhotosets.size());
		log.debug(migratedPhotosets.toString().replaceAll(",", "\n").replaceAll("[\\]\\[\\ ]", ""));
	}
	
	/**
	 * save migrated list to file for skipping migrated photoset in the future call
	 */
    private static void saveMigrated() {
    	log.info("Saving migrated list file...");
    	File f = new File(System.getProperty("user.home"), MIGRATED_ALBUM_LIST_FILE);
		try {
			FileOutputStream fos = new FileOutputStream(f);
	    	ObjectOutputStream oos = new ObjectOutputStream(fos);
	    	oos.writeObject(migratedPhotosets);
	    	oos.close();		
		} catch (FileNotFoundException e) {
			try {
				boolean created = f.createNewFile();
				if(!created) {
					log.warn("Can't create file to store migrated list ("+f.getAbsolutePath()+").");
				}			
			} catch (IOException e1) {
				log.warn("Can't create file to store migrated list ("+f.getAbsolutePath()+").", e1);
			}
		} catch (IOException e) {
			log.warn("Can't save file to store migrated list ("+f.getAbsolutePath()+").", e);
		}
    }	

    /**
     * migrate all Flickr photosets
     * 
     * @throws FlickrException
     * @throws IOException
     */
	private void migrateAllPhotosets() throws FlickrException, IOException {
    	Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
    	int total = sets.getTotal();
    	log.info("Flickr photosets: "+total);
    	int i = 0;
        for(Photoset s: sets.getPhotosets()) {
        	i++;
        	if(checkMigrated && migratedPhotosets.contains(s.getId())) {
        		log.info("Photoset ("+s.getTitle()+", id:"+s.getId()+") is on migrated list, skipping.");        		
        		continue;
        	}
        	try {
        		migratePhotoset(s.getId(), true, " ("+i+"/"+total+")");
        	} catch (NoPermissionToAddPhotoToAlbum e) {
        		log.info("Trying next photoset.");
        		continue; // try next photoset
        	}
        }		
	}

	/**
	 * migrate one Flickr photoset
	 * 
	 * @param nameOrId name or id photoset
	 * @param byId first parameter as photoset id
	 * @throws IOException
	 * @throws FlickrException
	 */
	private void migratePhotoset(String nameOrId, boolean byId, String additionalMessage) throws IOException, FlickrException {

		Photoset s = findSet(nameOrId, byId);
		if(s == null) {
			throw new IOException("Photoset not exist.");
		}
		String albumTitle = s.getTitle()+" "+s.getId();
		log.info("-------------------------------------------------------------------------");	
		log.info("Processing photoset \""+s.getTitle()+"\" ("+s.getId()+")"+(additionalMessage!=null?additionalMessage:"")+" ... ");
		
		Album a = googlePhotos.findAlbumByTitle(albumTitle);
		if(a != null) {
			if(a.getMediaItemsCount() == s.getPhotoCount()) {
				log.info("Skipped - album with title \""+a.getTitle()+"\" exists and have the same photos count ("+s.getPhotoCount()+") on Flickr and Google Photos");
				migratedPhotosets.add(s.getId());
				saveMigrated();
				return;
			} else {
				log.info("Photos in photoset: "+s.getPhotoCount()+", Photos in Google Photos album: "+a.getMediaItemsCount());
			}
		} else {
			log.info("Creating new album \""+albumTitle+"\" ... ");
			a = googlePhotos.createAlbum(albumTitle);
		}
		
		if(!googlePhotos.getClient().getAlbum(a.getId()).getIsWriteable()) {
			//throw new IOException("Album \""+a.getTitle()+"\" ("+a.getId()+") not writable.");
			log.warn("Album \""+a.getTitle()+"\" ("+a.getId()+") not writable.");
		}
		
		Set<String> extras = extras(downloadQuality);
		extras.add(Extras.TAGS);
		extras.add(Extras.MEDIA);

    	int page = 1;
        int pages = 0;
        int c = 1;
        do {
        	PhotoList<Photo> pl = f.getPhotosetsInterface().getPhotos(s.getId(), extras, Flickr.PRIVACY_LEVEL_NO_FILTER, 500, page);       	
        	List<String> photoTitles = googlePhotos.listFilenames(a);
        	for(Photo p: pl) {
        		if(!"photo".equalsIgnoreCase(p.getMedia())) {
        			log.info("Skipping video file: "+p.getTitle());
        			c++;
        			continue;
        		}
        		if(photoTitles.contains(p.getTitle())) {
        			log.info("Skipping "+p.getTitle()+" ("+c+"/"+s.getPhotoCount()+"), the same filename exists in Google Photos album.");
        		} else {
        			log.info("Copying "+p.getTitle()+" ("+c+"/"+s.getPhotoCount()+") ...");
    				File smDir = null;
        			try {
        				if(sm != null) {
        					smDir = new File(sm, a.getTitle());
        					if(smDir.exists() && smDir.isDirectory()) {        						
        					} else {
        						boolean created = smDir.mkdirs();
        						if(!created) {
        							throw new IOException("Couldn't create storage directory \""+smDir.getAbsolutePath()+"\"");
        						}
        					}
        				}        				
        				googlePhotos.migrate(p, downloadQuality, a, smDir);
        			} catch (IOException e) {
        				if(e instanceof NoPermissionToAddPhotoToAlbum && smDir == null) {
        					throw e;
        				} else {
        					// continue with next photo
        				}
        			}
        		}
        		c++;
        	}
        	if(page == 1) {
        		pages = pl.getPages();
        	}
        } while (page++ < pages);
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
		log.info("-------------------------------------------------------------------------");
		log.info("Flickr photoset list:");
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
	        		log.info(Utils.padRight(s.getPhotoCount()+"",5)+" "+DATE_FORMAT_DATE.format(printed)+" "+s.getId()+" "+s.getTitle());
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
   
    
    private void downloadPhotos() throws FlickrException, KeyManagementException, NoSuchAlgorithmException, IOException {    	
    	Photosets sets = f.getPhotosetsInterface().getList(auth.getUser().getId());
        for(Photoset s: sets.getPhotosets()) {
        	downloadPhotos(s.getId(), true, true);
        }
    }
    
    private Set<String> extras(String downloadQuality) {
    	Set<String> extras = new HashSet<String>();
    	String q = downloadQuality==null?Extras.URL_M
			:downloadQuality.equals("o")?Extras.URL_O
			:downloadQuality.equals("l")?Extras.URL_L
			:downloadQuality.equals("m")?Extras.URL_M
			:downloadQuality.equals("s")?Extras.URL_S
			:downloadQuality.equals("sq")?Extras.URL_SQ
			:downloadQuality.equals("t")?Extras.URL_T
			:Extras.URL_M;
    	extras.add(q);
    	extras.add(Extras.ORIGINAL_FORMAT);
    	return extras;
    }
    
    private void downloadPhotos(String albumTitle, boolean byId, boolean noQuestions) throws FlickrException, KeyManagementException, NoSuchAlgorithmException, IOException {    	
		HttpsURLConnection.setDefaultSSLSocketFactory(Utils.trustAllSocketFactory());    	
    	Photoset s = findSet(albumTitle, byId);
    	if(s == null) {
    		throw new IOException("Set not found.");
    	}
    	log.info("Processing photoset "+s.getId()+" ("+s.getTitle()+") ...");
        
    	Set<String> extras = extras(downloadQuality);
    	
    	String quality = downloadQuality==null?"medium"
			:downloadQuality.equals("o")?"original"
			:downloadQuality.equals("l")?"large"
			:downloadQuality.equals("m")?"medium"
			:downloadQuality.equals("s")?"small"
			:downloadQuality.equals("sq")?"square"
			:downloadQuality.equals("t")?"thumbnail"
			:"medium";    	
    	
    	final Pattern pattern = Pattern.compile(FILE_PATTERN);
    	
    	int page = 1;
        int pages = 0;
        do {
        	PhotoList<Photo> pl = f.getPhotosetsInterface().getPhotos(s.getId(), extras, Flickr.PRIVACY_LEVEL_NO_FILTER, 500, page);

        	File outDir = new File(DOWNLOAD_PREFIX+downloadQuality+"_"+ s.getId()+"_"+s.getTitle());
        	if(outDir.exists() && outDir.isDirectory()) {
        		long filesCount = outDir.list(new FilenameFilter() {
					public boolean accept(File dir, String name) {
						return pattern.matcher(name).matches();
					}
				}).length;
        		if(filesCount == pl.getTotal()) {
        			log.info("Skipping photoset "+s.getId()+" ("+s.getTitle()+"), filesCount in directory "+outDir.getAbsolutePath()+": "+filesCount);
        			continue;
        		}
        	}
        	
        	if(page == 1 && !noQuestions) {
	    		System.out.print("\nDownload (quality "+quality+") " + pl.getTotal() + " photos (y/n) ? ");						
	    		Scanner in = new Scanner(System.in);
	    		String yn = in.nextLine();
	    		in.close();
	    		if (!yn.equalsIgnoreCase("y")) {
	    			break;
	    		}    	
        	}
        	
        	for(Photo p: pl) {
        		
            	String urlString = Utils.getPhotoUrl(downloadQuality, p);

                String outFileTitle = p.getTitle();                
            	if(!pattern.matcher(outFileTitle).matches()) {
            		outFileTitle += "."+p.getOriginalFormat();
            	}            	
        		if(!outDir.isDirectory() && !outDir.exists()) {
        			boolean ok = outDir.mkdir();
        			if(!ok) {
        				throw new IOException("Can't create outpu directory "+outDir.getAbsolutePath());
        			}
        		}
        		File outFile = new File(outDir, outFileTitle);
                
        		if(outFile.exists()) {
        			log.info("Skipping "+urlString + ", fileExists: " + outFile);
        			continue;
        		}
        		
            	log.info("Downloading "+urlString + " -> " + outFile+" ...");
            	Utils.downloadUrlToFile(urlString, outFile);

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
