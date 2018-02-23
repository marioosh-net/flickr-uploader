package net.marioosh.flickr;

public class Utils {
	
	public static String padLeft(String s, int n) {
		return String.format("%1$"+n+"s", s);  
	}
	
	public static String padRight(String s, int n) {
		return String.format("%1$-"+n+"s", s);  
	}
	
}
