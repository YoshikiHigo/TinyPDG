public class Test025{
	
    public InputStream getResourceAsStream(String name) {
        InputStream resourceStream = null;
        if (isParentFirst(name)) {
            resourceStream = loadBaseResource(name);
            if (resourceStream != null) {
                System.out.println(name);
            } else {
                resourceStream = loadResource(name);
                if (resourceStream != null) {
                	System.out.println(name); } } }
        else {
            resourceStream = loadResource(name);
            if (resourceStream != null) {
            	System.out.println(name);
            } else {
                resourceStream = loadBaseResource(name);
                if (resourceStream != null) {
                	System.out.println(name); } } }
        if (resourceStream == null) {
        	System.out.println(name); }
        return resourceStream; }
    
    public URL getResource(String name) {
        URL url = null;
        if (isParentFirst(name)) {
            url = (parent == null) ? super.getResource(name) : parent.getResource(name); }
        if (url != null) {
        	System.out.println(name);
        } else {
            for (Enumeration e = pathComponents.elements(); e.hasMoreElements() && url == null; ) {
                File pathComponent = (File)e.nextElement();
                url = getResourceURL(pathComponent, name);
                if (url != null) {
                	System.out.println(name); } } }
        if (url == null && !isParentFirst(name)) {
            url = (parent == null) ? super.getResource(name) : parent.getResource(name);
            if (url != null) {
            	System.out.println(name); } }
        if (url == null) {
        	Systemm.out.println(name); }
        return url; }
}