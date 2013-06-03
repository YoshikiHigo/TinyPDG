/*
 *                 Sun Public License Notice
 * 
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 * 
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.modules.javadoc.search.environment;
import java.io.IOException;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.LocalFileSystem;
import org.openide.filesystems.JarFileSystem;
import org.openide.loaders.XMLDataObject;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileSystemCapability;
import org.openide.ErrorManager;
import org.openide.util.Lookup;
import java.io.File;
import org.netbeans.modules.javadoc.httpfs.HTTPFileSystem;
/**
 *
 * @author  sdedic, Petr Suchomel ( part of module taken from java )
 * @version 
 */
public class JavadocProcessor implements XMLDataObject.Processor, InstanceCookie {
    static final String JAVADOC_DTD_PUBLIC_ID = 
        "-//NetBeans IDE//DTD JavadocLibrary//EN"; // NOI18N
    static final String TAG_JAVADOC = "Javadoc"; //NOI18N
    static final String TAG_ARCHIVE = "Archive"; // NOI18N
    static final String TAG_FOLDER  = "Folder"; // NOI18N
    static final String TAG_HTTP    = "Http"; // NOI18N
    static final String ATTR_NAME = "name"; // NOI18N
    private static final char FILE_SEPARATOR = java.io.File.separatorChar;
    /**
     * XML info for the library def document.
     */
    private static XMLDataObject.Info   xmlinfo;
    /** XML data object. */
    protected XMLDataObject xmlDataObject;
    /**
     * Library File System created from the xml-info.
     */
    FileSystem  libraryFileSystem;
    /** Creates new LibraryProcessor */
    public JavadocProcessor() { }
    /** When the XMLDataObject creates new instance of the processor,
     * it uses this method to attach the processor to the data object.
     *
     * @param xmlDO XMLDataObject
     */
    public void attachTo(XMLDataObject xmlDO) {
        //System.out.println("Attaci to " + xmlDO);
        /*
        try{
            //System.out.println("Attaci to " + xmlDO.getDocument()); }
        catch(Exception ex){
            ex.printStackTrace();
        }*/
        xmlDataObject = xmlDO; }
    /** Create an instance.
     * @return the instance of type {@link #instanceClass}
     * @exception IOException if an I/O error occured
     * @exception ClassNotFoundException if a class was not found
     */
    public Object instanceCreate() throws java.io.IOException, ClassNotFoundException {
        if (libraryFileSystem != null)
            return libraryFileSystem;
            //try to create default location for javadoc
            File directory = null; 
            String fileSep = System.getProperty ("file.separator");   //NOI18N
            try {
                directory = new File (System.getProperty ("netbeans.user") + fileSep + "javadoc").getCanonicalFile();    }//NOI18N
            catch ( java.io.IOException e ) {                
                directory = new File (System.getProperty ("netbeans.user") + fileSep + "javadoc").getAbsoluteFile();    }//NOI18N
            if ( !directory.isDirectory() )
                directory.mkdirs();
            loadLibrary( xmlDataObject);
            return libraryFileSystem; }
    /** The representation type that may be created as instances.
     * Can be used to test whether the instance is of an appropriate
     * class without actually creating it.
     *
     * @return the representation class of the instance
     * @exception IOException if an I/O error occurred
     * @exception ClassNotFoundException if a class was not found
     */
    public Class instanceClass() {
        return FileSystem.class; }//JavadocArchive.class;
    /** The bean name for the instance.
     * @return the name
     */
    public String instanceName() {
        return instanceClass().getName(); }
    ////////////////////////////////////////////////////////////////////////
    //
    void throwIllegalMountException(String key, String mountedResource) 
        throws SAXException {
        ErrorManager man = (ErrorManager)Lookup.getDefault().lookup(ErrorManager.class);
        String message = java.text.MessageFormat.format(
            Utilities.getString(key),
            new Object[] {
                mountedResource,
                xmlDataObject.getPrimaryFile().toString()
        });
        throw (SAXException)man.annotate(
            new SAXException("Mount resource not found"), // NOI18N
            ErrorManager.USER,
            null, message, null, null); }
    public Object createInstance() throws java.io.IOException, ClassNotFoundException {
        if (libraryFileSystem != null)
            return libraryFileSystem;
            //try to create default location for javadoc
            File dir = null; 
            String sep = System.getProperty ("file.separator");   //NOI18N
            try {
                dir = new File (System.getProperty ("netbeans.user") + sep + "javadoc").getCanonicalFile();    }//NOI18N
            catch ( java.io.IOException e ) {                
                dir = new File (System.getProperty ("netbeans.user") + sep + "javadoc").getAbsoluteFile();    }//NOI18N
            if ( !dir.isDirectory() )
                dir.mkdirs();
            loadLibrary( xmlDataObject);
            return libraryFileSystem; }
    private void loadLibrary(final XMLDataObject xml) throws IOException {
        HandlerBase handler = new HandlerBase() {
            private boolean inJavadoc = false;
            public void startElement(String name, AttributeList attrlist) throws SAXException {
                if (TAG_JAVADOC.equals(name)) {
                    inJavadoc = true; }
                else if (!inJavadoc || (! TAG_ARCHIVE.equals(name)
                                        && ! TAG_FOLDER.equals(name)
                                        && ! TAG_HTTP.equals(name))) {
                    throwIllegalMountException("FMT_ILLEGAL_RESOURCE_SPEC", null);  }// NOI18N
                String nameString = attrlist.getValue(ATTR_NAME);
                if (nameString == null)
                    return;
                String systemName = xml.getPrimaryFile().getPackageNameExt('/', '.');
                if (systemName.equals("")) // NOI18N
                    return;
                if (TAG_HTTP.equals(name)) {
                    try {
                        JavadocHttp httpFs = new JavadocHttp(systemName);
                        httpFs.setURL(nameString);
                        libraryFileSystem = httpFs;
                        return; }
                    catch (IOException ex) {
                         } }// ignore
                if (FILE_SEPARATOR != '/') {
                    nameString.replace('/', FILE_SEPARATOR); }
                java.io.File  archiveRoot = Utilities.findInstalledFile(nameString);
                if (archiveRoot == null) {
                    throwIllegalMountException("FMT_RESOURCE_NOT_FOUND", nameString);  }// NOI18N
                try {
                    if (TAG_ARCHIVE.equals(name)) {
                        JarFileSystem jfs = new JavadocArchive(systemName);
                        jfs.setJarFile(archiveRoot);
                        libraryFileSystem = jfs; }
                    else if (TAG_FOLDER.equals(name)) {
                        LocalFileSystem jfs = new JavadocDirectory(systemName);
                        jfs.setRootDirectory(archiveRoot);
                        libraryFileSystem = jfs;
                    } else {
                        throwIllegalMountException("FMT_ILLEGAL_RESOURCE_SPEC", null);  }// NOI18N
                    libraryFileSystem.setHidden(true);
                    FileSystemCapability capab = libraryFileSystem.getCapability ();
                    if (capab instanceof FileSystemCapability.Bean) {
                        FileSystemCapability.Bean bean = (FileSystemCapability.Bean) capab;
                        bean.setCompile (false);
                        bean.setExecute (false);
                        bean.setDebug (false);
                        bean.setDoc (true); } }
                catch (java.beans.PropertyVetoException ex) {
                    libraryFileSystem = null;
                    throwIllegalMountException("FMT_ILLEGAL_RESOURCE_SPEC", null); // NOI18N
                    return; }
                catch (IOException ex) {
                    libraryFileSystem = null;
                    throwIllegalMountException("FMT_ILLEGAL_RESOURCE_SPEC", null); // NOI18N
                    return; } }
            public void endElement(String name) throws SAXException {
                if (TAG_JAVADOC.equals(name)) {
                    inJavadoc = false; } }
        };
        Parser parser = xml.createParser();
        parser.setDocumentHandler(handler);
        parser.setEntityResolver(new EntityResolver() {
            public InputSource resolveEntity(String pubid, String sysid) {
                return new InputSource(new java.io.ByteArrayInputStream(new byte[0])); }
        });
        try {
            parser.parse(new InputSource(xml.getPrimaryFile().getInputStream()));
        } catch (org.xml.sax.SAXException e) {
            IOException ex = new IOException ();
            org.openide.TopManager.getDefault().getErrorManager().copyAnnotation(ex, e);
            throw ex; } } }
